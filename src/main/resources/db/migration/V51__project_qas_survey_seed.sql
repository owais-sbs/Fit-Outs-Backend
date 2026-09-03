CREATE TABLE project_qas_survey_seed (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    source_estimate_uuid UUID,
    floors_json TEXT NOT NULL DEFAULT '[]',
    rooms_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_qas_survey_seed_project UNIQUE (project_id)
);

CREATE INDEX idx_project_qas_survey_seed_company ON project_qas_survey_seed(company_id);
CREATE INDEX idx_project_qas_survey_seed_estimate ON project_qas_survey_seed(source_estimate_uuid);
