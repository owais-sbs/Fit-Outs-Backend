-- Seed villa layout room masters/types and attach relevant BOQ work items (idempotent).

-- Parent categories (room masters)
INSERT INTO room_masters (company_id, name, code)
SELECT c.uuid, v.name, v.code
FROM companies c
CROSS JOIN (VALUES
    ('Kitchen', 'KITCHEN'),
    ('Living / Common', 'LIVING'),
    ('Bedroom', 'BEDROOM'),
    ('Bathroom', 'BATHROOM'),
    ('Balcony / Outdoor', 'BALCONY'),
    ('Circulation', 'CIRC'),
    ('Utility', 'UTILITY'),
    ('Other', 'OTHER')
) AS v(name, code)
ON CONFLICT (company_id, code) DO NOTHING;

-- Room types from EXISTING / PROPOSED layout
INSERT INTO room_types (
    company_id,
    room_master_id,
    room_type_name,
    room_code,
    description,
    active,
    deleted
)
SELECT
    c.uuid,
    rm.id,
    v.room_type_name,
    v.room_code,
    v.description,
    TRUE,
    FALSE
FROM companies c
CROSS JOIN (VALUES
    -- Kitchen
    ('KITCHEN', 'Kitchen', 'RM-KITCHEN', 'GF kitchen area (H 2.4m)'),
    ('KITCHEN', 'Kitchen Backsplash', 'RM-KIT-BACK', 'Kitchen backsplash zone'),

    -- Living / Common (GF)
    ('LIVING', 'Entrance Hall', 'RM-ENT-HALL', 'GF entrance hall (H 3.0m)'),
    ('LIVING', 'Living / Dining Area', 'RM-LIV-DIN', 'GF living and dining (H 3.0m)'),
    ('LIVING', 'Lobby', 'RM-LOBBY', 'GF lobby (H 3.0m)'),
    ('LIVING', 'Study (GF)', 'RM-STUDY-GF', 'GF study (H 3.0m)'),

    -- Bedroom (FF)
    ('BEDROOM', 'Bedroom 2', 'RM-BED-2', 'FF bedroom 2 (H 3.0m)'),
    ('BEDROOM', 'Bedroom 3', 'RM-BED-3', 'FF bedroom 3 (H 3.0m)'),
    ('BEDROOM', 'Master Bedroom', 'RM-MASTER-BED', 'FF master bedroom (H 3.0m)'),
    ('BEDROOM', 'Study (FF)', 'RM-STUDY-FF', 'FF study (H 3.0m)'),

    -- Bathroom
    ('BATHROOM', 'Powder Room', 'RM-POWDER', 'Powder room (H 2.4m)'),
    ('BATHROOM', 'Maid''s Bathroom', 'RM-MAID-BATH', 'Maid''s bathroom (H 2.4m)'),
    ('BATHROOM', 'Guest Bathroom', 'RM-GUEST-BATH', 'Guest bathroom (H 2.4m)'),
    ('BATHROOM', 'Common Bathroom', 'RM-COMMON-BATH', 'Common bathroom (H 2.4m)'),
    ('BATHROOM', 'Bathroom Ensuite', 'RM-ENSUITE', 'Bathroom ensuite (H 2.4m)'),
    ('BATHROOM', 'Master Bathroom', 'RM-MASTER-BATH', 'Master bathroom (H 2.4m)'),

    -- Balcony / Outdoor
    ('BALCONY', 'Patio', 'RM-PATIO', 'Outdoor patio (H 1.0m)'),
    ('BALCONY', 'Terrace', 'RM-TERRACE', 'Outdoor terrace (H 1.0m)'),
    ('BALCONY', 'Balcony 1', 'RM-BALCONY-1', 'Balcony 1 (H 1.0m)'),
    ('BALCONY', 'Balcony 2', 'RM-BALCONY-2', 'Balcony 2 (H 1.0m)'),

    -- Circulation
    ('CIRC', 'FF Hallway', 'RM-FF-HALL', 'First floor hallway (H 3.0m)'),
    ('CIRC', 'Service Passage', 'RM-SVC-PASS', 'GF service passage (H 3.0m)'),
    ('CIRC', 'Staircase', 'RM-STAIR', 'Staircase (H 6.0m)'),

    -- Utility
    ('UTILITY', 'Utility', 'RM-UTILITY', 'Utility room (H 3.0m)'),

    -- Other / non-room scopes used on BOQ layouts
    ('OTHER', 'Internal Doors', 'RM-INT-DOORS', 'Internal door package (GF/FF)'),
    ('OTHER', 'Main Doors', 'RM-MAIN-DOORS', 'Main door package'),
    ('OTHER', 'External Wall Painting', 'RM-EXT-PAINT', 'External wall painting'),
    ('OTHER', 'Roof Garage', 'RM-ROOF-GAR', 'Roof / garage area'),
    ('OTHER', 'Boundary Wall', 'RM-BOUND-WALL', 'Boundary wall')
) AS v(master_code, room_type_name, room_code, description)
JOIN room_masters rm
  ON rm.company_id = c.uuid
 AND rm.code = v.master_code
 AND COALESCE(rm.deleted, FALSE) = FALSE
ON CONFLICT (company_id, room_code) DO NOTHING;

-- Helper: attach work items by room code + work-item master codes (+ optional code prefixes)
-- Kitchen: demolitions/build/floor-wall/ceiling/paint/plumbing/elec/AC/joinery/counter/purchases
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-KITCHEN', 'RM-KIT-BACK')
  AND wim.code IN ('C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'N')
  AND (
        wi.work_item_code ~ '^(C\.|D\.|E\.|F\.|G\.|H\.|I\.|J\.|K\.|L\.|N\.)'
     OR wi.work_item_name ILIKE '%kitchen%'
     OR wi.work_item_name ILIKE '%backsplash%'
     OR wi.work_item_name ILIKE '%sink%'
     OR wi.work_item_name ILIKE '%cabinet%'
  )
ON CONFLICT DO NOTHING;

-- Living / common dry areas
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-ENT-HALL', 'RM-LIV-DIN', 'RM-LOBBY', 'RM-STUDY-GF')
  AND wim.code IN ('C', 'D', 'E', 'F', 'G', 'I', 'J', 'M')
ON CONFLICT DO NOTHING;

-- Bedrooms
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-BED-2', 'RM-BED-3', 'RM-MASTER-BED', 'RM-STUDY-FF')
  AND wim.code IN ('C', 'D', 'E', 'F', 'G', 'I', 'K')
ON CONFLICT DO NOTHING;

-- Bathrooms (wet areas + vanity/counter/glass/sanitary purchases)
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-POWDER', 'RM-MAID-BATH', 'RM-GUEST-BATH', 'RM-COMMON-BATH', 'RM-ENSUITE', 'RM-MASTER-BATH')
  AND wim.code IN ('C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N')
  AND (
        wi.work_item_name ILIKE '%bath%'
     OR wi.work_item_name ILIKE '%powder%'
     OR wi.work_item_name ILIKE '%maid%'
     OR wi.work_item_name ILIKE '%sanitary%'
     OR wi.work_item_name ILIKE '%vanity%'
     OR wi.work_item_name ILIKE '%shower%'
     OR wi.work_item_name ILIKE '%tile%'
     OR wi.work_item_name ILIKE '%water heater%'
     OR wi.work_item_name ILIKE '%plumbing%'
     OR wi.work_item_code ~ '^(C\.|E\.|F\.|G\.|H\.|I\.|K\.|L\.|M\.|N\.)'
  )
ON CONFLICT DO NOTHING;

-- Balcony / outdoor
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-PATIO', 'RM-TERRACE', 'RM-BALCONY-1', 'RM-BALCONY-2')
  AND (
        wim.code IN ('C', 'E', 'G')
     OR wi.work_item_name ILIKE '%patio%'
     OR wi.work_item_name ILIKE '%terrace%'
     OR wi.work_item_name ILIKE '%balcony%'
  )
ON CONFLICT DO NOTHING;

-- Circulation / staircase
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-FF-HALL', 'RM-SVC-PASS', 'RM-STAIR')
  AND (
        wim.code IN ('C', 'D', 'E', 'F', 'G', 'I', 'K', 'M')
     OR wi.work_item_name ILIKE '%stair%'
     OR wi.work_item_name ILIKE '%balustrade%'
     OR wi.work_item_name ILIKE '%handrail%'
  )
ON CONFLICT DO NOTHING;

-- Utility
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code = 'RM-UTILITY'
  AND wim.code IN ('C', 'D', 'E', 'F', 'G', 'H', 'I', 'K')
ON CONFLICT DO NOTHING;

-- Doors / other packages
INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-INT-DOORS', 'RM-MAIN-DOORS')
  AND (
        wim.code IN ('K', 'N', 'M')
     OR wi.work_item_name ILIKE '%door%'
     OR wi.work_item_name ILIKE '%architrave%'
     OR wi.work_item_name ILIKE '%ironmonger%'
     OR wi.work_item_name ILIKE '%handle%'
  )
ON CONFLICT DO NOTHING;

INSERT INTO room_type_work_items (room_type_id, work_item_id)
SELECT rt.id, wi.id
FROM room_types rt
JOIN work_items wi ON wi.company_id = rt.company_id AND COALESCE(wi.deleted, FALSE) = FALSE AND COALESCE(wi.active, TRUE) = TRUE
JOIN work_item_masters wim ON wim.id = wi.work_item_master_id
WHERE rt.room_code IN ('RM-EXT-PAINT', 'RM-ROOF-GAR', 'RM-BOUND-WALL')
  AND (
        wim.code IN ('G', 'D', 'C')
     OR wi.work_item_name ILIKE '%paint%'
     OR wi.work_item_name ILIKE '%external%'
     OR wi.work_item_name ILIKE '%wall%'
  )
ON CONFLICT DO NOTHING;
