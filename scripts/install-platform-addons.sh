#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLATFORM_DIR="$REPO_ROOT/deployment/helm/platform"

for command in curl helm kubectl terraform; do
  command -v "$command" >/dev/null || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

source "$PLATFORM_DIR/versions.env"

EXPECTED_CONTEXT="securebank-next-dev"
CURRENT_CONTEXT="$(kubectl config current-context)"

if [[ "$CURRENT_CONTEXT" != "$EXPECTED_CONTEXT" ]]; then
  echo "Expected context: $EXPECTED_CONTEXT; found: $CURRENT_CONTEXT" >&2
  exit 1
fi

ROLE_ARN="$(terraform -chdir="$REPO_ROOT/infrastructure/terraform/eks" \
  output -raw load_balancer_controller_role_arn)"

if [[ "$ROLE_ARN" != arn:aws:iam::*:role/* ]]; then
  echo "Terraform did not return a valid IAM role ARN." >&2
  exit 1
fi

DOWNLOAD_DIR="$(mktemp -d)"
trap 'rm -rf "$DOWNLOAD_DIR"' EXIT

LBC_CHART="$DOWNLOAD_DIR/aws-load-balancer-controller-${AWS_LOAD_BALANCER_CONTROLLER_CHART_VERSION}.tgz"
METRICS_CHART="$DOWNLOAD_DIR/metrics-server-${METRICS_SERVER_CHART_VERSION}.tgz"

curl --fail --location --retry 3 \
  --connect-timeout 15 --max-time 120 \
  "https://raw.githubusercontent.com/aws/eks-charts/gh-pages/aws-load-balancer-controller-${AWS_LOAD_BALANCER_CONTROLLER_CHART_VERSION}.tgz" \
  --output "$LBC_CHART"

helm repo add metrics-server \
  https://raw.githubusercontent.com/kubernetes-sigs/metrics-server/gh-pages \
  --force-update

helm repo update metrics-server

helm pull metrics-server/metrics-server \
  --version "$METRICS_SERVER_CHART_VERSION" \
  --destination "$DOWNLOAD_DIR"

helm show chart "$LBC_CHART"
helm show chart "$METRICS_CHART"

helm upgrade --install aws-load-balancer-controller "$LBC_CHART" \
  --namespace kube-system \
  --values "$PLATFORM_DIR/aws-load-balancer-controller-values.yaml" \
  --set-string "serviceAccount.annotations.eks\.amazonaws\.com/role-arn=$ROLE_ARN" \
  --wait --timeout 5m

helm upgrade --install metrics-server "$METRICS_CHART" \
  --namespace kube-system \
  --values "$PLATFORM_DIR/metrics-server-values.yaml" \
  --wait --timeout 5m

kubectl get deployment \
  aws-load-balancer-controller metrics-server -n kube-system

kubectl get apiservice v1beta1.metrics.k8s.io
kubectl top nodes
