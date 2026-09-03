CREATE TABLE project_team_assignment (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    account_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_team_assignment UNIQUE (project_id, role, account_id),
    CONSTRAINT chk_project_team_role CHECK (
        role IN ('QS_SENIOR_QS', 'PROJECT_MANAGER', 'FINANCE', 'CLIENT', 'SUBCONTRACTOR')
    )
);

CREATE INDEX idx_project_team_assignment_project ON project_team_assignment(project_id);
CREATE INDEX idx_project_team_assignment_company ON project_team_assignment(company_id);
CREATE INDEX idx_project_team_assignment_account ON project_team_assignment(account_id);
