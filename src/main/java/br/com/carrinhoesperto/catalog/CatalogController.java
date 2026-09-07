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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Minimal API over the Catalog bounded context (M004 §18). Catalog data is
 * platform-owned canonical knowledge: these endpoints require authentication but
 * are not user-scoped, and there is no catalog-management UI. The lookup
 * endpoints (`GET /products/{id}`, `?gtin=`, `?name=`) are what M005 Resolution
 * will build on.
 *
 * <p>No DELETE — canonical products will be referenced by future resolutions,
 * purchases and prices (M004 §21).
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // ── Manufacturer ───────────────────────────────────────────────────────

    @PostMapping("/manufacturers")
    @ResponseStatus(HttpStatus.CREATED)
    public ManufacturerResponse createManufacturer(@RequestBody(required = false) ManufacturerRequest request) {
        return catalogService.createManufacturer(request);
    }

    @GetMapping("/manufacturers/{id}")
    public ManufacturerResponse getManufacturer(@PathVariable UUID id) {
        return catalogService.getManufacturer(id);
    }

    // ── Brand ──────────────────────────────────────────────────────────────

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse createBrand(@RequestBody(required = false) BrandRequest request) {
        return catalogService.createBrand(request);
    }

    @GetMapping("/brands/{id}")
    public BrandResponse getBrand(@PathVariable UUID id) {
        return catalogService.getBrand(id);
    }

    // ── Category / Subcategory ─────────────────────────────────────────────

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@RequestBody(required = false) CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @GetMapping("/categories/{id}")
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return catalogService.getCategory(id);
    }

    @PostMapping("/subcategories")
    @ResponseStatus(HttpStatus.CREATED)
    public SubcategoryResponse createSubcategory(@RequestBody(required = false) SubcategoryRequest request) {
        return catalogService.createSubcategory(request);
    }

    @GetMapping("/subcategories/{id}")
    public SubcategoryResponse getSubcategory(@PathVariable UUID id) {
        return catalogService.getSubcategory(id);
    }

    // ── Product family ─────────────────────────────────────────────────────

    @PostMapping("/product-families")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductFamilyResponse createProductFamily(@RequestBody(required = false) ProductFamilyRequest request) {
        return catalogService.createProductFamily(request);
    }

    @GetMapping("/product-families/{id}")
    public ProductFamilyResponse getProductFamily(@PathVariable UUID id) {
        return catalogService.getProductFamily(id);
    }

    // ── Product ────────────────────────────────────────────────────────────

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@RequestBody(required = false) ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return catalogService.findProductById(id);
    }

    /**
     * {@code ?gtin=} → the canonical product for that GTIN (0 or 1 element);
     * {@code ?name=} → deterministic case-insensitive substring match. Exactly
     * one of the two parameters must be given.
     */
    @GetMapping("/products")
    public List<ProductResponse> queryProducts(
            @RequestParam(required = false) String gtin,
            @RequestParam(required = false) String name) {
        if (gtin != null && name != null) {
            throw new CatalogValidationException("provide only one of 'gtin' or 'name'");
        }
        if (gtin != null) {
            return catalogService.findProductByGtin(gtin).map(List::of).orElseGet(List::of);
        }
        if (name != null) {
            return catalogService.searchProducts(name);
        }
        throw new CatalogValidationException("provide either 'gtin' or 'name'");
    }
}
