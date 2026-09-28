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
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.securebank.banking.ledger.PostingAmount;
import com.securebank.banking.transfers.TransferService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.securebank.banking.accounts.AccountHistoryRepository;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
@Test
@Transactional
void sameCustomerCannotReuseRequestKeyForAnotherTransaction() {
    AccountSummary source = insertTestAccount(
            "retry-source", "RETRY-SOURCE", "Source account");

    AccountSummary destination = insertTestAccount(
            "retry-destination", "RETRY-DEST", "Destination account");

    UUID key = UUID.randomUUID();

    UUID firstTransaction = insertJournal(
            source.id(), destination.id(), "10.00");

    UUID secondTransaction = insertJournal(
            source.id(), destination.id(), "20.00");

    insertTransferRequest("retry-source", key, firstTransaction);

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> insertTransferRequest(
                    "retry-source", key, secondTransaction));

    assertEquals("23505", sqlState(failure));
}

@Test
@Transactional
void differentCustomersCanUseTheSameRequestKey() {
    AccountSummary alice = insertTestAccount(
            "request-alice", "REQUEST-ALICE", "Alice account");

    AccountSummary bob = insertTestAccount(
            "request-bob", "REQUEST-BOB", "Bob account");

    UUID sharedKey = UUID.randomUUID();

    UUID aliceTransaction = insertJournal(
            alice.id(), bob.id(), "10.00");

    UUID bobTransaction = insertJournal(
            bob.id(), alice.id(), "5.00");

    insertTransferRequest("request-alice", sharedKey, aliceTransaction);
    insertTransferRequest("request-bob", sharedKey, bobTransaction);

    assertEquals(
            2L,
            jdbc.queryForObject("""
                    SELECT count(*)
                    FROM banking.transfer_requests
                    WHERE idempotency_key = ?
                    """, Long.class, sharedKey));
}

@Test
@Transactional
void transferRequestMustReferenceAnExistingJournalTransaction() {
    insertTestAccount(
            "missing-journal-user",
            "MISSING-JOURNAL",
            "Customer account");

    DataAccessException failure = assertThrows(
            DataAccessException.class,
            () -> insertTransferRequest(
                    "missing-journal-user",
                    UUID.randomUUID(),
                    UUID.randomUUID()));

    assertEquals("23503", sqlState(failure));
}

private void insertTransferRequest(
        String identitySubject,
        UUID idempotencyKey,
        UUID transactionId
) {
    UUID customerId = jdbc.queryForObject("""
            SELECT id
            FROM banking.customers
            WHERE identity_subject = ?
            """, UUID.class, identitySubject);

    jdbc.update("""
            INSERT INTO banking.transfer_requests (
                customer_id, idempotency_key, transaction_id
            )
            VALUES (?, ?, ?)
            """,
            customerId,
            idempotencyKey,
            transactionId);
}
@Autowired
TransferService transfers;

@Test
@Transactional
void transferMovesMoneyBetweenOwnedAccounts() {
    TransferFixture fixture = createTransferFixture();

    TransferService.Result result = transferFor(
            fixture, UUID.randomUUID(), "30.00");

    assertEquals(false, result.replayed());
    assertEquals("70.00", balanceOf(fixture, fixture.source()));
    assertEquals("30.00", balanceOf(fixture, fixture.destination()));

    assertEquals(
            1L,
            jdbc.queryForObject("""
                    SELECT count(*)
                    FROM banking.transfer_requests
                    WHERE transaction_id = ?
                    """, Long.class, result.transactionId()));
}

@Test
@Transactional
void retryReturnsOriginalTransferWithoutMovingMoneyAgain() {
    TransferFixture fixture = createTransferFixture();
    UUID key = UUID.randomUUID();

    TransferService.Result first = transferFor(fixture, key, "30.00");
    TransferService.Result retry = transferFor(fixture, key, "30.00");

    assertEquals(first.transactionId(), retry.transactionId());
    assertEquals(true, retry.replayed());
    assertEquals("70.00", balanceOf(fixture, fixture.source()));
    assertEquals("30.00", balanceOf(fixture, fixture.destination()));
}

@Test
@Transactional
void changedAmountWithSameRequestKeyIsRejected() {
    TransferFixture fixture = createTransferFixture();
    UUID key = UUID.randomUUID();

    transferFor(fixture, key, "30.00");

    TransferService.Rejected failure = assertThrows(
            TransferService.Rejected.class,
            () -> transferFor(fixture, key, "40.00"));

    assertEquals("IDEMPOTENCY_CONFLICT", failure.code());
    assertEquals("70.00", balanceOf(fixture, fixture.source()));
    assertEquals("30.00", balanceOf(fixture, fixture.destination()));
}

@Test
@Transactional
void insufficientFundsLeaveBalancesUnchanged() {
    TransferFixture fixture = createTransferFixture();

    TransferService.Rejected failure = assertThrows(
            TransferService.Rejected.class,
            () -> transferFor(fixture, UUID.randomUUID(), "100.01"));

    assertEquals("INSUFFICIENT_FUNDS", failure.code());
    assertEquals("100.00", balanceOf(fixture, fixture.source()));
    assertEquals("0.00", balanceOf(fixture, fixture.destination()));
}

@Test
@Transactional
void customerCannotTransferFromAnotherCustomersAccounts() {
    TransferFixture fixture = createTransferFixture();

    String outsiderSubject = "outsider-" + UUID.randomUUID();
    insertTestAccount(
            outsiderSubject,
            "OUT-" + UUID.randomUUID().toString().substring(0, 20),
            "Other customer account");

    TransferService.Rejected failure = assertThrows(
            TransferService.Rejected.class,
            () -> transfers.transfer(
                    outsiderSubject,
                    UUID.randomUUID(),
                    fixture.source(),
                    fixture.destination(),
                    new PostingAmount(new BigDecimal("10.00"), "USD"),
                    "Unauthorized transfer"));

    assertEquals("ACCOUNT_UNAVAILABLE", failure.code());
    assertEquals("100.00", balanceOf(fixture, fixture.source()));
    assertEquals("0.00", balanceOf(fixture, fixture.destination()));
}

private TransferService.Result transferFor(
        TransferFixture fixture, UUID key, String amount
) {
    return transfers.transfer(
            fixture.subject(),
            key,
            fixture.source(),
            fixture.destination(),
            new PostingAmount(new BigDecimal(amount), "USD"),
            "Move money to savings");
}

private String balanceOf(TransferFixture fixture, UUID accountId) {
    return accountQueries.findByIdentitySubject(fixture.subject())
            .stream()
            .filter(account -> account.id().equals(accountId))
            .findFirst()
            .orElseThrow()
            .balance();
}

private TransferFixture createTransferFixture() {
    String suffix = UUID.randomUUID().toString().substring(0, 20);
    String subject = "transfer-" + suffix;

    AccountSummary source = insertTestAccount(
            subject, "SRC-" + suffix, "Everyday account");

    AccountSummary destination = insertTestAccount(
            "temporary-" + suffix, "DST-" + suffix, "Savings account");

    jdbc.update("""
            UPDATE banking.accounts
            SET customer_id = (
                SELECT customer_id
                FROM banking.accounts
                WHERE id = ?
            )
            WHERE id = ?
            """,
            source.id(),
            destination.id());

    UUID cashAccountId = UUID.randomUUID();

    jdbc.update("""
            INSERT INTO banking.accounts (
                id, customer_id, account_reference,
                account_name, currency, account_kind
            )
            VALUES (?, NULL, ?, 'Test cash asset', 'USD', 'INTERNAL_ASSET')
            """,
            cashAccountId,
            "CASH-" + suffix);

    insertJournal(cashAccountId, source.id(), "100.00");

    return new TransferFixture(subject, source.id(), destination.id());
}

private record TransferFixture(
        String subject,
        UUID source,
        UUID destination
) {
}
@Test
void anonymousTransferRequestIsRejected() throws Exception {
    mockMvc.perform(post("/api/v1/transfers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isUnauthorized());
}

@Test
void accountsReadScopeDoesNotPermitTransfers() throws Exception {
    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt().authorities(
                            new SimpleGrantedAuthority("SCOPE_accounts:read")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andExpect(status().isForbidden());
}

@Test
@Transactional
void transferEndpointPostsAndReplaysTheSameRequest() throws Exception {
    TransferFixture fixture = createTransferFixture();
    UUID key = UUID.randomUUID();
    String body = transferJson(fixture, "25.00");

    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_transfers:write")))
                    .header("Idempotency-Key", key.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").isNotEmpty())
            .andExpect(jsonPath("$.replayed").value(false));

    UUID transactionId = jdbc.queryForObject("""
            SELECT transaction_id
            FROM banking.transfer_requests
            WHERE idempotency_key = ?
            """, UUID.class, key);

    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_transfers:write")))
                    .header("Idempotency-Key", key.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId")
                    .value(transactionId.toString()))
            .andExpect(jsonPath("$.replayed").value(true));

    assertEquals("75.00", balanceOf(fixture, fixture.source()));
    assertEquals("25.00", balanceOf(fixture, fixture.destination()));
}

@Test
@Transactional
void insufficientFundsReturnsConflict() throws Exception {
    TransferFixture fixture = createTransferFixture();

    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_transfers:write")))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(transferJson(fixture, "100.01")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

    assertEquals("100.00", balanceOf(fixture, fixture.source()));
    assertEquals("0.00", balanceOf(fixture, fixture.destination()));
}

@Test
@Transactional
void fractionalCentRequestIsRejected() throws Exception {
    TransferFixture fixture = createTransferFixture();

    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_transfers:write")))
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(transferJson(fixture, "25.501")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertEquals("100.00", balanceOf(fixture, fixture.source()));
}
@Autowired
AccountHistoryRepository accountHistory;

@Test
void anonymousHistoryRequestIsRejected() throws Exception {
    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
}

@Test
void transferScopeAloneDoesNotPermitReadingHistory() throws Exception {
    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    UUID.randomUUID())
                    .with(jwt().authorities(
                            new SimpleGrantedAuthority("SCOPE_transfers:write"))))
            .andExpect(status().isForbidden());
}

@Test
@Transactional
void historyShowsEntriesForTheRequestedOwnedAccount() throws Exception {
    TransferFixture fixture = createTransferFixture();
    TransferService.Result result =
            transferFor(fixture, UUID.randomUUID(), "25.00");

    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    fixture.source())
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[*].direction")
                    .value(containsInAnyOrder("CREDIT", "DEBIT")))
            .andExpect(jsonPath("$.items[*].amount")
                    .value(containsInAnyOrder("100.00", "25.00")))
            .andExpect(jsonPath("$.hasNext").value(false));

    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    fixture.destination())
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].transactionId")
                    .value(result.transactionId().toString()))
            .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
            .andExpect(jsonPath("$.items[0].amount").value("25.00"));
}

@Test
@Transactional
void historyHidesOtherCustomersAndUnknownAccounts() throws Exception {
    TransferFixture fixture = createTransferFixture();

    for (UUID accountId : List.of(fixture.source(), UUID.randomUUID())) {
        mockMvc.perform(get(
                        "/api/v1/accounts/{id}/transactions",
                        accountId)
                        .with(jwt()
                                .jwt(token -> token.subject("another-customer"))
                                .authorities(new SimpleGrantedAuthority(
                                        "SCOPE_accounts:read"))))
                .andExpect(status().isNotFound());
    }
}

@Test
@Transactional
void ownedAccountWithoutPostingsHasEmptyHistory() throws Exception {
    AccountSummary account = insertTestAccount(
            "empty-history-user",
            "EMPTY-HISTORY",
            "Empty account");

    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    account.id())
                    .with(jwt()
                            .jwt(token -> token.subject("empty-history-user"))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.hasNext").value(false));
}

@Test
@Transactional
void historyPaginationReturnsDifferentEntries() {
    TransferFixture fixture = createTransferFixture();
    transferFor(fixture, UUID.randomUUID(), "25.00");

    var first = accountHistory.find(
            fixture.subject(), fixture.source(), 0, 1);

    var second = accountHistory.find(
            fixture.subject(), fixture.source(), 1, 1);

    assertEquals(1, first.items().size());
    assertEquals(1, second.items().size());
    assertEquals(true, first.hasNext());
    assertEquals(false, second.hasNext());

    assertNotEquals(
            first.items().getFirst().transactionId(),
            second.items().getFirst().transactionId());
}

@Test
@Transactional
void oversizedHistoryPageIsRejected() throws Exception {
    TransferFixture fixture = createTransferFixture();

    mockMvc.perform(get(
                    "/api/v1/accounts/{id}/transactions",
                    fixture.source())
                    .param("size", "101")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_accounts:read"))))
            .andExpect(status().isBadRequest());
}

@Test
@Transactional
void transferRequiresIdempotencyKey() throws Exception {
    TransferFixture fixture = createTransferFixture();

    mockMvc.perform(post("/api/v1/transfers")
                    .with(jwt()
                            .jwt(token -> token.subject(fixture.subject()))
                            .authorities(new SimpleGrantedAuthority(
                                    "SCOPE_transfers:write")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(transferJson(fixture, "25.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
}

private String transferJson(TransferFixture fixture, String amount) {
    return """
            {
              "sourceAccountId": "%s",
              "destinationAccountId": "%s",
              "amount": "%s",
              "currency": "USD",
              "description": "Move money to savings"
            }
            """.formatted(fixture.source(), fixture.destination(), amount);
}
}
