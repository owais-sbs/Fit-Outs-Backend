-- Authoritative multi-line BOQ scope for live subcontractor packages.
-- No foreign keys are added here because existing package/BOQ orphan data
-- must remain loadable; ownership is enforced in SubcontractorService.
CREATE TABLE IF NOT EXISTS subcontractor_package_boq_line (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL,
    boq_line_id UUID NOT NULL,
    company_id UUID NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_package_boq_scope UNIQUE (package_uuid, boq_line_id)
);

CREATE INDEX IF NOT EXISTS idx_sc_package_boq_scope_package
    ON subcontractor_package_boq_line(package_uuid, sort_order);

CREATE INDEX IF NOT EXISTS idx_sc_package_boq_scope_line
    ON subcontractor_package_boq_line(company_id, boq_line_id);

ALTER TABLE subcontractor_package
    ADD COLUMN IF NOT EXISTS trade_package_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS estimated_boq_value NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS ld_terms TEXT;
