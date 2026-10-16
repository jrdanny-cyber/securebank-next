#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART="$REPO_ROOT/deployment/helm/securebank"
VALUES="$REPO_ROOT/deployment/environments/dev-localhost.yaml"
NAMESPACE=securebank
RELEASE=securebank
LOCK=securebank-deployment-lock
MIGRATION_SECRET="${MIGRATION_SECRET:-securebank-db-migrator}"
MODE="${1:-deploy}"

case "$MODE" in
  deploy|--validate-only) ;;
  *) echo "Usage: bash scripts/deploy-application.sh [--validate-only]" >&2; exit 1 ;;
esac

for command in helm kubectl; do
  command -v "$command" >/dev/null || {
    echo "Missing command: $command" >&2
    exit 1
  }
done

if [[ "$(kubectl config current-context)" != securebank-next-dev ]]; then
  echo "Expected Kubernetes context securebank-next-dev" >&2
  exit 1
fi

kubectl get namespace "$NAMESPACE" >/dev/null
kubectl get secret securebank-db-app "$MIGRATION_SECRET" \
  -n "$NAMESPACE" >/dev/null
kubectl get configmap securebank-rds-ca -n "$NAMESPACE" >/dev/null

WORK_DIR="$(mktemp -d)"
LOCK_HELD=false

cleanup() {
  status=$?
  trap - EXIT
  if [[ "$LOCK_HELD" == true ]]; then
    kubectl delete configmap "$LOCK" -n "$NAMESPACE" \
      --ignore-not-found >/dev/null || true
  fi
  rm -rf "$WORK_DIR"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

JOB_NAME="securebank-migration-$(date -u +%Y%m%d%H%M%S)-$RANDOM"

COMMON=(
  "$RELEASE" "$CHART"
  --namespace "$NAMESPACE"
  --values "$VALUES"
)

helm lint "$CHART" --values "$VALUES" --strict

helm template "${COMMON[@]}" \
  --set migration.enabled=false \
  > "$WORK_DIR/application.yaml"

helm template "${COMMON[@]}" \
  --set migration.enabled=true \
  --set-string "migration.jobName=$JOB_NAME" \
  --set-string "migration.existingSecret=$MIGRATION_SECRET" \
  --show-only templates/migration-job.yaml \
  > "$WORK_DIR/migration.yaml"

helm upgrade --install "${COMMON[@]}" \
  --set migration.enabled=false \
  --dry-run=server \
  --hide-secret \
  > /dev/null

echo "Helm application preflight passed."

kubectl create --dry-run=server -n "$NAMESPACE" \
  -f "$WORK_DIR/migration.yaml"

if [[ "$MODE" == --validate-only ]]; then
  echo "Validation passed. No application or migration resources changed."
  exit 0
fi

kubectl create configmap "$LOCK" -n "$NAMESPACE" \
  --from-literal="migration-job=$JOB_NAME" >/dev/null
LOCK_HELD=true

kubectl create -n "$NAMESPACE" -f "$WORK_DIR/migration.yaml"

deadline=$((SECONDS + 360))
while true; do
  conditions="$(kubectl get job "$JOB_NAME" -n "$NAMESPACE" \
    -o jsonpath='{range .status.conditions[*]}{.type}={.status}{"\n"}{end}')"

  if [[ "$conditions" == *"Complete=True"* ]]; then
    kubectl logs "job/$JOB_NAME" -n "$NAMESPACE"
    break
  fi

  if [[ "$conditions" == *"Failed=True"* ]] || (( SECONDS >= deadline )); then
    kubectl logs "job/$JOB_NAME" -n "$NAMESPACE" || true
    kubectl describe job "$JOB_NAME" -n "$NAMESPACE" || true
    echo "Migration failed or timed out. Application rollout stopped." >&2
    exit 1
  fi

  sleep 5
done

helm upgrade --install "${COMMON[@]}" \
  --set migration.enabled=false \
  --wait --timeout 10m

kubectl get deployments -n "$NAMESPACE"
echo "Deployment completed. Migration Job retained: $JOB_NAME"
