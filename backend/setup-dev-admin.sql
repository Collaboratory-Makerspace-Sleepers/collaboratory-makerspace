-- Setup script for dev admin account
-- This script creates the dev admin user if they don't exist and assigns them the ADMIN role

-- First, create the user if it doesn't exist
INSERT INTO users (email, account_status, auth0_subject, created_at)
VALUES ('Jcs7350@g.rit.edu', 'ACTIVE', 'otp:jcs7350@g.rit.edu', NOW())
ON CONFLICT (email) DO UPDATE
SET account_status = 'ACTIVE', auth0_subject = COALESCE(users.auth0_subject, 'otp:jcs7350@g.rit.edu');

-- Then, assign the ADMIN role (if not already assigned)
INSERT INTO user_roles (user_id, role)
SELECT u.id, 'ADMIN'
FROM users u
WHERE u.email = 'Jcs7350@g.rit.edu'
ON CONFLICT (user_id, role) DO NOTHING;

-- Verify the setup
SELECT u.id, u.email, u.account_status, array_agg(ur.role) as roles
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
WHERE u.email = 'Jcs7350@g.rit.edu'
GROUP BY u.id, u.email, u.account_status;
