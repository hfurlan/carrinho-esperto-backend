package br.com.carrinhoesperto.fiscal.ingestion;

import br.com.carrinhoesperto.config.CurrentCaller;
import br.com.carrinhoesperto.fiscal.FiscalDocumentService;
import br.com.carrinhoesperto.fiscal.FiscalDocumentService.CreateResult;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentRequest;
import br.com.carrinhoesperto.fiscal.dto.FiscalDocumentResponse;
import br.com.carrinhoesperto.fiscal.ingestion.dto.NfceImportRequest;
import br.com.carrinhoesperto.session.AnonymousSessionService;
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
    private final CurrentCaller currentCaller;
    private final AnonymousSessionService anonymousSessionService;

    NfceImportService(List<NfceProvider> providers, FiscalDocumentService fiscalDocumentService,
                      CurrentCaller currentCaller, AnonymousSessionService anonymousSessionService) {
        this.providers = providers;
        this.fiscalDocumentService = fiscalDocumentService;
        this.currentCaller = currentCaller;
        this.anonymousSessionService = anonymousSessionService;
    }

    public CreateResult importFromQrCode(NfceImportRequest request) {
        log.info("nfce import: step 1/6 start");
        if (request == null || request.qrCodeUrl() == null || request.qrCodeUrl().isBlank()) {
            log.warn("nfce import: rejected - blank qrCodeUrl");
            throw new InvalidQrCodeUrlException();
        }
        String rawUrl = request.qrCodeUrl().strip();
        URI uri = parseHttpUri(rawUrl);
        log.info("nfce import: step 2/6 raw=[{}] parsed=[{}] (host={} path={} query={})",
            rawUrl, uri, uri.getHost(), uri.getPath(), uri.getRawQuery());

        NfceProvider provider = providers.stream()
            .filter(p -> p.supports(uri))
            .findFirst()
            .orElseThrow(() -> {
                log.warn("nfce import: rejected - no provider supports host={} path={}", uri.getHost(), uri.getPath());
                return new UnsupportedNfceProviderException();
            });
        log.info("nfce import: step 3/6 provider={}", provider.getClass().getSimpleName());

        String accessKey = provider.peekAccessKey(uri);
        log.info("nfce import: step 4/6 accessKey={}", accessKey);

        Optional<FiscalDocumentResponse> alreadyImported =
            fiscalDocumentService.findOwnedByAccessKey(accessKey);
        if (alreadyImported.isPresent()) {
            log.info("nfce import: step 5/6 access key already present, returning stored document accessKey={}", accessKey);
            touchAnonymousSessionActivity();
            return new CreateResult(alreadyImported.get(), false);
        }
        log.info("nfce import: step 5/6 not imported yet, fetching + parsing from SEFAZ-SP");

        FiscalDocumentRequest parsed = provider.fetchAndParse(uri);
        CreateResult result = fiscalDocumentService.create(parsed);
        log.info("nfce import: step 6/6 {} accessKey={}", result.created() ? "created" : "existing", accessKey);
        touchAnonymousSessionActivity();
        return result;
    }

    /**
     * Records activity on an anonymous trial session after a successful import
     * (M006.1) — the only write path for anonymous data, so {@code last_seen_at}
     * is kept current without writing on every anonymous read.
     */
    private void touchAnonymousSessionActivity() {
        if (currentCaller.isAnonymousTrial()) {
            anonymousSessionService.touch(currentCaller.ownerUserId());
        }
    }

    private static URI parseHttpUri(String raw) {
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            // A São Paulo NFC-e QR carries literal `|` (and sometimes spaces) in
            // the `p=` parameter — legal for a browser, rejected by java.net.URI.
            // Encode just those and retry once before giving up.
            try {
                uri = new URI(raw.replace("|", "%7C").replace(" ", "%20"));
            } catch (URISyntaxException retry) {
                throw new InvalidQrCodeUrlException();
            }
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
