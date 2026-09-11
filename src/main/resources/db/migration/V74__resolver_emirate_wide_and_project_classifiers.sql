ALTER TABLE approval_authorities
    ADD COLUMN IF NOT EXISTS applies_emirate_wide BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE approval_authorities
SET applies_emirate_wide = TRUE
WHERE deleted = FALSE
  AND UPPER(code) IN ('DCD', 'ADCD', 'DEWA', 'ADDC', 'SEWA', 'RTA', 'SIRA', 'DMW', 'DMFS', 'DET');

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS approval_property_type_id UUID REFERENCES approval_property_types (id);

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS approval_project_nature_id UUID REFERENCES approval_project_natures (id);

CREATE INDEX IF NOT EXISTS idx_projects_approval_property_type
    ON projects (approval_property_type_id);

CREATE INDEX IF NOT EXISTS idx_projects_approval_project_nature
    ON projects (approval_project_nature_id);
