CREATE TABLE IF NOT EXISTS company_enabled_features (
    company_uuid UUID NOT NULL REFERENCES companies (uuid) ON DELETE CASCADE,
    feature VARCHAR(64) NOT NULL,
    PRIMARY KEY (company_uuid, feature)
);
