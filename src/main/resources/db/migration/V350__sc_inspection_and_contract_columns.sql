-- Subcontractor QA/QC hold-point inspection requests + contract signature columns on awards.

CREATE TABLE IF NOT EXISTS sc_inspection_request (
    uuid UUID PRIMARY KEY,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    organization_uuid UUID NOT NULL,
    activity_uuid UUID,
    inspection_type VARCHAR(64) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    notice_period_hours INT,
    description TEXT,
    attachments TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    result_notes TEXT,
    evidence_paths TEXT,
    inspected_by BIGINT,
    inspected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_inspection_status CHECK (
        status IN ('SUBMITTED', 'SCHEDULED', 'APPROVED', 'REJECTED', 'RESUBMISSION_REQUESTED')
    )
);

CREATE INDEX IF NOT EXISTS idx_sc_inspection_project_company
    ON sc_inspection_request(project_id, company_id);
CREATE INDEX IF NOT EXISTS idx_sc_inspection_package_org
    ON sc_inspection_request(package_uuid, organization_uuid, company_id);

ALTER TABLE sc_package_award
    ADD COLUMN IF NOT EXISTS contract_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS admin_signed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS admin_signed_by BIGINT,
    ADD COLUMN IF NOT EXISTS admin_signer_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS admin_signer_title VARCHAR(160),
    ADD COLUMN IF NOT EXISTS admin_signature_audit_json TEXT,
    ADD COLUMN IF NOT EXISTS subcontractor_signed_by BIGINT,
    ADD COLUMN IF NOT EXISTS subcontractor_signer_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS subcontractor_signer_title VARCHAR(160);
