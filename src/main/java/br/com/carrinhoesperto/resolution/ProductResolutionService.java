package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.catalog.CatalogService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application workflow for the Resolution bounded context (M005 §17, M007 §18).
 *
 * <p>Answers "which canonical catalog product, if any, does this fiscal item
 * refer to?" by running the registered {@link ResolutionStrategy} beans in
 * precedence order and taking the first deterministic match:
 * <ol>
 *   <li>{@link GtinExactResolutionStrategy} — exact GTIN (M005)</li>
 *   <li>{@link StoreProductCodeResolutionStrategy} — curated {@code (store, cProd)} (M007)</li>
 * </ol>
 * GTIN always wins. No fuzzy / description / price / AI matching; no automatic
 * catalog creation; no automatic learning of mappings.
 *
 * <p>Cross-context access is by id through the existing application services.
 * Resolution never modifies Fiscal or Catalog data and never copies their
 * aggregates.
 */
@Service
public class ProductResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ProductResolutionService.class);

    private final FiscalDocumentService fiscalDocumentService;
    private final CatalogService catalogService;
    private final ProductResolutionRepository resolutionRepository;
    private final ResolutionEvidenceRepository evidenceRepository;
    private final List<ResolutionStrategy> strategies;

    ProductResolutionService(FiscalDocumentService fiscalDocumentService,
                             CatalogService catalogService,
                             ProductResolutionRepository resolutionRepository,
                             ResolutionEvidenceRepository evidenceRepository,
                             List<ResolutionStrategy> strategies) {
        this.fiscalDocumentService = fiscalDocumentService;
        this.catalogService = catalogService;
        this.resolutionRepository = resolutionRepository;
        this.evidenceRepository = evidenceRepository;
        this.strategies = strategies.stream()
            .sorted(Comparator.comparingInt(ResolutionStrategy::order))
            .toList();
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
        String storeId = fiscalDocumentService.findStoreIdForFiscalItem(fiscalItemId).orElse(null);
        return resolveItem(FiscalItemContext.from(item, storeId));
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
     * Re-runs resolution for every item of any fiscal document (M007 §11) — the
     * admin path after a store mapping is created. Same per-item logic and same
     * response shape as {@link #resolveOwnedDocument}, but not owner-scoped.
     *
     * @throws br.com.carrinhoesperto.fiscal.FiscalDocumentNotFoundException no such document (404)
     */
    @Transactional
    public List<DocumentItemResolutionResponse> resolveDocumentForAdmin(UUID documentId) {
        FiscalDocumentResponse document = fiscalDocumentService.findByIdForAdmin(documentId);
        String storeId = digitsOnly(document.issuer().cnpj());
        List<DocumentItemResolutionResponse> views = new ArrayList<>(document.items().size());
        for (FiscalItemResponse item : document.items()) {
            views.add(view(resolveItem(FiscalItemContext.from(item, storeId)).resolution()));
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

    // ── Core resolve, strategy-driven ──────────────────────────────────────

    /**
     * Runs the strategies in precedence order for one item and upserts its
     * single resolution row. Evidence (one row) is rewritten only when the
     * outcome actually changes — an unchanged re-resolve touches nothing.
     */
    private ResolveOutcome resolveItem(FiscalItemContext ctx) {
        UUID fiscalItemId = ctx.fiscalItemId();

        Optional<StrategyMatch> match = strategies.stream()
            .map(s -> s.match(ctx))
            .flatMap(Optional::stream)
            .findFirst();

        ResolutionStatus status = match.isPresent() ? ResolutionStatus.RESOLVED : ResolutionStatus.UNRESOLVED;
        UUID productId = match.map(StrategyMatch::canonicalProductId).orElse(null);
        ResolutionMethod method = match.map(StrategyMatch::method).orElse(null);
        EvidenceRecord evidence = match.map(StrategyMatch::evidence).orElse(null);

        Optional<ProductResolution> existing = resolutionRepository.findByFiscalItemId(fiscalItemId);
        if (existing.isPresent()) {
            ProductResolution current = existing.get();
            boolean unchanged = current.status() == status
                && Objects.equals(current.canonicalProductId(), productId)
                && current.method() == method;
            if (!unchanged) {
                resolutionRepository.updateOutcome(current.id(), productId, status, method);
                rewriteEvidence(current.id(), status, evidence);
                log.info("resolution updated fiscalItemId={} status={} canonicalProductId={} method={}",
                    fiscalItemId, status, productId, method);
            }
            return new ResolveOutcome(load(current.id()), false);
        }

        UUID resolutionId = UUID.randomUUID();
        resolutionRepository.insert(resolutionId, fiscalItemId, productId, status, method);
        if (status == ResolutionStatus.RESOLVED) {
            insertEvidence(resolutionId, Objects.requireNonNull(evidence));
        }
        log.info("resolution created fiscalItemId={} status={} canonicalProductId={} method={}",
            fiscalItemId, status, productId, method);
        return new ResolveOutcome(load(resolutionId), true);
    }

    // ── Views / helpers ───────────────────────────────────────────────────

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

    private void rewriteEvidence(UUID resolutionId, ResolutionStatus status, EvidenceRecord evidence) {
        evidenceRepository.deleteByResolutionId(resolutionId);
        if (status == ResolutionStatus.RESOLVED) {
            insertEvidence(resolutionId, Objects.requireNonNull(evidence));
        }
    }

    private void insertEvidence(UUID resolutionId, EvidenceRecord e) {
        evidenceRepository.insert(UUID.randomUUID(), resolutionId, e.type(), e.source(), e.value(), e.metadata());
    }

    private ResolutionResponse load(UUID resolutionId) {
        ProductResolution resolution = resolutionRepository.findById(resolutionId)
            .orElseThrow(() -> new IllegalStateException(
                "resolution row missing immediately after write: " + resolutionId));
        List<ResolutionEvidence> evidence = evidenceRepository.findByResolutionId(resolutionId);
        return ResolutionResponse.from(resolution, evidence);
    }

    private static String digitsOnly(String raw) {
        return raw == null ? null : raw.replaceAll("\\D", "");
    }

    /** The stored resolution plus whether this call created it (201) or it already existed (200). */
    public record ResolveOutcome(ResolutionResponse resolution, boolean created) {
    }
}
