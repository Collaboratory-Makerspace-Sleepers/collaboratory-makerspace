-- Add role assignment fields to unregistered_cards table
ALTER TABLE unregistered_cards ADD COLUMN assigned_role VARCHAR(50);
ALTER TABLE unregistered_cards ADD COLUMN role_assigned_at TIMESTAMP;

-- Add index on assigned_role for filtering
CREATE INDEX idx_unregistered_cards_assigned_role ON unregistered_cards(assigned_role);
