package br.com.carrinhoesperto.service;

import br.com.carrinhoesperto.config.AuthenticatedUser;
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

    UserService(UserRepository userRepository, AuthenticatedUser authenticatedUser) {
        this.userRepository = userRepository;
        this.authenticatedUser = authenticatedUser;
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
     * The local user id for the authenticated caller, read-only. The user is
     * always already provisioned by {@code UserProvisioningFilter} on the way in,
     * so this never has to write — callers inside a read-only transaction can use
     * it safely.
     */
    @Transactional(readOnly = true)
    public UUID currentUserId() {
        String cognitoSubject = authenticatedUser.cognitoSubject();
        return userRepository.findByCognitoSubject(cognitoSubject)
            .map(User::id)
            .orElseThrow(() -> new IllegalStateException(
                "No local user for the authenticated subject; provisioning did not run"));
    }
}
