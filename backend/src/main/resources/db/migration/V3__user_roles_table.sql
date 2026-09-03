-- Introduce user_roles join table and remove single-role column.
--
-- On databases created from V1__baseline.sql the user_roles table and the
-- roles FK already exist; the CREATE TABLE and ALTER TABLE are skipped.
-- On legacy databases (pre-V1) this runs the full migration as before.

-- Step 1: create the join table only if it does not already exist.
--         On V1 fresh installs it already exists with the roles FK; skip.
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL REFERENCES roles(code),
    PRIMARY KEY (user_id, role)
);

-- Steps 2 & 3: legacy path — only runs when users.role still exists
--              (i.e., on databases that pre-date V1__baseline.sql).
--              On V1 fresh installs the column is absent; the block is a no-op.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE  table_name  = 'users'
          AND  column_name = 'role'
          AND  table_schema = current_schema()
    ) THEN
        -- Backfill authority roles only; customer-type roles are intentionally excluded.
        INSERT INTO user_roles (user_id, role)
        SELECT id, role
        FROM   users
        WHERE  role IN ('ADMIN', 'STAFF', 'INSTRUCTOR')
          AND  deleted_at IS NULL
        ON CONFLICT DO NOTHING;

        ALTER TABLE users DROP COLUMN role;
    END IF;
END $$;