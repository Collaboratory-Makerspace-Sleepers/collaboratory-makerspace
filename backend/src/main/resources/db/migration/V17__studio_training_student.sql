-- Studio plans, student pricing, studio assignment table, CLASS equipment rates.
-- Idempotent: safe to run on databases that already have these changes from a prior migration.

-- ── Student plan price ────────────────────────────────────────────────────────

UPDATE membership_plan
SET amount_cents = 4900
WHERE code = 'STUDENT';

-- ── Studio membership plans ───────────────────────────────────────────────────

INSERT INTO membership_plan (code, display_name, stripe_price_id, stripe_product_id,
                             billing_interval, amount_cents, currency, active)
VALUES
    ('STUDIO_SMALL',  'Small Studio',  'price_1URzLjCqDStEDHd02IIYc61m', 'prod_VSvC52zx4mE6w5', 'MONTH', 35000,  'usd', true),
    ('STUDIO_MEDIUM', 'Medium Studio', 'price_1URzNOCqDStEDHd0gwqul6ah', 'prod_VSvDlFs86lYspI', 'MONTH', 75000,  'usd', true),
    ('STUDIO_LARGE',  'Large Studio',  'price_1URzNkCqDStEDHd0LxukmIP0', 'prod_VSvEK1PMQOUHJz', 'MONTH', 120000, 'usd', true)
ON CONFLICT (code) DO NOTHING;

-- ── Studio assignment table ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS studio_assignment (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    equipment_id  BIGINT      NOT NULL REFERENCES equipment(id),
    membership_id BIGINT      NOT NULL REFERENCES membership(id),
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_studio_user      UNIQUE (user_id),
    CONSTRAINT uq_studio_equipment UNIQUE (equipment_id)
);

CREATE INDEX IF NOT EXISTS idx_studio_assignment_membership ON studio_assignment(membership_id);

-- ── CLASS equipment hourly rates ──────────────────────────────────────────────
-- Placeholder: $20/hr. Confirm with client before go-live.

UPDATE equipment
SET hourly_rate_cents = 2000, member_rate_cents = 2000
WHERE category = 'CLASS' AND hourly_rate_cents = 0;
