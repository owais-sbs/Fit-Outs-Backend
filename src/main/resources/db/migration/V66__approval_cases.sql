-- Module 52 Wave 2: approval cases, checklists, submissions, fees and deposits.
-- One project spawns many cases; the case is the atomic object that carries an owner,
-- a checklist, an SLA clock, a fee, an outcome document and an expiry date.

CREATE TABLE approval_case (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    permit_type_code VARCHAR(32) NOT NULL,
    permit_type_name VARCHAR(255) NOT NULL,
    authority_code VARCHAR(32),
    authority_name VARCHAR(255),
    case_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    assigned_to_account_id BIGINT,
    target_submission_date DATE,
    submitted_date DATE,
    authority_reference VARCHAR(120),
    sla_due_date DATE,
    sla_days INT,
    approved_date DATE,
    permit_number VARCHAR(120),
    permit_file_path TEXT,
    issue_date DATE,
    expiry_date DATE,
    fee_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    deposit_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    deposit_status VARCHAR(24),
    deposit_refund_case_uuid UUID,
    renewal_of_case_uuid UUID,
    prerequisite_permit_codes TEXT,
    blocks_activity_codes TEXT,
    linked_activity_uuids TEXT,
    current_version INT NOT NULL DEFAULT 0,
    escalated_at TIMESTAMPTZ,
    closed_reason VARCHAR(255),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_approval_case_number UNIQUE (company_id, case_number)
);

CREATE INDEX idx_approval_case_project ON approval_case(project_id);
CREATE INDEX idx_approval_case_company_status ON approval_case(company_id, status);
CREATE INDEX idx_approval_case_expiry ON approval_case(expiry_date);
CREATE INDEX idx_approval_case_sla ON approval_case(sla_due_date);
CREATE INDEX idx_approval_case_deposit ON approval_case(company_id, deposit_status);

CREATE TABLE case_checklist_item (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_uuid UUID NOT NULL REFERENCES approval_case(uuid) ON DELETE CASCADE,
    document_type_code VARCHAR(32) NOT NULL,
    document_type_name VARCHAR(255),
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(24) NOT NULL DEFAULT 'MISSING',
    file_path TEXT,
    source VARCHAR(24),
    expiry_date DATE,
    waived_by BIGINT,
    waiver_reason TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_checklist_item UNIQUE (case_uuid, document_type_code),
    CONSTRAINT chk_case_checklist_status CHECK (status IN
        ('MISSING', 'EXPIRED', 'ATTACHED', 'NOT_APPLICABLE', 'WAIVED'))
);

CREATE INDEX idx_case_checklist_case ON case_checklist_item(case_uuid);

CREATE TABLE case_submission (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_uuid UUID NOT NULL REFERENCES approval_case(uuid) ON DELETE CASCADE,
    version INT NOT NULL,
    submitted_by BIGINT,
    submitted_date DATE NOT NULL,
    channel VARCHAR(120),
    receipt_file_path TEXT,
    fee_amount NUMERIC(14,2),
    outcome VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    outcome_date DATE,
    turnaround_days INT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_submission_version UNIQUE (case_uuid, version),
    CONSTRAINT chk_case_submission_outcome CHECK (outcome IN
        ('PENDING', 'APPROVED', 'COMMENTS', 'REJECTED'))
);

CREATE INDEX idx_case_submission_case ON case_submission(case_uuid);

CREATE TABLE case_comment (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_uuid UUID NOT NULL REFERENCES approval_case(uuid) ON DELETE CASCADE,
    submission_uuid UUID REFERENCES case_submission(uuid) ON DELETE CASCADE,
    comment_text TEXT NOT NULL,
    reason_code VARCHAR(64),
    raised_date DATE NOT NULL,
    responded_date DATE,
    response_text TEXT,
    responsible_party VARCHAR(120),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_case_comment_case ON case_comment(case_uuid);

-- Deposits are receivables, not expenses. is_refundable plus the refund dates are what
-- keeps them on the finance dashboard until the money actually comes back.
CREATE TABLE case_fee (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_uuid UUID NOT NULL REFERENCES approval_case(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    type VARCHAR(24) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'AED',
    paid_date DATE,
    payment_ref VARCHAR(120),
    receipt_file_path TEXT,
    cost_code VARCHAR(64),
    is_refundable BOOLEAN NOT NULL DEFAULT FALSE,
    refund_claimed_date DATE,
    refund_received_date DATE,
    refund_amount NUMERIC(14,2),
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_case_fee_type CHECK (type IN ('FEE', 'DEPOSIT', 'FINE', 'KNOWLEDGE_FEE'))
);

CREATE INDEX idx_case_fee_case ON case_fee(case_uuid);
CREATE INDEX idx_case_fee_refundable ON case_fee(company_id, is_refundable, refund_received_date);

CREATE TABLE approval_case_event (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_uuid UUID NOT NULL REFERENCES approval_case(uuid) ON DELETE CASCADE,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    action VARCHAR(64) NOT NULL,
    detail TEXT,
    actor_account_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_approval_case_event_case ON approval_case_event(case_uuid, created_at DESC);

-- The Notifications page is a stub today, so expiry and SLA alerts need somewhere to land
-- besides email.
CREATE TABLE in_app_notification (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    account_id BIGINT,
    category VARCHAR(48) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'INFO',
    title VARCHAR(255) NOT NULL,
    body TEXT,
    link_path VARCHAR(255),
    source_type VARCHAR(48),
    source_uuid UUID,
    dedupe_key VARCHAR(180),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_in_app_notification_dedupe ON in_app_notification(dedupe_key)
    WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_in_app_notification_account ON in_app_notification(account_id, read_at, created_at DESC);
CREATE INDEX idx_in_app_notification_company ON in_app_notification(company_id, created_at DESC);

-- Structured project location and scope. The resolver cannot work from a free-text address.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS emirate VARCHAR(64);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS community_name VARCHAR(180);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS building_name VARCHAR(180);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS plot_zone VARCHAR(180);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS scope_toggles_json TEXT;
