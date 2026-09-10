-- Wave 6: RFQ / sealed bidding / award
-- Wave 7: Claims pipeline, certificates, retention, back-charges, scorecard, submittals

ALTER TABLE subcontractor_package
    ADD COLUMN IF NOT EXISTS tender_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS tender_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tender_issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quote_validity_days INT,
    ADD COLUMN IF NOT EXISTS payment_terms VARCHAR(128),
    ADD COLUMN IF NOT EXISTS retention_pct NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS site_visit_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tender_description TEXT;

CREATE TABLE IF NOT EXISTS sc_package_bidder (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    company_id UUID NOT NULL,
    invited_at TIMESTAMPTZ,
    viewed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'INVITED',
    eligibility_json TEXT,
    regret_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (package_uuid, organization_uuid)
);

CREATE INDEX IF NOT EXISTS idx_sc_package_bidder_org ON sc_package_bidder(organization_uuid);

CREATE TABLE IF NOT EXISTS sc_quote (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    version INT NOT NULL DEFAULT 1,
    submitted_at TIMESTAMPTZ,
    total_value NUMERIC(18, 2),
    lead_time_days INT,
    exclusions_text TEXT,
    qualifications_text TEXT,
    validity_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sc_quote_package ON sc_quote(package_uuid);
CREATE INDEX IF NOT EXISTS idx_sc_quote_org ON sc_quote(organization_uuid);

CREATE TABLE IF NOT EXISTS sc_quote_line (
    uuid UUID PRIMARY KEY,
    quote_uuid UUID NOT NULL REFERENCES sc_quote(uuid) ON DELETE CASCADE,
    boq_line_id UUID,
    rate NUMERIC(18, 4),
    quantity NUMERIC(18, 4),
    amount NUMERIC(18, 2),
    line_status VARCHAR(32) NOT NULL DEFAULT 'QUOTED',
    remarks TEXT
);

CREATE TABLE IF NOT EXISTS sc_package_clarification (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    organization_uuid UUID REFERENCES sc_organization(uuid),
    question TEXT,
    answer TEXT,
    is_material BOOLEAN NOT NULL DEFAULT FALSE,
    issued_to_all_at TIMESTAMPTZ,
    asked_by_account_id BIGINT,
    answered_by_account_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_package_award (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL UNIQUE REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    quote_uuid UUID REFERENCES sc_quote(uuid),
    awarded_value NUMERIC(18, 2),
    awarded_at TIMESTAMPTZ,
    contract_file_path TEXT,
    signed_at TIMESTAMPTZ,
    signature_audit_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Wave 7: extended claim pipeline
ALTER TABLE subcontractor_claim
    ADD COLUMN IF NOT EXISTS measured_qty NUMERIC(18, 4),
    ADD COLUMN IF NOT EXISTS measured_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS certified_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS measured_by BIGINT,
    ADD COLUMN IF NOT EXISTS measured_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS certificate_uuid UUID;

CREATE TABLE IF NOT EXISTS sc_payment_certificate (
    uuid UUID PRIMARY KEY,
    claim_uuid UUID NOT NULL REFERENCES subcontractor_claim(uuid),
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    organization_uuid UUID REFERENCES sc_organization(uuid),
    certified_value NUMERIC(18, 2),
    retention_held NUMERIC(18, 2),
    back_charges_applied NUMERIC(18, 2),
    net_payable NUMERIC(18, 2),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    paid_date DATE,
    accounting_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_back_charge (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    charge_type VARCHAR(64),
    description TEXT,
    amount NUMERIC(18, 2) NOT NULL,
    evidence_paths TEXT,
    raised_by BIGINT,
    acknowledged_at TIMESTAMPTZ,
    disputed BOOLEAN NOT NULL DEFAULT FALSE,
    applied_to_certificate_uuid UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_retention_ledger (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid),
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    company_id UUID NOT NULL,
    project_id BIGINT NOT NULL,
    amount_held NUMERIC(18, 2) NOT NULL,
    release_date DATE,
    defect_liability_end DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'HELD',
    certificate_uuid UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_subcontractor_score (
    uuid UUID PRIMARY KEY,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    package_uuid UUID REFERENCES subcontractor_package(uuid),
    company_id UUID NOT NULL,
    quality_score NUMERIC(5, 2),
    programme_score NUMERIC(5, 2),
    safety_score NUMERIC(5, 2),
    commercial_score NUMERIC(5, 2),
    responsiveness_score NUMERIC(5, 2),
    total_score NUMERIC(5, 2),
    computed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_submittal (
    uuid UUID PRIMARY KEY,
    package_uuid UUID REFERENCES subcontractor_package(uuid),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    organization_uuid UUID NOT NULL REFERENCES sc_organization(uuid),
    title VARCHAR(255) NOT NULL,
    submittal_type VARCHAR(64),
    revision_no INT NOT NULL DEFAULT 1,
    review_code VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    file_paths TEXT,
    submitted_by BIGINT,
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_portal_notification_pref (
    organization_uuid UUID PRIMARY KEY REFERENCES sc_organization(uuid),
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    whatsapp_number VARCHAR(32),
    preferred_language VARCHAR(16) NOT NULL DEFAULT 'en',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sc_whatsapp_outbox (
    id BIGSERIAL PRIMARY KEY,
    organization_uuid UUID REFERENCES sc_organization(uuid),
    recipient_phone VARCHAR(32),
    message_body TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
