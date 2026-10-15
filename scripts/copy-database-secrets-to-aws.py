import base64
import json
import subprocess

import boto3


def capture(command):
    return subprocess.run(
        command, check=True, capture_output=True, text=True
    ).stdout.strip()


def main():
    if capture(["kubectl", "config", "current-context"]) != "securebank-next-dev":
        raise RuntimeError("Unexpected Kubernetes context")

    session = boto3.Session(region_name="us-east-1")
    if session.client("sts").get_caller_identity()["Account"] != "535079144516":
        raise RuntimeError("Unexpected AWS account")

    manager = session.client("secretsmanager")
    arns = json.loads(capture([
        "terraform", "-chdir=infrastructure/terraform/secrets",
        "output", "-json", "secret_arns"
    ]))

    sources = [
        ("banking-app", "securebank", "securebank-db-app",
         "BANKING_APP_PASSWORD"),
        ("banking-migrator", "securebank", "securebank-db-migrator",
         "BANKING_MIGRATOR_PASSWORD"),
        ("keycloak-db", "identity", "keycloak-db", "KC_DB_PASSWORD"),
    ]

    pending = []

    # Check all sources and destinations before writing anything.
    for target, namespace, name, key in sources:
        source = json.loads(capture([
            "kubectl", "get", "secret", name,
            "-n", namespace, "-o", "json"
        ]))
        password = base64.b64decode(
            source["data"][key], validate=True
        ).decode("utf-8")
        if not password:
            raise RuntimeError("Empty source credential")

        payload = {key: password}
        arn = arns[target]
        metadata = manager.describe_secret(SecretId=arn)
        versions = metadata.get("VersionIdsToStages", {})

        if any("AWSCURRENT" in stages for stages in versions.values()):
            current = manager.get_secret_value(
                SecretId=arn, VersionStage="AWSCURRENT"
            )
            if json.loads(current["SecretString"]) != payload:
                raise RuntimeError("Destination differs; refusing overwrite")
            print(f"Already matches: {target}")
        elif versions:
            raise RuntimeError("Destination has versions but no AWSCURRENT")
        else:
            pending.append((target, arn, payload))

    for target, arn, payload in pending:
        result = manager.put_secret_value(
            SecretId=arn,
            SecretString=json.dumps(payload)
        )
        saved = manager.get_secret_value(
            SecretId=arn, VersionId=result["VersionId"]
        )
        if json.loads(saved["SecretString"]) != payload:
            raise RuntimeError("Stored value verification failed")
        print(f"Copied and verified: {target}")

    print("Complete. Existing Kubernetes credentials were not changed.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        raise SystemExit(
            "Copy stopped. Check AWS access, source Secret names, and "
            "destination metadata. Existing AWS values are never overwritten "
            "when they differ; successful copies can be safely rechecked."
        ) from None
