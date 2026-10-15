data "aws_eks_cluster" "this" {
  name = "securebank-next-dev"
}

data "aws_iam_openid_connect_provider" "cluster" {
  url = data.aws_eks_cluster.this.identity[0].oidc[0].issuer
}

locals {
  oidc_host = trimprefix(
    data.aws_eks_cluster.this.identity[0].oidc[0].issuer,
    "https://"
  )

  sync_access = {
    securebank = [
      aws_secretsmanager_secret.database["banking-app"].arn,
      aws_secretsmanager_secret.database["banking-migrator"].arn
    ]
    identity = [
      aws_secretsmanager_secret.database["keycloak-db"].arn
    ]
  }
}

resource "aws_iam_role" "secret_sync" {
  for_each = local.sync_access

  name = "securebank-next-dev-${each.key}-secret-sync"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = data.aws_iam_openid_connect_provider.cluster.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_host}:aud" = "sts.amazonaws.com"
          "${local.oidc_host}:sub" = "system:serviceaccount:${each.key}:secret-sync"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "secret_sync" {
  for_each = local.sync_access

  name = "read-assigned-database-secrets"
  role = aws_iam_role.secret_sync[each.key].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ]
      Resource = each.value
    }]
  })
}

output "secret_sync_role_arns" {
  value = {
    for namespace, role in aws_iam_role.secret_sync :
    namespace => role.arn
  }
}
