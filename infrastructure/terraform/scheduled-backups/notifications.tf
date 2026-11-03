variable "notification_email" {
  description = "Email recipient for backup lab notifications."
  type        = string
  sensitive   = true
}

resource "aws_sns_topic" "notifications" {
  name = "${local.name}-notifications"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.notifications.arn
  protocol  = "email"
  endpoint  = var.notification_email
}

output "backup_notification_topic_arn" {
  value = aws_sns_topic.notifications.arn
}
