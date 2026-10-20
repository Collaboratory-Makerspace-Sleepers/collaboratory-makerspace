ALTER TABLE hardware_checkins
ADD COLUMN IF NOT EXISTS signature_data TEXT;
