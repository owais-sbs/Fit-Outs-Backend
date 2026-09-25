-- BOQ ↔ schedule provenance: unmatched/BOQ-only activities carry boq_line_id;
-- Blend mode attachments live on schedule_activity_boq_line.

ALTER TABLE schedule_activity
    ADD COLUMN IF NOT EXISTS boq_line_id UUID;

CREATE INDEX IF NOT EXISTS idx_schedule_activity_boq_line
    ON schedule_activity(boq_line_id)
    WHERE boq_line_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS schedule_activity_boq_line (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_activity_uuid UUID NOT NULL REFERENCES schedule_activity(uuid) ON DELETE CASCADE,
    boq_line_id UUID NOT NULL,
    project_id BIGINT NOT NULL,
    company_id UUID NOT NULL,
    match_source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sab_activity_line UNIQUE (schedule_activity_uuid, boq_line_id),
    CONSTRAINT uq_sab_project_line UNIQUE (project_id, boq_line_id)
);

CREATE INDEX IF NOT EXISTS idx_sab_activity
    ON schedule_activity_boq_line(schedule_activity_uuid);

CREATE INDEX IF NOT EXISTS idx_sab_project
    ON schedule_activity_boq_line(project_id, company_id);
