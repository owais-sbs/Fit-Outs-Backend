-- One subcontractor package per BOQ line (individual line assignment).
ALTER TABLE subcontractor_package ADD COLUMN IF NOT EXISTS boq_line_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS idx_sc_package_boq_line
    ON subcontractor_package(company_id, boq_line_id)
    WHERE boq_line_id IS NOT NULL;
