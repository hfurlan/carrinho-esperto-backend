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
        assertThat(body.subject()).isEqualTo(expectedSubject());
    }

    @Test
    void rejectsAnUnauthenticatedRequest() {
        int status = client().get()
            .uri("/api/me")
            .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(401);
    }
}
