-- Phase 2 Wave A: Planning Hub, Gantt schedule, progress updates

CREATE TABLE project_planning_status (
    project_id BIGINT PRIMARY KEY,
    company_id UUID NOT NULL,
    material_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    resource_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    labour_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    subcontractor_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    planning_ready BOOLEAN NOT NULL DEFAULT FALSE,
    gantt_publish_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_planning_status_company ON project_planning_status(company_id);

CREATE TABLE schedule_activity (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    percent_complete INT NOT NULL DEFAULT 0,
    weight NUMERIC(10,2) NOT NULL DEFAULT 1,
    parent_uuid UUID,
    project_room_id UUID,
    room_task_id UUID,
    assignee_account_id BIGINT,
    publish_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_schedule_activity_percent CHECK (percent_complete >= 0 AND percent_complete <= 100),
    CONSTRAINT chk_schedule_activity_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_schedule_activity_project ON schedule_activity(project_id);
CREATE INDEX idx_schedule_activity_company ON schedule_activity(company_id);
CREATE INDEX idx_schedule_activity_assignee ON schedule_activity(assignee_account_id);

CREATE TABLE schedule_dependency (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    predecessor_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    successor_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    dependency_type VARCHAR(8) NOT NULL DEFAULT 'FS',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schedule_dependency UNIQUE (predecessor_uuid, successor_uuid)
);

CREATE INDEX idx_schedule_dependency_project ON schedule_dependency(project_id);

CREATE TABLE schedule_baseline (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedule_baseline_project ON schedule_baseline(project_id);

CREATE TABLE schedule_baseline_activity (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    baseline_uuid UUID NOT NULL REFERENCES schedule_baseline(uuid) ON DELETE CASCADE,
    activity_uuid UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    percent_complete INT NOT NULL DEFAULT 0,
    weight NUMERIC(10,2) NOT NULL DEFAULT 1
);

CREATE INDEX idx_schedule_baseline_activity_baseline ON schedule_baseline_activity(baseline_uuid);

CREATE TABLE activity_progress_update (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    percent_complete INT NOT NULL,
    notes TEXT,
    labour_hours NUMERIC(10,2),
    photo_paths TEXT,
    reported_by BIGINT NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_progress_percent CHECK (percent_complete >= 0 AND percent_complete <= 100)
);

CREATE INDEX idx_activity_progress_activity ON activity_progress_update(activity_uuid, reported_at DESC);
CREATE INDEX idx_activity_progress_project ON activity_progress_update(project_id);
