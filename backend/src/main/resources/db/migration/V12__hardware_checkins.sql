-- V12__hardware_checkins.sql
-- Add hardware check-in tracking table

CREATE TABLE hardware_checkins (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    equipment_id BIGINT NOT NULL REFERENCES equipment(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    checkin_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    access_granted BOOLEAN,
    error_message TEXT,
    device_id VARCHAR(100),
    waiver_signed BOOLEAN DEFAULT false,
    waiver_approved_by_admin BOOLEAN DEFAULT false,
    approved_by_user_id BIGINT REFERENCES users(id),
    approval_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_hardware_checkins_user_id ON hardware_checkins(user_id);
CREATE INDEX idx_hardware_checkins_equipment_id ON hardware_checkins(equipment_id);
CREATE INDEX idx_hardware_checkins_status ON hardware_checkins(status);
CREATE INDEX idx_hardware_checkins_device_id ON hardware_checkins(device_id);
CREATE INDEX idx_hardware_checkins_waiver_signed ON hardware_checkins(waiver_signed);
CREATE INDEX idx_hardware_checkins_waiver_approved ON hardware_checkins(waiver_approved_by_admin);
CREATE INDEX idx_hardware_checkins_checkin_time ON hardware_checkins(checkin_time DESC);

-- Add requires_waiver column to equipment if it doesn't exist
ALTER TABLE equipment
ADD COLUMN IF NOT EXISTS requires_waiver BOOLEAN DEFAULT false;
