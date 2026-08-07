-- Draft BoQ lines link to company work items / room types (optional snapshots)

ALTER TABLE site_visit_estimate_lines
    ADD COLUMN IF NOT EXISTS work_item_id UUID,
    ADD COLUMN IF NOT EXISTS room_type_id UUID;
