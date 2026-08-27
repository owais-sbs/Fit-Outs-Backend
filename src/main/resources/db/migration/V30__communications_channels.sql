CREATE TABLE communication_channels (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    channel_type VARCHAR(20) NOT NULL,
    name VARCHAR(255),
    project_id BIGINT,
    project_room_id BIGINT,
    room_task_id BIGINT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE communication_channel_members (
    channel_uuid UUID NOT NULL REFERENCES communication_channels(uuid) ON DELETE CASCADE,
    account_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    last_read_at TIMESTAMPTZ,
    PRIMARY KEY (channel_uuid, account_id)
);

CREATE TABLE communication_messages (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_uuid UUID NOT NULL REFERENCES communication_channels(uuid) ON DELETE CASCADE,
    sender_account_id BIGINT NOT NULL,
    body TEXT,
    attachment_path VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comm_channels_company ON communication_channels(company_id);
CREATE INDEX idx_comm_messages_channel ON communication_messages(channel_uuid, created_at DESC);
