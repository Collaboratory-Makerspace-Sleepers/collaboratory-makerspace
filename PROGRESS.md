# Build Progress

Status of every feature area across backend and frontend.

Legend: Done · Partial · Not started

Last updated: 2026-10-23 · Tests passing: 124 / 124

---


## Auth & Identity

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Email OTP sign-in | Done | Done | Redis required in prod |
| Google OAuth | Done | Done | Secret in env |
| Microsoft OAuth | Done | Done | Secret in env |
| Apple OAuth | Done | Done | Secret in env |
| JWT issuance + refresh | Done | Done | |
| Soft-delete account | Done | Done | Admin panel: Disable/Enable Member wired to DELETE/{id} and POST/{id}/restore |
| Admin invite / claim registration | Done | Done | `AdminRegistrationController` + `AdminUsers.jsx` create dialog; OTP invite flow |
| RBAC (roles + permissions) | Done | Done | Role picker in create-user dialog; `UserInformation.jsx` role checkboxes |

---

## User Profile

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| View own profile | Done | Done | Account page |
| Edit name | Done | Done | Account page |
| Change profile image | Not started | Partial | Button renders but is a no-op |
| Admin: list all users | Done | Done | `AllUsers.jsx` — list, search, delete, resend OTP |
| Admin: update user role | Done | Done | `UserInformation.jsx` role checkboxes → PATCH /{id}/role |

---

## Equipment Catalog

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Browse equipment list | Done | Done | RentEquipment reads from API |
| Browse CLASS equipment | Done | Done | BookClasses reads from API |
| Equipment detail | Done | Done | ReserveEquipment reads from API |
| Display live hourly rate | Done | Done | `RentEquipment`, `ReserveEquipment`, `ReservationCheckout` all use `hourlyRateCents` from API |
| Server-side search | Done | Partial | Endpoint exists; frontend does client-side filter |
| Real-time availability (time slots) | Done | Done | `GET /reservations/equipment/{id}/booked` + `ReserveEquipment.jsx` greys out conflicting slots |
| Admin: create/edit/delete equipment | Done | Done | `AdminRentEquipment.jsx`, `AllEquipment.jsx` — create/edit dialog + delete |
| Admin: update equipment status | Done | Done | Edit dialog in `AllEquipment.jsx` |

---

## Reservations

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Create reservation | Done | Done | Checkout page posts to API |
| Membership gate (402) | Done | Done | Checkout handles 402 -> shows upgrade prompt |
| Day-pass checkout path | Done | Done | `ReservationCheckout` wired to Stripe via `POST /billing/checkout-session`; sessionStorage preserves pending reservation through redirect |
| Conflict detection | Done | Done | 409 handled client-side |
| View my reservations | Done | Done | MyReservations page |
| Cancel reservation | Done | Done | MyReservations page |
| Reschedule reservation | Done | Done | MyReservations page |
| Staff: extend reservation | Done | Not started | |
| Admin: view all reservations | Done | Done | `ReservationsTable.jsx` → `GET /reservations/admin/all` |
| Cost snapshot (quotedRate, estimatedCost) | Done | Done | Displayed from API; `estimatedCostCents` used for checkout total |
| Training prerequisite (TRAINING_PENDING) | Done | Done | `TrainingAttestCard` in `MyReservations`; video link + checkbox + attest button |
| Training attestation endpoint | Done | Done | `POST /{id}/training-attest` connected in `MyReservations.jsx` |
| Confirmation email | Done | — | Server-side via SMTP |

---

## Membership & Billing

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| List plans (API) | Done | Done | `Membership.jsx` reads from `GET /billing/plans`; all 7 plan codes rendered |
| Stripe checkout (subscription) | Done | Done | MembershipCheckout -> Stripe redirect |
| Stripe checkout (day pass) | Done | Done | `ReservationCheckout` -> Stripe -> `BillingSuccess` -> resume checkout |
| View current membership | Done | Done | Membership + Account pages |
| Cancel at period end | Done | Done | Membership page |
| Stripe billing portal | Done | Done | Membership page |
| Payment methods (list/add/remove/default) | Done | Done | PaymentMethods page |
| Billing history | Done | Done | BillingHistory page |
| Student .edu gate | Done | Partial | 403 returned; no friendly error in MembershipCheckout |
| Grace period + expiry sweep | Done | — | Server-side cron / Lambda |
| Admin refund endpoint | Not started | Not started | Config property set; endpoint not built |
| Webhook: subscription events | Done | — | Lambda -> InternalBillingController |
| Webhook: invoice.paid | Done | — | |
| Webhook: invoice.payment_failed | Partial (stub) | — | Phase 2 |
| Webhook: charge.refunded | Partial (stub) | — | Phase 2 |
| Webhook: charge.dispute | Partial (stub) | — | Phase 2 |

---

## Studio Rental Plans

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| STUDIO_SMALL/MEDIUM/LARGE plans (DB) | Done | Done | V10 with real Stripe IDs; `Membership.jsx` renders all three plan cards |
| Studio auto-assign on subscription | Done | — | StripeEventService |
| Studio release on cancellation | Done | — | StripeEventService |
| Machine rate waiver for studio members | Done | Done | `quotedRateCents=0` in API; checkout displays 0 correctly |
| Show assigned studio room to user | Not started | Not started | No GET endpoint for studio assignment; no UI |

---

## Training Flow

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Training completion table | Done | — | `training_completion` table in migrations |
| TRAINING_PENDING status on first booking | Done | Done | `TrainingAttestCard` UI in `MyReservations.jsx` |
| Online video attestation endpoint | Done | Done | `POST /{id}/training-attest` connected; checkbox + button |
| Subsequent bookings skip TRAINING_PENDING | Done | — | Checked via training_completion table |
| Training waiver signing (per reservation) | Done | Done | `LaserWaiver.jsx` → RabbitSign staff-approval flow → poll status → `waiverSignatureId` → checkout |
| Waiver versioning (`currentWaiverVersion`) | Done | Done | V13 migration; Equipment model; version checked on sign + booking |

---

## Admin Panel

| Feature | Backend | Frontend |
|---|---|---|
| User management | Done | Done — `AllUsers.jsx` delete/resend; `AdminUsers.jsx` create dialog |
| Role/permission management | Done | Done — role picker in create-user dialog; `UserInformation.jsx` checkboxes |
| Equipment management | Done | Done — `AdminRentEquipment.jsx` create/edit/delete; `AllEquipment.jsx` |
| Reservation oversight | Done | Done — `ReservationsTable.jsx` (`GET /reservations/admin/all`) |
| In-person registration | Done | Done — `AdminUsers.jsx` create dialog with OTP invite flow |
| Hardware access / NFC | Done | Done — `HardwareCheckIns.jsx`, `CardManager.jsx`, `CardScansLive.jsx` |
| Waiver approval workflow | Done | Done — `HardwareCheckIns.jsx` + `WaiverStatusPanel.jsx` |
| Activity feed | Done | Done — `RecentActivity.jsx` → `GET /users/activity/feed` |
| Refund processing | Not started | Not started |

---

## Hardware & NFC Access

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| NFC check-in endpoint | Done | — | `POST /hardware/checkin` via `X-Device-Key`; writes `hardware_checkins` row |
| Waiver-required workflow | Done | Done | `HardwareCheckInController` + `HardwareCheckInService`; RabbitSign staff approval |
| Waiver PDF generation + email | Done | — | `WaiverPdfService` (OpenPDF); emailed to member on sign |
| Member card assignment | Done | Done | `CardController`; `CardManager.jsx` in admin panel |
| Live card-scan feed | Done | Done | `CardScansLive.jsx` polls `GET /hardware/card-scans/recent` |
| Staff waiver approval | Done | Done | `HardwareCheckIns.jsx` approve/deny; `WaiverStatusPanel.jsx` for member |
| Signed document upload | Done | — | `POST /hardware/checkin/{id}/document` accepts multipart PDF |

---

## Infrastructure & Ops

| Item | Status | Notes |
|---|---|---|
| Flyway migrations (V1–V22) | Done | V19: class_sessions; V20: hardware_checkins; V21: member_cards; V22: waiver_documents |
| PostgreSQL (RDS) | Done | Connection in application.properties |
| Redis (OTP) | Done (config) | `REDIS_HOST` must be set in prod |
| AWS Lambda event consumers | Done | Deployed in lambdas/ directory |
| AWS SSM / IAM for Lambda | Done | Terraform in repo |
| SMTP transactional email | Done (config) | `MAIL_HOST` etc. must be set in prod |
| Stripe webhook registration | Partial | Must be registered in Stripe dashboard pointing to Lambda URL (REQUIRED.md item 6) |
| Production secret rotation | Not started | All secrets are dev placeholders (REQUIRED.md item 7) |