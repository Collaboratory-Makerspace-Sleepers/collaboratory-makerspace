ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

-- Seed a default MEMBER role assignment is not needed here; email/password
-- signup accounts start without roles, exactly like pre-registered users.