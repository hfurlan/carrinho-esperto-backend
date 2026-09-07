package br.com.carrinhoesperto.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

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
            UserService userService) throws Exception {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(new CognitoGroupsAuthoritiesConverter());

        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .decoder(jwtDecoder)
                .jwtAuthenticationConverter(authenticationConverter)))
            // After authentication + authorization: mirror the validated Cognito
            // identity into a local app_user row (idempotent).
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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
