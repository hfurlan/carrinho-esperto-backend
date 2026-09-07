package br.com.carrinhoesperto.fiscal.ingestion;

import br.com.carrinhoesperto.fiscal.FiscalDocumentService;
import br.com.carrinhoesperto.fiscal.FiscalDocumentService.CreateResult;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates an NFC-e import (M003 §16): validate the QR URL → pick the
 * provider → (short-circuit if already imported) → fetch + parse → hand the
 * structured {@link FiscalDocumentRequest} to the existing
 * {@link FiscalDocumentService}.
 *
 * <p>This service owns <em>no</em> persistence logic and no idempotency
 * mechanism of its own — ownership and the {@code (owner_user_id, access_key)}
 * dedupe both live in {@code FiscalDocumentService} (M002). Duplicates are never
 * identified by URL; the access key is the fiscal identity.
 */
@Service
public class NfceImportService {

    private static final Logger log = LoggerFactory.getLogger(NfceImportService.class);

    private final List<NfceProvider> providers;
    private final FiscalDocumentService fiscalDocumentService;

    NfceImportService(List<NfceProvider> providers, FiscalDocumentService fiscalDocumentService) {
        this.providers = providers;
        this.fiscalDocumentService = fiscalDocumentService;
    }

    public CreateResult importFromQrCode(NfceImportRequest request) {
        if (request == null || request.qrCodeUrl() == null || request.qrCodeUrl().isBlank()) {
            throw new InvalidQrCodeUrlException();
        }
        URI uri = parseHttpUri(request.qrCodeUrl().strip());

        NfceProvider provider = providers.stream()
            .filter(p -> p.supports(uri))
            .findFirst()
            .orElseThrow(UnsupportedNfceProviderException::new);

        String accessKey = provider.peekAccessKey(uri);

        Optional<FiscalDocumentResponse> alreadyImported =
            fiscalDocumentService.findOwnedByAccessKey(accessKey);
        if (alreadyImported.isPresent()) {
            log.info("nfce import: access key already present, returning stored document accessKey={}", accessKey);
            return new CreateResult(alreadyImported.get(), false);
        }

        FiscalDocumentRequest parsed = provider.fetchAndParse(uri);
        CreateResult result = fiscalDocumentService.create(parsed);
        log.info("nfce import: {} accessKey={}", result.created() ? "created" : "existing", accessKey);
        return result;
    }

    private static URI parseHttpUri(String raw) {
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidQrCodeUrlException();
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new InvalidQrCodeUrlException();
        }
        return uri;
    }
}
