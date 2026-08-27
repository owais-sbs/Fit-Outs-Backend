ALTER TABLE site_visits
    ADD COLUMN IF NOT EXISTS initial_email_sent_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS location_email_sent_at TIMESTAMPTZ;

ALTER TABLE communication_channels
    ADD COLUMN IF NOT EXISTS email_thread_subject VARCHAR(500),
    ADD COLUMN IF NOT EXISTS email_thread_root_id VARCHAR(255);

ALTER TABLE communication_outbox
    ADD COLUMN IF NOT EXISTS channel_uuid UUID,
    ADD COLUMN IF NOT EXISTS source_message_uuid UUID,
    ADD COLUMN IF NOT EXISTS email_message_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS in_reply_to VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_communication_outbox_channel_sent
    ON communication_outbox (channel_uuid, sent_at DESC);
