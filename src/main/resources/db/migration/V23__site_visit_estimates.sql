-- Rough estimate + cover letter fields for completed site visits

CREATE TABLE IF NOT EXISTS site_visit_estimates (
    uuid UUID PRIMARY KEY,
    site_visit_uuid UUID NOT NULL UNIQUE,
    quote_no VARCHAR(64),
    valid_until DATE,
    revision VARCHAR(16) NOT NULL DEFAULT 'R0',
    client_name VARCHAR(200),
    client_address VARCHAR(500),
    project_label VARCHAR(200),
    location_label VARCHAR(200),
    subject VARCHAR(500),
    prepared_by VARCHAR(200),
    currency VARCHAR(8) NOT NULL DEFAULT 'AED',
    notes TEXT,
    subtotal NUMERIC(14, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    company_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_site_visit_estimates_visit
        FOREIGN KEY (site_visit_uuid) REFERENCES site_visits (uuid) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS site_visit_estimate_lines (
    uuid UUID PRIMARY KEY,
    estimate_uuid UUID NOT NULL,
    floor_name VARCHAR(120),
    room_name VARCHAR(120),
    category VARCHAR(200),
    description VARCHAR(500) NOT NULL,
    qty NUMERIC(14, 2) NOT NULL DEFAULT 1,
    unit VARCHAR(32) NOT NULL DEFAULT 'LS',
    rate NUMERIC(14, 2) NOT NULL DEFAULT 0,
    amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_site_visit_estimate_lines_estimate
        FOREIGN KEY (estimate_uuid) REFERENCES site_visit_estimates (uuid) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_site_visit_estimate_lines_estimate
    ON site_visit_estimate_lines (estimate_uuid);
