CREATE TABLE room_masters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_room_master_company_code UNIQUE (company_id, code)
);

ALTER TABLE room_types ADD COLUMN room_master_id UUID REFERENCES room_masters(id) ON DELETE SET NULL;
ALTER TABLE room_types ALTER COLUMN category DROP NOT NULL;

CREATE TABLE work_item_masters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uk_work_item_master_company_code UNIQUE (company_id, code)
);

ALTER TABLE work_items ADD COLUMN work_item_master_id UUID REFERENCES work_item_masters(id) ON DELETE SET NULL;
ALTER TABLE work_items ALTER COLUMN category DROP NOT NULL;

-- Migrate existing room_types category to room_masters
INSERT INTO room_masters (company_id, name, code)
SELECT DISTINCT company_id, 
       INITCAP(category), 
       category
FROM room_types;

UPDATE room_types rt
SET room_master_id = rm.id
FROM room_masters rm
WHERE rt.company_id = rm.company_id AND rt.category = rm.code;

-- Migrate existing work_items category to work_item_masters
INSERT INTO work_item_masters (company_id, name, code)
SELECT DISTINCT company_id, 
       INITCAP(category), 
       category
FROM work_items;

UPDATE work_items wi
SET work_item_master_id = wim.id
FROM work_item_masters wim
WHERE wi.company_id = wim.company_id AND wi.category = wim.code;
