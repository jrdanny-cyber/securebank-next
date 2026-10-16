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
