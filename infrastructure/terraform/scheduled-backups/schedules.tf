resource "aws_scheduler_schedule_group" "backup" {
  name = local.name
}

resource "aws_sqs_queue" "scheduler_dead_letter" {
  name                      = "${local.name}-scheduler-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_iam_role" "scheduler" {
  name = "${local.name}-scheduler"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = "535079144516"
        }
        ArnEquals = {
          "aws:SourceArn" = aws_scheduler_schedule_group.backup.arn
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler" {
  name = "deliver-backup-lab-events"
  role = aws_iam_role.scheduler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = "sqs:SendMessage"
        Resource = [
          aws_sqs_queue.requests.arn,
          aws_sqs_queue.scheduler_dead_letter.arn
        ]
      },
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = aws_lambda_function.monitor.arn
      }
    ]
  })
}

resource "aws_scheduler_schedule" "backup" {
  name       = "${local.name}-daily"
  group_name = aws_scheduler_schedule_group.backup.name
  state      = "DISABLED"

  schedule_expression          = "cron(30 6 * * ? *)"
  schedule_expression_timezone = "Africa/Douala"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_sqs_queue.requests.arn
    role_arn = aws_iam_role.scheduler.arn

    input = <<-JSON
  {"request_id":"daily-<aws.scheduler.scheduled-time>"}
JSON

    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 3
    }

    dead_letter_config {
      arn = aws_sqs_queue.scheduler_dead_letter.arn
    }
  }

  depends_on = [aws_iam_role_policy.scheduler]
}

resource "aws_scheduler_schedule" "monitor" {
  name       = "${local.name}-monitor"
  group_name = aws_scheduler_schedule_group.backup.name
  state      = "DISABLED"

  schedule_expression = "rate(5 minutes)"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.monitor.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ source = "scheduled-monitor" })

    retry_policy {
      maximum_event_age_in_seconds = 3600
      maximum_retry_attempts       = 3
    }

    dead_letter_config {
      arn = aws_sqs_queue.scheduler_dead_letter.arn
    }
  }

  depends_on = [aws_iam_role_policy.scheduler]
}

resource "aws_lambda_function_event_invoke_config" "monitor" {
  function_name                = aws_lambda_function.monitor.function_name
  maximum_event_age_in_seconds = 3600
  maximum_retry_attempts       = 2

  destination_config {
    on_failure {
      destination = aws_sns_topic.notifications.arn
    }
  }

  depends_on = [aws_iam_role_policy.monitor]
}
