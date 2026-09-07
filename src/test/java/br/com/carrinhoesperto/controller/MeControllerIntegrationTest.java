package br.com.carrinhoesperto.controller;

import br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest;
import br.com.carrinhoesperto.dto.CurrentUserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: HTTP -> Spring Security (OAuth2 Resource Server) -> controller,
 * using a JWT minted by MiniStack Cognito.
 */
class MeControllerIntegrationTest extends AbstractAuthenticatedIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void returnsTheAuthenticatedIdentity() {
        CurrentUserResponse body = client().get()
            .uri("/api/me")
            .header("Authorization", "Bearer " + validAccessToken())
            .retrieve()
            .body(CurrentUserResponse.class);

        assertThat(body).isNotNull();
        assertThat(body.authenticated()).isTrue();
        assertThat(body.subject()).isEqualTo(expectedSubject());
    }

    @Test
    void describesAnAnonymousTrialCallerWithoutAn500() {
        String token = client().post().uri("/api/anonymous-sessions")
            .retrieve()
            .body(br.com.carrinhoesperto.session.dto.AnonymousSessionResponse.class)
            .token();

        CurrentUserResponse body = client().get()
            .uri("/api/me")
            .header("X-Anon-Session", token)
            .retrieve()
            .body(CurrentUserResponse.class);

        assertThat(body).isNotNull();
        assertThat(body.authenticated()).isFalse();
        assertThat(body.subject()).isNull();
        assertThat(body.username()).isNull();
    }

    @Test
    void rejectsAnUnauthenticatedRequest() {
        int status = client().get()
            .uri("/api/me")
            .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(401);
    }
}
