-- Waivers are now signed by typing the member's name.
ALTER TABLE hardware_checkins
    ADD COLUMN IF NOT EXISTS signed_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS signed_at TIMESTAMPTZ;

-- Physical access cards. The UID is the card's identifier; everything else
-- (membership, training, permissions) is read live from the linked user, so
-- changing the member later never requires rescanning the card.
CREATE TABLE IF NOT EXISTS member_cards (
    id                  BIGSERIAL PRIMARY KEY,
    card_uid            VARCHAR(32)  NOT NULL UNIQUE,
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    label               VARCHAR(100),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    assigned_by_user_id BIGINT       REFERENCES users(id),
    assigned_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_member_cards_user ON member_cards(user_id);

-- Every tap, including unknown cards, so staff can watch taps live and assign new cards.
CREATE TABLE IF NOT EXISTS card_scans (
    id           BIGSERIAL PRIMARY KEY,
    card_uid     VARCHAR(32)  NOT NULL,
    user_id      BIGINT       REFERENCES users(id),
    equipment_id BIGINT       REFERENCES equipment(id),
    device_id    VARCHAR(100),
    approved     BOOLEAN      NOT NULL,
    reason       VARCHAR(255),
    scanned_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_card_scans_time ON card_scans(scanned_at DESC);
