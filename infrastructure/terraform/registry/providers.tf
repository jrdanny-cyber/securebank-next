provider "aws" {
  region              = "us-east-1"
  allowed_account_ids = ["535079144516"]

  default_tags {
    tags = {
      Project   = "securebank-next"
      ManagedBy = "Terraform"
      Component = "container-registry"
    }
  }
}
