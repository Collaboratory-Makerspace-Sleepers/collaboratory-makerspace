-- Update Stripe Product IDs and Price IDs to use active products from Stripe dashboard

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv1h5nOzd03Zq'  
WHERE code = 'MONTHLY';

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv3Vw9u19zXIJ'  
WHERE code = 'STUDENT';

UPDATE membership_plan 
SET stripe_product_id = 'prod_VSv2pb4onc7qSy',
    stripe_price_id = 'price_1URzCYCqDStEDHd00V1salUU'
WHERE code = 'DAY_PASS';
