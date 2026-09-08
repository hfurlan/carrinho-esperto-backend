package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.catalog.CatalogService;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeRequest;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeResponse;
import br.com.carrinhoesperto.resolution.dto.StoreProductCodeUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Curates the store vocabulary (M007 §9, §13): explicit, admin-only creation of
 * {@code (store, cProd) -> Product} mappings. There is no automatic learning —
 * a mapping is only ever created here.
 *
 * <p>Owns the domain validation: the store id must be a 14-digit CNPJ, the code
 * must be non-blank, the product must exist, and a {@code (store, code)} already
 * mapped elsewhere is a hard conflict ({@code 409}), never an overwrite.
 */
@Service
public class StoreProductCodeService {

    private static final Logger log = LoggerFactory.getLogger(StoreProductCodeService.class);

    private final StoreProductCodeRepository repository;
    private final CatalogService catalogService;

    StoreProductCodeService(StoreProductCodeRepository repository, CatalogService catalogService) {
        this.repository = repository;
        this.catalogService = catalogService;
    }

    @Transactional
    public StoreProductCodeResponse create(StoreProductCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A store product code payload is required");
        }
        String storeId = requireCnpj(request.storeId());
        String code = requireCode(request.code());
        UUID productId = requireProduct(request.canonicalProductId());

        repository.findByStoreAndCode(storeId, code).ifPresent(existing -> {
            throw new StoreProductCodeConflictException(storeId, code, existing.canonicalProductId());
        });

        UUID id = UUID.randomUUID();
        repository.insert(id, storeId, code, productId);
        log.info("store product code created id={} storeId={} code={} productId={}", id, storeId, code, productId);
        return load(id);
    }

    @Transactional
    public StoreProductCodeResponse update(UUID id, StoreProductCodeUpdateRequest request) {
        repository.findById(id).orElseThrow(() -> new StoreProductCodeNotFoundException(id));
        UUID productId = requireProduct(request == null ? null : request.canonicalProductId());
        repository.updateProduct(id, productId);
        log.info("store product code updated id={} productId={}", id, productId);
        return load(id);
    }

    @Transactional
    public void delete(UUID id) {
        if (repository.delete(id) == 0) {
            throw new StoreProductCodeNotFoundException(id);
        }
        // Items resolved via this mapping keep their (now stale) resolution until
        // an admin re-runs resolution for their document (M007 §11).
        log.info("store product code deleted id={}", id);
    }

    @Transactional(readOnly = true)
    public List<StoreProductCodeResponse> list(String storeIdFilter, String codeFilter) {
        String normalizedStore = (storeIdFilter == null || storeIdFilter.isBlank())
            ? null : digitsOnly(storeIdFilter);
        return repository.findFiltered(normalizedStore, codeFilter).stream()
            .map(this::toResponse)
            .toList();
    }

    // ── validation ────────────────────────────────────────────────────────

    private static String requireCnpj(String raw) {
        String digits = digitsOnly(raw);
        if (digits == null || digits.length() != 14) {
            throw new IllegalArgumentException("storeId must be a 14-digit CNPJ");
        }
        return digits;
    }

    private static String requireCode(String raw) {
        String code = raw == null ? null : raw.strip();
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return code;
    }

    private UUID requireProduct(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("canonicalProductId is required");
        }
        // Throws CatalogEntityNotFoundException (404) when the product does not exist.
        return catalogService.findProductById(productId).id();
    }

    private StoreProductCodeResponse load(UUID id) {
        StoreProductCode mapping = repository.findById(id)
            .orElseThrow(() -> new IllegalStateException("store_product_code row missing after write: " + id));
        return toResponse(mapping);
    }

    private StoreProductCodeResponse toResponse(StoreProductCode mapping) {
        String name = catalogService.findProductById(mapping.canonicalProductId()).canonicalName();
        return StoreProductCodeResponse.from(mapping, name);
    }

    private static String digitsOnly(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
