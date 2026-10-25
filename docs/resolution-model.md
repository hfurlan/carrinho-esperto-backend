# Resolution model — conceptual (M001)

> **Conceptual / forward-looking.** This document describes the full intended
> Resolution model — uncertainty, the evidence catalog, store vocabulary,
> external providers, the priority order. It is the design target, not a
> description of current behaviour.
>
> **Implemented so far:** M005 built the first slice — the Resolution bounded
> context with deterministic **exact-GTIN** resolution only (`RESOLVED` /
> `UNRESOLVED`, method `GTIN_EXACT`, GTIN evidence). See
> [resolution-core.md](resolution-core.md) for what actually exists. Everything
> else below is still deferred.
>
> Read [architecture.md](architecture.md) first.

---

## 1. Problem statement

A fiscal item is a short, store-specific, often abbreviated string plus a few
numbers:

```text
rawDescription = "LEITE ITALAC INT 1L"
storeProductCode = "7891234"
quantity = 2, unit = "UN", unitPrice = 5.49
```

The same canonical product ("Italac — Leite UHT Integral — 1 L") appears under
different text at different stores, and sometimes differently at the same store
over time:

```text
LEITE ITALAC INT 1L
ITALAC LEITE UHT 1LT
LT ITALAC INTEGRAL 1000ML
```

**Resolution** is the process of deciding which canonical `Product` a
`FiscalItem` refers to — and recording *how sure* the system is and *why*.

## 2. What resolution is not

- It is **not** editing the fiscal item. `FiscalItem.rawDescription` is never
  changed (architecture.md §4.1). Resolution produces a *separate* link/record.
- It is **not** "the AI guessed it." AI, when eventually used, is one evidence
  source among many (architecture.md §4.7).
- It is **not** all-or-nothing. A purchase can be stored with an `UNRESOLVED`
  or `PROBABLE` item and improved later.
- It is **not** owned by Fiscal or Catalog. Resolution is its own context that
  reads both.

## 3. Uncertainty states

```text
RESOLVED     — one canonical product, high confidence (deterministic id match,
               or user confirmation)
PROBABLE     — a single leading candidate, not confirmed
AMBIGUOUS    — multiple candidates that cannot yet be separated
UNRESOLVED   — no usable candidate
```

State transitions are monotonic in practice but not forced: new evidence (a
later receipt, a provider lookup, a user confirmation) can move an item from
`UNRESOLVED` → `PROBABLE` → `RESOLVED`, or split a wrong `RESOLVED` back to
`AMBIGUOUS` when contradicting evidence arrives. The engine that decides this is
a later milestone.

## 4. Evidence catalog

Future resolution draws on multiple evidence sources. **None are implemented in
M001** — this is the catalog the model must be able to represent:

| Evidence | Nature | Typical strength |
|---|---|---|
| GTIN / EAN on the fiscal item | deterministic identifier | very high |
| Store product code | deterministic *within one store* | high (store-scoped) |
| Store-specific alias | learned store vocabulary | high (store-scoped) |
| Global alias | curated cross-store synonym | medium–high |
| Description similarity | fuzzy text match vs catalog | low–medium |
| Brand token | extracted from description | supporting |
| Package / unit / size | parsed from description | supporting |
| Price plausibility | consistent with known price band | weak, corroborating |
| Store website / catalog | external provider evidence | medium |
| External product database (GS1, Open Food Facts) | external provider evidence | medium |
| Historical purchases | this user bought this before under this text | medium–high |
| AI inference | model proposes / ranks candidates | corroborating, never sole |
| User confirmation | explicit human decision | decisive |

## 5. Conceptual priority order

Not an algorithm, not a scoring table, **no hard-coded weights in M001**. The
intended order of trust:

```text
1. Fiscal identifiers          (GTIN/EAN on the item)
2. Store-specific product codes
3. Store-specific aliases
4. Global aliases
5. Catalog matching            (attributes: brand, package, unit, size)
6. External product providers  (GS1, Open Food Facts, store sites) — evidence only
7. Evidence-based candidate ranking
8. AI-assisted resolution      — one more evidence source, ranks/proposes
9. User confirmation           — overrides everything
```

A later milestone decides how these combine (first-match short-circuit,
weighted accumulation, or a hybrid). M001 only fixes the *trust ordering* and
the rule that every step contributes **named evidence**, not an opaque verdict.

## 6. Conceptual records

Shapes only — column names, types and the actual tables are a later milestone.

```text
ProductResolution
  fiscalItem        → Fiscal.FiscalItem (by id; read-only reference)
  canonicalProduct  → Catalog.Product   (by id; nullable while not RESOLVED)
  status            RESOLVED | PROBABLE | AMBIGUOUS | UNRESOLVED
  confidence        a comparable measure (scale defined later, not in M001)
  method            which step/strategy produced the current conclusion
  evidence          ResolutionEvidence[]  (why)
  createdAt
  updatedAt
```

```text
ResolutionEvidence
  type            one row of the §4 catalog (GTIN, STORE_CODE, ALIAS, ...)
  source          who produced it (fiscal item, store vocabulary, provider name,
                  AI provider, user)
  value           the concrete datum (the GTIN, the alias text, the provider id)
  contribution    how it moved the conclusion (sign + magnitude; scale later)
  collectedAt
```

The invariant: given a `ProductResolution`, the system can always answer
*"why this product?"* by listing its `ResolutionEvidence`.

## 7. Store-specific vocabulary (first-class, future)

Store vocabulary is **not** implemented in M001 but is a planned first-class
capability, owned by the Resolution context (architecture.md §7, §11). The
concepts it will introduce:

```text
StoreProductCode   — (merchant, code) → canonical product, once confirmed
StoreProductAlias  — (merchant, rawDescription pattern) → canonical product
StoreBrandAlias    — (merchant, brand token) → canonical Brand
StoreUnitAlias     — (merchant, unit token e.g. "1LT","1000ML") → canonical Unit
StoreAttributeAlias— (merchant, token) → canonical Attribute value
StoreVocabulary    — the aggregate a resolver loads for one merchant
```

This knowledge **accumulates**: confirming a resolution at a store teaches that
store's vocabulary, so later receipts from the same store resolve
deterministically. This accumulated mapping is one of the product's strategic
assets (architecture.md §1).

## 8. External providers (evidence, never ownership)

```text
GS1 / GTIN registries      → attributes for a known GTIN
Open Food Facts            → attributes, categories, images for a GTIN
Supermarket websites       → description ↔ code ↔ price for one store
Commercial product catalogs→ bulk attribute data
```

Rules (architecture.md §4.6):

- A provider produces `ResolutionEvidence` and/or `ProductCandidate`s.
- A provider **never** writes directly into `Catalog.Product`. Promotion of
  provider data into the canonical catalog is a deliberate, reviewable step.
- Provider unavailability degrades confidence; it never blocks storing a
  purchase.

## 9. AI's place

AI is an **implementation/provider capability**, invoked behind the domain
concepts `ProductCandidate`, `Evidence`, `Confidence` (architecture.md §4.7):

- AI may **propose** candidates from a raw description.
- AI may **rank** an existing candidate set.
- AI output is `ResolutionEvidence` of type `AI_INFERENCE` with a named
  `source` (provider/model) — comparable and overridable like any other
  evidence.
- AI is **never** the sole basis for a `RESOLVED` status; that requires a
  deterministic identifier or user confirmation.

## 10. Build status

M001 fixed only the model and the rules. **M005** then built the
`product_resolution` / `resolution_evidence` tables and the deterministic
`GTIN_EXACT` resolver ([resolution-core.md](resolution-core.md)).

Still not built: any description / fuzzy / evidence-ranking / candidate matching,
production of `PROBABLE` / `AMBIGUOUS`, a confidence scale, store-vocabulary
tables and learning, provider clients (GS1, Open Food Facts, scrapers), and any
AI gateway.
