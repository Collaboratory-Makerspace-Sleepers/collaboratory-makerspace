ALTER TABLE cancellation_requests ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);
