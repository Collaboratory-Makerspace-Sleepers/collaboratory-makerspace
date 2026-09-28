# Collaboratory Makerspace — Backend Progress

## Component Breakdown

| # | Component | % | Why |
|---|-----------|---|-----|
| **3.1.1** | Database Setup | **85%** | PostgreSQL configured. JPA entities cover users, equipment, reservations, registration invites, profiles, roles, and billing. Flyway V1–V6 in place: baseline, user_roles, billing core, permission RBAC, membership plan seeds. Missing: no Dockerfile, no cloud schema config. |
| **3.1.2** | Cloud Infrastructure | **5%** | No Dockerfile, no Kubernetes manifests, no cloud provider config. Only a local PostgreSQL connection string exists. |
| **3.1.3** | CI/CD Pipeline | **0%** | No GitHub Actions workflows, no Jenkins, no pipeline config of any kind. |
| **3.2.1** | Authorization & Authentication | **90%** | Auth0/Okta SSO fully wired. OTP (email code) flow added: `/api/v1/auth/otp/send` and `/verify` provision or resolve users and issue a JWT cookie, same as the OAuth2 path. Local in-person registration (invite + claim) also complete. Gaps: no refresh token, no server-side logout. |
| **3.2.2** | User Roles | **95%** | `AppRole` entity with explicit permission sets. 7 built-in roles seeded; `is_system` flag prevents deletion. Admin API supports runtime CRUD on roles and permissions. Gap: no bulk-assignment tooling. |
| **3.2.3** | User Profiles | **65%** | `UserProfile`, `Address`, `PhoneNumber` models added. `/me` GET and PATCH exist. Missing: avatar/photo URL, preferences, profile-completeness tracking. |
| **3.3.1** | Role-based Access Control | **95%** | Full permission-based RBAC. All security filter chains unified in `SecurityConfig`. `UserPermissionService` caches effective permissions per user for 30s. Gap: `GUEST`/`RENTEE` roles exist in the catalog but no endpoints are gated on permissions specific to them. |
| **3.3.2** | Token System / OAuth2 | **90%** | JWT issuance and validation fully implemented. HttpOnly cookie flow in place for both OAuth2 and OTP paths. `UserStateService` and `UserPermissionService` checked on every authenticated request. Gap: no refresh token, no token revocation/blocklist. |
| **3.3.3** | API Security & Middleware | **60%** | `JwtAuthFilter` validates tokens and checks soft-delete + permission state on every request. `InternalAuthFilter` guards Lambda-facing endpoints with a shared secret (constant-time compare). Gap: no rate limiting, no CORS config, no input sanitization beyond `@Valid`. |
| **3.4.1** | Waiver Versioning | **0%** | No model, no table, no service, no controller. Completely absent. |
| **3.4.2** | Hard-block Enforcement | **0%** | Nothing gates access on waiver acceptance. |
| **3.5.1** | Subscription Status Engine | **85%** | `Membership`, `MembershipPlan` entities fully mapped to V4 schema. `MembershipService` implements `hasActiveMembership` (booking gate), `statusOf`, `upsertFromSubscription` (with out-of-order guard), and `applyGracePeriodExpiry`. Internal sweep endpoint (`POST /api/internal/billing/sweep`) is wired and ready for the hourly Lambda. `past-due-retains-access` and `grace-period-days` are config-driven. Gap: Lambda/EventBridge trigger for the sweep not yet deployed (Phase 3). |
| **3.5.2** | Expiry Logic | **40%** | `applyGracePeriodExpiry` transitions GRACE → CANCELED when `grace_period_ends_at <= asOf`. Sweep endpoint exists. Gap: no deployed scheduler/Lambda to call it; grace period is set on the `Membership` row but the hourly sweep Lambda is Phase 3. |
| **3.6.1** | Stripe Payment Processing | **85%** | `stripe-java` added. `BillingService` creates Checkout sessions (subscription and one-off modes) with lazy Stripe Customer creation (pessimistic lock + idempotency key) and Customer Portal sessions. `StripeEventService` handles `customer.subscription.*` events with insert-first idempotency and out-of-order guards. `DevStripeWebhookController` (dev profile) allows `stripe listen` to drive the full domain path locally. `PaymentRecord` entity and repository in place. Gap: invoice/checkout/charge event handlers are stubs (Phase 3); no cancellation/refund endpoints yet; EventBridge ingest (Phase 3) not deployed. |
| **3.6.2** | Reconciliation | **5%** | `GET /api/internal/billing/cursor` endpoint returns the `MAX(received_at)` from `stripe_event_log`, ready for the reconciliation Lambda to consume. No Lambda deployed. |
| **3.6.3** | Retry Logic | **10%** | `stripe_event_log.attempt_count` column in schema. SQS redrive policy will be configured in Phase 3 Terraform. No retry scheduler in Spring. |
| **3.6.4** | Cancellation Policies | **5%** | `membership.cancel_at_period_end` and `canceled_at` columns exist and are mapped. No `POST /api/billing/me/cancel` endpoint yet. No refund endpoint. |
| **3.7.1** | Booking Engine | **95%** | Fully implemented. Reservation creation now gates on `MembershipService.hasActiveMembership(userId)` — returns HTTP 402 if no active membership. Equipment availability enforced (MAINTENANCE/RETIRED blocked). Conflict detection, status guards, ownership checks all in place. Gap: no automated COMPLETED transition. |
| **3.7.2** | Capacity Rules | **50%** | Overlap detection fully implemented via JPQL. Gap: no max-concurrent-users-per-equipment rule. |
| **3.7.3** | Maintenance Fallback | **50%** | New reservations blocked on MAINTENANCE/RETIRED equipment. Gap: no automatic cancellation of existing reservations when equipment goes into maintenance. |
| **3.8.1** | Email Triggers | **20%** | `EmailService` interface with `sendInvite` and `sendOtp`. `SmtpEmailService` (prod) and `StubEmailService` (dev/test) both implemented. Registration invite emails and OTP codes send. Gap: no dunning emails (payment failure, trial ending), no real SES/SMTP provider verified end-to-end. |
| **3.8.2** | Alert Logic | **0%** | Not started. |
| **3.9.1** | Badge Integration | **0%** | No badge model, no hardware interface, no API surface. |
| **3.10** | Logging System | **25%** | `@Slf4j` used across services with audit calls for role changes, permission updates, soft deletes, reservation lifecycle, and Stripe event processing. No structured/centralized logging (no ELK, no log aggregator). |

---

## What to Work on Next

### Recommended: 3.4.1 — Waiver Versioning

The booking engine (95%) and membership gating (85%) are both in place. The remaining gap for reservation access control is waiver enforcement — a makerspace should not allow equipment reservations without a signed waiver.

Minimum slice:
- `Waiver` model — `id`, `version`, `content` (or URL), `effectiveDate`
- `WaiverSignature` model — `userId`, `waiverId`, `signedAt`
- `WaiverService` — `hasSignedCurrentWaiver(userId)`, `sign(userId, waiverId)`
- `WaiverController` — `GET /api/v1/waivers/current`, `POST /api/v1/waivers/{id}/sign`
- Hook in `ReservationService.create()` — reject if user hasn't signed the current waiver version

### Then: 3.6.1 / 3.6.4 — Complete Stripe Billing

Outstanding items in the billing layer before Phase 3:
- `POST /api/billing/me/cancel` → `cancel_at_period_end=true`
- Invoice/checkout event handlers in `StripeEventService.dispatch` → create `PaymentRecord` rows
- `POST /api/admin/billing/refunds` → ADMIN only, record actor

### Then: Phase 3 — EventBridge Ingest

Once the domain layer is complete:
- Terraform: EventBridge partner bus, rules, SQS queues, DLQs, IAM roles
- Python consumers + shared layer (Lambda Powertools, partial batch failure)
- Reconciliation Lambda (reads `/api/internal/billing/cursor`)
- Expiry sweep Lambda (posts to `/api/internal/billing/sweep` hourly)

### Also consider: 3.1.2 / 3.1.3 — Infrastructure and CI/CD

Both are at 0–5%. A Dockerfile and a basic GitHub Actions workflow would make the project deployable and testable in CI, which is likely on the capstone rubric.
