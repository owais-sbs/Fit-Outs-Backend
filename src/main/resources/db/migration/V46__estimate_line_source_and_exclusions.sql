-- Draft BoQ: line source metadata + soft-excluded site-visit scope refs

ALTER TABLE site_visit_estimates
    ADD COLUMN IF NOT EXISTS excluded_scope_refs JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE site_visit_estimate_lines
    ADD COLUMN IF NOT EXISTS line_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS scope_ref VARCHAR(500);
