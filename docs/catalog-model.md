# Catalog Model (M004)

> The **Catalog** bounded context ([architecture.md](architecture.md) §5) owns
> Carrinho Esperto's *canonical* product knowledge: what a product **is**,
> independent of how any supermarket describes it on a receipt. It is the
> strategic platform asset the later Resolution, price-intelligence,
> recommendation and analytics work all build on. M004 is the foundation —
> **no product resolution, no store vocabulary, no external providers, no AI**
> (those are M005+).

## The three concepts, kept separate

| Concept | Owner | Example |
|---|---|---|
| **Fiscal truth** — what the receipt reported | Fiscal ([fiscal-core.md](fiscal-core.md)) | `rawDescription = "LEITE JUSSARA INTEGRAL 1L"`, `cProd = "697002"`, `quantity = 12` |
| **Canonical interpretation** — what the product is | **Catalog (this doc)** | `Product{ canonicalName: "Leite UHT Integral Jussara 1L", brand: Jussara, family: "Leite UHT Integral", packageQuantity: 1, packageUnit: L }` |
| **Resolution** — that this fiscal item probably refers to that product, with evidence | Resolution (M005+, not built) | `"LEITE JUSSARA INTEGRAL 1L" @ store X → Product 123, confidence …, evidence …` |

M004 establishes the **target** (`Product 123`). It never links a fiscal item to
a product — there is no FK from `fiscal_item` to `product`, and that never
changes in this milestone.

## Ownership — platform-owned, not user-owned

This is the key architectural difference from Fiscal:

```text
FiscalDocument   → user-owned   (owner_user_id, cross-user access is 404)
Product          → platform-owned, shared by everyone
```

There is **no** `user_id` / `owner_user_id` on any catalog table. If 1,000 users
buy the same product there is still one canonical `Product` row; a product
created by one authenticated user is returned verbatim to every other. Catalog
endpoints require authentication but are not user-scoped. (Write access is open
to any authenticated user in M004; admin-gating is future catalog-governance
work.)

## Entities (`V3__catalog.sql`)

```text
manufacturer ─┐
              ├─< brand ─┐
              │          ├─< product_family
              │          │
category ─< subcategory  │
              │          │
              └──────────┴──< product   (every FK optional)
```

| Table | Notes |
|---|---|
| `manufacturer` | the company that makes a product. `name` NOT NULL, exact-match unique. Distinct from `brand` — **not** inferred from it |
| `brand` | consumer-facing brand. `name` NOT NULL/unique; `manufacturer_id` **nullable** — often unknown early, and private labels may have none (e.g. `Brand "Carrefour"`, manufacturer `null`) |
| `category` / `subcategory` | one level only. `subcategory.category_id` NOT NULL; unique `(category_id, name)`. Not a perfect taxonomy — it evolves as real fiscal data is seen |
| `product_family` | groups the sellable variants of one product line (e.g. `"Leite UHT Integral"` → the 1 L / 1.5 L / zero-lactose products). `brand_id` nullable. A grouping mechanism, **not** a replacement for `product` (§28) |
| `product` | the canonical product identity. `canonical_name` NOT NULL; **every** relationship (`brand_id`, `manufacturer_id`, `category_id`, `subcategory_id`, `product_family_id`) optional — a bare `canonical_name` is a valid start |

Conventions match M002/M003: `UUID` PKs (`gen_random_uuid()`), `TIMESTAMPTZ`
`created_at`/`updated_at`, FK constraints, ELSQL repositories (no ORM, no
repository interfaces), Flyway.

### Canonical GTIN

`product.gtin` — nullable `TEXT`, `CHECK (gtin ~ '^[0-9]{8,14}$')`, **partial
unique index** `WHERE gtin IS NOT NULL`. A canonical GTIN identifies **at most
one** canonical product; a duplicate create is `409`.

Ownership boundary: Fiscal owns the **observed** GTIN on a `fiscal_item`
(including the literal `"SEM GTIN"`); Catalog owns the **canonical** GTIN on a
`product`. The `CHECK` makes it impossible to store `"SEM GTIN"` in
`product.gtin` — that is a fiscal observation, not a canonical identifier. The
future Resolution context is what will connect `FiscalItem.cEAN` → `Product.gtin`.

### Package information

`package_quantity NUMERIC(12,3)` (`> 0` when present; never money, never a
fiscal quantity) + `package_unit` (`G | KG | ML | L | UN`, `CHECK`ed, backed by
the `PackageUnit` enum) + `packaging_type` (`CARTON | BOTTLE | CAN | BAG | BOX |
TRAY | JAR | POUCH | UNIT | OTHER`, `CHECK`ed, `PackagingType` enum). A small
closed set on purpose — enough to distinguish important variants, extensible
later. No universal measurement system.

### Names are canonical business data

Names are **not** lower-cased or de-accented — `"Leite UHT Integral Jussara 1L"`
and `"Perdigão"` are stored verbatim (edge whitespace trimmed only). Uniqueness
is exact-match. Smarter duplicate detection is deliberately deferred to future
catalog governance.

## API (minimal — M004 §18)

All under `/api/catalog`, authenticated, not user-scoped, **no DELETE** (canonical
products will be referenced by future resolutions / purchases / prices).

| Method & path | |
|---|---|
| `POST /manufacturers`, `/brands`, `/categories`, `/subcategories`, `/product-families`, `/products` | `201` + the created entity. Bodies carry only canonical data — never `rawDescription`, `cProd`, `fiscalDocumentId`, `userId`, `confidence`, `resolutionMethod` |
| `GET /{entity}/{id}` | the entity, or `404` |
| `GET /products/{id}` | canonical product by id (`404` if absent) — **for M005 Resolution** |
| `GET /products?gtin=<8–14 digits>` | list of 0 or 1 (canonical GTIN → at most one product) — **for M005 Resolution** |
| `GET /products?name=<text>` | deterministic, case-insensitive substring match on `canonical_name`, name-ordered, capped at 50. **No fuzzy matching** (no Levenshtein / embeddings / scoring — that is Resolution) |

Errors use the existing RFC 9457 `ProblemDetail` flow: `CatalogValidationException`
→ 400 (blank name, duplicate reference name, malformed GTIN, bad package
quantity/unit/type), `CatalogEntityNotFoundException` → 404 (lookup miss, or an
unknown FK on a create), `GtinAlreadyAssignedException` → 409.

## Relationship with Fiscal

Independent. M004 does not modify the Fiscal context, adds no FK between them,
and the boundary is covered by an integration test
(`FiscalCatalogBoundaryIntegrationTest`): a `fiscal_item.raw_description` of
`"LEITE JUSSARA INTEGRAL 1L"` and a `product.canonical_name` of
`"Leite UHT Integral Jussara 1L"` coexist, and re-reading the fiscal document
shows the raw description untouched.

## Relationship with Resolution (M005+)

Resolution will *consume* this catalog: given a fiscal item, it will look up
candidate products (by GTIN via `GET /products?gtin=`, by name via
`GET /products?name=`, by id via `GET /products/{id}`) and record a
`ProductResolution` with status / confidence / method / evidence
([resolution-model.md](resolution-model.md)). None of that exists yet.

## Deliberately deferred

Product resolution, candidate ranking, confidence/evidence, GTIN-based auto
resolution; store-specific product codes / aliases / vocabulary (M007); a
generic attribute / EAV framework (the richer attributes — flavor, fat content,
zero-sugar, lactose-free … — will be modelled from real data later, not
speculatively); global `ProductAlias` (not needed for the foundation — the
simpler model is preferred); external providers (GS1, Open Food Facts, store
sites); AI; purchase history; price intelligence; a `Store` entity; hard-delete
/ archival lifecycle; a frontend catalog screen.
