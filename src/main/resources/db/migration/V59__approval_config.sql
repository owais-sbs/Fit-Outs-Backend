CREATE TABLE approval_authorities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    emirate VARCHAR(80),
    jurisdiction_areas TEXT,
    permits_issued TEXT,
    submission_channel VARCHAR(255),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, code)
);

CREATE INDEX idx_approval_authorities_company ON approval_authorities (company_id) WHERE deleted = FALSE;

CREATE TABLE approval_permit_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    permit_code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    issuing_body VARCHAR(120),
    typical_trigger TEXT,
    prerequisite_cases TEXT,
    sla_working_days VARCHAR(40),
    typical_validity VARCHAR(80),
    deposit VARCHAR(80),
    renewable VARCHAR(20),
    blocks_activities TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, permit_code)
);

CREATE INDEX idx_approval_permit_types_company ON approval_permit_types (company_id) WHERE deleted = FALSE;

CREATE TABLE approval_document_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    doc_code VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(80),
    typically_required_for TEXT,
    expiry_tracked BOOLEAN NOT NULL DEFAULT FALSE,
    source_owner VARCHAR(120),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, doc_code)
);

CREATE INDEX idx_approval_document_types_company ON approval_document_types (company_id) WHERE deleted = FALSE;

CREATE TABLE jurisdiction_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    community_authority_id UUID REFERENCES approval_authorities (id),
    primary_authority_id UUID REFERENCES approval_authorities (id),
    selectable BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, code)
);

CREATE INDEX idx_jurisdiction_packs_company ON jurisdiction_packs (company_id) WHERE deleted = FALSE;

CREATE TABLE jurisdiction_pack_permits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_id UUID NOT NULL REFERENCES jurisdiction_packs (id) ON DELETE CASCADE,
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types (id),
    issuing_authority_id UUID REFERENCES approval_authorities (id),
    inclusion_rule VARCHAR(40) NOT NULL DEFAULT 'ALWAYS',
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_jurisdiction_pack_permits_pack ON jurisdiction_pack_permits (pack_id);

CREATE TABLE jurisdiction_pack_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pack_permit_id UUID NOT NULL REFERENCES jurisdiction_pack_permits (id) ON DELETE CASCADE,
    document_type_id UUID NOT NULL REFERENCES approval_document_types (id)
);

CREATE INDEX idx_jurisdiction_pack_documents_permit ON jurisdiction_pack_documents (pack_permit_id);

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS jurisdiction_pack_id UUID REFERENCES jurisdiction_packs (id),
    ADD COLUMN IF NOT EXISTS approval_scope_kitchen BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approval_scope_cctv BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approval_scope_rta BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approval_scope_demo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS approval_scope_load BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE project_permit_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL REFERENCES projects (id),
    company_id UUID NOT NULL REFERENCES companies (uuid),
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types (id),
    issuing_authority_id UUID REFERENCES approval_authorities (id),
    status VARCHAR(40) NOT NULL DEFAULT 'Not started',
    sla_working_days VARCHAR(40),
    blocks_activities TEXT,
    inclusion_rule VARCHAR(40),
    sort_order INT NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_permit_cases_project ON project_permit_cases (project_id);

CREATE TABLE project_permit_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES project_permit_cases (id) ON DELETE CASCADE,
    document_type_id UUID NOT NULL REFERENCES approval_document_types (id),
    status VARCHAR(40) NOT NULL DEFAULT 'Pending',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_permit_documents_case ON project_permit_documents (case_id);
