-- Manual schema migration: introduce user_roles join table and remove single-role column.
-- Run against the production PostgreSQL database before deploying the new backend build.
--
-- Step 1: create the authority-role join table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Step 2: backfill authority roles from the old single-role column.
--         Only ADMIN, STAFF, and INSTRUCTOR are authority roles; customer-type values
--         (MEMBER, GUEST, STUDENT, RENTEE) are intentionally excluded.
INSERT INTO user_roles (user_id, role)
SELECT id, role
FROM   users
WHERE  role IN ('ADMIN', 'STAFF', 'INSTRUCTOR')
  AND  deleted_at IS NULL;

-- Step 3: drop the old single-role column.
ALTER TABLE users DROP COLUMN role;