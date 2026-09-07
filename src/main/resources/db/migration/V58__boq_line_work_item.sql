-- Link survey BOQ lines to work items for material plan generation.
-- V56 was applied in some environments with a different checksum; this migration is idempotent.
ALTER TABLE boq_lines
    ADD COLUMN IF NOT EXISTS work_item_id UUID REFERENCES work_items(id);

CREATE INDEX IF NOT EXISTS idx_boq_lines_work_item ON boq_lines(work_item_id);
