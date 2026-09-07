package br.com.carrinhoesperto.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import br.com.carrinhoesperto.session.AnonymousSessionService;
import br.com.carrinhoesperto.service.UserService;

/**
 * Stateless OAuth2 Resource Server configuration.
 *
 * Every request except the health probe must carry a valid Cognito-issued JWT
 * access token. Identity is taken exclusively from the validated token — never
 * from a request parameter or body. Add per-route authority rules (e.g. an admin
 * group gate via {@link CognitoGroupsAuthoritiesConverter}) in
 * {@code authorizeHttpRequests} below as the app grows; ownership authorization
 * stays in the service layer.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({CognitoProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            UserService userService, AnonymousSessionService anonymousSessionService) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new CognitoGroupsAuthoritiesConverter());

        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                // Start an anonymous trial session (M006.1). The only
                // unauthenticated write; returns a one-time X-Anon-Session token.
                .requestMatchers(HttpMethod.POST, "/api/anonymous-sessions").permitAll()
                // Admin inspection surface (M006). ROLE_<GROUP> comes from the
                // Cognito `cognito:groups` claim via CognitoGroupsAuthoritiesConverter;
                // `admin` is the configured group (cognito.admin-group). An
                // anonymous trial caller only holds ROLE_TRIAL, so it gets 403 here.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(authenticationConverter)))
            // Anonymous trial (M006.1): authenticate a valid X-Anon-Session token
            // into the context before the bearer-token filter runs. A real
            // Cognito bearer token always wins; an unknown token is left
            // unauthenticated (→ 401), never silently downgraded.
            .addFilterBefore(new TrialSessionAuthenticationFilter(anonymousSessionService),
                BearerTokenAuthenticationFilter.class)
            // After authentication + authorization: mirror the validated Cognito
            // identity into a local app_user row (idempotent). No-op for an
            // anonymous trial caller (its app_user row already exists).
            .addFilterAfter(new UserProvisioningFilter(userService), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * JWT decoder backed by the Cognito JWKS endpoint, validating signature,
     * token lifetime and the {@code iss} claim. Using an explicit JWKS URI plus
     * issuer validator (rather than OIDC discovery) keeps a single configuration
     * shape working for both MiniStack and real AWS Cognito.
     */
    @Bean
    public JwtDecoder jwtDecoder(CognitoProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(properties.resolvedJwkSetUri())
            .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtIssuerValidator(properties.issuerUri())
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * CORS for the browser frontend. Explicit origin allow-list from
     * {@link CorsProperties} — never {@code *}. The API uses bearer tokens, not
     * cookies, so credentials are not allowed.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Anon-Session"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
