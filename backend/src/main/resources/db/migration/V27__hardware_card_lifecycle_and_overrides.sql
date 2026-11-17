-- Additive migration for frontendAdmin after V26. Do not modify applied migrations.
ALTER TABLE card_scans ADD COLUMN original_user_id BIGINT;
UPDATE card_scans SET original_user_id = user_id;
CREATE TABLE hardware_door_overrides (
 id BIGSERIAL PRIMARY KEY,
 user_id BIGINT NOT NULL REFERENCES users(id),
 device_id UUID NOT NULL REFERENCES hardware_devices(id),
 granted_by BIGINT NOT NULL REFERENCES users(id),
 reason VARCHAR(200) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 revoked_by BIGINT REFERENCES users(id),
 CONSTRAINT hardware_override_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_hardware_override_member ON hardware_door_overrides(user_id, device_id, expires_at);
ALTER TABLE card_scans ADD COLUMN door_override_id BIGINT REFERENCES hardware_door_overrides(id);

CREATE FUNCTION hardware_release_deleted_member() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 DELETE FROM member_cards WHERE user_id = NEW.id;
 UPDATE member_cards SET assigned_by_user_id = NULL WHERE assigned_by_user_id = NEW.id;
 UPDATE card_scans SET user_id = NULL WHERE user_id = NEW.id;
 UPDATE hardware_devices SET assignment_user_id = NULL, assignment_staff_id = NULL,
   assignment_label = NULL, assignment_expires_at = NULL
   WHERE assignment_user_id = NEW.id OR assignment_staff_id = NEW.id;
 UPDATE hardware_door_overrides SET revoked_at = NOW() WHERE user_id = NEW.id AND revoked_at IS NULL;
 RETURN NEW;
END;
$$;
CREATE TRIGGER hardware_release_after_member_delete
AFTER UPDATE OF deleted_at ON users FOR EACH ROW
WHEN (OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL)
EXECUTE FUNCTION hardware_release_deleted_member();
-- Repair existing stranded relationships. Preserve scan rows, decisions and original IDs.
DELETE FROM member_cards WHERE user_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL);
UPDATE member_cards SET assigned_by_user_id = NULL WHERE assigned_by_user_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL);
UPDATE card_scans SET user_id = NULL WHERE user_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL);
UPDATE hardware_devices SET assignment_user_id = NULL, assignment_staff_id = NULL,
 assignment_label = NULL, assignment_expires_at = NULL
 WHERE assignment_user_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL)
 OR assignment_staff_id IN (SELECT id FROM users WHERE deleted_at IS NOT NULL);
