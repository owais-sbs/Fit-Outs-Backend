CREATE TABLE work_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    work_item_name VARCHAR(200) NOT NULL,
    work_item_code VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    ceiling_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    wall_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    floor_applicable BOOLEAN NOT NULL DEFAULT FALSE,
    unit_type VARCHAR(20) NOT NULL,
    default_rate NUMERIC(12, 2),
    subcontractor_rate NUMERIC(12, 2),
    markup_percentage NUMERIC(5, 2),
    quantity_formula_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    icon VARCHAR(50),
    color_tag VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_work_item_company_code UNIQUE (company_id, work_item_code)
);

CREATE TABLE room_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    room_type_name VARCHAR(200) NOT NULL,
    room_code VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    ceiling_measurement_required BOOLEAN NOT NULL DEFAULT FALSE,
    wall_measurement_required BOOLEAN NOT NULL DEFAULT FALSE,
    floor_measurement_required BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_room_type_company_code UNIQUE (company_id, room_code)
);

CREATE TABLE room_type_work_items (
    room_type_id UUID NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    PRIMARY KEY (room_type_id, work_item_id)
);

CREATE INDEX idx_work_items_company_id ON work_items(company_id);
CREATE INDEX idx_work_items_category ON work_items(category);
CREATE INDEX idx_work_items_active ON work_items(active);
CREATE INDEX idx_work_items_deleted ON work_items(deleted);
CREATE INDEX idx_work_items_ceiling ON work_items(ceiling_applicable) WHERE ceiling_applicable = TRUE;
CREATE INDEX idx_work_items_wall ON work_items(wall_applicable) WHERE wall_applicable = TRUE;
CREATE INDEX idx_work_items_floor ON work_items(floor_applicable) WHERE floor_applicable = TRUE;

CREATE INDEX idx_room_types_company_id ON room_types(company_id);
CREATE INDEX idx_room_types_category ON room_types(category);
CREATE INDEX idx_room_types_active ON room_types(active);
CREATE INDEX idx_room_types_deleted ON room_types(deleted);

CREATE INDEX idx_room_type_work_items_room_type ON room_type_work_items(room_type_id);
CREATE INDEX idx_room_type_work_items_work_item ON room_type_work_items(work_item_id);
