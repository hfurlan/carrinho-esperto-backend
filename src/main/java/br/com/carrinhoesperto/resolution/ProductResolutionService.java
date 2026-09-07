package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.catalog.CatalogService;
import br.com.carrinhoesperto.catalog.dto.ProductResponse;
import br.com.carrinhoesperto.fiscal.FiscalDocumentService;
import br.com.carrinhoesperto.fiscal.FiscalItem;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalItemResponse;
import br.com.carrinhoesperto.resolution.dto.DocumentItemResolutionResponse;
import br.com.carrinhoesperto.resolution.dto.ResolutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application workflow for the Resolution bounded context (M005 §17).
 *
 * <p>Answers "which canonical catalog product, if any, does this user-owned
 * fiscal item refer to?" using <strong>deterministic exact-GTIN evidence
 * only</strong>. No description / fuzzy / price / brand / AI matching; no
 * automatic catalog creation.
 *
 * <p>Cross-context access is by id through the existing application services:
 * {@link FiscalDocumentService#findOwnedFiscalItem} (ownership-enforced read of
 * a user-owned fiscal item) and {@link CatalogService#findProductByGtin} (lookup
 * in the shared, platform-owned catalog). Resolution never modifies Fiscal or
 * Catalog data and never copies their aggregates.
 */
@Service
public class ProductResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ProductResolutionService.class);

    private final FiscalDocumentService fiscalDocumentService;
    private final CatalogService catalogService;
    private final ProductResolutionRepository resolutionRepository;
    private final ResolutionEvidenceRepository evidenceRepository;

    ProductResolutionService(FiscalDocumentService fiscalDocumentService,
                             CatalogService catalogService,
                             ProductResolutionRepository resolutionRepository,
                             ResolutionEvidenceRepository evidenceRepository) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.catalogService = catalogService;
        this.resolutionRepository = resolutionRepository;
        this.evidenceRepository = evidenceRepository;
    }

    /**
     * Resolves the caller's fiscal item. Idempotent: repeated calls converge on
     * a single resolution row and only rewrite it (and its evidence) when the
     * deterministic outcome actually changes.
     *
     * @throws FiscalItemNotFoundException the item does not exist or is not the caller's (404)
     */
    @Transactional
    public ResolveOutcome resolveFiscalItem(UUID fiscalItemId) {
        FiscalItem item = fiscalDocumentService.findOwnedFiscalItem(fiscalItemId)
            .orElseThrow(() -> new FiscalItemNotFoundException(fiscalItemId));

        Optional<String> gtin = selectGtin(item);
        Optional<UUID> productId = gtin
            .flatMap(catalogService::findProductByGtin)
            .map(ProductResponse::id);

        ResolutionStatus status = productId.isPresent() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
        ResolutionMethod method = productId.isPresent() ? ResolutionMethod.GTIN_EXACT : null;

        Optional<ProductResolution> existing = resolutionRepository.findByFiscalItemId(fiscalItemId);
        if (existing.isPresent()) {
            ProductResolution current = existing.get();
            boolean unchanged = current.status() == status
                && Objects.equals(current.canonicalProductId(), productId.orElse(null))
                && current.method() == method;
            if (!unchanged) {
                resolutionRepository.updateOutcome(current.id(), productId.orElse(null), status, method);
                rewriteEvidence(current.id(), status, gtin.orElse(null));
                log.info("resolution updated fiscalItemId={} status={} canonicalProductId={} method={}",
                    fiscalItemId, status, productId.orElse(null), method);
            }
            return new ResolveOutcome(load(current.id()), false);
        }

        UUID resolutionId = UUID.randomUUID();
        resolutionRepository.insert(resolutionId, fiscalItemId, productId.orElse(null), status, method);
        if (status == ResolutionStatus.RESOLVED) {
            insertGtinEvidence(resolutionId, gtin.orElseThrow());
        }
        log.info("resolution created fiscalItemId={} status={} canonicalProductId={} method={}",
            fiscalItemId, status, productId.orElse(null), method);
        return new ResolveOutcome(load(resolutionId), true);
    }

    /**
     * Reads an already-created resolution for the caller's fiscal item.
     *
     * @throws FiscalItemNotFoundException the item does not exist or is not the caller's (404)
     * @throws ResolutionNotFoundException the item exists but has not been resolved yet (404)
     */
    @Transactional(readOnly = true)
    public ResolutionResponse getResolution(UUID fiscalItemId) {
        if (fiscalDocumentService.findOwnedFiscalItem(fiscalItemId).isEmpty()) {
            throw new FiscalItemNotFoundException(fiscalItemId);
        }
        ProductResolution resolution = resolutionRepository.findByFiscalItemId(fiscalItemId)
            .orElseThrow(() -> new ResolutionNotFoundException(fiscalItemId));
        return load(resolution.id());
    }

    /**
     * Resolves every item of the caller's fiscal document (M006). Idempotent —
     * delegates to {@link #resolveFiscalItem} per item. Used right after an
     * import and behind a "re-check" action on the receipt screen.
     *
     * @throws br.com.carrinhoesperto.fiscal.FiscalDocumentNotFoundException the document is not the caller's (404)
     */
    @Transactional
    public List<DocumentItemResolutionResponse> resolveOwnedDocument(UUID documentId) {
        FiscalDocumentResponse document = fiscalDocumentService.getOwnedDetail(documentId);
        List<DocumentItemResolutionResponse> views = new ArrayList<>(document.items().size());
        for (FiscalItemResponse item : document.items()) {
            views.add(view(resolveFiscalItem(item.id()).resolution()));
        }
        return views;
    }

    /**
     * Reads the resolution state of every item of the caller's fiscal document
     * (M006). Items never resolved appear with {@code status = null}.
     *
     * @throws br.com.carrinhoesperto.fiscal.FiscalDocumentNotFoundException the document is not the caller's (404)
     */
    @Transactional(readOnly = true)
    public List<DocumentItemResolutionResponse> getOwnedDocumentResolutions(UUID documentId) {
        FiscalDocumentResponse document = fiscalDocumentService.getOwnedDetail(documentId);
        List<DocumentItemResolutionResponse> views = new ArrayList<>(document.items().size());
        for (FiscalItemResponse item : document.items()) {
            views.add(resolutionRepository.findByFiscalItemId(item.id())
                .map(this::view)
                .orElseGet(() -> new DocumentItemResolutionResponse(
                    item.id(), null, null, null, null, null)));
        }
        return views;
    }

    private DocumentItemResolutionResponse view(ResolutionResponse res) {
        return new DocumentItemResolutionResponse(res.fiscalItemId(), res.status(),
            res.canonicalProductId(), canonicalNameOf(res.canonicalProductId()), res.method(), res.updatedAt());
    }

    private DocumentItemResolutionResponse view(ProductResolution res) {
        return new DocumentItemResolutionResponse(res.fiscalItemId(), res.status().name(),
            res.canonicalProductId(), canonicalNameOf(res.canonicalProductId()),
            res.method() == null ? null : res.method().name(), res.updatedAt());
    }

    private String canonicalNameOf(UUID canonicalProductId) {
        return canonicalProductId == null ? null
            : catalogService.findProductById(canonicalProductId).canonicalName();
    }

    private void rewriteEvidence(UUID resolutionId, ResolutionStatus status, String gtin) {
        evidenceRepository.deleteByResolutionId(resolutionId);
        if (status == ResolutionStatus.RESOLVED) {
            insertGtinEvidence(resolutionId, Objects.requireNonNull(gtin));
        }
    }

    private void insertGtinEvidence(UUID resolutionId, String gtin) {
        evidenceRepository.insert(UUID.randomUUID(), resolutionId,
            ResolutionEvidenceType.GTIN, ResolutionEvidenceSource.FISCAL_ITEM, gtin);
    }

    private ResolutionResponse load(UUID resolutionId) {
        ProductResolution resolution = resolutionRepository.findById(resolutionId)
            .orElseThrow(() -> new IllegalStateException(
                "resolution row missing immediately after write: " + resolutionId));
        List<ResolutionEvidence> evidence = evidenceRepository.findByResolutionId(resolutionId);
        return ResolutionResponse.from(resolution, evidence);
    }

    /**
     * Fiscal GTIN selection rule (M005 §18): use {@code cEAN} when it is a usable
     * canonical GTIN; otherwise fall back to {@code cEANTrib}. A {@code cEAN}
     * that is a valid GTIN but matches no catalog product is still "used" — the
     * result is {@code UNRESOLVED}, with no fall-through to {@code cEANTrib}.
     * {@code "SEM GTIN"}, blank and non-numeric values are not usable.
     */
    private static Optional<String> selectGtin(FiscalItem item) {
        Optional<String> primary = Gtin.canonical(item.gtin());
        return primary.isPresent() ? primary : Gtin.canonical(item.gtinTaxable());
    }

    /** The stored resolution plus whether this call created it (201) or it already existed (200). */
    public record ResolveOutcome(ResolutionResponse resolution, boolean created) {
    }
}
