# Backend Application State

_Last updated: 2026-09-06_

---

## Overview

Spring Boot 3.3.9 / Java 21 REST API for the Collaboratory Makerspace. The application manages users, equipment, and equipment reservations. Authentication is handled via Auth0 (OIDC/OAuth2) with JWTs issued as HttpOnly cookies after the OAuth callback, then exchanged for in-memory Bearer tokens by the frontend.

Access control is **permission-based RBAC** — there is no role hierarchy. Each role carries an explicit set of permission codes stored in the database. Security filter chains and `@PreAuthorize` expressions reference `Permission` constants; which roles carry a permission is a database concern managed through the admin API at runtime.

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
| Migrations | Flyway (V1–V5) |
| Boilerplate | Lombok 1.18.44 |
| Build | Maven |

---

## Package Structure

```
com.makerspace.backend
├── Application.java                    Entry point (@SpringBootApplication, @EnableJpaAuditing)
├── config/
│   ├── CacheConfig.java                Caffeine cache manager; two caches: "userState" and "userPermissions", both 30s TTL / 10k max
│   ├── SecurityConfig.java             Unified JWT filter chain (Orders 1–5 + 100); all rules use Permission constants
│   └── security/
│       ├── OAuthProfile.java           Record: email, firstName, lastName, subject (OIDC → app DTO)
│       ├── UserPrincipal.java          Record: userId, auth0Subject, email, authorities — set on SecurityContext by JwtAuthFilter
│       └── UserSecurity.java           @Component("userSecurity") — isSelf(id, auth) for SpEL @PreAuthorize
├── controller/
│   ├── dto/
│   │   ├── AppRoleDTO.java             record: code, description, isSystem, permissions — static from(AppRole)
│   │   ├── ClaimRequest.java           record: token, password
│   │   ├── CreateReservationRequest.java record: equipmentId, startTime (@Future), endTime (@Future)
│   │   ├── CreateRoleRequest.java      record: code, description, permissions (optional initial set)
│   │   ├── ExtendReservationRequest.java record: newEndTime (@Future)
│   │   ├── PreRegisterRequest.java     record: email, firstName, lastName, roles
│   │   ├── PreRegisterResponse.java    record: inviteId, email, claimToken, expiresAt
│   │   ├── ReservationDTO.java         record + static from(EquipmentReservation)
│   │   ├── UpdatePermissionsRequest.java record: permissions (Set<String>)
│   │   ├── UpdateProfileRequest.java   record: firstName, lastName (@NotBlank, @Size(max=100))
│   │   ├── UpdateRoleRequest.java      record: role (@NotNull)
│   │   ├── UserAdminDTO.java           record: id, email, firstName, lastName, roles, createdAt, deletedAt
│   │   └── UserDTO.java                record: id, email, firstName, lastName, roles
│   ├── AdminRegistrationController.java  POST /api/v1/admin/registrations (REGISTER_USERS permission)
│   ├── EquipmentController.java        Full CRUD for equipment
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
│   ├── Permission.java                 Constants class — well-known permission code strings (MANAGE_USERS, MANAGE_ROLES, MANAGE_EQUIPMENT, VIEW_ALL_RESERVATIONS, MANAGE_RESERVATIONS, REGISTER_USERS)
│   ├── PhoneNumber.java                id, number, type
│   ├── RegistrationInvite.java         id, email, token (hashed), expiresAt, claimedAt, createdByUserId
│   ├── ReservationStatus.java          Enum: ACTIVE, CANCELLED, COMPLETED
│   ├── User.java                       id, email, emailDigest, auth0Subject, accountStatus, profile, Set<AppRole> roles, createdAt, deletedAt; effectivePermissions() helper
│   ├── UserProfile.java                id, userId, bio, address, phoneNumbers
│   └── UserResolution.java             Sealed interface: Active(user) | Deleted(id, deletedOn) | NotFound(profile)
├── repository/
│   ├── AppRoleRepository.java          findAll, existsById, isRoleInUse (native — checks user_roles)
│   ├── EquipmentRepository.java        findByStatus, findByCategory, findByNameContainingIgnoreCase
│   ├── PermissionRepository.java       findAllCodes → Set<String>
│   ├── RegistrationInviteRepository.java  findByToken, findByEmail, existsByEmailAndClaimedAtIsNull
│   ├── ReservationRepository.java      findByUserIdOrderByStartTimeDesc (@EntityGraph), findAllByOrderByStartTimeDesc (@EntityGraph), findByEquipmentIdOrderByStartTimeDesc (@EntityGraph), findOverlapping (JPQL overlap query)
│   └── UserRepository.java             findByEmail, findByEmailIncludingDeleted, findByIdIncludingDeleted (native), findByIdForUpdate (pessimistic lock), countByRole, existsByEmailIncludingDeleted
├── security/
│   ├── JwtAuthFilter.java              Per-request JWT validation; loads effective permissions from UserPermissionService (cached); sets UserPrincipal on SecurityContext; PENDING → ROLE_PENDING only; DELETED → 403; NOT_FOUND → 401
│   └── OAuth2SuccessHandler.java       OAuth2 success → provision user → set JWT cookie → redirect; Deleted → /account-closed
└── services/
    ├── AccountClaimService.java        claim(token, password) → activates PRE_REGISTERED user; validates token, sets credentials, transitions AccountStatus
    ├── AdminRegistrationService.java   preRegister(req, adminId) → creates RegistrationInvite, sends email; existsById guard
    ├── EmailService.java               Interface: sendInvite(email, token)
    ├── EquipmentService.java           findAll, findById, findByStatus, findByCategory, search, create, update, updateStatus, delete
    ├── InviteTokenService.java         generate(), hash(), verify()
    ├── JwtService.java                 generateToken (roles list claim), parseToken, isValid
    ├── ReservationService.java         create (conflict + status check), findByUser, findById, findByIdForUser (ownership), extend (conflict check), cancel (ownership + status guard), findAll (paginated), findByEquipment
    ├── RoleService.java                listAll, getByCode, createRole, updateDescription, setPermissions (validates against catalog, evicts userPermissions cache), deleteRole (blocks system roles + in-use roles)
    ├── StubEmailService.java           EmailService impl — logs to console, used in dev/test
    ├── UserPermissionService.java      getEffectivePermissions(email) → union of all role permissions, @Cacheable("userPermissions"); evict(email), evictAll()
    ├── UserService.java                findUser, findById, findAllActive(Pageable), resolve, provision, updateProfile, updateRole, softDelete (@CacheEvict), softDeleteUser (guard + evict), restore, countActiveAdmins
    └── UserStateService.java           stateOf(email) → ACTIVE|PENDING|DELETED|NOT_FOUND; autoClaimByEmail(userId, auth0Subject) for OAuth-initiated claim; evict(email)
```

---

## Flyway Migrations

| Version | Description |
|---|---|
| V1 | Baseline schema — users, roles catalog, user_roles, equipment, reservations, registration_invites, user_profiles |
| V3 | user_roles join table (idempotent; legacy single-role column backfill + drop) |
| V4 | Billing core — `users.stripe_customer_id`, `stripe_event_log`, `membership_plan`, `membership`, `payment_record` |
| V5 | Permission-based RBAC — `permissions` catalog, `role_permissions` join table, `roles.is_system` column, seed data |

---

## Authentication & Identity Flow

```
1. User visits /oauth2/authorization/okta  →  Auth0 login
2. Auth0 redirects to /login/oauth2/code/okta
3. OAuth2SuccessHandler fires:
   a. Builds OAuthProfile from OidcUser (email, firstName from getGivenName(), lastName from getFamilyName())
   b. Calls UserService.resolve(profile) → returns UserResolution (Active|NotFound|Deleted)
      - Active → reuse existing user
      - NotFound → provision (INSERT)
      - Deleted → redirect to /account-closed, no JWT issued
   c. Generates JWT via JwtService.generateToken(user) [sub=userId, email, exp=1h]
   d. Sets JWT in HttpOnly Secure cookie (access_token, maxAge=1h, SameSite=Lax)
   e. Redirects to {frontend}/oauth-callback
4. Frontend calls GET /api/v1/auth/token
   a. TokenController reads cookie, validates JWT signature
   b. Calls UserStateService.stateOf(email) — returns 403 for DELETED, 401 for NOT_FOUND
   c. Returns { "access_token": "<jwt>" } in response body
5. Frontend stores token in memory; sends as Authorization: Bearer <jwt>
6. JwtAuthFilter (OncePerRequestFilter) runs on every authenticated request:
   a. Extracts Bearer token; passes through if absent or invalid
   b. Validates JWT signature + expiry via JwtService
   c. Extracts email; calls UserStateService.stateOf(email) [cached 30s]
   d. ACTIVE  → loads effective permissions via UserPermissionService.getEffectivePermissions(email) [cached 30s]
               builds UserPrincipal(userId, auth0Subject, email, authorities)
               authorities = SimpleGrantedAuthority per permission code (e.g. "MANAGE_USERS")
               sets UsernamePasswordAuthenticationToken on SecurityContext, continues chain
   e. PENDING → builds UserPrincipal with single ROLE_PENDING authority only; continues chain
   f. DELETED → 403 { "error": "account_closed" }, chain halted
   g. NOT_FOUND → 401 { "error": "unknown_user" }, chain halted
```

### Identity Principal (`UserPrincipal`)

After JWT validation, `JwtAuthFilter` places a `UserPrincipal` record on the `SecurityContext`. It carries:
- `userId` (Long) — the database primary key, used by `currentUserId()` in controllers and `isSelf()` ownership checks
- `auth0Subject` (String) — the JWT `auth0Subject` claim (the Auth0 stable sub, e.g. `google-oauth2|123...`)
- `email` (String) — for reference and permission lookup
- `authorities` (Collection) — `SimpleGrantedAuthority` per effective permission code for ACTIVE users; `[ROLE_PENDING]` for PRE_REGISTERED users

`UserSecurity.isSelf(id, auth)` and `getUserId(auth)` read directly from `UserPrincipal`. If the principal is not a `UserPrincipal`, both fail closed — `isSelf` returns `false`, `getUserId` returns `null`.

---

## Access Control Architecture

### Security Filter Chains (in priority order)

All chains: CSRF disabled, stateless session, `JwtAuthFilter` added before `UsernamePasswordAuthenticationFilter`. All access rules reference `Permission` constants — no role names.

| Order | Chain | Matcher | Rules |
|---|---|---|---|
| 1 | `authChain` | `/api/v1/auth/**`, `/oauth2/**`, `/login/**` | `/api/v1/auth/token` permitAll; `/api/v1/auth/me` authenticated; others permitAll. Handles OAuth2 login. |
| 2 | `registrationChain` | `/api/v1/admin/registrations/**`, `/api/v1/registrations/**` | POST admin/registrations: `REGISTER_USERS`; POST registrations/claim: authenticated (ROLE_PENDING sufficient); others: `REGISTER_USERS` |
| 3 | `reservationChain` | `/api/v1/reservations/**` | POST create: authenticated; GET me/**: authenticated; PATCH cancel: authenticated; PATCH extend: `MANAGE_RESERVATIONS`; admin/all: `VIEW_ALL_RESERVATIONS`; admin/**: `VIEW_ALL_RESERVATIONS` |
| 4 | `userChain` | `/api/v1/users/**` | GET list: `MANAGE_USERS`; PATCH role: `MANAGE_ROLES`; POST restore: `MANAGE_USERS`; any other: authenticated. Returns 401 (not 403) for unauthenticated. |
| 5 | `roleAdminChain` | `/api/v1/admin/roles/**`, `/api/v1/admin/permissions/**` | All requests: `MANAGE_ROLES` |
| 100 | `fallbackChain` | `/**` | `/actuator/health` permitAll; everything else authenticated |

### Permission Model

Roles carry explicit permission sets stored in `role_permissions`. There is no role hierarchy — if two roles should share a permission, that permission must be assigned to both. The admin API (`RoleController`) allows modifying permission sets at runtime without redeployment.

**Seeded default assignments (from V5 migration):**

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
| GET | `/api/v1/auth/token` | Public (reads HttpOnly cookie, validates JWT, checks user state) |

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
| PATCH | `/api/v1/users/{id}/role` | `MANAGE_ROLES` | Self-change rejected (400) |
| DELETE | `/api/v1/users/{id}` | `MANAGE_USERS` or self | `@userSecurity.isSelf(#id, authentication)`; last-admin guard |
| POST | `/api/v1/users/{id}/restore` | `MANAGE_USERS` | — |

#### Registration — `/api/v1/admin/registrations`, `/api/v1/registrations`
| Method | Path | Rule |
|---|---|---|
| POST | `/api/v1/admin/registrations` | `REGISTER_USERS` — pre-register a user (generates invite, sends claim email) |
| POST | `/api/v1/registrations/claim` | Authenticated (incl. ROLE_PENDING) — claim invite token, activate account |

#### Reservations — `/api/v1/reservations`
| Method | Path | Rule | Notes |
|---|---|---|---|
| POST | `/api/v1/reservations` | Authenticated | Conflict-checked; MAINTENANCE/RETIRED equipment blocked |
| GET | `/api/v1/reservations/me` | Authenticated | User's own reservations, ordered by startTime desc |
| GET | `/api/v1/reservations/me/{id}` | Authenticated | Own reservation; `VIEW_ALL_RESERVATIONS` can view any |
| PATCH | `/api/v1/reservations/{id}/cancel` | Authenticated | Ownership enforced in service; `MANAGE_RESERVATIONS` can cancel any |
| PATCH | `/api/v1/reservations/{id}/extend` | `MANAGE_RESERVATIONS` | Conflict-checked for the extension window |
| GET | `/api/v1/reservations/admin/all` | `VIEW_ALL_RESERVATIONS` | Paginated (default 50/page) |
| GET | `/api/v1/reservations/admin/equipment/{id}` | `VIEW_ALL_RESERVATIONS` | All reservations for a piece of equipment |

#### Roles & Permissions — `/api/v1/admin/roles`, `/api/v1/admin/permissions`
| Method | Path | Rule |
|---|---|---|
| GET | `/api/v1/admin/permissions` | `MANAGE_ROLES` — list all available permission codes |
| GET | `/api/v1/admin/roles` | `MANAGE_ROLES` — list all roles with their permission sets |
| GET | `/api/v1/admin/roles/{code}` | `MANAGE_ROLES` — get a single role |
| POST | `/api/v1/admin/roles` | `MANAGE_ROLES` — create a custom role (optionally with initial permissions) |
| PUT | `/api/v1/admin/roles/{code}/permissions` | `MANAGE_ROLES` — replace permission set (send empty set to revoke all) |
| DELETE | `/api/v1/admin/roles/{code}` | `MANAGE_ROLES` — delete custom role; 409 if system role or still in use |

---

## Soft-Delete & Account Closure

- **`@SQLDelete`** on `User` converts `DELETE` to `UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?`
- **`@SQLRestriction("deleted_at IS NULL")`** filters soft-deleted rows from all standard JPA queries
- **`UserService.softDeleteUser(id, actorId)`** — the primary delete entry point from the controller. Enforces the last-admin guard, logs the actor, deletes, and immediately evicts both user state and permission caches.
- **`UserService.softDelete(User)`** — lower-level variant used internally; has `@CacheEvict`.
- **`UserStateService`** — caches `ACTIVE|PENDING|DELETED|NOT_FOUND` per email for 30s. Evicted on delete or restore.
- **`OAuth2SuccessHandler`** — if `resolve()` returns `Deleted`, redirects to `/account-closed` with no JWT issued.
- **Self-delete** (`DELETE /api/v1/users/{id}` where actor == target): controller clears the `access_token` HttpOnly cookie in the response.

---

## Caching

Two caches (Caffeine, both 30s TTL / 10,000 entries max):

### `userState`
- **Populated by** `UserStateService.stateOf(email)` — called by `JwtAuthFilter` on every authenticated request and by `TokenController`.
- **Evicted on** soft-delete (via `UserService.softDeleteUser` and `UserService.softDelete @CacheEvict`) and restore (via `UserService.restore`).

### `userPermissions`
- **Populated by** `UserPermissionService.getEffectivePermissions(email)` — called by `JwtAuthFilter` for every ACTIVE user request.
- **Evicted per-user** (`evict(email)`) when that user's role assignments change.
- **Evicted wholesale** (`evictAll()`) when a role's permission set is modified via `RoleService.setPermissions` — affects every user holding that role.

---

## Billing Schema (V4)

Database tables exist; no Java models or services yet.

| Table | Purpose |
|---|---|
| `users.stripe_customer_id` | Links a user to their Stripe Customer. Nullable; created lazily on first checkout. |
| `stripe_event_log` | Idempotency ledger. Every inbound Stripe event recorded exactly once. Statuses: RECEIVED, PROCESSED, FAILED, SKIPPED. Sources: EVENTBRIDGE, RECONCILIATION, MANUAL. |
| `membership_plan` | Plan catalog mapping membership tiers to Stripe Prices. Includes `grants_role` FK to roles catalog. |
| `membership` | Local mirror of the Stripe subscription. Statuses: ACTIVE, PAST_DUE, TRIALING, GRACE (+ terminal states). Unique partial index enforces at most one non-terminal membership per user. |
| `payment_record` | Immutable payment ledger. Append-only. Kinds: MEMBERSHIP, DAY_PASS, CLASS, RENTAL, REFUND. |

---

## Tests

All 55 tests pass.

| File | Count | Type | Covers |
|---|---|---|---|
| `BackendApplicationTests.java` | 1 | Spring context load | Application starts |
| `JwtServiceTest.java` | 4 | Unit (no Spring) | generateToken, parseClaims, invalid/tampered tokens |
| `UserRepositoryTest.java` | 8 | Integration (@DataJpaTest, H2) | @SQLDelete, @SQLRestriction, findByIdIncludingDeleted, re-insert constraint violation |
| `UserServiceTest.java` | 12 | Unit (Mockito) | findUser, findById, provision, updateProfile, updateRole, restore, countActiveAdmins, findAllActive |
| `UserStateServiceTest.java` | 3 | Unit (Mockito) | stateOf → ACTIVE / DELETED / NOT_FOUND |
| `JwtAuthFilterTest.java` | 6 | Unit (Mockito + MockHttpServlet) | No token/invalid pass-through, active user → UserPrincipal on SecurityContext, deleted → 403, unknown → 401, admin role authority |
| `UserControllerTest.java` | 21 | @WebMvcTest | All 7 endpoints: auth/authz rules, input validation, ownership checks via real UserPrincipal authentication, guard conditions (self-role-change, last-admin) |

---

## Known Issues / Incomplete Areas

### Functional gaps
- **No COMPLETED reservation transition** — `ReservationStatus.COMPLETED` exists but nothing transitions a reservation to COMPLETED automatically after its `endTime` passes. Needs a scheduled task (`@Scheduled`) or a Quartz job.
- **No automatic maintenance cancellation** — when equipment is set to MAINTENANCE, existing ACTIVE reservations are not cancelled or notified.
- **Email is stub-only** — `StubEmailService` logs to console. No real mail provider configured.
- **No waiver gating** — reservations can be created without a signed waiver.
- **Billing Java layer absent** — V4 schema is in place but there are no Java models, services, controllers, or Stripe SDK integration for `membership`, `membership_plan`, or `payment_record`.

### Security concerns
- **`application.properties` contains plaintext secrets** — Auth0 client secret and JWT signing key are committed in plain text. Must move to environment variables or a secrets manager before any shared deployment.
- **No token revocation** — JWTs are stateless and valid until expiry (1h). Soft-delete is mitigated by the `UserStateService` cache check, but a deleted user's token remains cryptographically valid for up to 30 seconds (cache TTL) after deletion.
- **Cookie `SameSite=Lax`** — acceptable since the token exchange reads the cookie and returns a Bearer token (effective double-submit pattern), but does not protect against same-site CSRF.
- **No refresh token** — the 1h JWT is the only credential. After expiry, the user must go through the full OAuth flow again.

### Design notes
- **`@EnableJpaAuditing` vs inline `LocalDateTime.now()`** — `Application` has `@EnableJpaAuditing` but `User.createdAt` and `Equipment.createdAt` are initialised inline. The `@CreatedDate` annotation is redundant and lacks `@Column(updatable=false)`.
- **`GUEST`/`RENTEE` roles unreferenced in access rules** — these roles exist in the catalog and can have permissions assigned via the admin API, but no endpoints are currently gated on permissions that only these roles would hold.

### Fixed
- ✅ Reservation system fully implemented
- ✅ Multi-role support — `User.roles` is `Set<AppRole>` via `user_roles` join table
- ✅ Role hierarchy replaced by permission-based RBAC — `RoleHierarchyConfig` removed; each role holds an explicit permission set; no implicit inheritance
- ✅ All security filter chains unified into `SecurityConfig`; `ReservationSecurityConfig` and `UserSecurityConfig` removed
- ✅ Local in-person registration — invite flow, `AccountClaimService`, `InviteTokenService`, `StubEmailService`
- ✅ `UserProfile`, `Address`, `PhoneNumber` decoupled from `User`
- ✅ `EquipmentReservation.user` `@OneToOne` → `@ManyToOne`
- ✅ `UserService.provision` always INSERTs unconditionally — `OAuth2SuccessHandler` now calls `resolve()` first
- ✅ Cookie maxAge (1h) / JWT expiry aligned to 3600000ms
- ✅ `TokenController` checks soft-delete via `UserStateService.stateOf`
- ✅ `UserPrincipal` fully wired — `JwtAuthFilter` builds and sets it; `UserController.currentUserId()` reads from it
- ✅ `UserController.deleteUser` guard — `UserService.softDeleteUser(Long, Long)` with last-admin guard and cache eviction
- ✅ `UserControllerTest` replaced `@WithMockUser` with `SecurityMockMvcRequestPostProcessors.authentication()` and real `UserPrincipal`
- ✅ Billing schema groundwork — V4 migration adds Stripe tables and `users.stripe_customer_id`
