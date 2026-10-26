-- Update Stripe Product IDs to use active products
-- IMPORTANT: Replace prod_XXXX values with your actual Stripe product IDs from your dashboard

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv1h5nOzd03Zq'  
WHERE code = 'MONTHLY';

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv3Vw9u19zXIJ'  
WHERE code = 'STUDENT';

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv2pb4onc7qSy'  
WHERE code = 'DAY_PASS';
