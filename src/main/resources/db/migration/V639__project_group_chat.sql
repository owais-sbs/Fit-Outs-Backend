ALTER TABLE communication_channels
    ADD COLUMN IF NOT EXISTS includes_client BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_comm_channels_project_type
    ON communication_channels (project_id, channel_type);
