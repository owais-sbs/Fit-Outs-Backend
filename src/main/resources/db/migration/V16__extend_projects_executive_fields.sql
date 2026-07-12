-- Executive / portfolio fields for project dashboards

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'Planning',
    ADD COLUMN IF NOT EXISTS progress INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS budget NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS location VARCHAR(200),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS project_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS assigned_manager VARCHAR(200),
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS expected_completion_date DATE;

UPDATE projects
SET
    status = 'In Progress',
    progress = 42,
    budget = 1850000.00,
    location = 'Dubai Marina, UAE',
    description = 'Full villa fit-out including kitchen, flooring, MEP upgrades, and joinery.',
    project_type = 'Residential Villa',
    assigned_manager = 'pm@fitouts.demo',
    start_date = CURRENT_DATE - INTERVAL '45 days',
    expected_completion_date = CURRENT_DATE + INTERVAL '120 days'
WHERE name = 'Demo Villa Fit-Out'
  AND is_deleted = FALSE;
