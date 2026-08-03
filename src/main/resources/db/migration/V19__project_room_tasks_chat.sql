-- Project rooms, room tasks, file versions, chat, and timeline events

CREATE TABLE IF NOT EXISTS project_rooms (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(uuid),
    name VARCHAR(200) NOT NULL,
    floor_label VARCHAR(120),
    room_type_id UUID,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_project_rooms_floor_name UNIQUE (project_id, floor_label, name)
);

CREATE INDEX IF NOT EXISTS idx_project_rooms_project ON project_rooms(project_id);

CREATE TABLE IF NOT EXISTS room_tasks (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_room_id UUID NOT NULL REFERENCES project_rooms(uuid) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES companies(uuid),
    title VARCHAR(300) NOT NULL,
    task_type VARCHAR(40) NOT NULL DEFAULT 'OTHER',
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    client_deadline TIMESTAMPTZ,
    created_by BIGINT,
    assignee_account_id BIGINT,
    first_sent_to_client_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    client_approval_days INT,
    revision_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_tasks_room ON room_tasks(project_room_id);
CREATE INDEX IF NOT EXISTS idx_room_tasks_project ON room_tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_room_tasks_status ON room_tasks(status);

CREATE TABLE IF NOT EXISTS room_task_file_versions (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES room_tasks(uuid) ON DELETE CASCADE,
    version_no INT NOT NULL,
    uploaded_by BIGINT,
    uploader_role VARCHAR(20) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(120),
    file_size BIGINT,
    change_notes TEXT,
    is_final BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_room_task_version UNIQUE (task_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_room_task_versions_task ON room_task_file_versions(task_id);

CREATE TABLE IF NOT EXISTS room_task_messages (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES room_tasks(uuid) ON DELETE CASCADE,
    sender_account_id BIGINT NOT NULL,
    body TEXT,
    attachment_path VARCHAR(1000),
    attachment_name VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_task_messages_task ON room_task_messages(task_id, created_at);

CREATE TABLE IF NOT EXISTS room_messages (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_room_id UUID NOT NULL REFERENCES project_rooms(uuid) ON DELETE CASCADE,
    sender_account_id BIGINT NOT NULL,
    body TEXT,
    attachment_path VARCHAR(1000),
    attachment_name VARCHAR(500),
    linked_task_id UUID REFERENCES room_tasks(uuid) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_messages_room ON room_messages(project_room_id, created_at);

CREATE TABLE IF NOT EXISTS room_task_events (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID NOT NULL REFERENCES room_tasks(uuid) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    actor_account_id BIGINT,
    message TEXT,
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_room_task_events_task ON room_task_events(task_id, created_at);
