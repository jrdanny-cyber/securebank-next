package com.securebank.banking.accounts;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class AccountHistoryRepository {

    private final JdbcTemplate jdbc;

    public AccountHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AccountHistoryPage find(
            String identitySubject,
            UUID accountId,
            int page,
            int size
    ) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be between 0 and 10000; size between 1 and 100");
        }

        Boolean owned = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM banking.accounts a
                    JOIN banking.customers c ON c.id = a.customer_id
                    WHERE a.id = ?
                      AND c.identity_subject = ?
                )
                """,
                Boolean.class,
                accountId,
                identitySubject);

        if (!Boolean.TRUE.equals(owned)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Account not found");
        }

        var entries = jdbc.query("""
                SELECT
                    e.transaction_id,
                    e.direction,
                    e.amount,
                    e.currency,
                    e.description,
                    e.recorded_at
                FROM banking.ledger_entries e
                JOIN banking.accounts a ON a.id = e.account_id
                JOIN banking.customers c ON c.id = a.customer_id
                WHERE a.id = ?
                  AND c.identity_subject = ?
                ORDER BY e.recorded_at DESC, e.transaction_id DESC
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new AccountHistoryPage.Entry(
                        rs.getObject("transaction_id", UUID.class),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount")
                                .setScale(2)
                                .toPlainString(),
                        rs.getString("currency"),
                        rs.getString("description"),
                        rs.getTimestamp("recorded_at").toInstant()),
                accountId,
                identitySubject,
                size + 1,
                (long) page * size);

        boolean hasNext = entries.size() > size;

        return new AccountHistoryPage(
                List.copyOf(entries.subList(
                        0, Math.min(size, entries.size()))),
                page,
                size,
                hasNext);
    }
}
