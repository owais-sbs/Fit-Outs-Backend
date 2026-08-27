-- Seed BOQ purchase / supply materials from categories K–N (idempotent).

INSERT INTO material_categories (company_id, name, code)
SELECT c.uuid, v.name, v.code
FROM companies c
CROSS JOIN (VALUES
    ('Purchases', 'PURCH')
) AS v(name, code)
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO materials (
    company_id,
    material_category_id,
    material_name,
    material_code,
    unit_type,
    cost_price,
    selling_price,
    supplier_name,
    sku,
    description
)
SELECT
    c.uuid,
    mc.id,
    v.material_name,
    v.material_code,
    v.unit_type,
    v.cost_price,
    v.selling_price,
    v.supplier_name,
    v.sku,
    v.description
FROM companies c
CROSS JOIN (VALUES
    ('ELEC', 'IP65 WHITE frame LED spotlight (non-dimmable)', 'PUR-LED-IP65-W', 'PCS', 115.00, 135.00, 'JCT Purchases', 'N.1', 'Bathrooms and kitchen'),
    ('ELEC', 'IP20 WHITE frame LED spotlight (non-dimmable)', 'PUR-LED-IP20-W', 'PCS', 45.00, 50.00, 'JCT Purchases', 'N.2', 'For item I.1'),
    ('ELEC', 'IP20 BLACK frame LED spotlight (non-dimmable)', 'PUR-LED-IP20-B', 'PCS', 130.00, 150.00, 'JCT Purchases', 'N.3', 'For item I.1'),
    ('ELEC', 'Legrand Mallia Senses White face plates (90pcs provisional)', 'PUR-FACE-MALLIA', 'LOT', 5000.00, 5000.00, 'Legrand', 'N.4', 'Switches and sockets face plates'),
    ('FIX', 'Internal / utility door handle chrome finish', 'PUR-HND-INT-CHR', 'PCS', 550.00, 650.00, 'JCT Purchases', 'N.5', 'Provisional door handles'),
    ('FIX', 'Main Door handle chrome finish', 'PUR-HND-MAIN-CHR', 'PCS', 750.00, 850.00, 'JCT Purchases', 'N.6', 'Provisional main door handle'),
    ('PLUMB', 'Sanitary Fixtures Maid Bathroom Kludi/RAK', 'PUR-SAN-MAID-KR', 'LOT', 2000.00, 2500.00, 'Kludi/RAK', 'N.7', 'Provisional sanitary package'),
    ('FLOOR', 'RAK Ceramics Tile 60x60cm (supply)', 'PUR-TILE-RAK-6060', 'SQM', 65.00, 65.00, 'RAK Ceramics', 'N.9', 'Floor and wall tiles supply rate'),
    ('PLUMB', 'Sanitary Fixtures Powder Room BAGNO DESIGN', 'PUR-SAN-PR-BAGNO', 'LOT', 3250.00, 3250.00, 'BAGNO DESIGN', 'N.10.a', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Guest Bathroom shower BAGNO', 'PUR-SAN-GB-SH-BAGNO', 'LOT', 4875.00, 4875.00, 'BAGNO DESIGN', 'N.10.a2', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Common Bathroom bathtub BAGNO', 'PUR-SAN-CB-BT-BAGNO', 'LOT', 6375.00, 6375.00, 'BAGNO DESIGN', 'N.10.b', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Common Bathroom shower BAGNO', 'PUR-SAN-CB-SH-BAGNO', 'LOT', 4875.00, 4875.00, 'BAGNO DESIGN', 'N.10.b2', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom single sink BAGNO', 'PUR-SAN-MB-SS-BAGNO', 'LOT', 4875.00, 4875.00, 'BAGNO DESIGN', 'N.10.d', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom double sink BAGNO', 'PUR-SAN-MB-DS-BAGNO', 'LOT', 5800.00, 5800.00, 'BAGNO DESIGN', 'N.10.c', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom inset bathtub BAGNO', 'PUR-SAN-MB-IB-BAGNO', 'LOT', 5500.00, 5500.00, 'BAGNO DESIGN', 'N.10.c2', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom stand alone bathtub BAGNO', 'PUR-SAN-MB-SA-BAGNO', 'LOT', 17500.00, 17500.00, 'BAGNO DESIGN', 'N.10.c3', 'Provisional sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Powder Room Kludi/RAK', 'PUR-SAN-PR-KR', 'LOT', 2500.00, 2500.00, 'Kludi/RAK', 'N.11.a', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Common Bathroom bathtub Kludi/RAK', 'PUR-SAN-CB-BT-KR', 'LOT', 3850.00, 3850.00, 'Kludi/RAK', 'N.11.b', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Common Bathroom shower Kludi/RAK', 'PUR-SAN-CB-SH-KR', 'LOT', 4350.00, 4350.00, 'Kludi/RAK', 'N.11.c', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom single sink Kludi/RAK', 'PUR-SAN-MB-SS-KR', 'LOT', 3400.00, 3400.00, 'Kludi/RAK', 'N.11.d', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom double sink Kludi/RAK', 'PUR-SAN-MB-DS-KR', 'LOT', 4050.00, 4050.00, 'Kludi/RAK', 'N.11.e', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom inset bathtub Kludi/RAK', 'PUR-SAN-MB-IB-KR', 'LOT', 5200.00, 5200.00, 'Kludi/RAK', 'N.11.f', 'Stand alone sanitary package'),
    ('PLUMB', 'Sanitary Fixtures Master Bathroom stand alone bathtub Kludi/RAK', 'PUR-SAN-MB-SA-KR', 'LOT', 17000.00, 17000.00, 'Kludi/RAK', 'N.11.g', 'Stand alone sanitary package'),
    ('FIX', 'Kitchen Sink and Mixer BAGNO DESIGN', 'PUR-SINK-MIX-BAGNO', 'LOT', 2200.00, 2200.00, 'BAGNO DESIGN', 'N.12', 'Provisional kitchen sink and mixer'),
    ('STONE', 'Thasos White KOZO Quartz 20mm countertop', 'PUR-QTZ-THASOS-20', 'SQM', 0.00, 0.00, 'KOZO', 'L.1', '20mm thk with 4cm fascia'),
    ('JOIN', 'MDF Architrave PU paint finish', 'PUR-ARCH-MDF-PU', 'PCS', 300.00, 360.00, 'JCT Joinery', 'K.4', 'One side architrave paint finish'),
    ('GLASS', 'Shower glass fixed panel 10mm tempered 1.0x2.4m', 'PUR-GLS-SHW-FIX', 'PCS', 0.00, 0.00, 'JCT Glass', 'M.1', 'Chrome U channel'),
    ('GLASS', 'Glass balustrade laminated 17.52mm side mounted', 'PUR-GLS-BAL-1752', 'RMT', 3100.00, 3680.00, 'JCT Glass', 'M.2', 'SS exposed accessories')
) AS v(
    cat_code,
    material_name,
    material_code,
    unit_type,
    cost_price,
    selling_price,
    supplier_name,
    sku,
    description
)
JOIN material_categories mc
  ON mc.company_id = c.uuid
 AND mc.code = v.cat_code
 AND mc.deleted = FALSE
ON CONFLICT (company_id, material_code) DO NOTHING;

INSERT INTO material_stock (company_id, material_id, quantity_on_hand)
SELECT m.company_id, m.id, 0
FROM materials m
WHERE m.deleted = FALSE
  AND m.material_code LIKE 'PUR-%'
  AND NOT EXISTS (
      SELECT 1 FROM material_stock ms WHERE ms.material_id = m.id
  );
