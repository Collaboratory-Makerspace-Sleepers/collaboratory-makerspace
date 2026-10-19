-- V15: Hardware NFC check-in support.
--   - nfc_card_id column on users maps an NFC/RFID card UID to a user account.
--   - access_events table provides an audit log of every check-in attempt.

ALTER TABLE users ADD COLUMN nfc_card_id TEXT UNIQUE;

CREATE TABLE access_events (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       REFERENCES users(id),
    card_id        TEXT         NOT NULL,
    granted        BOOLEAN      NOT NULL,
    denial_reason  TEXT,
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_events_user    ON access_events(user_id);
CREATE INDEX idx_access_events_time    ON access_events(occurred_at DESC);
