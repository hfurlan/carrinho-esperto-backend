package br.com.carrinhoesperto.config;

import br.com.carrinhoesperto.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures every authenticated Cognito identity has a local {@code app_user} row.
 *
 * <p>Wired into the Spring Security chain after authentication and authorization
 * (see {@link SecurityConfig}), so it only ever runs for a request whose identity
 * comes from a validated JWT — never from a client-supplied value. Unauthenticated
 * requests are rejected before reaching this filter. Provisioning is idempotent,
 * so running it on every authenticated request is safe.
 */
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserService userService;

    public UserProvisioningFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            userService.currentUser();
        }
        filterChain.doFilter(request, response);
    }
}
