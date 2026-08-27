-- Phase 2 Wave C: Subcontractor, Snags, Documents

-- Module 13: Subcontractor packages & claims
CREATE TABLE subcontractor_package (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    boq_section_code VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    appointed_account_id BIGINT,
    appointed_company_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_package_status CHECK (status IN ('OPEN', 'APPOINTED', 'IN_PROGRESS', 'COMPLETE'))
);

CREATE INDEX idx_sc_package_project ON subcontractor_package(project_id);
CREATE INDEX idx_sc_package_company ON subcontractor_package(company_id);
CREATE INDEX idx_sc_package_appointed ON subcontractor_package(appointed_account_id);

CREATE TABLE subcontractor_claim (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    claimed_qty NUMERIC(14,4) NOT NULL DEFAULT 0,
    planned_qty NUMERIC(14,4) NOT NULL DEFAULT 0,
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submitted_by BIGINT,
    submitted_at TIMESTAMPTZ,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_claim_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_sc_claim_package ON subcontractor_claim(package_uuid);
CREATE INDEX idx_sc_claim_project ON subcontractor_claim(project_id);
CREATE INDEX idx_sc_claim_company_status ON subcontractor_claim(company_id, status);

-- Module 17: Snags
CREATE TABLE snag (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    photo_paths TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    raised_by BIGINT,
    assignee_account_id BIGINT,
    client_visible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_snag_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_snag_project ON snag(project_id);
CREATE INDEX idx_snag_company ON snag(company_id);
CREATE INDEX idx_snag_client_visible ON snag(project_id, client_visible);

-- Module 18: Project documents
CREATE TABLE project_document (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    file_path VARCHAR(1024) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    published_to_client BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by BIGINT,
    parent_document_uuid UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_project_document_project ON project_document(project_id);
CREATE INDEX idx_project_document_company ON project_document(company_id);
CREATE INDEX idx_project_document_published ON project_document(project_id, published_to_client);
CREATE INDEX idx_project_document_parent ON project_document(parent_document_uuid);
