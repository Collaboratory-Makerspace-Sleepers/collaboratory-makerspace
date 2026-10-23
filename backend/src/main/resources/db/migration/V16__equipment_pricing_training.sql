-- Adds hourly pricing and training prerequisite fields to equipment.
-- Also adds quoted cost columns to reservations and creates the training_completion table.
-- Idempotent: safe to run on databases that already have these changes from a prior migration.

-- ── Reservation status: TRAINING_PENDING ─────────────────────────────────────
-- Previously in deleted V11; included here so fresh databases are fully seeded.

INSERT INTO reservation_statuses (code, description)
VALUES ('TRAINING_PENDING', 'Awaiting user online training attestation before becoming active')
ON CONFLICT (code) DO NOTHING;

-- ── Equipment pricing and training fields ─────────────────────────────────────

ALTER TABLE equipment
    ADD COLUMN IF NOT EXISTS hourly_rate_cents    INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS member_rate_cents    INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS free_with_membership BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_training    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS training_fee_cents   INT     NOT NULL DEFAULT 0;

-- 3D printers: $7/hr
UPDATE equipment SET hourly_rate_cents = 700,  member_rate_cents = 700
WHERE category = '3D_PRINTER' AND hourly_rate_cents = 0;

-- Laser: $20/hr
UPDATE equipment SET hourly_rate_cents = 2000, member_rate_cents = 2000
WHERE category = 'LASER' AND hourly_rate_cents = 0;

-- CNC (all sizes): $28/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 2800, member_rate_cents = 2800,
    requires_training = true, training_fee_cents = 2000
WHERE category IN ('CNC_LARGE', 'CNC_SMALL') AND hourly_rate_cents = 0;

-- Wood lathe: $12/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 1200, member_rate_cents = 1200,
    requires_training = true, training_fee_cents = 2000
WHERE category = 'WOOD_LATHE' AND hourly_rate_cents = 0;

-- Metal lathe: $20/hr, one-time training required, $20 fee
UPDATE equipment SET hourly_rate_cents = 2000, member_rate_cents = 2000,
    requires_training = true, training_fee_cents = 2000
WHERE category = 'METAL_LATHE' AND hourly_rate_cents = 0;

-- ── Reservation cost snapshot ─────────────────────────────────────────────────

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS quoted_rate_cents    INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS estimated_cost_cents INT NOT NULL DEFAULT 0;

-- ── Training completion tracking ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS training_completion (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES users(id),
    equipment_id            BIGINT      NOT NULL REFERENCES equipment(id),
    completed_at            TIMESTAMPTZ NOT NULL,
    training_reservation_id BIGINT      REFERENCES reservations(id),
    verified_by             BIGINT      REFERENCES users(id),
    notes                   TEXT,
    UNIQUE (user_id, equipment_id)
);

CREATE INDEX IF NOT EXISTS idx_training_completion_user     ON training_completion(user_id);
CREATE INDEX IF NOT EXISTS idx_training_completion_equipment ON training_completion(equipment_id);
