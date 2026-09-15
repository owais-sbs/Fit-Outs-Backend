-- Module 21: client-contract variations + Module 23: commercial approval matrices

-- ── Commercial approval matrix (tenant config) ───────────────────────────────

CREATE TABLE IF NOT EXISTS commercial_approval_matrix (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cam_event_type CHECK (event_type IN ('VARIATION', 'SC_CERTIFICATE', 'CREDIT_NOTE'))
);

CREATE INDEX IF NOT EXISTS idx_cam_company_event
    ON commercial_approval_matrix(company_id, event_type);

CREATE TABLE IF NOT EXISTS commercial_approval_band (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matrix_uuid UUID NOT NULL REFERENCES commercial_approval_matrix(uuid) ON DELETE CASCADE,
    min_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    max_amount NUMERIC(14,2),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cab_matrix ON commercial_approval_band(matrix_uuid);

CREATE TABLE IF NOT EXISTS commercial_approval_step (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    band_uuid UUID NOT NULL REFERENCES commercial_approval_band(uuid) ON DELETE CASCADE,
    step_order INT NOT NULL DEFAULT 1,
    mode VARCHAR(20) NOT NULL DEFAULT 'SEQUENTIAL',
    sla_hours INT NOT NULL DEFAULT 48,
    escalate_to_role VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cas_mode CHECK (mode IN ('SEQUENTIAL', 'PARALLEL'))
);

CREATE INDEX IF NOT EXISTS idx_cas_band ON commercial_approval_step(band_uuid);

CREATE TABLE IF NOT EXISTS commercial_approval_step_role (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_uuid UUID NOT NULL REFERENCES commercial_approval_step(uuid) ON DELETE CASCADE,
    role VARCHAR(40) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_casr_step ON commercial_approval_step_role(step_uuid);

-- ── Runtime approval runs ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS commercial_approval_run (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    project_id BIGINT,
    event_type VARCHAR(32) NOT NULL,
    entity_uuid UUID NOT NULL,
    matrix_uuid UUID,
    band_uuid UUID,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    current_step_order INT NOT NULL DEFAULT 1,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_car_event_type CHECK (event_type IN ('VARIATION', 'SC_CERTIFICATE', 'CREDIT_NOTE')),
    CONSTRAINT chk_car_status CHECK (status IN ('IN_PROGRESS', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_car_entity ON commercial_approval_run(event_type, entity_uuid);
CREATE INDEX IF NOT EXISTS idx_car_company_status ON commercial_approval_run(company_id, status);

CREATE TABLE IF NOT EXISTS commercial_approval_task (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_uuid UUID NOT NULL REFERENCES commercial_approval_run(uuid) ON DELETE CASCADE,
    step_uuid UUID,
    step_order INT NOT NULL,
    role VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMPTZ,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    comment TEXT,
    reminder_sent_at TIMESTAMPTZ,
    escalated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cat_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SKIPPED', 'WAITING'))
);

CREATE INDEX IF NOT EXISTS idx_cat_run ON commercial_approval_task(run_uuid);
CREATE INDEX IF NOT EXISTS idx_cat_pending ON commercial_approval_task(status, due_at)
    WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS commercial_approval_event (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_uuid UUID NOT NULL REFERENCES commercial_approval_run(uuid) ON DELETE CASCADE,
    company_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40),
    actor_id BIGINT,
    amount_snapshot NUMERIC(14,2),
    detail TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cae_run ON commercial_approval_event(run_uuid);
CREATE INDEX IF NOT EXISTS idx_cae_company_created ON commercial_approval_event(company_id, created_at);

-- ── Project commercial running totals ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS project_commercial (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    original_contract_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    current_contract_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_cost NUMERIC(14,2),
    current_cost NUMERIC(14,2),
    current_margin NUMERIC(14,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_commercial UNIQUE (project_id, company_id)
);

-- ── Variation / change requests (Module 21) ──────────────────────────────────

CREATE TABLE IF NOT EXISTS variation_request (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    cr_number VARCHAR(32) NOT NULL,
    origin VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    reason_code VARCHAR(40),
    cost_mode VARCHAR(20) NOT NULL DEFAULT 'LUMP_SUM',
    sell_delta NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_delta NUMERIC(14,2) NOT NULL DEFAULT 0,
    proposed_delay_days INT,
    apply_schedule_on_approval BOOLEAN NOT NULL DEFAULT FALSE,
    locked_at TIMESTAMPTZ,
    raised_by BIGINT,
    submitted_by BIGINT,
    submitted_at TIMESTAMPTZ,
    issued_by BIGINT,
    issued_at TIMESTAMPTZ,
    approved_by BIGINT,
    approved_at TIMESTAMPTZ,
    triage_note TEXT,
    reject_comment TEXT,
    approval_run_uuid UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_vr_origin CHECK (origin IN ('PM', 'QS', 'CLIENT')),
    CONSTRAINT chk_vr_status CHECK (status IN (
        'AWAITING_TRIAGE', 'DRAFT', 'INTERNAL_REVIEW', 'ISSUED_TO_CLIENT',
        'APPROVED', 'REJECTED', 'REVISED')),
    CONSTRAINT chk_vr_cost_mode CHECK (cost_mode IN ('BOQ_LINES', 'LUMP_SUM', 'MIXED')),
    CONSTRAINT uq_vr_cr_number UNIQUE (company_id, project_id, cr_number)
);

CREATE INDEX IF NOT EXISTS idx_vr_project ON variation_request(project_id, company_id);
CREATE INDEX IF NOT EXISTS idx_vr_company_status ON variation_request(company_id, status);

CREATE TABLE IF NOT EXISTS variation_line (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variation_uuid UUID NOT NULL REFERENCES variation_request(uuid) ON DELETE CASCADE,
    line_type VARCHAR(20) NOT NULL,
    source_boq_line_id UUID,
    work_item_id UUID,
    description TEXT,
    unit VARCHAR(32),
    quantity NUMERIC(14,4) NOT NULL DEFAULT 0,
    sell_rate NUMERIC(14,2) NOT NULL DEFAULT 0,
    sell_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_rate NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_vl_line_type CHECK (line_type IN ('NEW', 'MODIFY', 'LUMP_SUM'))
);

CREATE INDEX IF NOT EXISTS idx_vl_variation ON variation_line(variation_uuid);

CREATE TABLE IF NOT EXISTS variation_link (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variation_uuid UUID NOT NULL REFERENCES variation_request(uuid) ON DELETE CASCADE,
    link_type VARCHAR(16) NOT NULL,
    room_id UUID,
    activity_uuid UUID,
    CONSTRAINT chk_vlink_type CHECK (link_type IN ('ROOM', 'ACTIVITY'))
);

CREATE INDEX IF NOT EXISTS idx_vlink_variation ON variation_link(variation_uuid);

CREATE TABLE IF NOT EXISTS variation_attachment (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variation_uuid UUID NOT NULL REFERENCES variation_request(uuid) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    original_name VARCHAR(255),
    uploaded_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_va_variation ON variation_attachment(variation_uuid);

CREATE TABLE IF NOT EXISTS variation_event (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variation_uuid UUID NOT NULL REFERENCES variation_request(uuid) ON DELETE CASCADE,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(40),
    to_status VARCHAR(40),
    actor_id BIGINT,
    detail TEXT,
    previous_contract_value NUMERIC(14,2),
    new_contract_value NUMERIC(14,2),
    previous_margin NUMERIC(14,2),
    new_margin NUMERIC(14,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ve_variation ON variation_event(variation_uuid);
