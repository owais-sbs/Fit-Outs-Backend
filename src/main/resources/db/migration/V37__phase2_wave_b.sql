-- Phase 2 Wave B: Material plan, Resource & Labour, PM Validation

-- Module 11: Material plan
CREATE TABLE project_material_plan (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_from_boq_id UUID,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_material_plan UNIQUE (project_id, company_id),
    CONSTRAINT chk_material_plan_status CHECK (status IN ('DRAFT', 'READY'))
);

CREATE INDEX idx_material_plan_project ON project_material_plan(project_id);
CREATE INDEX idx_material_plan_company ON project_material_plan(company_id);

CREATE TABLE project_material_plan_line (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_uuid UUID NOT NULL REFERENCES project_material_plan(uuid) ON DELETE CASCADE,
    material_id UUID,
    material_name VARCHAR(255) NOT NULL,
    planned_qty NUMERIC(14,4) NOT NULL DEFAULT 0,
    stock_qty_snapshot NUMERIC(14,4) NOT NULL DEFAULT 0,
    unit VARCHAR(32),
    shortage_flag BOOLEAN NOT NULL DEFAULT FALSE,
    reserved_qty NUMERIC(14,4) NOT NULL DEFAULT 0,
    notes TEXT,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_material_plan_line_plan ON project_material_plan_line(plan_uuid);

-- Module 12: Resource & Labour
CREATE TABLE resource_type (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_resource_type_kind CHECK (kind IN ('PLANT', 'TOOL', 'LABOUR'))
);

CREATE INDEX idx_resource_type_company ON resource_type(company_id);

CREATE TABLE labour_crew (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    headcount INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_labour_crew_headcount CHECK (headcount >= 1)
);

CREATE INDEX idx_labour_crew_company ON labour_crew(company_id);

CREATE TABLE activity_crew_assignment (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    crew_uuid UUID NOT NULL REFERENCES labour_crew(uuid),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_crew_assignment_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_crew_assignment_project ON activity_crew_assignment(project_id);
CREATE INDEX idx_crew_assignment_crew ON activity_crew_assignment(crew_uuid);
CREATE INDEX idx_crew_assignment_activity ON activity_crew_assignment(activity_uuid);

-- Module 16: PM Validation
CREATE TABLE progress_validation (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_update_uuid UUID NOT NULL REFERENCES activity_progress_update(uuid) ON DELETE CASCADE,
    activity_uuid UUID NOT NULL,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_progress_validation_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT uq_progress_validation_progress UNIQUE (progress_update_uuid)
);

CREATE INDEX idx_progress_validation_company_status ON progress_validation(company_id, status);
CREATE INDEX idx_progress_validation_project ON progress_validation(project_id);
