package br.com.carrinhoesperto.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Single access point to the authenticated caller's identity.
 *
 * Application code must obtain the user identity through this component instead
 * of reading the {@link SecurityContextHolder} directly, and must never trust an
 * identifier supplied in the request. The Cognito {@code sub} claim is the stable
 * external identity key (persisted as {@code app_user.cognito_subject}).
 */
@Component
public class AuthenticatedUser {

    /**
     * @return the Cognito subject ({@code sub}) of the current request's validated JWT.
     * @throws IllegalStateException if there is no authenticated JWT in the context.
     */
    public String cognitoSubject() {
        return jwt().getSubject();
    }

    /**
     * @return the Cognito username claim of the current request, or {@code null} when absent.
     */
    public String username() {
        return jwt().getClaimAsString("username");
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated user in the security context");
        }
        return jwt;
    }
}
