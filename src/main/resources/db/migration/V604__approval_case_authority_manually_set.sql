-- Renumbered from V82 after merging main (main owns V73–V80 for subcontractor portal).
-- Manual authority picker: keep a user's bind across generate leftover cleanup.

ALTER TABLE approval_case
    ADD COLUMN IF NOT EXISTS authority_manually_set BOOLEAN;

UPDATE approval_case SET authority_manually_set = FALSE WHERE authority_manually_set IS NULL;

ALTER TABLE approval_case
    ALTER COLUMN authority_manually_set SET DEFAULT FALSE;
ALTER TABLE approval_case
    ALTER COLUMN authority_manually_set SET NOT NULL;
