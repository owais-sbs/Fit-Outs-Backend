CREATE TABLE IF NOT EXISTS payment_request_event (
    uuid UUID PRIMARY KEY,
    payment_request_uuid UUID NOT NULL REFERENCES payment_request(uuid) ON DELETE CASCADE,
    company_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    step VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payment_request_event_request
    ON payment_request_event(payment_request_uuid, created_at);
