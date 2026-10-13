package com.securebank.banking;

import java.sql.DriverManager;
import java.util.Map;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class DatabaseMigrationRunnerTests {

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

    @Test
    void migratesWithoutApplicationCredentialsAndCanRunAgain() throws Exception {
        var environment = Map.of(
                "BANKING_DB_URL", postgres.getJdbcUrl(),
                "BANKING_MIGRATOR_PASSWORD", "test-migrator-password");

        var first = DatabaseMigrationRunner.migrate(environment);
        assertEquals(4, first.migrationsExecuted);

        var second = DatabaseMigrationRunner.migrate(environment);
        assertEquals(0, second.migrationsExecuted);

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), "banking_app", "test-app-password");
             var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT count(*) FROM banking.transfer_requests")) {
            result.next();
            assertEquals(0L, result.getLong(1));
        }
    }

    @Test
    void invalidMigrationCredentialsFail() {
        assertThrows(FlywayException.class, () ->
                DatabaseMigrationRunner.migrate(Map.of(
                        "BANKING_DB_URL", postgres.getJdbcUrl(),
                        "BANKING_MIGRATOR_PASSWORD", "wrong-password")));
    }

    @Test
    void missingMigrationPasswordFailsBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () ->
                DatabaseMigrationRunner.migrate(Map.of(
                        "BANKING_DB_URL", postgres.getJdbcUrl())));
    }
}
