# Collaboratory Makerspace — Backend

Spring Boot 3.3.9 / Java 21 REST API. Manages users, equipment, reservations, and memberships for the Collaboratory Makerspace.

---

## Tech Stack

- **Framework:** Spring Boot 3.3.9, Java 21
- **Database:** PostgreSQL (prod), H2 (test)
- **Local services:** PostgreSQL and Redis via Docker Compose
- **Auth:** Auth0 (OIDC/OAuth2) + OTP (email code)
- **Payments:** Stripe (stripe-java 33.x)
- **Migrations:** Flyway (V1, V3–V6)
- **Build:** Maven

---

## Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL running locally

---

## Local Setup

### 1. Create the database

```sql
CREATE DATABASE collaboratory_makerspace;
CREATE USER app_user WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE collaboratory_makerspace TO app_user;
```

### 2. Configure secrets

Copy the example local properties file and fill in your values:

```bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

Edit `application-local.properties`:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/collaboratory_makerspace
SPRING_DATASOURCE_USERNAME=app_user
SPRING_DATASOURCE_PASSWORD=yourpassword
OKTA_ISSUER=https://your-tenant.auth0.com/
OKTA_CLIENT_ID=your_client_id
OKTA_CLIENT_SECRET=your_client_secret
APP_JWT_SECRET=a-long-random-base64-secret
STRIPE_SECRET_KEY=sk_test_...
stripe.webhook.secret=whsec_...          # only needed for stripe listen in dev
APP_INTERNAL_API_SECRET=your-internal-secret
app.billing.success-url=http://localhost:5173/billing/success
app.billing.cancel-url=http://localhost:5173/billing/cancel
app.billing.portal-return-url=http://localhost:5173/account
```

### 3. Run the app

Start the local PostgreSQL and Redis services from `backend`:

```bash
docker compose up -d postgres redis
```

Then run the application:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local
```

The API is available at `http://localhost:8080`.

Flyway runs automatically on startup and applies all pending migrations.

### 4. Run tests

```bash
./mvnw test
```

119 tests, all passing. Tests use an H2 in-memory database — no PostgreSQL or Stripe credentials needed.

---

## Stripe Local Development

Use the [Stripe CLI](https://stripe.com/docs/stripe-cli) to forward webhook events to the dev endpoint (only registered in the `dev` profile):

```bash
stripe listen --forward-to localhost:8080/api/dev/stripe/webhook
```

Trigger test events:

```bash
stripe trigger customer.subscription.created
stripe trigger customer.subscription.updated
stripe trigger customer.subscription.deleted
```

### Payment acceptance test (Stripe sandbox)

Use an isolated local PostgreSQL database and Redis, a Stripe sandbox/test-mode
`sk_test_` key, and test-mode prices for that account. Do not copy production data or
use a real payment card. Start the frontend and backend on ports 5173 and 8080.
Run `stripe listen --forward-to localhost:8080/api/dev/stripe/webhook` in test
mode, set `stripe.webhook.secret` locally to the listener's signing secret, and
start the backend with the `dev` profile. Keep the listener running during the test.

1. As a test member, buy a paid booking or class through the app. In hosted Stripe
   Checkout, use `4242 4242 4242 4242`, a future expiry, and any three-digit CVC.
   Verify the return page confirms the purchase, the booking/enrollment appears
   in the app, and the payment appears exactly once in payment history. Reload
   the confirmation page and verify no duplicate.
2. Repeat a checkout and back out before paying. Verify the app does not show
   a paid booking or a successful payment.
3. For the paid equipment reservation, submit a cancellation reason. Reject it
   once as an admin and verify the booking stays active. Resubmit, approve with a
   refund in the app, then refund the **same test payment** in Stripe. Verify the
   signed `charge.refunded` event arrives, payment history shows `REFUNDED`, and
   the cancellation moves from Awaiting Stripe to Completed.

The local Maven tests for checkout confirmation, class booking, and Stripe
event handling do not contact Stripe. The opt-in `StripeLiveCheckoutTest`
(`./mvnw -Dstripe.live=true -Dtest=StripeLiveCheckoutTest test`) creates a
sandbox Checkout Session, but does not pay, refund, or exercise the browser.

### Database rollout

These cancellation changes use the existing `reason`, `status`, and `admin_note`
columns from V23; no new migration or backfill is required. Existing requests
without reasons remain reviewable. Paired Pi registration requires the existing
V28 and V29 Flyway migrations. Back up PostgreSQL and validate Flyway on a staging
copy before rollout. Do not disable validation or edit applied migration files to
work around checksum mismatches. Verify a known allowed and denied tap after
upgrading; see the [Pi setup and recovery guide](../hardware/makerspace-pi/START_HERE.md).

---

## Project Structure

```
src/
  main/
    java/com/makerspace/backend/
      config/           Security, Stripe, cache configuration
      controller/       REST controllers and DTOs
      model/            JPA entities and enums
      repository/       Spring Data JPA repositories
      security/         JwtAuthFilter, OAuth2SuccessHandler
      services/         Business logic
    resources/
      db/migration/     Flyway SQL migrations (V1, V3–V6)
      application.properties
      application-dev.properties
      application-prod.properties
  test/
    java/com/makerspace/backend/
      (11 test files, 119 tests)
```

---

## Key Files

| File | Purpose |
|---|---|
| `STATE.md` | Detailed architecture reference — package structure, security chains, auth flows, billing design |
| `PROGRESS.md` | Component-by-component implementation status |
| `../docs/API.md` | Full API reference with request/response examples |
| `../stripe-implementation.md` | Stripe integration task breakdown (Phases 0–5) |
| `../stripe-integration-design.md` | Architecture design and rationale for the Stripe event-driven system |

---

## Authentication

Two flows both issue the same JWT cookie and Bearer token:

**OAuth2 (Auth0):**
```
GET /oauth2/authorization/okta  →  Auth0 login  →  redirect to frontend
GET /api/v1/auth/token          →  returns { "access_token": "..." }
```

**OTP (email code):**
```
POST /api/v1/auth/otp/send    { "email": "user@example.com" }
POST /api/v1/auth/otp/verify  { "email": "user@example.com", "code": "123456" }
                               → returns { "access_token": "..." } + sets cookie
```

All subsequent requests use `Authorization: Bearer <token>`.

---

## Membership Gating

Creating a reservation requires an active membership. Users without one receive HTTP 402:

```json
{ "status": 402, "error": "An active membership is required to reserve equipment" }
```

To purchase a membership:
```
POST /api/v1/billing/checkout-session  { "planCode": "MONTHLY" }
→ { "sessionUrl": "https://checkout.stripe.com/..." }
```

Available plan codes (seeded by V6 migration): `MONTHLY`, `ANNUAL`, `STUDENT`, `DAY_PASS`.

---

## API Reference

See `../docs/API.md` for the full API reference with JSON request/response examples.

---

## Migrations

| Version | Description |
|---|---|
| V1 | Baseline schema |
| V3 | Multi-role `user_roles` join table |
| V4 | Billing core — Stripe tables, membership tables, payment ledger |
| V5 | Permission-based RBAC — permissions catalog, `role_permissions`, seed data |
| V6 | Seed membership plans (MONTHLY, ANNUAL, STUDENT, DAY_PASS) |
