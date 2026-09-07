package br.com.carrinhoesperto.dto;

/** {@code GET /api/me} response — identity resolved from the validated JWT. */
public record CurrentUserResponse(String subject, String username) {
}
