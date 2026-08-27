CREATE TABLE communication_outbox (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estimate_uuid UUID,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_by BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT'
);

CREATE INDEX idx_communication_outbox_sent_at ON communication_outbox(sent_at DESC);
