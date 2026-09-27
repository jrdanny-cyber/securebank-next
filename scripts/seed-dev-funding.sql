\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF (
        SELECT count(*)
        FROM banking.accounts
        WHERE customer_id = '10000000-0000-4000-8000-000000000001'
          AND id IN (
              '20000000-0000-4000-8000-000000000001',
              '20000000-0000-4000-8000-000000000002'
          )
          AND currency = 'USD'
          AND status = 'ACTIVE'
          AND account_kind = 'CUSTOMER_LIABILITY'
    ) <> 2 THEN
        RAISE EXCEPTION
            'Expected two active USD demo accounts for Alice';
    END IF;
END
$$;

INSERT INTO banking.accounts (
    id,
    customer_id,
    account_reference,
    account_name,
    currency,
    account_kind
)
VALUES (
    '30000000-0000-4000-8000-000000000001',
    NULL,
    'SB-INTERNAL-CASH-USD',
    'Demo cash asset',
    'USD',
    'INTERNAL_ASSET'
);

INSERT INTO banking.journal_transactions (
    id,
    debit_account_id,
    credit_account_id,
    amount,
    currency,
    description
)
VALUES
(
    '40000000-0000-4000-8000-000000000001',
    '30000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000001',
    1000.00,
    'USD',
    'Local demo funding - Everyday Account'
),
(
    '40000000-0000-4000-8000-000000000002',
    '30000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000002',
    500.00,
    'USD',
    'Local demo funding - Savings Account'
);

COMMIT;
