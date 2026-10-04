terraform {
  backend "s3" {
    bucket              = "securebank-next-tfstate-535079144516-us-east-1"
    key                 = "registry/terraform.tfstate"
    region              = "us-east-1"
    encrypt             = true
    use_lockfile        = true
    allowed_account_ids = ["535079144516"]
  }
}
