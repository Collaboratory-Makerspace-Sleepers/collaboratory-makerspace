-- Hardware check-in workflow (waiver request + approval)
-- Combines frontendAdmin V12 + V13 + V14 content, renumbered to avoid collision with main

CREATE TABLE hardware_checkins (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT NOT NULL REFERENCES users(id),
    equipment_id              BIGINT NOT NULL REFERENCES equipment(id),
    status                    VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    checkin_time              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    access_granted            BOOLEAN,
    error_message             TEXT,
    device_id                 VARCHAR(100),
    waiver_signed             BOOLEAN DEFAULT false,
    waiver_approved_by_admin  BOOLEAN DEFAULT false,
    approved_by_user_id       BIGINT REFERENCES users(id),
    approval_time             TIMESTAMPTZ,
    signature_data            TEXT,
    signed_name               VARCHAR(120),
    signed_at                 TIMESTAMPTZ,
    requested_start           TIMESTAMPTZ,
    requested_end             TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hc_user      ON hardware_checkins(user_id);
CREATE INDEX idx_hc_equipment ON hardware_checkins(equipment_id);
CREATE INDEX idx_hc_status    ON hardware_checkins(status);
CREATE INDEX idx_hc_device    ON hardware_checkins(device_id);
CREATE INDEX idx_hc_time      ON hardware_checkins(checkin_time DESC);

ALTER TABLE equipment ADD COLUMN IF NOT EXISTS requires_waiver BOOLEAN DEFAULT false;
