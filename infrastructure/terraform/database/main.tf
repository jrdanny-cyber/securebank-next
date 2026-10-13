locals {
  identifier = "securebank-next-dev-postgres"
}

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

resource "aws_db_subnet_group" "this" {
  name       = local.identifier
  subnet_ids = values(data.terraform_remote_state.network.outputs.database_subnet_ids)
}

resource "aws_security_group" "database" {
  name        = "${local.identifier}-database"
  description = "Private PostgreSQL access from EKS nodes."
  vpc_id      = data.terraform_remote_state.network.outputs.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "eks_postgres" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = data.terraform_remote_state.eks.outputs.cluster_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from the EKS node security group."
}

resource "aws_db_parameter_group" "this" {
  name   = "${local.identifier}-pg17"
  family = "postgres17"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_cloudwatch_log_group" "postgres" {
  name              = "/aws/rds/instance/${local.identifier}/postgresql"
  retention_in_days = 14
}

resource "aws_db_instance" "this" {
  identifier     = local.identifier
  engine         = "postgres"
  engine_version = "17.11"
  instance_class = "db.t4g.micro"

  db_name                     = "securebank"
  username                    = "banking_admin"
  manage_master_user_password = true
  port                        = 5432

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.this.name

  publicly_accessible = false
  multi_az            = false

  backup_retention_period = 1
  backup_window           = "02:00-03:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  auto_minor_version_upgrade  = false
  allow_major_version_upgrade = false
  deletion_protection         = true
  skip_final_snapshot         = false
  final_snapshot_identifier   = "${local.identifier}-final"
  copy_tags_to_snapshot       = true

  enabled_cloudwatch_logs_exports = ["postgresql"]

  depends_on = [
    aws_cloudwatch_log_group.postgres,
    aws_vpc_security_group_ingress_rule.eks_postgres
  ]
}
