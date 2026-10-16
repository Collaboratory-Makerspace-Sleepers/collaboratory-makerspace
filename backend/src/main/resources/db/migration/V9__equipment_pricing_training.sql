-- Adds hourly pricing and training prerequisite fields to equipment.
-- Also adds quoted cost columns to reservations and creates the training_completion table.
--
-- member_rate_cents is seeded equal to hourly_rate_cents (no member discount yet).
-- Update these values once the client confirms the discount structure.
-- OPEN: which categories are "basic tools" (free_with_membership = true) — awaiting client.

-- ── Equipment pricing and training fields ───────────────────────────────────────

ALTER TABLE equipment
    ADD COLUMN hourly_rate_cents    INT     NOT NULL DEFAULT 0,
    ADD COLUMN member_rate_cents    INT     NOT NULL DEFAULT 0,
    ADD COLUMN free_with_membership BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN requires_training    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN training_fee_cents   INT     NOT NULL DEFAULT 0;

-- 3D printers: $7/hr, no training required
UPDATE equipment SET hourly_rate_cents = 700,  member_rate_cents = 700
WHERE category = '3D_PRINTER';

-- Laser: $20/hr, no training required
UPDATE equipment SET hourly_rate_cents = 2000, member_rate_cents = 2000
WHERE category = 'LASER';

-- CNC (all sizes): $28/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 2800, member_rate_cents = 2800,
    requires_training = true, training_fee_cents = 2000
WHERE category IN ('CNC_LARGE', 'CNC_SMALL');

-- Wood lathe: $12/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 1200, member_rate_cents = 1200,
    requires_training = true, training_fee_cents = 2000
WHERE category = 'WOOD_LATHE';

-- Metal lathe: $20/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 2000, member_rate_cents = 2000,
    requires_training = true, training_fee_cents = 2000
WHERE category = 'METAL_LATHE';

-- Studios are billed as monthly plans, not hourly — rates stay 0.
-- free_with_membership stays false (studios are a separate paid tier).

-- ── Reservation cost snapshot ────────────────────────────────────────────────────
-- Storing the rate and estimated cost at booking time so the price is never
-- recalculated retroactively if rates change later.

ALTER TABLE reservations
    ADD COLUMN quoted_rate_cents    INT NOT NULL DEFAULT 0,
    ADD COLUMN estimated_cost_cents INT NOT NULL DEFAULT 0;

-- ── Training completion tracking ─────────────────────────────────────────────────

CREATE TABLE training_completion (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES users(id),
    equipment_id            BIGINT      NOT NULL REFERENCES equipment(id),
    completed_at            TIMESTAMPTZ NOT NULL,
    -- The reservation used for the training session, if booked through the app.
    -- NULL when an admin marks a user trained (e.g. prior training at another makerspace).
    training_reservation_id BIGINT      REFERENCES reservations(id),
    -- Admin who recorded this completion.
    verified_by             BIGINT      REFERENCES users(id),
    notes                   TEXT,
    UNIQUE (user_id, equipment_id)
);

CREATE INDEX idx_training_completion_user     ON training_completion(user_id);
CREATE INDEX idx_training_completion_equipment ON training_completion(equipment_id);
