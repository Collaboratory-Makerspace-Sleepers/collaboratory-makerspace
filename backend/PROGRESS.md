# Collaboratory Makerspace — Backend Progress

## Component Breakdown

| # | Component | % | Why |
|---|-----------|---|-----|
| **3.1.1** | Database Setup | **60%** | PostgreSQL configured, JPA entities exist (`User`, `Equipment`, `EquipmentReservation`), soft-delete via `@SQLDelete` is implemented. Missing: no Flyway/Liquibase migrations, schema versioning, or seed data. |
| **3.1.2** | Cloud Infrastructure | **5%** | No Dockerfile, no Kubernetes manifests, no cloud provider config. The only hint is a local PostgreSQL connection string. |
| **3.1.3** | CI/CD Pipeline | **0%** | No GitHub Actions workflows, no Jenkins, no pipeline config of any kind. |
| **3.2.1** | Authorization & Authentication | **80%** | Auth0/Okta SSO via OIDC is wired up, `OAuth2SuccessHandler` provisions users on first login, `JwtService` generates/validates tokens, `TokenController` exchanges the HttpOnly cookie for a JWT. Gaps: no refresh token flow, no logout endpoint that clears the cookie server-side. |
| **3.2.2** | User Roles | **70%** | `Role` enum has 7 roles (MEMBER, STAFF, ADMIN, GUEST, INSTRUCTOR, RENTEE, STUDENT). Admin can update roles via `PATCH /api/users/{id}/role`, last-admin guard is enforced. Gap: no role-hierarchy definition and no bulk-assignment tooling. |
| **3.2.3** | User Profiles | **55%** | `/me` GET and PATCH endpoints exist, `UpdateProfileRequest` DTO validates firstName/lastName. Missing: phone number, avatar/photo URL, preferences, and any profile-completeness tracking. |
| **3.3.1** | Role-based Access Control | **75%** | Per-module `SecurityFilterChain` configs (`UserSecurityConfig`, `ReservationSecurityConfig`), `@PreAuthorize` annotations throughout controllers, `@EnableMethodSecurity` active. Gap: no permission matrix for all planned routes, `GUEST`/`INSTRUCTOR`/`RENTEE` roles are defined but never referenced in any access rules. |
| **3.3.2** | Token System / OAuth2 | **80%** | JWT issuance and validation fully implemented, HttpOnly cookie flow in place, `UserStateService` cache-backed state check on every request. Gap: no refresh token, no token revocation/blocklist. |
| **3.3.3** | API Security & Middleware | **60%** | `JwtAuthFilter` validates tokens on every request and checks soft-delete state. CSRF correctly disabled for stateless API. Gap: no rate limiting, no CORS config, no input sanitization beyond `@Valid` on a few DTOs. |
| **3.4.1** | Waiver Versioning | **0%** | No model, no table, no service, no controller. Completely absent. |
| **3.4.2** | Hard-block Enforcement | **0%** | Nothing exists to gate access based on waiver acceptance. |
| **3.5.1** | Subscription Status Engine | **0%** | No `Membership` or `Subscription` model. No status tracking logic exists. |
| **3.5.2** | Expiry Logic | **0%** | Not started. No scheduler, no expiry dates, no status transitions. |
| **3.6.1** | Stripe Payment Processing | **0%** | No Stripe SDK in `pom.xml`, no payment models, no webhook handler. |
| **3.6.2** | Reconciliation | **0%** | Not started. |
| **3.6.3** | Retry Logic | **0%** | Not started. |
| **3.6.4** | Cancellation Policies | **0%** | Not started. |
| **3.7.1** | Booking Engine | **20%** | `EquipmentReservation` model (user, equipment, start/end time) and `ReservationSecurityConfig` with all routes defined exist. No `ReservationController`, `ReservationService`, or `ReservationRepository` — the skeleton is ready but the implementation layer is missing. |
| **3.7.2** | Capacity Rules | **0%** | No conflict detection, no max-concurrent logic. |
| **3.7.3** | Maintenance Fallback | **15%** | `EquipmentStatus` enum exists and staff can `PATCH /api/equipment/{id}/status`. No automatic fallback when equipment goes into maintenance, no reservation-blocking logic. |
| **3.8.1** | Email Triggers | **0%** | No Spring Mail, SendGrid, or any email dependency in `pom.xml`. |
| **3.8.2** | Alert Logic | **0%** | Not started. |
| **3.9.1** | Badge Integration | **0%** | No badge model, no hardware interface, no API surface. |
| **3.10** | Logging System | **15%** | `@Slf4j` used in `UserService` with a few `log.info` audit calls (role changes, soft deletes). Spring Security TRACE logging enabled in properties. No structured/centralized logging (no ELK, no log aggregator, no consistent audit trail). |

---

## What to Work on Next

### Recommended: 3.7.1 — Booking Engine

This is the highest-value, lowest-friction next step. The hard parts are already done:

- The **`EquipmentReservation` model** is defined with the right relationships
- The **`ReservationSecurityConfig`** has all the route-level permissions mapped out (`POST /api/reservations`, `GET /api/reservations/me/**`, `PATCH /{id}/extend`, `PATCH /{id}/cancel`)
- The **`Equipment` model and service** are complete and tested

What remains is purely implementation: add a `ReservationRepository`, a `ReservationService` (with overlap/conflict detection), and a `ReservationController`. This delivers the core makerspace workflow — users booking equipment — and directly unlocks progress on **3.7.2 (Capacity Rules)** and **3.7.3 (Maintenance Fallback)** as natural extensions of the same service.

Once the booking engine is in place, **3.4.1 (Waiver Versioning)** becomes the next priority — a makerspace can't safely let users book equipment without a signed waiver, and this component also has no external dependencies (just a new model + service), making it fast to implement.
