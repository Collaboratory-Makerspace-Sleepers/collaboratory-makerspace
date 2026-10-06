# SSM read permissions for Lambda functions.
# The functions read secrets from /collaboratory/{env}/internal-api-url,
# /collaboratory/{env}/internal-api-secret, and /collaboratory/{env}/stripe-restricted-key.
# These policies attach to the IAM roles already defined in iam.tf.

data "aws_caller_identity" "current" {}

locals {
  ssm_path_prefix = "/collaboratory/${var.environment}/"
}

resource "aws_iam_role_policy" "membership_lambda_ssm" {
  name = "ssm-read"
  role = aws_iam_role.membership_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["ssm:GetParameter"]
      Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_path_prefix}*"
    }]
  })
}

resource "aws_iam_role_policy" "billing_lambda_ssm" {
  name = "ssm-read"
  role = aws_iam_role.billing_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["ssm:GetParameter"]
      Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_path_prefix}*"
    }]
  })
}

resource "aws_iam_role_policy" "alert_lambda_ssm" {
  name = "ssm-read"
  role = aws_iam_role.alert_lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["ssm:GetParameter"]
      Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_path_prefix}*"
    }]
  })
}

# IAM role ARNs for the SAM template parameters.
output "membership_lambda_role_arn" {
  value = aws_iam_role.membership_lambda.arn
}

output "billing_lambda_role_arn" {
  value = aws_iam_role.billing_lambda.arn
}

output "alert_lambda_role_arn" {
  value = aws_iam_role.alert_lambda.arn
}