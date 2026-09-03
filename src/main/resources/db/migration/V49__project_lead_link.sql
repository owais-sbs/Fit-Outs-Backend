ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS lead_id BIGINT REFERENCES leads(id),
    ADD COLUMN IF NOT EXISTS lead_reference_no VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_projects_company_lead_active
    ON projects (company_id, lead_id)
    WHERE lead_id IS NOT NULL AND is_deleted = FALSE;
