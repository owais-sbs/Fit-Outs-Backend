CREATE TABLE IF NOT EXISTS credit_note (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    note_number VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    reason TEXT,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approval_run_uuid UUID,
    reject_comment TEXT,
    created_by BIGINT,
    approved_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    CONSTRAINT chk_credit_note_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_credit_note_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_credit_note_project
    ON credit_note(project_id, company_id, created_at DESC);
