output "database_address" {
  value = aws_db_instance.this.address
}

output "database_port" {
  value = aws_db_instance.this.port
}

output "database_name" {
  value = aws_db_instance.this.db_name
}

output "database_security_group_id" {
  value = aws_security_group.database.id
}

output "administrator_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}
