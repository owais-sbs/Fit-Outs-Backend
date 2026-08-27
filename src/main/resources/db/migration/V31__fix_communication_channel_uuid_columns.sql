-- Align room/project room FK columns with UUID types used by JPA entities
ALTER TABLE communication_channels
    DROP COLUMN IF EXISTS project_room_id,
    DROP COLUMN IF EXISTS room_task_id;

ALTER TABLE communication_channels
    ADD COLUMN project_room_id UUID,
    ADD COLUMN room_task_id UUID;
