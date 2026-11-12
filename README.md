# collaboratory-makerspace

Full-stack web application for managing a makerspace: member accounts, equipment reservations, subscriptions, and in-person registration.

---

## Repository Structure

```
collaboratory-makerspace/
├── backend/    Spring Boot 3.3.9 / Java 21 REST API
└── frontend/   React + Vite SPA
```

---

## Backend

### Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3.9 |
| Language | Java 21 |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL (prod), H2 (test) |
| Auth | Auth0 via Okta Spring Boot Starter 3.0.7 |
| JWT | jjwt 0.12.6 |
| Cache | Caffeine |
| Migrations | Flyway |
| Build | Maven |

### Key Features

- **Auth0 SSO** — OAuth2/OIDC login with JWT cookie → Bearer token exchange
- **Permission-based RBAC** — roles carry explicit permission sets stored in the database; no hard-coded hierarchy; permissions are manageable at runtime via the admin API
- **Equipment management** — CRUD with status tracking (AVAILABLE, IN_USE, MAINTENANCE, RETIRED)
- **Reservations** — conflict-checked bookings with overlap detection, extend, cancel, and ownership enforcement
- **In-person registration** — staff pre-register users via invite link; users claim and activate their account
- **Soft-delete** — accounts are never hard-deleted; state is cached (30s TTL) and enforced on every request
- **Billing schema** — Stripe-ready tables for membership plans, subscriptions, and payment records (Java layer in progress)

### Quick Start

```bash
cd backend

# Copy and configure environment
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Fill in: Auth0 credentials, JWT secret, PostgreSQL URL

# Run (requires PostgreSQL running locally)
./mvnw spring-boot:run

# Run tests (H2 in-memory, no external services required)
./mvnw test
```

### Sharing the local database with main

`frontendAdmin` uses main's backend migration history and booking APIs. For the
existing local Docker database, launch the backend from `backend/` with
`--spring.datasource.url=jdbc:postgresql://localhost:5434/collab_app_clean`
and `--spring.jpa.hibernate.ddl-auto=validate`. Supply database credentials via
local configuration, never committed files.

Keep Flyway validation enabled. Do not repair migration history or enable
Hibernate schema updates to connect an incompatible branch. Test compatibility
on a separate database copy before reconnecting to the shared database.

After membership checkout, the payment return page verifies the subscription,
creates the pending equipment reservation, and opens My Reservations. Failed
confirmation keeps the booking details available for retry without another
payment. Retries using the same waiver and booking interval return the existing
reservation.

Booking signatures expire after 30 minutes. Returning to an approved waiver and
continuing to checkout requests a current signature; signing again does not
reuse an expired signature or rewrite the original signing timestamp. Reservation
errors include the rejection reason so users can re-sign without repeating payment.

Billing History synchronizes paid Stripe invoices and completed one-time
checkouts through `POST /api/v1/billing/me/payments/sync`. Existing records are
updated rather than duplicated, and Stripe receipt or hosted invoice links are
displayed. A synchronization failure is reported while retaining saved history.

The admin home uses `GET /api/v1/users/activity/summary` for upcoming equipment
bookings, signed waivers awaiting approval, upcoming enrolled class seats, and
successful payment counts. `GET /api/v1/users/activity/feed` shows database-backed
bookings/cancellations, waiver statuses, class enrollments, completed training,
payments with saved receipt links, and card scans. These are current record
snapshots, not an immutable audit log. Payment activity appears after a webhook
or billing-history synchronization saves it to the database.

Admin totals, recent activity, and the hardware waiver queue refresh every five
seconds while visible and immediately when returning to the tab. Refresh dashboard
reloads totals and activity on demand. No admin activity or totals are stored in
localStorage; API failures are displayed instead of placeholder capacity values.

Account and Membership share database-backed membership polling, including an
active paid day pass (24 hours after purchase). Opening either page or selecting
Check again synchronizes Stripe subscriptions; no subscription is represented by
HTTP 204. Recurring upgrades are prorated immediately. Downgrades take effect
immediately without a prorated refund, with the cheaper price at next renewal.
Confirmed Stripe plan changes and cancellations update the local membership
record directly rather than depending on webhook delivery.

Stripe returns now include the checkout session ID for day passes and saved-card
setup as well as subscriptions. The authenticated confirmation endpoint verifies
ownership and payment, saves the specific paid checkout and receipt, then allows
booking. Legacy return links and manual reservation retries reconcile saved
payments before requesting another purchase. Day passes expire 24 hours after
the actual charge; a signed waiver alone is not a payment or membership.
Reservation day-pass checkout offers a visible Save card option. Consented cards
are vaulted by Stripe and set as the default after confirmation. The dashboard
shows only brand, last four digits, and expiry. Previously unsaved cards can be
added through Payment Methods without buying another pass.

### Documentation

- [`STATE.md`](backend/STATE.md) — detailed architecture reference: package structure, auth flow, access control matrix, caching, known issues
- [`PROGRESS.md`](backend/PROGRESS.md) — component-by-component completion tracking and next steps

---

## Frontend

React + Vite single-page app consuming the backend REST API.

### Tech Stack

| Layer | Technology |
| ----- | ---------- |
| Framework | React 19 |
| Build | Vite 7 |
| Routing | React Router 7 |
| Styling | Tailwind CSS 4 |
| Linting | ESLint 9 |

### Key Features

- **Auth0 SSO** through the backend redirect flow; the JWT cookie issued at login is exchanged for a Bearer token on API calls
- **Role-aware UI** rendered from the permission set returned for the current user
- **Equipment browsing and reservation** with conflict feedback surfaced from the API
- **Account claim flow** for users invited through in-person registration

See [`frontend/README.md`](frontend/README.md) for setup instructions.
---

## Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, reviewed code |
| `development/*` | Feature branches merged via PR |

Current active branch: `development/stripe-payment-subscriptions`
