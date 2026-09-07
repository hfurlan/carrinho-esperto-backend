-- M005 — Basic Product Resolution (docs/resolution-core.md).
--
-- The Resolution bounded context (docs/architecture.md §5) owns the
-- INTERPRETATION connecting a user-owned Fiscal item to a shared, platform-owned
-- Catalog product:
--
--     Fiscal  (what the receipt says)
--        -> Resolution  (which canonical product it refers to, + evidence)
--        -> Catalog     (what the canonical product is)
--
-- One-way dependency: Resolution references Fiscal and Catalog; neither Fiscal
-- nor Catalog references Resolution. There is NO product_id / resolution_id on
-- fiscal_item, and NO fiscal_item_id / resolution_id / user_id on product.
--
-- M005 implements only deterministic exact-GTIN resolution (method GTIN_EXACT),
-- producing status RESOLVED or UNRESOLVED. PROBABLE / AMBIGUOUS are part of the
-- vocabulary for later milestones (evidence-based resolution) but are never
-- produced here. No description / fuzzy / price / brand / AI matching, no
-- automatic catalog creation.

-- One resolution per fiscal item (M005 §15 idempotency). Re-running the resolver
-- upserts this row rather than accumulating duplicates.
CREATE TABLE product_resolution (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- references Fiscal (user-owned). CASCADE: a resolution has no meaning once
    -- its fiscal item is gone.
    fiscal_item_id        UUID        NOT NULL REFERENCES fiscal_item (id) ON DELETE CASCADE,
    -- references Catalog (platform-owned, shared). NULL when UNRESOLVED. No
    -- CASCADE: a curated product should not be deletable out from under a
    -- resolution silently, and the catalog has no DELETE anyway.
    canonical_product_id  UUID        REFERENCES product (id),
    status                TEXT        NOT NULL,
    -- NULL when UNRESOLVED. Single value today; extensible (STORE_PRODUCT_CODE,
    -- GLOBAL_ALIAS, CATALOG_MATCH, EVIDENCE_RANKING, AI_ASSISTED, USER_CONFIRMED, …).
    method                TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT product_resolution_status_check
        CHECK (status IN ('RESOLVED', 'PROBABLE', 'AMBIGUOUS', 'UNRESOLVED')),
    CONSTRAINT product_resolution_method_check
        CHECK (method IS NULL OR method IN ('GTIN_EXACT')),
    -- Shape invariants for the statuses M005 actually produces. PROBABLE /
    -- AMBIGUOUS are left unconstrained until their semantics are implemented.
    CONSTRAINT product_resolution_shape_check CHECK (
        (status = 'RESOLVED'   AND canonical_product_id IS NOT NULL AND method IS NOT NULL)
     OR (status = 'UNRESOLVED' AND canonical_product_id IS NULL     AND method IS NULL)
     OR (status IN ('PROBABLE', 'AMBIGUOUS'))
    )
);
-- Idempotency key: at most one resolution row per fiscal item in M005.
CREATE UNIQUE INDEX product_resolution_fiscal_item_key ON product_resolution (fiscal_item_id);
CREATE INDEX product_resolution_product_idx ON product_resolution (canonical_product_id);

-- The auditable basis of a resolution. M005 records exactly one kind:
-- type = GTIN, source = FISCAL_ITEM, value = the 8..14-digit GTIN taken from the
-- fiscal item. No scoring / contribution fields; no JSON blobs. Preserved as
-- historical data — not a transient calculation cache.
CREATE TABLE resolution_evidence (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    resolution_id  UUID        NOT NULL REFERENCES product_resolution (id) ON DELETE CASCADE,
    type           TEXT        NOT NULL,
    source         TEXT        NOT NULL,
    value          TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT resolution_evidence_type_check CHECK (type IN ('GTIN')),
    CONSTRAINT resolution_evidence_source_check CHECK (source IN ('FISCAL_ITEM')),
    CONSTRAINT resolution_evidence_value_not_blank CHECK (btrim(value) <> '')
);
CREATE INDEX resolution_evidence_resolution_idx ON resolution_evidence (resolution_id);
