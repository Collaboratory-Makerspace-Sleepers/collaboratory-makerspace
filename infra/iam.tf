# ── Trust policy (shared across all Lambda roles) ────────────────────────────

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

# ── Membership Lambda ─────────────────────────────────────────────────────────

resource "aws_iam_role" "membership_lambda" {
  name               = "${var.environment}-stripe-membership-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "membership_lambda_logs" {
  role       = aws_iam_role.membership_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "membership_lambda_sqs" {
  name = "sqs-read"
  role = aws_iam_role.membership_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ]
      Resource = aws_sqs_queue.stripe_membership_queue.arn
    }]
  })
}

# ── Billing Lambda ────────────────────────────────────────────────────────────

resource "aws_iam_role" "billing_lambda" {
  name               = "${var.environment}-stripe-billing-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "billing_lambda_logs" {
  role       = aws_iam_role.billing_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "billing_lambda_sqs" {
  name = "sqs-read"
  role = aws_iam_role.billing_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ]
      Resource = aws_sqs_queue.stripe_billing_queue.arn
    }]
  })
}

# ── Alert Lambda ──────────────────────────────────────────────────────────────

resource "aws_iam_role" "alert_lambda" {
  name               = "${var.environment}-stripe-alert-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "alert_lambda_logs" {
  role       = aws_iam_role.alert_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "alert_lambda_sqs" {
  name = "sqs-read"
  role = aws_iam_role.alert_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes"
      ]
      Resource = aws_sqs_queue.stripe_alert_queue.arn
    }]
  })
}
