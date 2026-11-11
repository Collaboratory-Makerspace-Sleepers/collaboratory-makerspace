CREATE TABLE cancellation_requests (
    id                    BIGSERIAL    PRIMARY KEY,
    reservation_id        BIGINT       NOT NULL REFERENCES reservations(id),
    requested_by_user_id  BIGINT       NOT NULL REFERENCES users(id),
    reason                TEXT,
    status                VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    admin_note            TEXT,
    refund_eligible_until TIMESTAMPTZ  NOT NULL,
    requested_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ
);
CREATE INDEX idx_cr_reservation ON cancellation_requests(reservation_id);
CREATE INDEX idx_cr_status       ON cancellation_requests(status);

ALTER TABLE payment_record ADD COLUMN stripe_refund_id VARCHAR(255);
