-- Link project_document rows to drawings (and future sources) for a unified library.
ALTER TABLE project_document ADD COLUMN IF NOT EXISTS source_type VARCHAR(40);
ALTER TABLE project_document ADD COLUMN IF NOT EXISTS source_uuid UUID;

CREATE INDEX IF NOT EXISTS idx_project_document_source
    ON project_document(company_id, source_type, source_uuid);

CREATE INDEX IF NOT EXISTS idx_project_document_file_path
    ON project_document(company_id, file_path);
