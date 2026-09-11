-- Module 13 extensions: SC variations, site reports, invoicing, progress delay reason

ALTER TABLE activity_progress_update
    ADD COLUMN IF NOT EXISTS delay_reason VARCHAR(64);

-- Variation / change requests
CREATE TABLE IF NOT EXISTS sc_variation_request (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    qty_delta NUMERIC(14,4),
    unit VARCHAR(32),
    estimated_cost NUMERIC(14,2),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    attachment_paths TEXT,
    submitted_by BIGINT,
    submitted_at TIMESTAMPTZ,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_variation_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_sc_variation_package ON sc_variation_request(package_uuid);
CREATE INDEX IF NOT EXISTS idx_sc_variation_project ON sc_variation_request(project_id);
CREATE INDEX IF NOT EXISTS idx_sc_variation_company_status ON sc_variation_request(company_id, status);

-- Delay / issue / material delivery reports
CREATE TABLE IF NOT EXISTS sc_site_report (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_uuid UUID REFERENCES subcontractor_package(uuid) ON DELETE SET NULL,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    report_type VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    delay_reason_code VARCHAR(64),
    expected_date DATE,
    delivered_date DATE,
    material_name VARCHAR(255),
    quantity NUMERIC(14,4),
    unit VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    attachment_paths TEXT,
    raised_by BIGINT,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMPTZ,
    resolved_by BIGINT,
    resolved_at TIMESTAMPTZ,
    resolution_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_site_report_type CHECK (report_type IN ('DELAY', 'ISSUE', 'MATERIAL_DELIVERY')),
    CONSTRAINT chk_sc_site_report_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_sc_site_report_project ON sc_site_report(project_id);
CREATE INDEX IF NOT EXISTS idx_sc_site_report_company ON sc_site_report(company_id);
CREATE INDEX IF NOT EXISTS idx_sc_site_report_type ON sc_site_report(company_id, report_type);

-- Subcontractor invoices (AP loop)
CREATE TABLE IF NOT EXISTS sc_invoice (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_uuid UUID NOT NULL REFERENCES subcontractor_package(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    claim_uuid UUID REFERENCES subcontractor_claim(uuid) ON DELETE SET NULL,
    invoice_number VARCHAR(64),
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'AED',
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    attachment_paths TEXT,
    submitted_by BIGINT,
    submitted_at TIMESTAMPTZ,
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    payment_reference VARCHAR(128),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sc_invoice_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PAID', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_sc_invoice_package ON sc_invoice(package_uuid);
CREATE INDEX IF NOT EXISTS idx_sc_invoice_project ON sc_invoice(project_id);
CREATE INDEX IF NOT EXISTS idx_sc_invoice_company_status ON sc_invoice(company_id, status);
