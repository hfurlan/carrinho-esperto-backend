package br.com.carrinhoesperto.service;

import br.com.carrinhoesperto.config.AuthenticatedUser;
import br.com.carrinhoesperto.config.CurrentCaller;
import br.com.carrinhoesperto.model.User;
import br.com.carrinhoesperto.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the local {@link User} for the caller identified by the validated
 * Cognito JWT, creating it on first sight.
 *
 * <p>The Cognito subject is always read from {@link AuthenticatedUser} — the single
 * identity boundary — and never accepted as a method argument, request parameter
 * or request body.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuthenticatedUser authenticatedUser;
    private final CurrentCaller currentCaller;

    UserService(UserRepository userRepository, AuthenticatedUser authenticatedUser, CurrentCaller currentCaller) {
        this.userRepository = userRepository;
        this.authenticatedUser = authenticatedUser;
        this.currentCaller = currentCaller;
    }

    /**
     * Returns the local user for the currently authenticated Cognito subject,
     * provisioning it if it does not exist yet.
     *
     * <p>Idempotent: repeated or concurrent calls for the same subject converge on
     * exactly one row ({@code INSERT ... ON CONFLICT DO NOTHING}).
     */
    @Transactional
    public User currentUser() {
        String cognitoSubject = authenticatedUser.cognitoSubject();
        userRepository.insertIfAbsent(cognitoSubject);
        userRepository.updateUsername(cognitoSubject, authenticatedUser.username());
        return userRepository.findByCognitoSubject(cognitoSubject)
            .orElseThrow(() -> new IllegalStateException(
                "app_user row missing immediately after provisioning"));
    }

    /**
     * The owner {@code app_user.id} for the current request, read-only.
     *
     * <p>Delegates to {@link CurrentCaller}, which resolves either a validated
     * Cognito identity (provisioned by {@code UserProvisioningFilter} on the way
     * in, so this never has to write) or an anonymous trial session (M006.1).
     * Every owner-scoped query keys on this id, so both kinds of caller are
     * scoped identically.
     */
    @Transactional(readOnly = true)
    public UUID currentUserId() {
        return currentCaller.ownerUserId();
    }
}
