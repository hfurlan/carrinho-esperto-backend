package br.com.carrinhoesperto.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.UUID;

/**
 * Authentication for an anonymous trial caller (M006.1, docs/anonymous-trial.md).
 *
 * <p>This is a <strong>genuine authenticated principal</strong> — not Spring's
 * {@code AnonymousAuthenticationToken}, which represents "no one is logged in".
 * A request carrying a valid {@code X-Anon-Session} token gets one of these, so
 * {@code .anyRequest().authenticated()} is satisfied while {@code hasRole("ADMIN")}
 * on {@code /api/admin/**} is not (the sole authority is {@code ROLE_TRIAL}).
 *
 * <p>The principal is the anonymous {@code app_user} id — the ownership key every
 * Fiscal / Dashboard / Resolution / Insight query already filters on.
 */
public final class TrialSessionAuthentication extends AbstractAuthenticationToken {

    private final UUID appUserId;

    public TrialSessionAuthentication(UUID appUserId) {
        super(AuthorityUtils.createAuthorityList("ROLE_TRIAL"));
        this.appUserId = appUserId;
        setAuthenticated(true);
    }

    /** The anonymous {@code app_user} id that owns this session's data. */
    public UUID appUserId() {
        return appUserId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return appUserId;
    }
}
