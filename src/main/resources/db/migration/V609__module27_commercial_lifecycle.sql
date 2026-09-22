-- Module 27 Part 3: commercial close, DLP period, archive + warranty snag category.
-- Extends the existing close-out checklist; does not change Project.status semantics.

ALTER TABLE project_closeout_checklist
    ADD COLUMN IF NOT EXISTS commercially_closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS commercially_closed_by BIGINT,
    ADD COLUMN IF NOT EXISTS dlp_start_date DATE,
    ADD COLUMN IF NOT EXISTS dlp_end_date DATE,
    ADD COLUMN IF NOT EXISTS dlp_duration_months INTEGER,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by BIGINT;

ALTER TABLE snag
    ADD COLUMN IF NOT EXISTS category VARCHAR(20) NOT NULL DEFAULT 'STANDARD';

CREATE INDEX IF NOT EXISTS idx_snag_project_category
    ON snag(project_id, category);
