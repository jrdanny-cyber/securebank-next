CREATE TABLE banking.transfer_requests (
    customer_id UUID NOT NULL
        REFERENCES banking.customers(id),

    idempotency_key UUID NOT NULL,

    transaction_id UUID NOT NULL UNIQUE
        REFERENCES banking.journal_transactions(id),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT transfer_requests_pk
        PRIMARY KEY (customer_id, idempotency_key)
);

REVOKE ALL ON banking.transfer_requests FROM PUBLIC, banking_app;

GRANT SELECT, INSERT
    ON banking.transfer_requests
    TO banking_app;
