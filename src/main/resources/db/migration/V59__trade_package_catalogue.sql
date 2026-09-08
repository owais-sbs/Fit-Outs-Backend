-- The seed's 21 subcontract trade packages. Template activities carry a trade label in prose
-- ("Aluminium and glazing"), and the apply cascade needs a stable code to hang package shells
-- off, so the catalogue is the bridge between the two.

CREATE TABLE trade_package (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(160) NOT NULL,
    typical_boq_sections TEXT,
    special_licence_required VARCHAR(255),
    typical_retention VARCHAR(64),
    typical_payment_terms VARCHAR(64),
    match_keywords TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_trade_package_global ON trade_package(code) WHERE company_id IS NULL;
CREATE UNIQUE INDEX uq_trade_package_tenant ON trade_package(company_id, code) WHERE company_id IS NOT NULL;

-- Retention and payment terms are copied onto a shell when the cascade creates it, so the
-- commercial defaults do not have to be retyped per project.
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS typical_retention VARCHAR(64);
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS typical_payment_terms VARCHAR(64);
