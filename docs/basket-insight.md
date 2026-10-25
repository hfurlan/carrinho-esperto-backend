# M006.1 — Basket Insight

## Why

The anonymous trial needs an immediate payoff. After a receipt is read, Carrinho
Esperto groups what was bought and shows a qualitative "how was this shopping"
**seal**, plus a short explanation. That is what makes a no-login trial worth
doing — and it is the natural hook for "create an account to build your
history". Shown for anonymous **and** authenticated callers.

This is a small, deliberate carve-out from the "no basket intelligence" line in
the M006.1 spec. It is **not** the start of an Analytics/Recommendation context.

## What it is (and is not)

`br.com.carrinhoesperto.insight` is a thin read/derive layer, a sibling of
`dashboard`:

- reads one of the caller's receipts through `FiscalDocumentService`
  (ownership enforced — `404` when not the caller's, anonymous or authenticated);
- classifies each `fiscal_item.raw_description` into one coarse bucket;
- returns per-bucket value + share, the supérfluo / básico / unclassified
  shares, a seal, and 1–2 pt-BR sentences.

It **persists nothing**, adds **no migration**, never touches Resolution or
Catalog, and never rewrites fiscal truth. Every call recomputes.

`GET /api/fiscal-documents/{id}/insight` → `BasketInsightResponse`.

## Categories (`BasketCategory`)

| Bucket | Supérfluo? |
|---|---|
| `BASICO_IN_NATURA` — arroz, feijão, ovo, frutas, verduras, carne in natura, leite | no |
| `ALIMENTO_PROCESSADO` — macarrão, embutidos, laticínios, enlatados, óleo, café, pão | no |
| `BEBIDA_BAIXO_VALOR` — refrigerante, suco em pó, energético, bebida alcoólica | **yes** |
| `DOCE_SUPERFLUO` — chocolate, balas, biscoito recheado, sorvete, salgadinho | **yes** |
| `LIMPEZA` | no |
| `HIGIENE` | no |
| `NAO_CLASSIFICADO` — no rule matched | no |

## Classifier (`BasketClassifier`)

Deterministic and transparent — no catalog, no NCM (the SP parser does not
extract it), no AI:

- normalize the description: upper-case, strip diacritics, collapse
  non-alphanumerics to single spaces;
- match an **ordered** list of keyword rules at a word boundary (`\bKEYWORD`), so
  `REFRIG` hits `REFRIGERANTE` but `OVO` does not hit `NOVO`;
- order puts specific supérfluo/beverage rules before generic staples
  (`LEITE CONDENSADO` before `LEITE`, `OVOS DE PASCOA` before `OVO`);
- first match wins; nothing matches → `NAO_CLASSIFICADO`.

Coverage is intentionally partial and the ruleset is meant to grow. Thresholds
and keywords are named constants / a single static list.

## Seal (`BasketSeal`)

Value-weighted shares of the whole receipt:

| Seal | Rule |
|---|---|
| `SEM_DADOS` | unclassified share > 40% (not enough recognised to judge) |
| `MUITOS_SUPERFLUOS` | supérfluo share ≥ 35% |
| `EQUILIBRADO` | supérfluo share < 15% **and** básico-in-natura share ≥ 40% |
| `ATENCAO` | anything else |

`messages` are generated from those numbers (e.g. "42% do valor da nota foi em
doces, salgadinhos e bebidas de baixo valor nutricional.").

## Frontend

`BasketInsight` (`components/BasketInsight.tsx`) renders the seal badge, the
messages, a segmented value bar and a per-category list. It appears:

- on `ReceiptDetailPage`, between the totals card and the item list, for every
  caller;
- on `DashboardPage`, for the **most recent** receipt only (no new historical
  aggregate in M006.1).

If the insight call fails it renders nothing — it never blocks the receipt.
