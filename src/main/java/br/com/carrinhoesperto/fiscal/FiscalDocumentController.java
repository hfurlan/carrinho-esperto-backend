package br.com.carrinhoesperto.fiscal;

import br.com.carrinhoesperto.fiscal.FiscalDocumentService.CreateResult;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The Fiscal Document core slice (M002, docs/fiscal-core.md): import a fiscal
 * document payload and read it back. No NFC-e retrieval/parsing (M003) and no
 * product canonicalization (future Resolution/Catalog) — the API persists and
 * returns fiscal truth.
 *
 * <p>Requires authentication. The owner is always the authenticated principal —
 * the controller never accepts a user id, and a user can only ever see their
 * own fiscal documents.
 */
@RestController
@RequestMapping("/api/fiscal-documents")
public class FiscalDocumentController {

    private final FiscalDocumentService fiscalDocumentService;

    FiscalDocumentController(FiscalDocumentService fiscalDocumentService) {
        this.fiscalDocumentService = fiscalDocumentService;
    }

    /**
     * Imports a fiscal document. Idempotent on the NFC-e access key for this
     * user: {@code 201 Created} on first import, {@code 200 OK} returning the
     * already-stored document on a repeat.
     */
    @PostMapping
    public ResponseEntity<FiscalDocumentResponse> create(
            @RequestBody(required = false) FiscalDocumentRequest request) {
        CreateResult result = fiscalDocumentService.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.document());
    }

    /** Every fiscal document owned by the authenticated user, newest issued first. */
    @GetMapping
    public List<FiscalDocumentSummaryResponse> list() {
        return fiscalDocumentService.listOwned();
    }

    @GetMapping("/{id}")
    public FiscalDocumentResponse get(@PathVariable UUID id) {
        return fiscalDocumentService.getOwnedDetail(id);
    }
}
