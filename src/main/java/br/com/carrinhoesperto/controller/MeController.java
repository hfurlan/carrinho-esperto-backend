package br.com.carrinhoesperto.controller;

import br.com.carrinhoesperto.config.AuthenticatedUser;
import br.com.carrinhoesperto.config.CurrentCaller;
import br.com.carrinhoesperto.dto.CurrentUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Describes the current request's caller.
 *
 * <p>Requires an authorized request (Cognito JWT <em>or</em> a valid anonymous
 * trial session, M006.1). A Cognito caller gets its {@code sub}/{@code username};
 * an anonymous trial caller gets {@code authenticated:false} with no identity
 * (never a 500). An unauthorized request is a {@code 401} before this runs.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentCaller currentCaller;
    private final AuthenticatedUser authenticatedUser;

    MeController(CurrentCaller currentCaller, AuthenticatedUser authenticatedUser) {
        this.currentCaller = currentCaller;
        this.authenticatedUser = authenticatedUser;
    }

    @GetMapping
    public CurrentUserResponse me() {
        if (currentCaller.isAnonymousTrial()) {
            return CurrentUserResponse.anonymousTrial();
        }
        return CurrentUserResponse.of(authenticatedUser.cognitoSubject(), authenticatedUser.username());
    }
}
