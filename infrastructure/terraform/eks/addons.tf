resource "aws_eks_addon" "vpc_cni" {
  cluster_name             = aws_eks_cluster.this.name
  addon_name               = "vpc-cni"
  addon_version            = "v1.22.4-eksbuild.3"
  service_account_role_arn = aws_iam_role.vpc_cni.arn
  configuration_values = jsonencode({
    enableNetworkPolicy = "true"
  })

  depends_on = [
    aws_iam_role_policy_attachment.vpc_cni
  ]
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name  = aws_eks_cluster.this.name
  addon_name    = "kube-proxy"
  addon_version = "v1.36.0-eksbuild.25"
}

resource "aws_eks_addon" "pod_identity" {
  cluster_name  = aws_eks_cluster.this.name
  addon_name    = "eks-pod-identity-agent"
  addon_version = "v1.3.10-eksbuild.3"
}

resource "aws_eks_addon" "coredns" {
  cluster_name  = aws_eks_cluster.this.name
  addon_name    = "coredns"
  addon_version = "v1.14.7-eksbuild.10"

  depends_on = [
    aws_eks_node_group.application
  ]
}
