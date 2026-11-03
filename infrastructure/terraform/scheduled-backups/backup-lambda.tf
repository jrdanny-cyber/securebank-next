locals {
  snapshot_prefix = "${local.name}-snapshot"
  snapshot_arn    = "arn:aws:rds:us-east-1:535079144516:snapshot:${local.snapshot_prefix}-*"
}

resource "aws_cloudwatch_log_group" "backup" {
  name              = "/aws/lambda/${local.name}-create"
  retention_in_days = 7
}

resource "aws_iam_role" "backup" {
  name = "${local.name}-create"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "backup" {
  name = "backup-request-processing"
  role = aws_iam_role.backup.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadBackupRequests"
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.requests.arn
      },
      {
        Sid    = "CreateLabSnapshots"
        Effect = "Allow"
        Action = "rds:CreateDBSnapshot"
        Resource = [
          data.aws_db_instance.target.db_instance_arn,
          local.snapshot_arn
        ]
      },
      {
        Sid    = "TagAndVerifyLabSnapshots"
        Effect = "Allow"
        Action = [
          "rds:AddTagsToResource",
          "rds:ListTagsForResource"
        ]
        Resource = local.snapshot_arn
      },
      {
        Sid      = "DescribeSnapshots"
        Effect   = "Allow"
        Action   = "rds:DescribeDBSnapshots"
        Resource = "*"
      },
      {
        Sid    = "WriteFunctionLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.backup.arn}:*"
      }
    ]
  })
}

resource "aws_lambda_function" "backup" {
  function_name = "${local.name}-create"
  role          = aws_iam_role.backup.arn
  runtime       = "python3.13"
  handler       = "backup.handler"

  filename         = "${path.module}/backup.zip"
  source_code_hash = filebase64sha256("${path.module}/backup.zip")

  timeout     = 60
  memory_size = 128

  environment {
    variables = {
      DATABASE_IDENTIFIER = local.database_identifier
      SNAPSHOT_PREFIX     = local.snapshot_prefix
    }
  }

  depends_on = [
    aws_iam_role_policy.backup,
    aws_cloudwatch_log_group.backup
  ]
}

resource "aws_lambda_event_source_mapping" "backup" {
  event_source_arn = aws_sqs_queue.requests.arn
  function_name    = aws_lambda_function.backup.arn

  batch_size                         = 1
  maximum_batching_window_in_seconds = 0
  function_response_types            = ["ReportBatchItemFailures"]

  scaling_config {
    maximum_concurrency = 2
  }

  depends_on = [aws_iam_role_policy.backup]
}

output "backup_function_name" {
  value = aws_lambda_function.backup.function_name
}
