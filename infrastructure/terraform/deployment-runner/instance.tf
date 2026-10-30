resource "aws_instance" "runner" {
  ami                         = nonsensitive(data.aws_ssm_parameter.runner_ami.value)
  instance_type               = "t3.small"
  subnet_id                   = local.subnet_id
  associate_public_ip_address = false
  vpc_security_group_ids      = [aws_security_group.runner.id]
  iam_instance_profile        = aws_iam_instance_profile.runner.name

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 30
    encrypted             = true
    delete_on_termination = true
  }

  credit_specification {
    cpu_credits = "standard"
  }

  user_data = <<-USERDATA
    #!/bin/bash
    set -euo pipefail
    systemctl enable --now amazon-ssm-agent
  USERDATA

  user_data_replace_on_change = true

  tags = {
    Name = local.name
  }

  depends_on = [
    aws_iam_role_policy_attachment.ssm,
    aws_vpc_security_group_egress_rule.https
  ]
}
