package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrCodeUrlException;
import br.com.carrinhoesperto.fiscal.ingestion.InvalidQrPayloadException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Allow-lists and dissects São Paulo NFC-e QR code URLs before any outbound
 * request is made (M003 §6). The import endpoint takes a user-supplied URL, so
 * this is the SSRF boundary: only the exact official SEFAZ-SP consultation
 * endpoint, over HTTPS, on the standard port, with no embedded credentials, and
 * carrying a 44-digit access key, is ever fetched.
 */
@Component
public class SaoPauloNfceQrCodeValidator {

    /** Official SEFAZ-SP NFC-e public-consultation host. Exact match, no subdomains. */
    static final String HOST = "www.nfce.fazenda.sp.gov.br";

    /** Consultation page path (compared lower-cased; ASP.NET treats it case-insensitively). */
    static final String PATH = "/nfceconsultapublica/paginas/consultaqrcode.aspx";

    /**
     * Same path in SEFAZ's canonical casing. Used when we build an outbound URL:
     * SEFAZ 302-redirects a lower-cased first segment to this form, so emitting it
     * up front usually avoids the redirect entirely.
     */
    static final String PATH_CANONICAL = "/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx";

    /**
     * Short entry path printed on current São Paulo NFC-e QR codes
     * ({@code https://www.nfce.fazenda.sp.gov.br/qrcode?p=…}). SEFAZ redirects it
     * to {@link #PATH} carrying the same {@code p}; we canonicalise to {@link #PATH}
     * ourselves ({@link #canonicalConsultationUri}) so the outbound fetch still
     * targets the exact consultation page and never has to follow a redirect.
     */
    static final String ENTRY_PATH = "/qrcode";

    private static final Pattern ACCESS_KEY = Pattern.compile("\\d{44}");

    /**
     * Cheap, non-throwing check used by {@link NfceProvider#supports(URI)}:
     * is this an http(s) URL on the SEFAZ-SP consultation host and path? Scheme
     * strictness (HTTPS-only) is enforced later by {@link #extractAccessKey};
     * this way an {@code http://} SEFAZ-SP URL is reported as a bad URL (400),
     * not as an unsupported provider (422).
     */
    public boolean isSaoPauloConsultationUrl(URI uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        return scheme != null
            && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
            && HOST.equalsIgnoreCase(uri.getHost())
            && isConsultationOrEntryPath(uri.getPath());
    }

    private static boolean isConsultationOrEntryPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return PATH.equals(normalized) || ENTRY_PATH.equals(normalized);
    }

    /**
     * Returns the consultation URI in canonical form: any accepted path (the
     * short {@link #ENTRY_PATH} or a differently-cased consultation path) is
     * rebuilt on {@link #PATH_CANONICAL}, keeping scheme, host, port and the raw
     * query verbatim. A URI already in canonical form is returned unchanged. The
     * result still passes {@link #extractAccessKey}.
     */
    public URI canonicalConsultationUri(URI uri) {
        if (uri == null || uri.getPath() == null || !isConsultationOrEntryPath(uri.getPath())) {
            return uri;
        }
        if (PATH_CANONICAL.equals(uri.getPath())) {
            return uri;
        }
        StringBuilder sb = new StringBuilder()
            .append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            sb.append(':').append(uri.getPort());
        }
        sb.append(PATH_CANONICAL);
        if (uri.getRawQuery() != null) {
            sb.append('?').append(uri.getRawQuery());
        }
        return URI.create(sb.toString());
    }

    /**
     * Fully validates the URL and returns the 44-digit NFC-e access key from the
     * {@code p} parameter.
     *
     * @throws InvalidQrCodeUrlException the URL is not HTTPS, not the SEFAZ-SP
     *         host/path, uses a non-standard port, or embeds credentials
     * @throws InvalidQrPayloadException {@code p} is absent or carries no valid access key
     */
    public String extractAccessKey(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidQrCodeUrlException();
        }
        if (!HOST.equalsIgnoreCase(uri.getHost())) {
            throw new InvalidQrCodeUrlException();
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new InvalidQrCodeUrlException();
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidQrCodeUrlException();
        }
        if (!isConsultationOrEntryPath(uri.getPath())) {
            throw new InvalidQrCodeUrlException();
        }

        String p = firstQueryValue(uri, "p");
        if (p == null || p.isBlank()) {
            throw new InvalidQrPayloadException();
        }
        String decoded = URLDecoder.decode(p, StandardCharsets.UTF_8);
        String firstField = decoded.split("\\|", -1)[0].strip();
        if (!ACCESS_KEY.matcher(firstField).matches()) {
            throw new InvalidQrPayloadException();
        }
        return firstField;
    }

    /**
     * Defense in depth against DNS-based SSRF: rejects a host that resolves to a
     * loopback, link-local (incl. the {@code 169.254.169.254} cloud metadata
     * address), site-local, wildcard or multicast address. Called by
     * {@link SefazSpHttpClient} immediately before opening the connection.
     *
     * @throws InvalidQrCodeUrlException the host resolves to a non-public address
     * @throws java.net.UnknownHostException wrapped by the caller as "SEFAZ unavailable"
     */
    public void assertHostResolvesToPublicAddress(URI uri) throws UnknownHostException {
        String host = uri.getHost();
        if (host == null) {
            throw new InvalidQrCodeUrlException();
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
                throw new InvalidQrCodeUrlException();
            }
        }
    }

    private static String firstQueryValue(URI uri, String name) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (key.equals(name)) {
                return eq >= 0 ? pair.substring(eq + 1) : "";
            }
        }
        return null;
    }
}
