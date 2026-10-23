-- =====================================================================
-- V19 — Class Session Booking System
--
-- Adds standalone class_session and class_enrollment tables so classes
-- can have fixed schedules, capacity limits, instructors, and Stripe
-- payment flows independent of the equipment reservation system.
-- Removes legacy CLASS-category equipment rows.
-- =====================================================================

CREATE TABLE class_session (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255)  NOT NULL,
    description      TEXT,
    instructor_id    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    start_time       TIMESTAMPTZ   NOT NULL,
    end_time         TIMESTAMPTZ   NOT NULL,
    max_capacity     INT           NOT NULL DEFAULT 10,
    price_cents      INT           NOT NULL DEFAULT 0,
    member_price_cents INT         NOT NULL DEFAULT 0,
    status           VARCHAR(50)   NOT NULL DEFAULT 'SCHEDULED',
    image_url        VARCHAR(500),
    location         VARCHAR(255),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_class_session_start_time  ON class_session(start_time);
CREATE INDEX idx_class_session_status      ON class_session(status);
CREATE INDEX idx_class_session_instructor  ON class_session(instructor_id);

CREATE TABLE class_enrollment (
    id                          BIGSERIAL PRIMARY KEY,
    class_session_id            BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE CASCADE,
    user_id                     BIGINT      NOT NULL REFERENCES users(id)         ON DELETE CASCADE,
    quoted_price_cents          INT         NOT NULL DEFAULT 0,
    status                      VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    stripe_checkout_session_id  VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (class_session_id, user_id)
);

CREATE INDEX idx_class_enrollment_user     ON class_enrollment(user_id);
CREATE INDEX idx_class_enrollment_session  ON class_enrollment(class_session_id);

-- ── Permissions ───────────────────────────────────────────────────────────────

INSERT INTO permissions (code, description) VALUES
    ('MANAGE_CLASSES', 'Create, edit, and cancel class sessions')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_code, permission) VALUES
    ('ADMIN',       'MANAGE_CLASSES'),
    ('STAFF',       'MANAGE_CLASSES'),
    ('INSTRUCTOR',  'MANAGE_CLASSES')
ON CONFLICT DO NOTHING;

-- ── Remove legacy CLASS-category equipment rows ───────────────────────────────

DELETE FROM equipment WHERE category = 'CLASS';
