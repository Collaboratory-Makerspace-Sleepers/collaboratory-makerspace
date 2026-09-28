# Backend Application State

_Last updated: 2026-09-27_

---

## Overview

Spring Boot 3.3.9 / Java 21 REST API for the Collaboratory Makerspace. The application manages users, equipment, equipment reservations, and memberships. Authentication is handled via Auth0 (OIDC/OAuth2) and OTP (email code), both issuing JWTs as HttpOnly cookies, which are then exchanged for in-memory Bearer tokens by the frontend.

Access control is **permission-based RBAC** — there is no role hierarchy. Each role carries an explicit set of permission codes stored in the database. Security filter chains and `@PreAuthorize` expressions reference `Permission` constants; which roles carry a permission is a database concern managed through the admin API at runtime.

**Stripe integration (Phases 1–2 complete):** A user can buy a membership in a Stripe sandbox and the local database reflects it correctly, end to end, with no AWS involved. Reservation creation is gated on an active membership (HTTP 402 if none).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.3.9 |
| Language | Java 21 |
| ORM | Spring Data JPA / Hibernate |
| Database (prod) | PostgreSQL |
| Database (test) | H2 in-memory |
| Auth provider | Auth0 via Okta Spring Boot Starter 3.0.7 |
| JWT | jjwt 0.12.6 |
| Cache | Caffeine (via spring-boot-starter-cache) |
| Migrations | Flyway (V1, V3–V6) |
| Payments | stripe-java (33.x) |
| Boilerplate | Lombok 1.18.44 |
| Build | Maven |

---

## Package Structure

```
com.makerspace.backend
├── Application.java                    Entry point (@SpringBootApplication, @EnableJpaAuditing)
├── config/
│   ├── CacheConfig.java                Caffeine cache manager; two caches: "userState" and "userPermissions", both 30s TTL / 10k max
│   ├── SecurityConfig.java             Unified JWT filter chain (Orders 1–8 + 100); all rules use Permission constants
│   ├── StripeConfig.java               Registers a single StripeClient bean from ${STRIPE_SECRET_KEY}
│   └── security/
│       ├── InternalAuthFilter.java     Validates X-Internal-Api-Key header (constant-time compare); sets SCOPE_INTERNAL authority
│       ├── OAuthProfile.java           Record: email, firstName, lastName, subject (OIDC → app DTO)
│       ├── UserPrincipal.java          Record: userId, auth0Subject, email, authorities — set on SecurityContext by JwtAuthFilter
│       └── UserSecurity.java           @Component("userSecurity") — isSelf(id, auth) and getUserId(auth) for SpEL @PreAuthorize
├── controller/
│   ├── dto/
│   │   ├── AppRoleDTO.java             record: code, description, isSystem, permissions — static from(AppRole)
│   │   ├── CheckoutRequest.java        record: planCode
│   │   ├── CheckoutSessionResponse.java record: sessionUrl
│   │   ├── ClaimRequest.java           record: token, password
│   │   ├── CreateReservationRequest.java record: equipmentId, startTime (@Future), endTime (@Future)
│   │   ├── CreateRoleRequest.java      record: code, description, permissions (optional initial set)
│   │   ├── CursorResponse.java         record: lastReceivedAt (ZonedDateTime, nullable)
│   │   ├── ExtendReservationRequest.java record: newEndTime (@Future)
│   │   ├── MembershipDTO.java          record: status, planCode, currentPeriodEnd, cancelAtPeriodEnd, gracePeriodEndsAt
│   │   ├── PaymentRecordDTO.java       record: kind, status, amountCents, currency, description, occurredAt
│   │   ├── PortalSessionResponse.java  record: portalUrl
│   │   ├── PreRegisterRequest.java     record: email, firstName, lastName, roles
│   │   ├── PreRegisterResponse.java    record: inviteId, email, claimToken, expiresAt
│   │   ├── ReservationDTO.java         record + static from(EquipmentReservation)
│   │   ├── StripeEventCommand.java     record: eventId, type, apiVersion, livemode, occurredAt, source, payload (raw JSON)
│   │   ├── SubscriptionSnapshot.java   record: user, subscriptionId, stripePriceId, status, periodStart, periodEnd, cancelAtPeriodEnd, canceledAt, updatedAt
│   │   ├── SweepRequest.java           record: asOf (ZonedDateTime, nullable)
│   │   ├── UpdatePermissionsRequest.java record: permissions (Set<String>)
│   │   ├── UpdateProfileRequest.java   record: firstName, lastName (@NotBlank, @Size(max=100))
│   │   ├── UpdateRoleRequest.java      record: role (@NotNull)
│   │   ├── UserAdminDTO.java           record: id, email, firstName, lastName, roles, createdAt, deletedAt
│   │   └── UserDTO.java                record: id, email, firstName, lastName, roles
│   ├── AdminRegistrationController.java  POST /api/v1/admin/registrations (REGISTER_USERS permission)
│   ├── BillingController.java          4 endpoints: checkout-session, portal-session, /me/subscription, /me/payments
│   ├── DevStripeWebhookController.java  POST /api/dev/stripe/webhook (@Profile("dev") only — stripe listen target)
│   ├── EquipmentController.java        Full CRUD for equipment
│   ├── InternalBillingController.java  3 endpoints: POST /events, POST /sweep, GET /cursor (Lambda-facing)
│   ├── OtpController.java              POST /api/v1/auth/otp/send, POST /api/v1/auth/otp/verify
│   ├── RegistrationClaimController.java  POST /api/v1/registrations/claim (authenticated incl. ROLE_PENDING)
│   ├── ReservationController.java      7 endpoints: create, /me, /me/{id}, cancel, extend, /admin/all, /admin/equipment/{id}
│   ├── RoleController.java             Role + permission admin API (MANAGE_ROLES required); see endpoint matrix
│   ├── TokenController.java            Cookie → Bearer token exchange (/api/v1/auth/token)
│   └── UserController.java             7 endpoints (see API section)
├── model/
│   ├── AccountStatus.java              Enum: PRE_REGISTERED, ACTIVE, DELETED
│   ├── Address.java                    id, street, city, state, zip, country
│   ├── AppRole.java                    Entity mapped to roles table: code (PK), description, isSystem, permissions (Set<String> via role_permissions join table)
│   ├── Equipment.java                  id, name, description, category, imageUrl, status, createdAt
│   ├── EquipmentReservation.java       id, user (ManyToOne lazy), userId (read-only col), equipment (ManyToOne lazy), startTime, endTime, status, cancelledAt, createdAt
│   ├── EquipmentStatus.java            Enum: AVAILABLE, IN_USE, MAINTENANCE, RETIRED
│   ├── EventOutcome.java               Enum: PROCESSED, DUPLICATE, SKIPPED
│   ├── EventProcessingStatus.java      Enum: RECEIVED, PROCESSED, FAILED, SKIPPED
│   ├── Membership.java                 id, user, plan, stripeSubscriptionId, status, currentPeriodStart, currentPeriodEnd, cancelAtPeriodEnd, canceledAt, gracePeriodEndsAt, stripeUpdatedAt, createdAt, updatedAt
│   ├── MembershipPlan.java             id, code, displayName, stripePriceId, stripeProductId, billingInterval, amountCents, currency, grantsRole, active, createdAt
│   ├── MembershipStatus.java           Enum: NONE, ACTIVE, TRIALING, PAST_DUE, GRACE, CANCELED, INCOMPLETE, INCOMPLETE_EXPIRED, EXPIRED, UNPAID, PAUSED. Constants: BOOKING_STATUSES, INDEX_STATUSES
│   ├── PaymentKind.java                Enum: MEMBERSHIP, DAY_PASS, CLASS, RENTAL, REFUND
│   ├── PaymentRecord.java              id, userId, membershipId, reservationId, stripePaymentIntentId, stripeInvoiceId, stripeChargeId, kind, amountCents, currency, status, description, occurredAt, createdAt
│   ├── PaymentStatus.java              Enum: PENDING, SUCCEEDED, FAILED, REFUNDED
│   ├── Permission.java                 Constants class — well-known permission code strings
│   ├── PhoneNumber.java                id, number, type
│   ├── RegistrationInvite.java         id, email, token (hashed), expiresAt, claimedAt, createdByUserId
│   ├── ReservationStatus.java          Enum: ACTIVE, CANCELLED, COMPLETED
│   ├── StripeEventLog.java             id (event_id PK), eventType, apiVersion, livemode, receivedAt, processedAt, status, failureReason, attemptCount, source
│   ├── User.java                       id, email, emailDigest, auth0Subject, stripeCustomerId, accountStatus, profile, Set<AppRole> roles, createdAt, deletedAt; effectivePermissions() helper
│   ├── UserProfile.java                id, userId, bio, address, phoneNumbers
│   └── UserResolution.java             Sealed interface: Active(user) | Pending(user) | Deleted(id, deletedOn) | NotFound(profile)
├── repository/
│   ├── AppRoleRepository.java          findAll, existsById, isRoleInUse (native — checks user_roles)
│   ├── EquipmentRepository.java        findByStatus, findByCategory, findByNameContainingIgnoreCase
│   ├── MembershipPlanRepository.java   findByCode, findByStripePriceId
│   ├── MembershipRepository.java       findByUserId, findLiveByUserIdForUpdate (@Lock PESSIMISTIC_WRITE), findByStripeSubscriptionId, findByStatusAndGracePeriodEndsAtLessThanEqual
│   ├── PaymentRecordRepository.java    findByUserIdOrderByOccurredAtDesc
│   ├── PermissionRepository.java       findAllCodes → Set<String>
│   ├── RegistrationInviteRepository.java  findByToken, findByEmail, existsByEmailAndClaimedAtIsNull
│   ├── ReservationRepository.java      findByUserIdOrderByStartTimeDesc, findAllByOrderByStartTimeDesc, findByEquipmentIdOrderByStartTimeDesc, findOverlapping
│   ├── StripeEventLogRepository.java   findMaxReceivedAt, markProcessed, markSkipped (native updates)
│   └── UserRepository.java             findByEmail, findByEmailIncludingDeleted, findByIdIncludingDeleted, findByIdForUpdate, findByStripeCustomerId, countByRole, existsByEmailIncludingDeleted
├── security/
│   ├── JwtAuthFilter.java              Per-request JWT validation; loads effective permissions from UserPermissionService (cached); sets UserPrincipal on SecurityContext; PENDING → ROLE_PENDING only; DELETED → 403; NOT_FOUND → 401
│   └── OAuth2SuccessHandler.java       OAuth2 success → provision user → set JWT cookie → redirect; Deleted → /account-closed
└── services/
    ├── AccountClaimService.java        claim(token, password) → activates PRE_REGISTERED user; validates token, sets credentials, transitions AccountStatus
    ├── AdminRegistrationService.java   preRegister(req, adminId) → creates RegistrationInvite, sends email; existsById guard
    ├── BillingService.java             createCheckoutSession (lazy Stripe Customer creation with pessimistic lock + idempotency key), createPortalSession, getSubscription, getPayments
    ├── EmailService.java               Interface: sendInvite(email, token), sendOtp(email, code)
    ├── EquipmentService.java           findAll, findById, findByStatus, findByCategory, search, create, update, updateStatus, delete
    ├── InviteTokenService.java         generate(), hash(), verify()
    ├── JwtService.java                 generateToken (roles list claim), parseToken, isValid
    ├── MembershipService.java          hasActiveMembership, statusOf, currentMembership, upsertFromSubscription (out-of-order guard), applyGracePeriodExpiry (sweep target)
    ├── OtpService.java                 sendCode(email) — generates 6-digit code, rate-limited 60s, emails via EmailService; verifyCode(email, code) — validates and burns
    ├── RoleService.java                listAll, getByCode, createRole, updateDescription, setPermissions (validates against catalog, evicts userPermissions cache), deleteRole (blocks system roles + in-use roles)
    ├── SmtpEmailService.java           Production EmailService impl — sends via SMTP/SES
    ├── StubEmailService.java           Dev/test EmailService impl — logs to console
    ├── StripeEventLogWriter.java       tryInsert (native insert returning boolean — false on PK violation, avoids poisoning the persistence context)
    ├── StripeEventService.java         handle(cmd) with insert-first idempotency guard; dispatch routes subscription events to handleSubscription; invoice/checkout/charge types marked PROCESSED (Phase 3 handlers pending)
    ├── UserPermissionService.java      getEffectivePermissions(email) → union of all role permissions, @Cacheable("userPermissions"); evict(email), evictAll()
    ├── UserService.java                findUser, findById, findAllActive(Pageable), resolve, provision, updateProfile, updateRole, softDelete (@CacheEvict), softDeleteUser (guard + evict), restore, countActiveAdmins
    └── UserStateService.java           stateOf(email) → ACTIVE|PENDING|DELETED|NOT_FOUND; autoClaimByEmail(userId, auth0Subject) for OTP/OAuth-initiated claim; evict(email)
```

---

## Flyway Migrations

| Version | Description |
|---|---|
| V1 | Baseline schema — users, roles catalog, user_roles, equipment, reservations, registration_invites, user_profiles |
| V3 | user_roles join table (idempotent; legacy single-role column backfill + drop) |
| V4 | Billing core — `users.stripe_customer_id`, `stripe_event_log`, `membership_plan`, `membership`, `payment_record` |
| V5 | Permission-based RBAC — `permissions` catalog, `role_permissions` join table, `roles.is_system` column, seed data |
| V6 | Seed `membership_plan` — MONTHLY, ANNUAL, STUDENT, DAY_PASS rows with placeholder `stripe_price_id` values |

---

## Authentication & Identity Flow

### OAuth2 / OIDC (Auth0)

```
1. User visits /oauth2/authorization/okta  →  Auth0 login
2. Auth0 redirects to /login/oauth2/code/okta
3. OAuth2SuccessHandler fires:
   a. Builds OAuthProfile from OidcUser (email, firstName, lastName, subject)
   b. Calls UserService.resolve(profile) → UserResolution (Active | Pending | NotFound | Deleted)
      - Active  → reuse existing user
      - Pending → autoClaimByEmail (bind auth0Subject, activate)
      - NotFound → provision (INSERT)
      - Deleted → redirect to /account-closed, no JWT issued
   c. Generates JWT via JwtService.generateToken(user, subject)
   d. Sets JWT in HttpOnly Secure cookie (access_token, maxAge=1h, SameSite=Lax)
   e. Redirects to {frontend}/oauth-callback
4. Frontend calls GET /api/v1/auth/token → returns { "access_token": "<jwt>" }
5. Frontend stores token in memory; sends as Authorization: Bearer <jwt>
```

### OTP (Email Code)

```
1. POST /api/v1/auth/otp/send { "email": "..." }
   - OtpService generates a 6-digit code, rate-limited to one send per 60s per email
   - Code emailed via EmailService; returns 200
2. POST /api/v1/auth/otp/verify { "email": "...", "code": "..." }
   - OtpService verifies and burns the code
   - UserService.resolve(OAuthProfile with subject="otp:<email>")
     - Active    → reuse
     - Pending   → autoClaimByEmail (activate)
     - NotFound  → provision as GUEST
     - Deleted   → 403 account_closed
   - JWT issued; HttpOnly cookie set; token returned in body
```

### Bearer Token Request Flow (JwtAuthFilter)

```
Every authenticated request:
a. Extracts Bearer token; passes through if absent or invalid
b. Validates JWT signature + expiry via JwtService
c. Extracts email; calls UserStateService.stateOf(email) [cached 30s]
d. ACTIVE  → loads effective permissions via UserPermissionService [cached 30s]
             builds UserPrincipal(userId, auth0Subject, email, authorities)
             authorities = SimpleGrantedAuthority per permission code
             sets UsernamePasswordAuthenticationToken on SecurityContext, continues
e. PENDING → builds UserPrincipal with single ROLE_PENDING authority only
f. DELETED → 403 { "error": "account_closed" }, chain halted
g. NOT_FOUND → 401 { "error": "unknown_user" }, chain halted
```

### Identity Principal (`UserPrincipal`)

- `userId` (Long) — database PK, used by `currentUserId()` and `isSelf()` ownership checks
- `auth0Subject` (String) — stable Auth0/OTP subject (e.g. `google-oauth2|123…` or `otp:user@example.com`)
- `email` (String) — for permission lookup
- `authorities` (Collection) — `SimpleGrantedAuthority` per permission code for ACTIVE users; `[ROLE_PENDING]` for PRE_REGISTERED

`UserSecurity.isSelf(id, auth)` and `getUserId(auth)` fail closed on non-`UserPrincipal` principals.

---

## Access Control Architecture

### Security Filter Chains (in priority order)

| Order | Chain | Matcher | Rules |
|---|---|---|---|
| 1 | `authChain` | `/api/v1/auth/**`, `/oauth2/**`, `/login/**` | `/api/v1/auth/token` permitAll; OTP send/verify permitAll; `/api/v1/auth/me` authenticated; OAuth2 login. Session IF_REQUIRED (OAuth2 state nonce). |
| 2 | `registrationChain` | `/api/v1/admin/registrations/**`, `/api/v1/registrations/**` | POST admin/registrations: `REGISTER_USERS`; POST registrations/claim: authenticated (ROLE_PENDING sufficient) |
| 3 | `billingChain` | `/api/v1/billing/**` | All requests: authenticated |
| 4 | `reservationChain` | `/api/v1/reservations/**` | POST create: authenticated; GET me/**: authenticated; PATCH cancel: authenticated; PATCH extend: `MANAGE_RESERVATIONS`; admin/all: `VIEW_ALL_RESERVATIONS`; admin/**: `VIEW_ALL_RESERVATIONS` |
| 5 | `userChain` | `/api/v1/users/**` | GET list: `MANAGE_USERS`; PATCH role: `MANAGE_ROLES`; POST restore: `MANAGE_USERS`; any other: authenticated. Returns 401 for unauthenticated. |
| 6 | `internalChain` | `/api/internal/**` | `InternalAuthFilter` runs before `JwtAuthFilter`; all requests: `SCOPE_INTERNAL` |
| 7 | `roleAdminChain` | `/api/v1/admin/roles/**`, `/api/v1/admin/permissions/**` | All requests: `MANAGE_ROLES` |
| 8 | `devWebhookChain` | `/api/dev/**` | All requests: permitAll (dev profile only, Stripe listen target) |
| 100 | `fallbackChain` | `/**` | `/actuator/health` permitAll; everything else authenticated |

### Permission Model

Seeded default assignments (from V5 migration):

| Permission | ADMIN | STAFF | INSTRUCTOR |
|---|---|---|---|
| MANAGE_USERS | ✓ | ✓ | |
| MANAGE_ROLES | ✓ | | |
| MANAGE_EQUIPMENT | ✓ | ✓ | |
| VIEW_ALL_RESERVATIONS | ✓ | ✓ | ✓ |
| MANAGE_RESERVATIONS | ✓ | ✓ | |
| REGISTER_USERS | ✓ | ✓ | |

### Endpoint Authorization Matrix

#### Auth — `/api/v1/auth`

| Method | Path | Rule |
|---|---|---|
| GET | `/api/v1/auth/token` | Public |
| POST | `/api/v1/auth/otp/send` | Public |
| POST | `/api/v1/auth/otp/verify` | Public |

#### Billing — `/api/v1/billing`

| Method | Path | Rule |
|---|---|---|
| POST | `/api/v1/billing/checkout-session` | Authenticated |
| POST | `/api/v1/billing/portal-session` | Authenticated |
| GET | `/api/v1/billing/me/subscription` | Authenticated |
| GET | `/api/v1/billing/me/payments` | Authenticated |

#### Equipment — `/api/v1/equipment`

| Method | Path | Rule |
|---|---|---|
| GET | `/api/v1/equipment` | Authenticated |
| GET | `/api/v1/equipment/{id}` | Authenticated |
| GET | `/api/v1/equipment/status/{status}` | Authenticated |
| GET | `/api/v1/equipment/category/{category}` | Authenticated |
| GET | `/api/v1/equipment/search?q=` | Authenticated |
| POST | `/api/v1/equipment` | `MANAGE_EQUIPMENT` |
| PUT | `/api/v1/equipment/{id}` | `MANAGE_EQUIPMENT` |
| PATCH | `/api/v1/equipment/{id}/status` | `MANAGE_EQUIPMENT` |
| DELETE | `/api/v1/equipment/{id}` | `MANAGE_EQUIPMENT` |

#### Users — `/api/v1/users`

| Method | Path | Rule | Guard |
|---|---|---|---|
| GET | `/api/v1/users/me` | Authenticated | — |
| PATCH | `/api/v1/users/me` | Authenticated | — |
| GET | `/api/v1/users` | `MANAGE_USERS` | — |
| GET | `/api/v1/users/{id}` | `MANAGE_USERS` or self | `@userSecurity.isSelf(#id, authentication)` |
| PATCH | `/api/v1/users/{id}/role` | `MANAGE_ROLES` | Self-change rejected (400); last-admin guard |
| DELETE | `/api/v1/users/{id}` | `MANAGE_USERS` or self | `@userSecurity.isSelf(#id, authentication)`; last-admin guard |
| POST | `/api/v1/users/{id}/restore` | `MANAGE_USERS` | — |

#### Registration — `/api/v1/admin/registrations`, `/api/v1/registrations`

| Method | Path | Rule |
|---|---|---|
| POST | `/api/v1/admin/registrations` | `REGISTER_USERS` |
| POST | `/api/v1/registrations/claim` | Authenticated (incl. ROLE_PENDING) |

#### Reservations — `/api/v1/reservations`

| Method | Path | Rule | Notes |
|---|---|---|---|
| POST | `/api/v1/reservations` | Authenticated | **402** if no active membership; conflict-checked; MAINTENANCE/RETIRED equipment blocked |
| GET | `/api/v1/reservations/me` | Authenticated | User's own reservations, ordered by startTime desc |
| GET | `/api/v1/reservations/me/{id}` | Authenticated | Own reservation; `VIEW_ALL_RESERVATIONS` can view any |
| PATCH | `/api/v1/reservations/{id}/cancel` | Authenticated | Ownership enforced; `MANAGE_RESERVATIONS` can cancel any |
| PATCH | `/api/v1/reservations/{id}/extend` | `MANAGE_RESERVATIONS` | Conflict-checked for extension window |
| GET | `/api/v1/reservations/admin/all` | `VIEW_ALL_RESERVATIONS` | Paginated (default 50/page) |
| GET | `/api/v1/reservations/admin/equipment/{id}` | `VIEW_ALL_RESERVATIONS` | All reservations for a piece of equipment |

#### Roles & Permissions — `/api/v1/admin/roles`, `/api/v1/admin/permissions`

| Method | Path | Rule |
|---|---|---|
| GET | `/api/v1/admin/permissions` | `MANAGE_ROLES` |
| GET | `/api/v1/admin/roles` | `MANAGE_ROLES` |
| GET | `/api/v1/admin/roles/{code}` | `MANAGE_ROLES` |
| POST | `/api/v1/admin/roles` | `MANAGE_ROLES` |
| PUT | `/api/v1/admin/roles/{code}/permissions` | `MANAGE_ROLES` |
| DELETE | `/api/v1/admin/roles/{code}` | `MANAGE_ROLES` — 409 if system role or in use |

#### Internal (Lambda-facing) — `/api/internal/billing`

| Method | Path | Rule | Notes |
|---|---|---|---|
| POST | `/api/internal/billing/events` | `SCOPE_INTERNAL` | 200=processed/skipped; 409=duplicate; 422=unprocessable; 5xx=retry |
| POST | `/api/internal/billing/sweep` | `SCOPE_INTERNAL` | Fires grace-period expiry side effects |
| GET | `/api/internal/billing/cursor` | `SCOPE_INTERNAL` | Returns `{ lastReceivedAt }` for reconciliation Lambda |

---

## Billing / Stripe Integration

### Architecture (Phases 1–2 complete)

- **Outbound (user-facing):** `BillingController` + `BillingService` + `stripe-java` handle checkout session creation, customer portal, and subscription/payment reads.
- **Inbound (event-driven, Phase 3 pending):** `InternalBillingController` + `StripeEventService` handle Stripe event ingestion. Currently reachable via the dev webhook endpoint (`DevStripeWebhookController`, `@Profile("dev")`) using `stripe listen`. Production path via EventBridge → SQS → Lambda → this API is Phase 3.
- **Membership domain:** `MembershipService` owns all status derivation and booking eligibility. Reservation gating reads `MembershipService`, not the role set.

### Lazy Stripe Customer creation

`BillingService.createCheckoutSession` acquires a pessimistic write lock on the `User` row and uses a Stripe idempotency key (`cust-create-{userId}`) to prevent duplicate Customer creation under concurrent requests.

### Idempotency guard

`StripeEventService.handle` calls `StripeEventLogWriter.tryInsert` (native SQL, catches PK violation) before any domain write. PK collision → `DUPLICATE` → HTTP 409 → Lambda deletes SQS message without retry. The insert and domain write are in the same `@Transactional` boundary; a rollback also rolls back the log row so the retry is clean.

### Out-of-order guard

`MembershipService.upsertFromSubscription` compares `snapshot.updatedAt()` against `membership.stripeUpdatedAt`. A stale event (older resource timestamp) is ignored without error.

### Membership status model

| Local | Derived from | Can book? |
|---|---|---|
| `TRIALING` | Stripe `trialing` | Yes |
| `ACTIVE` | Stripe `active` | Yes |
| `PAST_DUE` | Stripe `past_due` | Yes (configurable: `app.billing.past-due-retains-access=true`) |
| `GRACE` | `canceled`/`unpaid` + within grace window | No |
| `CANCELED` | grace period expired | No |
| `INCOMPLETE` | `incomplete`, `incomplete_expired` | No |
| `PAUSED` | `paused` | No |
| `NONE` | No membership row | No |

---

## Soft-Delete & Account Closure

- `@SQLDelete` on `User` converts `DELETE` to `UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?`
- `@SQLRestriction("deleted_at IS NULL")` filters soft-deleted rows from all standard JPA queries
- `UserService.softDeleteUser(id, actorId)` — last-admin guard, logs actor, deletes, evicts both caches
- Self-delete clears the `access_token` HttpOnly cookie in the response

---

## Caching

Two Caffeine caches, both 30s TTL / 10,000 entries max:

| Cache | Populated by | Evicted by |
|---|---|---|
| `userState` | `UserStateService.stateOf(email)` | `UserService.softDeleteUser`, `UserService.restore` |
| `userPermissions` | `UserPermissionService.getEffectivePermissions(email)` | Per-user on role change; wholesale on `RoleService.setPermissions` |

---

## Tests

119 tests across 11 files. All passing.

| File | Count | Type | Covers |
|---|---|---|---|
| `BackendApplicationTests.java` | 1 | Spring context load | Application starts |
| `JwtServiceTest.java` | 6 | Unit | generateToken, parseClaims, invalid/tampered tokens |
| `UserRepositoryTest.java` | 8 | Integration (@DataJpaTest, H2) | @SQLDelete, @SQLRestriction, re-insert constraint |
| `UserServiceTest.java` | 19 | Unit (Mockito) | findUser, provision, updateProfile, updateRole, restore, countActiveAdmins, findAllActive |
| `UserStateServiceTest.java` | 3 | Unit (Mockito) | stateOf → ACTIVE / DELETED / NOT_FOUND |
| `JwtAuthFilterTest.java` | 8 | Unit (Mockito + MockHttpServlet) | No token/invalid pass-through, active → UserPrincipal, deleted → 403, unknown → 401 |
| `UserControllerTest.java` | 21 | @WebMvcTest | All 7 user endpoints: auth/authz rules, input validation, ownership checks, guard conditions |
| `MembershipServiceTest.java` | 23 | Unit (Mockito) | hasActiveMembership, statusOf, upsertFromSubscription, applyGracePeriodExpiry, past-due flag, out-of-order guard |
| `InternalBillingControllerTest.java` | 15 | @WebMvcTest | No header → 401; wrong header → 401; new event → 200; duplicate → 409; malformed → 422; sweep; cursor |
| `ReservationServiceTest.java` | 3 | Unit (Mockito) | Membership gate (402), conflict detection, equipment status guard |
| `StripeEventServiceTest.java` | 12 | Unit (Mockito) | Duplicate event → DUPLICATE; dispatch routes; stale subscription ignored; rollback cleans log row |

---

## Known Issues / Incomplete Areas

### Functional gaps

- **No COMPLETED reservation transition** — `ReservationStatus.COMPLETED` exists but no scheduler transitions it.
- **No automatic maintenance cancellation** — existing reservations not cancelled when equipment goes to MAINTENANCE.
- **Email is partially stubbed** — `SmtpEmailService` is the prod impl but not fully wired to an SES/SMTP provider in all environments.
- **No waiver gating** — reservations require an active membership but not a signed waiver.
- **Stripe Phase 3 (EventBridge) not implemented** — inbound events currently only reach the app via `stripe listen` + `DevStripeWebhookController` (dev profile). Production event path (EventBridge → SQS → Lambda → `/api/internal/billing/events`) is planned Phase 3.
- **Invoice/checkout/charge/dispute event handlers stub** — `StripeEventService.dispatch` accepts these event types and returns PROCESSED but does not create `PaymentRecord` rows. Planned Phase 3.
- **No cancellation policy endpoint** — `cancel_at_period_end` and refund flows not yet wired.

### Security concerns

- **`application.properties` may contain plaintext secrets** — move all secrets to environment variables or Secrets Manager before any shared deployment.
- **No token revocation** — JWTs are stateless and valid until expiry (1h).
- **No refresh token** — after expiry, user must re-authenticate via OAuth2 or OTP.
- **Internal API uses shared secret** — `X-Internal-Api-Key` is constant-time compared and should be network-restricted (security group / ALB listener rule). SigV4 upgrade deferred.

### Open business decisions

1. `PAST_DUE` retains access? (default: yes, `app.billing.past-due-retains-access=true`)
2. Grace period length (default: 7 days, `app.billing.grace-period-days=7`)
3. Refund window and immediate vs. period-end revocation
4. Day passes — one-off Checkout or prepaid credit balance?
5. Proration on plan change
6. `STUDENT` plan eligibility verification
7. Internal API auth — shared secret (now) vs. SigV4 (later)

### Fixed

- ✅ Reservation system fully implemented
- ✅ Multi-role support via `user_roles` join table
- ✅ Permission-based RBAC — `RoleHierarchyConfig` removed
- ✅ All security filter chains unified into `SecurityConfig`
- ✅ Local in-person registration — invite flow, `AccountClaimService`, `InviteTokenService`
- ✅ `UserProfile`, `Address`, `PhoneNumber` decoupled from `User`
- ✅ `UserPrincipal` fully wired
- ✅ Last-admin guard on soft-delete and role updates
- ✅ Billing schema groundwork — V4 migration
- ✅ Billing Java layer — `Membership`, `MembershipPlan`, `PaymentRecord`, `StripeEventLog` models + services
- ✅ Stripe integration (Phases 1–2) — checkout, portal, subscription sync, idempotency guard
- ✅ Reservation gating — 402 if no active membership
- ✅ OTP authentication flow — email code login via `/api/v1/auth/otp/send` + `/verify`
- ✅ `UserResolution.Pending` variant — handles pre-registered users in both OAuth2 and OTP flows
