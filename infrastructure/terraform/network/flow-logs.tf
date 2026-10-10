resource "aws_cloudwatch_log_group" "vpc_flow" {
  name              = "/securebank-next/${var.environment}/vpc-flow"
  retention_in_days = 14

  tags = {
    Name = "${local.name}-vpc-flow"
  }
}

resource "aws_iam_role" "vpc_flow" {
  name = "${local.name}-vpc-flow-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
      Action = "sts:AssumeRole"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = "535079144516"
        }
        ArnLike = {
          "aws:SourceArn" = "arn:aws:ec2:us-east-1:535079144516:vpc-flow-log/*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "vpc_flow" {
  name = "publish-vpc-flow-logs"
  role = aws_iam_role.vpc_flow.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "WriteFlowLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:DescribeLogStreams"
        ]
        Resource = [
          aws_cloudwatch_log_group.vpc_flow.arn,
          "${aws_cloudwatch_log_group.vpc_flow.arn}:*"
        ]
      },
      {
        Sid      = "DiscoverLogGroups"
        Effect   = "Allow"
        Action   = "logs:DescribeLogGroups"
        Resource = "*"
      }
    ]
  })
}

resource "aws_flow_log" "this" {
  vpc_id                   = aws_vpc.this.id
  traffic_type             = "ALL"
  log_destination_type     = "cloud-watch-logs"
  log_destination          = aws_cloudwatch_log_group.vpc_flow.arn
  iam_role_arn             = aws_iam_role.vpc_flow.arn
  max_aggregation_interval = 60

  depends_on = [aws_iam_role_policy.vpc_flow]

  tags = {
    Name = "${local.name}-flow-logs"
  }
}
