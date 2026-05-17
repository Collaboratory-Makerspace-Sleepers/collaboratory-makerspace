# Backend Application State

_Last updated: 2026-05-17 (all tests passing)_

---

## Overview

Spring Boot 3.3.9 / Java 21 REST API for the Collaboratory Makerspace. The application manages users, equipment, and equipment reservations. Authentication is handled via Auth0 (OIDC/OAuth2) with JWTs issued as HttpOnly cookies after the OAuth callback, then exchanged for in-memory Bearer tokens by the frontend.

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
| Boilerplate | Lombok 1.18.44 |
| Build | Maven |

---

## Package Structure

```
com.makerspace.backend
├── Application.java                    Entry point (@SpringBootApplication, @EnableJpaAuditing)
├── config/
│   ├── CacheConfig.java                Caffeine cache, "userState" cache, 30s TTL, 10k max
│   ├── SecurityConfig.java             JWT filter chain (Order 1: auth/oauth2, Order 100: fallback)
│   └── security/
│       ├── OAuthProfile.java           Record: email, firstName, lastName, subject (OIDC → app DTO)
│       ├── ReservationSecurityConfig.java  Security chain (Order 2) for /api/reservations/**
│       ├── UserPrincipal.java          Record: userId, auth0Subject, email, authorities — set on SecurityContext by JwtAuthFilter
│       ├── UserSecurity.java           @Component("userSecurity") — isSelf(id, auth) for SpEL @PreAuthorize
│       └── UserSecurityConfig.java     Security chain (Order 3) for /api/users/**
├── controller/
│   ├── dto/
│   │   ├── UpdateProfileRequest.java   record: firstName, lastName (@NotBlank, @Size(max=100))
│   │   ├── UpdateRoleRequest.java      record: role (@NotNull)
│   │   ├── UserAdminDTO.java           record: id, email, firstName, lastName, role, createdAt, deletedAt
│   │   └── UserDTO.java                record: id, email, firstName, lastName, role
│   ├── EquipmentController.java        Full CRUD for equipment
│   ├── TokenController.java            Cookie → Bearer token exchange (/api/auth/token)
│   └── UserController.java             7 endpoints (see API section)
├── model/
│   ├── Equipment.java                  id, name, description, category, imageUrl, status, createdAt
│   ├── EquipmentReservation.java       id, user, equipment, startTime, endTime
│   ├── EquipmentStatus.java            Enum: AVAILABLE, IN_USE, MAINTENANCE, RETIRED
│   ├── Role.java                       Enum: MEMBER, STAFF, ADMIN, GUEST, INSTRUCTOR, RENTEE, STUDENT
│   ├── User.java                       id, email, firstName, lastName, role, createdAt, deletedAt
│   └── UserResolution.java             Sealed interface: Active(user) | Deleted(id, deletedOn) | NotFound(profile)
├── repository/
│   ├── EquipmentRepository.java        findByStatus, findByCategory, findByNameContainingIgnoreCase
│   └── UserRepository.java             findByEmail, findByEmailIncludingDeleted, findByIdIncludingDeleted (native), countByRole
├── security/
│   ├── JwtAuthFilter.java              Per-request JWT validation + soft-delete enforcement; sets UserPrincipal on SecurityContext
│   └── OAuth2SuccessHandler.java       OAuth2 success → provision user → set JWT cookie → redirect
└── services/
    ├── EquipmentService.java           findAll, findById, findByStatus, findByCategory, search, create, update, updateStatus, delete
    ├── JwtService.java                 generateToken, parseToken, isValid
    ├── UserService.java                findUser, findById, findAllActive(Pageable), resolve, provision, updateProfile, updateRole, softDelete (@CacheEvict), softDeleteUser (guard + evict), restore, countActiveAdmins
    └── UserStateService.java           stateOf(email) → ACTIVE|DELETED|NOT_FOUND, @Cacheable("userState"), evict(email)
```

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
   c. Generates JWT via JwtService.generateToken(user) [sub=userId, email, role, exp=1h]
   d. Sets JWT in HttpOnly Secure cookie (access_token, maxAge=1h, SameSite=Lax)
   e. Redirects to {frontend}/oauth-callback
4. Frontend calls GET /api/auth/token
   a. TokenController reads cookie, validates JWT signature
   b. Calls UserStateService.stateOf(email) — returns 403 for DELETED, 401 for NOT_FOUND
   c. Returns { "access_token": "<jwt>" } in response body
5. Frontend stores token in memory; sends as Authorization: Bearer <jwt>
6. JwtAuthFilter (OncePerRequestFilter) runs on every authenticated request:
   a. Extracts Bearer token; passes through if absent or invalid
   b. Validates JWT signature + expiry via JwtService
   c. Extracts email + role claims; calls UserStateService.stateOf(email) [cached 30s]
   d. ACTIVE  → builds UserPrincipal(userId, auth0Subject, email, authorities),
               sets UsernamePasswordAuthenticationToken on SecurityContext, continues chain
   e. DELETED → 403 { "error": "account_closed" }, chain halted
   f. NOT_FOUND → 401 { "error": "unknown_user" }, chain halted
```

### Identity Principal (`UserPrincipal`)

After JWT validation, `JwtAuthFilter` places a `UserPrincipal` record on the `SecurityContext`. It carries:
- `userId` (Long) — the database primary key, used by `currentUserId()` in controllers and `isSelf()` ownership checks
- `auth0Subject` (String) — the JWT `sub` claim (currently the DB user ID; the actual Auth0 sub is not stored in the JWT)
- `email` (String) — for reference; not used for identity decisions after filter
- `authorities` (Collection) — `ROLE_<ROLE>` authority derived from the JWT `role` claim

`UserSecurity.isSelf(id, auth)` and `getUserId(auth)` read directly from `UserPrincipal`. If the principal is not a `UserPrincipal` (unauthenticated slip-through or misconfiguration), both fail closed — `isSelf` returns `false`, `getUserId` returns `null`.

---

## Access Control Architecture

### Security Filter Chains (in priority order)

| Order | Chain | Matcher | Rules |
|---|---|---|---|
| 1 | `filterChain` (SecurityConfig) | `/api/auth/**`, `/oauth2/**`, `/login/**` | `/api/auth/token` permitAll; `/api/auth/me` authenticated; others permitAll. Handles OAuth2 login. |
| 2 | `reservationChain` (ReservationSecurityConfig) | `/api/reservations/**` | POST create: authenticated; GET me/**: authenticated; PATCH extend/cancel: STAFF or ADMIN; admin/**: ADMIN |
| 3 | `userChain` (UserSecurityConfig) | `/api/users/**` | GET `/api/users`: STAFF or ADMIN; PATCH `*/role`: ADMIN; POST `*/restore`: ADMIN; any other: authenticated. Returns 401 (not 403) for unauthenticated via explicit AuthenticationEntryPoint. |
| 100 | `fallbackChain` (SecurityConfig) | `/**` | `/actuator/health` permitAll; everything else authenticated |

All chains: CSRF disabled, stateless session, `JwtAuthFilter` added before `UsernamePasswordAuthenticationFilter`.

### Role Hierarchy

Roles are flat (no inheritance). A request matching `hasRole('ADMIN')` will not pass for `STAFF`. SpEL ownership checks are additive (`or @userSecurity.isSelf(...)`).

| Role | Privileges |
|---|---|
| GUEST | Lowest. Authenticated but minimal access. |
| STUDENT, RENTEE | Authenticated general members. |
| MEMBER | Default role assigned on first login. |
| INSTRUCTOR | Authenticated general member. |
| STAFF | Can read user list, create/update equipment. |
| ADMIN | Full access including role changes, restores, deletes, hard-delete equipment. |

### Endpoint Authorization Matrix

#### Auth — `/api/auth`
| Method | Path | Rule |
|---|---|---|
| GET | `/api/auth/token` | Public (reads HttpOnly cookie, validates JWT, checks user state) |

#### Equipment — `/api/equipment`
| Method | Path | Rule |
|---|---|---|
| GET | `/api/equipment` | Authenticated |
| GET | `/api/equipment/{id}` | Authenticated |
| GET | `/api/equipment/status/{status}` | Authenticated |
| GET | `/api/equipment/category/{category}` | Authenticated |
| GET | `/api/equipment/search?q=` | Authenticated |
| POST | `/api/equipment` | STAFF or ADMIN |
| PUT | `/api/equipment/{id}` | STAFF or ADMIN |
| PATCH | `/api/equipment/{id}/status` | STAFF or ADMIN |
| DELETE | `/api/equipment/{id}` | ADMIN |

#### Users — `/api/users`
| Method | Path | Rule | Guard |
|---|---|---|---|
| GET | `/api/users/me` | Authenticated | — |
| PATCH | `/api/users/me` | Authenticated | — |
| GET | `/api/users` | STAFF or ADMIN | — |
| GET | `/api/users/{id}` | STAFF, ADMIN, or self | `@userSecurity.isSelf(#id, authentication)` |
| PATCH | `/api/users/{id}/role` | ADMIN | Self-change rejected (400) |
| DELETE | `/api/users/{id}` | ADMIN or self | `@userSecurity.isSelf(#id, authentication)`; last-admin guard in `UserService.softDeleteUser` |
| POST | `/api/users/{id}/restore` | ADMIN | — |

#### Reservations — `/api/reservations`
_Security rules defined in `ReservationSecurityConfig` but no controller or service exists yet — the chain is inert._

---

## Soft-Delete & Account Closure

- **`@SQLDelete`** on `User` converts `DELETE` to `UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?`
- **`@SQLRestriction("deleted_at IS NULL")`** filters soft-deleted rows from all standard JPA queries
- **`UserService.softDeleteUser(id, actorId)`** — the primary delete entry point from the controller. Enforces the last-admin guard, logs the actor, deletes, and immediately evicts the user state cache.
- **`UserService.softDelete(User)`** — lower-level variant used internally; has `@CacheEvict`.
- **`UserStateService`** — caches `ACTIVE|DELETED|NOT_FOUND` per email for 30s. Evicted on delete or restore so `JwtAuthFilter` sees the new state on the very next request.
- **`OAuth2SuccessHandler`** — if `resolve()` returns `Deleted`, redirects to `/account-closed` with no JWT issued.
- **Self-delete** (`DELETE /api/users/{id}` where actor == target): controller clears the `access_token` HttpOnly cookie in the response, invalidating the session client-side.

---

## Caching

One cache: `"userState"` (Caffeine, 30s TTL, max 10,000 entries).

- **Populated by** `UserStateService.stateOf(email)` — called by `JwtAuthFilter` on every authenticated request and by `TokenController` on cookie→bearer exchange.
- **Evicted on soft-delete** by `UserService.softDeleteUser` (explicit `userStateService.evict`) and `UserService.softDelete` (`@CacheEvict`).
- **Evicted on restore** by `UserService.restore` (explicit `userStateService.evict`).

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
- **No reservation controller or service** — `ReservationSecurityConfig` defines access rules but there is no matching controller or service. The `/api/reservations/**` security config is inert.

### Security concerns
- **`application.properties` contains plaintext secrets** — Auth0 client secret and JWT signing key are committed in plain text. These must move to environment variables or a secrets manager before any shared deployment.
- **JWT `sub` vs Auth0 subject mismatch** — `JwtService.generateToken` sets `sub = user.getId().toString()` (the DB primary key), not the Auth0 subject. `UserPrincipal.auth0Subject` is therefore populated with the DB ID string, not the actual Auth0 stable identifier. If Auth0 subject is ever needed for identity verification post-JWT, a new `auth0Subject` claim must be added to the token.
- **No token revocation** — JWTs are stateless and valid until expiry (1h). Soft-delete is mitigated by the `UserStateService` cache check, but a deleted user's token remains cryptographically valid for up to 30 seconds (cache TTL) after deletion. There is no blocklist or short-circuit for immediate revocation.
- **Cookie `SameSite=Lax`** — protects against CSRF on cross-site navigations but does not protect against same-site CSRF. Acceptable since the token exchange (`/api/auth/token`) reads the cookie and returns a Bearer token, which the frontend must then include explicitly — effectively a double-submit pattern.
- **No refresh token** — the 1h JWT is the only credential. After expiry, the user must go through the full OAuth flow again.

### Design notes
- **`@EnableJpaAuditing` vs inline `LocalDateTime.now()`** — `Application` has `@EnableJpaAuditing` but `User.createdAt` and `Equipment.createdAt` are initialised inline (`= LocalDateTime.now()`). The `@CreatedDate` annotation on `User.createdAt` is redundant and lacks `@Column(updatable=false)`.

### Fixed
- ✅ `EquipmentReservation.user` `@OneToOne` → `@ManyToOne`
- ✅ `UserService.findUser` unused `fullName` parameter — removed
- ✅ `UserService.provision` always INSERTs unconditionally — `OAuth2SuccessHandler` now calls `resolve()` first
- ✅ Cookie maxAge (1h) / JWT expiry aligned to 3600000ms
- ✅ `TokenController` does not check soft-delete — now calls `UserStateService.stateOf`
- ✅ `ReservationSecurityConfig` missing stateless session policy — fixed
- ✅ `UserController` empty — fully implemented with 7 endpoints, DTOs, guards
- ✅ `UserPrincipal` created but not wired — `JwtAuthFilter` now builds and sets `UserPrincipal`; `UserController.currentUserId()` reads from it; `UserSecurity.isSelf/getUserId` ownership checks work correctly
- ✅ `UserController.deleteUser` called non-existent `softDeleteUser` — `UserService.softDeleteUser(Long, Long)` added with last-admin guard and cache eviction
- ✅ `UserControllerTest` used `@WithMockUser` (String principal) — replaced with `SecurityMockMvcRequestPostProcessors.authentication()` and real `UserPrincipal` to match production behaviour
