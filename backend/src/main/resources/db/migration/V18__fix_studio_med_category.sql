-- Rename legacy STUDIO_MED category code to STUDIO_MEDIUM.
-- The old V9 migration used the abbreviated code; V8 and all current code use STUDIO_MEDIUM.
-- Safe to run on fresh databases (no rows match the WHERE clause).

UPDATE equipment
SET category = 'STUDIO_MEDIUM'
WHERE category = 'STUDIO_MED';