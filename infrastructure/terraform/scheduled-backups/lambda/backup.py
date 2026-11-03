import hashlib
import json
import logging
import os
import re

import boto3
from botocore.config import Config

logger = logging.getLogger()
logger.setLevel(logging.INFO)

rds = boto3.client(
    "rds",
    config=Config(
        connect_timeout=5,
        read_timeout=10,
        retries={"mode": "standard", "total_max_attempts": 3},
    ),
)

DATABASE = os.environ["DATABASE_IDENTIFIER"]
PREFIX = os.environ["SNAPSHOT_PREFIX"]
LAB = "scheduled-rds-backups"


def process(record):
    request = json.loads(record["body"])
    if not isinstance(request, dict):
        raise ValueError("Request must be a JSON object")

    request_id = request.get("request_id")
    if not isinstance(request_id, str) or not re.fullmatch(
        r"[A-Za-z0-9_.:-]{1,128}", request_id
    ):
        raise ValueError("A valid request_id is required")

    suffix = hashlib.sha256(request_id.encode()).hexdigest()[:32]
    snapshot_id = f"{PREFIX}-{suffix}"

    try:
        response = rds.create_db_snapshot(
            DBInstanceIdentifier=DATABASE,
            DBSnapshotIdentifier=snapshot_id,
            Tags=[
                {"Key": "Project", "Value": "securebank-next"},
                {"Key": "Environment", "Value": "dev"},
                {"Key": "Lab", "Value": LAB},
                {"Key": "ManagedBy", "Value": "backup-lambda"},
                {"Key": "RequestId", "Value": request_id},
            ],
        )
        snapshot = response["DBSnapshot"]

    except rds.exceptions.DBSnapshotAlreadyExistsFault:
        snapshot = rds.describe_db_snapshots(
            DBSnapshotIdentifier=snapshot_id
        )["DBSnapshots"][0]

        tags = {
            item["Key"]: item["Value"]
            for item in rds.list_tags_for_resource(
                ResourceName=snapshot["DBSnapshotArn"]
            )["TagList"]
        }

        if (
            snapshot["DBInstanceIdentifier"] != DATABASE
            or tags.get("Lab") != LAB
            or tags.get("RequestId") != request_id
            or tags.get("ManagedBy") != "backup-lambda"
        ):
            raise ValueError("Existing snapshot does not match this request")

        if snapshot["Status"] not in {"creating", "available"}:
            raise RuntimeError(
                f"Existing snapshot has unexpected status: {snapshot['Status']}"
            )

    logger.info(
        "Snapshot request accepted: snapshot=%s status=%s",
        snapshot_id,
        snapshot["Status"],
    )


def handler(event, context):
    failures = []

    for record in event["Records"]:
        try:
            process(record)
        except Exception:
            logger.exception(
                "Backup request failed: message_id=%s", record["messageId"]
            )
            failures.append({"itemIdentifier": record["messageId"]})

    return {"batchItemFailures": failures}
