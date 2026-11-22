# Stripe Option C — Claude Code Implementation Brief

_Companion to `stripe-integration-design.md`. That doc holds the rationale; this one holds the work._
_Execute tasks in order. Each task has a verification gate — do not proceed past a failing gate._

---

## Ground rules for this work

1. **Spring owns every database write.** Python Lambdas parse, validate, and forward. If a Lambda needs to know what `GRACE` means, the logic is in the wrong place.
2. **No new business logic in Python.** Consumers map an event to a command DTO and POST it. That's all.
3. **Never accept a price ID or amount from the client.** The client sends a plan code; the server resolves `stripe_price_id` from `membership_plan`.
4. **Billing endpoints follow the `/me` pattern.** No `/api/billing/{userId}/...` — the user comes from `UserPrincipal`.
5. **Do not apply `@SQLDelete`/`@SQLRestriction` to `payment_record` or `stripe_event_log`.** Financial and audit records are never soft-deleted.
6. **Do not add secrets to any committed file.** Placeholders only: `${VAR}` with no default.
7. Run `./mvnw test` after every task in Phases 0–2. All 55 existing tests must stay green.

Tasks marked 🧑 **HUMAN** cannot be done by Claude Code — they need console access or credentials. Stop and report when you hit one.

---

## Phase 0 — Config hygiene (blocking prerequisite)

Do this before writing any Stripe code. Adding a payments secret to the current config layout would compound an existing problem.

### Task 0.1 — Resolve the properties/yml conflict

**Problem:** `backend/src/main/resources/` contains both `application.properties` and `application.yml` with conflicting datasource config (`collaboratory_makerspace`/`admin` vs `collab_app`/`app_admin`). Both load; `.properties` wins. The yml — including `ddl-auto: update` — is partly dead config that reads as live.

**Do:**
- Delete `application.yml`. Consolidate everything needed into `application.properties`.
- Carry over `server.port=8080` and the Hibernate dialect if not already implied.

**Verify:** `grep -r "application.yml" backend/` returns nothing. App still boots against a local Postgres.

### Task 0.2 — Externalize secrets

**Files:** `backend/src/main/resources/application.properties`, `backend/.gitignore`

**Do:**
- Replace every literal secret with a no-default placeholder:
  ```properties
  spring.datasource.url=${SPRING_DATASOURCE_URL}
  spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
  spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
  okta.oauth2.issuer=${OKTA_ISSUER}
  okta.oauth2.client-id=${OKTA_CLIENT_ID}
  okta.oauth2.client-secret=${OKTA_CLIENT_SECRET}
  app.jwt.secret=${APP_JWT_SECRET}
  app.jwt.expiration=3600000
  ```
- Delete `spring.security.user.name` / `spring.security.user.password` — vestigial, unused with the custom `SecurityFilterChain` beans.
- Move `logging.level.org.springframework.security=TRACE` into a new `application-dev.properties`. It writes token material to logs; it must not be in the base file.
- Create `application-prod.properties` with `spring.jpa.hibernate.ddl-auto=validate` and default log levels.
- Add to `backend/.gitignore`:
  ```
  application-local.properties
  application-*-local.properties
  ```
- Create `application-local.properties.example` (committed) documenting the required keys with dummy values.

**Verify:** `grep -nE "(client-secret|jwt.secret|datasource.password)=[^$]" backend/src/main/resources/*.properties` returns nothing.

### Task 0.3 — Fix the test profile leak

**Problem:** `src/test/resources/application-test.properties` only applies to `UserRepositoryTest`, the one test with `@ActiveProfiles("test")`. Every other Spring-context test — including `BackendApplicationTests` — falls through to `src/main/resources/application.properties` and attempts to boot against the real Auth0 tenant.

**Do:**
- `git mv src/test/resources/application-test.properties src/test/resources/application.properties`
- Remove `@ActiveProfiles("test")` from `UserRepositoryTest` (no longer needed — `src/test/resources` shadows main on the test classpath).

**Verify:** `./mvnw test` passes with no network access to Auth0. Temporarily unset the env vars from 0.2 and confirm tests still pass — that proves the test config is actually taking effect.

### Task 0.4 — Add Flyway

Money tables must not be managed by `ddl-auto`.

**Do:**
- Add `flyway-core` and `flyway-database-postgresql` to `pom.xml`.
- Reconcile existing state: `V3__user_roles_table.sql` exists in `db/migration/` but was applied by hand and there is no V1/V2. Create `V1__baseline.sql` capturing the current schema as-is (users, user_profiles, user_phone_numbers, user_addresses, user_roles, equipment, reservation, registration_invite), and either delete V3 or fold it into the baseline. Set `spring.flyway.baseline-on-migrate=true` for existing databases.
- Set `spring.jpa.hibernate.ddl-auto=validate` in `application-prod.properties`, leave `update` in dev if you like, keep `create-drop` in test.

**Verify:** Fresh Postgres + `./mvnw spring-boot:run` produces the full schema via Flyway with `ddl-auto=validate` and no Hibernate validation errors.

### Task 0.5 — 🧑 HUMAN: rotate credentials

Independent of the code changes. Rotate the Auth0 client secret, the JWT signing key, and the commented-out Google and GitHub OAuth secrets. All four are in git history and must be assumed compromised. Rotating the JWT key invalidates issued tokens — everyone re-authenticates once.

Store the new values per environment: `application-local.properties` for local, GitHub Actions repository secrets for CI, SSM Parameter Store SecureString under `/collaboratory/prod/*` for production.

**Gate:** Do not start Phase 1 until 0.1–0.4 are done and `./mvnw test` is green.

---

## Phase 1 — Domain layer, no Stripe dependency

Everything here is plain Spring and fully testable without AWS or a Stripe account. This is the phase that actually delivers requirement 3.5.1.

### Task 1.1 — Migration `V4__billing_core.sql`

**File:** `backend/src/main/resources/db/migration/V4__billing_core.sql`

Use the DDL in `stripe-integration-design.md` §4 verbatim. Five changes: `users.stripe_customer_id` column, plus `stripe_event_log`, `membership_plan`, `membership`, `payment_record` tables.

Note the partial unique index — it's what enforces one live membership per user:
```sql
CREATE UNIQUE INDEX uq_membership_active_per_user
    ON membership(user_id) WHERE status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE');
```

**Verify:** Flyway applies cleanly against a fresh database. `ddl-auto=validate` passes after the entities in 1.2 exist.

### Task 1.2 — Entities and repositories

**New files in `model/`:** `Membership.java`, `MembershipPlan.java`, `PaymentRecord.java`, `StripeEventLog.java`, `MembershipStatus.java` (enum), `PaymentKind.java` (enum), `PaymentStatus.java` (enum), `EventProcessingStatus.java` (enum).

**Modified:** `User.java` — add `stripeCustomerId` field.

**New files in `repository/`:** `MembershipRepository.java`, `MembershipPlanRepository.java`, `PaymentRecordRepository.java`, `StripeEventLogRepository.java`.

Match the existing conventions exactly: Lombok `@Getter @Setter`, `@Enumerated(EnumType.STRING)`, `TIMESTAMPTZ` → `ZonedDateTime` (as `EquipmentReservation` does, not `LocalDateTime`).

`MembershipRepository` needs, mirroring the existing `UserRepository.findByIdForUpdate`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.status IN :statuses")
Optional<Membership> findLiveByUserIdForUpdate(@Param("userId") Long userId,
                                               @Param("statuses") Collection<MembershipStatus> statuses);
```

`StripeEventLogRepository` needs a native insert that can fail on PK violation without poisoning the persistence context — see 1.4.

**Verify:** `@DataJpaTest` round-trips each entity. `ddl-auto=validate` passes against the V4 schema.

### Task 1.3 — `MembershipService`

**File:** `services/MembershipService.java`

Public surface:
```java
boolean hasActiveMembership(Long userId);
MembershipStatus statusOf(Long userId);
Optional<Membership> currentMembership(Long userId);
Membership upsertFromSubscription(SubscriptionSnapshot snapshot);  // called by event handler
void applyGracePeriodExpiry(ZonedDateTime asOf);                   // called by sweep
```

Status derivation table is in the design doc §6. Two behaviors that are easy to get wrong:

- **`PAST_DUE` retains booking access.** Deliberate — Stripe Smart Retries usually resolve a decline within days. Flagged as an OPEN business decision; implement it as a config property `app.billing.past-due-retains-access=true` so it can flip without a code change.
- **Expiry is derived at read time**, not by a cron mutating rows: `hasActiveMembership` checks `status IN (...) AND current_period_end > now()`. The sweep exists only to fire side effects at the grace boundary, which is our invention and gets no Stripe event.

**Critical:** a paid membership must **not** write `Role.MEMBER` into `user_roles`. Authority roles and customer eligibility stay orthogonal. Reservation gating reads `MembershipService`, not the role set.

**Verify:** Unit tests with Mockito covering every row of the §6 status table plus both sides of the `past-due-retains-access` flag.

### Task 1.4 — `StripeEventService` with the idempotency guard

**File:** `services/StripeEventService.java`

This is the highest-risk code in the integration. At-least-once delivery twice over (Stripe redelivery + SQS redelivery) means handling `invoice.paid` twice must not create two `PaymentRecord` rows.

```java
@Transactional
public EventOutcome handle(StripeEventCommand cmd) {
    // Insert-first. PK violation == already seen.
    try {
        eventLogRepository.insertReceived(cmd.eventId(), cmd.type(), cmd.apiVersion(),
                                          cmd.livemode(), cmd.source());
    } catch (DataIntegrityViolationException e) {
        log.debug("Duplicate Stripe event {}", cmd.eventId());
        return EventOutcome.DUPLICATE;   // → HTTP 409
    }
    dispatch(cmd);                        // same transaction
    eventLogRepository.markProcessed(cmd.eventId());
    return EventOutcome.PROCESSED;
}
```

Insert-first, in the **same transaction** as the domain write. Marking processed in a separate transaction after the write leaves a crash window where the event is permanently lost.

`dispatch` routes on `cmd.type()` to handlers for the events in design doc §5. Unknown type → `EventOutcome.SKIPPED`, logged, marked `SKIPPED` (not `FAILED`) — an unsubscribed event arriving is a config drift signal, not an error.

**Out-of-order guard** in the subscription handler:
```java
if (existing.getStripeUpdatedAt() != null
        && snapshot.updatedAt().isBefore(existing.getStripeUpdatedAt())) {
    log.info("Stale subscription event {} for {}, ignoring", cmd.eventId(), snapshot.id());
    return;
}
```
This compares the *resource's* timestamp, not the event envelope's. Stripe records event `created` in whole seconds and does not guarantee ordering, so envelope timestamps are useless for this.

**Verify:** Tests for — same event twice yields one `PaymentRecord` and a `409` on the second; a rollback inside `dispatch` also rolls back the log row so retry is clean; a stale subscription update does not resurrect a canceled membership.

### Task 1.5 — Internal command API

**Files:** `controller/InternalBillingController.java`, `controller/dto/StripeEventCommand.java`, `config/security/InternalAuthFilter.java`, and a new chain in `config/SecurityConfig.java`.

```
POST /api/internal/billing/events   → 200 | 409 | 422 | 5xx
POST /api/internal/billing/sweep    → 200
GET  /api/internal/billing/cursor   → { lastReceivedAt }
```

The status contract is load-bearing. **409 means "already processed, delete the message."** If the Lambda treats it as a failure you get an infinite retry loop, and that is the single most likely bug in this whole integration.

Security chain — insert at `@Order(3)` and renumber the existing `fallbackChain` if needed:
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

Two things to get right:
- `applySharedConfig` adds `JwtAuthFilter` to every chain. It passes through when no Bearer token is present, so it won't interfere — but `InternalAuthFilter` must run **before** it.
- The authentication `InternalAuthFilter` sets must **not** be a `UserPrincipal`. `UserSecurity.isSelf()` and `getUserId()` fail closed on non-`UserPrincipal` principals, and that behavior must be preserved for internal calls.

Auth: shared secret header, compared with `MessageDigest.isEqual` (constant-time — `String.equals` is timing-vulnerable). Value from `${APP_INTERNAL_API_SECRET}`. This is acceptable **only** combined with a security-group restriction so the secret is defense-in-depth rather than the sole control. Add a TODO for the SigV4 upgrade.

**Verify:** `@WebMvcTest` following the `UserControllerTest` pattern — use `SecurityMockMvcRequestPostProcessors.authentication()` with a real authentication object, not `@WithMockUser` (see `NOTES.md` fix 3; `@WithMockUser`'s String principal broke SpEL bean resolution last time). Cover: no header → 401; wrong header → 401; valid header + new event → 200; valid header + duplicate → 409; malformed body → 422.

### Task 1.6 — Seed `membership_plan`

**File:** `V5__seed_membership_plans.sql` or a dev-profile `CommandLineRunner`.

Plan codes MONTHLY / ANNUAL / STUDENT / DAY_PASS with placeholder `stripe_price_id` values. Real price IDs get filled in during Phase 2 after the products exist in Stripe.

**Phase 1 gate:** Subscription status engine (3.5.1) works end to end driven by test fixtures, with no Stripe account and no AWS. `./mvnw test` green. **This phase alone is a defensible capstone deliverable** — if the schedule collapses, you can stop here and hand-enter memberships.

---

## Phase 2 — Outbound Stripe

### Task 2.1 — 🧑 HUMAN: Stripe account setup

Create a Stripe sandbox. Create Products and Prices matching the plan codes from 1.6. Record the `price_…` IDs. Enable Workbench in Developer settings (required for Phase 3). Generate two keys: a full-access secret key for the Spring app, and a **restricted key with read-only Billing + Events permissions** for the Lambdas.

### Task 2.2 — `stripe-java` and `StripeClient` config

Add `com.stripe:stripe-java` to `pom.xml`, pinned to the latest stable 33.x — **check Maven Central for the current version, do not guess.** stripe-java pins an API version at build time, so the version choice determines request behavior.

Configure a single `StripeClient` bean from `${STRIPE_SECRET_KEY}`. Do not use the deprecated global `Stripe.apiKey` static.

### Task 2.3 — `BillingController` and `BillingService`

```
POST /api/billing/checkout-session   { planCode }        → { sessionUrl }
POST /api/billing/portal-session                          → { portalUrl }
GET  /api/billing/me/subscription                         → MembershipDTO
GET  /api/billing/me/payments        (paged)              → List<PaymentRecordDTO>
```

New `@Order` chain for `/api/billing/**`, all authenticated.

**Lazy Stripe Customer creation** — this is where a race bites. Two concurrent checkout clicks create two Stripe Customers for one user. Guard with the existing pessimistic-lock pattern:
```java
User user = userRepository.findByIdForUpdate(userId).orElseThrow();
if (user.getStripeCustomerId() == null) {
    Customer c = stripeClient.customers().create(params,
        RequestOptions.builder().setIdempotencyKey("cust-create-" + userId).build());
    user.setStripeCustomerId(c.getId());
}
```
Belt and braces: DB lock plus a Stripe idempotency key.

Resolve `stripe_price_id` server-side from `planCode`. Never read a price or amount from the request body.

### Task 2.4 — Dev-only webhook endpoint

**File:** `controller/DevStripeWebhookController.java`, annotated `@Profile("dev")`.

`stripe listen` cannot forward to an EventBridge partner bus, so local development needs an HTTP path. This controller verifies the signature with `Webhook.constructEvent`, builds a `StripeEventCommand`, and calls the **same** `StripeEventService.handle` the internal API uses. One code path, two transports.

Must not register under the prod profile. Add a test asserting the bean is absent when `dev` is inactive.

**Verify:** `stripe listen --forward-to localhost:8080/api/dev/stripe/webhook`, then `stripe trigger checkout.session.completed` and `stripe trigger customer.subscription.updated`. Confirm rows land in `stripe_event_log` and `membership`.

### Task 2.5 — Test Clocks

Write an integration test (or a documented manual script) using Stripe Test Clocks to drive: subscription creation → renewal → failed payment → `past_due` → recovery, and separately → cancellation → grace → expiry.

This is the only practical way to test 3.5.2. Do not skip it and do not simulate it with hand-written fixtures — the point is exercising Stripe's actual state machine.

**Phase 2 gate:** A user can buy a membership in a Stripe sandbox and the local database reflects it correctly, end to end, with no AWS involved.

---

## Phase 3 — EventBridge ingest

### Task 3.1 — 🧑 HUMAN: create the Stripe event destination

In Workbench → Webhooks → Create new destination:
- Events on your account
- Select **only** the event types in design doc §5
- Destination type: **Amazon EventBridge**
- Payload format: **snapshot** (not thin)
- AWS account ID and region

⚠️ **Stripe then creates a partner event source in your AWS account that must be associated with an event bus within 7 days.** Miss the window and Amazon deletes the source, Stripe auto-disables the destination, and you have to create a new one. Do the AWS side the same day.

### Task 3.2 — Terraform for the event bus and queues

**New directory:** `infra/` at repo root.

Resources:
- `aws_cloudwatch_event_bus` associated with the Stripe partner source (`aws.partner/stripe.com/ed_…`)
- Rules: `customer.subscription.*` → membership queue; `invoice.*`, `checkout.session.*`, `payment_intent.*`, `charge.refunded` → billing queue; `charge.dispute.*` → alert queue; a catch-all rule → CloudWatch Logs
- Three SQS queues, each with a redrive policy to a matching DLQ, `maxReceiveCount = 3`
- CloudWatch log group with **30-day retention** — the audit rule stores full event payloads containing customer email and name, so retention and access restriction are not optional
- IAM roles: one execution role per function, least privilege
- CloudWatch alarms: any DLQ depth > 0; `StripeEventsRecovered` metric > 0 on two consecutive runs

Follow the ephemeral-environment pattern already in use — these should be cheap to tear down between test sessions.

### Task 3.3 — Python consumers

**New directory:** `lambdas/` at repo root.

```
lambdas/
  shared/               # Lambda layer
    envelope.py         # EventBridge → StripeEventCommand
    internal_client.py  # HTTP client, retries, status-code contract
    config.py           # secrets from SSM
  membership_consumer/
  billing_consumer/
  alert_consumer/
  tests/
    fixtures/           # captured EventBridge envelopes
```

Runtime Python 3.13. Use AWS Lambda Powertools (`Logger`, `Tracer`, `BatchProcessor`).

**Envelope shape** — the Stripe event is nested under EventBridge's `detail`, which differs from every webhook tutorial:
```python
eb = json.loads(record["body"])
stripe_event = eb["detail"]
event_id   = stripe_event["id"]
event_type = stripe_event["type"]
obj        = stripe_event["data"]["object"]
livemode   = stripe_event["livemode"]
```

**Guard on `livemode`.** The prod function rejects sandbox events and vice versa. Cross-wired buses are a real and very confusing failure.

**Partial batch failure is mandatory.** Set `ReportBatchItemFailures` on the event source mapping and return `batchItemFailures`. Without it, one poison message causes the other nine in the batch to be redelivered and reprocessed. Use Powertools' `BatchProcessor` rather than hand-rolling it.

**Status-code handling** — the contract from 1.5:
| Response | Action |
|---|---|
| 200 | Success, delete message |
| 409 | **Success**, delete message (duplicate) |
| 422 | Do not retry — add to `batchItemFailures` after logging as permanent, let it reach the DLQ |
| 5xx / timeout | Raise, let SQS retry |

**Re-fetch for subscription events.** In `membership_consumer`, call `stripe.Subscription.retrieve(id)` with the restricted read-only key and apply *that* state rather than the embedded snapshot. Ordering is not guaranteed and this makes the applied state always current. Do **not** do this in `billing_consumer` — invoice events are immutable historical facts and re-fetching wastes rate limit.

**Verify:** `pytest` against recorded fixtures, `responses` mocking the internal API, `moto` for AWS calls. Cover the 409 path explicitly — that's the infinite-loop bug.

### Task 3.4 — Deployment

SAM or Terraform, whichever matches the existing infra approach. Separate CI workflow from the Maven build; the Lambdas version independently of the Spring app.

Lambdas stay **outside the VPC** — they reach Spring over its public HTTPS endpoint. Putting them in the VPC to reach RDS directly would require a NAT Gateway at roughly $32/month against student credits, for no benefit given Spring owns the writes.

**Phase 3 gate:** `stripe trigger` in the sandbox produces a row in `stripe_event_log` via the full EventBridge → SQS → Lambda → Spring path. Kill the Beanstalk app, trigger events, restart, and confirm the queue drains and everything processes — that's the resilience claim, so prove it.

---

## Phase 4 — Resilience and policy

### Task 4.1 — Reconciliation Lambda

**Not optional.** Stripe does not support manual event resend to EventBridge, so this is the only recovery path when processing has a bug.

`stripe_reconciler`, EventBridge Scheduler, daily 03:00 local:
1. `GET /api/internal/billing/cursor` for `MAX(received_at)`, minus a 6-hour overlap window
2. Page `GET /v1/events?created[gte]=…&types[]=…` for the subscribed types
3. POST any event ID absent from `stripe_event_log` through the internal API with `source=RECONCILIATION`
4. Emit CloudWatch metric `StripeEventsRecovered`

Note the retention ceiling: Stripe's Events API only serves the last 30 days. Beyond that, `payment_record` is the durable record, which is why it's append-only.

### Task 4.2 — State auditor

`stripe_state_auditor`, weekly. For every `Membership` with a `stripe_subscription_id`, fetch the subscription and assert status and period match. Log divergences to a staff-visible report.

This catches a different bug class from 4.1 — events that *were* delivered but processed wrongly. The event-replay reconciler cannot see those.

### Task 4.3 — Expiry sweep

`membership_expiry_sweep`, hourly → `POST /api/internal/billing/sweep`. Fires side effects at the grace-period boundary only. All logic in `MembershipService.applyGracePeriodExpiry`; the Lambda is a trigger.

### Task 4.4 — Dunning and notifications

Wire `invoice.payment_failed`, `invoice.payment_action_required`, `customer.subscription.trial_will_end`, and `invoice.upcoming` into the existing `EmailService` / SES path. Extend the interface alongside `sendRegistrationInvite`. This is requirement 3.6.3 and feeds 3.8.1.

### Task 4.5 — Cancellation and refunds (3.6.4)

- `POST /api/billing/me/cancel` → `cancel_at_period_end=true` (not immediate deletion)
- `POST /api/admin/billing/refunds` → **ADMIN only**, records the actor following the `softDeleteUser(id, actorId)` pattern, policy-checked before execution
- Refund window and whether a refund revokes membership immediately or at period end are OPEN business decisions — implement as config, not hardcoded

### Task 4.6 — Log hygiene pass

Audit every new log statement. `event_id`, `type`, and outcome only. Never full payloads at INFO — they carry customer email, name, and partial card data.

---

## Phase 5 — Gating

### Task 5.1 — Reservation gating

In `ReservationService.create`, before the conflict check:
```java
if (!membershipService.hasActiveMembership(userId)) {
    throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
        "An active membership is required to reserve equipment");
}
```
Read from `MembershipService`, not from the role set. Update `ReservationService` tests for the new failure mode.

### Task 5.2 — Badge access (deferred)

RFID hardware is not selected. Note the integration point — badge check reads membership status with the `GRACE` carve-out from §6 (no booking, but building access to retrieve belongings) — and leave it unimplemented rather than speculatively specified.

---

## Open decisions blocking full implementation

These need the client, not a developer. Implement each as configuration with a documented default so work isn't blocked:

1. `PAST_DUE` retains access? (default: yes)
2. Grace period length (default: 7 days)
3. Refund window in days, and does a refund revoke immediately or at period end?
4. Day passes — one-off Checkout, or prepaid credit balance?
5. Proration on plan change — Stripe default, or change at period end?
6. `STUDENT` plan eligibility verification — Stripe won't do this; who does?
7. Internal API auth — shared secret (now) vs SigV4 (later)

---

## Verification checklist

- [ ] No literal secret in any committed file
- [ ] `./mvnw test` green, all 55 original tests plus new ones
- [ ] Tests pass with Auth0 env vars unset
- [ ] Flyway applies from empty to current with `ddl-auto=validate`
- [ ] Duplicate event → one `PaymentRecord`, second returns 409
- [ ] Lambda treats 409 as success (no retry loop)
- [ ] Stale subscription event does not resurrect a canceled membership
- [ ] Beanstalk down → events queue → restart → queue drains, no loss
- [ ] DLQ alarm fires on a deliberately poisoned message
- [ ] Reconciler recovers an event deleted from `stripe_event_log`
- [ ] Test Clock drives a full renewal and a full expiry cycle
- [ ] Partial batch failure: one bad message in ten does not reprocess the other nine
- [ ] `/api/billing/**` has no user-controllable ID in any path
- [ ] Membership grants do not write to `user_roles`
- [ ] CloudWatch audit log group has 30-day retention and restricted access
