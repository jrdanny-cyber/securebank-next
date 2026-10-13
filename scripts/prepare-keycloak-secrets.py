import json
import secrets
import subprocess


def capture(command):
    return subprocess.run(
        command, check=True, capture_output=True, text=True
    ).stdout.strip()


def main():
    if capture(["kubectl", "config", "current-context"]) != "securebank-next-dev":
        raise RuntimeError("Unexpected Kubernetes context")

    names = [
        "keycloak-db",
        "keycloak-bootstrap-admin",
        "keycloak-db-bootstrap-admin",
    ]
    existing = json.loads(capture([
        "kubectl", "get", "secrets", "-n", "identity", "-o", "json"
    ]))
    if {item["metadata"]["name"] for item in existing["items"]}.intersection(names):
        raise RuntimeError("Refusing to replace existing credentials")

    secret_arn = capture([
        "terraform", "-chdir=infrastructure/terraform/database",
        "output", "-raw", "administrator_secret_arn"
    ])
    administrator = json.loads(capture([
        "aws", "secretsmanager", "get-secret-value",
        "--region", "us-east-1", "--secret-id", secret_arn,
        "--query", "SecretString", "--output", "text", "--no-cli-pager"
    ]))
    if administrator.get("username") != "banking_admin":
        raise RuntimeError("Unexpected RDS administrator")

    values = [
        {"KC_DB_PASSWORD": secrets.token_hex(32)},
        {
            "KC_BOOTSTRAP_ADMIN_USERNAME": "bootstrap-admin",
            "KC_BOOTSTRAP_ADMIN_PASSWORD": secrets.token_hex(32),
        },
        {"PGPASSWORD": administrator["password"]},
    ]

    for name, credentials in zip(names, values):
        manifest = {
            "apiVersion": "v1",
            "kind": "Secret",
            "metadata": {"name": name, "namespace": "identity"},
            "type": "Opaque",
            "stringData": credentials,
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
            "retrying; do not replace existing credentials blindly."
        ) from None
