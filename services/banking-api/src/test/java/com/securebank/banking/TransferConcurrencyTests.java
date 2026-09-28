package com.securebank.banking;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.securebank.banking.ledger.PostingAmount;
import com.securebank.banking.transfers.TransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Timeout(60)
class TransferConcurrencyTests {

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

    @Autowired
    TransferService transfers;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void competingTransfersCannotOverspend() throws Exception {
        Fixture fixture = createFixture();

        List<Outcome> outcomes = concurrently(
                () -> attempt(fixture, UUID.randomUUID(), "70.00"),
                () -> attempt(fixture, UUID.randomUUID(), "70.00"));

        assertEquals(
                1L,
                outcomes.stream()
                        .filter(outcome -> outcome.result() != null)
                        .count());

        assertEquals(
                1L,
                outcomes.stream()
                        .filter(outcome ->
                                "INSUFFICIENT_FUNDS".equals(outcome.rejection()))
                        .count());

        assertBalance(fixture.source(), "30.00");
        assertBalance(fixture.destination(), "70.00");
        assertRecordedTransfers(fixture, 1L);
    }

    @Test
    void concurrentRetriesCreateOnlyOneTransfer() throws Exception {
        Fixture fixture = createFixture();
        UUID key = UUID.randomUUID();

        List<Outcome> outcomes = concurrently(
                () -> attempt(fixture, key, "40.00"),
                () -> attempt(fixture, key, "40.00"));

        assertTrue(outcomes.stream()
                .allMatch(outcome -> outcome.result() != null));

        assertEquals(
                outcomes.get(0).result().transactionId(),
                outcomes.get(1).result().transactionId());

        assertEquals(
                1L,
                outcomes.stream()
                        .filter(outcome -> outcome.result().replayed())
                        .count());

        assertBalance(fixture.source(), "60.00");
        assertBalance(fixture.destination(), "40.00");
        assertRecordedTransfers(fixture, 1L);
    }

    @Test
    void rollbackRemovesBothPostingAndRequestRecord() {
        Fixture fixture = createFixture();
        UUID key = UUID.randomUUID();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    executeTransfer(fixture, key, "25.00");
                    throw new IllegalStateException("Simulated failure before commit");
                }));

        assertEquals("Simulated failure before commit", failure.getMessage());

        assertBalance(fixture.source(), "100.00");
        assertBalance(fixture.destination(), "0.00");
        assertRecordedTransfers(fixture, 0L);

        TransferService.Result retry =
                executeTransfer(fixture, key, "25.00");

        assertEquals(false, retry.replayed());
        assertBalance(fixture.source(), "75.00");
        assertBalance(fixture.destination(), "25.00");
        assertRecordedTransfers(fixture, 1L);
    }

    private Outcome attempt(Fixture fixture, UUID key, String amount) {
        try {
            return new Outcome(executeTransfer(fixture, key, amount), null);
        } catch (TransferService.Rejected exception) {
            return new Outcome(null, exception.code());
        }
    }

    private TransferService.Result executeTransfer(
            Fixture fixture, UUID key, String amount
    ) {
        return transfers.transfer(
                fixture.subject(),
                key,
                fixture.source(),
                fixture.destination(),
                new PostingAmount(new BigDecimal(amount), "USD"),
                "Concurrency test transfer");
    }

    private <T> List<T> concurrently(
            Callable<T> first, Callable<T> second
    ) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try {
            var firstFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return first.call();
            });

            var secondFuture = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return second.call();
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            return List.of(
                    firstFuture.get(25, TimeUnit.SECONDS),
                    secondFuture.get(25, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Fixture createFixture() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return transaction.execute(status -> {
            UUID customerId = UUID.randomUUID();
            UUID sourceId = UUID.randomUUID();
            UUID destinationId = UUID.randomUUID();
            UUID cashId = UUID.randomUUID();

            String suffix = UUID.randomUUID().toString().substring(0, 20);
            String subject = "concurrency-" + suffix;

            jdbc.update("""
                    INSERT INTO banking.customers (
                        id, identity_subject, display_name
                    )
                    VALUES (?, ?, 'Concurrency test customer')
                    """,
                    customerId, subject);

            jdbc.update("""
                    INSERT INTO banking.accounts (
                        id, customer_id, account_reference,
                        account_name, currency, account_kind
                    )
                    VALUES
                        (?, ?, ?, 'Source', 'USD', 'CUSTOMER_LIABILITY'),
                        (?, ?, ?, 'Destination', 'USD', 'CUSTOMER_LIABILITY'),
                        (?, NULL, ?, 'Cash', 'USD', 'INTERNAL_ASSET')
                    """,
                    sourceId, customerId, "SRC-" + suffix,
                    destinationId, customerId, "DST-" + suffix,
                    cashId, "CASH-" + suffix);

            jdbc.update("""
                    INSERT INTO banking.journal_transactions (
                        id, debit_account_id, credit_account_id,
                        amount, currency, description
                    )
                    VALUES (?, ?, ?, 100.00, 'USD', 'Test funding')
                    """,
                    UUID.randomUUID(), cashId, sourceId);

            return new Fixture(
                    customerId, subject, sourceId, destinationId);
        });
    }

    private void assertBalance(UUID accountId, String expected) {
        BigDecimal actual = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE WHEN direction = 'CREDIT'
                         THEN amount ELSE -amount END
                ), 0)
                FROM banking.ledger_entries
                WHERE account_id = ?
                """,
                BigDecimal.class, accountId);

        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void assertRecordedTransfers(Fixture fixture, long expected) {
        assertEquals(
                expected,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM banking.journal_transactions
                        WHERE debit_account_id = ?
                        """,
                        Long.class, fixture.source()));

        assertEquals(
                expected,
                jdbc.queryForObject("""
                        SELECT count(*)
                        FROM banking.transfer_requests
                        WHERE customer_id = ?
                        """,
                        Long.class, fixture.customerId()));
    }

    private record Fixture(
            UUID customerId,
            String subject,
            UUID source,
            UUID destination
    ) {
    }

    private record Outcome(
            TransferService.Result result,
            String rejection
    ) {
    }
}
