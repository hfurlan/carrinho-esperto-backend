# M007 — Store Intelligence: deterministic `(store, cProd)` resolution

## Why

M005 gave Resolution one deterministic strategy — exact GTIN. Many Brazilian
fiscal items carry no usable GTIN (or no catalog product for it yet), but a
supermarket uses its own product code (`cProd`) consistently for the same item.
M007 teaches Resolution this **store-specific vocabulary, deterministically**: a
curated `(store, cProd) → Product` mapping becomes a second resolution method.

No fuzzy matching, no AI, no external providers, no automatic learning. Nothing
lives outside `br.com.carrinhoesperto.resolution` — no new bounded context.

## Store identity

The store is the **fiscal issuer CNPJ** (`fiscal_document.issuer_cnpj`, 14
digits, already normalized and the issuer key in the Fiscal context). Not the
store name. The chain-vs-physical-store distinction stays deferred
(`fiscal-core.md`); for `(store, cProd)` the legal-entity CNPJ is the right
grain. Fiscal is unchanged.

## Schema — `V6__store_product_code.sql`

```
store_product_code
  id, store_id TEXT, code TEXT,
  canonical_product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  created_at, updated_at
  UNIQUE (store_id, code)
  CHECK (store_id ~ '^[0-9]{14}$'),  CHECK (btrim(code) <> '')
```

**No `user_id`** — platform-owned. One curated mapping benefits every user who
imports from that store. `code` is the identity signal, stored verbatim — never
the fiscal `raw_description` (which stays immutable on `fiscal_item`).

V6 also relaxes two `CHECK`s (`product_resolution.method` and
`resolution_evidence.type` gain `STORE_PRODUCT_CODE`) and adds a nullable
`resolution_evidence.metadata` column. **No data backfill** — existing
resolutions are untouched until an admin acts.

## Resolution — strategy pipeline

`ProductResolutionService` no longer computes the outcome inline. It runs the
registered `ResolutionStrategy` beans in ascending `order()` and takes the first
match:

| order | strategy | method | evidence |
|---|---|---|---|
| 10 | `GtinExactResolutionStrategy` | `GTIN_EXACT` | `type=GTIN, source=FISCAL_ITEM, value=<gtin>, metadata=null` |
| 20 | `StoreProductCodeResolutionStrategy` | `STORE_PRODUCT_CODE` | `type=STORE_PRODUCT_CODE, source=FISCAL_ITEM, value=<cProd>, metadata=<store CNPJ>` |

First match wins ⇒ **a valid exact GTIN always beats a store code** (M007 §7).
`FiscalItemContext` (fiscal id + gtin + gtinTaxable + cProd + storeId) is built
from either the Fiscal aggregate (owner-scoped single-item path) or a
`FiscalItemResponse` (admin-scoped document path), so both reuse the strategies.
The single resolution row per fiscal item and the "rewrite evidence only when
the outcome changes" idempotency (M005) are unchanged. This is a small ordered
list, not a rules engine — a future deterministic method adds one class.

## Curation — admin only

Mappings are **only** created through the admin API. No automatic learning from
unresolved items; no consumer-facing correction UI.

- `GET  /api/admin/store-product-codes?storeId=&code=` — list (code filter is a prefix)
- `POST /api/admin/store-product-codes` `{storeId, code, canonicalProductId}` → `201`
- `PUT  /api/admin/store-product-codes/{id}` `{canonicalProductId}` → re-point
- `DELETE /api/admin/store-product-codes/{id}` → `204`

`StoreProductCodeAdminController` lives in the `resolution` package (Resolution
owns the domain); the `/api/admin/**` path guarantees `ROLE_ADMIN` via
`SecurityConfig`. Validation in `StoreProductCodeService`: 14-digit CNPJ,
non-blank code, product must exist (`404`), and a `(storeId, code)` already
mapped is a hard **`409`** (`StoreProductCodeConflictException`) — never an
overwrite (M007 §13). Deleting a mapping leaves items resolved via it stale
until the next re-resolution.

## Re-resolution

`POST /api/admin/fiscal-documents/{id}/resolutions` (`AdminResolutionController`)
re-runs `ProductResolutionService.resolveDocumentForAdmin` — the **same**
per-item pipeline and response DTO as the consumer batch
(`resolveOwnedDocument`), only the document fetch is admin-scoped
(`findByIdForAdmin`). Admin frontend: the fiscal-document detail page gets a
**"Reprocessar identificação"** button that renders per-item
`status / method / produto canônico` inline.

## Consumer

Untouched (M007 §21). An item previously shown as "não identificado" simply
starts showing its canonical product name once a mapping exists and resolution
is re-run — the existing receipt UI already renders that for any method.

## Deferred

`StoreProductAlias` (store description → product) is **not** in M007 — no table,
no admin, no `STORE_ALIAS` method. Also out: `BrandAlias` / `UnitAlias` /
attribute EAV, per-user vocabulary, external providers, any probabilistic /
fuzzy / AI method.
