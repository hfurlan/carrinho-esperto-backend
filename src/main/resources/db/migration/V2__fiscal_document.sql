-- M002 — Fiscal Document Core Slice (docs/fiscal-core.md).
--
-- The Fiscal bounded context (docs/architecture.md §5) persists what an NFC-e
-- fiscal document actually reports. It is SOURCE TRUTH: raw_description and the
-- fiscal identifiers below are stored exactly as received and are never
-- rewritten into a canonical product name. Product canonicalization belongs to
-- the future Catalog/Resolution contexts and is deliberately absent here.
--
-- No NFC-e retrieval/parsing exists yet (that is M003): a document arrives as a
-- structured payload through the API. These tables only store it.

-- The fiscal document as reported by an NFC-e consultation, owned by the
-- authenticated user who imported it. Issuer data is kept as plain columns on
-- the document (not a merchant/store domain) — enough to identify the issuing
-- business and to solve the legal-entity / chain / physical-store distinction
-- later (docs/fiscal-core.md), which M002 deliberately does not attempt.
CREATE TABLE fiscal_document (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id       UUID          NOT NULL REFERENCES app_user (id),
    -- NFC-e "chave de acesso": 44 numeric digits, the natural fiscal identity of
    -- the document. Stored normalized (digits only). Uniqueness is per owner:
    -- the same receipt re-imported by the same user is the same row (idempotent
    -- create), while two different keys from the same issuer are two documents.
    access_key          TEXT          NOT NULL,
    -- nNF / serie. Encoded inside the access key too, but kept explicit when the
    -- payload provides them. Nullable: a partial payload may omit them.
    document_number     BIGINT,
    series              INTEGER,
    -- dhEmi — when the document was issued.
    issued_at           TIMESTAMPTZ   NOT NULL,
    -- Issuer (emitente) as reported by the document.
    issuer_cnpj         TEXT          NOT NULL,
    issuer_legal_name   TEXT,
    issuer_trade_name   TEXT,
    -- Free-text address exactly as printed. Not decomposed — no address domain.
    issuer_address      TEXT,
    -- vNF — total value of the document. vProd (merchandise) and vDesc (discount)
    -- are kept when reported; only vNF is required.
    total_amount        NUMERIC(14,2) NOT NULL,
    total_items_amount  NUMERIC(14,2),
    total_discount      NUMERIC(14,2),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fiscal_document_access_key_owner_key UNIQUE (owner_user_id, access_key),
    CONSTRAINT fiscal_document_access_key_format_check
        CHECK (access_key ~ '^[0-9]{44}$'),
    CONSTRAINT fiscal_document_total_amount_check CHECK (total_amount >= 0),
    CONSTRAINT fiscal_document_total_items_amount_check
        CHECK (total_items_amount IS NULL OR total_items_amount >= 0),
    CONSTRAINT fiscal_document_total_discount_check
        CHECK (total_discount IS NULL OR total_discount >= 0)
);
-- Owner listing, newest issued first.
CREATE INDEX fiscal_document_owner_idx ON fiscal_document (owner_user_id, issued_at DESC);
-- Issuer lookups (future analytics / store reconciliation).
CREATE INDEX fiscal_document_issuer_cnpj_idx ON fiscal_document (issuer_cnpj);

-- One printed line of the fiscal document. Every value here is fiscal truth.
CREATE TABLE fiscal_item (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_document_id  UUID           NOT NULL REFERENCES fiscal_document (id) ON DELETE CASCADE,
    -- nItem — 1-based position of the line within the document.
    line_number         INTEGER        NOT NULL,
    -- xProd — the original fiscal product description. NEVER replaced with a
    -- canonical name (docs/architecture.md §4.1). Stored verbatim.
    raw_description      TEXT           NOT NULL,
    -- cEAN / cEANTrib. When the document reports "SEM GTIN" that literal string
    -- is preserved as-is — it is not silently turned into NULL (source info is
    -- kept). NULL means the field was absent from the payload entirely.
    gtin                TEXT,
    gtin_taxable        TEXT,
    -- cProd — the issuer's own product code. Preserved for future
    -- store-specific product resolution (docs/resolution-model.md §7).
    supplier_item_code  TEXT,
    -- qCom / uCom / vUnCom / vProd / vDesc — reported as-is, not recomputed.
    quantity            NUMERIC(15,4)  NOT NULL,
    unit                TEXT           NOT NULL,
    unit_price          NUMERIC(21,10) NOT NULL,
    total_price         NUMERIC(14,2)  NOT NULL,
    discount            NUMERIC(14,2),
    -- NCM / CEST fiscal classifications, when present.
    ncm                 TEXT,
    cest                TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fiscal_item_document_line_key UNIQUE (fiscal_document_id, line_number),
    CONSTRAINT fiscal_item_line_number_check CHECK (line_number >= 1),
    CONSTRAINT fiscal_item_quantity_check CHECK (quantity >= 0),
    CONSTRAINT fiscal_item_unit_price_check CHECK (unit_price >= 0),
    CONSTRAINT fiscal_item_total_price_check CHECK (total_price >= 0),
    CONSTRAINT fiscal_item_discount_check CHECK (discount IS NULL OR discount >= 0)
);
CREATE INDEX fiscal_item_document_idx ON fiscal_item (fiscal_document_id);
-- Future deterministic resolution by identifier. Partial: a real GTIN only —
-- the "SEM GTIN" literal and absent values are not worth indexing.
CREATE INDEX fiscal_item_gtin_idx ON fiscal_item (gtin)
    WHERE gtin IS NOT NULL AND gtin <> 'SEM GTIN';
-- Future store-specific resolution by the issuer's product code.
CREATE INDEX fiscal_item_supplier_item_code_idx ON fiscal_item (supplier_item_code)
    WHERE supplier_item_code IS NOT NULL;

-- A payment line (pag/detPag) as reported by the fiscal document. This is
-- purely the payment information printed on the receipt — NOT a personal-finance
-- or account domain (deferred).
CREATE TABLE fiscal_payment (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    fiscal_document_id  UUID          NOT NULL REFERENCES fiscal_document (id) ON DELETE CASCADE,
    line_number         INTEGER       NOT NULL,
    -- tPag as reported: the SEFAZ code ("01", "03", "17", …) or whatever the
    -- payload carries. method_description holds the human label when present.
    method              TEXT          NOT NULL,
    method_description  TEXT,
    amount              NUMERIC(14,2) NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fiscal_payment_document_line_key UNIQUE (fiscal_document_id, line_number),
    CONSTRAINT fiscal_payment_line_number_check CHECK (line_number >= 1),
    CONSTRAINT fiscal_payment_amount_check CHECK (amount >= 0)
);
CREATE INDEX fiscal_payment_document_idx ON fiscal_payment (fiscal_document_id);
