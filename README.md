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

### Documentation

- [`STATE.md`](backend/STATE.md) — detailed architecture reference: package structure, auth flow, access control matrix, caching, known issues
- [`PROGRESS.md`](backend/PROGRESS.md) — component-by-component completion tracking and next steps

---

## Frontend

React + Vite SPA. See [`frontend/README.md`](frontend/README.md) for setup instructions.

---

## Branch Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable, reviewed code |
| `development/*` | Feature branches merged via PR |

Current active branch: `development/stripe-payment-subscriptions`
