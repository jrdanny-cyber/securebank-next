CREATE TABLE banking.customers (
    id UUID PRIMARY KEY,
    identity_subject VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT customers_name_not_blank
        CHECK (length(trim(display_name)) > 0),

    CONSTRAINT customers_status_valid
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE TABLE banking.accounts (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES banking.customers(id),
    account_reference VARCHAR(32) NOT NULL UNIQUE,
    account_name VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT accounts_name_not_blank
        CHECK (length(trim(account_name)) > 0),

    CONSTRAINT accounts_currency_valid
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT accounts_status_valid
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),

    CONSTRAINT accounts_version_valid
        CHECK (version >= 0)
);

CREATE INDEX accounts_customer_id_idx
    ON banking.accounts(customer_id);

GRANT SELECT, INSERT, UPDATE
    ON banking.customers, banking.accounts
    TO banking_app;
