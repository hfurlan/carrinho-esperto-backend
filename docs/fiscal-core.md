# Fiscal Document Core (M002)

> Implements the first functional slice of the **Fiscal** bounded context
> designed in [architecture.md](architecture.md) §5: persist a fiscal document
> (NFC-e) and its items and payments as **source truth**, with no product
> canonicalization of any kind. No NFC-e retrieval or parsing yet — that is M003
> (§8 below). Everything here is testable without SEFAZ access.

## What exists

```text
FiscalDocument  (aggregate root, user-owned)
   ├── FiscalItem     (1..N, the printed lines)
   └── FiscalPayment  (0..N, the payment lines)
```

A user can: import a fiscal-document payload (`POST`), list their fiscal
documents, and read one back with its items and payments. That is the whole
surface — no update, no delete, no interpretation.

Issuer data (`CNPJ`, legal name, trade name, printed address) is kept as plain
columns on `fiscal_document`. M002 deliberately does **not** model the
distinction between legal entity, supermarket chain and physical store
([architecture.md](architecture.md) §11) — it only preserves what is needed to
solve that later.

## Critical rule — fiscal truth is preserved

`FiscalItem.rawDescription` is stored **exactly** as received and returned
exactly as stored:

```text
"LEITE ITALAC INT 1L"   stays  "LEITE ITALAC INT 1L"
"NESCAU 2.0 350G"        stays  "NESCAU 2.0 350G"
```

It is never rewritten into a canonical name like `"Italac Leite UHT Integral
1L"` — that is Resolution/Catalog work ([architecture.md](architecture.md)
§4.1), and none of it happens in M002. Likewise:

- **`SEM GTIN`** — when the document reports no GTIN, the literal string
  `"SEM GTIN"` is preserved in `gtin` / `gtin_taxable`. It is not silently
  turned into `NULL` (which would lose the fact that the document explicitly
  said so). `NULL` means the field was absent from the payload entirely.
- **`cProd`** (`supplier_item_code`) — the issuer's own product code is kept
  verbatim. It is the anchor for future store-specific resolution
  ([resolution-model.md](resolution-model.md) §7).
- **quantities / prices / totals** — stored as reported (`qCom`, `vUnCom`,
  `vProd`, `vNF`, …). Nothing is recomputed; the service only checks values are
  non-negative.

## Data model (`V2__fiscal_document.sql`)

| Table | Notes |
|---|---|
| `fiscal_document` | `owner_user_id` → `app_user`. `access_key` (44 digits, `CHECK` on format) is the natural fiscal identity. `document_number` (`nNF`), `series` (`serie`) nullable — a partial payload may omit them. `issued_at` (`dhEmi`), `issuer_cnpj`, `total_amount` (`vNF`) required; `total_items_amount` (`vProd`), `total_discount` (`vDesc`) optional. `issuer_*` columns, no merchant domain. `UNIQUE (owner_user_id, access_key)` |
| `fiscal_item` | `fiscal_document_id` (`ON DELETE CASCADE`), `line_number` (`nItem`). `raw_description` (`xProd`) verbatim. `gtin` / `gtin_taxable` keep `"SEM GTIN"`. `supplier_item_code` (`cProd`). `quantity`, `unit`, `unit_price`, `total_price` required; `discount`, `ncm`, `cest` optional. `CHECK` ≥ 0 on the numerics, `UNIQUE (fiscal_document_id, line_number)` |
| `fiscal_payment` | `fiscal_document_id` (`ON DELETE CASCADE`), `line_number`, `method` (`tPag` as reported), `method_description` (optional label), `amount` (`vPag`, `CHECK` ≥ 0). Only method + amount — **not** a personal-finance domain |

Indexes: `fiscal_document (owner_user_id, issued_at DESC)` (owner listing),
`fiscal_document (issuer_cnpj)`; `fiscal_item (fiscal_document_id)`, partial
`fiscal_item (gtin) WHERE gtin <> 'SEM GTIN'` and partial
`fiscal_item (supplier_item_code)` for future deterministic/store resolution;
`fiscal_payment (fiscal_document_id)`.

## Uniqueness & idempotent import

`(owner_user_id, access_key)` is unique. The access key — **never** the
description, date or total — is the fiscal identity. So:

- the **same** NFC-e re-submitted by the **same** user → the stored document is
  returned, unchanged, with **`200 OK`** (no second row);
- **different** access keys from the same issuer → distinct documents;
- two different users may each import the same key (their own copy) — M002 has
  no household/sharing model.

The insert is `INSERT … ON CONFLICT (owner_user_id, access_key) DO NOTHING`
(same pattern as `UserRepository.insertIfAbsent`): a race between two concurrent
imports resolves to one row, and the loser gets the stored document back rather
than an error.

## API

All routes require authentication. Ownership is always
`UserService.currentUserId()` — the payload carries no user field, and a user
can only ever see their own documents (a cross-user `GET` is `404`, not `403`,
so ownership is not leaked).

| Method & path | Notes |
|---|---|
| `POST /api/fiscal-documents` | Body: `FiscalDocumentRequest` (access key, issuer, totals, `items[]`, `payments[]`). `201 Created` on first import; `200 OK` returning the stored document on a repeat. Returns `FiscalDocumentResponse` (document + items + payments) so no second round trip is needed |
| `GET /api/fiscal-documents` | The caller's documents, newest `issued_at` first (`FiscalDocumentSummaryResponse`: identity, issuer CNPJ/trade name, total, item count) |
| `GET /api/fiscal-documents/{id}` | `FiscalDocumentResponse`; `404` if not owned by the caller |

Errors follow the existing `ProblemDetail` convention via the central
`ApiExceptionHandler`: `FiscalValidationException` → `400`,
`FiscalDocumentNotFoundException` → `404`, unauthenticated → `401` from Spring
Security.

## Domain integrity (not fiscal certification)

The service validates only enough to keep the data coherent (domain integrity,
not certification): access key present and 44 digits (non-digits
stripped first), issuer CNPJ present and 14 digits, `issuedAt` a parseable
ISO-8601 date-time with an offset, `totalAmount` present and ≥ 0, at least one
item, each item's `rawDescription` non-blank and `quantity`/`unitPrice`/
`totalPrice` ≥ 0, `unit` present, each payment's `method` present and `amount`
≥ 0. It does **not** verify NF-e check digits, tax arithmetic, schema
conformance or SEFAZ authorization — that would need a full NF-e validation
engine and is out of scope.

## Explicit separation from Catalog / Resolution

M002 does **none** of: create canonical products, `ProductAlias`, resolve or
normalize descriptions, infer brand/manufacturer/category, call GS1 / Open Food
Facts / store sites, or call an AI model. `raw_description` in, `raw_description`
out. The Fiscal context owns fiscal truth and nothing else
([architecture.md](architecture.md) §7).

## M003 dependency — NFC-e providers

M002 has no QR-code parsing, URL validation, SEFAZ HTTP client, per-state
provider, HTML scraping or XML retrieval. A document arrives as a structured
`FiscalDocumentRequest` — supplied by tests today, and by an NFC-e provider in
**M003**, which will fetch/parse a real consultation and hand the same
structured payload to `FiscalDocumentService.create`.

## Deliberately not built here

Update/delete of a fiscal document; merchant/store reconciliation; any Catalog,
Resolution, store vocabulary, analytics, recommendation or subscription
feature; a receipt-import UI, catalog screens or dashboards. No frontend
changes were made in M002 — the slice is a backend persistence boundary and the
milestone explicitly deprioritizes UI work.

## Known limitations

- **Issuer is per-document.** The same store across two receipts is two
  independent `issuer_*` blobs; there is no canonical merchant yet.
- **`method` is stored raw.** `tPag` codes (`"01"`, `"03"`, `"17"`, …) are not
  mapped to a labelled enum — that mapping, if useful, is a later, cheap
  addition.
- **No cross-field arithmetic check.** The service does not assert that
  `Σ item.total_price` or `Σ payment.amount` equals `total_amount`; fiscal
  documents in the wild carry rounding and freight lines that would make a
  strict check reject valid data.
