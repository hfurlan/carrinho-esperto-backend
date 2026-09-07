package br.com.carrinhoesperto.fiscal.ingestion;

import br.com.carrinhoesperto.fiscal.FiscalDocumentService.CreateResult;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NFC-e import endpoint (M003 §16): {@code POST /api/fiscal-documents/import}
 * with {@code { "qrCodeUrl": "https://…" }}. Delegates to
 * {@link NfceImportService}; no HTTP-to-SEFAZ or persistence logic lives here.
 *
 * <p>Requires authentication. Same idempotency as the M002 create endpoint:
 * {@code 201 Created} on first import, {@code 200 OK} returning the stored
 * document on a repeat (keyed on the NFC-e access key, per owner).
 */
@RestController
@RequestMapping("/api/fiscal-documents")
public class NfceImportController {

    private static final Logger log = LoggerFactory.getLogger(NfceImportController.class);

    private final NfceImportService nfceImportService;

    NfceImportController(NfceImportService nfceImportService) {
        this.nfceImportService = nfceImportService;
    }

    @PostMapping("/import")
    public ResponseEntity<FiscalDocumentResponse> importFromQrCode(
            @RequestBody(required = false) NfceImportRequest request) {
        // Verbatim, in brackets so trailing/leading whitespace is visible. The
        // QR payload is a link to a public fiscal consultation (access key +
        // version + signature hash) — it carries no consumer data.
        log.info("POST /api/fiscal-documents/import received qrCodeUrl=[{}]",
            request == null ? null : request.qrCodeUrl());
        CreateResult result = nfceImportService.importFromQrCode(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        log.info("POST /api/fiscal-documents/import -> {} id={}", status, result.document().id());
        return ResponseEntity.status(status).body(result.document());
    }
}
