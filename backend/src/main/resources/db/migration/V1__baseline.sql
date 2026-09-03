-- =====================================================================
-- V1 — Baseline Schema
-- Collaboratory Makerspace
--
-- Authoritative schema for a clean install. Covers the complete model
-- as of the initial release: users, equipment, reservations, and the
-- in-person registration flow.
--
-- All status/role columns reference lookup tables so new values can be
-- added via a migration without a code change. Java enums mirror each
-- lookup table and remain the compile-time source of truth.
--
-- On databases built through the old incremental approach (no V1 file),
-- set flyway.baselineVersion=1 and flyway.baselineOnMigrate=true.
-- =====================================================================

-- =====================================================================
-- Lookup tables
-- =====================================================================

CREATE TABLE account_statuses (
    code        VARCHAR(30) PRIMARY KEY,
    description TEXT        NOT NULL
);
INSERT INTO account_statuses (code, description) VALUES
    ('PRE_REGISTERED', 'Created by staff; awaiting user claim'),
    ('ACTIVE',         'Fully activated account');

-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    code         VARCHAR(30) PRIMARY KEY,
    description  TEXT        NOT NULL,
    is_authority BOOLEAN     NOT NULL DEFAULT FALSE
);
INSERT INTO roles (code, description, is_authority) VALUES
    ('GUEST',      'Minimal-access temporary account',                 FALSE),
    ('STUDENT',    'Student member',                                    FALSE),
    ('RENTEE',     'Equipment-rental member',                           FALSE),
    ('MEMBER',     'Default role assigned on first OAuth login',        FALSE),
    ('INSTRUCTOR', 'Instruction authority; implied by STAFF and ADMIN', TRUE),
    ('STAFF',      'Makerspace staff; can manage equipment and users',  TRUE),
    ('ADMIN',      'Full administrative access; implies STAFF',         TRUE);

-- ---------------------------------------------------------------------------

CREATE TABLE equipment_statuses (
    code        VARCHAR(30) PRIMARY KEY,
    description TEXT        NOT NULL
);
INSERT INTO equipment_statuses (code, description) VALUES
    ('AVAILABLE',   'Ready for reservation'),
    ('IN_USE',      'Currently checked out'),
    ('MAINTENANCE', 'Under repair — not reservable'),
    ('RETIRED',     'Permanently decommissioned');

-- ---------------------------------------------------------------------------

CREATE TABLE reservation_statuses (
    code        VARCHAR(30) PRIMARY KEY,
    description TEXT        NOT NULL
);
INSERT INTO reservation_statuses (code, description) VALUES
    ('ACTIVE',    'Confirmed and upcoming or in progress'),
    ('CANCELLED', 'Cancelled before completion'),
    ('COMPLETED', 'Reservation period ended normally');

-- =====================================================================
-- User profile
-- Created before users so the users table can FK to it.
-- =====================================================================

CREATE TABLE user_profiles (
    id         BIGSERIAL    PRIMARY KEY,
    first_name VARCHAR(100),
    last_name  VARCHAR(100),
    photo_url  TEXT
);

CREATE TABLE user_addresses (
    profile_id BIGINT      NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    label      VARCHAR(20)          CHECK (label IN ('HOME', 'MAILING', 'BILLING', 'BUSINESS')),
    street     TEXT,
    city       VARCHAR(100),
    state      VARCHAR(100),
    zip_code   VARCHAR(20),
    country    VARCHAR(100)
);
CREATE INDEX idx_user_addresses_profile ON user_addresses(profile_id);

CREATE TABLE user_phone_numbers (
    profile_id BIGINT      NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    label      VARCHAR(20)          CHECK (label IN ('PRIMARY', 'MOBILE', 'HOME', 'WORK', 'FAX')),
    number     VARCHAR(30) NOT NULL
);
CREATE INDEX idx_user_phone_numbers_profile ON user_phone_numbers(profile_id);

-- =====================================================================
-- Users
-- =====================================================================

CREATE TABLE users (
    id             BIGSERIAL    PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    -- SHA-256 hex of the email; used for privacy-safe lookup after hard delete.
    email_digest   CHAR(64),
    auth0_subject  VARCHAR(255) UNIQUE,
    account_status VARCHAR(30)  NOT NULL DEFAULT 'PRE_REGISTERED'
                                REFERENCES account_statuses(code),
    profile_id     BIGINT       UNIQUE   REFERENCES user_profiles(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ
);

CREATE INDEX idx_users_auth0_subject  ON users(auth0_subject)  WHERE auth0_subject IS NOT NULL;
CREATE INDEX idx_users_account_status ON users(account_status);
CREATE INDEX idx_users_deleted_at     ON users(deleted_at)     WHERE deleted_at IS NOT NULL;

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL REFERENCES roles(code),
    PRIMARY KEY (user_id, role)
);

-- =====================================================================
-- Registration invites  (staff-initiated in-person onboarding)
-- =====================================================================

CREATE TABLE registration_invites (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id),
    -- SHA-256 hex of the raw one-time token. The raw token is never stored.
    token_hash     CHAR(64)     NOT NULL UNIQUE,
    intended_email VARCHAR(255) NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    consumed_at    TIMESTAMPTZ,
    created_by     BIGINT       REFERENCES users(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invites_intended_email ON registration_invites(intended_email);

-- =====================================================================
-- Equipment
-- =====================================================================

CREATE TABLE equipment (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    category    VARCHAR(100),
    image_url   TEXT,
    status      VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE'
                             REFERENCES equipment_statuses(code),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_equipment_status   ON equipment(status);
CREATE INDEX idx_equipment_category ON equipment(category) WHERE category IS NOT NULL;
-- Full-text search index to support EquipmentRepository.findByNameContainingIgnoreCase
CREATE INDEX idx_equipment_name_fts ON equipment USING gin(to_tsvector('english', name));

-- =====================================================================
-- Reservations
-- =====================================================================

CREATE TABLE reservations (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    equipment_id BIGINT      NOT NULL REFERENCES equipment(id),
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                             REFERENCES reservation_statuses(code),
    cancelled_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_end_after_start CHECK (end_time > start_time)
);

-- Supports findByUserIdOrderByStartTimeDesc
CREATE INDEX idx_reservations_user
    ON reservations(user_id, start_time DESC);

-- Supports findByEquipmentIdOrderByStartTimeDesc
CREATE INDEX idx_reservations_equipment
    ON reservations(equipment_id, start_time DESC);

-- Partial index for the overlap detection query (ReservationRepository.findOverlapping).
-- Only ACTIVE reservations participate in conflict checking.
CREATE INDEX idx_reservations_active_overlap
    ON reservations(equipment_id, start_time, end_time)
    WHERE status = 'ACTIVE';
