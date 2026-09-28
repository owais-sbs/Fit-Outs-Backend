-- Project documents: optional publish to appointed subcontractors (separate from client)
ALTER TABLE project_document ADD COLUMN IF NOT EXISTS published_to_sc BOOLEAN;
UPDATE project_document SET published_to_sc = FALSE WHERE published_to_sc IS NULL;
ALTER TABLE project_document ALTER COLUMN published_to_sc SET DEFAULT FALSE;
ALTER TABLE project_document ALTER COLUMN published_to_sc SET NOT NULL;

COMMENT ON COLUMN project_document.published_to_sc IS 'When true, appointed subcontractors on the project can see this document';
