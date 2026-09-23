-- Module 26 alignment: claim lines, claim/certificate/invoice/retention status fixes

-- 1) Claim status CHECK must allow Wave 7 + Module 26 statuses
ALTER TABLE subcontractor_claim DROP CONSTRAINT IF EXISTS chk_sc_claim_status;
ALTER TABLE subcontractor_claim
    ADD CONSTRAINT chk_sc_claim_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED',
        'MEASURED', 'CERTIFIED', 'PAID'
    ));

-- Claim commercial summary fields
ALTER TABLE subcontractor_claim
    ADD COLUMN IF NOT EXISTS claimed_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS claim_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS claim_period_from DATE,
    ADD COLUMN IF NOT EXISTS claim_period_to DATE;

-- Historical PAID claims: payment lives on certificate; claim stays CERTIFIED
UPDATE subcontractor_claim SET status = 'CERTIFIED' WHERE status = 'PAID';

-- 2) BOQ-line claim grid
CREATE TABLE IF NOT EXISTS sc_claim_line (
    uuid UUID PRIMARY KEY,
    claim_uuid UUID NOT NULL REFERENCES subcontractor_claim(uuid) ON DELETE CASCADE,
    award_boq_line_uuid UUID REFERENCES sc_award_boq_line(uuid),
    boq_line_id UUID,
    package_uuid UUID NOT NULL,
    company_id UUID NOT NULL,
    section_code VARCHAR(64),
    description TEXT,
    unit VARCHAR(32),
    contract_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    award_rate NUMERIC(18, 4) NOT NULL DEFAULT 0,
    previous_certified_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    claimed_qty NUMERIC(18, 4) NOT NULL DEFAULT 0,
    claimed_value NUMERIC(18, 2) NOT NULL DEFAULT 0,
    measured_qty NUMERIC(18, 4),
    measured_value NUMERIC(18, 2),
    certified_qty NUMERIC(18, 4),
    certified_value NUMERIC(18, 2),
    qs_comment TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sc_claim_line_claim ON sc_claim_line(claim_uuid);
CREATE INDEX IF NOT EXISTS idx_sc_claim_line_package ON sc_claim_line(package_uuid);

-- 3) Certificate: PAYABLE is the invoice-eligible state
-- Existing ISSUED certificates are treated as PAYABLE (already past QS certification)
UPDATE sc_payment_certificate SET status = 'PAYABLE' WHERE status = 'ISSUED';

ALTER TABLE sc_payment_certificate
    ADD COLUMN IF NOT EXISTS certificate_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS certificate_date DATE,
    ADD COLUMN IF NOT EXISTS other_deductions NUMERIC(18, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS paid_by BIGINT,
    ADD COLUMN IF NOT EXISTS payment_notes TEXT;

-- 4) Invoice links to payment certificate (primary), claim remains optional cross-link
ALTER TABLE sc_invoice
    ADD COLUMN IF NOT EXISTS certificate_uuid UUID REFERENCES sc_payment_certificate(uuid),
    ADD COLUMN IF NOT EXISTS invoice_date DATE;

CREATE INDEX IF NOT EXISTS idx_sc_invoice_certificate ON sc_invoice(certificate_uuid);

-- 5) Retention release tracking
ALTER TABLE sc_retention_ledger
    ADD COLUMN IF NOT EXISTS retention_pct NUMERIC(8, 4),
    ADD COLUMN IF NOT EXISTS certified_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS amount_released NUMERIC(18, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS released_by BIGINT;

-- Expand retention status vocabulary (HELD / ELIGIBLE_FOR_RELEASE / APPROVED_FOR_RELEASE / RELEASED)
-- No CHECK constraint historically — status is free VARCHAR.
