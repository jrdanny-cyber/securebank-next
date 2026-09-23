package com.securebank.banking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class BankingApiApplicationTests {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("securebank")
                    .withUsername("postgres")
                    .withPassword("test-admin-password")
                    .withEnv(
                            "BANKING_MIGRATOR_PASSWORD",
                            "test-migrator-password")
                    .withEnv(
                            "BANKING_APP_PASSWORD",
                            "test-app-password")
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
        registry.add(
                "spring.flyway.password",
                () -> "test-migrator-password");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void applicationUsesRestrictedDatabaseRole() {
        assertEquals(
                "banking_app",
                jdbc.queryForObject("SELECT current_user", String.class));
    }

    @Test
    void migratedTablesAreReadable() {
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM banking.customers", Long.class));

        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM banking.accounts", Long.class));
    }

    @Test
    void applicationCannotCreateTables() {
        DataAccessException failure = assertThrows(
                DataAccessException.class,
                () -> jdbc.execute(
                        "CREATE TABLE banking.unauthorized_table (id INTEGER)"));

        var sqlFailure =
                (java.sql.SQLException) failure.getMostSpecificCause();

        assertEquals("42501", sqlFailure.getSQLState());
    }
}
