-- Physical access cards and card-scan audit log
-- From frontendAdmin V15, renumbered to avoid collision with main

CREATE TABLE IF NOT EXISTS member_cards (
    id                  BIGSERIAL PRIMARY KEY,
    card_uid            VARCHAR(32) NOT NULL UNIQUE,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    label               VARCHAR(100),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by_user_id BIGINT REFERENCES users(id),
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at        TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_member_cards_user ON member_cards(user_id);

-- Every tap, including unknown cards, so staff can watch taps live and assign new cards.
CREATE TABLE IF NOT EXISTS card_scans (
    id           BIGSERIAL PRIMARY KEY,
    card_uid     VARCHAR(32) NOT NULL,
    user_id      BIGINT REFERENCES users(id),
    equipment_id BIGINT REFERENCES equipment(id),
    device_id    VARCHAR(100),
    approved     BOOLEAN NOT NULL,
    reason       VARCHAR(255),
    scanned_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_card_scans_time ON card_scans(scanned_at DESC);
