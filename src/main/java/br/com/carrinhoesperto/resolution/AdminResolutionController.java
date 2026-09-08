package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.resolution.dto.DocumentItemResolutionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin re-resolution (M007 §11): re-run resolution for every item of any fiscal
 * document — used after a store mapping is created. Reuses the same per-item
 * strategy pipeline as the consumer batch endpoint; only the document fetch is
 * admin-scoped. Gated to {@code ROLE_ADMIN} by the {@code /api/admin/**} rule.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminResolutionController {

    private final ProductResolutionService resolutionService;

    AdminResolutionController(ProductResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @PostMapping("/fiscal-documents/{documentId}/resolutions")
    public List<DocumentItemResolutionResponse> reResolve(@PathVariable UUID documentId) {
        return resolutionService.resolveDocumentForAdmin(documentId);
    }
}
