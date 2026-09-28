package com.securebank.banking.transfers;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.securebank.banking.ledger.PostingAmount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class TransferService {

    private final JdbcTemplate jdbc;

    public TransferService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public Result transfer(
            String identitySubject,
            UUID idempotencyKey,
            UUID sourceAccountId,
            UUID destinationAccountId,
            PostingAmount money,
            String description
    ) {
        Assert.hasText(identitySubject, "Identity subject is required");
        Objects.requireNonNull(idempotencyKey, "Request key is required");
        Objects.requireNonNull(sourceAccountId, "Source account is required");
        Objects.requireNonNull(destinationAccountId, "Destination account is required");
        Objects.requireNonNull(money, "Posting amount is required");
        Assert.hasText(description, "Description is required");

        String normalizedDescription = description.strip();

        if (normalizedDescription.length() > 255) {
            throw new IllegalArgumentException("Description is too long");
        }

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new Rejected("SAME_ACCOUNT");
        }

        var customerIds = jdbc.query("""
                SELECT id
                FROM banking.customers
                WHERE identity_subject = ?
                  AND status = 'ACTIVE'
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                identitySubject);

        if (customerIds.isEmpty()) {
            throw new Rejected("CUSTOMER_UNAVAILABLE");
        }

        UUID customerId = customerIds.getFirst();

        var previous = jdbc.query("""
                SELECT j.id, j.debit_account_id, j.credit_account_id,
                       j.amount, j.currency, j.description
                FROM banking.transfer_requests r
                JOIN banking.journal_transactions j
                  ON j.id = r.transaction_id
                WHERE r.customer_id = ?
                  AND r.idempotency_key = ?
                """,
                (rs, rowNum) -> new Previous(
                        rs.getObject("id", UUID.class),
                        rs.getObject("debit_account_id", UUID.class),
                        rs.getObject("credit_account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("description")),
                customerId,
                idempotencyKey);

        if (!previous.isEmpty()) {
            Previous posted = previous.getFirst();

            boolean sameRequest =
                    posted.source().equals(sourceAccountId)
                    && posted.destination().equals(destinationAccountId)
                    && posted.amount().compareTo(money.amount()) == 0
                    && posted.currency().equals(money.currency())
                    && posted.description().equals(normalizedDescription);

            if (!sameRequest) {
                throw new Rejected("IDEMPOTENCY_CONFLICT");
            }

            return new Result(posted.id(), true);
        }

        var accounts = jdbc.query("""
                SELECT id, status, currency, account_kind
                FROM banking.accounts
                WHERE customer_id = ?
                  AND id IN (?, ?)
                ORDER BY id
                FOR UPDATE
                """,
                (rs, rowNum) -> new LockedAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("status"),
                        rs.getString("currency"),
                        rs.getString("account_kind")),
                customerId,
                sourceAccountId,
                destinationAccountId);

        if (accounts.size() != 2) {
            throw new Rejected("ACCOUNT_UNAVAILABLE");
        }

        for (LockedAccount account : accounts) {
            if (!"ACTIVE".equals(account.status())
                    || !"CUSTOMER_LIABILITY".equals(account.kind())) {
                throw new Rejected("ACCOUNT_UNAVAILABLE");
            }

            if (!money.currency().equals(account.currency())) {
                throw new Rejected("CURRENCY_MISMATCH");
            }
        }

        BigDecimal balance = jdbc.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE WHEN direction = 'CREDIT'
                         THEN amount ELSE -amount END
                ), 0)
                FROM banking.ledger_entries
                WHERE account_id = ?
                  AND currency = ?
                """,
                BigDecimal.class,
                sourceAccountId,
                money.currency());

        if (balance == null || balance.compareTo(money.amount()) < 0) {
            throw new Rejected("INSUFFICIENT_FUNDS");
        }

        UUID transactionId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO banking.journal_transactions (
                    id, debit_account_id, credit_account_id,
                    amount, currency, description
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                transactionId,
                sourceAccountId,
                destinationAccountId,
                money.amount(),
                money.currency(),
                normalizedDescription);

        jdbc.update("""
                INSERT INTO banking.transfer_requests (
                    customer_id, idempotency_key, transaction_id
                )
                VALUES (?, ?, ?)
                """,
                customerId,
                idempotencyKey,
                transactionId);

        return new Result(transactionId, false);
    }

    public record Result(UUID transactionId, boolean replayed) {
    }

    public static final class Rejected extends RuntimeException {
        private final String code;

        public Rejected(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private record LockedAccount(
            UUID id,
            String status,
            String currency,
            String kind
    ) {
    }

    private record Previous(
            UUID id,
            UUID source,
            UUID destination,
            BigDecimal amount,
            String currency,
            String description
    ) {
    }
}
