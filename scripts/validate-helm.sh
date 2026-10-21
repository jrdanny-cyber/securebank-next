#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="$REPO_ROOT/deployment/helm/securebank"
FIXTURES="$CHART/ci/render-values.yaml"

for enabled in false true; do
  helm lint "$CHART" \
    --values "$FIXTURES" \
    --set "api.autoscaling.enabled=$enabled" \
    --set "ingress.enabled=$enabled" \
    --set "migration.enabled=$enabled" \
    --set "api.metrics.enabled=$enabled" \
    --set "api.networkPolicy.enabled=$enabled" \
    --strict

  helm template securebank "$CHART" \
    --namespace securebank \
    --values "$FIXTURES" \
    --set "api.autoscaling.enabled=$enabled" \
    --set "ingress.enabled=$enabled" \
    --set "migration.enabled=$enabled" \
    --set "api.networkPolicy.enabled=$enabled" \
    --set "api.metrics.enabled=$enabled" \
    > /dev/null
done

DEV_VALUES="$REPO_ROOT/deployment/environments/dev-localhost.yaml"

echo "==> Validating development deployment values"

  helm lint "$CHART" \
    --values "$DEV_VALUES" \
    --strict

  helm template securebank "$CHART" \
    --namespace securebank \
    --values "$DEV_VALUES" \
    > /dev/null

echo "==> Validating development migration rendering"

  helm template securebank "$CHART" \
    --namespace securebank \
    --values "$DEV_VALUES" \
    --set migration.enabled=true \
    --set-string migration.jobName=securebank-migration-validation \
    --set-string migration.existingSecret=securebank-db-migrator \
    --show-only templates/migration-job.yaml \
    > /dev/null
