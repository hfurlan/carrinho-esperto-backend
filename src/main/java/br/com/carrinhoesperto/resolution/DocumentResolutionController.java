package br.com.carrinhoesperto.resolution;

import br.com.carrinhoesperto.resolution.dto.DocumentItemResolutionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Document-level resolution (M006): resolve or read the resolution state of
 * every item of one of the caller's fiscal documents in a single call. The
 * frontend calls {@code POST} right after an import and behind a "re-check"
 * button, and {@code GET} to render the receipt-detail badges.
 *
 * <p>Ownership is enforced in {@link ProductResolutionService} (the document
 * must be the authenticated caller's). {@code GET /api/fiscal-documents/{id}}
 * itself is unchanged and still does not auto-resolve.
 */
@RestController
@RequestMapping("/api/fiscal-documents")
public class DocumentResolutionController {

    private final ProductResolutionService resolutionService;

    DocumentResolutionController(ProductResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @PostMapping("/{documentId}/resolutions")
    public List<DocumentItemResolutionResponse> resolveAll(@PathVariable UUID documentId) {
        return resolutionService.resolveOwnedDocument(documentId);
    }

    @GetMapping("/{documentId}/resolutions")
    public List<DocumentItemResolutionResponse> list(@PathVariable UUID documentId) {
        return resolutionService.getOwnedDocumentResolutions(documentId);
    }
}
