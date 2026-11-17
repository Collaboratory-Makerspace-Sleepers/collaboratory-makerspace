# Frontend <-> Backend Connection State

Each row describes a backend endpoint or feature and whether the frontend is connected to it.
"Connected" means the frontend actively calls the endpoint and uses the response.

Last updated: 2026-10-23 · Tests passing: 124

---

## Auth & Users

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/users/me` | `useDashboard.js`, `Account.jsx` | Connected |
| `PATCH /api/v1/users/me` | `Account.jsx` | Connected |
| `GET /api/v1/users` (admin) | `AllUsers.jsx` | Connected — list, search, delete, resend OTP |
| `GET /api/v1/users/{id}` (admin) | `UserInformation.jsx` | Connected |
| `PATCH /api/v1/users/{id}/role` | `UserInformation.jsx` | Connected — role checkboxes; saves `{ roleCodes }` |
| `DELETE /api/v1/users/{id}` | `UserInformation.jsx` | Connected — "Disable Member" soft-deletes user |
| `POST /api/v1/users/{id}/restore` | `UserInformation.jsx` | Connected — "Enable Member" restores soft-deleted user |
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
| `POST /api/v1/equipment` (admin) | `AdminRentEquipment.jsx` | Connected |
| `PUT /api/v1/equipment/{id}` (admin) | `AllEquipment.jsx` | Connected |
| `PATCH /api/v1/equipment/{id}/status` (admin) | `AllEquipment.jsx` | Connected |
| `DELETE /api/v1/equipment/{id}` (admin) | `AllEquipment.jsx` | Connected |

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
| `GET /api/v1/reservations/admin/all` | `ReservationsTable.jsx` | Connected |
| `GET /api/v1/reservations/admin/equipment/{id}` | — | Not connected |
| `GET /api/v1/reservations/in-use-now` | `useInUseNow.js` | Connected — 15s poll; highlights active equipment |
| `GET /api/v1/reservations/equipment/{id}/booked` | `ReserveEquipment.jsx` | Connected — greys out booked time slots |

**Resolved gaps:**
- `MyReservations.jsx` now has three buckets: `TRAINING_PENDING` (shows `TrainingAttestCard` with
  video link + checkbox + attest button), `ACTIVE` (reschedule/cancel), and past (COMPLETED/CANCELLED).
- `upcomingReservations()` in `useDashboard.js` includes `TRAINING_PENDING`.
- `ReservationsTable.jsx` shows all reservations in admin panel.
- `ReserveEquipment.jsx` fetches booked ranges and greys out conflicting time slots.
- 15-second auto-refresh in `MyReservations.jsx` and `useDashboard.js` shows COMPLETED status live.

---

## Access Check (Staff / Kiosk)

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/access/check` | — | Not connected — staff/kiosk UI only |
| `POST /api/v1/hardware/checkin` | NFC hardware | Hardware only — `X-Device-Key` auth |
| `POST /api/v1/hardware/card-scan` | NFC hardware | Hardware only — card tap event |
| `GET /api/v1/hardware/waiver-requests` | `HardwareCheckIns.jsx` | Connected — admin pending waiver list |
| `POST /api/v1/hardware/checkin/{id}/approve` | `HardwareCheckIns.jsx` | Connected |
| `POST /api/v1/hardware/checkin/{id}/deny` | `HardwareCheckIns.jsx` | Connected |
| `POST /api/v1/hardware/checkin/{id}/sign` | `LaserWaiver.jsx` | Connected — RabbitSign flow |
| `GET /api/v1/hardware/cards` | `CardManager.jsx` | Connected — admin card list |
| `POST /api/v1/hardware/cards` | `CardManager.jsx` | Connected — assign card |
| `GET /api/v1/hardware/card-scans/recent` | `CardScansLive.jsx` | Connected — live feed |
| `GET /api/v1/users/activity/feed` | `RecentActivity.jsx` | Connected — admin home activity feed |

---

## Training Waiver

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/training-waivers/{equipmentId}/sign` | `LaserWaiver.jsx` | Connected |
| `POST /api/v1/hardware/checkin/{id}/sign` | `LaserWaiver.jsx` (RabbitSign path) | Connected |
| `POST /api/v1/hardware/checkin/{id}/approve` | `HardwareCheckIns.jsx` | Connected |

`LaserWaiver.jsx` now uses a RabbitSign staff-approval workflow: member signs digitally → backend
creates a `hardware_checkins` row in WAIVER_SIGNED status → staff approves in `HardwareCheckIns.jsx`
→ frontend polls status → on WAIVER_APPROVED, passes `waiverSignatureId` to `ReservationCheckout.jsx`.
`WaiverStatusPanel.jsx` shows pending waiver banners on `DashboardHome.jsx` and `RentEquipment.jsx`.

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

| Feature | Backend | Frontend |
|---|---|---|
| Admin registration / invite flow | `AdminRegistrationController`, `RegistrationClaimController` | `AdminUsers.jsx` create dialog |
| Role management | `RoleController` | `UserInformation.jsx` role checkboxes |
| User management | `UserController` | `AllUsers.jsx`, `AdminUsers.jsx` |
| Equipment management | `EquipmentController` (write) | `AdminRentEquipment.jsx`, `AllEquipment.jsx` |
| Staff reservation extension | `PATCH /{id}/extend` | Not connected |
| Admin reservation view | `ReservationsTable.jsx` | `GET /admin/all` connected |
| Hardware check-in / waiver management | `HardwareCheckInController`, `CardController` | `HardwareCheckIns.jsx`, `CardManager.jsx`, `CardScansLive.jsx` |
| Admin refund | Not yet built (Phase 4) | None |

---

## New Backend Features With No Frontend Yet

| Feature | Backend | Frontend |
|---|---|---|
| Studio assignment | Auto-assigned on STUDIO_* plan activation | No UI — user cannot see their assigned room |
| CLASS 50% member discount | `quotedRateCents = hourlyRate/2` for members | Rate in response; `BookClasses.jsx` shows "Free" (rate unconfirmed by client) |
| Student .edu gate | 403 returned if email doesn't end in `.edu` | No friendly error in `MembershipCheckout.jsx` |
| Day-pass alternative to membership gate | `hasRecentDayPass()` in `ReservationService` | Handled via `ReservationCheckout.jsx` day-pass button |

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
| Flyway migrations (V1–V22) | Done | V20: hardware_checkins; V21: member_cards/card_scans; V22: waiver_documents |
| PostgreSQL (RDS) | Done | Connection in application.properties |
| Redis (OTP) | Done (config) | `REDIS_HOST` must be set in prod |
| AWS Lambda event consumers | Done | Deployed in lambdas/ directory |
| AWS SSM / IAM for Lambda | Done | Terraform in repo |
| SMTP transactional email | Done (config) | `MAIL_HOST` etc. must be set in prod |
| Stripe webhook registration | Partial | Must be registered in Stripe dashboard pointing to Lambda URL |
| Production secret rotation | Not started | All secrets are dev placeholders |