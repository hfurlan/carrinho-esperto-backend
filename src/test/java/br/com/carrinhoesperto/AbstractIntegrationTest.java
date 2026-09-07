package br.com.carrinhoesperto;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Duration;

/**
 * Base class for integration tests.
 *
 * Mode A — external docker compose already running:
 *   Detects live services and reuses them. Does not start or stop any container.
 *
 * Mode B — no external compose running:
 *   Starts docker-compose.yaml via Testcontainers and stops it after the test run.
 *
 * Detection uses real service probes (JDBC for Postgres, HTTP health for MiniStack),
 * not just port availability.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://localhost:5432/carrinho_esperto";
    private static final String POSTGRES_USER = "carrinho_esperto";
    private static final String POSTGRES_PASSWORD = "carrinho_esperto";
    private static final String MINISTACK_HEALTH_URL = "http://localhost:4566/health";

    private static final boolean EXTERNAL_INFRA;
    private static final ComposeContainer COMPOSE;

    /** Base URL of the MiniStack endpoint in use, valid in both Mode A and Mode B. */
    protected static final String MINISTACK_ENDPOINT;
    protected static final String AWS_REGION = "us-east-1";

    static {
        boolean postgresReady = isPostgresReady();
        boolean ministackReady = isMiniStackReady();
        EXTERNAL_INFRA = postgresReady && ministackReady;

        if (EXTERNAL_INFRA) {
            COMPOSE = null;
            MINISTACK_ENDPOINT = "http://localhost:4566";
        } else {
            File composeFile = resolveComposeFile();
            COMPOSE = new ComposeContainer(composeFile)
                // The `bootstrap` one-shot in the compose file is a no-op here
                // (tests provision their own disposable Cognito).
                .withEnv("BOOTSTRAP_APPLY", "false")
                .withExposedService("postgres", 5432,
                    Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                .withExposedService("ministack", 4566,
                    Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(120)));
            COMPOSE.start();
            MINISTACK_ENDPOINT = "http://" + COMPOSE.getServiceHost("ministack", 4566)
                + ":" + COMPOSE.getServicePort("ministack", 4566);
        }
    }

    @DynamicPropertySource
    static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        if (EXTERNAL_INFRA) {
            // Datasource properties already set correctly in application-test.yaml
            return;
        }
        String pgHost = COMPOSE.getServiceHost("postgres", 5432);
        Integer pgPort = COMPOSE.getServicePort("postgres", 5432);
        registry.add("spring.datasource.url",
            () -> "jdbc:postgresql://" + pgHost + ":" + pgPort + "/carrinho_esperto");
        registry.add("spring.datasource.username", () -> POSTGRES_USER);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
    }

    private static boolean isPostgresReady() {
        try (var conn = DriverManager.getConnection(POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMiniStackReady() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                URI.create(MINISTACK_HEALTH_URL).toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static File resolveComposeFile() {
        // When running from backend/, the compose file is one level up
        Path current = Paths.get("").toAbsolutePath();
        Path candidate = current.resolve("../docker-compose.yaml").normalize();
        if (candidate.toFile().exists()) {
            return candidate.toFile();
        }
        // Fallback: same directory (e.g. running from project root)
        return current.resolve("docker-compose.yaml").toFile();
    }
}
