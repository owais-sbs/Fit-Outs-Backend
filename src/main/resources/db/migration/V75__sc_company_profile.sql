-- Subcontractor vendor company profile (one per SC admin account per tenant).
CREATE TABLE sc_company_profile (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    admin_account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    legal_company_name VARCHAR(255),
    trade_licence_number VARCHAR(120),
    trade_licence_file_path TEXT,
    trade_licence_expiry DATE,
    trn VARCHAR(64),
    registered_address TEXT,
    primary_contact_name VARCHAR(160),
    primary_contact_email VARCHAR(255),
    primary_contact_phone VARCHAR(64),
    accounts_contact_name VARCHAR(160),
    accounts_contact_email VARCHAR(255),
    accounts_contact_phone VARCHAR(64),
    trade_categories TEXT,
    declared_capacity VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'INVITED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_company_status CHECK (
        status IN ('INVITED', 'REGISTERED', 'UNDER_REVIEW', 'APPROVED', 'CONDITIONAL', 'SUSPENDED', 'BLACKLISTED')
    )
);

CREATE UNIQUE INDEX uq_sc_company_profile_account ON sc_company_profile(company_id, admin_account_id);
CREATE INDEX idx_sc_company_profile_company ON sc_company_profile(company_id);

-- Insurance and specialist compliance documents (trade licence lives on profile).
CREATE TABLE sc_compliance_document (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_uuid UUID NOT NULL REFERENCES sc_company_profile(uuid) ON DELETE CASCADE,
    document_type VARCHAR(32) NOT NULL,
    reference_no VARCHAR(120),
    expiry_date DATE,
    file_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_compliance_doc_type CHECK (
        document_type IN ('CAR_INSURANCE', 'TPL_INSURANCE', 'WC_INSURANCE', 'CIVIL_DEFENCE', 'SIRA', 'DEWA_ELECTRICAL')
    )
);

CREATE UNIQUE INDEX uq_sc_compliance_doc ON sc_compliance_document(profile_uuid, document_type);
CREATE INDEX idx_sc_compliance_doc_profile ON sc_compliance_document(profile_uuid);

-- Worker roster (data only — no login).
CREATE TABLE sc_worker (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_uuid UUID NOT NULL REFERENCES sc_company_profile(uuid) ON DELETE CASCADE,
    full_name VARCHAR(160) NOT NULL,
    trade VARCHAR(120),
    passport_number VARCHAR(64),
    visa_expiry DATE,
    emirates_id_expiry DATE,
    insurance_expiry DATE,
    induction_date DATE,
    access_card_expiry DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sc_worker_profile ON sc_worker(profile_uuid);
