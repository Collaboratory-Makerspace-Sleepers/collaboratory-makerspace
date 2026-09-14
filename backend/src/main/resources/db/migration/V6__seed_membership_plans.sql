-- =====================================================================
-- V6 — Seed membership_plan catalog
--
-- Placeholder stripe_price_id and stripe_product_id values must be
-- replaced with real IDs from the Stripe dashboard in Phase 2.
-- amounts_cents and billing_interval are best estimates; adjust before
-- going live.
-- =====================================================================

INSERT INTO membership_plan
    (code, display_name, stripe_price_id, stripe_product_id,
     billing_interval, amount_cents, currency, grants_role, active)
VALUES
    ('MONTHLY',  'Monthly Membership',  'price_PLACEHOLDER_MONTHLY',  'prod_PLACEHOLDER_MONTHLY',  'MONTH', 5000,  'usd', 'MEMBER',  TRUE),
    ('ANNUAL',   'Annual Membership',   'price_PLACEHOLDER_ANNUAL',   'prod_PLACEHOLDER_ANNUAL',   'YEAR',  50000, 'usd', 'MEMBER',  TRUE),
    ('STUDENT',  'Student Membership',  'price_PLACEHOLDER_STUDENT',  'prod_PLACEHOLDER_STUDENT',  'MONTH', 2500,  'usd', 'STUDENT', TRUE),
    ('DAY_PASS', 'Day Pass',            'price_PLACEHOLDER_DAY_PASS', 'prod_PLACEHOLDER_DAY_PASS', NULL,    1500,  'usd', 'MEMBER',  TRUE);
