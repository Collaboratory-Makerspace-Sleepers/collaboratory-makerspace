# Build Progress

Status of every feature area across backend and frontend.

Legend: ✅ Done · ⚠️ Partial · 🔲 Not started

---

## Auth & Identity

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Email OTP sign-in | ✅ | ✅ | Redis required in prod |
| Google OAuth | ✅ | ✅ | Secret in env |
| Microsoft OAuth | ✅ | ✅ | Secret in env |
| Apple OAuth | ✅ | ✅ | Secret in env |
| JWT issuance + refresh | ✅ | ✅ | |
| Soft-delete account | ✅ | 🔲 | Endpoint exists, no "Delete account" UI |
| Admin invite / claim registration | ✅ | 🔲 | In-person flow backend done; no frontend |
| RBAC (roles + permissions) | ✅ | 🔲 | Full backend; no admin UI |

---

## User Profile

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| View own profile | ✅ | ✅ | Account page |
| Edit name | ✅ | ✅ | Account page |
| Change profile image | 🔲 | ⚠️ | Button renders but is a no-op |
| Admin: list all users | ✅ | 🔲 | |
| Admin: update user role | ✅ | 🔲 | |

---

## Equipment Catalog

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Browse equipment list | ✅ | ✅ | RentEquipment reads from API |
| Browse CLASS equipment | ✅ | ✅ | BookClasses reads from API |
| Equipment detail | ✅ | ✅ | ReserveEquipment reads from API |
| Display live hourly rate | ✅ | ⚠️ | API returns rate; frontend hardcodes $7/hr |
| Server-side search | ✅ | ⚠️ | Endpoint exists; frontend does client-side filter |
| Real-time availability (time slots) | 🔲 | ⚠️ | No "available slots" endpoint; calendar shows all slots |
| Admin: create/edit/delete equipment | ✅ | 🔲 | |
| Admin: update equipment status | ✅ | 🔲 | |

---

## Reservations

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Create reservation | ✅ | ✅ | Checkout page posts to API |
| Membership gate (402) | ✅ | ✅ | Checkout handles 402 → shows upgrade prompt |
| Day-pass checkout path | ✅ billing | ⚠️ | Button disabled "Coming soon" |
| Conflict detection | ✅ | ✅ | 409 handled client-side |
| View my reservations | ✅ | ✅ | MyReservations page |
| Cancel reservation | ✅ | ✅ | MyReservations page |
| Reschedule reservation | ✅ | ✅ | MyReservations page |
| Staff: extend reservation | ✅ | 🔲 | |
| Admin: view all reservations | ✅ | 🔲 | |
| Cost snapshot (quotedRate, estimatedCost) | ✅ | ⚠️ | Returned in API; frontend ignores, hardcodes $7/hr |
| Training prerequisite (TRAINING_PENDING) | ✅ | ⚠️ | Status returned; no attestation UI, falls into "past" bucket |
| Training attestation endpoint | ✅ | 🔲 | `POST /{id}/training-attest` built; no UI |
| Confirmation email | ✅ | — | Server-side via SMTP |

---

## Membership & Billing

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| List plans (API) | ✅ | ⚠️ | Endpoint exists; Membership.jsx hardcodes plans instead |
| Stripe checkout (subscription) | ✅ | ✅ | MembershipCheckout → Stripe redirect |
| Stripe checkout (day pass) | ✅ | ⚠️ | Backend handles it; frontend path disabled |
| View current membership | ✅ | ✅ | Membership + Account pages |
| Cancel at period end | ✅ | ✅ | Membership page |
| Stripe billing portal | ✅ | ✅ | Membership page |
| Payment methods (list/add/remove/default) | ✅ | ✅ | PaymentMethods page |
| Billing history | ✅ | ✅ | BillingHistory page |
| Student .edu gate | ✅ | ⚠️ | 403 returned; no friendly error in MembershipCheckout |
| Grace period + expiry sweep | ✅ | — | Server-side cron / Lambda |
| Admin refund endpoint | 🔲 | 🔲 | Config property set; endpoint not built |
| Webhook: subscription events | ✅ | — | Lambda → InternalBillingController |
| Webhook: invoice.paid | ✅ | — | |
| Webhook: invoice.payment_failed | ⚠️ stub | — | Phase 2 |
| Webhook: charge.refunded | ⚠️ stub | — | Phase 2 |
| Webhook: charge.dispute | ⚠️ stub | — | Phase 2 |

---

## Studio Rental Plans

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| STUDIO_SMALL/MEDIUM/LARGE plans (DB) | ✅ | 🔲 | V10 migration; Stripe IDs are placeholders |
| Studio auto-assign on subscription | ✅ | — | StripeEventService |
| Studio release on cancellation | ✅ | — | StripeEventService |
| Machine rate waiver for studio members | ✅ | ⚠️ | quotedRateCents=0 in API; frontend hardcodes $7/hr |
| Show assigned studio room to user | 🔲 endpoint | 🔲 | No GET endpoint for studio assignment; no UI |
| Studio plans visible in membership page | 🔲 | 🔲 | Membership.jsx hardcodes 4 plans |

---

## Training Flow

| Sub-feature | Backend | Frontend | Notes |
|---|---|---|---|
| Training completion table | ✅ | — | |
| TRAINING_PENDING status on first booking | ✅ | ⚠️ | Status in response; no UI for it |
| Online video attestation endpoint | ✅ | 🔲 | `POST /{id}/training-attest` |
| Subsequent bookings skip TRAINING_PENDING | ✅ | — | Checked via training_completion table |

---

## Admin Panel

No frontend exists for any admin/staff operations.

| Feature | Backend | Frontend |
|---|---|---|
| User management | ✅ | 🔲 |
| Role/permission management | ✅ | 🔲 |
| Equipment management | ✅ | 🔲 |
| Reservation oversight | ✅ | 🔲 |
| In-person registration | ✅ | 🔲 |
| Refund processing | 🔲 | 🔲 |

---

## Infrastructure & Ops

| Item | Status | Notes |
|---|---|---|
| Flyway migrations (V1–V10) | ✅ | Applied on startup |
| PostgreSQL (RDS) | ✅ | Connection in application.properties |
| Redis (OTP) | ✅ config | `REDIS_HOST` must be set in prod |
| AWS Lambda event consumers | ✅ | Deployed in lambdas/ directory |
| AWS SSM / IAM for Lambda | ✅ | Terraform in repo |
| SMTP transactional email | ✅ config | `MAIL_HOST` etc. must be set in prod |
| Stripe webhook registration | ⚠️ | Must be registered in Stripe dashboard pointing to Lambda URL |
| Production secret rotation | 🔲 | All secrets are dev placeholders |