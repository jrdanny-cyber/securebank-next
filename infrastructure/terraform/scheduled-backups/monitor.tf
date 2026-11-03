resource "aws_cloudwatch_log_group" "monitor" {
  name              = "/aws/lambda/${local.name}-monitor"
  retention_in_days = 7
}

resource "aws_iam_role" "monitor" {
  name = "${local.name}-monitor"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "monitor" {
  name = "monitor-lab-snapshots"
  role = aws_iam_role.monitor.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "rds:DescribeDBSnapshots"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "rds:ListTagsForResource",
          "rds:AddTagsToResource"
        ]
        Resource = local.snapshot_arn
      },
      {
        Effect   = "Allow"
        Action   = "sns:Publish"
        Resource = aws_sns_topic.notifications.arn
      },
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.monitor.arn}:*"
      }
    ]
  })
}

resource "aws_lambda_function" "monitor" {
  function_name = "${local.name}-monitor"
  role          = aws_iam_role.monitor.arn
  runtime       = "python3.13"
  handler       = "monitor.handler"

  filename         = "${path.module}/monitor.zip"
  source_code_hash = filebase64sha256("${path.module}/monitor.zip")

  timeout     = 60
  memory_size = 128

  environment {
    variables = {
      DATABASE_IDENTIFIER    = local.database_identifier
      SNAPSHOT_PREFIX        = local.snapshot_prefix
      NOTIFICATION_TOPIC_ARN = aws_sns_topic.notifications.arn
    }
  }

  depends_on = [
    aws_iam_role_policy.monitor,
    aws_cloudwatch_log_group.monitor
  ]
}

output "backup_monitor_function_name" {
  value = aws_lambda_function.monitor.function_name
}
