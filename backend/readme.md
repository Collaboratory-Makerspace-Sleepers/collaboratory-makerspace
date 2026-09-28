# Collaboratory Makerspace — Backend

Spring Boot 3.3.9 / Java 21 REST API. Manages users, equipment, reservations, and memberships for the Collaboratory Makerspace.

---

## Tech Stack

- **Framework:** Spring Boot 3.3.9, Java 21
- **Database:** PostgreSQL (prod), H2 (test)
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
