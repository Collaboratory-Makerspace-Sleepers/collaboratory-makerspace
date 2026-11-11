ALTER TABLE class_session
  ADD COLUMN IF NOT EXISTS stripe_product_id VARCHAR(255),
  ADD COLUMN IF NOT EXISTS stripe_price_id   VARCHAR(255);
