-- Allow Site Engineer on project team assignments (UI already offered this role).
ALTER TABLE project_team_assignment
    DROP CONSTRAINT IF EXISTS chk_project_team_role;

ALTER TABLE project_team_assignment
    ADD CONSTRAINT chk_project_team_role CHECK (
        role IN (
            'QS_SENIOR_QS',
            'PROJECT_MANAGER',
            'SITE_ENGINEER',
            'FINANCE',
            'CLIENT',
            'SUBCONTRACTOR'
        )
    );
