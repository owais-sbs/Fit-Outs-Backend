-- Room-first site visit checklist scope + report item metadata

ALTER TABLE site_visits
    ADD COLUMN IF NOT EXISTS property_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS property_type_custom VARCHAR(120),
    ADD COLUMN IF NOT EXISTS room_scopes TEXT;

ALTER TABLE site_visit_report_items
    ADD COLUMN IF NOT EXISTS room_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS section_name VARCHAR(200),
    ADD COLUMN IF NOT EXISTS question VARCHAR(500);
