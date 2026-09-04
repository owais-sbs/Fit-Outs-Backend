-- Align excluded_scope_refs with StringListJsonConverter (TEXT), not JSONB

ALTER TABLE site_visit_estimates
    ALTER COLUMN excluded_scope_refs TYPE TEXT
    USING excluded_scope_refs::text;

ALTER TABLE site_visit_estimates
    ALTER COLUMN excluded_scope_refs SET DEFAULT '[]';
