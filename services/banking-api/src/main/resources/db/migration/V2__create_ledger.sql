ALTER TABLE banking.accounts
    ADD CONSTRAINT accounts_id_currency_unique UNIQUE (id, currency);

CREATE TABLE banking.journal_transactions (
    id UUID PRIMARY KEY,
    debit_account_id UUID NOT NULL,
    credit_account_id UUID NOT NULL,
    amount NUMERIC NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT journal_different_accounts
        CHECK (debit_account_id <> credit_account_id),

    CONSTRAINT journal_supported_currency
        CHECK (currency = 'USD'),

    CONSTRAINT journal_amount_valid
        CHECK (
            amount > 0
            AND amount <= 99999999999999999.99
            AND amount = trunc(amount, 2)
        ),

    CONSTRAINT journal_description_not_blank
        CHECK (length(trim(description)) > 0),

    CONSTRAINT journal_debit_account_currency_fk
        FOREIGN KEY (debit_account_id, currency)
        REFERENCES banking.accounts (id, currency),

    CONSTRAINT journal_credit_account_currency_fk
        FOREIGN KEY (credit_account_id, currency)
        REFERENCES banking.accounts (id, currency)
);

CREATE INDEX journal_debit_account_time_idx
    ON banking.journal_transactions (debit_account_id, recorded_at, id);

CREATE INDEX journal_credit_account_time_idx
    ON banking.journal_transactions (credit_account_id, recorded_at, id);

CREATE VIEW banking.ledger_entries AS
SELECT
    id AS transaction_id,
    debit_account_id AS account_id,
    'DEBIT'::VARCHAR(6) AS direction,
    amount,
    currency,
    description,
    recorded_at
FROM banking.journal_transactions

UNION ALL

SELECT
    id AS transaction_id,
    credit_account_id AS account_id,
    'CREDIT'::VARCHAR(6) AS direction,
    amount,
    currency,
    description,
    recorded_at
FROM banking.journal_transactions;

REVOKE ALL ON banking.journal_transactions FROM PUBLIC, banking_app;
REVOKE ALL ON banking.ledger_entries FROM PUBLIC, banking_app;

GRANT SELECT, INSERT
    ON banking.journal_transactions
    TO banking_app;

GRANT SELECT
    ON banking.ledger_entries
    TO banking_app;
