# Resolution Core (M005)

> The **Resolution** bounded context ([architecture.md](architecture.md) §5)
> owns the interpretation that connects a user-owned **Fiscal** item to a
> shared, platform-owned **Catalog** product. M005 is the first slice: it
> answers *"which canonical product, if any, does this fiscal item refer to?"*
> using **deterministic exact-GTIN evidence only**. The conceptual model,
> evidence catalog and future methods live in
> [resolution-model.md](resolution-model.md); this document is what M005 built.

## The one-way dependency

```text
Fiscal            Resolution                     Catalog
(user-owned)      (platform-owned)               (platform-owned, shared)
  FiscalItem  ──►  ProductResolution  ──►  Product
                   + ResolutionEvidence
```

- Resolution **references** Fiscal (`fiscal_item_id`) and Catalog
  (`canonical_product_id`) by id. It never copies or persists those aggregates
  and never modifies them.
- Fiscal does **not** reference Resolution. Catalog does **not** reference
  Resolution. There is still no `product_id` / `resolution_id` on `fiscal_item`
  and no `fiscal_item_id` / `resolution_id` / `user_id` on `product` — the M004
  boundary test was updated to assert exactly this.

## Model (`V4__resolution.sql`)

| Table | Notes |
|---|---|
| `product_resolution` | `fiscal_item_id` NOT NULL → `fiscal_item` (`ON DELETE CASCADE`); `canonical_product_id` → `product`, NULL when `UNRESOLVED` (no cascade — the catalog has no DELETE); `status`, `method` (NULL when `UNRESOLVED`); `created_at`/`updated_at`. **`UNIQUE (fiscal_item_id)`** — one resolution per fiscal item (idempotency key). `CHECK`: `RESOLVED` ⇒ product + method both set; `UNRESOLVED` ⇒ both null |
| `resolution_evidence` | `resolution_id` NOT NULL → `product_resolution` (`ON DELETE CASCADE`); `type`, `source`, `value`, `created_at`. `CHECK type IN ('GTIN')`, `CHECK source IN ('FISCAL_ITEM')` |

Conventions match M002–M004: `UUID` PKs, `TIMESTAMPTZ`, explicit FKs + indexes,
ELSQL repositories (`sql/product_resolution.sql`, `sql/resolution_evidence.sql`),
no ORM, no repository interfaces.

### Statuses (`ResolutionStatus`)

`RESOLVED`, `PROBABLE`, `AMBIGUOUS`, `UNRESOLVED` — the full vocabulary is
defined now for later milestones. **M005's resolver produces only `RESOLVED` or
`UNRESOLVED`.**

- **`RESOLVED`** — exactly one canonical product identified from deterministic
  evidence.
- **`PROBABLE`** *(not produced yet)* — a leading candidate that available
  evidence strongly suggests but not deterministically (e.g. a future
  store-alias + description + package + brand combination).
- **`AMBIGUOUS`** *(not produced yet)* — several plausible canonical products
  remain (e.g. `"LEITE ITALAC 1L"` could be several variants); no choice is
  forced.
- **`UNRESOLVED`** — the evidence was evaluated and no sufficiently reliable
  canonical product was found. **A valid domain outcome, not an error** — it is
  a `200`/`201` response, never a 4xx/5xx (M005 §32).

### Methods (`ResolutionMethod`)

Only `GTIN_EXACT` exists. The enum and the DB `CHECK` are extensible
(`STORE_PRODUCT_CODE`, `GLOBAL_ALIAS`, `CATALOG_MATCH`, `EVIDENCE_RANKING`,
`AI_ASSISTED`, `USER_CONFIRMED`, …) without reshaping `product_resolution`.

### No confidence field

A deterministic exact GTIN match is not a probability. M005 represents it as
`status = RESOLVED, method = GTIN_EXACT` with no numeric score. There is no
`confidence` column; if evidence-ranking (M006) needs one it is a one-line
`ALTER TABLE ADD COLUMN … NULL`.

### Evidence

One item per `RESOLVED` resolution: `{ type: GTIN, source: FISCAL_ITEM, value:
"<the 8–14-digit GTIN>" }`. Historical/auditable — preserved once written, not a
transient cache. `UNRESOLVED` resolutions have no evidence. No scoring /
contribution fields, no JSON blobs.

## Deterministic GTIN resolution

### Fiscal GTIN selection (M005 §18)

`SaoPauloNfceParser` and the direct API both preserve `cEAN` / `cEANTrib` from
the fiscal document (or `null` / `"SEM GTIN"` when the source had none). The
resolver picks:

1. **`cEAN`** when it is a *usable canonical GTIN*;
2. otherwise **`cEANTrib`** when *it* is usable;
3. otherwise **no usable GTIN**.

A `cEAN` that is a valid GTIN but matches no catalog product is still "used" —
the outcome is `UNRESOLVED`, with **no fall-through** to `cEANTrib`.

*Usable canonical GTIN* (`resolution.Gtin`): after trimming, 8–14 digits —
matching `CHECK (gtin ~ '^[0-9]{8,14}$')` on `catalog.product.gtin`. This
rejects blank, non-numeric, wrong-length, and the `"SEM GTIN"` placeholder. No
GS1 check-digit validation (kept deliberately minimal).

### Outcomes

| Case | Result |
|---|---|
| A — usable GTIN, exactly one catalog product has it | `RESOLVED`, `GTIN_EXACT`, `canonicalProductId` set, one GTIN evidence row |
| B — usable GTIN, no catalog product has it | `UNRESOLVED` — **no product is created** (the catalog stays curated, M005 §21) |
| C — no usable GTIN (`null` / `"SEM GTIN"` on both fields) | `UNRESOLVED` — descriptions are **never** matched (M005 §22) |
| D — invalid GTIN text | `UNRESOLVED` — the fiscal item is valid, it just lacks usable evidence |

**No** description / fuzzy / Levenshtein / token / embedding / price / quantity
/ brand matching; **no** candidate ranking; **no** external providers (GS1, Open
Food Facts, store sites); **no** AI; **no** store vocabulary; **no** user
confirmation. Those are later milestones.

## API

| Method & path | |
|---|---|
| `POST /api/fiscal-items/{fiscalItemId}/resolution` | Resolve this user's fiscal item by exact GTIN. Idempotent: `201` on first resolution, `200` when one already existed. Returns the resolution. |
| `GET /api/fiscal-items/{fiscalItemId}/resolution` | Read the existing resolution; `404` if the item has none yet. Never creates one. |

Response (`ResolutionResponse`): `resolutionId`, `fiscalItemId`,
`canonicalProductId` (`null` when unresolved), `status`, `method` (`null` when
unresolved), `evidence[]` (`{type, source, value}`, empty when unresolved),
`createdAt`, `updatedAt`.

Errors (RFC 9457 `ProblemDetail`, central `ApiExceptionHandler`):
`FiscalItemNotFoundException` → `404` (item missing **or** not the caller's —
indistinguishable, no cross-user leak); `ResolutionNotFoundException` → `404`
(read endpoint, item not resolved yet); malformed `{fiscalItemId}` → `400`
(Spring); unauthenticated → `401`. An `UNRESOLVED` result is **not** in this
table.

## Ownership & privacy

- Every operation resolves the fiscal item through
  `FiscalDocumentService.findOwnedFiscalItem`, which joins to
  `fiscal_document.owner_user_id = <authenticated principal>`. User B cannot
  resolve or read User A's fiscal item (`404`).
- Catalog products are shared: two users' fiscal items with the same GTIN
  resolve to the **same** `canonicalProductId` (distinct resolution rows, one
  catalog row).
- Logs record `fiscalItemId`, `status`, `canonicalProductId`, `method` only —
  never `rawDescription`, never the full fiscal payload, never consumer data.
  No consumer CPF/name is introduced anywhere.

## Idempotency & transactions

`resolveFiscalItem` runs in one transaction: determine the outcome, then upsert.
On re-resolve it loads the existing row (unique on `fiscal_item_id`) and:

- **unchanged outcome** → returns it untouched (same `updatedAt`) — a true
  no-op;
- **changed outcome** (e.g. the catalog later gained the product) → updates the
  same row in place and rewrites its evidence set.

So repeated calls never accumulate `Resolution A/B/C` and never duplicate
evidence. No versioned resolution history, no cache (Redis or otherwise), no
async/queue processing — the table is the persisted result.

## Cross-context wiring

`ProductResolutionService` depends on the existing application services —
`FiscalDocumentService.findOwnedFiscalItem` (new, minimal read port) and
`CatalogService.findProductByGtin` (M004) — the same direct `@Service` injection
style used elsewhere (`NfceImportService` → `FiscalDocumentService`). No catalog
or fiscal SQL is duplicated in Resolution; no `ResolutionProduct` /
`ResolutionFiscalItem` copies exist.

## Deliberately deferred

Everything in [resolution-model.md](resolution-model.md) beyond exact GTIN:
description / fuzzy / evidence-ranking / candidate methods, `PROBABLE` /
`AMBIGUOUS` production, confidence scoring, store product codes & aliases (M007),
global aliases, external providers (GS1, Open Food Facts, store sites), AI,
user confirmation, price/quantity/brand evidence, the Purchase context (M008),
and any aggregate/cross-user analytics (M014).
