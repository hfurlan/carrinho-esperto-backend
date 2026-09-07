package br.com.carrinhoesperto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Cross-origin configuration for the browser frontend.
 *
 * <p>Origins are an explicit allow-list — never {@code *}. The default is the
 * local Vite dev server; production origins are supplied through
 * {@code WEB_CORS_ALLOWED_ORIGINS}.
 *
 * @param allowedOrigins exact scheme://host[:port] entries the SPA may be served from
 */
@ConfigurationProperties(prefix = "web.cors")
public record CorsProperties(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
