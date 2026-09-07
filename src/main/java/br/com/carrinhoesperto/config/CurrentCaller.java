package br.com.carrinhoesperto.config;

import br.com.carrinhoesperto.model.User;
import br.com.carrinhoesperto.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single access point to the current request's owner — the {@code app_user.id}
 * that owns its Fiscal / Dashboard / Resolution / Insight data.
 *
 * <p>Resolves two kinds of caller (M006.1):
 * <ul>
 *   <li>a validated Cognito JWT — the local user provisioned by
 *       {@link UserProvisioningFilter} for the {@code sub} claim;</li>
 *   <li>an anonymous trial session — the {@code app_user} id carried by a
 *       {@link TrialSessionAuthentication} (see docs/anonymous-trial.md).</li>
 * </ul>
 *
 * <p>The identity is always taken from the security context, never from a
 * request parameter or body. Application services obtain the owner id through
 * {@code UserService.currentUserId()}, which delegates here.
 */
@Component
public class CurrentCaller {

    private final AuthenticatedUser authenticatedUser;
    private final UserRepository userRepository;

    CurrentCaller(AuthenticatedUser authenticatedUser, UserRepository userRepository) {
        this.authenticatedUser = authenticatedUser;
        this.userRepository = userRepository;
    }

    /**
     * @return the owner {@code app_user.id} for the current request
     * @throws IllegalStateException if the request has no recognised caller
     */
    public UUID ownerUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof TrialSessionAuthentication trial) {
            return trial.appUserId();
        }
        if (authentication instanceof JwtAuthenticationToken) {
            String subject = authenticatedUser.cognitoSubject();
            return userRepository.findByCognitoSubject(subject)
                .map(User::id)
                .orElseThrow(() -> new IllegalStateException(
                    "No local user for the authenticated subject; provisioning did not run"));
        }
        throw new IllegalStateException("No authenticated caller in the security context");
    }

    /** {@code true} when the current request is an anonymous trial session. */
    public boolean isAnonymousTrial() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof TrialSessionAuthentication;
    }
}
