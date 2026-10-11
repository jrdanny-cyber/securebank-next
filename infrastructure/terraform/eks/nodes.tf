variable "node_instance_type" {
  description = "EC2 instance type for the development worker nodes."
  type        = string
  default     = "c7i-flex.large"
}

resource "aws_launch_template" "nodes" {
  name_prefix = "${var.cluster_name}-nodes-"

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_type           = "gp3"
      volume_size           = 30
      encrypted             = true
      delete_on_termination = true
    }
  }

  tag_specifications {
    resource_type = "instance"

    tags = {
      Name        = "${var.cluster_name}-worker"
      Project     = "securebank-next"
      Environment = "dev"
      ManagedBy   = "Terraform"
    }
  }

  tag_specifications {
    resource_type = "volume"

    tags = {
      Name        = "${var.cluster_name}-worker"
      Project     = "securebank-next"
      Environment = "dev"
      ManagedBy   = "Terraform"
    }
  }
}

resource "aws_eks_node_group" "application" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.cluster_name}-application"
  node_role_arn   = aws_iam_role.nodes.arn

  subnet_ids = values(
    data.terraform_remote_state.network.outputs.private_subnet_ids
  )

  version        = var.kubernetes_version
  ami_type       = "AL2023_x86_64_STANDARD"
  capacity_type  = "ON_DEMAND"
  instance_types = [var.node_instance_type]

  launch_template {
    id      = aws_launch_template.nodes.id
    version = tostring(aws_launch_template.nodes.latest_version)
  }

  scaling_config {
    desired_size = 2
    min_size     = 2
    max_size     = 3
  }

  update_config {
    max_unavailable = 1
  }

  labels = {
    workload = "application"
  }

  depends_on = [
    aws_iam_role_policy_attachment.nodes,
    aws_eks_addon.vpc_cni,
    aws_eks_addon.kube_proxy,
    aws_eks_addon.pod_identity
  ]
}
