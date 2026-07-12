-- BOQ approval workflow, version control, and QS roles

ALTER TABLE account_roles DROP CONSTRAINT IF EXISTS account_roles_role_check;

ALTER TABLE account_roles ADD CONSTRAINT account_roles_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'ADMIN',
        'BUSINESS_OWNER',
        'PROJECT_MANAGER',
        'DESIGNER',
        'QAS',
        'QS',
        'SENIOR_QS',
        'FINANCE',
        'SUBCONTRACTOR',
        'CLIENT',
        'SALES',
        'EMPLOYEE'
    ));

-- Migrate legacy FINAL status to APPROVED
UPDATE boq_documents SET status = 'APPROVED' WHERE status = 'FINAL';

ALTER TABLE boq_documents ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE boq_documents
    ADD COLUMN IF NOT EXISTS parent_boq_id UUID REFERENCES boq_documents(id),
    ADD COLUMN IF NOT EXISTS revision_label VARCHAR(100),
    ADD COLUMN IF NOT EXISTS current_approval_step VARCHAR(30),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS submitted_by BIGINT,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_by BIGINT,
    ADD COLUMN IF NOT EXISTS last_rejection_comment TEXT;

CREATE INDEX IF NOT EXISTS idx_boq_documents_parent ON boq_documents(parent_boq_id);
CREATE INDEX IF NOT EXISTS idx_boq_documents_status ON boq_documents(status);
CREATE INDEX IF NOT EXISTS idx_boq_documents_company_status ON boq_documents(company_id, status);

CREATE TABLE IF NOT EXISTS boq_approval_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    boq_id UUID NOT NULL REFERENCES boq_documents(id) ON DELETE CASCADE,
    step VARCHAR(30) NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_id BIGINT,
    actor_role VARCHAR(30),
    actor_name VARCHAR(200),
    comments TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_boq_approval_log_boq ON boq_approval_log(boq_id, created_at);
