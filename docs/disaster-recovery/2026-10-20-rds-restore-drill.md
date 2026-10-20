# RDS snapshot restore drill — 2026-10-20

## Objective
Verify that an automated snapshot can restore into a separate private
RDS instance and that the recovered banking data is readable and
internally consistent.

## Source and restore target
- Source: securebank-next-dev-postgres
- Snapshot: rds:securebank-next-dev-postgres-2026-10-20-02-01
- Snapshot timestamp: 2026-10-20T02:01:17.662Z
- Target: securebank-next-dev-restore-drill-20261020
- PostgreSQL: 17.11
- Instance class: db.t4g.micro
- Storage: encrypted gp3, 20 GiB
- Public access: disabled
- Parameter group: securebank-next-dev-postgres-pg17, in-sync
- Target observed available by: 2026-10-20T14:02:11Z

The application continued using the original database endpoint.

## Validation
A temporary Kubernetes client connected to the restored endpoint as
banking_app using sslmode=verify-full and the RDS CA bundle.

- TLS 1.3 connection confirmed.
- Read-only transactions confirmed.
- Banking tables and ledger_entries view present.
- Customers: 1.
- Accounts: 3 — two CUSTOMER_LIABILITY and one INTERNAL_ASSET, all USD.
- Journal transactions: 28.
- Ledger entries: 56.
- Transfer requests: 26.
- Sum of journal transaction amounts: USD 11,435.00.
  This is transaction volume, not an account balance.
- Latest recovered transaction timestamp: 2026-10-20T00:07:30.729726Z.

All five integrity checks returned zero violations:
- Invalid journal amounts.
- Identical debit and credit accounts.
- Missing accounts or mismatched transaction currencies.
- Missing transfer customers or journal transactions.
- Invalid account ownership.

The ledger_entries view emits one debit and one credit per journal
transaction. The entry count is consistent with that definition.

## Scope and limitations
This drill verified snapshot restoration, database connectivity,
certificate verification, data readability, and the listed integrity checks.

It did not test application cutover, point-in-time recovery, or recovery
after permanent loss of the encryption key.

Transaction completeness was not proven against a snapshot-time baseline.
RTO was not measured precisely because the restore request timestamp was
not captured in this report. No achieved RPO is claimed.

## Cleanup
The temporary RDS instance was deleted without a final snapshot.
Its automated backups were also requested for deletion.
The deletion waiter completed successfully.
The original database remained available.

## Follow-up
- Capture restore request, availability, and validation timestamps.
- Establish a snapshot-time data baseline for completeness checks.
- Perform a separate point-in-time recovery drill.
