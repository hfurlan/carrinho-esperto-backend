package br.com.carrinhoesperto.config;

import br.com.carrinhoesperto.session.AnonymousSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates an anonymous trial caller from the {@code X-Anon-Session} header
 * (M006.1, docs/anonymous-trial.md).
 *
 * <p>Registered before the resource server's bearer-token filter. It only acts
 * when there is no {@code Authorization: Bearer} header — a real Cognito token
 * always wins. A present-but-unknown token is left unauthenticated on purpose:
 * the request then fails the {@code authenticated()} check and the entry point
 * returns {@code 401}, rather than silently downgrading to "no session".
 */
public class TrialSessionAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Anon-Session";

    private final AnonymousSessionService anonymousSessionService;

    public TrialSessionAuthenticationFilter(AnonymousSessionService anonymousSessionService) {
        this.anonymousSessionService = anonymousSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        boolean hasBearer = StringUtils.startsWithIgnoreCase(
            request.getHeader(HttpHeaders.AUTHORIZATION), "bearer ");

        if (StringUtils.hasText(token)
                && !hasBearer
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<UUID> appUserId = anonymousSessionService.resolve(token);
            appUserId.ifPresent(id ->
                SecurityContextHolder.getContext().setAuthentication(new TrialSessionAuthentication(id)));
        }

        filterChain.doFilter(request, response);
    }
}
