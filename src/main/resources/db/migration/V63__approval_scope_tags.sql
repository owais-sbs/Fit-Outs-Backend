CREATE TABLE approval_scope_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, code)
);

CREATE INDEX idx_approval_scope_tags_company ON approval_scope_tags (company_id) WHERE deleted = FALSE;

CREATE TABLE work_item_scope_tags (
    work_item_id UUID NOT NULL REFERENCES work_items(id) ON DELETE CASCADE,
    scope_tag_id UUID NOT NULL REFERENCES approval_scope_tags(id) ON DELETE CASCADE,
    PRIMARY KEY (work_item_id, scope_tag_id)
);

CREATE INDEX idx_work_item_scope_tags_work_item ON work_item_scope_tags (work_item_id);
CREATE INDEX idx_work_item_scope_tags_scope_tag ON work_item_scope_tags (scope_tag_id);

INSERT INTO approval_scope_tags (company_id, code, name, description)
SELECT c.uuid, v.code, v.name, v.description
FROM companies c
CROSS JOIN (VALUES
    ('STRUCTURAL', 'Structural change', 'Changes to structure, slabs, beams, or load-bearing fabric.'),
    ('LAYOUT', 'Layout / partition change', 'New or moved internal walls, partitions, or room layout.'),
    ('FACADE', 'Façade / exterior change', 'External envelope, windows, doors, or boundary fabric.'),
    ('MEP_LOAD', 'MEP load or capacity change', 'Work that can increase connected electrical or HVAC load.'),
    ('FIRE_LIFE', 'Fire and life safety', 'Fire detection, suppression, fire-rated construction, gas, or emergency lighting.'),
    ('DEMOLITION', 'Demolition', 'Structural or full strip-out of walls and fabric, not finish-only removal.'),
    ('SECURITY', 'Security systems (CCTV / access control)', 'CCTV or access-control installation.'),
    ('SIGNAGE', 'Signage', 'External signage or fascia change.'),
    ('KITCHEN', 'Commercial kitchen fit-out', 'Commercial or food-service kitchen fit-out, not a villa kitchen refresh.'),
    ('HOT_WORKS', 'Hot works', 'Welding, grinding, cutting, or torch work.'),
    ('NIGHT', 'Night work', 'Work outside standard permitted hours.'),
    ('HOARDING', 'Road hoarding / lifting', 'Hoarding, skip, crane, or lifting on public right of way.')
) AS v(code, name, description)
ON CONFLICT (company_id, code) DO NOTHING;

-- Demolition: structural / wall fabric only (not tile, screed, ceiling, or joinery strip-out)
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'DEMOLITION'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND (
    wi.work_item_code IN ('C.5', 'C.6', 'C.6.a', 'C.7', 'C.7-2', 'C.16')
    OR (
      wi.work_item_name ILIKE 'Demolition%'
      AND (
        wi.work_item_name ILIKE '%block wall%'
        OR wi.work_item_name ILIKE '%boundary wall%'
        OR wi.work_item_name ILIKE '%full height%wall%'
        OR wi.work_item_name ILIKE '%internal block%'
        OR wi.work_item_name ILIKE '%external block%'
      )
    )
    OR wi.description ILIKE 'Demolition of full height internal block wall%'
  )
ON CONFLICT DO NOTHING;

-- Structural
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'STRUCTURAL'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND (
    wi.work_item_code IN ('D.8', 'D.8.a', 'D.14', 'D.15', 'D.16', 'D.22', 'D.25')
    OR wi.work_item_name ILIKE '%concrete beam%'
    OR wi.work_item_name ILIKE '%structural work%'
    OR wi.work_item_name ILIKE '%grade slab%'
    OR wi.work_item_name ILIKE '%steel reinforcement%'
    OR wi.work_item_name ILIKE '%staircase step extension%'
  )
ON CONFLICT DO NOTHING;

-- Layout / partition
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'LAYOUT'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND (
    wi.work_item_code IN ('D.7', 'D.7.a', 'D.9', 'D.24', 'D.24.a', 'D.26')
    OR wi.work_item_name ILIKE '%full height internal wall%'
    OR wi.work_item_name ILIKE '%thermal block%'
  )
ON CONFLICT DO NOTHING;

-- Façade / exterior
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'FACADE'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND (
    wi.work_item_code IN ('C.5', 'C.7-2', 'M.0.1', 'M.3', 'M.3.a', 'M.3.1', 'M.3.1.a', 'M.3.2', 'M.3.2.a', 'M.3.2.b', 'M.3.2.c', 'M.4', 'M.4.1')
    OR (wi.work_item_code LIKE 'M.3%' AND wi.work_item_code NOT LIKE 'M.3.3%')
    OR wi.work_item_name ILIKE '%external block wall%'
    OR wi.work_item_name ILIKE 'Demolition of existing boundary wall%'
    OR wi.work_item_name ILIKE '%closing of the existing window%'
  )
ON CONFLICT DO NOTHING;

-- MEP load
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'MEP_LOAD'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND wi.work_item_code IN ('I.10', 'J.3', 'J.3-2')
ON CONFLICT DO NOTHING;

-- Fire and life safety
INSERT INTO work_item_scope_tags (work_item_id, scope_tag_id)
SELECT wi.id, t.id
FROM work_items wi
JOIN approval_scope_tags t
  ON t.company_id = wi.company_id
 AND t.code = 'FIRE_LIFE'
 AND t.deleted = FALSE
WHERE COALESCE(wi.deleted, FALSE) = FALSE
  AND (
    wi.work_item_code IN ('O.4', 'I.13', 'I.14')
    OR wi.work_item_name ILIKE '%fire fighting%'
    OR wi.work_item_name ILIKE '%fire alarm%'
    OR wi.work_item_name ILIKE '%gas piping%'
    OR wi.work_item_name ILIKE '%gas pipeline%'
  )
ON CONFLICT DO NOTHING;
