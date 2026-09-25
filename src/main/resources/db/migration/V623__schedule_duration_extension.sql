-- SE duration extension requests pending PM approval before CPM dates change
CREATE TABLE schedule_duration_extension (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    project_id BIGINT NOT NULL,
    activity_uuid UUID NOT NULL,
    requested_by BIGINT NOT NULL,
    current_duration_working_days INT NOT NULL,
    requested_duration_working_days INT NOT NULL,
    delay_reason_code VARCHAR(40) NOT NULL,
    delay_reason_text TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by BIGINT,
    decided_at TIMESTAMPTZ,
    decision_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_schedule_duration_extension_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_schedule_duration_extension_days
        CHECK (requested_duration_working_days > current_duration_working_days
               AND current_duration_working_days >= 1)
);

CREATE INDEX idx_schedule_duration_extension_company_status
    ON schedule_duration_extension(company_id, status);
CREATE INDEX idx_schedule_duration_extension_activity
    ON schedule_duration_extension(activity_uuid);
CREATE INDEX idx_schedule_duration_extension_project
    ON schedule_duration_extension(project_id);

-- At most one PENDING request per activity
CREATE UNIQUE INDEX uq_schedule_duration_extension_activity_pending
    ON schedule_duration_extension(activity_uuid)
    WHERE status = 'PENDING';
