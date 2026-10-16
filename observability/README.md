# Development monitoring

Chart: kube-prometheus-stack 92.8.0
Namespace and Helm release: monitoring

## Configuration

- Prometheus retention: 24 hours, capped at 3GB of retained TSDB blocks.
- Encrypted gp3 PVCs: Prometheus 5Gi, Grafana 2Gi, Alertmanager 1Gi.
- Private ClusterIP Services accessed through localhost port-forwarding.
- Grafana administrator credentials come from the grafana-admin Secret.
- Inaccessible EKS control-plane scrape configurations are disabled.

## Validation

- All monitoring pods became Ready.
- Grafana sidecars initially exceeded their 64Mi memory limits.
- Increasing each sidecar request to 128Mi and limit to 256Mi resolved the issue.
- Replacement Grafana pod remained 3/3 Ready with no restarts for 14 hours.
- No failing scrape targets were reported by count(up == 0).

## Alerts and capacity

Watchdog intentionally fires continuously.

KubeCPUOvercommit and KubeMemoryOvercommit remain enabled.
At validation time, total requests exceeded capacity remaining after one
worker-node failure:

- CPU: 1.97 cores requested versus 1.93 cores remaining.
- Memory: 3.738 GiB requested versus 3.068 GiB remaining.

This development cluster is not sized to accommodate all current requests
after losing one node. EBS Availability Zone placement further constrains
stateful recovery.

## Storage and credentials

The gp3-encrypted StorageClass uses reclaimPolicy: Delete.
Deleting its PVCs deletes the backing volumes.

Prometheus retention limits are not backups.
Grafana credentials must be restored separately before reinstalling the chart.
Alert delivery to an external destination has not yet been configured.

## Banking API metrics validation

- Metrics are scraped on management port 9001.
- Both API replicas reported up=1, and JVM metrics were available.
- A temporary client Deployment reached the frontend health endpoint.
- The client resolved the metrics Service, but its metrics request timed out.
- The API ingress policy permits metrics traffic from the selected Prometheus
  pods in monitoring and application traffic from the release's frontend pods.
- The temporary client Deployment was removed after testing.

The deployment script uses Helm's server-connected dry run for application
preflight. A kubectl apply dry run against the Helm-managed Deployment merged
the old TCP probe with the new HTTP probe and produced an invalid combined
handler. The migration Job retains its server-side create dry run.

Helm preflight does not guarantee that every actual update will pass admission.
