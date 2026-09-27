# Collaboratory Makerspace — API Reference

_Base URL: `http://localhost:8080` (dev) / `https://api.your-domain.com` (prod)_
_Last updated: 2026-09-27_

---

## Authentication

All protected endpoints require a Bearer token in the `Authorization` header:

```
Authorization: Bearer <jwt>
```

JWTs are obtained via one of two flows (see Auth section below). They expire after **1 hour**.

---

## Table of Contents

1. [Auth](#1-auth)
2. [Billing](#2-billing)
3. [Equipment](#3-equipment)
4. [Users](#4-users)
5. [Registration](#5-registration)
6. [Reservations](#6-reservations)
7. [Roles & Permissions (Admin)](#7-roles--permissions-admin)
8. [Error Responses](#8-error-responses)

---

## 1. Auth

### GET `/api/v1/auth/token`

Exchanges the HttpOnly `access_token` cookie (set after OAuth2 login) for a Bearer token. Call this once after the OAuth2 redirect to get the token to store in memory.

**Auth:** None (public)

**Response 200:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response 403 — account closed:**
```json
{ "error": "account_closed" }
```

---

### POST `/api/v1/auth/otp/send`

Sends a 6-digit OTP to the given email address. Rate-limited to one send per 60 seconds per email. Always returns 200 regardless of whether the email exists (prevents enumeration).

**Auth:** None (public)

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response 200:** Empty body

**Response 400 — missing email:**
```json
{ "status": 400, "error": "email is required" }
```

---

### POST `/api/v1/auth/otp/verify`

Verifies the OTP. On success, provisions or resolves the user account, sets a JWT HttpOnly cookie, and returns the token in the response body. The OTP is consumed on use (single-use).

**Auth:** None (public)

**Request:**
```json
{
  "email": "user@example.com",
  "code": "847293"
}
```

**Response 200:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response 401 — bad or expired code:**
```json
{ "status": 401, "error": "invalid_or_expired_code" }
```

**Response 403 — account closed:**
```json
{ "status": 403, "error": "account_closed" }
```

---

## 2. Billing

All billing endpoints operate on the authenticated user's own account. There are no user-ID path parameters — the user is always derived from the Bearer token.

### POST `/api/v1/billing/checkout-session`

Creates a Stripe Checkout session for the given plan. Returns a Stripe-hosted URL; redirect the user to it. A Stripe Customer is created lazily on the first checkout.

**Auth:** Authenticated

**Request:**
```json
{
  "planCode": "MONTHLY"
}
```

Available plan codes: `MONTHLY`, `ANNUAL`, `STUDENT`, `DAY_PASS`

**Response 200:**
```json
{
  "sessionUrl": "https://checkout.stripe.com/c/pay/cs_test_..."
}
```

**Response 404 — unknown or inactive plan:**
```json
{ "status": 404, "error": "Plan not found" }
```

**Response 502 — Stripe error:**
```json
{ "status": 502, "error": "Payment provider error" }
```

---

### POST `/api/v1/billing/portal-session`

Creates a Stripe Customer Portal session. Redirect the user to the returned URL so they can manage their subscription, update payment methods, or cancel.

**Auth:** Authenticated

**Request:** Empty body

**Response 200:**
```json
{
  "portalUrl": "https://billing.stripe.com/p/session/..."
}
```

**Response 400 — no billing account yet:**
```json
{ "status": 400, "error": "No billing account found. Please complete a checkout first" }
```

**Response 502 — Stripe error:**
```json
{ "status": 502, "error": "Payment provider error" }
```

---

### GET `/api/v1/billing/me/subscription`

Returns the current user's membership status and plan details.

**Auth:** Authenticated

**Response 200:**
```json
{
  "status": "ACTIVE",
  "planCode": "MONTHLY",
  "currentPeriodEnd": "2026-10-27T00:00:00Z",
  "cancelAtPeriodEnd": false,
  "gracePeriodEndsAt": null
}
```

**Possible `status` values:**

| Value | Meaning | Can Reserve Equipment? |
|---|---|---|
| `ACTIVE` | Subscription current | Yes |
| `TRIALING` | In trial period | Yes |
| `PAST_DUE` | Payment failed, retrying | Yes (configurable) |
| `GRACE` | Canceled/unpaid, within grace window | No |
| `CANCELED` | Grace period expired | No |
| `INCOMPLETE` | Initial payment incomplete | No |
| `PAUSED` | Subscription paused | No |
| `NONE` | No membership record | No |

**Response 404 — no membership:**
```json
{ "status": 404, "error": "No Membership found" }
```

---

### GET `/api/v1/billing/me/payments`

Returns the current user's payment history, most recent first.

**Auth:** Authenticated

**Response 200:**
```json
[
  {
    "kind": "MEMBERSHIP",
    "status": "SUCCEEDED",
    "amountCents": 2999,
    "currency": "usd",
    "description": "Monthly membership — October 2026",
    "occurredAt": "2026-09-27T14:23:00Z"
  },
  {
    "kind": "MEMBERSHIP",
    "status": "SUCCEEDED",
    "amountCents": 2999,
    "currency": "usd",
    "description": "Monthly membership — September 2026",
    "occurredAt": "2026-08-27T14:23:00Z"
  }
]
```

**Possible `kind` values:** `MEMBERSHIP`, `DAY_PASS`, `CLASS`, `RENTAL`, `REFUND`

**Possible `status` values:** `PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED`

Returns an empty array `[]` if no payments on record.

---

## 3. Equipment

### GET `/api/v1/equipment`

Returns all equipment.

**Auth:** Authenticated

**Response 200:**
```json
[
  {
    "id": 1,
    "name": "Laser Cutter",
    "description": "60W CO2 laser cutter, 24x16 inch bed",
    "category": "Fabrication",
    "imageUrl": "https://...",
    "status": "AVAILABLE",
    "createdAt": "2026-01-15T10:00:00Z"
  }
]
```

**Possible `status` values:** `AVAILABLE`, `IN_USE`, `MAINTENANCE`, `RETIRED`

---

### GET `/api/v1/equipment/{id}`

Returns a single piece of equipment.

**Auth:** Authenticated

**Response 200:** Same shape as a single item from the list above.

**Response 404:**
```json
{ "status": 404, "error": "Equipment not found" }
```

---

### GET `/api/v1/equipment/status/{status}`

Returns equipment filtered by status.

**Auth:** Authenticated

**Path param:** `status` — one of `AVAILABLE`, `IN_USE`, `MAINTENANCE`, `RETIRED`

**Response 200:** Array of equipment objects.

---

### GET `/api/v1/equipment/category/{category}`

Returns equipment filtered by category (exact match, case-insensitive).

**Auth:** Authenticated

**Response 200:** Array of equipment objects.

---

### GET `/api/v1/equipment/search?q={term}`

Searches equipment by name (case-insensitive substring match).

**Auth:** Authenticated

**Response 200:** Array of equipment objects.

---

### POST `/api/v1/equipment`

Creates a new piece of equipment.

**Auth:** `MANAGE_EQUIPMENT` permission

**Request:**
```json
{
  "name": "3D Printer",
  "description": "FDM printer, 250x250x250mm build volume",
  "category": "Fabrication",
  "imageUrl": "https://...",
  "status": "AVAILABLE"
}
```

**Response 200:** The created equipment object.

---

### PUT `/api/v1/equipment/{id}`

Replaces an equipment record.

**Auth:** `MANAGE_EQUIPMENT` permission

**Request:** Same shape as POST.

**Response 200:** The updated equipment object.

---

### PATCH `/api/v1/equipment/{id}/status`

Updates equipment status only.

**Auth:** `MANAGE_EQUIPMENT` permission

**Request:**
```json
{
  "status": "MAINTENANCE"
}
```

**Response 200:** The updated equipment object.

---

### DELETE `/api/v1/equipment/{id}`

Deletes a piece of equipment.

**Auth:** `MANAGE_EQUIPMENT` permission

**Response 204:** No content.

---

## 4. Users

### GET `/api/v1/users/me`

Returns the authenticated user's own profile.

**Auth:** Authenticated

**Response 200:**
```json
{
  "id": 42,
  "email": "user@example.com",
  "firstName": "Alex",
  "lastName": "Smith",
  "roles": ["MEMBER"]
}
```

---

### PATCH `/api/v1/users/me`

Updates the authenticated user's own profile.

**Auth:** Authenticated

**Request:**
```json
{
  "firstName": "Alexandra",
  "lastName": "Smith"
}
```

Both fields are required and limited to 100 characters each.

**Response 200:**
```json
{
  "id": 42,
  "email": "user@example.com",
  "firstName": "Alexandra",
  "lastName": "Smith",
  "roles": ["MEMBER"]
}
```

---

### GET `/api/v1/users`

Returns a paginated list of all active users.

**Auth:** `MANAGE_USERS` permission

**Query params:** `page` (0-indexed, default 0), `size` (default 20), `sort`

**Response 200:**
```json
{
  "content": [
    {
      "id": 1,
      "email": "admin@makerspace.org",
      "firstName": "Jordan",
      "lastName": "Lee",
      "roles": ["ADMIN"],
      "createdAt": "2026-01-01T00:00:00Z",
      "deletedAt": null
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

### GET `/api/v1/users/{id}`

Returns a single user. Accessible by the user themselves or anyone with `MANAGE_USERS`.

**Auth:** Authenticated (self) or `MANAGE_USERS`

**Response 200:** Same shape as an item in the paginated list above.

**Response 404:**
```json
{ "status": 404, "error": "User not found" }
```

---

### PATCH `/api/v1/users/{id}/role`

Updates a user's roles. Cannot update your own roles. Cannot remove the last admin.

**Auth:** `MANAGE_ROLES` permission

**Request:**
```json
{
  "role": "STAFF"
}
```

**Response 200:** The updated `UserAdminDTO`.

**Response 400 — self-change attempt:**
```json
{ "status": 400, "error": "Cannot change your own role" }
```

**Response 400 — last admin guard:**
```json
{ "status": 400, "error": "Cannot remove the last admin" }
```

---

### DELETE `/api/v1/users/{id}`

Soft-deletes a user. The user's account is closed; their data remains. Accessible by the user themselves or anyone with `MANAGE_USERS`. Cannot delete the last admin.

**Auth:** Authenticated (self) or `MANAGE_USERS`

**Response 204:** No content.

**Response 400 — last admin guard:**
```json
{ "status": 400, "error": "Cannot delete the last admin" }
```

---

### POST `/api/v1/users/{id}/restore`

Restores a soft-deleted user, setting their account back to ACTIVE.

**Auth:** `MANAGE_USERS` permission

**Request:** Empty body

**Response 200:** The restored `UserAdminDTO`.

---

## 5. Registration

### POST `/api/v1/admin/registrations`

Pre-registers a user. Creates a `RegistrationInvite`, generates a secure claim token, and emails a claim link to the provided address. The user's account is created in `PRE_REGISTERED` state.

**Auth:** `REGISTER_USERS` permission

**Request:**
```json
{
  "email": "newmember@example.com",
  "firstName": "Taylor",
  "lastName": "Brown",
  "roles": ["MEMBER"]
}
```

**Response 200:**
```json
{
  "inviteId": "inv_a1b2c3d4",
  "email": "newmember@example.com",
  "claimToken": "tok_xyzABC123...",
  "expiresAt": "2026-10-04T14:00:00Z"
}
```

**Response 409 — email already registered:**
```json
{ "status": 409, "error": "Email already registered" }
```

---

### POST `/api/v1/registrations/claim`

Claims a registration invite. The caller must already be authenticated (via OAuth2 or OTP) as the invited email address — the backend verifies the authenticated identity matches the pre-registered record before activating. No password is involved; the system is fully passwordless.

After a successful claim the client should re-authenticate (repeat the OAuth2 or OTP flow) to get a JWT with full roles — the `ROLE_PENDING` token does not automatically upgrade.

**Auth:** Authenticated (including `ROLE_PENDING` accounts)

**Request:**
```json
{
  "token": "tok_xyzABC123..."
}
```

**Response 200:**
```json
{
  "id": 42,
  "email": "newmember@example.com",
  "firstName": "Taylor",
  "lastName": "Brown",
  "roles": ["MEMBER"]
}
```

**Response 400 — invalid or expired token:**
```json
{ "status": 400, "error": "Invalid invite token" }
```

**Response 409 — email mismatch (signed in as wrong account):**
```json
{
  "status": 409,
  "error": "You signed in as other@example.com but were invited as newmember@example.com. Please contact staff to correct the record."
}
```

---

## 6. Reservations

### POST `/api/v1/reservations`

Creates a new equipment reservation. Requires an active membership — returns HTTP 402 otherwise. Blocks reservations on equipment in MAINTENANCE or RETIRED status. Conflict-checks against existing reservations.

**Auth:** Authenticated

**Request:**
```json
{
  "equipmentId": 3,
  "startTime": "2026-10-01T09:00:00Z",
  "endTime": "2026-10-01T11:00:00Z"
}
```

Both `startTime` and `endTime` must be in the future.

**Response 200:**
```json
{
  "id": 101,
  "equipmentId": 3,
  "equipmentName": "Laser Cutter",
  "userId": 42,
  "startTime": "2026-10-01T09:00:00Z",
  "endTime": "2026-10-01T11:00:00Z",
  "status": "ACTIVE",
  "cancelledAt": null,
  "createdAt": "2026-09-27T15:30:00Z"
}
```

**Response 402 — no active membership:**
```json
{ "status": 402, "error": "An active membership is required to reserve equipment" }
```

**Response 409 — time conflict:**
```json
{ "status": 409, "error": "Equipment is already reserved for this time slot" }
```

**Response 422 — equipment unavailable:**
```json
{ "status": 422, "error": "Equipment is currently unavailable for reservations" }
```

---

### GET `/api/v1/reservations/me`

Returns all reservations for the authenticated user, most recent first.

**Auth:** Authenticated

**Response 200:** Array of reservation objects (same shape as POST response).

---

### GET `/api/v1/reservations/me/{id}`

Returns a single reservation. Users can only see their own. Users with `VIEW_ALL_RESERVATIONS` can view any reservation by ID.

**Auth:** Authenticated (own) or `VIEW_ALL_RESERVATIONS`

**Response 200:** Reservation object.

**Response 404 — not found or not owned:**
```json
{ "status": 404, "error": "Reservation not found" }
```

---

### PATCH `/api/v1/reservations/{id}/cancel`

Cancels a reservation. Users can cancel their own. `MANAGE_RESERVATIONS` can cancel any.

**Auth:** Authenticated (own) or `MANAGE_RESERVATIONS`

**Request:** Empty body

**Response 200:** Updated reservation object with `"status": "CANCELLED"`.

**Response 403 — not the owner:**
```json
{ "status": 403, "error": "Not authorized to cancel this reservation" }
```

**Response 409 — already cancelled:**
```json
{ "status": 409, "error": "Reservation is already cancelled" }
```

---

### PATCH `/api/v1/reservations/{id}/extend`

Extends a reservation's end time. Conflict-checks the new window.

**Auth:** `MANAGE_RESERVATIONS` permission

**Request:**
```json
{
  "newEndTime": "2026-10-01T13:00:00Z"
}
```

**Response 200:** Updated reservation object.

**Response 409 — extension conflicts:**
```json
{ "status": 409, "error": "Equipment is already reserved for this time slot" }
```

---

### GET `/api/v1/reservations/admin/all`

Returns all reservations, paginated, ordered by start time descending.

**Auth:** `VIEW_ALL_RESERVATIONS` permission

**Query params:** `page` (default 0), `size` (default 50)

**Response 200:** Paginated response with `content` array of reservation objects.

---

### GET `/api/v1/reservations/admin/equipment/{id}`

Returns all reservations for a specific piece of equipment, ordered by start time descending.

**Auth:** `VIEW_ALL_RESERVATIONS` permission

**Response 200:** Array of reservation objects.

---

## 7. Roles & Permissions (Admin)

### GET `/api/v1/admin/permissions`

Lists all available permission codes.

**Auth:** `MANAGE_ROLES` permission

**Response 200:**
```json
[
  "MANAGE_USERS",
  "MANAGE_ROLES",
  "MANAGE_EQUIPMENT",
  "VIEW_ALL_RESERVATIONS",
  "MANAGE_RESERVATIONS",
  "REGISTER_USERS"
]
```

---

### GET `/api/v1/admin/roles`

Lists all roles with their permission sets.

**Auth:** `MANAGE_ROLES` permission

**Response 200:**
```json
[
  {
    "code": "ADMIN",
    "description": "Full system administrator",
    "isSystem": true,
    "permissions": [
      "MANAGE_USERS",
      "MANAGE_ROLES",
      "MANAGE_EQUIPMENT",
      "VIEW_ALL_RESERVATIONS",
      "MANAGE_RESERVATIONS",
      "REGISTER_USERS"
    ]
  },
  {
    "code": "MEMBER",
    "description": "Standard makerspace member",
    "isSystem": true,
    "permissions": []
  }
]
```

---

### GET `/api/v1/admin/roles/{code}`

Returns a single role.

**Auth:** `MANAGE_ROLES` permission

**Response 200:** Single role object (same shape as above).

**Response 404:**
```json
{ "status": 404, "error": "Role not found" }
```

---

### POST `/api/v1/admin/roles`

Creates a custom role. System roles (`isSystem=true`) can only be created via Flyway migrations.

**Auth:** `MANAGE_ROLES` permission

**Request:**
```json
{
  "code": "VOLUNTEER",
  "description": "Volunteer with limited admin access",
  "permissions": ["VIEW_ALL_RESERVATIONS"]
}
```

`permissions` is optional; defaults to an empty set.

**Response 200:** Created role object.

**Response 409 — code already exists:**
```json
{ "status": 409, "error": "Role already exists" }
```

---

### PUT `/api/v1/admin/roles/{code}/permissions`

Replaces the full permission set for a role. Send an empty array to revoke all permissions. Takes effect within 30 seconds for already-authenticated users (cache TTL).

**Auth:** `MANAGE_ROLES` permission

**Request:**
```json
{
  "permissions": ["VIEW_ALL_RESERVATIONS", "MANAGE_RESERVATIONS"]
}
```

**Response 200:** Updated role object.

**Response 400 — unknown permission code:**
```json
{ "status": 400, "error": "Unknown permission: FAKE_PERMISSION" }
```

---

### DELETE `/api/v1/admin/roles/{code}`

Deletes a custom role. Cannot delete system roles or roles currently assigned to users.

**Auth:** `MANAGE_ROLES` permission

**Response 204:** No content.

**Response 409 — system role or in use:**
```json
{ "status": 409, "error": "Role is a system role and cannot be deleted" }
```

---

## 8. Error Responses

All error responses follow Spring's standard `ProblemDetail` / RFC 7807 shape:

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "End time must be after start time",
  "path": "/api/v1/reservations"
}
```

### Common status codes

| Code | Meaning |
|---|---|
| 400 | Bad request — validation failure or invalid input |
| 401 | Unauthenticated — missing or invalid Bearer token |
| 402 | Payment required — active membership needed to reserve equipment |
| 403 | Forbidden — authenticated but not authorized (or account closed) |
| 404 | Resource not found |
| 409 | Conflict — duplicate resource, reservation overlap, or constraint violation |
| 422 | Unprocessable — semantically invalid request (e.g. end before start) |
| 502 | Bad gateway — upstream Stripe error |
| 5xx | Server error — retry |

### Auth-specific errors

| Status | Body | Cause |
|---|---|---|
| 401 | `{ "error": "unknown_user" }` | JWT valid but user not found in DB |
| 403 | `{ "error": "account_closed" }` | User has been soft-deleted |
| 401 | `{ "error": "invalid_or_expired_code" }` | OTP wrong or expired (60s window) |

---

## Appendix: Permission Reference

| Code | What it gates |
|---|---|
| `MANAGE_USERS` | View user list, view any user, restore deleted users, soft-delete any user |
| `MANAGE_ROLES` | CRUD on roles and permission assignments, update any user's role |
| `MANAGE_EQUIPMENT` | Create, update, delete equipment |
| `VIEW_ALL_RESERVATIONS` | View all reservations, view any user's reservation by ID |
| `MANAGE_RESERVATIONS` | Extend any reservation, cancel any reservation |
| `REGISTER_USERS` | Pre-register users via the admin invite flow |

Default role → permission assignments (from V5 migration):

| Role | Permissions |
|---|---|
| `ADMIN` | All of the above |
| `STAFF` | MANAGE_USERS, MANAGE_EQUIPMENT, VIEW_ALL_RESERVATIONS, MANAGE_RESERVATIONS, REGISTER_USERS |
| `INSTRUCTOR` | VIEW_ALL_RESERVATIONS |
| `MEMBER` | (none — booking access comes from membership status) |
| `STUDENT` | (none) |
| `GUEST` | (none) |
| `RENTEE` | (none) |