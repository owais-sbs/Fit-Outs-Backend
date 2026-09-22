-- Module 27 Part 1: project commercial close-out checklist.
-- Automatic items (variations, snags) are evaluated from existing modules.
-- Manual confirmations and snag carry-forward are stored here so this module
-- does not change variation, snag, or billing schemas.

CREATE TABLE IF NOT EXISTS project_closeout_checklist (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    final_invoice_confirmed_at TIMESTAMPTZ,
    final_invoice_confirmed_by BIGINT,
    accounting_synced_at TIMESTAMPTZ,
    accounting_synced_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_closeout_checklist_project UNIQUE (project_id)
);

CREATE INDEX IF NOT EXISTS idx_project_closeout_checklist_company
    ON project_closeout_checklist(company_id);

CREATE TABLE IF NOT EXISTS project_closeout_carried_snag (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_uuid UUID NOT NULL REFERENCES project_closeout_checklist(uuid) ON DELETE CASCADE,
    snag_uuid UUID NOT NULL,
    carried_by BIGINT,
    carried_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_closeout_carried_snag UNIQUE (checklist_uuid, snag_uuid)
);

CREATE INDEX IF NOT EXISTS idx_project_closeout_carried_snag_checklist
    ON project_closeout_carried_snag(checklist_uuid);
