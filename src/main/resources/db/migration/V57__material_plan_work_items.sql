-- Trace BOQ work items on aggregated material plan lines.
ALTER TABLE project_material_plan_line
    ADD COLUMN IF NOT EXISTS work_item_names TEXT;
