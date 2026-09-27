ALTER TABLE banking.accounts
    ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE banking.accounts
    ADD COLUMN account_kind VARCHAR(24)
        NOT NULL DEFAULT 'CUSTOMER_LIABILITY';

ALTER TABLE banking.accounts
    ADD CONSTRAINT accounts_kind_and_owner_valid
    CHECK (
        (
            account_kind = 'CUSTOMER_LIABILITY'
            AND customer_id IS NOT NULL
        )
        OR
        (
            account_kind = 'INTERNAL_ASSET'
            AND customer_id IS NULL
        )
    );
