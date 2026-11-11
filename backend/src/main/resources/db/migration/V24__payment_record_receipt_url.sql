ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS receipt_url VARCHAR(500);
ALTER TABLE payment_record ADD COLUMN IF NOT EXISTS stripe_refund_id VARCHAR(255);
