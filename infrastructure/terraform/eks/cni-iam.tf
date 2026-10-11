locals {
  cluster_oidc_issuer = aws_eks_cluster.this.identity[0].oidc[0].issuer
  cluster_oidc_host   = trimprefix(local.cluster_oidc_issuer, "https://")
}

resource "aws_iam_openid_connect_provider" "cluster" {
  url            = local.cluster_oidc_issuer
  client_id_list = ["sts.amazonaws.com"]
}

resource "aws_iam_role" "vpc_cni" {
  name = "${var.cluster_name}-vpc-cni"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.cluster.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.cluster_oidc_host}:aud" = "sts.amazonaws.com"
          "${local.cluster_oidc_host}:sub" = "system:serviceaccount:kube-system:aws-node"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "vpc_cni" {
  role       = aws_iam_role.vpc_cni.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}
