-- Completed waiver PDFs (e.g. downloaded from RabbitSign) attached by staff to a waiver request.
CREATE TABLE IF NOT EXISTS waiver_documents (
    check_in_id         BIGINT PRIMARY KEY REFERENCES hardware_checkins(id) ON DELETE CASCADE,
    filename            VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    data                BYTEA        NOT NULL,
    uploaded_by_user_id BIGINT       REFERENCES users(id),
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
