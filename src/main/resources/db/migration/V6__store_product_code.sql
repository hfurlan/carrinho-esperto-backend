-- M007 — Store Intelligence: deterministic (store, cProd) resolution
-- (docs/resolution-model.md §7, docs/store-vocabulary.md).
--
-- The Resolution bounded context learns a supermarket's own product code. A
-- store uses its cProd (fiscal_item.supplier_item_code) consistently for the
-- same item, so `(store, cProd) -> canonical Product` is a deterministic
-- resolution signal — second only to an exact GTIN.
--
-- Store identity is the fiscal issuer CNPJ (14 digits), already the stable
-- issuer key in the Fiscal context. NOT the store name. This vocabulary is
-- PLATFORM-OWNED (no user column): a mapping curated once benefits every user
-- who imports from that store.

-- A store's own product code mapped to a canonical catalog product. Resolution
-- may FK into Catalog (it already depends on it). ON DELETE CASCADE: if a
-- canonical product is ever removed, the mapping that points at it is dead and
-- goes with it (unlike product_resolution, which is audited user data and keeps
-- no cascade).
CREATE TABLE store_product_code (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- issuer CNPJ, digits only.
    store_id              TEXT        NOT NULL,
    -- cProd, stored verbatim (it is the identity signal — NOT the raw
    -- description, which stays fiscal truth on fiscal_item).
    code                  TEXT        NOT NULL,
    canonical_product_id  UUID        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One canonical product per (store, code) at a time. A conflicting mapping
    -- is rejected by the service (409), never silently overwritten (M007 §13).
    CONSTRAINT store_product_code_store_code_key UNIQUE (store_id, code),
    CONSTRAINT store_product_code_store_id_check CHECK (store_id ~ '^[0-9]{14}$'),
    CONSTRAINT store_product_code_code_not_blank CHECK (btrim(code) <> '')
);
CREATE INDEX store_product_code_product_idx ON store_product_code (canonical_product_id);

-- product_resolution now also records the STORE_PRODUCT_CODE method.
ALTER TABLE product_resolution DROP CONSTRAINT product_resolution_method_check;
ALTER TABLE product_resolution ADD CONSTRAINT product_resolution_method_check
    CHECK (method IS NULL OR method IN ('GTIN_EXACT', 'STORE_PRODUCT_CODE'));

-- resolution_evidence now also records STORE_PRODUCT_CODE evidence. `metadata`
-- carries the store identity for that evidence type (NULL for GTIN). The
-- existing evidence model is extended, not replaced (M007 §19).
ALTER TABLE resolution_evidence DROP CONSTRAINT resolution_evidence_type_check;
ALTER TABLE resolution_evidence ADD CONSTRAINT resolution_evidence_type_check
    CHECK (type IN ('GTIN', 'STORE_PRODUCT_CODE'));
ALTER TABLE resolution_evidence ADD COLUMN metadata TEXT;

-- No data backfill: existing resolutions are untouched until an administrator
-- creates a mapping and re-runs resolution (M007 §16).
