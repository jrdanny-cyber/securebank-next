output "state_bucket_name" {
  description = "S3 bucket used to store Terraform state."
  value       = aws_s3_bucket.terraform_state.id
}

output "state_bucket_arn" {
  description = "ARN of the Terraform state bucket."
  value       = aws_s3_bucket.terraform_state.arn
}

output "aws_region" {
  description = "Region containing the Terraform state bucket."
  value       = "us-east-1"
}
