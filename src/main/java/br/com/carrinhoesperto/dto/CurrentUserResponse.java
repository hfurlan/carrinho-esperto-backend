package br.com.carrinhoesperto.dto;

/**
 * {@code GET /api/me} response.
 *
 * <p>For a Cognito caller, {@code authenticated} is {@code true} and
 * {@code subject}/{@code username} carry the validated identity. For an
 * anonymous trial caller (M006.1), {@code authenticated} is {@code false} and
 * both identity fields are {@code null} — the request is still authorized
 * ({@code ROLE_TRIAL}), it simply has no user identity. A request with no
 * recognised caller never reaches here (it is a {@code 401}).
 */
public record CurrentUserResponse(boolean authenticated, String subject, String username) {

    public static CurrentUserResponse anonymousTrial() {
        return new CurrentUserResponse(false, null, null);
    }

    public static CurrentUserResponse of(String subject, String username) {
        return new CurrentUserResponse(true, subject, username);
    }
}
