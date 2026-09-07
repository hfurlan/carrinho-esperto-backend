package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunables for the outbound call to the São Paulo SEFAZ NFC-e public
 * consultation. Defaults are conservative; override per environment with
 * {@code nfce.sao-paulo.*}.
 *
 * @param connectTimeout  TCP/TLS connect timeout
 * @param readTimeout     overall request timeout (no indefinite waits)
 * @param maxResponseBytes hard cap on the consultation HTML that will be read
 * @param userAgent        {@code User-Agent} sent to SEFAZ
 */
@ConfigurationProperties(prefix = "nfce.sao-paulo")
public record SefazSpProperties(
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("10s") Duration readTimeout,
    @DefaultValue("3145728") int maxResponseBytes,
    @DefaultValue("CarrinhoEsperto/0.1 (NFC-e fiscal consultation)") String userAgent
) {

    public SefazSpProperties {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = Duration.ofSeconds(10);
        }
        if (maxResponseBytes <= 0) {
            maxResponseBytes = 3_145_728;
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "CarrinhoEsperto/0.1 (NFC-e fiscal consultation)";
        }
    }
}
