# Frontend ↔ Backend Connection State

Each row describes a backend endpoint or feature and whether the frontend is connected to it. "Connected" means the frontend actively calls the endpoint and uses the response.

---

## Auth & Users

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/users/me` | `useDashboard.js`, `Account.jsx` | ✅ Connected |
| `PATCH /api/v1/users/me` | `Account.jsx` | ✅ Connected |
| `GET /api/v1/users` (admin) | — | ❌ Not connected |
| `GET /api/v1/users/{id}` (admin) | — | ❌ Not connected |
| `PATCH /api/v1/users/{id}/role` | — | ❌ Not connected |
| `DELETE /api/v1/users/{id}` | — | ❌ Not connected |
| `POST /api/v1/users/{id}/restore` | — | ❌ Not connected |
| OTP email sign-in | `SignIn.jsx`, `SignUp.jsx`, `EmailOtpForm.jsx` | ✅ Connected |
| OAuth (Google/Microsoft/Apple) | `OAuthButtons.jsx`, `OAuthCallback.jsx` | ✅ Connected |

---

## Equipment

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/equipment` | `RentEquipment.jsx` (filters out CLASS) | ✅ Connected |
| `GET /api/v1/equipment/{id}` | `ReserveEquipment.jsx` | ✅ Connected |
| `GET /api/v1/equipment/category/CLASS` | `BookClasses.jsx` | ✅ Connected |
| `GET /api/v1/equipment/status/{status}` | — | ❌ Not connected |
| `GET /api/v1/equipment/search` | — | ⚠️ Not connected — search is client-side filter |
| `POST /api/v1/equipment` (admin) | — | ❌ Not connected |
| `PUT /api/v1/equipment/{id}` (admin) | — | ❌ Not connected |
| `PATCH /api/v1/equipment/{id}/status` (admin) | — | ❌ Not connected |
| `DELETE /api/v1/equipment/{id}` (admin) | — | ❌ Not connected |

**Gap:** `equipment.hourlyRateCents` is in the API response but `RentEquipment.jsx`, `ReserveEquipment.jsx`, and `ReservationCheckout.jsx` all hardcode `$7/hr` / `HOURLY_RATE = 7`.

---

## Reservations

| Endpoint | Frontend component | Status |
|---|---|---|
| `POST /api/v1/reservations` | `ReservationCheckout.jsx` | ✅ Connected |
| `GET /api/v1/reservations/me` | `useDashboard.js`, `MyReservations.jsx` | ✅ Connected |
| `GET /api/v1/reservations/me/{id}` | — | ❌ Not connected |
| `POST /api/v1/reservations/{id}/training-attest` | — | ❌ Not connected — no UI built |
| `PATCH /api/v1/reservations/{id}/cancel` | `MyReservations.jsx` | ✅ Connected |
| `PATCH /api/v1/reservations/{id}/reschedule` | `MyReservations.jsx` | ✅ Connected |
| `PATCH /api/v1/reservations/{id}/extend` (staff) | — | ❌ Not connected |
| `GET /api/v1/reservations/admin/all` | — | ❌ Not connected |
| `GET /api/v1/reservations/admin/equipment/{id}` | — | ❌ Not connected |

**Gaps:**
- `MyReservations.jsx` splits on `status === "ACTIVE"`. `TRAINING_PENDING` reservations fall into "past" bucket and are shown as inactive. They need their own UI section with the training attestation flow.
- `upcomingReservations()` in `useDashboard.js` also filters `status === "ACTIVE"` only, so the dashboard home and other pages won't show TRAINING_PENDING reservations in the upcoming count.
- No time-slot availability check: `ReserveEquipment.jsx` shows all fixed time slots as available. There is no endpoint to query which slots are already taken for a given equipment + day, so the calendar doesn't grey out booked times.

---

## Billing

| Endpoint | Frontend component | Status |
|---|---|---|
| `GET /api/v1/billing/plans` | — | ❌ Not connected — `Membership.jsx` hardcodes plan list |
| `POST /api/v1/billing/checkout-session` | `MembershipCheckout.jsx` | ✅ Connected |
| `POST /api/v1/billing/portal-session` | `Membership.jsx` | ✅ Connected |
| `POST /api/v1/billing/cancel` | `Membership.jsx` | ✅ Connected |
| `POST /api/v1/billing/payment-methods/setup` | `PaymentMethods.jsx` | ✅ Connected |
| `GET /api/v1/billing/payment-methods` | `PaymentMethods.jsx` | ✅ Connected |
| `DELETE /api/v1/billing/payment-methods/{pmId}` | `PaymentMethods.jsx` | ✅ Connected |
| `PATCH /api/v1/billing/payment-methods/{pmId}/set-default` | `PaymentMethods.jsx` | ✅ Connected |
| `GET /api/v1/billing/me/subscription` | `Membership.jsx`, `Account.jsx` | ✅ Connected |
| `GET /api/v1/billing/me/payments` | `BillingHistory.jsx` | ✅ Connected |

**Gaps:**
- `Membership.jsx` hardcodes 4 plan cards (MONTHLY, ANNUAL, STUDENT, DAY_PASS). The three new STUDIO_* plans are not shown. The page should call `GET /api/v1/billing/plans` and render dynamically.
- `Membership.jsx` displays ANNUAL as "Member with Private Studio Space" — this is incorrect. ANNUAL is a time-based discount; STUDIO_* plans are the studio offering.
- `MembershipCheckout.jsx` uses `plan.price` from the hardcoded frontend constant rather than `plan.amountCents` from the API.
- Day-pass checkout button in `ReservationCheckout.jsx` is disabled ("Coming soon") — the billing flow exists but is not wired to the reservation flow.

---

## Admin & Staff Features

None of the following backend capabilities have any frontend UI:

| Feature | Backend | Frontend |
|---|---|---|
| Admin registration / invite flow | `AdminRegistrationController`, `RegistrationClaimController` | ❌ None |
| Role management | `RoleController` | ❌ None |
| User management | `UserController` (MANAGE_USERS endpoints) | ❌ None |
| Equipment management | `EquipmentController` (write endpoints) | ❌ None |
| Staff reservation extension | `PATCH /{id}/extend` | ❌ None |
| Admin reservation view | `GET /admin/all`, `GET /admin/equipment/{id}` | ❌ None |
| Admin refund | Not yet built (Phase 4) | ❌ None |

---

## New Backend Features With No Frontend Yet

These were built in the current or recent session and have no frontend connection:

| Feature | Backend | Frontend |
|---|---|---|
| Training attestation | `POST /api/v1/reservations/{id}/training-attest` | ❌ No UI |
| TRAINING_PENDING reservation status | `ReservationStatus.TRAINING_PENDING`, status returned in `ReservationDTO` | ⚠️ Status received but treated as "past" |
| Studio assignment | Auto-assigned on STUDIO_* plan activation | ❌ No UI — user can't see their assigned room |
| Machine rate waiver (studio members) | `quotedRateCents = 0` for studio members | ⚠️ Rate in response but frontend hardcodes $7/hr |
| CLASS 50% member discount | `quotedRateCents = hourlyRate/2` for members | ⚠️ Rate in response but `BookClasses.jsx` shows "Free" |
| Student .edu gate | 403 returned if email doesn't end in `.edu` | ❌ No error handling in checkout |

---

## Stripe Event Handling (AWS Lambda → Backend)

| Event | Backend handler | Notes |
|---|---|---|
| `customer.subscription.created/updated/deleted` | ✅ Full handler — upserts membership, handles studio assign/release | Live |
| `invoice.paid` | ✅ Creates payment record, sends receipt email | Live |
| `checkout.session.completed` | ✅ Records day-pass payment, promotes payment method | Live |
| `invoice.payment_failed` | ⚠️ Received but not handled (Phase 2 stub) | Pending |
| `charge.refunded` | ⚠️ Received but not handled (Phase 2 stub) | Pending |
| `charge.dispute.created` | ⚠️ Received but not handled (Phase 2 stub) | Pending |
| `customer.deleted` | ⚠️ Received but not handled (Phase 2 stub) | Pending |