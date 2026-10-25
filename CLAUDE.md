# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Carrinho Esperto — a Brazilian supermarket purchase-intelligence app. It ingests
NFC-e fiscal receipts, resolves their line items to canonical products, and
surfaces spending insight. Spring Boot 4 (Java 25) backend + React 19 / Vite /
TypeScript / Tailwind v4 frontend, PostgreSQL, Cognito JWT auth.

Development proceeds as **numbered milestones** (M001–M006 done; see `docs/`).
Each milestone spec is implemented in isolation — do not build capabilities a
future milestone owns just because the architecture anticipates them.

## Commands

Local infra (Postgres 17 + MiniStack Cognito + a one-shot `bootstrap` that
writes `infra/ministack/.cognito.env` and `frontend/.env`):

```bash
docker compose up -d --wait
```

Backend (run from repo root):

```bash
mvn -f backend/pom.xml test                               # unit + integration (Testcontainers)
mvn -f backend/pom.xml test -Dtest=CatalogIntegrationTest # one class
mvn -f backend/pom.xml test -Dtest='ClassName#methodName' # one method
mvn -f backend/pom.xml -DskipTests package                # build the jar
source infra/ministack/.cognito.env
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend (`cd frontend` first):

```bash
npm install
npm test                                  # vitest run (all)
npx vitest run src/pages/DashboardPage.test.tsx   # one file
npx vitest run -t "empty state"           # by test name
npm run build                             # tsc -b && vite build — ALSO type-checks test files
npm run lint
npm run dev                               # http://localhost:5173
```

Dev login: `dev@carrinho-esperto.local` / `Devpass123!`.

### Integration-test infra gotcha

Integration tests use Testcontainers: they reuse a running `docker compose` if
they detect one, otherwise start their own. **This repo and the sibling
`../regulationai` both bind host ports 5432 and 4566** — only one stack can be
up at a time. Before running this project's backend tests, stop the other:
`cd ../regulationai && docker compose stop`, then bring this one up.

## Architecture

### Persistence — no ORM

Spring JDBC + **ELSQL**. SQL lives in `backend/src/main/resources/sql/*.sql` as
`@NAME(query)` blocks (indentation-sensitive), loaded per-repository via
`ElSqlBundle.parse(...)`. Repositories are concrete `@Repository` classes with
**no interfaces**. Schema changes are forward-only Flyway migrations
`db/migration/V{n}__name.sql` — inspect the existing sequence to pick the next
number; never edit an applied migration.

### Bounded contexts (packages under `br.com.carrinhoesperto`)

`fiscal` (+ `fiscal.ingestion`, `fiscal.ingestion.saopaulo`), `catalog`,
`resolution`, `dashboard`, `admin`, `insight`, `session`. Each context = model records → ELSQL
repositories → one `*Service` that owns all domain validation → controller +
`dto/`. Controllers hold no business logic. Cross-context access is
**service → service** (e.g. `ProductResolutionService` injects `CatalogService`
and `FiscalDocumentService`); never duplicate another context's SQL, model, or
aggregate.

### The domain spine: Fiscal → Resolution → Catalog

This one-way dependency is the core invariant:

- **Fiscal** — what a receipt *says*. **User-owned** (`fiscal_document.owner_user_id`;
  cross-user access is a 404). Source truth: `fiscal_item.raw_description` is
  stored verbatim and never rewritten; the literal `"SEM GTIN"` is preserved,
  not nulled. Ingestion (`fiscal.ingestion.saopaulo`) fetches the São Paulo
  SEFAZ public consultation behind an SSRF allow-list and parses HTML with Jsoup.
- **Catalog** — what a product canonically *is*. **Platform-owned**: no
  user/owner column anywhere; one `product` row is shared by all users. Has a
  canonical `gtin` (8–14 digits, `CHECK`ed so `"SEM GTIN"` can't land there),
  distinct from the observed GTIN on a `fiscal_item`.
- **Resolution** — the interpretation linking a fiscal item to a canonical
  product, plus its evidence. References Fiscal and Catalog **by id only**;
  neither references Resolution — there is **no FK from `fiscal_item` to
  `product`**. Deterministic only: two `ResolutionStrategy` beans run in
  precedence order and the first match wins — `GtinExactResolutionStrategy`
  (`GTIN_EXACT`, order 10), then `StoreProductCodeResolutionStrategy`
  (`STORE_PRODUCT_CODE`, order 20, M007: a curated `(store, cProd) → product`
  mapping in `store_product_code`, store = issuer CNPJ, platform-owned,
  admin-curated only). A valid exact GTIN always beats a store code. No
  fuzzy/description/alias/AI/provider matching; it never creates catalog rows.
  `UNRESOLVED` is a normal `200` result, not an error. Store-code mappings are
  managed under `/api/admin/store-product-codes` and re-applied to existing
  documents via `POST /api/admin/fiscal-documents/{id}/resolutions`
  (`docs/store-vocabulary.md`).

`dashboard`, `admin` and `insight` are thin read/aggregation layers over the
above, not new contexts. `/api/admin/**` is gated to `ROLE_ADMIN` (from the
Cognito `cognito:groups` claim) in `SecurityConfig`; its reads are platform-wide.
`insight` (M006.1, `docs/basket-insight.md`) groups a receipt's items into coarse
categories from `raw_description` with a deterministic keyword ruleset (no
catalog/NCM/AI) and a health "selo"; it persists nothing.

### Auth & ownership

OAuth2 resource server validating Cognito JWTs. Identity is read **only** from
`AuthenticatedUser` (the `sub` claim) — never a request parameter or body.
`UserProvisioningFilter` mirrors each validated identity into `app_user`.
Owner-scoped queries always filter on `owner_user_id`; a not-owned resource
returns the same 404 as a missing one (no ownership leak).

**Anonymous trial (M006.1, `docs/anonymous-trial.md`).** A visitor can use the
import → receipt → insight flow with no Cognito account. An anonymous session is
an `app_user` row (`kind = 'ANONYMOUS'`, hashed `anon_token_hash`, no
`cognito_subject`); the browser sends the raw token as `X-Anon-Session`.
`TrialSessionAuthenticationFilter` turns it into a `TrialSessionAuthentication`
(authority `ROLE_TRIAL`); `CurrentCaller.ownerUserId()` (behind
`UserService.currentUserId()`) resolves either kind of caller, so every
owner-scoped query is unchanged. `POST /api/anonymous-sessions` is the only
`permitAll` write. Admin stays `ROLE_ADMIN`-only. Trial data is
session-oriented: `last_seen_at` is recorded for a future retention sweep — no
cleanup job in M006.1. **M006.2** adds `POST /api/anonymous-sessions/claim`
(authenticated + `X-Anon-Session`): moves the trial's `fiscal_document` rows to
the account and deletes the anonymous `app_user` — one `UPDATE` + one `DELETE`,
no schema change. `GET /api/me` now returns `{ authenticated: false, subject:
null, username: null }` for a trial caller (was a 500), resolved via
`CurrentCaller`.

### Idempotency

Fiscal import dedupes on `(owner_user_id, access_key)` via
`INSERT ... ON CONFLICT DO NOTHING`. Resolution dedupes on a `UNIQUE
(fiscal_item_id)` — re-resolving upserts one row and only rewrites it when the
outcome actually changes.

### Errors

`@RestControllerAdvice ApiExceptionHandler` maps domain exceptions to RFC 9457
`ProblemDetail`, one handler per exception. Upstream-failure messages
(SEFAZ unavailable/unparseable) are fixed generic strings — no raw HTML,
upstream status, or stack traces reach the client.

### Frontend

`src/api/client.ts` `request()` is the single HTTP entry point: prepends the
base URL, attaches the bearer token (or, when there is none, the anonymous-trial
`X-Anon-Session` header), normalises every non-2xx to a typed `ApiError`, and
funnels 401s back to the auth layer (refresh for a Cognito session; drop the
trial session for an anonymous one). `src/auth/` owns both the Cognito session
and the anonymous trial session (`AuthContext` bridges them to the client via
`configureClient`). `useAsync` (`src/lib/`) + `StateViews` give every data screen
loading/empty/error states. Routing: `App.tsx` `RootRoute` shows the public
`LandingPage` at `/` when there is neither a user nor a trial session, the
`AppLayout` consumer shell otherwise; `/login` + `/cadastro` are consumer auth
pages (own chrome via `components/AuthShell`, no admin styling); `AdminLayout`
admin shell sits behind `RequireAuth` + `RequireAdmin`. `AuthContext` also owns
registration (`register` → `cognito.signUp` then `signIn`, with a
`ConfirmSignUp` step for a Cognito that requires it) and calls the account claim
after any successful auth. QR capture (`src/lib/qr/`) decodes the NFC-e QR in the
browser (`qr-scanner`; uses the native `BarcodeDetector` when available, behind a
small adapter); only the URL reaches the backend.

The **consumer** UI uses the "cupom & caderno" visual language
(`docs/consumer-ui.md`): tokens in `src/index.css` `@theme` (warm `--color-paper`
ground, `--color-ink` numbers, one `--color-accent` green), `Bricolage Grotesque`
display face, hairline rows over cards, shared primitives in `components/ui.tsx`
(`Button`/`Card`/`Field`/`Figure`/`Seal`/`Badge`/`PageHeader`). The **admin** UI
keeps its dense slate chrome and does NOT use these tokens. The **product UI is
Portuguese (Brazil)**, warm/human on the consumer side, technical in admin; code,
API names and docs stay English. `src/lib/format.ts` centralises `R$` / date /
CNPJ / access-key formatting.

### Integration test bases

`AbstractIntegrationTest` boots the app + Postgres. `AbstractAuthenticatedIntegrationTest`
additionally provisions a disposable MiniStack Cognito pool and exposes
`validAccessToken()`, `secondUserAccessToken()` (cross-user tests), and
`adminAccessToken()` (a user in the `admin` group). Anonymous-trial tests
(`AnonymousTrialIntegrationTest`) `POST /api/anonymous-sessions` for a token and
send it as `X-Anon-Session`; clean up with
`DELETE FROM app_user WHERE kind = 'ANONYMOUS'` after `DELETE FROM fiscal_document`.

## Docs

`docs/architecture.md` is the map (bounded contexts, ownership rules, roadmap).
Per-milestone: `fiscal-core.md`, `nfce-ingestion.md`, `catalog-model.md`,
`resolution-model.md` (conceptual) / `resolution-core.md` (M005 slice) /
`store-vocabulary.md` (M007 `(store, cProd)` resolution),
`frontend.md`, `anonymous-trial.md` + `basket-insight.md` (M006.1),
`consumer-ui.md` (M006.2 consumer visual language).
