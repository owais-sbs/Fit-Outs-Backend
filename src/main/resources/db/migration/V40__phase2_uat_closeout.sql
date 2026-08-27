-- Phase 2 UAT closeout: documents soft-delete, snag fields, hold points, material substitute

-- Module 18: soft-delete documents
ALTER TABLE project_document ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_project_document_deleted ON project_document(project_id, deleted);

-- Module 17: snag severity, due date, Ready for inspection
ALTER TABLE snag ADD COLUMN IF NOT EXISTS severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE snag ADD COLUMN IF NOT EXISTS due_date DATE;

ALTER TABLE snag DROP CONSTRAINT IF EXISTS chk_snag_status;
ALTER TABLE snag ADD CONSTRAINT chk_snag_status
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'READY_FOR_INSPECTION', 'RESOLVED', 'CLOSED'));

ALTER TABLE snag DROP CONSTRAINT IF EXISTS chk_snag_severity;
ALTER TABLE snag ADD CONSTRAINT chk_snag_severity
    CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

-- Module 16b: quality hold points (lightweight validation)
CREATE TABLE IF NOT EXISTS quality_hold_point (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    activity_uuid UUID,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    checklist_json TEXT,
    notes TEXT,
    created_by BIGINT,
    decided_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_hold_point_status CHECK (status IN ('OPEN', 'CLEARED', 'HELD'))
);

CREATE INDEX IF NOT EXISTS idx_hold_point_project ON quality_hold_point(project_id);
CREATE INDEX IF NOT EXISTS idx_hold_point_company ON quality_hold_point(company_id);
CREATE INDEX IF NOT EXISTS idx_hold_point_activity ON quality_hold_point(activity_uuid);

-- Module 11: substitute reason on material plan lines
ALTER TABLE project_material_plan_line ADD COLUMN IF NOT EXISTS substitute_reason VARCHAR(255);
