-- V14: Replace placeholder Stripe IDs with real production IDs for membership plans.
--      Remove V7 placeholder equipment rows that are superseded by V8 named inventory.
--
-- ANNUAL plan Stripe IDs are not yet provided — update before go-live.

-- ── Membership plan corrections ───────────────────────────────────────────────────

UPDATE membership_plan SET
    stripe_price_id   = 'price_1URzBuCqDStEDHd0ZmIxoVey',
    stripe_product_id = 'prod_VSv1h5nOzd03Zq',
    display_name      = 'Monthly Membership',
    amount_cents      = 9500
WHERE code = 'MONTHLY';

UPDATE membership_plan SET
    stripe_price_id   = 'price_1URzDDCqDStEDHd0DcnB6ccc',
    stripe_product_id = 'prod_VSv3Vw9u19zXIJ',
    display_name      = 'Student Membership',
    amount_cents      = 4900
WHERE code = 'STUDENT';

UPDATE membership_plan SET
    stripe_price_id   = 'price_1URzCYCqDStEDHd00V1salUU',
    stripe_product_id = 'prod_VSv2pb4onc7qSy',
    display_name      = 'Day Pass',
    amount_cents      = 2500
WHERE code = 'DAY_PASS';

-- TODO: ANNUAL plan — Stripe product/price not yet provided.
-- UPDATE membership_plan SET
--     stripe_price_id   = 'price_REAL_ANNUAL',
--     stripe_product_id = 'prod_REAL_ANNUAL',
--     amount_cents      = REAL_AMOUNT_CENTS
-- WHERE code = 'ANNUAL';

-- ── Remove V7 placeholder equipment ──────────────────────────────────────────────
-- V8 seeds the real named inventory. The V7 placeholder rows are duplicates
-- and must not appear in the frontend catalog.
-- Must clear FK chains before deleting: waiver signatures → reservations → equipment.

UPDATE training_waiver_signatures
SET reservation_id = NULL
WHERE reservation_id IN (
    SELECT r.id FROM reservations r
    WHERE r.equipment_id IN (
        SELECT id FROM equipment WHERE name IN (
            'Laser Cutter', '3D Printer', 'CNC Router',
            'Soldering Station', 'Table Saw', 'Sewing Machine'
        )
    )
);

DELETE FROM reservations
WHERE equipment_id IN (
    SELECT id FROM equipment WHERE name IN (
        'Laser Cutter', '3D Printer', 'CNC Router',
        'Soldering Station', 'Table Saw', 'Sewing Machine'
    )
);

DELETE FROM equipment WHERE name IN (
    'Laser Cutter',
    '3D Printer',
    'CNC Router',
    'Soldering Station',
    'Table Saw',
    'Sewing Machine'
);