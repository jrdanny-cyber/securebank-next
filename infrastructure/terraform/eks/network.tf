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
