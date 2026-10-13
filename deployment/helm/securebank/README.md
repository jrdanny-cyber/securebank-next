# SecureBank application chart

Deploys the frontend and banking API, with optional API HPA and HTTPS ALB
Ingress.

## Required before deployment

- Publish application images through the successful main-branch CI pipeline.
  Use the published ECR digests in web.image.digest and api.image.digest.
  The frontend image must include runtime authentication configuration support.
- Provide a PostgreSQL endpoint reachable from the API pods.
- Create the banking schema and run migrations before starting the API.
  Flyway is disabled in API pods. Run the optional migration Job explicitly
and verify completion before rolling out the application.
- Create a Secret in the release namespace containing BANKING_APP_PASSWORD,
  or configure api.database.passwordKey for a different key.
- Supply the database URL and existing Secret name.
- Provide a browser-reachable Keycloak URL and the API issuer and JWK endpoints.
  Configure the securebank-web client with the deployed redirect URI and origin.
- When enabling Ingress, provide a hostname, an appropriate ACM certificate
  in the ALB region, and explicit allowed inbound CIDRs.
- Point the application hostname to the provisioned ALB.
- Metrics Server is required when enabling the API HPA.

## Security and runtime behavior

- Application containers run as non-root with read-only root filesystems.
- Writable temporary storage is mounted at /tmp.
- Pods do not automatically mount Kubernetes service account tokens.
- API pods receive application credentials, not migration credentials.
- Runtime frontend authentication settings are public configuration.
  Never put passwords or client secrets in the ConfigMap.
- API readiness and startup use /actuator/health.
  Liveness currently uses TCP and does not detect every application failure.
- Updating the frontend ConfigMap triggers a frontend rollout.
- Rotating the database Secret requires restarting API pods.
- Keep the API Service available while Nginx starts: its upstream hostname
  is resolved at startup.

## Render-only fixtures

ci/render-values.yaml contains fake digests and endpoints.
Use it only for linting and template validation, never for installation.

## Validation completed

- Strict Helm lint with HPA and Ingress disabled.
- Strict Helm lint with HPA and Ingress enabled.
- Helm template rendering.
- EKS server-side dry-run validation of all rendered resources.

Application startup, authentication, database access, migrations, HTTPS routing,
and application HPA behavior remain to be tested with real environment values.

## Database migrations

The API image supports --migrate-only. This mode runs Flyway without starting
Spring or the web server, then exits. Migration failures return a nonzero status.

The banking schema and restricted database roles must already exist.
The runner does not create schemas or bootstrap roles.

Configure migration.existingSecret with a separate Secret containing the
migrator password. API pods receive only the application password.

Render only templates/migration-job.yaml with migration.enabled=true.
Apply the rendered Job separately, wait for completion, and inspect its logs.
Stop deployment if the Job fails or times out.

Deploy the application with migration.enabled=false after migration succeeds.
The Job is not a Helm hook and does not automatically enforce rollout order.

Before a subsequent migration run, inspect and remove the previous completed
or failed Job. Job pod templates are immutable. Never delete an active
migration Job merely to start another run.

Use the same API image digest for migrations and application deployment.
Migrations must remain compatible with the previous API version during rollout.
Rolling back an application does not roll back database migrations.
