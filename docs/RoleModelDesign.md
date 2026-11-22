# Role Model & Inheritance — Implementation Brief

**Audience:** Claude Code, working in the `collaboratory-makerspace` backend (Spring Boot 3.3.9 / Spring Security 6.3.x, JPA/Hibernate 6).
**Source material:** `Sleepers_User_Stories.pdf` (role definitions) and `Business_Requirements_vs_Backend.xlsx` (current backend audit, "Requirements vs Backend" sheet).

This brief decides how role inheritance should work and what to change. **Verify each "current state" claim against the actual code before editing** — the audit may lag the repo.

---

## 1. The decision (read first)

There are **two orthogonal axes**, not one role ladder:

1. **Authority axis** — `INSTRUCTOR`, `STAFF`, `ADMIN`. What management actions a user may perform.
2. **Customer-eligibility axis** — member / day-pass guest / student / equipment renter. Whether a user may *use a resource right now*, computed from data (active membership/pass + valid waiver + required training + valid reservation window), per the access-eligibility requirements (IUM-08b…IUM-13b).

**Implement inheritance only on the authority axis, as a DAG:**

- `ADMIN` implies `STAFF` and `INSTRUCTOR`.
- `STAFF` and `INSTRUCTOR` are **siblings** — neither implies the other (staff = payments/eligibility checks; instructor = classes/certification).

**Hard rule — inheritance must NOT cross into the customer/eligibility axis.** Administrative seniority must never imply customer eligibility. If `ADMIN` implied `MEMBER`, an admin would pass equipment-access checks without a signed waiver or required training — the exact safety/liability failure the administrator user stories say the system exists to prevent. Eligibility stays a data computation, independent of authority level.

---

## 2. Requirements this addresses

| ID | Requirement | Current status (verify) | This change |
|----|-------------|------------------------|-------------|
| IUM-11 | A user can be an employee and a client at the same time | Not implemented — single enum value per user | Closed by multi-valued authority roles |
| IUM-06/07/08 | Roles for admin / instructor / client | ADMIN enforced; INSTRUCTOR partial (defined, unused) | INSTRUCTOR/STAFF become enforceable via hierarchy |
| IUM-14b | Implement a base/intermediate staff role | STAFF + ADMIN only; no middle tier | Hierarchy makes adding a middle tier trivial |
| IUM-18b | Secure authorization for all users | `@PreAuthorize` + per-route rules | Hierarchy removes `hasAnyRole(...)` duplication |

The customer-type values (`MEMBER`/`GUEST`/`STUDENT`/`RENTEE`, currently "defined but unused in authorization rules" per IUM-03/04/05/07) should **stop being treated as authorization roles**. Leave them descriptive at most; real customer gating comes from the eligibility engine when those subsystems are built (out of scope here — see §6).

---

## 3. Verify current state before editing

Run these checks and confirm before making changes:

1. **The `Role` enum** — confirm values: expecting `MEMBER, GUEST, STUDENT, RENTEE, INSTRUCTOR, STAFF, ADMIN`. Note which are referenced in any `@PreAuthorize` / `hasRole` / `authorizeHttpRequests` rule vs. defined-but-unused.
2. **The `User` entity** — confirm `role` is a single `@Enumerated` field (the audit says single value per user). Find every read of it.
3. **`UserPrincipal`** — check whether the in-progress typed-principal refactor exposes the role; if so, it must expose a **collection** of authority roles after this change, not a single value. Align the two refactors so the principal isn't migrated twice.
4. **`@PreAuthorize` usage** — grep for `hasRole`, `hasAnyRole`, `hasAuthority` across controllers and security configs. List every place that enumerates multiple roles to cover seniority (e.g. `hasAnyRole('ADMIN','STAFF')`) — these are what the hierarchy will simplify.
5. **Spring Security version** — confirm 6.3.x (from Boot 3.3.9). In 6.3 a published `RoleHierarchy` bean is auto-applied to **both** web and method security. If the resolved version differs, adjust wiring in §4.2 accordingly.
6. **Last-admin guard** — confirm the existing `softDeleteUser` pessimistic-lock guard keys off DB-sourced ADMIN role; it must keep working after the enum→collection change (the "is this user an admin" check now becomes "does the role set contain ADMIN").

---

## 4. Changes to make

### 4.1 Make authority roles multi-valued (closes IUM-11)

Replace the single `role` field on `User` with a set of authority roles.

- Use a join table (e.g. `user_roles`) via `@ElementCollection` + `@CollectionTable`, or a dedicated entity if a relation is preferred. Keep it `EnumType.STRING`.
- Migrate existing data: every current single role becomes a one-element set. Provide a schema migration (Flyway/Liquibase if present; otherwise document the manual DDL + backfill).
- Update all reads of `user.getRole()` to operate on the set (`getRoles()`), including:
    - the role-from-DB authorization path,
    - the last-admin guard (now `roles.contains(ADMIN)`),
    - role assignment/removal (admin "manage user roles" story — assign/remove a role from the set, effective immediately),
    - `UserPrincipal` population.
- **Scope the set to authority roles only.** Do not store `MEMBER/GUEST/STUDENT/RENTEE` here as access drivers. If you need to retain a customer-type marker short-term, keep it as a separate descriptive field, not in the authority set, and do not gate access on it.

### 4.2 Add the authority RoleHierarchy

Publish a `RoleHierarchy` bean encoding the DAG. Place it in the security config (alongside the existing filter-chain beans).

```java
@Bean
static RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("ADMIN").implies("STAFF", "INSTRUCTOR")
        .build();
}
```

- On Security 6.3 this is auto-detected by both web and method security. **Verify** by testing that an ADMIN-only principal passes a `hasRole('STAFF')`-protected endpoint. If your version does not auto-wire it, register it explicitly on the `MethodSecurityExpressionHandler` and the web `AuthorizationManager`.
- After it's confirmed working, simplify the duplicated checks found in §3.4: `hasAnyRole('ADMIN','STAFF')` → `hasRole('STAFF')`, etc. Do this only where the intent is genuinely "STAFF and anyone above." Leave any check that is intentionally exact (e.g. an action only STAFF and not ADMIN should do — unlikely, but confirm) untouched.

### 4.3 Wire INSTRUCTOR/STAFF into rules they currently lack

The audit lists INSTRUCTOR/STAFF as defined-but-unused. With the hierarchy in place, add the `@PreAuthorize`/route rules the user stories imply (instructor class management, staff eligibility-view endpoints) **only for endpoints that already exist.** Do not create new controllers for unbuilt subsystems here.

### 4.4 Separation of duties is a service rule, not a hierarchy concern

The "a staff member cannot manage payments for their own account" story is **not** about whether STAFF holds the payment permission (it does). It's a target check. When the payment service is built, enforce it there: reject if `target.equals(currentUser)`. Do **not** try to express this in the role hierarchy or security config. (Same category as the existing last-admin guard.)

---

## 5. Acceptance checks

- A user can hold `STAFF` (or `ADMIN`) **and** be a customer (hold a membership record) simultaneously — IUM-11.
- An `ADMIN`-only principal passes endpoints guarded by `hasRole('STAFF')` and `hasRole('INSTRUCTOR')`.
- A `STAFF`-only principal does **not** pass `hasRole('INSTRUCTOR')` endpoints, and vice versa (siblings).
- No code path lets authority roles satisfy a customer-eligibility/physical-access check.
- Last-admin guard still blocks deletion of the final ADMIN, now reading the role set.
- All previously passing security tests still pass; add tests for the three bullets above.

---

## 6. Explicitly out of scope (do not build now)

- The eligibility engine (membership/waiver/training/reservation evaluation) and all the unbuilt domains it depends on — payments, waivers, training/certification, reservations, check-in. These are separate, large efforts in the audit and must not be stubbed in as part of the role refactor.
- An intermediate staff tier (IUM-14b) — the hierarchy makes it easy to add later (`ADMIN > MANAGER > STAFF`); add it only when the requirement is scheduled.
- RFID/physical-access integration.

Keep this change confined to: the authority role data model, the hierarchy bean, and the authorization rules/principal that consume them.