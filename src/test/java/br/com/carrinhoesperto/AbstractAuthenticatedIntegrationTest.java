package br.com.carrinhoesperto;

import br.com.carrinhoesperto.support.CognitoTestSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

/**
 * Integration-test base that additionally provisions a real MiniStack Cognito
 * user pool and points the OAuth2 Resource Server at it.
 *
 * <p>The pool/client and a primary user with a live access token are created once
 * per JVM. A second user in the same pool is provisioned lazily on first use, for
 * cross-user isolation tests. The resolved issuer and JWKS URIs are injected
 * through {@link DynamicPropertySource}, overriding the placeholder in
 * {@code application-test.yaml} — mirroring how the datasource is wired in
 * {@link AbstractIntegrationTest}.
 */
public abstract class AbstractAuthenticatedIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME = "it-user@carrinho-esperto.local";
    private static final String PASSWORD = "ItPass123!";
    private static final String SECOND_USERNAME = "it-user-2@carrinho-esperto.local";
    private static final String SECOND_PASSWORD = "ItPass456!";
    private static final String ADMIN_USERNAME = "it-admin@carrinho-esperto.local";
    private static final String ADMIN_PASSWORD = "ItAdmin789!";
    /** Must match the default of {@code cognito.admin-group}. */
    private static final String ADMIN_GROUP = "admin";

    private static CognitoTestSupport support;
    private static CognitoTestSupport.ProvisionedUser user;
    private static CognitoTestSupport.ProvisionedUser secondUser;
    private static CognitoTestSupport.ProvisionedUser adminUser;

    @DynamicPropertySource
    static void cognitoProperties(DynamicPropertyRegistry registry) {
        if (user == null) {
            support = new CognitoTestSupport(MINISTACK_ENDPOINT, AWS_REGION);
            user = support.provision(USERNAME, PASSWORD);
        }
        registry.add("cognito.issuer-uri", user::issuerUri);
        registry.add("cognito.jwk-set-uri", user::jwkSetUri);
    }

    /** A valid, unexpired Cognito access-token JWT for the primary user. */
    protected String validAccessToken() {
        return user.accessToken();
    }

    /** The Cognito {@code sub} the application must expose for {@link #validAccessToken()}. */
    protected String expectedSubject() {
        return user.subject();
    }

    protected String expectedUsername() {
        return user.username();
    }

    /** A valid access-token JWT for a distinct second user in the same pool. */
    protected String secondUserAccessToken() {
        return secondUser().accessToken();
    }

    /** The Cognito {@code sub} of the second user. */
    protected String secondUserSubject() {
        return secondUser().subject();
    }

    /**
     * A valid access-token JWT for a distinct user in the same pool who is a
     * member of the {@code admin} group — its {@code cognito:groups} claim maps to
     * {@code ROLE_ADMIN}.
     */
    protected String adminAccessToken() {
        return adminUser().accessToken();
    }

    /** The Cognito {@code sub} of the admin user. */
    protected String adminSubject() {
        return adminUser().subject();
    }

    private static synchronized CognitoTestSupport.ProvisionedUser secondUser() {
        if (secondUser == null) {
            secondUser = support.provisionAdditionalUser(user, SECOND_USERNAME, SECOND_PASSWORD);
        }
        return secondUser;
    }

    private static synchronized CognitoTestSupport.ProvisionedUser adminUser() {
        if (adminUser == null) {
            adminUser = support.provisionAdditionalUser(
                user, ADMIN_USERNAME, ADMIN_PASSWORD, List.of(ADMIN_GROUP));
        }
        return adminUser;
    }
}
