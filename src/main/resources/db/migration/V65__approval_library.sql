-- Module 52 Wave 1: UAE authority and community approval reference library.
-- Rows with company_id IS NULL are the global seed catalogue shared by every tenant.
-- Rows with company_id set are tenant additions (a community or building not in the seed).

CREATE TABLE authority (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    emirate VARCHAR(64),
    jurisdiction_areas TEXT,
    permits_issued_typical TEXT,
    submission_channel TEXT,
    portal_url TEXT,
    contact_json TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    verified_by VARCHAR(120),
    verified_date DATE,
    source_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_authority_type CHECK (type IN (
        'REGULATOR', 'MASTER_DEVELOPER', 'BUILDING_MANAGEMENT', 'UTILITY', 'SPECIAL'))
);

CREATE UNIQUE INDEX uq_authority_code_global ON authority(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_authority_code_tenant ON authority(company_id, code) WHERE company_id IS NOT NULL;
CREATE INDEX idx_authority_emirate ON authority(emirate);
CREATE INDEX idx_authority_type ON authority(type);

-- The jurisdiction resolver lookup. The seed file has no jurisdiction table; these rows are
-- derived by splitting authority.jurisdiction_areas prose, so they land with is_verified = FALSE.
CREATE TABLE jurisdiction (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    emirate VARCHAR(64) NOT NULL,
    community_name VARCHAR(180) NOT NULL,
    community_key VARCHAR(180) NOT NULL,
    building_name VARCHAR(180),
    plot_zone VARCHAR(180),
    regulator_authority_code VARCHAR(32),
    master_developer_authority_code VARCHAR(32),
    building_management_authority_code VARCHAR(32),
    utility_authority_code VARCHAR(32),
    additional_authority_codes TEXT,
    aliases TEXT,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    derived_from VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_jurisdiction_global ON jurisdiction(community_key, COALESCE(building_name, ''))
    WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_jurisdiction_tenant ON jurisdiction(company_id, community_key, COALESCE(building_name, ''))
    WHERE company_id IS NOT NULL;
CREATE INDEX idx_jurisdiction_emirate ON jurisdiction(emirate);
CREATE INDEX idx_jurisdiction_community_key ON jurisdiction(community_key);

CREATE TABLE permit_type (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    -- The seed names an issuing body type ("Master Developer") rather than a specific authority
    -- for community permits, because which developer applies depends on the project's community.
    authority_code VARCHAR(32),
    authority_type VARCHAR(32),
    typical_trigger TEXT,
    trigger_rule_json TEXT,
    prerequisite_permit_codes TEXT,
    prerequisite_notes TEXT,
    indicative_sla_days_min INT,
    indicative_sla_days_max INT,
    indicative_sla_raw VARCHAR(64),
    actual_median_sla_days INT,
    completed_case_count INT NOT NULL DEFAULT 0,
    validity_days_min INT,
    validity_days_max INT,
    validity_raw VARCHAR(64),
    has_deposit BOOLEAN NOT NULL DEFAULT FALSE,
    deposit_conditional BOOLEAN NOT NULL DEFAULT FALSE,
    deposit_amount_indicative NUMERIC(14,2),
    fee_indicative NUMERIC(14,2),
    is_renewable BOOLEAN NOT NULL DEFAULT FALSE,
    blocks_activities_raw TEXT,
    blocks_activity_codes TEXT,
    required_document_codes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    verified_by VARCHAR(120),
    verified_date DATE,
    source_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_permit_type_global ON permit_type(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_permit_type_tenant ON permit_type(company_id, code) WHERE company_id IS NOT NULL;
CREATE INDEX idx_permit_type_authority ON permit_type(authority_code);

CREATE TABLE document_type (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(48),
    typically_required_for TEXT,
    is_expiry_tracked BOOLEAN NOT NULL DEFAULT FALSE,
    owner_role VARCHAR(64),
    source_owner VARCHAR(120),
    template_file_path TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_document_type_global ON document_type(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_document_type_tenant ON document_type(company_id, code) WHERE company_id IS NOT NULL;

-- Company-level compliance documents (trade licence, insurances, specialist approvals).
-- These are what pack assembly auto-collects, and what the company gate checks before submission.
CREATE TABLE company_compliance (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    document_type_code VARCHAR(32) NOT NULL,
    reference_no VARCHAR(120),
    issue_date DATE,
    expiry_date DATE,
    file_path TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'MISSING',
    renewal_owner_account_id BIGINT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_company_compliance UNIQUE (company_id, document_type_code),
    CONSTRAINT chk_company_compliance_status CHECK (status IN ('MISSING', 'ATTACHED', 'EXPIRED', 'NOT_APPLICABLE'))
);

CREATE INDEX idx_company_compliance_expiry ON company_compliance(company_id, expiry_date);

-- Tracks which seed file version was loaded, so re-import is idempotent and auditable.
CREATE TABLE approval_seed_import (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source VARCHAR(255) NOT NULL,
    seed_version VARCHAR(32),
    imported_by BIGINT,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    summary_json TEXT
);
