-- Adds training video URL and waiver versioning to equipment.
-- Alters training_waiver_signatures to be per-reservation with version tracking.

-- ── Equipment: training video + waiver version ────────────────────────────────

ALTER TABLE equipment
    ADD COLUMN training_video_url    TEXT,
    ADD COLUMN current_waiver_version INT NOT NULL DEFAULT 1;

-- ── training_waiver_signatures: per-reservation, version-tracked ──────────────

-- Drop the old unique constraint (was per user+equipment; now per reservation)
ALTER TABLE training_waiver_signatures
    DROP CONSTRAINT IF EXISTS uk_training_waiver_user_equipment;

ALTER TABLE training_waiver_signatures
    ADD COLUMN reservation_id  BIGINT      REFERENCES reservations(id),
    ADD COLUMN waiver_version  INT         NOT NULL DEFAULT 1,
    ADD COLUMN start_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN end_time        TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- One waiver per reservation once linked (reservation_id is null until booking completes)
CREATE UNIQUE INDEX idx_waiver_reservation
    ON training_waiver_signatures(reservation_id)
    WHERE reservation_id IS NOT NULL;

CREATE INDEX idx_waiver_user_equipment
    ON training_waiver_signatures(user_id, equipment_id);
