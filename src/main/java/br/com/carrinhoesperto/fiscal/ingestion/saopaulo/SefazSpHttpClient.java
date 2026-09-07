package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.ingestion.SefazUnavailableException;
import br.com.carrinhoesperto.fiscal.ingestion.SefazUnexpectedResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

/**
 * Single-purpose HTTP adapter for the São Paulo SEFAZ NFC-e public consultation
 * (M003 §8). It is intentionally <em>not</em> a general HTTP client:
 *
 * <ul>
 *   <li>the target URL is re-validated against the allow-list on every call
 *       ({@link SaoPauloNfceQrCodeValidator#extractAccessKey});</li>
 *   <li>the host is checked not to resolve to a private/loopback address;</li>
 *   <li>the HTTP client never auto-follows redirects; at most <strong>one</strong>
 *       redirect is followed manually, and only when its {@code Location} is
 *       itself an allow-listed SEFAZ-SP consultation URL that is re-checked for a
 *       public address (SEFAZ 302-redirects a lower-cased path to its canonical
 *       casing);</li>
 *   <li>connect and read timeouts are always set — no indefinite waits;</li>
 *   <li>the response body is read up to a hard byte cap;</li>
 *   <li>nothing from the response body is logged.</li>
 * </ul>
 *
 * No retry logic — one controlled request per call.
 */
@Component
public class SefazSpHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SefazSpHttpClient.class);

    private final SefazSpProperties properties;
    private final SaoPauloNfceQrCodeValidator validator;
    private final HttpClient httpClient;

    SefazSpHttpClient(SefazSpProperties properties, SaoPauloNfceQrCodeValidator validator) {
        this.properties = properties;
        this.validator = validator;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /**
     * Fetches the consultation page for {@code uri} and returns its raw bytes
     * (charset detection is left to the parser).
     *
     * @throws br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException the URL is not an allowed SEFAZ-SP URL
     * @throws SefazUnavailableException connection/timeout/DNS failure or upstream 5xx/429
     * @throws SefazUnexpectedResponseException redirect, other non-200 status, wrong content type, or oversized body
     */
    public byte[] fetch(URI uri) {
        HttpResponse<InputStream> response = requestAllowListed(uri);

        // SEFAZ 302-redirects a lower-cased path to its canonical casing. Follow
        // that once — and only to another allow-listed SEFAZ-SP consultation URL.
        if (isRedirect(response.statusCode())) {
            URI location = redirectTarget(uri, response);
            drainQuietly(response);
            if (!validator.isSaoPauloConsultationUrl(location)) {
                log.warn("SEFAZ-SP redirect points off the allow-list: {}", location);
                throw new SefazUnexpectedResponseException("redirect-off-allowlist");
            }
            log.info("SEFAZ-SP following one redirect -> {}", location);
            response = requestAllowListed(location);
            if (isRedirect(response.statusCode())) {
                log.warn("SEFAZ-SP redirected again (status {})", response.statusCode());
                throw new SefazUnexpectedResponseException("redirect-loop:" + response.statusCode());
            }
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (response.statusCode() != 200) {
            log.warn("SEFAZ-SP consultation returned status {} (content-type={})",
                response.statusCode(), contentType);
        }
        checkStatus(response.statusCode());
        checkContentType(contentType);

        try (InputStream body = response.body()) {
            byte[] bytes = readLimited(body, properties.maxResponseBytes());
            log.info("SEFAZ-SP consultation fetched {} bytes (content-type={})", bytes.length, contentType);
            return bytes;
        } catch (IOException e) {
            log.warn("SEFAZ-SP consultation body read failed");
            throw new SefazUnavailableException("body-read");
        }
    }

    /** Validates {@code uri} against the allow-list + public-address check, then GETs it once. */
    private HttpResponse<InputStream> requestAllowListed(URI uri) {
        // Defense in depth: even though the provider already validated, never let
        // this client be pointed at an arbitrary or internal address.
        validator.extractAccessKey(uri);
        try {
            validator.assertHostResolvesToPublicAddress(uri);
        } catch (UnknownHostException e) {
            log.warn("SEFAZ-SP consultation host did not resolve: {}", uri.getHost());
            throw new SefazUnavailableException("dns");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(properties.readTimeout())
            .header("Accept", "text/html")
            .header("User-Agent", properties.userAgent())
            .GET()
            .build();

        log.info("SEFAZ-SP GET {} (connectTimeout={}, readTimeout={})",
            uri, properties.connectTimeout(), properties.readTimeout());

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            log.warn("SEFAZ-SP consultation timed out");
            throw new SefazUnavailableException("timeout");
        } catch (IOException e) {
            log.warn("SEFAZ-SP consultation connection failed");
            throw new SefazUnavailableException("io");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SefazUnavailableException("interrupted");
        }
    }

    private static boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private static URI redirectTarget(URI from, HttpResponse<InputStream> response) {
        String location = response.headers().firstValue("Location")
            .orElseThrow(() -> new SefazUnexpectedResponseException("redirect-no-location"));
        try {
            URI target = URI.create(location);
            return target.isAbsolute() ? target : from.resolve(target);
        } catch (IllegalArgumentException e) {
            throw new SefazUnexpectedResponseException("redirect-bad-location");
        }
    }

    private static void drainQuietly(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            body.readAllBytes();
        } catch (IOException ignored) {
            // best effort — we are discarding a redirect's body
        }
    }

    /** 200 → ok; 3xx → unexpected; 429/5xx → unavailable; anything else → unexpected. */
    static void checkStatus(int status) {
        if (status == 200) {
            return;
        }
        if (status >= 300 && status < 400) {
            throw new SefazUnexpectedResponseException("redirect:" + status);
        }
        if (status == 429 || status >= 500) {
            throw new SefazUnavailableException("status:" + status);
        }
        throw new SefazUnexpectedResponseException("status:" + status);
    }

    private static void checkContentType(String contentType) {
        if (!contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
            log.warn("SEFAZ-SP consultation returned unexpected content-type: {}", contentType);
            throw new SefazUnexpectedResponseException("content-type");
        }
    }

    /** Reads at most {@code maxBytes}; a larger body is rejected rather than truncated. */
    static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] chunk = new byte[8 * 1024];
        int read;
        while ((read = in.read(chunk)) != -1) {
            if (buffer.size() + read > maxBytes) {
                log.warn("SEFAZ-SP consultation body exceeded {} bytes", maxBytes);
                throw new SefazUnexpectedResponseException("body-too-large");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
