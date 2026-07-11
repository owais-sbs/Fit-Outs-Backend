CREATE TABLE IF NOT EXISTS material_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_material_category_company_code UNIQUE (company_id, code)
);

CREATE TABLE IF NOT EXISTS materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    material_category_id UUID REFERENCES material_categories(id),
    material_name VARCHAR(200) NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    cost_price NUMERIC(12, 2),
    selling_price NUMERIC(12, 2),
    supplier_name VARCHAR(200),
    sku VARCHAR(100),
    min_stock_level NUMERIC(12, 3) DEFAULT 0,
    reorder_qty NUMERIC(12, 3) DEFAULT 0,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_material_company_code UNIQUE (company_id, material_code)
);

CREATE TABLE IF NOT EXISTS material_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    material_id UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    quantity_on_hand NUMERIC(14, 3) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_material_stock_company_material UNIQUE (company_id, material_id)
);

CREATE TABLE IF NOT EXISTS stock_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    material_id UUID NOT NULL REFERENCES materials(id),
    movement_type VARCHAR(20) NOT NULL,
    quantity NUMERIC(14, 3) NOT NULL,
    unit_cost NUMERIC(12, 2),
    total_cost NUMERIC(14, 2),
    project_id BIGINT REFERENCES projects(id),
    reference_no VARCHAR(100),
    notes TEXT,
    movement_date TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE TABLE IF NOT EXISTS work_item_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    quantity_per_unit NUMERIC(12, 4) NOT NULL DEFAULT 1,
    wastage_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    CONSTRAINT uk_work_item_material UNIQUE (work_item_id, material_id)
);

ALTER TABLE work_items
    ADD COLUMN IF NOT EXISTS cost_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS selling_price_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cost_price_override BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_material_categories_company ON material_categories(company_id);
CREATE INDEX IF NOT EXISTS idx_materials_company ON materials(company_id);
CREATE INDEX IF NOT EXISTS idx_materials_category ON materials(material_category_id);
CREATE INDEX IF NOT EXISTS idx_material_stock_company ON material_stock(company_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_company ON stock_movements(company_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_material ON stock_movements(material_id);
CREATE INDEX IF NOT EXISTS idx_stock_movements_date ON stock_movements(movement_date);
CREATE INDEX IF NOT EXISTS idx_work_item_materials_work_item ON work_item_materials(work_item_id);
