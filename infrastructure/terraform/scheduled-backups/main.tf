locals {
  name                = "securebank-next-dev-backup-lab"
  database_identifier = "securebank-next-dev-postgres"
}

data "aws_db_instance" "target" {
  db_instance_identifier = local.database_identifier
}

output "target_database_arn" {
  value = data.aws_db_instance.target.db_instance_arn
}

output "existing_backup_retention_days" {
  value = data.aws_db_instance.target.backup_retention_period
}
