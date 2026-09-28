# ── DLQ depth alarms ─────────────────────────────────────────────────────────
# Fires when any message lands in a DLQ (depth > 0).

resource "aws_cloudwatch_metric_alarm" "membership_dlq" {
  alarm_name          = "${var.environment}-stripe-membership-dlq-depth"
  alarm_description   = "Messages are stuck in the membership DLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.stripe_membership_dlq.name }
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "billing_dlq" {
  alarm_name          = "${var.environment}-stripe-billing-dlq-depth"
  alarm_description   = "Messages are stuck in the billing DLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.stripe_billing_dlq.name }
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "alert_dlq" {
  alarm_name          = "${var.environment}-stripe-alert-dlq-depth"
  alarm_description   = "Messages are stuck in the alert DLQ"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.stripe_alert_dlq.name }
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
}