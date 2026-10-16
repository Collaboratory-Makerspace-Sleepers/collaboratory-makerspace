# Required Before Go-Live

Items that are blocked, incorrect, or missing that must be resolved before deploying to production.

---

## 1. Stripe IDs — Studio Plans (BLOCKING)

V10 migration inserts studio plans with placeholder Stripe IDs. The migration cannot be applied to a live database until real IDs exist.

**Action:** In the Stripe dashboard, create three products with monthly recurring prices:

| Plan code       | Display name    | Price (monthly) |
|-----------------|-----------------|-----------------|
| `STUDIO_SMALL`  | Small Studio    | $350.00         |
| `STUDIO_MEDIUM` | Medium Studio   | $750.00         |
| `STUDIO_LARGE`  | Large Studio    | $1,200.00       |

Then replace in `V10__studio_training_student.sql`:

```sql
'PLACEHOLDER_PRICE_STUDIO_SMALL'   → real price ID
'PLACEHOLDER_PRODUCT_STUDIO_SMALL' → real product ID
-- (repeat for MEDIUM and LARGE)
```

---

## 2. Stripe IDs — Existing Plans: Amounts Don't Match Frontend (BLOCKING)

V6 migration has real-looking Stripe price IDs but the seeded `amount_cents` values do not match what the frontend displays to users:

| Plan      | DB (V6)       | Frontend shows |
|-----------|---------------|----------------|
| MONTHLY   | $50.00        | $95/month      |
| ANNUAL    | $500.00       | (not shown separately) |
| STUDENT   | $25.00 → $49 (V10) | $45/month |
| DAY_PASS  | $15.00        | $25            |

**Action:** Verify the Stripe prices behind `price_1UFR2y...` etc. match the intended amounts. If not, create new prices in Stripe and update the `stripe_price_id` column. The `amount_cents` column in the DB is for display only — Stripe is the source of truth for what gets charged, so mismatches are a billing error risk.

---

## 3. V6 Product ID Placeholders (BLOCKING)

V6 uses `prod_PLACEHOLDER_MONTHLY` etc. for `stripe_product_id`. These are NOT placeholder-safe — if any code reads `stripe_product_id` they will fail or mislead.

**Action:** Replace with real Stripe product IDs, or run a corrective migration after V6 executes:
```sql
UPDATE membership_plan SET stripe_product_id = 'prod_REAL_ID' WHERE code = 'MONTHLY';
-- etc.
```

---

## 4. CLASS Equipment Rates — Pending Client Confirmation (BLOCKING for billing)

V10 seeds CLASS equipment at `$20/hr` (2000 cents) as a placeholder. Members get 50% off ($10/hr), non-members pay full rate. Backend enforces this but the frontend hardcodes "Free" on the BookClasses page.

**Action:** Confirm with client. Update the seed if the rate differs. Update `BookClasses.jsx` to read `hourlyRateCents` from the equipment API response.

---

## 5. Membership Prices — Final Sign-Off (BLOCKING for billing)

The Membership.jsx page still hardcodes plan prices as static text (not read from the backend `/api/v1/billing/plans` endpoint). The displayed prices need to match Stripe exactly.

**Action:**
1. Wire `Membership.jsx` to call `GET /api/v1/billing/plans` and render prices from the response.
2. STUDIO_SMALL/MEDIUM/LARGE plans must appear once their Stripe IDs are live.

---

## 6. Stripe Webhook Secret + Production Endpoint (BLOCKING)

The Lambda consumer ingests Stripe events and forwards them to `InternalBillingController`. The webhook secret and production URL must be registered in the Stripe dashboard.

**Action:**
- Register the production webhook endpoint in Stripe → Developers → Webhooks.
- Add `STRIPE_WEBHOOK_SECRET` to production secrets/SSM.
- Verify the Lambda is deployed and connected.

---

## 7. Environment Secrets (BLOCKING)

The following env vars are required and not checked in:

| Variable | Purpose |
|---|---|
| `APP_JWT_SECRET` | JWT signing — must be a long random value in prod |
| `STRIPE_SECRET_KEY` | Stripe API auth |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook verification |
| `OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | Google OAuth |
| `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET` | Microsoft OAuth |
| `APPLE_CLIENT_ID` / `APPLE_CLIENT_SECRET` | Apple OAuth |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP for transactional email |
| `REDIS_HOST` | OTP storage |

---

## 8. Training Attestation — Frontend Not Built

Backend endpoint `POST /api/v1/reservations/{id}/training-attest` is live. When a user books a training-required machine for the first time, the reservation is created with status `TRAINING_PENDING` and the user must confirm they watched the video to activate it.

**Action:** Build a UI component that:
1. Detects `status === "TRAINING_PENDING"` in `MyReservations.jsx`.
2. Shows an attestation prompt ("I have watched the training video for {equipment}").
3. Calls `POST /api/v1/reservations/{id}/training-attest` on confirmation.
4. Refreshes the reservation to show `ACTIVE`.

Currently, TRAINING_PENDING reservations are invisible in the "active" list because `useDashboard.upcomingReservations` filters for `status === "ACTIVE"` only.

---

## 9. Equipment Pricing — Frontend Hardcodes $7/hr

`RentEquipment.jsx` and `ReserveEquipment.jsx` both hardcode `$7/hr`. `ReservationCheckout.jsx` hardcodes `HOURLY_RATE = 7`. The backend now returns `quotedRateCents` and `estimatedCostCents` on the reservation response.

**Action:** Replace the hardcoded constants with values from:
- `equipment.hourlyRateCents` (for display on the browse/detail pages)
- `reservation.quotedRateCents` and `reservation.estimatedCostCents` (on the checkout confirmation screen)

---

## 10. Refund Endpoint — Phase 4 (Not Started)

`app.billing.refund-window-hours=48` is configured. The admin refund endpoint that reads this config has not been built.

**Action:** Build `POST /api/v1/admin/billing/refund` when Phase 4 begins. This is not blocking launch if refunds are handled manually in the Stripe dashboard for now.

---

## 11. Day-Pass Checkout Disabled

`ReservationCheckout.jsx` shows "Pay ${price}" as a disabled button with "Coming soon" for users without a membership. The day-pass checkout flow exists in `BillingService` but is not wired into the reservation flow.

**Action:** Connect the day-pass button to `POST /api/v1/billing/checkout-session` with `planCode: "DAY_PASS"`. After checkout success, redirect back to the reservation and automatically create it.
