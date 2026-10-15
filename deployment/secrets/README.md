# Database secret synchronization

AWS Secrets Manager is the durable source for the existing database passwords.

Mappings:
- securebank-next/dev/banking-app -> securebank/securebank-db-app
- securebank-next/dev/banking-migrator -> securebank/securebank-db-migrator
- securebank-next/dev/keycloak-db -> identity/keycloak-db

External Secrets Operator chart version: 2.12.0.

Two namespace-scoped controller releases synchronize the secrets.
Each namespace uses a secret-sync service account with a dedicated IRSA role.
IAM policies grant only GetSecretValue and DescribeSecret for assigned ARNs.

The securebank operator release manages shared CRDs and the webhook.
The identity release depends on those shared components.

## Recovery

1. Restore or provision the infrastructure and cluster.
2. Reconcile the synchronization roles with the new cluster OIDC provider.
3. Install the securebank operator release, then the identity release.
4. Apply the namespace-specific ServiceAccounts and SecretStores.
5. Create empty Opaque target Secrets if they do not exist.
6. Apply the live ExternalSecret manifests.
7. Wait for all ExternalSecrets to report Ready before deploying applications.

Targets use creationPolicy: Merge, so target Secrets must already exist.
Never replace an existing populated Secret with an empty one.

Verification manifests are temporary checks, not normal recovery resources.

## Credential handling

Terraform manages secret metadata and IAM permissions, not password values.
The initial copy script preserves passwords and refuses differing AWS values.

Synchronization does not rotate PostgreSQL passwords.
Password rotation must coordinate database changes, AWS secret versions,
synchronization, and application restarts.

Applications consuming credentials through environment variables must restart
to pick up changed values. Removing these ExternalSecrets does not delete
the existing Merge targets.
