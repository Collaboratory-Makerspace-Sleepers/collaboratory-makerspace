# Stripe Event Consumers

Three Python Lambda functions that consume Stripe events from SQS and forward them to the Spring internal API.

```
EventBridge (Stripe partner source)
  └── SQS queues (membership / billing / alert)
        └── Lambda consumers  →  POST /api/internal/billing/events
```

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Python | 3.13 | [python.org](https://python.org) or `pyenv install 3.13` |
| AWS CLI v2 | ≥ 2.15 | [docs.aws.amazon.com/cli](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html) |
| AWS SAM CLI | ≥ 1.120 | `pip install aws-sam-cli` |
| Terraform | 1.16.2 | [terraform.io](https://developer.hashicorp.com/terraform/install) |

---

## AWS credentials

Set these before running any AWS or Terraform commands. Use whichever form your account uses.

**Option A — explicit keys:**
```bash
export AWS_ACCESS_KEY_ID=<your-key-id>
export AWS_SECRET_ACCESS_KEY=<your-secret-key>
export AWS_REGION=us-east-2
export AWS_DEFAULT_REGION=us-east-2
```

**Option B — named profile:**
```bash
export AWS_PROFILE=collaboratory
export AWS_REGION=us-east-2
export AWS_DEFAULT_REGION=us-east-2
```

Verify access:
```bash
aws sts get-caller-identity
```

---

## Step 1 — Apply the Terraform plan

The existing plan (`infra/tfplan`) adds SSM read permissions to the three Lambda IAM roles and exports their ARNs.

```bash
cd infra/
terraform apply tfplan
```

After apply, confirm the new outputs are available:

```bash
terraform output
```

You should now see `membership_lambda_role_arn`, `billing_lambda_role_arn`, and `alert_lambda_role_arn` alongside the queue outputs that were already there.

---

## Step 2 — Create SSM parameters

The Lambda functions read three secrets from SSM at cold start. Create them as `SecureString` under `/collaboratory/dev/`:

```bash
# URL of the running Spring app (no trailing slash)
aws ssm put-parameter \
  --name "/collaboratory/dev/internal-api-url" \
  --value "https://<your-beanstalk-url>" \
  --type SecureString \
  --region us-east-2

# Shared secret that InternalAuthFilter validates (APP_INTERNAL_API_SECRET in Spring)
aws ssm put-parameter \
  --name "/collaboratory/dev/internal-api-secret" \
  --value "<your-internal-api-secret>" \
  --type SecureString \
  --region us-east-2

# Stripe restricted key — read-only Billing + Events permissions only
# Generate in Stripe Dashboard → Developers → API keys → Restricted keys
aws ssm put-parameter \
  --name "/collaboratory/dev/stripe-restricted-key" \
  --value "rk_test_..." \
  --type SecureString \
  --region us-east-2
```

---

## Step 3 — Fill in samconfig.toml

Pull the values from Terraform output and paste them into `samconfig.toml`:

```bash
cd infra/

terraform output -raw membership_queue_arn
terraform output -raw billing_queue_arn
terraform output -raw alert_queue_arn
terraform output -raw membership_lambda_role_arn
terraform output -raw billing_lambda_role_arn
terraform output -raw alert_lambda_role_arn
```

Open `lambdas/samconfig.toml` and replace each `FILL_FROM_TF_OUTPUT` under `[dev.deploy.parameters]` with the corresponding value.

Or do it in one shot with `sed`:

```bash
cd lambdas/

MEMBERSHIP_Q=$(cd ../infra && terraform output -raw membership_queue_arn)
BILLING_Q=$(cd ../infra && terraform output -raw billing_queue_arn)
ALERT_Q=$(cd ../infra && terraform output -raw alert_queue_arn)
MEMBERSHIP_R=$(cd ../infra && terraform output -raw membership_lambda_role_arn)
BILLING_R=$(cd ../infra && terraform output -raw billing_lambda_role_arn)
ALERT_R=$(cd ../infra && terraform output -raw alert_lambda_role_arn)

sed -i \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$MEMBERSHIP_Q|" \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$BILLING_Q|" \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$ALERT_Q|" \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$MEMBERSHIP_R|" \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$BILLING_R|" \
  -e "0,/FILL_FROM_TF_OUTPUT/s||$ALERT_R|" \
  samconfig.toml
```

---

## Step 4 — Build and deploy

```bash
cd lambdas/

sam build
sam deploy --config-env dev
```

SAM installs each function's pip dependencies, packages the shared layer, and deploys the CloudFormation stack `dev-stripe-consumers`.

**First-time deploy** — if the S3 bucket for SAM artifacts does not exist yet, run the guided flow instead:

```bash
sam deploy --guided --config-env dev
```

SAM will ask for a bucket name and save the answer back into `samconfig.toml`.

---

## Step 5 — Verify

Trigger a test event through the full path:

```bash
# From the Stripe CLI (must be installed and authenticated)
stripe trigger customer.subscription.created

# Watch the membership consumer logs
aws logs tail /aws/lambda/dev-stripe-membership-consumer --follow --region us-east-2
```

Check that a row landed in `stripe_event_log` via the Spring app, and that the membership table updated.

To verify the billing consumer:
```bash
stripe trigger invoice.paid
aws logs tail /aws/lambda/dev-stripe-billing-consumer --follow --region us-east-2
```

---

## Running tests locally

```bash
cd lambdas/

python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt

.venv/bin/pytest tests/ -v
```

Tests use `responses` to mock HTTP calls to the internal API and do not require a live AWS connection or Stripe account. All 29 tests should pass.

---

## Project layout

```
lambdas/
  layer/
    python/
      shared/
        config.py          # SSM secrets reader (lru_cache — one SSM call per container)
        envelope.py        # SQS+EventBridge → StripeEventCommand
        internal_client.py # HTTP POST with 200/409/422/5xx contract
  membership_consumer/
    handler.py             # subscription.* events; re-fetches subscription before forwarding
    requirements.txt
  billing_consumer/
    handler.py             # invoice.*, checkout.*, charge.refunded; no re-fetch
    requirements.txt
  alert_consumer/
    handler.py             # charge.dispute.*
    requirements.txt
  tests/
    conftest.py            # sys.path setup, FakeLambdaContext, fixture helpers
    fixtures/              # EventBridge envelopes (one JSON file per event type)
    test_envelope.py
    test_internal_client.py
    test_membership_consumer.py
    test_billing_consumer.py
    test_alert_consumer.py
  template.yaml            # SAM deployment template
  samconfig.toml           # Per-environment deploy parameters
  requirements-dev.txt
```

---

## Key design notes

**409 is success.** When the internal API returns 409, the event was already processed. The consumer treats this as success and deletes the message. Treating it as a failure would cause an infinite retry loop — this is the single most likely bug to introduce during changes.

**Membership consumer re-fetches.** `customer.subscription.*` events are re-fetched from Stripe using the restricted key before being forwarded. Stripe does not guarantee event ordering, so the embedded snapshot may be stale. Invoice and checkout events are immutable records and are never re-fetched.

**Partial batch failure.** `BatchProcessor` is configured with `raise_on_entire_batch_failure=False`. If every record in a batch fails, the function returns `batchItemFailures` for all of them rather than raising. SQS then retries each message individually up to `maxReceiveCount=3` before routing to the DLQ.

**Livemode guard.** `EXPECTED_LIVEMODE=false` in dev, `true` in prod. A cross-wired event (sandbox event in prod or vice versa) is logged as a warning and dropped rather than failed — it is a config signal, not a processing error.

**Secrets never in code.** All three secrets (`internal-api-url`, `internal-api-secret`, `stripe-restricted-key`) live in SSM Parameter Store as `SecureString`. The Lambda execution roles have `ssm:GetParameter` permission scoped to `/collaboratory/<env>/*` only.
