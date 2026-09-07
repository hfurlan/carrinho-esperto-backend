package br.com.carrinhoesperto.session;

import br.com.carrinhoesperto.config.CurrentCaller;
import br.com.carrinhoesperto.service.UserService;
import br.com.carrinhoesperto.session.dto.AnonymousSessionResponse;
import br.com.carrinhoesperto.session.dto.ClaimResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous trial sessions (M006.1) and their conversion into an account (M006.2,
 * docs/anonymous-trial.md).
 *
 * <ul>
 *   <li>{@code POST /api/anonymous-sessions} — start a session. The only
 *       unauthenticated write; returns a one-time {@code X-Anon-Session} token.</li>
 *   <li>{@code POST /api/anonymous-sessions/claim} — for a caller that has just
 *       signed in / registered and still carries an {@code X-Anon-Session}
 *       header: move that session's receipts into the account and drop the
 *       anonymous row. Requires a Cognito identity.</li>
 * </ul>
 *
 * No rate limiting here in M006 — trial data is low value and session-scoped.
 */
@RestController
@RequestMapping("/api/anonymous-sessions")
public class AnonymousSessionController {

    private final AnonymousSessionService anonymousSessionService;
    private final UserService userService;
    private final CurrentCaller currentCaller;

    AnonymousSessionController(AnonymousSessionService anonymousSessionService,
                              UserService userService, CurrentCaller currentCaller) {
        this.anonymousSessionService = anonymousSessionService;
        this.userService = userService;
        this.currentCaller = currentCaller;
    }

    @PostMapping
    public ResponseEntity<AnonymousSessionResponse> create() {
        String token = anonymousSessionService.create();
        return ResponseEntity.status(HttpStatus.CREATED).body(new AnonymousSessionResponse(token));
    }

    @PostMapping("/claim")
    public ClaimResult claim(
            @RequestHeader(value = "X-Anon-Session", required = false) String anonToken) {
        // Only a real account can claim. A trial-only caller has nothing to
        // claim into (it would resolve to the same row) — no-op rather than error.
        if (currentCaller.isAnonymousTrial()) {
            return new ClaimResult(0);
        }
        int moved = anonymousSessionService.claimInto(anonToken, userService.currentUserId());
        return new ClaimResult(moved);
    }
}
