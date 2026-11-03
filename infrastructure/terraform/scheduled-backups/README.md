# Scheduled RDS backup automation lab

Temporary DevOps exercise using EventBridge Scheduler, SQS, Lambda,
SNS, CloudWatch, and Terraform.

## Workflow

- A daily schedule sends a backup request to SQS.
- A Lambda validates the request and starts a manual RDS snapshot.
- A deterministic snapshot name prevents duplicate snapshots for retries
  of the same request ID.
- A second Lambda checks snapshots every five minutes and publishes
  completion or failure notifications to SNS.
- Snapshot tags suppress repeated notifications during normal operation.
- Failed requests reach a dead-letter queue and trigger a CloudWatch alarm.
- Scheduler delivery failures use a separate dead-letter queue.

## Validation completed

- Manual snapshot creation.
- Duplicate-request handling.
- SNS email delivery.
- Invalid-request retries, dead-letter routing, and alarm notification.
- Completion notification and suppression on a repeat monitor invocation.
- Recurring monitor execution.
- One-time Scheduler delivery using the corrected daily target input,
  followed by successful snapshot completion.

Terraform jsonencode escaped the Scheduler placeholder's angle brackets.
The daily target input uses a literal JSON heredoc to preserve the placeholder.

## Limitations

- No database restore test was performed.
- Notifications are at least once: publishing followed by a tagging failure
  can produce duplicate emails.
- Stalled-snapshot detection and automated retention cleanup were not implemented.
- The scheduler dead-letter queue has no dedicated alarm.
- This lab supplements, rather than replaces, RDS automated backups.

## Packaging

Run from the repository root before Terraform validation or deployment:

```bash
python - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path("infrastructure/terraform/scheduled-backups")
for name in ("backup", "monitor"):
    source = root / "lambda" / f"{name}.py"
    compile(source.read_text(), str(source), "exec")
    with ZipFile(root / f"{name}.zip", "w", ZIP_DEFLATED) as archive:
        archive.write(source, f"{name}.py")
PY
