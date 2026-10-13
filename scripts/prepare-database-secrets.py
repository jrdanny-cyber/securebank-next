import json
import secrets
import subprocess


def capture(command):
    return subprocess.run(
        command, check=True, capture_output=True, text=True
    ).stdout.strip()


def main():
    context = capture(["kubectl", "config", "current-context"])
    if context != "securebank-next-dev":
        raise RuntimeError("Expected Kubernetes context securebank-next-dev")

    names = [
        "securebank-db-app",
        "securebank-db-migrator",
        "securebank-db-bootstrap-admin",
    ]

    existing = json.loads(capture([
        "kubectl", "get", "secrets", "-n", "securebank", "-o", "json"
    ]))
    existing_names = {item["metadata"]["name"] for item in existing["items"]}
    if existing_names.intersection(names):
        raise RuntimeError(
            "Database Secrets already exist; refusing to replace credentials"
        )

    secret_arn = capture([
        "terraform", "-chdir=infrastructure/terraform/database",
        "output", "-raw", "administrator_secret_arn"
    ])
    administrator = json.loads(capture([
        "aws", "secretsmanager", "get-secret-value",
        "--region", "us-east-1",
        "--secret-id", secret_arn,
        "--query", "SecretString", "--output", "text",
        "--no-cli-pager"
    ]))

    if administrator.get("username") != "banking_admin":
        raise RuntimeError("Unexpected administrator username")

    credentials = [
        {"BANKING_APP_PASSWORD": secrets.token_hex(32)},
        {"BANKING_MIGRATOR_PASSWORD": secrets.token_hex(32)},
        {"PGPASSWORD": administrator["password"]},
    ]

    for name, values in zip(names, credentials):
        manifest = {
            "apiVersion": "v1",
            "kind": "Secret",
            "metadata": {"name": name, "namespace": "securebank"},
            "type": "Opaque",
            "stringData": values,
        }
        subprocess.run(
            ["kubectl", "create", "-f", "-"],
            input=json.dumps(manifest),
            text=True,
            check=True,
            capture_output=True,
        )
        print(f"Created Secret: {name}")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        raise SystemExit(
            "Secret preparation failed. Inspect existing Secret names before "
            "retrying; do not delete or replace credentials blindly."
        ) from None
