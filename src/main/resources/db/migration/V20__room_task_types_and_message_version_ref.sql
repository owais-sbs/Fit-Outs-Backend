-- Expand room task typing + message version references

ALTER TABLE room_tasks
    ADD COLUMN IF NOT EXISTS type_label VARCHAR(120);

ALTER TABLE room_tasks
    ALTER COLUMN task_type TYPE VARCHAR(60);

ALTER TABLE room_task_messages
    ADD COLUMN IF NOT EXISTS referenced_version_id UUID REFERENCES room_task_file_versions(uuid) ON DELETE SET NULL;
