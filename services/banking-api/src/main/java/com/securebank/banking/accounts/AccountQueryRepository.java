package com.securebank.banking.accounts;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public class AccountQueryRepository {

    private final JdbcClient jdbc;

    public AccountQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AccountSummary> findByIdentitySubject(String identitySubject) {
        Assert.hasText(identitySubject, "Identity subject must not be blank");

        return jdbc.sql("""
                SELECT
                    a.id,
                    a.account_reference,
                    a.account_name,
                    a.currency,
                    a.status,
                    COALESCE((
                        SELECT SUM(
                            CASE
                                WHEN e.direction = 'CREDIT' THEN e.amount
                                ELSE -e.amount
                            END
                        )
                        FROM banking.ledger_entries e
                        WHERE e.account_id = a.id
                          AND e.currency = a.currency
                    ), 0) AS balance
                FROM banking.accounts a
                JOIN banking.customers c ON c.id = a.customer_id
                WHERE c.identity_subject = :identitySubject
                ORDER BY a.account_reference
                """)
                .param("identitySubject", identitySubject)
                .query((rs, rowNum) -> new AccountSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("account_reference"),
                        rs.getString("account_name"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getBigDecimal("balance")
                                .setScale(2)
                                .toPlainString()
                ))
                .list();
    }
}
