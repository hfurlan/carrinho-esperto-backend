package br.com.carrinhoesperto.fiscal.ingestion.saopaulo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the NFC-e ingestion adapter's configuration. The adapter classes
 * themselves ({@code SaoPauloNfceProvider}, {@code SefazSpHttpClient},
 * {@code SaoPauloNfceParser}, …) are component-scanned like the rest of the app.
 */
@Configuration
@EnableConfigurationProperties(SefazSpProperties.class)
public class NfceIngestionConfiguration {
}
