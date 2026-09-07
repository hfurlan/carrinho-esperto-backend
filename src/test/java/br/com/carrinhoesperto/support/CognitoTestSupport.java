package br.com.carrinhoesperto.support;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExplicitAuthFlowsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;

import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only helper that provisions a disposable Cognito user pool in MiniStack
 * and mints a real access-token JWT for it.
 *
 * Used by {@link br.com.carrinhoesperto.AbstractAuthenticatedIntegrationTest} to drive
 * the OAuth2 Resource Server end to end without any application-side AWS code.
 *
 * MiniStack mirrors AWS: the token {@code iss} is the AWS-style
 * {@code https://cognito-idp.<region>.amazonaws.com/<poolId>}, while the JWKS is
 * served locally at {@code <endpoint>/<poolId>/.well-known/jwks.json}. Both values
 * are captured here and injected into the resource server.
 */
public final class CognitoTestSupport {

    private final String endpoint;
    private final CognitoIdentityProviderClient client;

    public CognitoTestSupport(String endpoint, String region) {
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.client = CognitoIdentityProviderClient.builder()
            .endpointOverride(URI.create(this.endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }

    /**
     * Creates a fresh pool + client + confirmed user and authenticates.
     *
     * @param username the sign-in name for the user
     * @param password a password meeting the default Cognito policy
     * @return everything the resource server and the tests need
     */
    public ProvisionedUser provision(String username, String password) {
        String poolName = "carrinho-esperto-it-" + UUID.randomUUID();
        String poolId = client.createUserPool(CreateUserPoolRequest.builder()
                .poolName(poolName)
                .build())
            .userPool().id();

        String clientId = client.createUserPoolClient(CreateUserPoolClientRequest.builder()
                .userPoolId(poolId)
                .clientName("carrinho-esperto-backend")
                .generateSecret(false)
                .explicitAuthFlows(
                    ExplicitAuthFlowsType.ALLOW_ADMIN_USER_PASSWORD_AUTH,
                    ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH)
                .build())
            .userPoolClient().clientId();

        return createConfirmedUserAndAuthenticate(poolId, clientId, username, password, List.of());
    }

    /**
     * Adds a second confirmed user to an already-provisioned pool/client and
     * authenticates as that user. Same issuer and JWKS as {@code existing}, so
     * the resource server accepts both tokens — used by cross-user isolation
     * tests.
     */
    public ProvisionedUser provisionAdditionalUser(ProvisionedUser existing, String username, String password) {
        return provisionAdditionalUser(existing, username, password, List.of());
    }

    /**
     * As {@link #provisionAdditionalUser(ProvisionedUser, String, String)} but the
     * user is placed in {@code groups} (created if absent) before the token is
     * minted, so the access token carries a {@code cognito:groups} claim.
     */
    public ProvisionedUser provisionAdditionalUser(ProvisionedUser existing, String username,
                                                   String password, List<String> groups) {
        return createConfirmedUserAndAuthenticate(
            existing.userPoolId(), existing.clientId(), username, password, groups);
    }

    private ProvisionedUser createConfirmedUserAndAuthenticate(
            String poolId, String clientId, String username, String password, List<String> groups) {
        client.adminCreateUser(AdminCreateUserRequest.builder()
            .userPoolId(poolId)
            .username(username)
            .messageAction(MessageActionType.SUPPRESS)
            .userAttributes(
                AttributeType.builder().name("email").value(username).build(),
                AttributeType.builder().name("email_verified").value("true").build())
            .build());

        client.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
            .userPoolId(poolId)
            .username(username)
            .password(password)
            .permanent(true)
            .build());

        // Group membership must be set before authentication: the claim is baked
        // into the token at issue time.
        for (String group : groups) {
            try {
                client.createGroup(CreateGroupRequest.builder()
                    .userPoolId(poolId).groupName(group).build());
            } catch (RuntimeException alreadyExists) {
                // group already present in this pool — fine
            }
            client.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                .userPoolId(poolId).username(username).groupName(group).build());
        }

        AdminInitiateAuthResponse auth = client.adminInitiateAuth(AdminInitiateAuthRequest.builder()
            .userPoolId(poolId)
            .clientId(clientId)
            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
            .authParameters(Map.of("USERNAME", username, "PASSWORD", password))
            .build());

        String accessToken = auth.authenticationResult().accessToken();
        JWTClaimsSet claims = parse(accessToken);

        return new ProvisionedUser(
            poolId,
            clientId,
            claims.getIssuer(),
            endpoint + "/" + poolId + "/.well-known/jwks.json",
            accessToken,
            claims.getSubject(),
            string(claims, "username", username));
    }

    private static JWTClaimsSet parse(String jwt) {
        try {
            return JWTParser.parse(jwt).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new IllegalStateException("Cannot parse Cognito access token", e);
        }
    }

    private static String string(JWTClaimsSet claims, String name, String fallback) {
        try {
            String value = claims.getStringClaim(name);
            return value != null ? value : fallback;
        } catch (ParseException e) {
            return fallback;
        }
    }

    /** Provisioned Cognito artefacts and a live access token. */
    public record ProvisionedUser(
        String userPoolId,
        String clientId,
        String issuerUri,
        String jwkSetUri,
        String accessToken,
        String subject,
        String username) {
    }
}
