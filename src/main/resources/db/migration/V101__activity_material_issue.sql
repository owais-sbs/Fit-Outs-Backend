CREATE TABLE activity_material_issue (
    uuid                  UUID PRIMARY KEY,
    activity_uuid         UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    project_id            BIGINT NOT NULL,
    company_id            UUID NOT NULL,
    progress_update_uuid  UUID REFERENCES activity_progress_update(uuid) ON DELETE SET NULL,
    plan_line_uuid        UUID,
    material_id           UUID NOT NULL,
    material_name         VARCHAR(255),
    qty                   NUMERIC(14, 4) NOT NULL,
    status                VARCHAR(32) NOT NULL DEFAULT 'DECLARED',
    stock_movement_id     UUID,
    created_by            BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ami_activity ON activity_material_issue(activity_uuid);
CREATE INDEX idx_ami_progress ON activity_material_issue(progress_update_uuid);
CREATE INDEX idx_ami_project_status ON activity_material_issue(project_id, company_id, status);
CREATE INDEX idx_ami_material ON activity_material_issue(material_id);
