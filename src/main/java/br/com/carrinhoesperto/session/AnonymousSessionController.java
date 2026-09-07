package br.com.carrinhoesperto.session;

import br.com.carrinhoesperto.session.dto.AnonymousSessionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts an anonymous trial session (M006.1, docs/anonymous-trial.md).
 *
 * <p>{@code POST /api/anonymous-sessions} is the only unauthenticated write in
 * the API (permitted in {@code SecurityConfig}). It returns a one-time token the
 * browser then presents as {@code X-Anon-Session} on subsequent calls.
 *
 * <p>M006.1 adds no rate limiting here — trial data is low value and
 * session-scoped. Abuse controls and retention enforcement belong to a later
 * milestone.
 */
@RestController
@RequestMapping("/api/anonymous-sessions")
public class AnonymousSessionController {

    private final AnonymousSessionService anonymousSessionService;

    AnonymousSessionController(AnonymousSessionService anonymousSessionService) {
        this.anonymousSessionService = anonymousSessionService;
    }

    @PostMapping
    public ResponseEntity<AnonymousSessionResponse> create() {
        String token = anonymousSessionService.create();
        return ResponseEntity.status(HttpStatus.CREATED).body(new AnonymousSessionResponse(token));
    }
}
