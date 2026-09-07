package br.com.carrinhoesperto.session.dto;

/**
 * The one-time token for a freshly created anonymous trial session (M006.1). The
 * browser stores it and sends it back as the {@code X-Anon-Session} header; it
 * cannot be recovered afterwards.
 */
public record AnonymousSessionResponse(String token) {
}
