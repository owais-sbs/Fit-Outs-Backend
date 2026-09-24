CREATE TABLE IF NOT EXISTS sc_package_free_issue_material (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL,
    company_id UUID NOT NULL,
    item_description VARCHAR(240) NOT NULL,
    supplied_by VARCHAR(32) NOT NULL,
    quantity NUMERIC(14, 4),
    unit VARCHAR(32),
    notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_free_issue_supplied_by
        CHECK (supplied_by IN ('MAIN_CONTRACTOR', 'SUBCONTRACTOR', 'CLIENT', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_sc_free_issue_package
    ON sc_package_free_issue_material(package_uuid, sort_order);

CREATE TABLE IF NOT EXISTS sc_package_attendance (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL,
    company_id UUID NOT NULL,
    responsibility_type VARCHAR(48) NOT NULL,
    responsible_party VARCHAR(32) NOT NULL,
    notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_attendance_responsible_party
        CHECK (responsible_party IN ('MAIN_CONTRACTOR', 'SUBCONTRACTOR', 'CLIENT', 'SHARED', 'NOT_APPLICABLE'))
);

CREATE INDEX IF NOT EXISTS idx_sc_attendance_package
    ON sc_package_attendance(package_uuid, sort_order);

ALTER TABLE subcontractor_package
    ADD COLUMN IF NOT EXISTS specialist_licence_required VARCHAR(240);
