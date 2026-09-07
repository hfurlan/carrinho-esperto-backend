-- M004 — Catalog Foundation (docs/catalog-model.md).
--
-- The Catalog bounded context (docs/architecture.md §5) owns Carrinho Esperto's
-- CANONICAL product knowledge: what a product *is*, independent of how any
-- supermarket describes it on a receipt. This is the opposite of the Fiscal
-- context, which owns observed fiscal truth.
--
-- Ownership: catalog data is PLATFORM-owned and shared by all users. There is
-- deliberately no owner_user_id / user_id column anywhere in this migration —
-- one canonical "Leite UHT Integral Jussara 1L" is seen by everyone.
--
-- Not here (later milestones): product resolution, confidence/evidence,
-- store-specific product codes/aliases, external providers, AI, a generic
-- attribute/EAV framework. There is no foreign key from fiscal_item to product
-- — that link belongs to the Resolution context (M005+).

-- The company that manufactures a product. Distinct from Brand (a manufacturer
-- may own several brands; a brand's manufacturer may be unknown or change).
CREATE TABLE manufacturer (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT manufacturer_name_not_blank CHECK (btrim(name) <> '')
);
-- Exact-match uniqueness only. Names are canonical human-readable business data:
-- they are NOT lower-cased or de-accented (docs/catalog-model.md). Smarter
-- duplicate detection is future catalog-governance work.
CREATE UNIQUE INDEX manufacturer_name_key ON manufacturer (name);

-- The consumer-facing brand. manufacturer_id is nullable on purpose: during
-- early catalog construction the manufacturer is often unknown, and retail
-- private labels (e.g. "Carrefour") may have no distinct manufacturer recorded.
CREATE TABLE brand (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name             TEXT        NOT NULL,
    manufacturer_id  UUID        REFERENCES manufacturer (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT brand_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX brand_name_key ON brand (name);
CREATE INDEX brand_manufacturer_idx ON brand (manufacturer_id);

-- Top-level taxonomy. One category -> subcategory level only in M004; the
-- taxonomy is expected to evolve as real fiscal data is observed.
CREATE TABLE category (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT category_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX category_name_key ON category (name);

CREATE TABLE subcategory (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id  UUID        NOT NULL REFERENCES category (id),
    name         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT subcategory_name_not_blank CHECK (btrim(name) <> '')
);
CREATE UNIQUE INDEX subcategory_category_name_key ON subcategory (category_id, name);

-- A grouping of closely related canonical products that form one product line
-- (e.g. "Leite UHT Integral" for brand Jussara). A family is a grouping
-- mechanism, NOT a replacement for Product: sellable variants stay individual
-- Product rows. brand_id nullable — a family may span or precede brand data.
CREATE TABLE product_family (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id    UUID        REFERENCES brand (id),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT product_family_name_not_blank CHECK (btrim(name) <> '')
);
CREATE INDEX product_family_brand_idx ON product_family (brand_id);

-- The canonical product identity known by Carrinho Esperto. Every relationship
-- is optional: the catalog is built incrementally and a bare canonical_name is
-- a valid starting point.
CREATE TABLE product (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_name     TEXT          NOT NULL,
    brand_id           UUID          REFERENCES brand (id),
    manufacturer_id    UUID          REFERENCES manufacturer (id),
    category_id        UUID          REFERENCES category (id),
    subcategory_id     UUID          REFERENCES subcategory (id),
    product_family_id  UUID          REFERENCES product_family (id),
    -- Canonical GTIN. Owned by Catalog; independent from the OBSERVED GTIN on a
    -- fiscal_item (owned by Fiscal). Digits only, 8..14 — the "SEM GTIN" literal
    -- from a receipt is a Fiscal observation and must never land here.
    gtin               TEXT,
    -- Pack size, e.g. 1 (L), 395 (G), 12 (UN). Numeric, never money, never a
    -- fiscal quantity.
    package_quantity   NUMERIC(12,3),
    package_unit       TEXT,
    packaging_type     TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT product_canonical_name_not_blank CHECK (btrim(canonical_name) <> ''),
    CONSTRAINT product_gtin_format_check CHECK (gtin IS NULL OR gtin ~ '^[0-9]{8,14}$'),
    CONSTRAINT product_package_quantity_positive CHECK (package_quantity IS NULL OR package_quantity > 0),
    CONSTRAINT product_package_unit_check
        CHECK (package_unit IS NULL OR package_unit IN ('G', 'KG', 'ML', 'L', 'UN')),
    CONSTRAINT product_packaging_type_check
        CHECK (packaging_type IS NULL OR packaging_type IN (
            'CARTON', 'BOTTLE', 'CAN', 'BAG', 'BOX', 'TRAY', 'JAR', 'POUCH', 'UNIT', 'OTHER'))
);
-- A canonical GTIN identifies at most one canonical product.
CREATE UNIQUE INDEX product_gtin_key ON product (gtin) WHERE gtin IS NOT NULL;
CREATE INDEX product_brand_idx ON product (brand_id);
CREATE INDEX product_manufacturer_idx ON product (manufacturer_id);
CREATE INDEX product_category_idx ON product (category_id);
CREATE INDEX product_subcategory_idx ON product (subcategory_id);
CREATE INDEX product_family_ref_idx ON product (product_family_id);
-- Supports the deterministic case-insensitive name search (no fuzzy matching in M004).
CREATE INDEX product_canonical_name_lower_idx ON product (lower(canonical_name));
