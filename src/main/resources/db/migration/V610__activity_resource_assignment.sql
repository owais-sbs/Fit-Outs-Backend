-- Module 12: plant/tool assignment to schedule activities (parallel to activity_crew_assignment)
CREATE TABLE activity_resource_assignment (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    activity_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    resource_type_uuid UUID NOT NULL REFERENCES resource_type(uuid),
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_resource_assignment_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_resource_assignment_quantity CHECK (quantity >= 1)
);

CREATE INDEX idx_resource_assignment_project ON activity_resource_assignment(project_id);
CREATE INDEX idx_resource_assignment_type ON activity_resource_assignment(resource_type_uuid);
CREATE INDEX idx_resource_assignment_activity ON activity_resource_assignment(activity_uuid);
