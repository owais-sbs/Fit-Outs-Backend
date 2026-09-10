-- Wave 5: Cross-tenant subcontractor organization + tenant membership + portal roles.

CREATE TABLE sc_organization (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_company_name VARCHAR(255),
    trade_licence_number VARCHAR(120),
    trade_licence_authority VARCHAR(160),
    trade_licence_activities TEXT,
    trade_licence_file_path TEXT,
    trade_licence_expiry DATE,
    establishment_card_file_path TEXT,
    establishment_card_expiry DATE,
    trn VARCHAR(64),
    vat_certificate_file_path TEXT,
    registered_address TEXT,
    location_pin VARCHAR(120),
    primary_contact_name VARCHAR(160),
    primary_contact_email VARCHAR(255),
    primary_contact_phone VARCHAR(64),
    accounts_contact_name VARCHAR(160),
    accounts_contact_email VARCHAR(255),
    accounts_contact_phone VARCHAR(64),
    trade_categories TEXT,
    declared_capacity VARCHAR(255),
    monthly_capacity_value NUMERIC(14, 2),
    monthly_capacity_manpower INT,
    years_in_operation INT,
    annual_turnover_band VARCHAR(64),
    workshop_address TEXT,
    workshop_photos TEXT,
    hse_policy_file_path TEXT,
    hse_lti_count INT,
    hse_officer_name VARCHAR(160),
    hse_officer_cert_file_path TEXT,
    hse_officer_cert_expiry DATE,
    payment_terms_accepted VARCHAR(64),
    retention_pct_accepted NUMERIC(5, 2),
    advance_payment_required NUMERIC(14, 2),
    workforce_total INT,
    workforce_trade_breakdown TEXT,
    performance_score NUMERIC(5, 2),
    is_cross_tenant_visible BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sc_organization_name ON sc_organization(legal_company_name);

-- Per-GC tenant relationship (AVL / prequalification status).
CREATE TABLE sc_tenant_membership (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    company_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'INVITED',
    approved_trades TEXT,
    max_package_value NUMERIC(14, 2),
    prequalification_notes TEXT,
    reviewed_by_account_id BIGINT REFERENCES accounts(id),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_tenant_membership_status CHECK (
        status IN ('INVITED', 'REGISTERED', 'UNDER_REVIEW', 'APPROVED', 'CONDITIONAL', 'SUSPENDED', 'BLACKLISTED')
    )
);

CREATE UNIQUE INDEX uq_sc_tenant_membership ON sc_tenant_membership(organization_uuid, company_id);
CREATE INDEX idx_sc_tenant_membership_company ON sc_tenant_membership(company_id);
CREATE INDEX idx_sc_tenant_membership_status ON sc_tenant_membership(company_id, status);

-- Portal users within a subcontractor organization (C5 roles).
CREATE TABLE sc_portal_user (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    portal_role VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    invited_by_account_id BIGINT REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_portal_role CHECK (
        portal_role IN ('SC_ADMIN', 'SC_ESTIMATOR', 'SC_SUPERVISOR', 'SC_QS', 'SC_DOC_CONTROLLER')
    ),
    CONSTRAINT chk_sc_portal_user_status CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_sc_portal_user_account ON sc_portal_user(account_id);
CREATE INDEX idx_sc_portal_user_org ON sc_portal_user(organization_uuid);

-- Extended document vault (C7 document types).
CREATE TABLE sc_organization_document (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    document_code VARCHAR(32) NOT NULL,
    reference_no VARCHAR(120),
    issue_date DATE,
    expiry_date DATE,
    file_path TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_sc_org_document ON sc_organization_document(organization_uuid, document_code);
CREATE INDEX idx_sc_org_document_org ON sc_organization_document(organization_uuid);

-- Community / master developer registrations (C7, blocks award per community).
CREATE TABLE sc_community_registration (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    authority_code VARCHAR(32) NOT NULL,
    authority_name VARCHAR(160),
    registration_no VARCHAR(120),
    expiry_date DATE,
    file_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sc_community_reg_org ON sc_community_registration(organization_uuid);

-- Bank details with verification gate (C12 rule 4).
CREATE TABLE sc_bank_detail (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    bank_name VARCHAR(160),
    account_name VARCHAR(160),
    iban VARCHAR(64),
    swift_code VARCHAR(32),
    bank_letter_file_path TEXT,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    verified_by_account_id BIGINT REFERENCES accounts(id),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_bank_verification CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'CHANGE_PENDING')
    )
);

CREATE UNIQUE INDEX uq_sc_bank_org ON sc_bank_detail(organization_uuid);

-- Reference projects (C7 experience).
CREATE TABLE sc_organization_reference (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid) ON DELETE CASCADE,
    client_name VARCHAR(160),
    project_value NUMERIC(14, 2),
    year_completed INT,
    scope_description TEXT,
    contact_name VARCHAR(160),
    contact_phone VARCHAR(64),
    contact_email VARCHAR(255),
    photo_paths TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sc_org_reference_org ON sc_organization_reference(organization_uuid);

-- Public self-registration tokens.
CREATE TABLE sc_registration_invite (
    token UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    email VARCHAR(255) NOT NULL,
    organization_uuid UUID REFERENCES sc_organization(uuid),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_by_account_id BIGINT REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sc_registration_invite_email ON sc_registration_invite(email);

-- Link legacy profile rows to organizations.
ALTER TABLE sc_company_profile ADD COLUMN IF NOT EXISTS organization_uuid UUID REFERENCES sc_organization(uuid);
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS organization_uuid UUID REFERENCES sc_organization(uuid);
ALTER TABLE sc_compliance_document ADD COLUMN IF NOT EXISTS organization_uuid UUID REFERENCES sc_organization(uuid);

-- Backfill: one organization per existing company profile.
DO $$
DECLARE
    r RECORD;
    new_org UUID;
BEGIN
    FOR r IN SELECT * FROM sc_company_profile WHERE organization_uuid IS NULL LOOP
        new_org := gen_random_uuid();
        INSERT INTO sc_organization (
            uuid, legal_company_name, trade_licence_number, trade_licence_file_path,
            trade_licence_expiry, trn, registered_address, primary_contact_name,
            primary_contact_email, primary_contact_phone, accounts_contact_name,
            accounts_contact_email, accounts_contact_phone, trade_categories,
            declared_capacity, created_at, updated_at
        ) VALUES (
            new_org, r.legal_company_name, r.trade_licence_number, r.trade_licence_file_path,
            r.trade_licence_expiry, r.trn, r.registered_address, r.primary_contact_name,
            r.primary_contact_email, r.primary_contact_phone, r.accounts_contact_name,
            r.accounts_contact_email, r.accounts_contact_phone, r.trade_categories,
            r.declared_capacity, r.created_at, r.updated_at
        );
        UPDATE sc_company_profile SET organization_uuid = new_org WHERE uuid = r.uuid;
        INSERT INTO sc_tenant_membership (
            uuid, organization_uuid, company_id, status, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), new_org, r.company_id, r.status, r.created_at, r.updated_at
        );
        INSERT INTO sc_portal_user (
            uuid, organization_uuid, account_id, portal_role, status, created_at, updated_at
        ) VALUES (
            gen_random_uuid(), new_org, r.admin_account_id, 'SC_ADMIN', 'ACTIVE', r.created_at, r.updated_at
        )
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

UPDATE sc_worker w
SET organization_uuid = p.organization_uuid
FROM sc_company_profile p
WHERE w.profile_uuid = p.uuid AND w.organization_uuid IS NULL;

UPDATE sc_compliance_document d
SET organization_uuid = p.organization_uuid
FROM sc_company_profile p
WHERE d.profile_uuid = p.uuid AND d.organization_uuid IS NULL;

-- Expand compliance doc types for C7.
ALTER TABLE sc_compliance_document DROP CONSTRAINT IF EXISTS chk_sc_compliance_doc_type;
ALTER TABLE sc_compliance_document ADD CONSTRAINT chk_sc_compliance_doc_type CHECK (
    document_type IN (
        'CAR_INSURANCE', 'TPL_INSURANCE', 'WC_INSURANCE',
        'CIVIL_DEFENCE', 'SIRA', 'DEWA_ELECTRICAL',
        'MUNICIPALITY_CLASSIFICATION', 'ISO_9001', 'ISO_45001', 'ISO_14001',
        'CHAMBER_OF_COMMERCE'
    )
);

-- Worker: induction + trade certificates + access card (C5).
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS trade_cert_expiry DATE;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS induction_completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS access_card_number VARCHAR(64);
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS photo_file_path TEXT;
