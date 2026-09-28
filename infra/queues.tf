
resource "aws_sqs_queue" "stripe_membership_dlq" {
  name                      = "${var.environment}-stripe-membership-dlq"
  message_retention_seconds = 1209600 // 14 days
}

resource "aws_sqs_queue" "stripe_membership_queue" {
  name                       = "${var.environment}-stripe-membership-queue"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600 // 4 days

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.stripe_membership_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "stripe_billing_dlq" {
  name                      = "${var.environment}-stripe-billing-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "stripe_billing_queue" {
  name                       = "${var.environment}-stripe-billing-queue"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.stripe_billing_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "stripe_alert_dlq" {
  name                      = "${var.environment}-stripe-alert-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "stripe_alert_queue" {
  name                       = "${var.environment}-stripe-alert-queue"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.stripe_alert_dlq.arn
    maxReceiveCount     = 3
  })
}