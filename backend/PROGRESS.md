# Collaboratory Makerspace — Backend Progress

## Component Breakdown

| # | Component | % | Why |
|---|-----------|---|-----|
| **3.1.1** | Database Setup | **65%** | PostgreSQL configured. JPA entities: `User`, `Equipment`, `EquipmentReservation`, `RegistrationInvite`, `UserProfile`, `Address`, `PhoneNumber`. Soft-delete via `@SQLDelete`. Flyway migrations V1–V3 (initial schema → authority roles → user_roles join table). Missing: no seed data, schema versioning beyond V3. |
| **3.1.2** | Cloud Infrastructure | **5%** | No Dockerfile, no Kubernetes manifests, no cloud provider config. The only hint is a local PostgreSQL connection string. |
| **3.1.3** | CI/CD Pipeline | **0%** | No GitHub Actions workflows, no Jenkins, no pipeline config of any kind. |
| **3.2.1** | Authorization & Authentication | **85%** | Auth0/Okta SSO fully wired. Local in-person registration added: staff pre-registers a user via `POST /api/admin/registrations` (generates a `RegistrationInvite` with a secure token and emails a claim link), user claims with `POST /api/registrations/claim` (activates account, sets password hash). `AccountClaimService`, `AdminRegistrationService`, `InviteTokenService`, `EmailService`/`StubEmailService` all implemented. Gaps: no refresh token flow, no server-side logout. |
| **3.2.2** | User Roles | **90%** | `Role` enum has 7 values. User now carries a `Set<Role>` via `user_roles` join table (V3 migration). Role hierarchy DAG: ADMIN → STAFF → INSTRUCTOR (siblings). `RoleHierarchyConfig` published as a Spring Security `RoleHierarchy` bean — `hasRole('STAFF')` now implicitly passes for ADMIN. JWT carries a `roles` list claim. Last-admin guard updated for the roles set. Gap: no bulk-assignment tooling. |
| **3.2.3** | User Profiles | **65%** | `UserProfile`, `Address`, and `PhoneNumber` models added and decoupled from `User`. `/me` GET and PATCH endpoints exist. Missing: avatar/photo URL, preferences, profile-completeness tracking. |
| **3.3.1** | Role-based Access Control | **85%** | Per-module `SecurityFilterChain` configs, `@PreAuthorize` throughout controllers, `@EnableMethodSecurity` active. Role hierarchy (ADMIN → STAFF) enforced via `RoleHierarchyConfig` — `hasRole('STAFF')` simplified across all filter chains. Registration chain added (Order 2). Gap: `GUEST`/`RENTEE` roles still unreferenced in access rules. |
| **3.3.2** | Token System / OAuth2 | **85%** | JWT issuance and validation fully implemented. JWT now carries a `roles` list claim instead of single `role` string. HttpOnly cookie flow in place, `UserStateService` cache-backed state check on every request. Gap: no refresh token, no token revocation/blocklist. |
| **3.3.3** | API Security & Middleware | **60%** | `JwtAuthFilter` validates tokens on every request and checks soft-delete state. CSRF correctly disabled for stateless API. Gap: no rate limiting, no CORS config, no input sanitization beyond `@Valid` on a few DTOs. |
| **3.4.1** | Waiver Versioning | **0%** | No model, no table, no service, no controller. Completely absent. |
| **3.4.2** | Hard-block Enforcement | **0%** | Nothing exists to gate access based on waiver acceptance. |
| **3.5.1** | Subscription Status Engine | **0%** | No `Membership` or `Subscription` model. No status tracking logic exists. |
| **3.5.2** | Expiry Logic | **0%** | Not started. No scheduler, no expiry dates, no status transitions. |
| **3.6.1** | Stripe Payment Processing | **0%** | No Stripe SDK in `pom.xml`, no payment models, no webhook handler. |
| **3.6.2** | Reconciliation | **0%** | Not started. |
| **3.6.3** | Retry Logic | **0%** | Not started. |
| **3.6.4** | Cancellation Policies | **0%** | Not started. |
| **3.7.1** | Booking Engine | **90%** | Fully implemented: `ReservationController` (create, my reservations, cancel, extend, admin all, by-equipment), `ReservationService` (overlap/conflict detection, status guards, ownership checks), `ReservationRepository` (JPQL overlap query with excludeId for extend), `ReservationStatus` enum (ACTIVE, CANCELLED, COMPLETED), `ReservationDTO`. Equipment availability enforced (MAINTENANCE/RETIRED blocked). `@EntityGraph` on list queries prevents N+1. Gap: no automated COMPLETED transition (no scheduler). |
| **3.7.2** | Capacity Rules | **50%** | Overlap detection fully implemented via `ReservationRepository.findOverlapping` JPQL query (checks `existingStart < newEnd AND existingEnd > newStart`, excludes self for extend). Gap: no max-concurrent-users-per-equipment rule. |
| **3.7.3** | Maintenance Fallback | **50%** | `ReservationService.create` blocks new reservations when equipment status is MAINTENANCE or RETIRED. Staff can update status via `PATCH /api/equipment/{id}/status`. Gap: no automatic cancellation of existing reservations when equipment goes into maintenance. |
| **3.8.1** | Email Triggers | **10%** | `EmailService` interface and `StubEmailService` stub implemented (logs to console). Used for registration invite emails. No real mail provider (no Spring Mail, SendGrid) wired. |
| **3.8.2** | Alert Logic | **0%** | Not started. |
| **3.9.1** | Badge Integration | **0%** | No badge model, no hardware interface, no API surface. |
| **3.10** | Logging System | **20%** | `@Slf4j` used in `UserService`, `ReservationService`, `AdminRegistrationService` with `log.info` audit calls (role changes, soft deletes, reservation create/cancel/extend). Spring Security TRACE logging enabled in properties. No structured/centralized logging (no ELK, no log aggregator, no consistent audit trail). |

---

## What to Work on Next

### Recommended: 3.4.1 — Waiver Versioning

The booking engine is now implemented (3.7.1 → 90%). The natural next step is waiver enforcement: a makerspace should not allow equipment reservations without a signed waiver. This component has no external dependencies — just a new model, a migration, and a service — making it fast to implement.

Minimum slice:
- **`Waiver` model** — `id`, `version`, `content` (or URL), `effectiveDate`
- **`WaiverSignature` model** — `userId`, `waiverId`, `signedAt`
- **`WaiverService`** — `hasSignedCurrentWaiver(userId)`, `sign(userId, waiverId)`
- **`WaiverController`** — `GET /api/waivers/current`, `POST /api/waivers/{id}/sign`
- **Hard-block hook** in `ReservationService.create()` — reject if user hasn't signed the current waiver version

Once waiver enforcement is in place, **3.5.1 (Subscription Status Engine)** becomes the next logical priority — membership status is the other gate that should block reservations for expired members.
