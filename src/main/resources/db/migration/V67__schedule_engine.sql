-- Module 43: baseline schedule template engine.
-- Adds the work calendar, template library, CPM columns on the live schedule, and the
-- backward-scheduled procurement order-by rows.

CREATE TABLE work_calendar (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    name VARCHAR(120) NOT NULL,
    -- ISO day numbers (1 = Monday .. 7 = Sunday). UAE default is Sat-Thu, so 6,7,1,2,3,4.
    working_days VARCHAR(32) NOT NULL DEFAULT '6,7,1,2,3,4',
    -- Mid-June to mid-September midday break. It restricts hours; it does not remove the day.
    summer_break_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    summer_break_start VARCHAR(8) DEFAULT '06-15',
    summer_break_end VARCHAR(8) DEFAULT '09-15',
    summer_break_window VARCHAR(32) DEFAULT '12:30-15:00',
    ramadan_hours_note TEXT,
    community_restriction_note TEXT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_calendar_company ON work_calendar(company_id);

CREATE TABLE work_calendar_holiday (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_uuid UUID NOT NULL REFERENCES work_calendar(uuid) ON DELETE CASCADE,
    holiday_date DATE NOT NULL,
    name VARCHAR(120),
    CONSTRAINT uq_work_calendar_holiday UNIQUE (calendar_uuid, holiday_date)
);

CREATE TABLE schedule_template (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    project_type VARCHAR(64),
    description TEXT,
    target_calendar_days INT,
    target_working_days INT,
    -- What CPM actually produces from this template's logic, which may differ from the
    -- headline figure the source document quotes.
    computed_working_days INT,
    base_parameters_json TEXT,
    work_week VARCHAR(32),
    is_system_template BOOLEAN NOT NULL DEFAULT TRUE,
    is_fast_track BOOLEAN NOT NULL DEFAULT FALSE,
    fast_track_conditions_json TEXT,
    version INT NOT NULL DEFAULT 1,
    data_quality_notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_schedule_template_global ON schedule_template(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_schedule_template_tenant ON schedule_template(company_id, code) WHERE company_id IS NOT NULL;

CREATE TABLE template_activity (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_uuid UUID NOT NULL REFERENCES schedule_template(uuid) ON DELETE CASCADE,
    activity_code VARCHAR(32) NOT NULL,
    wbs_phase VARCHAR(120),
    name VARCHAR(255) NOT NULL,
    base_duration_days INT NOT NULL DEFAULT 1,
    scaling_method VARCHAR(16) NOT NULL DEFAULT 'PARAMETRIC',
    scaling_driver VARCHAR(32),
    crew_output_ref VARCHAR(120),
    trade_package_code VARCHAR(32),
    trade_label VARCHAR(120),
    is_milestone BOOLEAN NOT NULL DEFAULT FALSE,
    is_critical_seed BOOLEAN NOT NULL DEFAULT FALSE,
    is_locked_duration BOOLEAN NOT NULL DEFAULT FALSE,
    scope_toggle_code VARCHAR(48),
    constraint_note TEXT,
    -- Kept from the source file purely so the variance against pure CPM stays visible.
    seed_start_wd INT,
    seed_finish_wd INT,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_activity UNIQUE (template_uuid, activity_code),
    CONSTRAINT chk_template_scaling CHECK (scaling_method IN ('QUANTITY', 'PARAMETRIC', 'FIXED'))
);

CREATE INDEX idx_template_activity_template ON template_activity(template_uuid);

CREATE TABLE template_dependency (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_uuid UUID NOT NULL REFERENCES schedule_template(uuid) ON DELETE CASCADE,
    predecessor_code VARCHAR(32) NOT NULL,
    successor_code VARCHAR(32) NOT NULL,
    type VARCHAR(4) NOT NULL DEFAULT 'FS',
    lag_days INT NOT NULL DEFAULT 0,
    -- A cure, test or strength-gain period. The compression engine must never shrink these.
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    lock_reason TEXT,
    CONSTRAINT uq_template_dependency UNIQUE (template_uuid, predecessor_code, successor_code),
    CONSTRAINT chk_template_dependency_type CHECK (type IN ('FS', 'SS', 'FF', 'SF'))
);

CREATE INDEX idx_template_dependency_template ON template_dependency(template_uuid);

CREATE TABLE template_procurement_item (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_uuid UUID REFERENCES schedule_template(uuid) ON DELETE CASCADE,
    company_id UUID,
    item_name VARCHAR(255) NOT NULL,
    lead_time_calendar_days_min INT,
    lead_time_calendar_days_max INT,
    lead_time_raw VARCHAR(64),
    order_by_rule TEXT,
    site_info_needed TEXT,
    risk_note TEXT,
    linked_install_activity_code VARCHAR(32),
    match_keywords TEXT,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_template_procurement_template ON template_procurement_item(template_uuid);

CREATE TABLE locked_constraint_rule (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    name VARCHAR(180) NOT NULL,
    minimum_hold_raw VARCHAR(120),
    minimum_hold_working_days INT,
    applies_after TEXT,
    reason TEXT,
    engine_behaviour TEXT,
    is_compressible BOOLEAN NOT NULL DEFAULT FALSE,
    match_keywords TEXT,
    CONSTRAINT uq_locked_constraint_rule UNIQUE (company_id, name)
);

CREATE TABLE productivity_norm (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    work_item VARCHAR(180) NOT NULL,
    unit VARCHAR(32),
    output_per_crew_per_day_min NUMERIC(12,3),
    output_per_crew_per_day_max NUMERIC(12,3),
    output_raw VARCHAR(64),
    standard_crew VARCHAR(180),
    notes TEXT,
    match_keywords TEXT,
    CONSTRAINT uq_productivity_norm UNIQUE (company_id, work_item)
);

CREATE TABLE scope_toggle (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(48) NOT NULL,
    label VARCHAR(180) NOT NULL,
    default_on BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    CONSTRAINT uq_scope_toggle UNIQUE (company_id, code)
);

-- The applied instance: which template, with which parameters, produced this programme.
CREATE TABLE project_schedule (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    template_uuid UUID,
    template_code VARCHAR(32),
    template_version INT,
    parameters_json TEXT,
    toggles_json TEXT,
    work_calendar_uuid UUID,
    data_date DATE,
    baseline_saved_at TIMESTAMPTZ,
    baseline_finish_date DATE,
    current_finish_date DATE,
    computed_working_days INT,
    -- Template A2 cannot publish until the nine fast-track conditions are acknowledged.
    fast_track_ack_json TEXT,
    published_by BIGINT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_schedule UNIQUE (project_id)
);

CREATE INDEX idx_project_schedule_company ON project_schedule(company_id);

-- Backward-scheduled procurement deadlines. Not a purchasing workflow; these are the dates
-- that make the finish date achievable or not.
CREATE TABLE schedule_order_by (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    lead_time_calendar_days INT NOT NULL,
    install_activity_code VARCHAR(32),
    install_activity_uuid UUID,
    install_start_date DATE,
    order_by_date DATE NOT NULL,
    is_overdue BOOLEAN NOT NULL DEFAULT FALSE,
    risk_note TEXT,
    site_info_needed TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_schedule_order_by_project ON schedule_order_by(project_id);

-- CPM columns on the live schedule.
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS activity_code VARCHAR(32);
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS wbs_phase VARCHAR(120);
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS duration_working_days INT;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS scaling_method VARCHAR(16);
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS trade_package_code VARCHAR(32);
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS is_milestone BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS is_locked_duration BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS constraint_note TEXT;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS early_start DATE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS early_finish DATE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS late_start DATE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS late_finish DATE;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS total_float INT;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS free_float INT;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS is_critical BOOLEAN NOT NULL DEFAULT FALSE;
-- Set when an unapproved blocking permit holds the activity back.
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS constrained_by_case_uuid UUID;
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS constraint_start_date DATE;

CREATE INDEX IF NOT EXISTS idx_schedule_activity_code ON schedule_activity(project_id, activity_code);

-- Dependency types beyond FS, with lags and locks.
ALTER TABLE schedule_dependency ADD COLUMN IF NOT EXISTS lag_working_days INT NOT NULL DEFAULT 0;
ALTER TABLE schedule_dependency ADD COLUMN IF NOT EXISTS is_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule_dependency ADD COLUMN IF NOT EXISTS lock_reason TEXT;

ALTER TABLE schedule_dependency DROP CONSTRAINT IF EXISTS chk_schedule_dependency_type;
ALTER TABLE schedule_dependency ADD CONSTRAINT chk_schedule_dependency_type
    CHECK (dependency_type IN ('FS', 'SS', 'FF', 'SF'));

-- Package shells created by the apply cascade.
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS trade_package_code VARCHAR(32);
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS planned_start DATE;
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS planned_finish DATE;
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS activity_codes TEXT;

INSERT INTO work_calendar (name, working_days, is_default, ramadan_hours_note, community_restriction_note)
SELECT 'UAE standard (Sat-Thu)', '6,7,1,2,3,4', TRUE,
       'Ramadan working hours are shortened by two hours; durations are not automatically extended.',
       'Community working-hour restrictions attach here once the project community is known.'
WHERE NOT EXISTS (SELECT 1 FROM work_calendar WHERE is_default = TRUE AND company_id IS NULL);
