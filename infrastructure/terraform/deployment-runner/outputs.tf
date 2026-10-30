output "instance_id" {
  value = aws_instance.runner.id
}

output "private_ip" {
  value = aws_instance.runner.private_ip
}

output "security_group_id" {
  value = aws_security_group.runner.id
}

output "instance_role_arn" {
  value = aws_iam_role.runner.arn
}

output "ami_id" {
  value = aws_instance.runner.ami
}
