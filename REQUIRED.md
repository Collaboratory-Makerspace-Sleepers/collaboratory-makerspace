# Required Before Go-Live

Items that are blocked, incorrect, or missing that must be resolved before deploying to production.

---

## 0. ~~Duplicate Flyway Migration Versions~~ — RESOLVED

The duplicate V9/V10/V11 migration version conflicts from the merge have been fixed:

| Action taken | File |
|---|---|
| Deleted | `V9__training_tasks.sql` — obsolete; `TrainingTask` model was removed |
| Deleted | `V11__pending_reservation_status.sql` — referenced non-existent `reservation_statuses` table; used wrong status `'PENDING'` |
| Renamed to V12 | `V10__training_waiver_signatures.sql` → `V12__training_waiver_signatures.sql` |

Effective migration sequence is now: V1, V3, V4, V5, V6, V7, V8, V9, V10, V12. All 121 tests pass.

---

## 0b. ~~Training + Waiver — Full Redesign~~ — RESOLVED

The current training/waiver implementation does not match the confirmed requirements. The design
below replaces both the waiver module and the attestation flow.

### Confirmed flow

```
1. User selects equipment + time window (frontend)
2. Frontend calls POST /training-waivers/{equipmentId}/sign
       body: { signerName, startTime, endTime, waiverVersion }
       response: { waiverSignatureId }
   — reservation_id on the signature row is NULL at this point
3. User reviews / pays (Stripe checkout if applicable)
4. Frontend calls POST /reservations
       body: { equipmentId, startTime, endTime, waiverSignatureId }
   — backend validates the waiver, creates the reservation, and writes
     reservation_id back onto the waiver row in the same transaction
5. If equipment.requiresTraining and no training_completion exists:
       reservation.status = TRAINING_PENDING
       user must watch the training video and call POST /{id}/training-attest
6. Else: reservation.status = ACTIVE
7. Building access check (see item 0c) verifies ACTIVE + time window
```

### Waivers are signed per reservation, every time

A waiver must be signed for **every machine, every booking** — not just on first use. This ensures
users always sign the current waiver version. There is no "sign once" exemption.

`ReservationService.create()` must:
- Require `waiverSignatureId` in `CreateReservationRequest` (non-null for all equipment)
- Verify the waiver row exists, belongs to this user and equipment, has no `reservation_id` yet
  (not already used for another booking), and was signed within an acceptable window (e.g. 30 min)
- Throw `409 CONFLICT` if the waiver version on the signature does not match
  `equipment.currentWaiverVersion` — forcing the user to re-sign before proceeding

### Waiver versioning

Add `current_waiver_version` (integer, default 1) to the `equipment` table. Staff increments it
when the waiver text changes. Each `training_waiver_signatures` row stores the version that was
signed. If the version on the stored signature does not match `equipment.currentWaiverVersion` at
booking time, the booking is rejected.

### Schema changes needed (new migration V13)

**`equipment` table — add columns:**
```sql
training_video_url      TEXT,
current_waiver_version  INT NOT NULL DEFAULT 1
```

**`training_waiver_signatures` table — alter:**
```sql
-- Add columns
reservation_id   BIGINT REFERENCES reservations(id),
waiver_version   INT NOT NULL DEFAULT 1,
start_time       TIMESTAMPTZ NOT NULL,
end_time         TIMESTAMPTZ NOT NULL,

-- Drop old unique constraint (was per user+equipment; now per reservation)
DROP CONSTRAINT uk_training_waiver_user_equipment,

-- New unique constraint: one waiver per reservation (once linked)
-- Partial unique on non-null reservation_id handled in application logic
```

### Code changes needed

| File | Change |
|---|---|
| `model/Equipment.java` | Add `trainingVideoUrl`, `currentWaiverVersion` fields |
| `model/TrainingWaiverSignature.java` | Add `reservation`, `waiverVersion`, `startTime`, `endTime` fields; remove UNIQUE constraint annotation |
| `repository/TrainingWaiverSignatureRepository.java` | Add `findByIdAndUserIdAndReservationIdIsNull` for validation at booking |
| `controller/dto/SignTrainingWaiverRequest.java` | Add `startTime`, `endTime`, `waiverVersion` fields |
| `controller/dto/CreateReservationRequest.java` | Add required `waiverSignatureId` field |
| `services/TrainingWaiverService.sign()` | Store `startTime`, `endTime`, `waiverVersion`; validate version matches equipment |
| `services/ReservationService.create()` | Validate waiver, create reservation, link `reservation_id` on waiver row in same transaction |
| `db/migration/V13__waiver_versioning_video_url.sql` | Done |

---

## 0c. ~~Building Access Check Endpoint~~ — RESOLVED

`POST /api/v1/access/check` is live. Requires `MANAGE_RESERVATIONS` (staff / kiosk account).

Request: `{ userId, equipmentId }`
Response: `{ granted, reason, reservationId }`

Logic:
1. Looks for an `ACTIVE` reservation for the user + equipment whose window overlaps `[now, now+30min)` (30-minute early entry).
2. If none found, checks for `TRAINING_PENDING` in the same window to return a specific denial message.
3. If an ACTIVE reservation exists, verifies membership is still valid (`hasActiveMembership || hasMachineRateWaiver`).

---

## 1. ~~Stripe IDs — Studio Plans~~ — RESOLVED

V10 updated with real Stripe IDs:
- STUDIO_SMALL: `price_1URzLjCqDStEDHd02IIYc61m` / `prod_VSvC52zx4mE6w5`
- STUDIO_MEDIUM: `price_1URzNOCqDStEDHd0gwqul6ah` / `prod_VSvDlFs86lYspI`
- STUDIO_LARGE: `price_1URzNkCqDStEDHd0LxukmIP0` / `prod_VSvEK1PMQOUHJz`

Also fixed: V8 used `STUDIO_MED` as category for medium studios, which would never match plan
code `STUDIO_MEDIUM` in `StripeEventService.assignStudioRoom()`. Corrected to `STUDIO_MEDIUM`.

---

## 2. ~~Stripe IDs — Existing Plans~~ — RESOLVED (ANNUAL pending)

V14 corrective migration sets real Stripe IDs and correct amounts for MONTHLY, STUDENT, DAY_PASS:

| Plan     | amount_cents | stripe_price_id                    | stripe_product_id    |
|----------|--------------|------------------------------------|----------------------|
| MONTHLY  | 9500         | price_1URzBuCqDStEDHd0ZmIxoVey    | prod_VSv1h5nOzd03Zq  |
| STUDENT  | 4900         | price_1URzDDCqDStEDHd0DcnB6ccc    | prod_VSv3Vw9u19zXIJ  |
| DAY_PASS | 2500         | price_1URzCYCqDStEDHd00V1salUU    | prod_VSv2pb4onc7qSy  |
| ANNUAL   | **TODO**     | **TODO — not yet provided**        | **TODO**             |

**Action still needed:** Provide the ANNUAL plan's Stripe product + price ID and confirm the
amount. Update the commented-out block in `V14__correct_plan_stripe_ids.sql`.

V14 also deletes the V7 placeholder equipment rows (Laser Cutter, 3D Printer, CNC Router,
Soldering Station, Table Saw, Sewing Machine) which are superseded by the named V8 inventory.

---

## 3. ~~V6 Product ID Placeholders~~ — RESOLVED

Handled by V14 corrective migration (see item 2 above).

---

## 4. CLASS Equipment Rates — Pending Client Confirmation (BLOCKING for billing)

V10 seeds CLASS equipment at `$20/hr` (2000 cents) as a placeholder. Members get 50% off
($10/hr); non-members pay full rate. Backend enforces this but the frontend hardcodes "Free" on
the BookClasses page.

**Action:** Confirm the rate with the client. Update the seed if it differs. Update
`BookClasses.jsx` to read `hourlyRateCents` from the equipment API response.

---

## 5. ~~Membership Prices~~ — RESOLVED

`Membership.jsx` already fetched from `GET /api/v1/billing/plans`. The fix was adding
`STUDIO_SMALL`, `STUDIO_MEDIUM`, `STUDIO_LARGE` to the `PLAN_DETAILS` map (they were being
silently dropped by `if (!details) return null`). Also corrected `ANNUAL` which was mislabeled
as "Member with Private Studio Space" — studio plans now carry that description.

---

## 6. Stripe Webhook Secret + Production Endpoint (BLOCKING)

The Lambda consumer ingests Stripe events and forwards them to `InternalBillingController`. The
webhook secret and production URL must be registered in Stripe.

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

## 8. ~~Training Attestation — Frontend~~ — RESOLVED

`MyReservations.jsx` now has a dedicated `TrainingAttestCard` component for `TRAINING_PENDING`
reservations. It shows an orange "Training required" badge, the date/time, a "Watch training
video →" link (from `reservation.trainingVideoUrl`), a confirmation checkbox, and an attest
button that calls `POST /api/v1/reservations/{id}/training-attest`. On success the reservation
refreshes to `ACTIVE` and moves into the normal upcoming section.

`useDashboard.upcomingReservations` updated to include `TRAINING_PENDING` so the count appears
on the dashboard widget.

`ReservationDTO` updated to include `trainingVideoUrl` from the equipment record.

---

## 9. Equipment Pricing — Frontend Hardcodes $7/hr and Ignores Duration

`RentEquipment.jsx`, `ReserveEquipment.jsx`, and `ReservationCheckout.jsx` all hardcode `$7/hr` /
`HOURLY_RATE = 7` and do not factor in the selected duration. The backend already calculates the
correct total: `estimatedCostCents = quotedRateCents × durationMinutes / 60`. A 3-hour booking at
$10/hr should display $30, not $10.

**Action:**
- Use `equipment.hourlyRateCents` for the per-hour display on browse/detail pages.
- On the checkout screen, compute and display the total from `reservation.quotedRateCents` and
  `reservation.estimatedCostCents` returned by the API — do not derive cost client-side.
- The time-window selector must allow multi-hour blocks (e.g. select start + end, or start +
  duration). The backend accepts any valid `startTime`/`endTime` pair already.

---

## 10. Refund Endpoint — Phase 4 (Not Started)

`app.billing.refund-window-hours=48` is configured. The admin endpoint that reads it has not been
built.

**Action:** Build `POST /api/v1/admin/billing/refund` when Phase 4 begins. Not blocking launch if
refunds are handled manually in the Stripe dashboard for now.

---

## 11. ~~Day-Pass Checkout Disabled~~ — RESOLVED

`ReservationCheckout.jsx` now wires the "Pay $X (day pass)" button to
`POST /api/v1/billing/checkout-session` with `planCode: "DAY_PASS"`. Before redirecting, the
pending reservation is saved to sessionStorage. On return from Stripe (`/billing/success?type=daypass`),
`BillingSuccess` shows a "Continue reservation" link. `ReservationCheckout` falls back to
sessionStorage when router state is absent (after a full-page Stripe redirect), so the user can
complete their booking without re-entering any details.

---

## 12. Operating Hours — Hardcoded in Frontend (BLOCKING for scheduling)

`ReserveEquipment.jsx` hardcodes the available time slots and closing hour:

```js
const TIME_SLOTS = ["9:00 AM", "10:00 AM", "11:00 AM", "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM"];
const CLOSING_HOUR = 18; // 6:00 PM
```

This means:
- Open 9 AM – 6 PM with a lunch break at noon (no 12:00 PM slot).
- Max duration of 4 hours.
- No way for staff to change hours without a code deploy.

**Questions to answer before go-live:**
1. What are the actual operating hours (open/close time, days of week)?
2. Is the noon lunch gap intentional, or should 12:00 PM be bookable?
3. What is the maximum single-session duration?
4. Are hours the same for all equipment, or do some machines have restricted windows?

**Actions:**
- Confirm the above with the client and update `TIME_SLOTS`, `SLOT_HOURS`, and `CLOSING_HOUR`
  in `ReserveEquipment.jsx` to match.
- If hours need to be configurable by staff without a deploy, add an
  `operating_hours` config table to the DB and expose a `GET /api/v1/config/hours` endpoint.
  For launch, hardcoding the confirmed values is acceptable.

---

## 13. ~~Hardware NFC Check-In Endpoint~~ — RESOLVED (pending Lianshi reader questions)

Backend is built. Three questions about the Lianshi reader still need answers before the device
can be pointed at the endpoint (see questions below). The `HARDWARE_DEVICE_KEY` secret must also
be set in prod before go-live.

### Original spec

The building entry point uses a Lianshi NFC/IC card reader. When a member taps their card, the
reader sends the card UID to the backend; the backend returns granted or denied; the reader
controls the door relay.

### MVP design

**New endpoint:** `POST /api/v1/hardware/checkin`
- Auth: single static `X-Device-Key` header value stored in env (`HARDWARE_DEVICE_KEY`).
  No JWT. No per-device table needed for MVP.
- Request body: `{ "cardId": "<nfc_uid_from_reader>" }`
- Response: `{ "granted": true|false, "reason": "...", "userName": "..." }`

**Access is granted if ANY of the following are true at the time of the request:**
1. User has an active studio subscription (`studio_assignment` row exists for this user)
2. User has any active membership (`membership.status IN ('ACTIVE','TRIALING','PAST_DUE','GRACE')`)
3. User has an ACTIVE reservation whose window overlaps `[now - 30min, now + 30min]`
   (the same ±30-min window used by `AccessCheckService`)

**Note:** `TRAINING_PENDING` reservations do NOT grant building access. The user must complete
training attestation first to move the reservation to `ACTIVE`.

### Schema change (V15 migration)

```sql
-- Link NFC card UID to a user account
ALTER TABLE users ADD COLUMN nfc_card_id TEXT UNIQUE;

-- Minimal audit log
CREATE TABLE access_events (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES users(id),
    card_id        TEXT NOT NULL,
    granted        BOOLEAN NOT NULL,
    denial_reason  TEXT,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Code changes needed

| File | Change |
|---|---|
| `db/migration/V15__hardware_checkin.sql` | `nfc_card_id` column on `users`; `access_events` table |
| `model/AccessEvent.java` | New entity |
| `repository/AccessEventRepository.java` | `save()` |
| `services/HardwareCheckinService.java` | Card → user lookup; access logic (studio / membership / reservation window); writes `AccessEvent` |
| `controller/HardwareCheckinController.java` | `POST /api/v1/hardware/checkin`; `X-Device-Key` verified against `HARDWARE_DEVICE_KEY` env var |
| `security/SecurityConfig.java` | Permit `POST /api/v1/hardware/checkin` without JWT (device key checked in the controller/filter) |
| `model/User.java` | Add `nfcCardId` field |
| `frontend/.../Account.jsx` or admin panel | Staff needs a way to assign an NFC card ID to a user account |

### Staff card assignment

Staff must be able to link a user's NFC card UID to their account. For MVP this can be done
directly via the admin user-edit endpoint (`PATCH /api/v1/users/{id}`). Extend the request body
to accept `nfcCardId`. Long-term, add a card-tap enrollment screen in the admin panel.

### Questions before implementation

1. What HTTP method and payload format does the Lianshi reader use when it posts the card ID?
   (Some readers only support GET with a query param; others do a JSON POST.)
2. Does the reader support a custom header (`X-Device-Key`) or does auth need to be in the URL
   as a query param instead?
3. Should the door grant access on a `GRACE` period membership, or only on fully active?

---

## Launch Checklist

All items must be ✅ before go-live.

Legend:
- 🔴 Hard blocker — system will not function correctly without it
- 🟡 Important — user-visible gap or staff workflow broken; manual workaround possible
- ⚪ Phase 2 — not needed at launch; backend already built, wire up later

---

### A. External data / client confirmations

- [ ] 🔴 **Item 2** — ANNUAL plan: provide Stripe `price_id`, `product_id`, `amount_cents`
- [ ] 🔴 **Item 4** — CLASS equipment hourly rate confirmed (currently placeholder $20/hr); update V8/seed and `BookClasses.jsx`
- [ ] 🔴 **Item 6** — Register production webhook endpoint in Stripe → Developers → Webhooks; set `STRIPE_WEBHOOK_SECRET` in SSM
- [ ] 🔴 **Item 7** — All production secrets set (`APP_JWT_SECRET`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, Google/Microsoft/Apple OAuth secrets, `MAIL_HOST/USERNAME/PASSWORD`, `REDIS_HOST`, `HARDWARE_DEVICE_KEY`)
- [ ] 🔴 **Item 13** — Answer Lianshi reader questions: HTTP method + payload format; custom header support; GRACE period policy
- [ ] 🟡 **Item 12** — Confirm operating hours: open/close times, days of week, lunch gap at noon, max session duration

---

### B. Backend — must build before launch

- [x] ~~**Item 13 backend**~~ — `POST /api/v1/hardware/checkin` built: V15 migration, `DeviceKeyFilter`, `HardwareCheckinService`, `HardwareCheckinController`, `access_events` logging
- [ ] 🔴 **Item 13 card assignment** — extend `PATCH /api/v1/users/{id}` to accept `nfcCardId` so staff can link cards to accounts
- [ ] 🟡 **Studio room endpoint** — `GET /api/v1/studio-assignment/me` so users can see their assigned room; no backend endpoint exists yet
- [ ] 🟡 **Student .edu gate error** — backend already returns 403 with a message; frontend just needs to surface it (see Frontend section)

---

### C. Frontend — must build before launch

#### User-facing

- [ ] 🔴 **Item 13** — NFC card display / enrollment: staff needs to enter a card UID against a user account (extend admin user-edit page once built, or add a temporary field in `Account.jsx`)
- [ ] 🟡 **Student .edu error** — catch 403 in `MembershipCheckout.jsx` and display `body.message` instead of a generic error
- [ ] 🟡 **Studio room display** — after subscribing to a STUDIO_* plan show the assigned room name/number on `Account.jsx` or `Membership.jsx` (depends on studio room endpoint above)
- [ ] 🟡 **BookClasses pricing** — `BookClasses.jsx` hardcodes "Free"; read `hourlyRateCents` from API and apply member 50% discount display (blocked on item 4 rate confirmation)
- [ ] 🟡 **Item 12** — Update `TIME_SLOTS`, `SLOT_HOURS`, `CLOSING_HOUR` in `ReserveEquipment.jsx` once operating hours are confirmed

#### Admin / staff panel — all backend endpoints are built; no frontend exists

- [ ] 🟡 **User management** — connect `GET /api/v1/users`, `GET /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`, `POST /api/v1/users/{id}/restore`; build `AdminUsers.jsx` table with search + soft-delete + restore actions
- [ ] 🟡 **Role management** — connect `PATCH /api/v1/users/{id}/role`; add role picker to user detail view
- [ ] 🟡 **Equipment management** — connect `POST /api/v1/equipment`, `PUT /api/v1/equipment/{id}`, `PATCH /api/v1/equipment/{id}/status`, `DELETE /api/v1/equipment/{id}`; build create/edit form in `AdminRentEquipment.jsx`; includes setting `trainingVideoUrl` and `currentWaiverVersion` per machine
- [ ] 🟡 **Reservation oversight** — connect `GET /api/v1/reservations/admin/all`, `GET /api/v1/reservations/admin/equipment/{id}`, `PATCH /{id}/extend`; build admin reservations view
- [ ] 🟡 **In-person registration / invite** — connect `AdminRegistrationController` + `RegistrationClaimController`; staff registers a walk-in user and prints/shows a claim code

---

### D. Phase 2 — backend built, wire up when ready

- [ ] ⚪ **Time-slot availability** — build `GET /api/v1/equipment/{id}/availability?date=` endpoint and update `ReserveEquipment.jsx` to grey out already-booked slots
- [ ] ⚪ **Admin refund** — build `POST /api/v1/admin/billing/refund` (config `app.billing.refund-window-hours=48` is already set); until then, process refunds in Stripe dashboard
- [ ] ⚪ **Server-side equipment search** — frontend currently filters client-side; connect `GET /api/v1/equipment/search` when catalog grows large
- [ ] ⚪ **Single reservation detail** — connect `GET /api/v1/reservations/me/{id}` if a detail/receipt page is added
- [ ] ⚪ **Profile image upload** — backend not started; frontend button is a no-op; needs S3 or equivalent storage
- [ ] ⚪ **Soft-delete account UI** — backend `DELETE /api/v1/users/{me}` exists; add "Delete account" option in `Account.jsx`
- [ ] ⚪ **Stripe Phase 2 webhooks** — `invoice.payment_failed`, `charge.refunded`, `charge.dispute.created`, `customer.deleted` all received but not handled; implement dunning + dispute flows
- [ ] ⚪ **Access check kiosk UI** — `POST /api/v1/access/check` is live but has no frontend; build a staff-facing check-in screen if a kiosk terminal is deployed alongside the NFC reader

---

### E. Infrastructure / ops

- [ ] 🔴 **Item 6** — Stripe webhook registered in dashboard; `STRIPE_WEBHOOK_SECRET` in SSM
- [ ] 🔴 **Item 7** — All prod secrets rotated and confirmed
- [ ] 🔴 **Item 13** — `HARDWARE_DEVICE_KEY` added to prod secrets
- [ ] 🟡 **SMTP** — test OTP and reservation confirmation emails end-to-end in prod
- [ ] 🟡 **Redis** — verify OTP sign-in works in prod (connection to `REDIS_HOST`)
- [ ] 🟡 **Lambda** — confirm Lambda consumer is deployed and processing Stripe events; test with a real checkout in staging
