package br.com.carrinhoesperto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Objects;

/**
 * Cognito configuration for the OAuth2 Resource Server.
 *
 * Environment-specific values are supplied through Spring profiles / environment
 * variables — never hard-coded. Local development points at MiniStack Cognito;
 * production points at the real AWS Cognito user pool. The application code that
 * consumes the validated identity does not change between environments.
 *
 * @param issuerUri  the token issuer ({@code iss} claim) that must be trusted.
 *                   MiniStack: {@code http://localhost:4566/<userPoolId>}.
 *                   AWS: {@code https://cognito-idp.<region>.amazonaws.com/<userPoolId>}.
 * @param jwkSetUri  optional explicit JWKS endpoint. When absent it is derived as
 *                   {@code <issuerUri>/.well-known/jwks.json}, which is correct for
 *                   both MiniStack and AWS Cognito.
 * @param adminGroup name of the Cognito group whose members may reach {@code /api/admin/**}.
 *                   Members carry it in the {@code cognito:groups} access-token
 *                   claim; it is mapped to the Spring authority
 *                   {@code ROLE_<ADMINGROUP>}. Defaults to {@code admin}. Not wired
 *                   into any route yet in this starter — add the rule in
 *                   {@link SecurityConfig} when an admin surface exists.
 */
@ConfigurationProperties(prefix = "cognito")
public record CognitoProperties(String issuerUri, String jwkSetUri,
                                @DefaultValue("admin") String adminGroup) {

    public CognitoProperties {
        Objects.requireNonNull(issuerUri, "cognito.issuer-uri must be configured");
        if (issuerUri.isBlank()) {
            throw new IllegalArgumentException("cognito.issuer-uri must not be blank");
        }
        if (adminGroup == null || adminGroup.isBlank()) {
            adminGroup = "admin";
        }
    }

    /**
     * The JWKS endpoint to fetch signing keys from: the explicit {@code jwkSetUri}
     * when provided, otherwise derived from the issuer.
     */
    public String resolvedJwkSetUri() {
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return jwkSetUri;
        }
        return issuerUri.replaceAll("/+$", "") + "/.well-known/jwks.json";
    }
}
