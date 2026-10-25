-- Track unregistered cards (new taps from unknown card UIDs).
-- Admin uses this to see new cards that need to be assigned to a member,
-- assign them to an email, and send an OTP to allow the user to activate access.
CREATE TABLE IF NOT EXISTS unregistered_cards (
    id                  BIGSERIAL PRIMARY KEY,
    card_uid            VARCHAR(32)  NOT NULL UNIQUE,
    first_scanned_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_scanned_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    assigned_to_email   VARCHAR(255),
    assigned_at         TIMESTAMPTZ,
    otp_code            VARCHAR(10),
    otp_generated_at    TIMESTAMPTZ,
    otp_expires_at      TIMESTAMPTZ,
    otp_verified        BOOLEAN      DEFAULT FALSE,
    verified_at         TIMESTAMPTZ,
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    device_id           VARCHAR(100),
    scan_count          INTEGER      DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_unregistered_cards_status ON unregistered_cards(status);
CREATE INDEX IF NOT EXISTS idx_unregistered_cards_email ON unregistered_cards(assigned_to_email);
CREATE INDEX IF NOT EXISTS idx_unregistered_cards_otp ON unregistered_cards(otp_code);
