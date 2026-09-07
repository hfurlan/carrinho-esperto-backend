package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.resolution.ProductResolutionService.ResolveOutcome;
import br.com.carrinhoesperto.resolution.dto.ResolutionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Resolution endpoints for a single fiscal item (M005 §25, §45). Authentication
 * is required and the authenticated principal must own the fiscal item —
 * enforced in {@link ProductResolutionService} via
 * {@code FiscalDocumentService.findOwnedFiscalItem}.
 *
 * <p>An {@code UNRESOLVED} outcome is a normal {@code 200}/{@code 201} response,
 * not an error (M005 §32).
 */
@RestController
@RequestMapping("/api/fiscal-items")
public class ResolutionController {

    private final ProductResolutionService resolutionService;

    ResolutionController(ProductResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    /**
     * Resolves this user's fiscal item against the canonical catalog by exact
     * GTIN. Idempotent — {@code 201} on first resolution, {@code 200} when a
     * resolution already existed for the item.
     */
    @PostMapping("/{fiscalItemId}/resolution")
    public ResponseEntity<ResolutionResponse> resolve(@PathVariable UUID fiscalItemId) {
        ResolveOutcome outcome = resolutionService.resolveFiscalItem(fiscalItemId);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(outcome.resolution());
    }

    /** Reads the existing resolution for this user's fiscal item; {@code 404} if it has none yet. */
    @GetMapping("/{fiscalItemId}/resolution")
    public ResolutionResponse get(@PathVariable UUID fiscalItemId) {
        return resolutionService.getResolution(fiscalItemId);
    }
}
