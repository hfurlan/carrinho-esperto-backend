package br.com.carrinhoesperto.controller;

import br.com.carrinhoesperto.config.AuthenticatedUser;
import br.com.carrinhoesperto.dto.CurrentUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the identity of the authenticated caller as resolved from the validated
 * Cognito JWT. Requires authentication; no application-specific authorization.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthenticatedUser authenticatedUser;

    MeController(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public CurrentUserResponse me() {
        return new CurrentUserResponse(
            authenticatedUser.cognitoSubject(),
            authenticatedUser.username()
        );
    }
}
