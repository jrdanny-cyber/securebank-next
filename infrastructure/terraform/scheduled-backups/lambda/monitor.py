import logging
import os

import boto3
from botocore.config import Config

logger = logging.getLogger()
logger.setLevel(logging.INFO)

config = Config(
    connect_timeout=5,
    read_timeout=10,
    retries={"mode": "standard", "total_max_attempts": 3},
)

rds = boto3.client("rds", config=config)
sns = boto3.client("sns", config=config)

DATABASE = os.environ["DATABASE_IDENTIFIER"]
PREFIX = os.environ["SNAPSHOT_PREFIX"]
TOPIC = os.environ["NOTIFICATION_TOPIC_ARN"]
NOTIFIED_TAG = "BackupLabNotifiedStatus"


def handler(event, context):
    notified = 0
    errors = 0

    pages = rds.get_paginator("describe_db_snapshots").paginate(
        DBInstanceIdentifier=DATABASE,
        SnapshotType="manual",
    )

    for page in pages:
        for snapshot in page["DBSnapshots"]:
            snapshot_id = snapshot["DBSnapshotIdentifier"]

            if not snapshot_id.startswith(PREFIX + "-"):
                continue

            try:
                arn = snapshot["DBSnapshotArn"]
                tags = {
                    tag["Key"]: tag["Value"]
                    for tag in rds.list_tags_for_resource(
                        ResourceName=arn
                    )["TagList"]
                }

                if (
                    tags.get("Lab") != "scheduled-rds-backups"
                    or tags.get("ManagedBy") != "backup-lambda"
                    or not tags.get("RequestId")
                ):
                    continue

                status = snapshot["Status"]
                if status not in {"available", "failed"}:
                    logger.info(
                        "Snapshot still pending: snapshot=%s status=%s",
                        snapshot_id,
                        status,
                    )
                    continue

                if tags.get(NOTIFIED_TAG) == status:
                    continue

                sns.publish(
                    TopicArn=TOPIC,
                    Subject=f"SecureBank backup lab: snapshot {status}",
                    Message=(
                        f"Database: {DATABASE}\n"
                        f"Snapshot: {snapshot_id}\n"
                        f"Status: {status}\n"
                        f"Request: {tags['RequestId']}\n\n"
                        "An available snapshot has completed creation. "
                        "This is not confirmation of a successful restore test."
                    ),
                )

                rds.add_tags_to_resource(
                    ResourceName=arn,
                    Tags=[{"Key": NOTIFIED_TAG, "Value": status}],
                )

                notified += 1
                logger.info(
                    "Notification published: snapshot=%s status=%s",
                    snapshot_id,
                    status,
                )

            except Exception:
                errors += 1
                logger.exception(
                    "Snapshot monitoring failed: snapshot=%s", snapshot_id
                )

    if errors:
        raise RuntimeError(f"Monitoring failed for {errors} snapshot(s)")

    return {"notifications_sent": notified}
