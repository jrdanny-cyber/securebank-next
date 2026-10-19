# EKS node and RDS access incident — 2026-10-19

## Impact
Both EKS worker nodes became NotReady, leaving application workloads
unavailable or Pending. After node recovery, both banking API replicas
failed during startup because PostgreSQL connections timed out.

## Confirmed findings
- Node startup logs reported AssumeRoleUnauthorizedAccess when retrieving
  EC2 instance-role credentials.
- The inspected node role trust policy and instance-profile association
  were correctly configured.
- On one rebooted node, nodeadm-config failed during the credential outage,
  leaving nodeadm-run, kubelet, and containerd inactive.
- RDS entered inaccessible-encryption-credentials-recoverable after losing
  access to its encryption key.
- During investigation, that AWS-managed KMS key was Enabled.

## Recovery
- Added AmazonSSMManagedInstanceCore to the node role through Terraform
  to support host diagnostics and recovery.
- Refreshed the instance-profile association on the first node.
- Once SSM access was available, retried nodeadm-config and nodeadm-run
  on that node; kubelet and containerd became active.
- Started the existing RDS instance using start-db-instance.
- RDS completed recovery and returned to available.
- Banking API containers recovered through their existing restart cycle.

## Validation
- Both worker nodes Ready.
- Both banking API replicas Running and Ready.
- Keycloak deployment available.
- User confirmed application checks passed.
- Terraform plan reported no changes after recovery.

## Cause and follow-up
The original cause of the EC2 role-credential and RDS KMS-access failures
is unconfirmed. Their timing does not establish a shared cause.

Review AWS Health and relevant CloudTrail events around the incident.
Retain diagnostic evidence and add tested operational alert delivery.
