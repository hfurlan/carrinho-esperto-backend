# M006.1 — Anonymous Trial

## Why

The first interaction with Carrinho Esperto must not ask the user to understand
or sign up for the product before experiencing it. A visitor should be able to
scan a real NFC-e, see the receipt, inspect resolution, and see basket insight —
with no Cognito account. Authentication remains the mechanism for **persistence**
(history, deeper reports), offered *after* the value is visible, not before.

Two modes coexist:

```
Anonymous trial            Authenticated user
  capture QR                 persistent purchases
  import NFC-e               historical analytics
  inspect receipt            future personalized features
  basket insight
  session-scoped data
```

## Mechanism (smallest coherent)

An anonymous trial session **is an `app_user` row** — `kind = 'ANONYMOUS'`, no
`cognito_subject`, a unique `anon_token_hash`, and `last_seen_at`
(`V5__anonymous_session.sql`). Reusing the identity table is the whole point:
every owner-scoped query already keys on `app_user.id`, so Fiscal, Dashboard,
Resolution and Insight work unchanged.

- **Token.** `POST /api/anonymous-sessions` (the only unauthenticated write,
  `permitAll`) mints a 256-bit URL-safe token, stores only its SHA-256 digest,
  and returns the raw token once. The browser keeps it in `localStorage`
  (`carrinho-esperto.anon`) and sends it as the `X-Anon-Session` header.
- **Authentication.** `TrialSessionAuthenticationFilter` runs before the bearer
  filter. With a valid `X-Anon-Session` and no `Authorization: Bearer`, it puts a
  `TrialSessionAuthentication` (a real authenticated principal, authority
  `ROLE_TRIAL`, principal = the anonymous `app_user.id`) in the context. A real
  Cognito token always wins; an unknown trial token is left unauthenticated → the
  resource server returns `401` (never a silent downgrade).
- **Owner resolution.** `CurrentCaller.ownerUserId()` returns the JWT user's id
  or the trial session's id; `UserService.currentUserId()` delegates to it, so no
  service changed.
- **Activity.** `last_seen_at` is stamped on session creation and on each
  successful anonymous import (`NfceImportService`) — a write-path touch only, so
  anonymous reads stay read-only.

## Boundaries

- **Isolation.** An anonymous session can only see its own data. A cross-session
  read — anonymous↔anonymous or anonymous↔authenticated — is the same `404` as
  any not-owned resource. Verified by `AnonymousTrialIntegrationTest`.
- **Admin.** `/api/admin/**` stays `hasRole("ADMIN")`. `ROLE_TRIAL` → `403`.
- **QR / SSRF.** Anonymous imports go through the same `NfceImportService` →
  `SaoPauloNfceQrCodeValidator` path: HTTPS-only allow-list, no credentials,
  DNS-rebind guard, response limits, provider selection. The frontend's
  `classifyDecodedQr` is a UX pre-check only and cannot widen it.
- **PII.** Unchanged — no consumer identity is parsed, stored or exposed.
- **QR images.** Decoded entirely in the browser (`qr-scanner` / native
  `BarcodeDetector`); only the resulting URL string reaches the backend. The
  photo is never uploaded.

## `GET /api/me` (M006.2)

- Cognito caller → `200 { "authenticated": true, "subject": "...", "username": "..." }`.
- Anonymous trial caller → `200 { "authenticated": false, "subject": null, "username": null }`
  (the request is authorized as `ROLE_TRIAL`; it simply has no user identity).
- No recognised caller → `401` (before the controller runs).

`MeController` resolves this through `CurrentCaller`; it no longer reads the JWT
unconditionally, so a trial caller can no longer trigger a `500`.

## Account claim (M006.2)

`POST /api/anonymous-sessions/claim` — for a caller that has **just signed in or
registered** and still sends its `X-Anon-Session` header. It moves that session's
receipts into the account and drops the anonymous `app_user` row:

```
UPDATE fiscal_document SET owner_user_id = <account> WHERE owner_user_id = <anon>;
DELETE FROM app_user WHERE id = <anon> AND kind = 'ANONYMOUS';
```

Requires a Cognito identity (a trial-only caller is a no-op — it would resolve to
its own row). A caller can only ever claim the session whose token it presents.
No schema change, no new bounded context — ownership already keys on
`fiscal_document.owner_user_id`. The browser calls it right after a successful
auth when an anonymous token is present, then discards the token. Returns
`{ "movedReceipts": n }`.

## Retention (documented, not enforced)

Anonymous trial data that is **not** claimed is temporary/session-oriented.
`last_seen_at` exists so a future milestone can purge unclaimed anonymous
`app_user` rows (and their cascaded Fiscal data) after ~30 days of inactivity.
Still no background cleanup job and no TTL enforcement.

## Frontend

- `AuthContext` gains `anonSession`, `startAnonymousSession()`,
  `endAnonymousSession()`. A Cognito session always supersedes and clears the
  anonymous token; `login()` ends the trial session.
- `client.ts` attaches `X-Anon-Session` only when there is no bearer token; a
  `401` on an anonymous request clears the session (no "expired" screen).
- `RootRoute` renders the public `LandingPage` at `/` when there is neither a
  user nor a trial session, the `AppLayout` shell otherwise, and bounces deep
  routes to `/`. `/admin/**` keeps `RequireAuth` + `RequireAdmin`.
- `AppLayout` shows a "Modo teste — seus dados não são salvos" banner and a
  "Criar conta para salvar" CTA instead of the user menu.
