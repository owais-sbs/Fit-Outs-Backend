CREATE TABLE appendix_masters (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    image_path VARCHAR(512) NOT NULL,
    category VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_appendix_masters_company ON appendix_masters(company_id);

CREATE TABLE site_visit_estimate_appendices (
    estimate_uuid UUID NOT NULL REFERENCES site_visit_estimates(uuid) ON DELETE CASCADE,
    appendix_master_uuid UUID NOT NULL REFERENCES appendix_masters(uuid) ON DELETE CASCADE,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (estimate_uuid, appendix_master_uuid)
);
