CREATE TABLE hardware_devices (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    area VARCHAR(100) NOT NULL,
    equipment_id BIGINT REFERENCES equipment(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    door_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    unlock_seconds INTEGER NOT NULL DEFAULT 5 CHECK (unlock_seconds BETWEEN 1 AND 30),
    key_hash VARCHAR(64),
    pairing_hash VARCHAR(64) UNIQUE,
    pairing_expires_at TIMESTAMPTZ,
    hostname VARCHAR(100),
    reader_status VARCHAR(160),
    last_seen_at TIMESTAMPTZ,
    connected BOOLEAN NOT NULL DEFAULT FALSE,
    assignment_user_id BIGINT REFERENCES users(id),
    assignment_staff_id BIGINT REFERENCES users(id),
    assignment_label VARCHAR(100),
    assignment_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_hardware_devices_last_seen ON hardware_devices(last_seen_at)
    WHERE connected AND deleted_at IS NULL;

CREATE TABLE hardware_device_events (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES hardware_devices(id),
    event_id UUID NOT NULL,
    type VARCHAR(80) NOT NULL,
    detail VARCHAR(400) NOT NULL,
    outcome VARCHAR(10) NOT NULL,
    source VARCHAR(10) NOT NULL,
    card_uid VARCHAR(32),
    scan_id BIGINT,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (device_id, event_id)
);
CREATE INDEX idx_hardware_device_events_received ON hardware_device_events(received_at DESC, id DESC);
