-- Module 25 — Profit & Loss: overhead rules + project P&L snapshots

CREATE TABLE IF NOT EXISTS overhead_rule (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    percentage NUMERIC(8,4) NOT NULL DEFAULT 0,
    basis VARCHAR(32) NOT NULL DEFAULT 'CONTRACT_VALUE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_overhead_rule_company_active
    ON overhead_rule(company_id, active);

CREATE TABLE IF NOT EXISTS project_pnl_snapshot (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    project_id BIGINT NOT NULL,
    period_year_month VARCHAR(7) NOT NULL,
    contract_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    material_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    labour_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    sc_certified_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    variation_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    overhead_allocated NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_cost NUMERIC(14,2) NOT NULL DEFAULT 0,
    margin NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_contract_value NUMERIC(14,2),
    original_estimated_cost NUMERIC(14,2),
    margin_vs_original_estimate NUMERIC(14,2),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_pnl_snapshot UNIQUE (company_id, project_id, period_year_month)
);

CREATE INDEX IF NOT EXISTS idx_project_pnl_snapshot_company_period
    ON project_pnl_snapshot(company_id, period_year_month);
