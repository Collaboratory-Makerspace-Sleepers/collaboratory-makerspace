-- =====================================================================
-- V5 — Permission-based RBAC
--
-- Adds a permissions table and a role_permissions join table so that
-- each role carries an explicit set of capability codes. Removes the
-- hard-coded authority hierarchy from Java and makes it fully
-- configurable through the admin API at runtime.
--
-- Well-known permission codes (referenced by Permission.java):
--   MANAGE_USERS           — list, view, restore user accounts
--   MANAGE_ROLES           — create/edit/delete roles; assign permissions
--   MANAGE_EQUIPMENT       — create, update, delete, change status
--   VIEW_ALL_RESERVATIONS  — paginated admin reservation list
--   MANAGE_RESERVATIONS    — extend or cancel any reservation
--   REGISTER_USERS         — pre-register users via the invite flow
-- =====================================================================

-- ---------------------------------------------------------------------------
-- permissions catalog
-- ---------------------------------------------------------------------------
CREATE TABLE permissions (
    code        VARCHAR(100) PRIMARY KEY,
    description TEXT         NOT NULL
);

INSERT INTO permissions (code, description) VALUES
    ('MANAGE_USERS',           'View, manage, and restore user accounts'),
    ('MANAGE_ROLES',           'Create, edit, delete roles and assign permissions to them'),
    ('MANAGE_EQUIPMENT',       'Create, update, delete equipment and change its status'),
    ('VIEW_ALL_RESERVATIONS',  'View the paginated admin-all reservation list'),
    ('MANAGE_RESERVATIONS',    'Extend or cancel any user''s reservation'),
    ('REGISTER_USERS',         'Pre-register users via the staff invite flow');

-- ---------------------------------------------------------------------------
-- role_permissions join table
-- ---------------------------------------------------------------------------
CREATE TABLE role_permissions (
    role_code  VARCHAR(30)  NOT NULL REFERENCES roles(code)       ON DELETE CASCADE,
    permission VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
    PRIMARY KEY (role_code, permission)
);

-- ---------------------------------------------------------------------------
-- Mark built-in roles so they cannot be deleted through the admin API.
-- ---------------------------------------------------------------------------
ALTER TABLE roles ADD COLUMN is_system BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE roles SET is_system = TRUE
    WHERE code IN ('ADMIN', 'STAFF', 'INSTRUCTOR', 'MEMBER', 'GUEST', 'STUDENT', 'RENTEE');

-- ---------------------------------------------------------------------------
-- Seed default role → permission assignments.
-- These mirror the rules that were previously hardcoded in the filter chains.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_code, permission) VALUES
    -- ADMIN gets everything
    ('ADMIN', 'MANAGE_USERS'),
    ('ADMIN', 'MANAGE_ROLES'),
    ('ADMIN', 'MANAGE_EQUIPMENT'),
    ('ADMIN', 'VIEW_ALL_RESERVATIONS'),
    ('ADMIN', 'MANAGE_RESERVATIONS'),
    ('ADMIN', 'REGISTER_USERS'),
    -- STAFF gets the operational subset (no MANAGE_ROLES)
    ('STAFF', 'MANAGE_USERS'),
    ('STAFF', 'MANAGE_EQUIPMENT'),
    ('STAFF', 'VIEW_ALL_RESERVATIONS'),
    ('STAFF', 'MANAGE_RESERVATIONS'),
    ('STAFF', 'REGISTER_USERS'),
    -- INSTRUCTOR can view reservations (add more as the instructor feature is built)
    ('INSTRUCTOR', 'VIEW_ALL_RESERVATIONS');
