terraform {
  required_version = "1.16.2"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

resource "aws_cloudwatch_event_bus" "stripe" {
  name              = "aws.partner/stripe.com/ed_test_61VS6ELvBsE6jb54y16VDlqLMnSQkJt00AVEwyQ7cPi4"
  event_source_name = "aws.partner/stripe.com/ed_test_61VS6ELvBsE6jb54y16VDlqLMnSQkJt00AVEwyQ7cPi4"
}