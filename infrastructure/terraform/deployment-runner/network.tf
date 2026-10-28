resource "aws_security_group" "runner" {
  name        = local.name
  description = "Private deployment runner managed through SSM"
  vpc_id      = data.terraform_remote_state.network.outputs.vpc_id

  tags = {
    Name = local.name
  }
}

resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.runner.id
  description       = "HTTPS to GitHub, AWS services, and EKS"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "eks_from_runner" {
  security_group_id = data.terraform_remote_state.eks.outputs.cluster_security_group_id
  description       = "Kubernetes API from the deployment runner"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443

  referenced_security_group_id = aws_security_group.runner.id
}
