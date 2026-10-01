-- Material plan packages: group lines for per-package purchase orders

CREATE TABLE IF NOT EXISTS project_material_plan_package (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_uuid UUID NOT NULL REFERENCES project_material_plan(uuid) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_material_plan_package_plan
    ON project_material_plan_package(plan_uuid);

ALTER TABLE project_material_plan_line
    ADD COLUMN IF NOT EXISTS package_uuid UUID REFERENCES project_material_plan_package(uuid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_material_plan_line_package
    ON project_material_plan_line(package_uuid);
