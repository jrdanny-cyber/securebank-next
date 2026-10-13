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
    --strict

  helm template securebank "$CHART" \
    --namespace securebank \
    --values "$FIXTURES" \
    --set "api.autoscaling.enabled=$enabled" \
    --set "ingress.enabled=$enabled" \
    --set "migration.enabled=$enabled" \
    > /dev/null
done
