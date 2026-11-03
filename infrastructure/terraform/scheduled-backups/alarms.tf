locals {
  dlq_alarm_name = "${local.name}-failed-requests"
  dlq_alarm_arn  = "arn:aws:cloudwatch:us-east-1:535079144516:alarm:${local.dlq_alarm_name}"
}

resource "aws_sns_topic_policy" "notifications" {
  arn = aws_sns_topic.notifications.arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "TopicOwner"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::535079144516:root" }
        Action = [
          "sns:GetTopicAttributes",
          "sns:SetTopicAttributes",
          "sns:AddPermission",
          "sns:RemovePermission",
          "sns:DeleteTopic",
          "sns:Subscribe",
          "sns:ListSubscriptionsByTopic",
          "sns:Publish"
        ]
        Resource = aws_sns_topic.notifications.arn
      },
      {
        Sid       = "AllowBackupAlarm"
        Effect    = "Allow"
        Principal = { Service = "cloudwatch.amazonaws.com" }
        Action    = "sns:Publish"
        Resource  = aws_sns_topic.notifications.arn
        Condition = {
          StringEquals = {
            "aws:SourceAccount" = "535079144516"
          }
          ArnEquals = {
            "aws:SourceArn" = local.dlq_alarm_arn
          }
        }
      }
    ]
  })
}

resource "aws_cloudwatch_metric_alarm" "dead_letter" {
  alarm_name        = local.dlq_alarm_name
  alarm_description = "Backup requests exhausted retries. Inspect the DLQ and Lambda logs."

  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.dead_letter.name
  }

  alarm_actions = [aws_sns_topic.notifications.arn]

  depends_on = [aws_sns_topic_policy.notifications]
}
