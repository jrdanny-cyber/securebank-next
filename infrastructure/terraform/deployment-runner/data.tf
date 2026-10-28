data "terraform_remote_state" "network" {
  backend = "s3"

  config = {
    bucket              = "securebank-next-tfstate-535079144516-us-east-1"
    key                 = "network/dev/terraform.tfstate"
    region              = "us-east-1"
    encrypt             = true
    allowed_account_ids = ["535079144516"]
  }
}

data "terraform_remote_state" "eks" {
  backend = "s3"

  config = {
    bucket              = "securebank-next-tfstate-535079144516-us-east-1"
    key                 = "eks/dev/terraform.tfstate"
    region              = "us-east-1"
    encrypt             = true
    allowed_account_ids = ["535079144516"]
  }
}

data "aws_ssm_parameter" "runner_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  name = "securebank-next-dev-deployment-runner"

  subnet_id = values(
    data.terraform_remote_state.network.outputs.private_subnet_ids
  )[0]
}
