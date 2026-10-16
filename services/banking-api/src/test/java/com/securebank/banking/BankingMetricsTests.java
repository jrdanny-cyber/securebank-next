package com.securebank.banking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureMetrics

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=9001")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BankingMetricsTests {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("securebank")
                    .withUsername("postgres")
                    .withPassword("test-admin-password")
                    .withEnv("BANKING_MIGRATOR_PASSWORD", "test-migrator-password")
                    .withEnv("BANKING_APP_PASSWORD", "test-app-password")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(
                                    "../../infrastructure/docker/postgres/"
                                            + "001-create-roles.sql"),
                            "/docker-entrypoint-initdb.d/001-create-roles.sql");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "banking_app");
        registry.add("spring.datasource.password", () -> "test-app-password");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "banking_migrator");
        registry.add("spring.flyway.password", () -> "test-migrator-password");
    }

    @LocalServerPort
    int applicationPort;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private HttpResponse<String> get(int port, String path) throws Exception {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test
    void managementPortExposesPrometheusMetrics() throws Exception {
        var response = get(9001, "/actuator/prometheus");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("jvm_memory_used_bytes"));
    }

    @Test
    void applicationPortRejectsAnonymousMetricsAccess() throws Exception {
        assertEquals(
                401,
                get(applicationPort, "/actuator/prometheus").statusCode());
    }

    @Test
    void applicationPortExposesHealthProbes() throws Exception {
        assertEquals(200, get(applicationPort, "/livez").statusCode());
        assertEquals(200, get(applicationPort, "/readyz").statusCode());
    }

    @Test
    void managementPortDoesNotExposeAccountsApi() throws Exception {
        int status = get(9001, "/api/v1/accounts").statusCode();
        assertTrue(status == 401 || status == 403 || status == 404);
    }
}
