INSERT INTO reservation_statuses (code, description) VALUES
    ('PENDING', 'Awaiting required equipment training before confirmation');

DROP INDEX IF EXISTS idx_reservations_active_overlap;
CREATE INDEX idx_reservations_active_overlap
    ON reservations(equipment_id, start_time, end_time)
    WHERE status IN ('ACTIVE', 'PENDING');
