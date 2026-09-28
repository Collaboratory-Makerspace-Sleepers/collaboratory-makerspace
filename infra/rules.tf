# ── CloudWatch log group for catch-all audit rule ────────────────────────────

resource "aws_cloudwatch_log_group" "stripe_events" {
  name              = "/aws/events/${var.environment}/stripe"
  retention_in_days = 30
}

# Allow EventBridge to write to the log group
resource "aws_cloudwatch_log_resource_policy" "stripe_events" {
  policy_name = "${var.environment}-stripe-events-log-policy"
  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = ["logs:CreateLogStream", "logs:PutLogEvents"]
      Resource  = "${aws_cloudwatch_log_group.stripe_events.arn}:*"
    }]
  })
}

# ── SQS queue policies (allow EventBridge to send messages) ──────────────────

resource "aws_sqs_queue_policy" "membership" {
  queue_url = aws_sqs_queue.stripe_membership_queue.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.stripe_membership_queue.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.membership.arn }
      }
    }]
  })
}

resource "aws_sqs_queue_policy" "billing" {
  queue_url = aws_sqs_queue.stripe_billing_queue.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.stripe_billing_queue.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.billing.arn }
      }
    }]
  })
}

resource "aws_sqs_queue_policy" "alert" {
  queue_url = aws_sqs_queue.stripe_alert_queue.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.stripe_alert_queue.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.alert.arn }
      }
    }]
  })
}

# ── Rules ─────────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_event_rule" "membership" {
  name           = "${var.environment}-stripe-membership"
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  event_pattern = jsonencode({
    "detail-type" = [
      "customer.subscription.created",
      "customer.subscription.updated",
      "customer.subscription.deleted"
    ]
  })
}

resource "aws_cloudwatch_event_rule" "billing" {
  name           = "${var.environment}-stripe-billing"
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  event_pattern = jsonencode({
    "detail-type" = [
      "invoice.paid",
      "invoice.payment_failed",
      "invoice.payment_action_required",
      "invoice.upcoming",
      "checkout.session.completed",
      "checkout.session.async_payment_succeeded",
      "checkout.session.async_payment_failed",
      "charge.refunded",
      "payment_intent.payment_failed",
      "customer.deleted"
    ]
  })
}

resource "aws_cloudwatch_event_rule" "alert" {
  name           = "${var.environment}-stripe-alert"
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  event_pattern = jsonencode({
    "detail-type" = ["charge.dispute.created"]
  })
}

resource "aws_cloudwatch_event_rule" "catch_all" {
  name           = "${var.environment}-stripe-catch-all"
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  event_pattern = jsonencode({
    source = [{ prefix = "aws.partner/stripe.com" }]
  })
}

# ── Targets ───────────────────────────────────────────────────────────────────

resource "aws_cloudwatch_event_target" "membership" {
  rule           = aws_cloudwatch_event_rule.membership.name
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  target_id      = "membership-queue"
  arn            = aws_sqs_queue.stripe_membership_queue.arn
}

resource "aws_cloudwatch_event_target" "billing" {
  rule           = aws_cloudwatch_event_rule.billing.name
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  target_id      = "billing-queue"
  arn            = aws_sqs_queue.stripe_billing_queue.arn
}

resource "aws_cloudwatch_event_target" "alert" {
  rule           = aws_cloudwatch_event_rule.alert.name
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  target_id      = "alert-queue"
  arn            = aws_sqs_queue.stripe_alert_queue.arn
}

resource "aws_cloudwatch_event_target" "catch_all" {
  rule           = aws_cloudwatch_event_rule.catch_all.name
  event_bus_name = aws_cloudwatch_event_bus.stripe.name
  target_id      = "catch-all-logs"
  arn            = aws_cloudwatch_log_group.stripe_events.arn
}