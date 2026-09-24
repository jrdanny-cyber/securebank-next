package com.securebank.banking;

import java.util.List;
import java.util.UUID;

import com.securebank.banking.accounts.AccountQueryRepository;
import com.securebank.banking.accounts.AccountSummary;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    AccountQueryRepository accountQueries;

    @Test
    @Transactional
    void customersCanRetrieveOnlyTheirOwnAccounts() {
         AccountSummary aliceAccount = insertTestAccount(
            "test-alice", "TEST-ALICE-001", "Everyday account");

    AccountSummary bobAccount = insertTestAccount(
            "test-bob", "TEST-BOB-001", "Savings account");

    assertEquals(
            List.of(aliceAccount),
            accountQueries.findByIdentitySubject("test-alice"));

    assertEquals(
            List.of(bobAccount),
            accountQueries.findByIdentitySubject("test-bob"));
    }

    @Test
    @Transactional
    void unknownIdentityAndSqlInjectionTextReturnNoAccounts() {
         insertTestAccount(
            "test-alice", "TEST-ALICE-001", "Everyday account");

    assertEquals(
            List.of(),
            accountQueries.findByIdentitySubject("unknown-customer"));

    assertEquals(
            List.of(),
            accountQueries.findByIdentitySubject("' OR '1'='1"));
    } 

    private AccountSummary insertTestAccount(
        String identitySubject,
        String accountReference,
        String accountName
    ) {
    UUID customerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    jdbc.update("""
            INSERT INTO banking.customers
                (id, identity_subject, display_name)
            VALUES (?, ?, ?)
            """,
            customerId,
            identitySubject,
            "Test customer"
    );

    jdbc.update("""
            INSERT INTO banking.accounts
                (id, customer_id, account_reference, account_name, currency)
            VALUES (?, ?, ?, ?, ?)
            """,
            accountId,
            customerId,
            accountReference,
            accountName,
            "USD"
    );

    return new AccountSummary(
            accountId,
            accountReference,
            accountName,
            "USD",
            "ACTIVE"
    );
  }
}
