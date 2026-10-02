output "repository_urls" {
  description = "ECR repository URLs keyed by application service."
  value = {
    for service, repository in aws_ecr_repository.application :
    service => repository.repository_url
  }
}

output "github_ecr_publisher_role_arn" {
  description = "IAM role assumed by the GitHub image publishing workflow."
  value       = aws_iam_role.github_ecr_publisher.arn
}

output "github_oidc_provider_arn" {
  description = "GitHub Actions OIDC provider."
  value       = aws_iam_openid_connect_provider.github.arn
}
