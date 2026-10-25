# NFC-e Ingestion — São Paulo (M003)

> The first real-world ingestion slice: a São Paulo NFC-e QR code URL becomes a
> persisted fiscal document. This is an **ingestion adapter** in front of the
> Fiscal bounded context ([architecture.md](architecture.md) §5); it does not
> own fiscal truth and it does no product interpretation
> ([fiscal-core.md](fiscal-core.md), [resolution-model.md](resolution-model.md)).
> Only **São Paulo** is supported — the provider seam is built so other states
> slot in without touching the Fiscal model.

## Flow

```text
POST /api/fiscal-documents/import   { "qrCodeUrl": "https://www.nfce.fazenda.sp.gov.br/…?p=…" }
      │
      ▼
NfceImportService            parse URL, pick provider by supports(uri)
      │
      ▼
SaoPauloNfceProvider
      ├── SaoPauloNfceQrCodeValidator   allow-list + extract 44-digit access key   (no network)
      │        └── short-circuit: FiscalDocumentService.findOwnedByAccessKey → already imported? return it
      ├── SefazSpHttpClient              one GET, HTTPS-only, no redirects, timeouts, size cap
      └── SaoPauloNfceParser             Jsoup → FiscalDocumentRequest   (no persistence)
      │
      ▼
FiscalDocumentService.create           M002: validation + (owner_user_id, access_key) idempotency + ownership
      │
      ▼
PostgreSQL
```

### Why São Paulo first

SP publishes a **stable public HTML consultation** at a single official endpoint
(`www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx`),
needs no authentication, and is a high-volume retail state. It is a good shape
to design the seam against. Other UFs (Minas Gerais, Paraná, …) each have their
own host, path and page layout — each will be a new `NfceProvider`.

## Provider abstraction

`NfceProvider` (in `br.com.carrinhoesperto.fiscal.ingestion`):

| Method | Purpose |
|---|---|
| `boolean supports(URI)` | cheap, non-throwing host/path recognition — routes an import to one provider |
| `String peekAccessKey(URI)` | extract + validate the 44-digit access key, **no network** — lets `NfceImportService` skip the fetch for an already-imported document |
| `FiscalDocumentRequest fetchAndParse(URI)` | fetch the public consultation and parse it; no persistence |

`NfceImportService` holds `List<NfceProvider>` (Spring injects all beans) and
picks the first whose `supports` returns true, else `422`. Adding a state = add
one `@Component implements NfceProvider`. The Fiscal tables, `FiscalDocumentService`
and the `/api/fiscal-documents` core endpoints are untouched.

## URL validation & SSRF protection

The QR URL is untrusted user input, so the import endpoint is **not** an
arbitrary URL fetcher. `SaoPauloNfceQrCodeValidator.extractAccessKey` requires
**all** of:

- scheme exactly `https` (an `http://` SEFAZ-SP URL → `400`, not `422`);
- host exactly `www.nfce.fazenda.sp.gov.br` (case-insensitive, **no** subdomain
  or suffix matching — `…sp.gov.br.evil.com` is rejected);
- port absent or `443`;
- no embedded credentials (`user:pass@…`);
- path exactly `/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx` **or** the
  short entry path `/qrcode` that current SP QR codes print (case-insensitive).
  A `/qrcode` URL is rewritten to the consultation path
  (`SaoPauloNfceQrCodeValidator.canonicalConsultationUri`, keeping the raw query)
  **before** the fetch, so the outbound request still targets the exact
  consultation page and no redirect has to be followed (M006.1);
- a `p` query parameter whose URL-decoded first `|`-field is exactly 44 digits.
  The QR carries literal `|` separators; `NfceImportService.parseHttpUri`
  percent-encodes them before `java.net.URI` sees the string.

Before the socket is opened, `SefazSpHttpClient` re-runs that validation
(defense in depth) and additionally resolves the host and rejects any
loopback / wildcard / link-local (incl. the `169.254.169.254` cloud-metadata
address) / site-local / multicast address. The HTTP client never auto-follows
redirects (`HttpClient.Redirect.NEVER`); **at most one** redirect is followed
manually, and only when its `Location` is itself an allow-listed SEFAZ-SP
consultation URL that is re-checked for a public address — SEFAZ 302-redirects a
lower-cased path to its canonical casing (M006.1). A second 3xx, or a `Location`
off the allow-list, is an unexpected response. This is an exact allow-list, not
a prefix or DNS check.

## HTTP client

`SefazSpHttpClient` — a single-purpose adapter over the JDK `HttpClient`:

- explicit connect timeout (`nfce.sao-paulo.connect-timeout`, default `5s`) and
  request timeout (`nfce.sao-paulo.read-timeout`, default `10s`) — no indefinite
  waits;
- response body read up to a hard cap (`nfce.sao-paulo.max-response-bytes`,
  default 3 MiB); a larger body is rejected, not truncated;
- `Content-Type` must be `text/html` when present;
- **no retry** — one controlled request per import;
- nothing from the response body is logged; failures log a short reason
  (`timeout`, `io`, `status:503`, `redirect:302`, `body-too-large`) and nothing
  else.

Error mapping (RFC 9457 `ProblemDetail`, via the central `ApiExceptionHandler`):

| Condition | Exception | HTTP |
|---|---|---|
| Malformed / non-HTTPS / wrong host or path / non-public address | `InvalidQrCodeUrlException` | 400 |
| `p` missing or no valid access key | `InvalidQrPayloadException` | 400 |
| Well-formed URL, no provider (other state) | `UnsupportedNfceProviderException` | 422 |
| Connection / timeout / DNS failure / upstream 5xx / 429 | `SefazUnavailableException` | 502 |
| Redirect, other non-200, wrong content type, oversized body | `SefazUnexpectedResponseException` | 502 |
| Page retrieved but fiscal content not extractable | `NfceParseException` | 502 |
| Parsed document fails M002 fiscal validation | `FiscalValidationException` | 400 |

The 502 responses carry a fixed generic message — raw HTML, upstream status
codes, stack traces and any consumer data stay server-side.

## Parser

`SaoPauloNfceParser` uses **Jsoup** with semantic selectors and label matching
(not absolute DOM positions), tolerating whitespace and minor layout changes.
Charset is auto-detected from the bytes. It performs no persistence.

Extracted from the SP consultation:

**Document** — access key (from the QR, not the HTML), `Número`, `Série`,
`Emissão` (converted from `dd/MM/uuuu HH:mm:ss` in `America/Sao_Paulo` to an
ISO-8601 offset string), issuer name (`#u20`), issuer CNPJ, issuer printed
address, `Valor total` (merchandise), `Descontos`, `Valor a pagar` (payable).
The three-way total distinction (merchandise / discount / payable) is preserved
and nothing is recomputed.

**Items** (`#tabResult tr`) — line order (duplicates kept as separate lines),
raw description **verbatim**, `cProd` (`(Código: …)`), quantity, unit, unit
price, line total. Weighted products are handled as decimals with full
precision (`0.9944 KG @ 10.750201 → 10.69`, no rounding).

**Payments** (`#totalNota` payment rows) — every displayed method (e.g.
`Cartão de Débito`, `Cartão de Crédito`) with its amount. Split payments
supported. The public page shows a **label**, not a `tPag` code, so the label
goes into `FiscalPayment.method` as-is; no code is inferred and no canonical
payment enum is introduced. A `Troco` (change) row is **not** a payment and is
skipped — the live page renders it as the literal `NaN` when there is none.

**Robustness against the live page.** `money()` returns `null` for any cell that
is not a plain number after Brazilian→decimal normalisation (`NaN`, an empty
cell, a stray token) rather than failing the whole import — a genuinely missing
*required* value is still caught by the `item-fields` / `totals-payable` checks.
The `#totalNota` iteration also skips the block element itself (jsoup's
`select()` can return the context node).

### Fields intentionally left `null`

The public consultation does **not** expose these, so the parser never sets them
and never infers them ([architecture.md](architecture.md) §4.1):

- item `gtin` (`cEAN`), `gtinTaxable` (`cEANTrib`)
- item `ncm`, `cest`
- per-item `discount`

M002 already models all of these as optional.

## Privacy / data minimization (M003 §5)

The real consultation page shows the **consumer's CPF and name**. Carrinho
Esperto:

- has **no** consumer CPF/name field in the Fiscal model, and adds none;
- the parser has **no code path** that reads the consumer block
  (`.clConteudoConsumidor` / `.ulListaConsumidor`);
- never logs response-body content;
- the committed fixtures under
  `backend/src/test/resources/fixtures/nfce/` are **sanitized** (CPF/name
  replaced with placeholders). `sao-paulo-consulta-qrcode.html` is a trimmed
  capture; `sao-paulo-consulta-qrcode-live-troco-nan.html` is a full, current
  capture of the same receipt straight from SEFAZ (with the `NaN` Troco row and
  the extra chrome the live page emits). All fiscal content is real public data.

## Idempotency & ownership (unchanged from M002)

- Identity is the **NFC-e access key**, per owner — never the URL.
  `NfceImportService` extracts the key from the QR (no network) and, if the
  caller already has that document, returns it (`200`) without contacting SEFAZ.
  Otherwise the parsed request goes through `FiscalDocumentService.create`,
  which also dedupes on `(owner_user_id, access_key)` via
  `INSERT … ON CONFLICT DO NOTHING`.
- Ownership is always the authenticated principal. `POST /api/fiscal-documents/import`
  → `201` first time, `200` on repeat. A cross-user `GET` of an imported
  document is `404`.

## Manual verification (M003 §21)

Verified against the live consultation on **2026-09-06** using the official
endpoint
`https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=<access key>|2|1|1|<hash>`
(access key `35260900371046000117650080001172161001384659`).

**Extracted successfully:** issuer name / CNPJ / address; access key;
`Número` 117216, `Série` 8, `Emissão` 06/09/2026 14:57:04; all 22 line items
with description, `cProd`, quantity (incl. 5 weighted `KG` lines and a
quantity-12 line), unit, unit price (6-decimal on weighted), line total;
duplicate line (`LEITE COND.ITALAC TP 395G` ×2) kept separate; totals
merchandise `R$ 328,11`, discount `R$ 12,88`, payable `R$ 315,23`; split
payment `Cartão de Débito R$ 313,26` + `Cartão de Crédito R$ 1,97`.

**Not available on the public page** (left `null`): per-item GTIN / `cEANTrib` /
NCM / CEST / discount. Also shown on the page but deliberately **not** ingested:
the consumer CPF and name; the authorization protocol number.

The sanitized fixtures mirror this exact receipt. The normal build never calls
the live site — unit tests use a fixture, integration tests mock
`SefazSpHttpClient`.

**Opt-in live test.** `SefazSpLiveIntegrationTest` really calls SEFAZ (real
`SefazSpHttpClient` + parser, no Spring). It is `@EnabledIfSystemProperty` and
runs only on demand:

```bash
mvn -f backend/pom.xml test -Dtest=SefazSpLiveIntegrationTest -Dnfce.live=true
# against your own receipt:
mvn -f backend/pom.xml test -Dtest=SefazSpLiveIntegrationTest -Dnfce.live=true \
  -Dnfce.live.url='https://www.nfce.fazenda.sp.gov.br/qrcode?p=<chave>|2|1|1|<hash>'
```

It exercises the `/qrcode` short link, the canonical `.aspx` URL, a lower-cased
path (one redirect), access-key peeking, and the allow-list rejections (wrong
key length, foreign host, plain http), and prints the itemised receipt.

## Extending to another state

1. New `@Component class <Uf>NfceProvider implements NfceProvider` (+ its own
   validator / HTTP client / parser as needed).
2. Its `supports(URI)` recognises that state's official consultation host/path.
3. Nothing else changes — `NfceImportService` picks it up, and it feeds the
   same `FiscalDocumentService.create`.

## Deliberately not built here

QR retrieval from an image; NFC-e XML retrieval; any other state; any Catalog /
Product / Resolution / Merchant / GTIN-lookup / GS1 / Open Food Facts / AI /
analytics work; a retry/backoff system; frontend UI for import.
