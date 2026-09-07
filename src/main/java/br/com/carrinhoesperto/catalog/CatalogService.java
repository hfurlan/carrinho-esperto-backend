package br.com.carrinhoesperto.catalog;

import br.com.carrinhoesperto.catalog.dto.BrandRequest;
import br.com.carrinhoesperto.catalog.dto.BrandResponse;
import br.com.carrinhoesperto.catalog.dto.CategoryRequest;
import br.com.carrinhoesperto.catalog.dto.CategoryResponse;
import br.com.carrinhoesperto.catalog.dto.ManufacturerRequest;
import br.com.carrinhoesperto.catalog.dto.ManufacturerResponse;
import br.com.carrinhoesperto.catalog.dto.ProductFamilyRequest;
import br.com.carrinhoesperto.catalog.dto.ProductFamilyResponse;
import br.com.carrinhoesperto.catalog.dto.ProductRequest;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.catalog.dto.SubcategoryRequest;
import br.com.carrinhoesperto.catalog.dto.SubcategoryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Application service for the Catalog bounded context (M004 §16). Owns all
 * catalog domain validation — controllers only pass DTOs through.
 *
 * <p>Catalog data is <strong>platform-owned</strong>: nothing here is scoped to
 * a user, and no method takes or trusts a user id. It contains no Resolution
 * concepts (confidence, evidence, method) and no Fiscal concepts
 * (rawDescription, cProd) — see docs/catalog-model.md.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_CANONICAL_NAME_LENGTH = 300;
    private static final int PACKAGE_QUANTITY_SCALE = 3;
    private static final Pattern GTIN = Pattern.compile("\\d{8,14}");

    private final ManufacturerRepository manufacturerRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final ProductFamilyRepository productFamilyRepository;
    private final ProductRepository productRepository;

    CatalogService(ManufacturerRepository manufacturerRepository,
                   BrandRepository brandRepository,
                   CategoryRepository categoryRepository,
                   SubcategoryRepository subcategoryRepository,
                   ProductFamilyRepository productFamilyRepository,
                   ProductRepository productRepository) {
        this.manufacturerRepository = manufacturerRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.productFamilyRepository = productFamilyRepository;
        this.productRepository = productRepository;
    }

    // ── Manufacturer ───────────────────────────────────────────────────────

    @Transactional
    public ManufacturerResponse createManufacturer(ManufacturerRequest request) {
        String name = requireName(request == null ? null : request.name(), "name", MAX_NAME_LENGTH);
        UUID id = UUID.randomUUID();
        try {
            manufacturerRepository.insert(id, name);
        } catch (DuplicateKeyException e) {
            throw new CatalogValidationException("A manufacturer named '" + name + "' already exists");
        }
        log.info("catalog manufacturer created id={}", id);
        return ManufacturerResponse.from(loadManufacturer(id));
    }

    @Transactional(readOnly = true)
    public ManufacturerResponse getManufacturer(UUID id) {
        return ManufacturerResponse.from(loadManufacturer(id));
    }

    // ── Brand ──────────────────────────────────────────────────────────────

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        String name = requireName(request == null ? null : request.name(), "name", MAX_NAME_LENGTH);
        UUID manufacturerId = request == null ? null : request.manufacturerId();
        requireReference(manufacturerId, manufacturerRepository::existsById, "Manufacturer");

        UUID id = UUID.randomUUID();
        try {
            brandRepository.insert(id, name, manufacturerId);
        } catch (DuplicateKeyException e) {
            throw new CatalogValidationException("A brand named '" + name + "' already exists");
        }
        log.info("catalog brand created id={} manufacturerId={}", id, manufacturerId);
        return BrandResponse.from(loadBrand(id));
    }

    @Transactional(readOnly = true)
    public BrandResponse getBrand(UUID id) {
        return BrandResponse.from(loadBrand(id));
    }

    // ── Category / Subcategory ─────────────────────────────────────────────

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = requireName(request == null ? null : request.name(), "name", MAX_NAME_LENGTH);
        UUID id = UUID.randomUUID();
        try {
            categoryRepository.insert(id, name);
        } catch (DuplicateKeyException e) {
            throw new CatalogValidationException("A category named '" + name + "' already exists");
        }
        log.info("catalog category created id={}", id);
        return CategoryResponse.from(loadCategory(id));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(UUID id) {
        return CategoryResponse.from(loadCategory(id));
    }

    @Transactional
    public SubcategoryResponse createSubcategory(SubcategoryRequest request) {
        UUID categoryId = request == null ? null : request.categoryId();
        if (categoryId == null) {
            throw new CatalogValidationException("categoryId is required");
        }
        requireReference(categoryId, categoryRepository::existsById, "Category");
        String name = requireName(request.name(), "name", MAX_NAME_LENGTH);

        UUID id = UUID.randomUUID();
        try {
            subcategoryRepository.insert(id, categoryId, name);
        } catch (DuplicateKeyException e) {
            throw new CatalogValidationException(
                "A subcategory named '" + name + "' already exists in that category");
        }
        log.info("catalog subcategory created id={} categoryId={}", id, categoryId);
        return SubcategoryResponse.from(loadSubcategory(id));
    }

    @Transactional(readOnly = true)
    public SubcategoryResponse getSubcategory(UUID id) {
        return SubcategoryResponse.from(loadSubcategory(id));
    }

    // ── Product family ─────────────────────────────────────────────────────

    @Transactional
    public ProductFamilyResponse createProductFamily(ProductFamilyRequest request) {
        String name = requireName(request == null ? null : request.name(), "name", MAX_NAME_LENGTH);
        UUID brandId = request == null ? null : request.brandId();
        requireReference(brandId, brandRepository::existsById, "Brand");

        UUID id = UUID.randomUUID();
        productFamilyRepository.insert(id, brandId, name);
        log.info("catalog product family created id={} brandId={}", id, brandId);
        return ProductFamilyResponse.from(loadProductFamily(id));
    }

    @Transactional(readOnly = true)
    public ProductFamilyResponse getProductFamily(UUID id) {
        return ProductFamilyResponse.from(loadProductFamily(id));
    }

    // ── Product ────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (request == null) {
            throw new CatalogValidationException("A product payload is required");
        }
        String canonicalName = requireName(request.canonicalName(), "canonicalName", MAX_CANONICAL_NAME_LENGTH);

        requireReference(request.brandId(), brandRepository::existsById, "Brand");
        requireReference(request.manufacturerId(), manufacturerRepository::existsById, "Manufacturer");
        requireReference(request.categoryId(), categoryRepository::existsById, "Category");
        requireReference(request.subcategoryId(), subcategoryRepository::existsById, "Subcategory");
        requireReference(request.productFamilyId(), productFamilyRepository::existsById, "Product family");

        String gtin = normalizeGtin(request.gtin());
        if (gtin != null && productRepository.findByGtin(gtin).isPresent()) {
            throw new GtinAlreadyAssignedException(gtin);
        }
        BigDecimal packageQuantity = normalizePackageQuantity(request.packageQuantity());
        PackageUnit packageUnit = parseEnum(PackageUnit.class, request.packageUnit(), "packageUnit");
        PackagingType packagingType = parseEnum(PackagingType.class, request.packagingType(), "packagingType");

        UUID id = UUID.randomUUID();
        Product product = new Product(id, canonicalName, request.brandId(), request.manufacturerId(),
            request.categoryId(), request.subcategoryId(), request.productFamilyId(),
            gtin, packageQuantity, packageUnit, packagingType, null, null);
        try {
            productRepository.insert(product);
        } catch (DuplicateKeyException e) {
            // Backstop for the GTIN unique index under a race.
            throw new GtinAlreadyAssignedException(gtin);
        }
        log.info("catalog product created id={} gtin={}", id, gtin);
        return ProductResponse.from(loadProduct(id));
    }

    /** By canonical id. Throws {@link CatalogEntityNotFoundException} (404) when absent. */
    @Transactional(readOnly = true)
    public ProductResponse findProductById(UUID id) {
        return ProductResponse.from(loadProduct(id));
    }

    /** By canonical GTIN — at most one product. Empty when no product carries that GTIN. */
    @Transactional(readOnly = true)
    public Optional<ProductResponse> findProductByGtin(String rawGtin) {
        String gtin = normalizeGtin(rawGtin);
        if (gtin == null) {
            return Optional.empty();
        }
        return productRepository.findByGtin(gtin).map(ProductResponse::from);
    }

    /**
     * Deterministic, case-insensitive substring search on the canonical name
     * (M004 §17). No fuzzy matching. A blank query returns nothing.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) {
            return List.of();
        }
        return productRepository.searchByName(q).stream().map(ProductResponse::from).toList();
    }

    // ── loaders ────────────────────────────────────────────────────────────

    private Manufacturer loadManufacturer(UUID id) {
        return manufacturerRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Manufacturer", id));
    }

    private Brand loadBrand(UUID id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Brand", id));
    }

    private Category loadCategory(UUID id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Category", id));
    }

    private Subcategory loadSubcategory(UUID id) {
        return subcategoryRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Subcategory", id));
    }

    private ProductFamily loadProductFamily(UUID id) {
        return productFamilyRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Product family", id));
    }

    private Product loadProduct(UUID id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new CatalogEntityNotFoundException("Product", id));
    }

    // ── validation helpers ─────────────────────────────────────────────────

    /**
     * Trims edge whitespace and enforces presence and length. Names are NOT
     * lower-cased or de-accented (M004 §15) — the trimmed value is stored as-is.
     */
    private static String requireName(String raw, String field, int maxLength) {
        String value = raw == null ? null : raw.strip();
        if (value == null || value.isEmpty()) {
            throw new CatalogValidationException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new CatalogValidationException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    private interface ExistsCheck {
        boolean test(UUID id);
    }

    private static void requireReference(UUID id, ExistsCheck exists, String entityLabel) {
        if (id != null && !exists.test(id)) {
            throw new CatalogEntityNotFoundException(entityLabel, id);
        }
    }

    /** {@code null} / blank → {@code null}; otherwise must be 8–14 digits and never "SEM GTIN". */
    private static String normalizeGtin(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (!GTIN.matcher(value).matches()) {
            throw new CatalogValidationException(
                "gtin must be 8 to 14 digits (the fiscal \"SEM GTIN\" placeholder is not a canonical GTIN)");
        }
        return value;
    }

    private static BigDecimal normalizePackageQuantity(BigDecimal raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value = raw.setScale(PACKAGE_QUANTITY_SCALE, RoundingMode.HALF_UP);
        if (value.signum() <= 0) {
            throw new CatalogValidationException("packageQuantity must be greater than zero");
        }
        return value;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String allowed = String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
                .map(Enum::name).toList());
            throw new CatalogValidationException(field + " must be one of: " + allowed);
        }
    }
}
