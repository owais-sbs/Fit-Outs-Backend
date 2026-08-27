CREATE TABLE site_visit_recordings (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_visit_uuid UUID NOT NULL REFERENCES site_visits(uuid) ON DELETE CASCADE,
    audio_path VARCHAR(512) NOT NULL,
    duration_seconds INT,
    transcript TEXT,
    ai_summary TEXT,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_site_visit_recordings_visit ON site_visit_recordings(site_visit_uuid);
CREATE INDEX idx_site_visit_recordings_status ON site_visit_recordings(processing_status);
