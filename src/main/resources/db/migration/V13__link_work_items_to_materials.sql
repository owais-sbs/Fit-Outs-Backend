-- Link common work items to seeded materials and recalculate rates from material cost + markup.

-- Flooring / tiles
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 8.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'FLR-VNL-PLK'
WHERE wi.deleted = FALSE
  AND (
    wi.work_item_name ILIKE '%pvc%floor%'
    OR wi.work_item_name ILIKE '%vinyl%floor%'
    OR wi.work_item_name ILIKE '%vinyl%plank%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 0.0400, 0.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'FIX-ADH-20'
WHERE wi.deleted = FALSE
  AND (
    wi.work_item_name ILIKE '%pvc%floor%'
    OR wi.work_item_name ILIKE '%vinyl%floor%'
    OR wi.work_item_name ILIKE '%porcelain%tile%'
    OR wi.work_item_name ILIKE '%floor%tile%'
    OR wi.work_item_name ILIKE '%marble%floor%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 10.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'FLR-POR-600'
WHERE wi.deleted = FALSE
  AND (wi.work_item_name ILIKE '%porcelain%tile%' OR wi.work_item_name ILIKE '%ceramic%tile%')
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 12.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'FLR-MRB-TILE'
WHERE wi.deleted = FALSE
  AND wi.work_item_name ILIKE '%marble%floor%'
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'FLR-EPX-COAT'
WHERE wi.deleted = FALSE
  AND wi.work_item_name ILIKE '%epoxy%floor%'
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Ceiling
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'CEL-GYP-125'
WHERE wi.deleted = FALSE
  AND (
    wi.work_item_name ILIKE '%gypsum%ceiling%'
    OR wi.work_item_name ILIKE '%false%ceiling%'
    OR wi.work_item_name ILIKE '%ceiling%board%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 8.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'CEL-ACO-TILE'
WHERE wi.deleted = FALSE
  AND wi.work_item_name ILIKE '%acoustic%ceiling%'
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Partition / walls
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'PRT-GYP-100'
WHERE wi.deleted = FALSE
  AND (
    wi.work_item_name ILIKE '%gypsum%partition%'
    OR wi.work_item_name ILIKE '%drywall%'
    OR wi.work_item_name ILIKE '%wall%partition%'
    OR wi.work_item_name ILIKE '%wall%cladding%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'PRT-GLS-SNG'
WHERE wi.deleted = FALSE
  AND wi.work_item_name ILIKE '%glass%partition%'
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Paint
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'PNT-EMU-INT'
WHERE wi.deleted = FALSE
  AND (
    wi.work_item_name ILIKE '%emulsion%paint%'
    OR wi.work_item_name ILIKE '%interior%paint%'
    OR wi.work_item_name ILIKE '%wall%paint%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 5.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'PNT-EPX-2C'
WHERE wi.deleted = FALSE
  AND wi.work_item_name ILIKE '%epoxy%paint%'
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Electrical
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 0.2500, 0.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'ELC-DNL-12W'
WHERE wi.deleted = FALSE
  AND (wi.work_item_name ILIKE '%downlight%' OR wi.work_item_name ILIKE '%led%light%')
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Joinery / counters
INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 8.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'JNY-MDF-18'
WHERE wi.deleted = FALSE
  AND (wi.work_item_name ILIKE '%mdf%' OR wi.work_item_name ILIKE '%joinery%cabinet%')
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

INSERT INTO work_item_materials (work_item_id, material_id, quantity_per_unit, wastage_percent)
SELECT wi.id, m.id, 1.0000, 10.00
FROM work_items wi
JOIN materials m ON m.company_id = wi.company_id AND m.material_code = 'STN-QTZ-20'
WHERE wi.deleted = FALSE
  AND (wi.work_item_name ILIKE '%quartz%counter%' OR wi.work_item_name ILIKE '%counter%top%')
  AND NOT EXISTS (
    SELECT 1 FROM work_item_materials wim
    WHERE wim.work_item_id = wi.id AND wim.material_id = m.id
  );

-- Recalculate work item cost + selling rate from linked materials
UPDATE work_items wi
SET
    markup_percentage = COALESCE(NULLIF(wi.markup_percentage, 0), 15),
    cost_price_override = FALSE,
    selling_price_override = FALSE,
    cost_price = totals.material_cost,
    default_rate = ROUND(
        totals.material_cost * (1 + COALESCE(NULLIF(wi.markup_percentage, 0), 15) / 100.0),
        2
    )
FROM (
    SELECT
        wim.work_item_id,
        ROUND(SUM(
            m.cost_price * wim.quantity_per_unit * (1 + wim.wastage_percent / 100.0)
        )::numeric, 2) AS material_cost
    FROM work_item_materials wim
    JOIN materials m ON m.id = wim.material_id
    GROUP BY wim.work_item_id
) AS totals
WHERE wi.id = totals.work_item_id
  AND wi.deleted = FALSE;
