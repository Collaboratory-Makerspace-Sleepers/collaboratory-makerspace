-- V10: Studio plans, student pricing, studio assignment table, CLASS equipment rates.
--
-- HUMAN TODO before deploying:
--   1. Create three Stripe products + recurring prices for studio plans.
--   2. Replace every 'PLACEHOLDER_PRICE_*' and 'PLACEHOLDER_PRODUCT_*' value with the
--      real Stripe IDs.
--   3. Confirm CLASS equipment hourly rates (currently seeded at $20/hr = 2000 cents).

-- ── Student plan price ────────────────────────────────────────────────────────────

UPDATE membership_plan
SET amount_cents = 4900
WHERE code = 'STUDENT';

-- ── Studio membership plans ────────────────────────────────────────────────────────
-- Billing interval MONTH; stripe_price_id / stripe_product_id must be replaced with
-- real Stripe IDs before go-live.

INSERT INTO membership_plan (code, display_name, stripe_price_id, stripe_product_id,
                             billing_interval, amount_cents, currency, active)
VALUES
    ('STUDIO_SMALL',  'Small Studio',  'PLACEHOLDER_PRICE_STUDIO_SMALL',  'PLACEHOLDER_PRODUCT_STUDIO_SMALL',  'MONTH', 35000,  'usd', true),
    ('STUDIO_MEDIUM', 'Medium Studio', 'PLACEHOLDER_PRICE_STUDIO_MEDIUM', 'PLACEHOLDER_PRODUCT_STUDIO_MEDIUM', 'MONTH', 75000,  'usd', true),
    ('STUDIO_LARGE',  'Large Studio',  'PLACEHOLDER_PRICE_STUDIO_LARGE',  'PLACEHOLDER_PRODUCT_STUDIO_LARGE',  'MONTH', 120000, 'usd', true);

-- ── Studio assignment table ────────────────────────────────────────────────────────
-- Tracks which user holds which studio room under which membership subscription.
-- One user → one studio; one room → one user. No time-slot conflicts because
-- occupancy lasts for the entire subscription period.

CREATE TABLE studio_assignment (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    equipment_id  BIGINT      NOT NULL REFERENCES equipment(id),
    membership_id BIGINT      NOT NULL REFERENCES membership(id),
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_studio_user      UNIQUE (user_id),
    CONSTRAINT uq_studio_equipment UNIQUE (equipment_id)
);

CREATE INDEX idx_studio_assignment_membership ON studio_assignment(membership_id);

-- ── CLASS equipment hourly rates ──────────────────────────────────────────────────
-- Placeholder: $20/hr. Confirm with client before go-live.
-- Active members receive 50% off CLASS equipment (applied in ReservationService).

UPDATE equipment
SET hourly_rate_cents = 2000, member_rate_cents = 2000
WHERE category = 'CLASS';
