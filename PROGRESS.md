# Build Progress

Status of every feature area across backend and frontend.

Legend: Done · Partial · Not started

Last updated: 2026-10-18 · Tests passing: 121 / 121

---


## Auth & Identity

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Email OTP sign-in | Done | Done | Redis required in prod |
| Google OAuth | Done | Done | Secret in env |
| Microsoft OAuth | Done | Done | Secret in env |
| Apple OAuth | Done | Done | Secret in env |
| JWT issuance + refresh | Done | Done | |
| Soft-delete account | Done | Not started | Endpoint exists, no "Delete account" UI |
| Admin invite / claim registration | Done | Not started | In-person flow backend done; no frontend |
| RBAC (roles + permissions) | Done | Not started | Full backend; no admin UI |

---

## User Profile

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| View own profile | Done | Done | Account page |
| Edit name | Done | Done | Account page |
| Change profile image | Not started | Partial | Button renders but is a no-op |
| Admin: list all users | Done | Not started | |
| Admin: update user role | Done | Not started | |

---

## Equipment Catalog

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Browse equipment list | Done | Done | RentEquipment reads from API |
| Browse CLASS equipment | Done | Done | BookClasses reads from API |
| Equipment detail | Done | Done | ReserveEquipment reads from API |
| Display live hourly rate | Done | Done | `RentEquipment`, `ReserveEquipment`, `ReservationCheckout` all use `hourlyRateCents` from API |
| Server-side search | Done | Partial | Endpoint exists; frontend does client-side filter |
| Real-time availability (time slots) | Not started | Partial | No "available slots" endpoint; calendar shows all slots |
| Admin: create/edit/delete equipment | Done | Not started | |
| Admin: update equipment status | Done | Not started | |

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
| Admin: view all reservations | Done | Not started | |
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
| Training waiver signing (per reservation) | Done | Done | `LaserWaiver.jsx` → sign → `waiverSignatureId` → checkout; version + time validation in backend |
| Waiver versioning (`currentWaiverVersion`) | Done | Done | V13 migration; Equipment model; version checked on sign + booking |

---

## Admin Panel

No frontend exists for any admin/staff operations.

| Feature | Backend | Frontend |
|---|---|---|
| User management | Done | Not started |
| Role/permission management | Done | Not started |
| Equipment management | Done | Not started |
| Reservation oversight | Done | Not started |
| In-person registration | Done | Not started |
| Refund processing | Not started | Not started |

---

## Infrastructure & Ops

| Item | Status | Notes |
|---|---|---|
| Flyway migrations (V1, V3–V10, V12–V15) | Done | V13: waiver versioning + video URL; V14: Stripe ID corrections; V15: nfc_card_id + access_events |
| PostgreSQL (RDS) | Done | Connection in application.properties |
| Redis (OTP) | Done (config) | `REDIS_HOST` must be set in prod |
| AWS Lambda event consumers | Done | Deployed in lambdas/ directory |
| AWS SSM / IAM for Lambda | Done | Terraform in repo |
| SMTP transactional email | Done (config) | `MAIL_HOST` etc. must be set in prod |
| Stripe webhook registration | Partial | Must be registered in Stripe dashboard pointing to Lambda URL (REQUIRED.md item 6) |
| Production secret rotation | Not started | All secrets are dev placeholders (REQUIRED.md item 7) |