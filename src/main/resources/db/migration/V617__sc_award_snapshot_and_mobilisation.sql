-- Step 5: freeze awarded commercial BOQ at award time; package worker mobilisation.
-- No FKs so legacy orphan data remains loadable; ownership enforced in services.

CREATE TABLE IF NOT EXISTS sc_award_boq_line (
    uuid UUID PRIMARY KEY,
    award_uuid UUID NOT NULL,
    package_uuid UUID NOT NULL,
    company_id UUID NOT NULL,
    quote_uuid UUID,
    quote_line_uuid UUID,
    boq_line_id UUID,
    section_code VARCHAR(64),
    description TEXT,
    unit VARCHAR(32),
    quantity NUMERIC(18, 4),
    rate NUMERIC(18, 4),
    amount NUMERIC(18, 2),
    line_status VARCHAR(32) NOT NULL DEFAULT 'QUOTED',
    remarks TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sc_award_boq_award
    ON sc_award_boq_line(award_uuid, sort_order);

CREATE INDEX IF NOT EXISTS idx_sc_award_boq_package
    ON sc_award_boq_line(package_uuid, company_id);

CREATE TABLE IF NOT EXISTS sc_package_worker (
    uuid UUID PRIMARY KEY,
    package_uuid UUID NOT NULL,
    worker_uuid UUID NOT NULL,
    organization_uuid UUID NOT NULL,
    company_id UUID NOT NULL,
    nominated_by_account_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'NOMINATED',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_package_worker UNIQUE (package_uuid, worker_uuid)
);

CREATE INDEX IF NOT EXISTS idx_sc_package_worker_package
    ON sc_package_worker(package_uuid, company_id);

ALTER TABLE subcontractor_package
    ADD COLUMN IF NOT EXISTS original_award_value NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS closeout_notes TEXT;
