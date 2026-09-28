output "membership_queue_url" {
  value = aws_sqs_queue.stripe_membership_queue.id
}

output "membership_queue_arn" {
  value = aws_sqs_queue.stripe_membership_queue.arn
}

output "billing_queue_url" {
  value = aws_sqs_queue.stripe_billing_queue.id
}

output "billing_queue_arn" {
  value = aws_sqs_queue.stripe_billing_queue.arn
}

output "alert_queue_url" {
  value = aws_sqs_queue.stripe_alert_queue.id
}

output "alert_queue_arn" {
  value = aws_sqs_queue.stripe_alert_queue.arn
}

output "stripe_event_bus_name" {
  value = aws_cloudwatch_event_bus.stripe.name
}