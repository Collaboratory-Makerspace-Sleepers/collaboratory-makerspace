# Frontend <-> Backend Connection State

Each row describes a backend endpoint or feature and whether the frontend is connected to it.
"Connected" means the frontend actively calls the endpoint and uses the response.

Last updated: 2026-10-18 · Tests passing: 121

---

## Auth & Users

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/users/me` | `useDashboard.js`, `Account.jsx` | Connected |
| `PATCH /api/v1/users/me` | `Account.jsx` | Connected |
| `GET /api/v1/users` (admin) | — | Not connected |
| `GET /api/v1/users/{id}` (admin) | — | Not connected |
| `PATCH /api/v1/users/{id}/role` | — | Not connected |
| `DELETE /api/v1/users/{id}` | — | Not connected |
| `POST /api/v1/users/{id}/restore` | — | Not connected |
| OTP email sign-in | `SignIn.jsx`, `SignUp.jsx`, `EmailOtpForm.jsx` | Connected |
| OAuth (Google/Microsoft/Apple) | `OAuthButtons.jsx`, `OAuthCallback.jsx` | Connected |

---

## Equipment

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/equipment` | `RentEquipment.jsx` (filters out CLASS) | Connected |
| `GET /api/v1/equipment/{id}` | `ReserveEquipment.jsx` | Connected |
| `GET /api/v1/equipment/category/CLASS` | `BookClasses.jsx` | Connected |
| `GET /api/v1/equipment/status/{status}` | — | Not connected |
| `GET /api/v1/equipment/search` | — | Not connected — search is client-side filter in frontend |
| `POST /api/v1/equipment` (admin) | — | Not connected |
| `PUT /api/v1/equipment/{id}` (admin) | — | Not connected |
| `PATCH /api/v1/equipment/{id}/status` (admin) | — | Not connected |
| `DELETE /api/v1/equipment/{id}` (admin) | — | Not connected |

**Resolved:** `RentEquipment.jsx` displays live `hourlyRateCents` from API. `ReserveEquipment.jsx`
has a duration selector (1–4 hrs) and computes live cost. `ReservationCheckout.jsx` uses
`hourlyRateCents × durationHours` for price display.

**Remaining gap:** No "available slots" endpoint — calendar shows all time slots as bookable with no
conflict awareness (time-slot availability is a Phase 2 backend feature).

---

## Reservations

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/reservations` | `ReservationCheckout.jsx` | Connected |
| `GET /api/v1/reservations/me` | `useDashboard.js`, `MyReservations.jsx` | Connected |
| `GET /api/v1/reservations/me/{id}` | — | Not connected |
| `POST /api/v1/reservations/{id}/training-attest` | `MyReservations.jsx` (`TrainingAttestCard`) | Connected |
| `PATCH /api/v1/reservations/{id}/cancel` | `MyReservations.jsx` | Connected |
| `PATCH /api/v1/reservations/{id}/reschedule` | `MyReservations.jsx` | Connected |
| `PATCH /api/v1/reservations/{id}/extend` (staff) | — | Not connected |
| `GET /api/v1/reservations/admin/all` | — | Not connected |
| `GET /api/v1/reservations/admin/equipment/{id}` | — | Not connected |

**Resolved gaps:**
- `MyReservations.jsx` now has three buckets: `TRAINING_PENDING` (shows `TrainingAttestCard` with
  video link + checkbox + attest button), `ACTIVE` (reschedule/cancel), and past.
- `upcomingReservations()` in `useDashboard.js` includes `TRAINING_PENDING`.

**Remaining gap:** No time-slot availability check — `ReserveEquipment.jsx` shows all slots as
bookable; conflict is caught at booking time with a 409.

---

## Access Check (Staff / Kiosk)

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/access/check` | — | Not connected — staff/kiosk UI only |
| `POST /api/v1/hardware/checkin` | — | Hardware only — NFC reader calls this directly |

`POST /api/v1/access/check` — requires `MANAGE_RESERVATIONS` (JWT). Takes `{ userId, equipmentId }`, checks for an ACTIVE reservation in a 30-min window.

`POST /api/v1/hardware/checkin` — requires `X-Device-Key` header (no JWT). Takes `{ cardId }` (NFC UID), resolves to a user, grants entry if the user has a studio subscription, any active/grace membership, or an ACTIVE reservation in window. Writes an `access_events` audit row on every attempt.

---

## Training Waiver

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/training-waivers/{equipmentId}/sign` | `LaserWaiver.jsx` | Connected |

Per-reservation waiver signing is required for every booking. `LaserWaiver.jsx` posts
`{ signerName, startTime, endTime, waiverVersion }` and captures `waiverSignatureId` from the
response. That ID is passed through nav state to `ReservationCheckout.jsx`, which includes it in
the `POST /reservations` body. The backend validates version freshness (30-min window, must match
`equipment.currentWaiverVersion`) and links the waiver row to the created reservation atomically.

---

## Billing

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/billing/plans` | `Membership.jsx` | Connected |
| `POST /api/v1/billing/checkout-session` | `MembershipCheckout.jsx`, `ReservationCheckout.jsx` (day pass) | Connected |
| `POST /api/v1/billing/portal-session` | `Membership.jsx` | Connected |
| `POST /api/v1/billing/cancel` | `Membership.jsx` | Connected |
| `POST /api/v1/billing/payment-methods/setup` | `PaymentMethods.jsx` | Connected |
| `GET /api/v1/billing/payment-methods` | `PaymentMethods.jsx` | Connected |
| `DELETE /api/v1/billing/payment-methods/{pmId}` | `PaymentMethods.jsx` | Connected |
| `PATCH /api/v1/billing/payment-methods/{pmId}/set-default` | `PaymentMethods.jsx` | Connected |
| `GET /api/v1/billing/me/subscription` | `Membership.jsx`, `Account.jsx` | Connected |
| `GET /api/v1/billing/me/payments` | `BillingHistory.jsx` | Connected |

**Resolved gaps:**
- `Membership.jsx` now calls `GET /api/v1/billing/plans` and maps all 7 plan codes including
  `STUDIO_SMALL`, `STUDIO_MEDIUM`, `STUDIO_LARGE` via `PLAN_DETAILS`.
- ANNUAL description corrected (no longer mislabeled as studio plan).
- Day-pass checkout button in `ReservationCheckout.jsx` is now wired — calls
  `POST /billing/checkout-session` with `planCode: "DAY_PASS"`, saves pending reservation to
  sessionStorage, redirects to Stripe. On return, `BillingSuccess` shows "Continue reservation"
  and checkout restores state from sessionStorage.

**Remaining gap:** `MembershipCheckout.jsx` has no error handling for the `.edu` gate 403 on the STUDENT plan.

---

## Admin & Staff Features

None of the following backend capabilities have any frontend UI:

| Feature | Backend | Frontend |
|---|---|---|
| Admin registration / invite flow | `AdminRegistrationController`, `RegistrationClaimController` | None |
| Role management | `RoleController` | None |
| User management | `UserController` (MANAGE_USERS endpoints) | None |
| Equipment management | `EquipmentController` (write endpoints) | None |
| Staff reservation extension | `PATCH /{id}/extend` | None |
| Admin reservation view | `GET /admin/all`, `GET /admin/equipment/{id}` | None |
| Admin refund | Not yet built (Phase 4) | None |

---

## New Backend Features With No Frontend Yet

| Feature | Backend | Frontend |
|---|---|---|
| Studio assignment | Auto-assigned on STUDIO_* plan activation | No UI — user cannot see their assigned room |
| CLASS 50% member discount | `quotedRateCents = hourlyRate/2` for members | Rate in response; `BookClasses.jsx` shows "Free" (rate unconfirmed by client) |
| Student .edu gate | 403 returned if email doesn't end in `.edu` | No friendly error in `MembershipCheckout.jsx` |

---

## Stripe Event Handling (AWS Lambda -> Backend)

| Event | Backend handler | Notes |
|---|---|---|
| `customer.subscription.created/updated/deleted` | Full handler — upserts membership, handles studio assign/release | Live |
| `invoice.paid` | Creates payment record, sends receipt email | Live |
| `checkout.session.completed` | Records day-pass payment, promotes payment method | Live |
| `invoice.payment_failed` | Received but not handled (Phase 2 stub) | Pending |
| `charge.refunded` | Received but not handled (Phase 2 stub) | Pending |
| `charge.dispute.created` | Received but not handled (Phase 2 stub) | Pending |
| `customer.deleted` | Received but not handled (Phase 2 stub) | Pending |

---

## Infrastructure & Ops

| Item | Status | Notes |
|---|---|---|
| Flyway migrations (V1, V3–V10, V12–V14) | Done | V13: waiver versioning + video URL; V14: corrects Stripe IDs for MONTHLY/STUDENT/DAY_PASS; ANNUAL IDs still TODO |
| PostgreSQL (RDS) | Done | Connection in application.properties |
| Redis (OTP) | Done (config) | `REDIS_HOST` must be set in prod |
| AWS Lambda event consumers | Done | Deployed in lambdas/ directory |
| AWS SSM / IAM for Lambda | Done | Terraform in repo |
| SMTP transactional email | Done (config) | `MAIL_HOST` etc. must be set in prod |
| Stripe webhook registration | Partial | Must be registered in Stripe dashboard pointing to Lambda URL |
| Production secret rotation | Not started | All secrets are dev placeholders |