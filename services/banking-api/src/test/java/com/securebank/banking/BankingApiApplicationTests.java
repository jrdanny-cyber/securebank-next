package com.securebank.banking;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
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
            "ACTIVE",
	    "0.00"
    );
  }
   @Autowired
MockMvc mockMvc;

@MockitoBean
JwtDecoder jwtDecoder;

@Test
void anonymousAccountRequestIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/accounts"))
            .andExpect(status().isUnauthorized());
}

@Test
@Transactional
void accountEndpointReturnsOnlyAuthenticatedCustomersAccounts()
        throws Exception {
    AccountSummary aliceAccount = insertTestAccount(
            "test-alice", "TEST-ALICE-001", "Everyday account");

    AccountSummary bobAccount = insertTestAccount(
            "test-bob", "TEST-BOB-001", "Savings account");

    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.subject("test-alice"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id")
                    .value(aliceAccount.id().toString()));

    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.subject("test-bob"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id")
                    .value(bobAccount.id().toString()));
}

@Test
void tokenWithoutAccountsReadScopeIsForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.subject("test-alice"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_profile"))))
            .andExpect(status().isForbidden());
}

@Test
void rejectedBearerTokenReturnsUnauthorized() throws Exception {
    when(jwtDecoder.decode("invalid-token"))
            .thenThrow(new BadJwtException("Invalid test token"));

    mockMvc.perform(get("/api/v1/accounts")
                    .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
}
@Test
void tokenWithoutSubjectIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.claims(
                                    claims -> claims.remove("sub")))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isUnauthorized());
}
@Test
@Transactional
void journalProducesEqualDebitAndCreditEntries() {
    AccountSummary source = insertTestAccount(
            "ledger-source", "LEDGER-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "ledger-destination", "LEDGER-DESTINATION", "Destination account");

    UUID transactionId = insertJournal(
            source.id(), destination.id(), "25.50");

    assertEquals(
            2L,
            jdbc.queryForObject("""
                    SELECT count(*)
                    FROM banking.ledger_entries
                    WHERE transaction_id = ?
                    """, Long.class, transactionId));

    BigDecimal debit = jdbc.queryForObject("""
            SELECT amount
            FROM banking.ledger_entries
            WHERE transaction_id = ? AND direction = 'DEBIT'
            """, BigDecimal.class, transactionId);

    BigDecimal credit = jdbc.queryForObject("""
            SELECT amount
            FROM banking.ledger_entries
            WHERE transaction_id = ? AND direction = 'CREDIT'
            """, BigDecimal.class, transactionId);

    assertEquals(0, new BigDecimal("25.50").compareTo(debit));
    assertEquals(0, debit.compareTo(credit));
}

@Test
@Transactional
void databaseRejectsFractionalCents() {
    AccountSummary source = insertTestAccount(
            "precision-source", "PRECISION-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "precision-destination", "PRECISION-DEST", "Destination account");

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> insertJournal(source.id(), destination.id(), "25.501"));

    assertEquals("23514", sqlState(failure));
}

@Test
@Transactional
void databaseRejectsAccountCurrencyMismatch() {
    AccountSummary source = insertTestAccount(
            "currency-source", "CURRENCY-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "currency-destination", "CURRENCY-DEST", "Destination account");

    jdbc.update(
            "UPDATE banking.accounts SET currency = 'EUR' WHERE id = ?",
            destination.id());

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> insertJournal(source.id(), destination.id(), "25.50"));

    assertEquals("23503", sqlState(failure));
}

@Test
@Transactional
void applicationCannotUpdatePostedJournal() {
    AccountSummary source = insertTestAccount(
            "update-source", "UPDATE-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "update-destination", "UPDATE-DEST", "Destination account");

    UUID transactionId = insertJournal(
            source.id(), destination.id(), "25.50");

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> jdbc.update("""
                    UPDATE banking.journal_transactions
                    SET amount = 99.00
                    WHERE id = ?
                    """, transactionId));

    assertEquals("42501", sqlState(failure));
}

@Test
@Transactional
void applicationCannotDeletePostedJournal() {
    AccountSummary source = insertTestAccount(
            "delete-source", "DELETE-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "delete-destination", "DELETE-DEST", "Destination account");

    UUID transactionId = insertJournal(
            source.id(), destination.id(), "25.50");

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> jdbc.update("""
                    DELETE FROM banking.journal_transactions
                    WHERE id = ?
                    """, transactionId));

    assertEquals("42501", sqlState(failure));
}

private UUID insertJournal(UUID debitAccountId, UUID creditAccountId,
                           String amount) {
    UUID transactionId = UUID.randomUUID();

    jdbc.update("""
            INSERT INTO banking.journal_transactions (
                id, debit_account_id, credit_account_id,
                amount, currency, description
            )
            VALUES (?, ?, ?, ?, 'USD', 'Integration test posting')
            """,
            transactionId,
            debitAccountId,
            creditAccountId,
            new BigDecimal(amount));

    return transactionId;
}

private String sqlState(DataAccessException failure) {
    return ((java.sql.SQLException) failure.getMostSpecificCause())
            .getSQLState();
}
@Test
@Transactional
void accountWithoutPostingsHasZeroBalance() throws Exception {
    insertTestAccount(
            "zero-balance-user", "ZERO-BALANCE", "Empty account");

    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.subject("zero-balance-user"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].balance").value("0.00"));
}

@Test
@Transactional
void accountBalancesReflectCreditsMinusDebits() throws Exception {
    AccountSummary source = insertTestAccount(
            "balance-source", "BALANCE-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "balance-destination", "BALANCE-DEST", "Destination account");

    insertJournal(source.id(), destination.id(), "25.50");
    insertJournal(destination.id(), source.id(), "5.20");

    assertEquals(
            "-20.30",
            accountQueries.findByIdentitySubject("balance-source")
                    .getFirst()
                    .balance());

    mockMvc.perform(get("/api/v1/accounts")
                    .with(jwt()
                            .jwt(token -> token.subject("balance-destination"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id")
                    .value(destination.id().toString()))
            .andExpect(jsonPath("$[0].balance").value("20.30"));
}
@Test
@Transactional
void internalAccountsAreExcludedFromCustomerAccountResults() {
    AccountSummary customerAccount = insertTestAccount(
            "internal-isolation-user",
            "CUSTOMER-ISOLATION",
            "Customer account");

    jdbc.update("""
            INSERT INTO banking.accounts (
                id, customer_id, account_reference,
                account_name, currency, account_kind
            )
            VALUES (?, NULL, ?, ?, 'USD', 'INTERNAL_ASSET')
            """,
            UUID.randomUUID(),
            "INTERNAL-ISOLATION",
            "Internal cash asset");

    assertEquals(
            List.of(customerAccount),
            accountQueries.findByIdentitySubject("internal-isolation-user"));
}

@Test
@Transactional
void customerAccountMustHaveAnOwner() {
    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> jdbc.update("""
                    INSERT INTO banking.accounts (
                        id, customer_id, account_reference,
                        account_name, currency, account_kind
                    )
                    VALUES (
                        ?, NULL, 'OWNERLESS-TEST',
                        'Invalid account', 'USD', 'CUSTOMER_LIABILITY'
                    )
                    """,
                    UUID.randomUUID()));

    assertEquals("23514", sqlState(failure));
}
}
