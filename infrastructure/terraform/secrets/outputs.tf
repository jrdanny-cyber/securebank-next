output "secret_arns" {
  description = "Secret identifiers only; no credential values."
  value = {
    for name, secret in aws_secretsmanager_secret.database :
    name => secret.arn
  }
}
