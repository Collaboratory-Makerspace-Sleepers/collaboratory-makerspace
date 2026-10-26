-- V20: Apply correct Stripe product and price IDs from main branch
-- These are the real active Stripe products and prices configured in the account

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
