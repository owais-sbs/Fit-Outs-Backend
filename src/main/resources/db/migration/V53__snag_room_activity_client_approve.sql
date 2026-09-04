ALTER TABLE snag
    ADD COLUMN IF NOT EXISTS project_room_id UUID,
    ADD COLUMN IF NOT EXISTS activity_uuid UUID,
    ADD COLUMN IF NOT EXISTS raised_by_client BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS client_approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS client_approved_by BIGINT;

CREATE INDEX IF NOT EXISTS idx_snag_project_room ON snag (project_room_id);
CREATE INDEX IF NOT EXISTS idx_snag_activity ON snag (activity_uuid);
