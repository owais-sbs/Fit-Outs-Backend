-- Phase 2 Wave D: Progress reporting + Milestone billing

-- Module 19: optional delay reason on schedule activities
ALTER TABLE schedule_activity ADD COLUMN IF NOT EXISTS delay_reason VARCHAR(64);

-- Module 20: Milestone billing
CREATE TABLE billing_milestone (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    due_date DATE,
    linked_activity_uuid UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    percent_complete_required NUMERIC(5,2),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_billing_milestone_status CHECK (status IN ('DRAFT', 'PENDING_PM', 'ISSUED', 'PAID', 'PART_PAID'))
);

CREATE INDEX idx_billing_milestone_project ON billing_milestone(project_id);
CREATE INDEX idx_billing_milestone_company ON billing_milestone(company_id);

CREATE TABLE payment_request (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    milestone_uuid UUID NOT NULL REFERENCES billing_milestone(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes TEXT,
    requested_by BIGINT,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_request_status CHECK (status IN ('DRAFT', 'PENDING_PM', 'ISSUED', 'PAID', 'PART_PAID'))
);

CREATE INDEX idx_payment_request_milestone ON payment_request(milestone_uuid);
CREATE INDEX idx_payment_request_project ON payment_request(project_id);
CREATE INDEX idx_payment_request_company_status ON payment_request(company_id, status);
