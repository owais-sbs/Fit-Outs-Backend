-- Phase 2 hardening: planning gates/audit, soft stock hold, quality templates, hold-point activity type

-- M10: company planning gate config
CREATE TABLE IF NOT EXISTS planning_gate_config (
    company_id UUID PRIMARY KEY,
    require_material BOOLEAN NOT NULL DEFAULT FALSE,
    require_resource BOOLEAN NOT NULL DEFAULT FALSE,
    require_labour BOOLEAN NOT NULL DEFAULT FALSE,
    require_subcontractor BOOLEAN NOT NULL DEFAULT FALSE,
    require_planning_ready BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- M10: planning decision audit trail
CREATE TABLE IF NOT EXISTS planning_decision_audit (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    decision_type VARCHAR(64) NOT NULL,
    from_value TEXT,
    to_value TEXT,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_planning_decision_audit_project
    ON planning_decision_audit(project_id, decided_at DESC);
CREATE INDEX IF NOT EXISTS idx_planning_decision_audit_company
    ON planning_decision_audit(company_id);

-- M11: soft stock hold (reserved qty separate from on-hand)
ALTER TABLE material_stock
    ADD COLUMN IF NOT EXISTS quantity_reserved NUMERIC(14, 3) NOT NULL DEFAULT 0;

-- M16: activity type on hold points + company quality checklist templates
ALTER TABLE quality_hold_point
    ADD COLUMN IF NOT EXISTS activity_type VARCHAR(100);

CREATE TABLE IF NOT EXISTS activity_quality_template (
    company_id UUID NOT NULL,
    activity_type VARCHAR(100) NOT NULL,
    checklist_json TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, activity_type)
);
