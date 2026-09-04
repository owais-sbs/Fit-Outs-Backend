ALTER TABLE subcontractor_claim
    ADD COLUMN IF NOT EXISTS attachment_paths TEXT;
