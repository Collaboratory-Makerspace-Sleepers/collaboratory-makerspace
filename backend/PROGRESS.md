# Collaboratory Makerspace — Backend Progress

## Component Breakdown

| # | Component | % | Why |
|---|-----------|---|-----|
| **3.1.1** | Database Setup | **75%** | PostgreSQL configured. JPA entities: `User`, `Equipment`, `EquipmentReservation`, `RegistrationInvite`, `UserProfile`, `Address`, `PhoneNumber`, `AppRole`. Flyway V1–V5 in place: baseline schema, user_roles join table, billing core tables (`stripe_event_log`, `membership_plan`, `membership`, `payment_record`), and permission-based RBAC tables (`permissions`, `role_permissions`). Missing: no seed data, no Dockerfile or cloud config. |
| **3.1.2** | Cloud Infrastructure | **5%** | No Dockerfile, no Kubernetes manifests, no cloud provider config. Only a local PostgreSQL connection string exists. |
| **3.1.3** | CI/CD Pipeline | **0%** | No GitHub Actions workflows, no Jenkins, no pipeline config of any kind. |
| **3.2.1** | Authorization & Authentication | **85%** | Auth0/Okta SSO fully wired. Local in-person registration added: staff pre-registers a user via `POST /api/v1/admin/registrations` (generates a `RegistrationInvite` with a secure token, emails a claim link), user claims with `POST /api/v1/registrations/claim` (activates account). `AccountStatus.PRE_REGISTERED` + `UserStateService.State.PENDING` handle the pre-registered window; `autoClaimByEmail` supports OAuth-initiated claim. Gaps: no refresh token flow, no server-side logout. |
| **3.2.2** | User Roles | **95%** | `AppRole` entity replaces the `Role` enum. Roles are database rows in the `roles` table with an explicit permission set stored in `role_permissions`. 7 built-in roles seeded; `is_system` flag prevents accidental deletion. Admin API (`RoleController`) supports runtime CRUD on roles and permission assignment without redeployment. `RoleService` validates permission codes against the catalog and evicts the `userPermissions` cache on change. Gap: no bulk-assignment tooling for assigning roles to users. |
| **3.2.3** | User Profiles | **65%** | `UserProfile`, `Address`, and `PhoneNumber` models added and decoupled from `User`. `/me` GET and PATCH endpoints exist. Missing: avatar/photo URL, preferences, profile-completeness tracking. |
| **3.3.1** | Role-based Access Control | **95%** | Full permission-based RBAC. All security filter chains unified in `SecurityConfig` and reference `Permission` constants — no hard-coded role names, no role hierarchy. `UserPermissionService` computes the effective permission set per user (union of all assigned role permissions) and caches it for 30s. Permissions propagate within 30s of any role or permission change, without reissuing tokens. Gap: `GUEST`/`RENTEE` roles exist in the catalog but no endpoints are currently gated on permissions specific to them. |
| **3.3.2** | Token System / OAuth2 | **85%** | JWT issuance and validation fully implemented. HttpOnly cookie flow in place. `UserStateService` (30s cache) and `UserPermissionService` (30s cache) checked on every authenticated request. Authorities are permission codes loaded from DB, not role claims embedded in the JWT. Gap: no refresh token, no token revocation/blocklist. |
| **3.3.3** | API Security & Middleware | **60%** | `JwtAuthFilter` validates tokens and checks soft-delete + permission state on every request. CSRF correctly disabled for stateless API. Gap: no rate limiting, no CORS config, no input sanitization beyond `@Valid` on a few DTOs. |
| **3.4.1** | Waiver Versioning | **0%** | No model, no table, no service, no controller. Completely absent. |
| **3.4.2** | Hard-block Enforcement | **0%** | Nothing exists to gate access based on waiver acceptance. |
| **3.5.1** | Subscription Status Engine | **15%** | V4 migration adds `membership_plan` (plan catalog with Stripe Price IDs and `grants_role` FK) and `membership` (local mirror of Stripe subscription with status, period, grace, and cancel fields). Partial unique index enforces at most one non-terminal membership per user. No Java models, services, or API layer yet. |
| **3.5.2** | Expiry Logic | **0%** | No scheduler, no expiry dates, no status transitions in Java. Schema columns exist (`current_period_end`, `grace_period_ends_at`). |
| **3.6.1** | Stripe Payment Processing | **10%** | V4 migration adds `users.stripe_customer_id`, `stripe_event_log` (idempotency ledger with status, attempt count, and source), and `payment_record` (immutable ledger). Schema is production-ready for webhook ingestion. No Stripe SDK in `pom.xml`, no Java models for billing, no webhook controller. |
| **3.6.2** | Reconciliation | **0%** | Schema includes `source` column on `stripe_event_log` (EVENTBRIDGE \| RECONCILIATION \| MANUAL) to support future reconciliation jobs. No implementation. |
| **3.6.3** | Retry Logic | **0%** | `stripe_event_log.attempt_count` column is in place. No retry scheduler or logic. |
| **3.6.4** | Cancellation Policies | **0%** | `membership.cancel_at_period_end` and `canceled_at` columns exist. No business logic. |
| **3.7.1** | Booking Engine | **90%** | Fully implemented: `ReservationController`, `ReservationService` (overlap/conflict detection, status guards, ownership checks), `ReservationRepository` (JPQL overlap query with excludeId for extend), `ReservationStatus` enum, `ReservationDTO`. Equipment availability enforced (MAINTENANCE/RETIRED blocked). `@EntityGraph` on list queries prevents N+1. Gap: no automated COMPLETED transition. |
| **3.7.2** | Capacity Rules | **50%** | Overlap detection fully implemented via JPQL. Gap: no max-concurrent-users-per-equipment rule. |
| **3.7.3** | Maintenance Fallback | **50%** | `ReservationService.create` blocks new reservations when equipment is MAINTENANCE or RETIRED. Gap: no automatic cancellation of existing reservations when equipment goes into maintenance. |
| **3.8.1** | Email Triggers | **10%** | `EmailService` interface and `StubEmailService` stub implemented (logs to console). Used for registration invite emails. No real mail provider (no Spring Mail, SendGrid) wired. |
| **3.8.2** | Alert Logic | **0%** | Not started. |
| **3.9.1** | Badge Integration | **0%** | No badge model, no hardware interface, no API surface. |
| **3.10** | Logging System | **20%** | `@Slf4j` used in `UserService`, `ReservationService`, `AdminRegistrationService`, `RoleService` with `log.info` audit calls (role changes, permission updates, soft deletes, reservation create/cancel/extend). Spring Security TRACE logging enabled in properties. No structured/centralized logging (no ELK, no log aggregator, no consistent audit trail). |

---

## What to Work on Next

### Recommended: 3.4.1 — Waiver Versioning

The booking engine is implemented (90%) and RBAC is now permission-driven and extensible. The natural next blocker for reservations is waiver enforcement: a makerspace should not allow equipment reservations without a signed waiver. This component has no external dependencies — just a new model, a migration, and a service.

Minimum slice:
- **`Waiver` model** — `id`, `version`, `content` (or URL), `effectiveDate`
- **`WaiverSignature` model** — `userId`, `waiverId`, `signedAt`
- **`WaiverService`** — `hasSignedCurrentWaiver(userId)`, `sign(userId, waiverId)`
- **`WaiverController`** — `GET /api/v1/waivers/current`, `POST /api/v1/waivers/{id}/sign`
- **Hard-block hook** in `ReservationService.create()` — reject if user hasn't signed the current waiver version

### Then: 3.6.1 — Stripe Payment Processing

The billing schema (V4) is production-ready. The next step is the Java layer:
- Add `stripe-java` SDK to `pom.xml`
- Java models for `Membership` and `MembershipPlan` (mapped to V4 tables)
- `StripeWebhookController` — ingest events, record to `stripe_event_log`, route to handlers
- `MembershipService` — sync Stripe subscription state into `membership`, apply `grants_role`
- `CheckoutService` — create Stripe Checkout sessions, lazily create Stripe Customer, store `stripe_customer_id`

### Also consider: 3.5.1 — Subscription Status Engine (gates alongside waivers)

Once Stripe integration is in place, membership status becomes the second gate for reservations: expired members should be blocked from new bookings. This can be layered into `ReservationService.create()` alongside the waiver check.
