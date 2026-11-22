# Stripe Integration — Event-Driven Design (Python Lambda on AWS)

_Target repo: `collaboratory-makerspace` / `backend`_
_Status: design brief for implementation. Decisions marked **OPEN** are not settled._
_Covers business requirements 3.5.1, 3.5.2, 3.6.1–3.6.4, and unblocks 3.7.2 gating._

---

## 1. Scope

What Stripe is being asked to own:

| Requirement | Stripe surface | Notes |
|---|---|---|
| 3.5.1 Subscription status engine | Billing subscriptions | Stripe is the source of truth; we mirror locally |
| 3.5.2 Expiry logic | `current_period_end`, `subscription.updated/deleted` | Plus a local grace-period sweep |
| 3.6.1 Payment processing | Checkout Sessions (subscription + payment mode) | No raw card data touches our infra |
| 3.6.2 Reconciliation | `/v1/events` list API + nightly sweep | Required because EventBridge has no manual resend |
| 3.6.3 Retry logic | Stripe Smart Retries + SQS redrive + DLQ | Two distinct retry layers, don't conflate them |
| 3.6.4 Cancellation policies | `cancel_at_period_end`, Refunds API | Policy lives in our code, execution in Stripe |

Payment types the makerspace needs:

- **Recurring membership** — monthly/annual subscription (`mode=subscription`)
- **Day pass / drop-in** — one-off (`mode=payment`)
- **Class registration** — one-off, tied to a class/instructor
- **Equipment or room rental fee** — one-off, tied to a reservation
- **Refunds** — staff-initiated, policy-gated

### Hard constraint that shapes everything below

**Checkout Session and Customer Portal creation cannot be event-driven.** They are synchronous, authenticated, user-facing calls that need the `UserPrincipal` on the SecurityContext. Those stay in Spring Boot with `stripe-java`.

The event-driven Python Lambda layer handles **ingest only**: what happens *after* Stripe tells us something changed. Keeping this split explicit prevents the common failure mode of trying to push the purchase flow into Lambda and ending up with a second identity system.

---

## 2. Architecture options

Four viable shapes. All of them need idempotency and a reconciliation path; they differ in transport and in who writes to Postgres.

### Option A — Spring Boot webhook controller only (no Lambda)

`Stripe → HTTPS POST /api/webhooks/stripe → StripeWebhookController → service → RDS`

**Pros**
- One language, one deployment, one secrets store, one test suite.
- Transactional integrity is trivial: the event handler and the domain write share a `@Transactional` boundary and the existing Hibernate entities.
- Signature verification is one line (`Webhook.constructEvent`).
- Easiest thing to demo and explain in a capstone defense.
- Zero additional AWS cost.

**Cons**
- Webhook availability is coupled to Beanstalk availability. If the app is down or mid-deploy, Stripe retries for up to 3 days, so events aren't lost — but backlog processing is bursty and your recovery story is "hope Stripe's retries cover it."
- The webhook endpoint must be `permitAll` and publicly reachable, which adds a new unauthenticated attack surface to an app whose every other route is authenticated.
- Slow handlers hold a Tomcat thread; Stripe's delivery timeout means a slow DB write can cause a retry storm and duplicate processing.
- Doesn't demonstrate any cloud-architecture competency, which is likely on the rubric.

### Option B — API Gateway → Python Lambda (verify) → SQS → Python Lambda (process)

`Stripe → API GW → Lambda(verify sig) → SQS → Lambda(process) → RDS or Spring`

**Pros**
- Ingest scales and stays up independently of Beanstalk.
- Fast ACK to Stripe (verify + enqueue, ~50ms), so no retry storms.
- SQS gives free redrive + DLQ + visibility-timeout retries.
- Full control over the signature verification step.

**Cons**
- You are hand-rolling what Stripe's EventBridge destination gives you for free.
- The webhook signing secret becomes another secret to rotate and store.
- API Gateway + Lambda + SQS is three more Terraform modules for the same outcome as Option C.
- Still needs a public endpoint.

### Option C — Stripe Event Destination → EventBridge → SQS → Python Lambda ✅ **recommended**

`Stripe → EventBridge partner bus → rules → SQS (per domain) → Python Lambda → HTTPS callback into Spring → RDS`

Stripe's Event Destinations shipped GA and support Amazon EventBridge as a first-class destination type, so <cite index="8-1">you receive event data from Stripe directly in your AWS account, which eliminates the need to handle traffic and manage integration code logic yourself</cite>.

**Pros**
- **No public webhook endpoint at all.** Nothing to expose, nothing to DDoS, no signature secret to rotate. Authenticity comes from the AWS partner event source itself. This is a genuine security win over A and B and directly complements the "no unauthenticated surface" posture already in `SecurityConfig`.
- EventBridge rules give declarative routing: `invoice.*` to the billing queue, `customer.subscription.*` to the membership queue, `charge.dispute.*` to a staff-alert queue. Adding a consumer later is a rule, not a code change.
- Free fan-out. The same event can hit a processor Lambda, a CloudWatch Logs group for audit, and an SNS alert with no extra code.
- Ingest survives a Beanstalk outage or deploy. Events queue in SQS and drain when the app returns.
- Native DLQ and redrive per queue.
- <cite index="20-1">Stripe attempts delivery for up to three days with exponential backoff in live mode</cite>, so this sits on top of an already-durable delivery layer.
- Strong capstone artifact: partner event source, rules, queues, DLQs, IaC.

**Cons**
- <cite index="20-1">You can't manually resend events to Amazon EventBridge.</cite> If a bug corrupts processing, there is no "replay from the Stripe dashboard" button. **A reconciliation job is not optional in this design** — see §8.
- Setup has a footgun: <cite index="20-1">Stripe creates a partner event source that you must associate with an event bus within 7 days, or Amazon deletes it and your Stripe destination is automatically disabled, requiring a new destination</cite>.
- <cite index="20-1">Workbench must be enabled in Developer settings</cite> before you can create the destination.
- <cite index="20-1">The Stripe event JSON is wrapped in a top-level `detail` field</cite> using EventBridge's envelope, so the payload shape differs from webhook examples in most Stripe tutorials.
- Local development can't hit EventBridge easily. `stripe listen` forwards to a local HTTP port, not a partner bus. You need a dev-only webhook path or a fixture-replay harness (§11).
- Two languages, two build pipelines, two dependency sets to keep current.
- **Checkout caveat**: <cite index="20-1">you can subscribe to `checkout.session.completed` on EventBridge, but delivering it there won't affect Checkout redirect behavior; to influence redirect behavior you need a webhook endpoint</cite>. We use redirect-to-success-page (not custom redirect logic), so this doesn't bite us — but it means the success page must poll our API rather than assume fulfillment already happened.

### Option D — EventBridge → Lambda writing directly to RDS

**Pros**
- Fewer hops, lowest latency, no callback contract to maintain.

**Cons**
- **Two writers to one schema.** Hibernate owns the entities, mappings, `@SQLDelete` soft-delete semantics, and the `user_roles` join table. A Python Lambda issuing raw SQL will silently diverge the moment an entity changes — exactly the class of bug that is expensive to find and impossible to unit-test from either side.
- Lambda would need to be in the VPC to reach RDS, which means either a NAT Gateway (~$32/mo, meaningful against student credits) or VPC endpoints for every AWS service the function touches.
- RDS connection exhaustion under concurrent Lambda invocations unless you add RDS Proxy (another cost line).
- Business rules (last-admin guard, membership eligibility, reservation gating) would have to be duplicated in Python or bypassed entirely.

**Rejected.** The DB-write monopoly stays with Spring.

### Decision

**Option C**, with Lambda as a *translator and dispatcher*, not a data-layer participant. Python Lambdas parse the EventBridge envelope, decide what domain command the event implies, and call a narrow authenticated internal API on the Spring app, which performs the write inside a normal `@Transactional` service method using the existing entities.

If schedule pressure hits, Option A is a legitimate fallback and can be swapped in without changing the domain layer — the internal command API is the same surface either way. Build the domain services first for exactly this reason.

---

## 3. Target architecture

```
                    ┌──────────────────────────────────────────┐
  user clicks       │  Spring Boot (Elastic Beanstalk)         │
  "Join" ─────────► │  POST /api/billing/checkout-session      │
                    │  POST /api/billing/portal-session        │──► Stripe API
                    │    (stripe-java, authenticated,          │    (synchronous)
                    │     UserPrincipal required)              │
                    └──────────────────────────────────────────┘
                                       ▲
                                       │ (7) HTTPS + SigV4, internal chain
                                       │     POST /api/internal/billing/events
                                       │
  Stripe ──(1)──► EventBridge partner bus (aws.partner/stripe.com/ed_…)
                          │
                          ├─(2)─► rule: customer.subscription.*  ─► SQS membership-events ─┐
                          ├─(2)─► rule: invoice.*                ─► SQS billing-events   ──┤
                          ├─(2)─► rule: charge.dispute.*         ─► SQS alert-events     ──┤
                          └─(3)─► rule: all events              ─► CloudWatch Logs (audit) │
                                                                                           │
                        (4) SQS batch ──► Python Lambda consumer ◄─────────────────────────┘
                                             │
                                             ├─(5) parse envelope, map to domain command
                                             ├─(6) if thin/needs freshness: re-fetch from Stripe API
                                             ├─(7) POST internal command to Spring  ─────────┘
                                             └─(8) on failure: raise → SQS retry → DLQ

  EventBridge Scheduler ──(daily 03:00)──► reconciliation Lambda ──► GET /v1/events since T
                                                                 └─► replays anything missed
  EventBridge Scheduler ──(hourly)───────► expiry-sweep Lambda ───► POST /api/internal/billing/sweep
```

**Why the callback hop (7) instead of Lambda→RDS:** it keeps a single writer, reuses `@Transactional` + Hibernate + the existing guards, keeps Lambda out of the VPC (no NAT Gateway), and makes the whole flow testable from the Spring side with MockMvc. The cost is one HTTP round-trip and a signed-request contract to maintain.

---

## 4. Data model additions

Five new tables. Follow the existing convention: numbered SQL in `src/main/resources/db/migration/`, next free version is `V4`.

> **Note:** `db/migration/` exists with `V3__user_roles_table.sql`, but Flyway is **not** in `pom.xml` and `application.yml` uses `ddl-auto: update`. Migrations are being applied by hand. Adding billing tables is a good moment to add `flyway-core` + `flyway-database-postgresql` and set `ddl-auto: validate`. Money tables are the wrong place to trust Hibernate auto-DDL.

### `V4__billing_core.sql`

```sql
-- Link a User to its Stripe Customer. Nullable: created lazily on first checkout.
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255) UNIQUE;
CREATE INDEX idx_users_stripe_customer ON users(stripe_customer_id);

-- Idempotency ledger. Every inbound Stripe event is recorded here exactly once.
CREATE TABLE stripe_event_log (
    event_id        VARCHAR(255) PRIMARY KEY,      -- evt_… from Stripe
    event_type      VARCHAR(100) NOT NULL,
    api_version     VARCHAR(50),
    livemode        BOOLEAN NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    status          VARCHAR(30) NOT NULL,          -- RECEIVED | PROCESSED | FAILED | SKIPPED
    failure_reason  TEXT,
    attempt_count   INT NOT NULL DEFAULT 0,
    source          VARCHAR(30) NOT NULL           -- EVENTBRIDGE | RECONCILIATION | MANUAL
);
CREATE INDEX idx_stripe_event_log_status ON stripe_event_log(status, received_at);
CREATE INDEX idx_stripe_event_log_type ON stripe_event_log(event_type, received_at DESC);

-- Plan catalog. Maps our membership tiers to Stripe Prices.
CREATE TABLE membership_plan (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50) UNIQUE NOT NULL,   -- MONTHLY, ANNUAL, STUDENT, DAY_PASS
    display_name        VARCHAR(100) NOT NULL,
    stripe_price_id     VARCHAR(255) UNIQUE NOT NULL,
    stripe_product_id   VARCHAR(255) NOT NULL,
    billing_interval    VARCHAR(20),                   -- MONTH | YEAR | NULL for one-off
    amount_cents        INT NOT NULL,
    currency            CHAR(3) NOT NULL DEFAULT 'usd',
    grants_role         VARCHAR(50),                   -- MEMBER | STUDENT | RENTEE
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Local mirror of the Stripe subscription. Stripe is authoritative.
CREATE TABLE membership (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id),
    plan_id                 BIGINT NOT NULL REFERENCES membership_plan(id),
    stripe_subscription_id  VARCHAR(255) UNIQUE,       -- NULL for one-off day passes
    status                  VARCHAR(30) NOT NULL,      -- see §6
    current_period_start    TIMESTAMPTZ,
    current_period_end      TIMESTAMPTZ,
    cancel_at_period_end    BOOLEAN NOT NULL DEFAULT FALSE,
    canceled_at             TIMESTAMPTZ,
    grace_period_ends_at    TIMESTAMPTZ,
    stripe_updated_at       TIMESTAMPTZ,               -- for out-of-order rejection, §7
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_membership_user ON membership(user_id);
CREATE INDEX idx_membership_status_period ON membership(status, current_period_end);
-- At most one non-terminal membership per user.
CREATE UNIQUE INDEX uq_membership_active_per_user
    ON membership(user_id) WHERE status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE');

-- Immutable payment ledger. Append-only; never UPDATE a row's amount.
CREATE TABLE payment_record (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT REFERENCES users(id),
    membership_id               BIGINT REFERENCES membership(id),
    reservation_id              BIGINT REFERENCES reservation(id),
    stripe_payment_intent_id    VARCHAR(255) UNIQUE,
    stripe_invoice_id           VARCHAR(255),
    stripe_charge_id            VARCHAR(255),
    kind                        VARCHAR(30) NOT NULL,  -- MEMBERSHIP | DAY_PASS | CLASS | RENTAL | REFUND
    amount_cents                INT NOT NULL,          -- negative for refunds
    currency                    CHAR(3) NOT NULL DEFAULT 'usd',
    status                      VARCHAR(30) NOT NULL,  -- PENDING | SUCCEEDED | FAILED | REFUNDED
    description                 VARCHAR(255),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_record_user ON payment_record(user_id, occurred_at DESC);
CREATE INDEX idx_payment_record_invoice ON payment_record(stripe_invoice_id);
```

### Entity notes

- `Membership`, `MembershipPlan`, `PaymentRecord`, `StripeEventLog` as `@Entity` classes in `model/`, matching the existing Lombok `@Getter @Setter` style.
- **Do not** apply `@SQLDelete`/`@SQLRestriction` to `payment_record` or `stripe_event_log`. Financial records and audit logs are never soft-deleted.
- `MembershipRepository` needs `findByUserIdForUpdate` with `@Lock(LockModeType.PESSIMISTIC_WRITE)`, mirroring the existing `UserRepository.findByIdForUpdate` pattern.

### Interaction with the existing role model

Memory says authority roles (`STAFF`/`ADMIN`/`INSTRUCTOR`) and customer eligibility types (`MEMBER`/`GUEST`/`STUDENT`/`RENTEE`) are orthogonal, with eligibility derived from data. **Keep that.** A paid membership should *not* write `Role.MEMBER` into `user_roles`. Instead:

```java
// MembershipService
public boolean hasActiveMembership(Long userId);
public MembershipStatus statusOf(Long userId);
```

and gate reservations on that, not on a role. Otherwise every payment event mutates the authorization table and you get a revocation-window bug in the other direction.

---

## 5. Event inventory

Subscribe to exactly these in the Workbench destination. Every event enabled but unhandled is noise in the DLQ; every event handled but not enabled is a silent gap.

| Event type | Queue | Handler action | Priority |
|---|---|---|---|
| `checkout.session.completed` | billing | Link `stripe_customer_id` to user; create `Membership` (subscription) or `PaymentRecord` (one-off) | P0 |
| `checkout.session.async_payment_succeeded` | billing | Same as above for delayed methods (ACH) | P2 |
| `checkout.session.async_payment_failed` | billing | Mark `PaymentRecord` FAILED, notify | P2 |
| `customer.subscription.created` | membership | Upsert `Membership` from subscription object | P0 |
| `customer.subscription.updated` | membership | Status transition, period roll, `cancel_at_period_end` | P0 |
| `customer.subscription.deleted` | membership | Terminal cancel, start grace period | P0 |
| `customer.subscription.trial_will_end` | membership | Email 3 days out (3.8.1) | P2 |
| `invoice.paid` | billing | `PaymentRecord` SUCCEEDED; clear `PAST_DUE` | P0 |
| `invoice.payment_failed` | billing | `PAST_DUE`; dunning email (3.6.3) | P0 |
| `invoice.payment_action_required` | billing | Email "action needed" with hosted invoice URL | P1 |
| `invoice.upcoming` | billing | Renewal-reminder email | P2 |
| `charge.refunded` | billing | Negative `PaymentRecord`; possibly revoke membership | P1 |
| `charge.dispute.created` | alert | SNS to staff; freeze access pending review | P1 |
| `payment_intent.payment_failed` | billing | One-off failures (day passes, rentals) | P1 |
| `customer.deleted` | membership | Null out `stripe_customer_id`, terminate memberships | P2 |

**Use snapshot events, not thin events, for these v1 types.** Snapshot payloads are self-contained; thin events would force a Stripe API call on every single message, which triples latency and burns rate limit for no benefit at this volume. Reserve thin events for the specific cases in §7 where staleness actually matters.

---

## 6. Membership status model

Local statuses, derived from Stripe's subscription status plus our own grace policy:

| Local | Derived from | Can book equipment? | Can badge in? |
|---|---|---|---|
| `TRIALING` | `trialing` | Yes | Yes |
| `ACTIVE` | `active` | Yes | Yes |
| `PAST_DUE` | `past_due` | Yes | Yes |
| `GRACE` | `canceled`/`unpaid` + within grace window | No | Yes (retrieve belongings) |
| `CANCELED` | `canceled` + grace expired | No | No |
| `INCOMPLETE` | `incomplete`, `incomplete_expired` | No | No |
| `PAUSED` | `paused` | No | No |

`PAST_DUE` intentionally keeps access. Stripe Smart Retries typically resolve a failed card within days, and locking a paying member out of the building over a temporary decline is the wrong tradeoff for a community makerspace. **OPEN:** confirm this with the client — it's a business policy call, not a technical one.

**Expiry is derived at read time, not by a cron job.** `hasActiveMembership()` checks `status IN (...) AND current_period_end > now()`. The hourly sweep Lambda exists only to fire *side effects* at boundaries Stripe doesn't emit events for — specifically the end of our custom grace period, which is our invention and therefore our responsibility.

---

## 7. Idempotency and ordering

Two independent problems. Solve them separately.

### Idempotency (duplicate delivery)

At-least-once delivery, twice over: Stripe may redeliver, and SQS standard queues may deliver a message more than once. Handling `invoice.paid` twice must not create two `PaymentRecord` rows.

The `stripe_event_log` primary key is the guard. In the internal command handler:

```java
@Transactional
public void handle(StripeEventCommand cmd) {
    // Insert-first. PK violation == already seen == no-op.
    try {
        eventLogRepository.insertReceived(cmd.eventId(), cmd.type(), ...);
    } catch (DataIntegrityViolationException e) {
        log.debug("Duplicate Stripe event {}, skipping", cmd.eventId());
        return;
    }
    dispatch(cmd);                       // same transaction as the log insert
    eventLogRepository.markProcessed(cmd.eventId());
}
```

Insert-first, in the same transaction as the domain write. If dispatch throws, the whole thing rolls back including the log row, and the retry is clean. Marking processed *after* the write in a separate transaction would leave a window where a crash means permanent loss.

### Ordering (out-of-order delivery)

Stripe is explicit: <cite index="20-1">delivery order is not guaranteed, and because snapshot events record `created` in seconds, distinct events can share a timestamp — don't use `created` to determine order or whether an event was already processed; track event IDs for duplicate detection instead</cite>.

This matters concretely: `customer.subscription.updated` (→ `canceled`) can land before an earlier `updated` (→ `active`) and resurrect a dead membership.

Three defenses, use all three:

1. **Version guard on the mirrored object.** Store the subscription object's own timestamp in `membership.stripe_updated_at`. Reject any event whose object timestamp is older than what's stored. This is a monotonicity check on the *resource*, not on the event envelope.
2. **Re-fetch on ambiguity.** For `customer.subscription.*`, have the Lambda call `stripe.Subscription.retrieve(id)` and apply *that* state rather than the embedded snapshot. One extra API call per subscription event, and the result is always current. Worth it for the P0 membership events; skip it for `invoice.*` where the snapshot is an immutable historical fact.
3. **SQS FIFO with `MessageGroupId = customer_id`** — **OPEN**. This serializes per-customer, which is exactly the ordering guarantee you want. But EventBridge→SQS FIFO requires a group ID from the event, adds throughput limits, and defenses 1–2 already cover the failure mode. Recommend starting with standard queues and only escalating if reordering shows up in the event log.

---

## 8. Reconciliation (3.6.2) — mandatory, not optional

Because EventBridge has no manual resend, a nightly reconciliation Lambda is the only recovery path when processing has a bug.

`reconcile_stripe_events` on EventBridge Scheduler, daily at 03:00 local:

1. Read `MAX(received_at)` from `stripe_event_log` (via internal API), minus a 6-hour overlap window.
2. Page `GET /v1/events?created[gte]=…&types[]=…` for the subscribed types.
3. For each event ID not present in `stripe_event_log`, submit it through the same internal command endpoint with `source=RECONCILIATION`.
4. Emit a CloudWatch metric `StripeEventsRecovered`. Alarm if > 0 twice in a row — that means the live path is broken, not just flaky.

A second reconciler compares state rather than events: for every local `Membership` with a `stripe_subscription_id`, fetch the subscription and assert status/period match. Log divergences to a `billing_divergence` report for staff. Run weekly. This catches the class of bug where events *were* processed but processed wrongly, which the event-replay reconciler cannot see.

**Retention note:** Stripe's Events API only serves the last 30 days. Anything older is unrecoverable from Stripe, which is another argument for the append-only `payment_record` ledger being the durable financial record.

---

## 9. Lambda function inventory (Python 3.13)

| Function | Trigger | Responsibility |
|---|---|---|
| `stripe_membership_consumer` | SQS `membership-events` | Subscription lifecycle → internal command |
| `stripe_billing_consumer` | SQS `billing-events` | Invoices, checkout, payments, refunds → internal command |
| `stripe_alert_consumer` | SQS `alert-events` | Disputes and fraud → SNS to staff + freeze command |
| `stripe_reconciler` | Scheduler, daily | §8 event replay |
| `stripe_state_auditor` | Scheduler, weekly | §8 state comparison |
| `membership_expiry_sweep` | Scheduler, hourly | Grace-period boundary side effects |
| `stripe_dlq_reporter` | SQS DLQ | Structured CloudWatch log + SNS on any DLQ arrival |

Shared code in a Lambda layer: envelope parsing, internal-API client with SigV4 signing, structured logging (AWS Lambda Powertools for Python — `@logger.inject_lambda_context`, `@tracer.capture_lambda_handler`, and `BatchProcessor` for partial batch failure).

### Partial batch failure

Set `ReportBatchItemFailures` on the SQS event source mapping and return `batchItemFailures`. Without it, one poison message in a batch of ten causes all ten to be redelivered, and the nine good ones get processed repeatedly. Powertools' `BatchProcessor` handles this correctly; hand-rolling it is a common source of duplicate-processing bugs.

### Envelope parsing

```python
def handler(event, context):
    for record in event["Records"]:
        eb = json.loads(record["body"])       # EventBridge envelope from SQS
        stripe_event = eb["detail"]           # the Stripe Event object
        event_id   = stripe_event["id"]       # evt_…
        event_type = stripe_event["type"]     # e.g. invoice.paid
        obj        = stripe_event["data"]["object"]
        livemode   = stripe_event["livemode"]
```

Guard on `livemode` and reject sandbox events in the prod function, and vice versa. Sandbox and live events arriving on the wrong bus is a real and confusing failure.

### Keep the Lambdas thin

A consumer should be: parse → validate → map to a command DTO → POST → translate HTTP status into success or raise. No business rules, no SQL, no membership-state logic. If a Lambda starts needing to know what `GRACE` means, that logic belongs in `MembershipService`.

---

## 10. Internal command API

New security chain in `SecurityConfig`, matching the existing per-module pattern:

```java
@Bean
@Order(3)
public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
    applySharedConfig(http)
        .securityMatcher("/api/internal/**")
        .addFilterBefore(internalAuthFilter, JwtAuthFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("SCOPE_INTERNAL"));
    return http.build();
}
```

Note `applySharedConfig` disables CSRF and adds `JwtAuthFilter` to every chain. `JwtAuthFilter` passes through when no Bearer token is present, so it won't interfere — but the internal filter must run first and must set an authentication that is *not* a `UserPrincipal`, so that `UserSecurity.isSelf()` continues to fail closed on these requests.

Endpoints:

```
POST /api/internal/billing/events   { eventId, type, apiVersion, livemode, occurredAt, payload }
POST /api/internal/billing/sweep    { asOf }
GET  /api/internal/billing/cursor   → { lastReceivedAt }
```

**Authentication — OPEN.** Two candidates:

- **SigV4 with an IAM role.** Lambda signs with its execution role; Spring verifies. No shared secret to rotate, IAM-native, auditable in CloudTrail. Costs a custom verification filter (there's no Spring Security starter for inbound SigV4).
- **Shared secret from Secrets Manager**, sent as a header, compared with `MessageDigest.isEqual` (constant-time). Trivial to implement, but it's a bearer credential that needs rotation.

Given the timeline, the shared secret is the pragmatic choice **provided** the internal chain is also network-restricted (security group / ALB listener rule), so the secret is defense-in-depth rather than the only control. Document the SigV4 upgrade as future work.

Response contract that the Lambda relies on:

- `200` — processed
- `409` — duplicate event, already processed → **treat as success**, delete the SQS message
- `422` — permanently unprocessable → **do not retry**, route straight to DLQ
- `5xx` / timeout → raise, let SQS retry

Getting `409` wrong is the single most likely source of an infinite retry loop.

---

## 11. Local development and testing

The awkward part of Option C: you can't point `stripe listen` at an EventBridge partner bus.

**Layered approach:**

1. **Domain services first, no Stripe.** `MembershipService`, `PaymentService`, and the internal controller are plain Spring and testable with Mockito + `@WebMvcTest`, exactly like the existing `UserControllerTest`. Feed them command DTOs directly. This is where the real business logic gets covered and it needs no AWS at all.
2. **Dev-only webhook endpoint.** A `@Profile("dev")` controller at `/api/dev/stripe/webhook` that verifies the signature and calls the same internal command service. `stripe listen --forward-to localhost:8080/api/dev/stripe/webhook` then drives the full domain path locally. Must not be registered in the prod profile.
3. **Lambda unit tests.** `pytest` with recorded EventBridge envelopes as fixtures (capture real ones from the CloudWatch Logs audit rule). Mock the internal API with `responses`. `moto` for any AWS calls.
4. **Stripe Test Clocks** for subscription lifecycle. This is the only sane way to test renewal, dunning, and expiry — you advance a simulated clock instead of waiting a month. Non-negotiable for 3.5.2.
5. **Sandbox end-to-end.** A Stripe sandbox with its own EventBridge destination into a dev AWS account, exercised before demo. Note that <cite index="20-1">sandbox retries are only three attempts over a few hours</cite>, unlike live mode's three days — so a sandbox test with the app down will lose events faster than production would.

---

## 12. Security

Applying the posture already established in the codebase:

- **No card data ever touches our infrastructure.** Checkout is hosted; we store only Stripe IDs. This is what keeps PCI scope at SAQ A, and it's worth saying explicitly in the capstone write-up.
- **Secrets.** `STATE.md` already flags that `application.properties` has the Auth0 client secret and JWT signing key in plaintext, committed. **Do not add `STRIPE_SECRET_KEY` to that file.** Move all four to Secrets Manager or Beanstalk environment properties before writing any Stripe code — a leaked Stripe live key is a materially worse incident than a leaked dev Auth0 secret, and the ones currently in the repo should be rotated regardless.
- **Restricted API keys.** The Lambda functions only need to *read* (`Subscription.retrieve`, `Event.list`). Issue them a restricted key with read-only permissions on Billing and Events. The Spring app gets the full-access key because it creates sessions. Two keys, least privilege each.
- **No public webhook surface** — the main structural security benefit of Option C.
- **IDOR.** Billing endpoints follow the established `/me` pattern: `GET /api/billing/me/subscription`, `POST /api/billing/checkout-session` with the plan code in the body and the user from `UserPrincipal`. Never `/api/billing/{userId}/…`.
- **Price IDs come from the server.** The client sends a plan *code*; the server looks up `stripe_price_id` from `membership_plan`. Never accept a price ID or amount from the client.
- **Log hygiene.** Never log full event payloads at INFO — they contain customer email, name, and partial card details. Log `event_id`, `type`, and outcome. Note that the "all events → CloudWatch Logs" audit rule *does* store full payloads, so set a retention policy (30 days) and restrict access to that log group.
- **Refund authorization.** Refunds are `ADMIN`-only, go through a service method that records the actor (following the `softDeleteUser(id, actorId)` pattern), and are policy-checked before execution.

---

## 13. Cost

Rough monthly, at 1,000 MAU (~1,000–3,000 Stripe events/month):

| Item | Estimate |
|---|---|
| EventBridge partner events | Free (partner source ingestion is not charged) |
| SQS | Free tier (1M requests) covers it |
| Lambda invocations + duration | Free tier covers it |
| CloudWatch Logs | ~$0.50 with 30-day retention |
| Stripe fees | 2.9% + $0.30 per transaction |
| **NAT Gateway** | **$0 — avoided by keeping Lambda out of the VPC** |

The NAT Gateway line is the one that matters. Option D would add roughly $32/month plus data processing, which against student credits is real money. Calling Spring over its public HTTPS endpoint keeps the Lambdas VPC-free.

---

## 14. Phased implementation

**Phase 1 — Domain layer, no Stripe (unblocks everything, testable immediately)**
- V4 migration, Flyway added, `ddl-auto: validate`
- `Membership`, `MembershipPlan`, `PaymentRecord`, `StripeEventLog` entities + repositories
- `MembershipService` with `hasActiveMembership`, status derivation, grace policy
- `StripeEventService.handle(command)` with the insert-first idempotency guard
- Internal command controller + security chain
- Unit and `@WebMvcTest` coverage mirroring `UserControllerTest`
- **Deliverable:** subscription status engine (3.5.1) working, driven by test fixtures

**Phase 2 — Outbound Stripe (the user-facing purchase flow)**
- `stripe-java` (33.x, pin the version) in `pom.xml`
- `BillingController`: `POST /api/billing/checkout-session`, `POST /api/billing/portal-session`, `GET /api/billing/me/subscription`
- Lazy Stripe Customer creation with `findByIdForUpdate` to prevent duplicate customers on concurrent checkout
- Dev-only webhook endpoint + `stripe listen`
- **Deliverable:** end-to-end purchase works locally against a Stripe sandbox (3.6.1)

**Phase 3 — EventBridge ingest**
- Enable Workbench; create the event destination; **associate the partner event source within 7 days**
- Terraform: event bus, rules, SQS queues, DLQs, IAM roles
- Python consumers + shared layer, Powertools, partial batch failure
- CloudWatch audit rule with 30-day retention
- **Deliverable:** live event-driven processing

**Phase 4 — Resilience and policy**
- Reconciliation and state-audit Lambdas (3.6.2)
- Dunning emails via the existing `EmailService`/SES path (3.6.3, feeds 3.8.1)
- Cancellation policy: `cancel_at_period_end`, refund rules, proration decisions (3.6.4)
- DLQ alarms, `StripeEventsRecovered` metric alarm
- Expiry sweep Lambda (3.5.2)

**Phase 5 — Gating**
- `ReservationService.create` calls `membershipService.hasActiveMembership(userId)` before booking
- Badge/RFID access checks membership status (3.9.1, when hardware is chosen)

Phases 1 and 2 deliver a defensible working payment system on their own. If the schedule compresses, Phase 3 can be replaced by Option A's webhook controller in about a day, because the domain layer is identical.

---

## 15. Open decisions

1. **`PAST_DUE` access policy** — keep access during dunning, or cut immediately? Business call, needs the client.
2. **Grace period length** — 7 days after cancellation for badge-in-only access? Arbitrary until the client weighs in.
3. **Internal API auth** — SigV4 vs. shared secret + network restriction.
4. **SQS FIFO** — standard queues with version guards, or FIFO keyed on customer ID?
5. **Day passes** — one-off Checkout payment, or prepaid credit balance on the account?
6. **Proration on plan change** — Stripe default proration, or no proration and change at period end?
7. **Refund policy** — full refund window in days, and does a refund revoke membership immediately or at period end?
8. **Student verification** — how is the `STUDENT` plan eligibility proven before checkout? Stripe won't do this.
9. **Multi-currency / tax** — Stripe Tax adds cost and complexity. Recommend deferring; single-jurisdiction NY sales tax handling is likely out of capstone scope, but confirm.

---

## 16. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Partner event source not associated within 7 days → destination auto-disabled | High | Calendar reminder; Terraform the association in the same apply |
| No manual event resend on EventBridge | High | Reconciliation Lambda is mandatory (§8) |
| Out-of-order subscription events corrupt membership state | High | Version guard + re-fetch (§7) |
| EventBridge 256 KB entry size limit vs. large Stripe events (invoices with many line items) | Medium | **Verify behavior before relying on it.** Mitigate by re-fetching from the Stripe API rather than trusting the snapshot for large objects |
| Two-language stack doubles the maintenance surface for a six-person student team | Medium | Keep Lambdas thin (§9); all business logic stays in Java |
| Stripe events retained only 30 days | Medium | `payment_record` is the durable ledger, not Stripe |
| Plaintext secrets currently in the repo | High | Rotate and move to Secrets Manager **before** Phase 2 |
| Test Clocks not used → renewal/expiry paths untested | Medium | Mandatory in Phase 1 test plan |
| `409` mishandled in the Lambda → infinite retry | Medium | Explicit status-code contract (§10) + DLQ alarm |
