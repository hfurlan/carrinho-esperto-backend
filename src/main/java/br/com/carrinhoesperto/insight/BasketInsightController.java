package br.com.carrinhoesperto.insight;

import br.com.carrinhoesperto.insight.dto.BasketInsightResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Basket insight for one of the caller's receipts (M006.1,
 * docs/basket-insight.md): {@code GET /api/fiscal-documents/{documentId}/insight}.
 *
 * <p>Sits on the same base path as {@code DocumentResolutionController},
 * mirroring how M006 hung document-level resolution off the fiscal document.
 * Works for both an authenticated user and an anonymous trial session; ownership
 * is enforced in {@link BasketInsightService} (404 when the document is not the
 * caller's).
 */
@RestController
@RequestMapping("/api/fiscal-documents")
public class BasketInsightController {

    private final BasketInsightService basketInsightService;

    BasketInsightController(BasketInsightService basketInsightService) {
        this.basketInsightService = basketInsightService;
    }

    @GetMapping("/{documentId}/insight")
    public BasketInsightResponse insight(@PathVariable UUID documentId) {
        return basketInsightService.forOwnedDocument(documentId);
    }
}
