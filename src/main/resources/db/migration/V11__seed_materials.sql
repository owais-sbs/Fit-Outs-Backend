-- Seed default material categories and catalog items for every company (idempotent).

INSERT INTO material_categories (company_id, name, code)
SELECT c.uuid, v.name, v.code
FROM companies c
CROSS JOIN (VALUES
    ('Flooring', 'FLOOR'),
    ('Ceiling', 'CEIL'),
    ('Partition & Drywall', 'PART'),
    ('Paint & Finishes', 'PAINT'),
    ('Electrical', 'ELEC'),
    ('Plumbing', 'PLUMB'),
    ('Joinery', 'JOIN'),
    ('Stone & Countertops', 'STONE'),
    ('Glass & Aluminum', 'GLASS'),
    ('Hardware & Fixtures', 'FIX')
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
    ('FLOOR', 'Porcelain Floor Tile 600x600', 'FLR-POR-600', 'SQM', 45.00, 65.00, 'RAK Ceramics', 'RAK-POR-600', 'Standard porcelain floor tile'),
    ('FLOOR', 'Vinyl Plank Flooring', 'FLR-VNL-PLK', 'SQM', 35.00, 52.00, 'Gerflor', 'GF-VNL-01', 'Commercial grade vinyl plank'),
    ('FLOOR', 'Marble Floor Tile', 'FLR-MRB-TILE', 'SQM', 120.00, 165.00, 'Al Habtoor Marble', 'AH-MRB-01', 'Polished marble floor tile'),
    ('FLOOR', 'Epoxy Floor Coating', 'FLR-EPX-COAT', 'SQM', 28.00, 42.00, 'Sika', 'SIKA-EPX', '2-part epoxy floor coating'),

    ('CEIL', 'Gypsum Ceiling Board 12.5mm', 'CEL-GYP-125', 'SQM', 22.00, 32.00, 'Knauf', 'KN-GYP-125', 'Moisture resistant gypsum board'),
    ('CEIL', 'Metal Ceiling Grid System', 'CEL-GRD-MTL', 'SQM', 18.00, 28.00, 'Armstrong', 'ARM-GRD', 'Suspended ceiling grid'),
    ('CEIL', 'Acoustic Ceiling Tile', 'CEL-ACO-TILE', 'SQM', 38.00, 55.00, 'Rockfon', 'RK-ACO-01', 'Acoustic mineral fiber tile'),
    ('CEIL', 'LED Cove Light Profile', 'CEL-COVE-LED', 'RMT', 15.00, 24.00, 'Philips', 'PH-COVE', 'Aluminum LED cove profile'),

    ('PART', 'Gypsum Partition 100mm', 'PRT-GYP-100', 'SQM', 85.00, 120.00, 'Knauf', 'KN-PART-100', 'Single layer both sides'),
    ('PART', 'Glass Partition Single', 'PRT-GLS-SNG', 'SQM', 280.00, 380.00, 'Dorma', 'DR-GLS-SNG', '10mm tempered glass partition'),
    ('PART', 'Rockwool Insulation 50mm', 'PRT-INS-RW50', 'SQM', 12.00, 18.00, 'Rockwool', 'RW-50', 'Partition cavity insulation'),
    ('PART', 'Metal Stud & Track 75mm', 'PRT-STUD-75', 'RMT', 8.00, 14.00, 'Knauf', 'KN-STUD-75', 'Galvanized partition stud'),

    ('PAINT', 'Interior Emulsion Paint', 'PNT-EMU-INT', 'SQM', 8.00, 14.00, 'Jotun', 'JT-EMU', '2-coat interior emulsion'),
    ('PAINT', 'Epoxy Paint 2-Coat', 'PNT-EPX-2C', 'SQM', 22.00, 35.00, 'Jotun', 'JT-EPX', 'Industrial epoxy paint system'),
    ('PAINT', 'Primer Sealer', 'PNT-PRM-SEL', 'SQM', 5.00, 9.00, 'National Paints', 'NP-PRM', 'Wall primer sealer'),
    ('PAINT', 'Textured Wall Finish', 'PNT-TEX-FIN', 'SQM', 18.00, 28.00, 'Dulux', 'DL-TEX', 'Decorative textured finish'),

    ('ELEC', 'LED Downlight 12W', 'ELC-DNL-12W', 'PCS', 18.00, 28.00, 'Philips', 'PH-DNL-12', 'Recessed LED downlight'),
    ('ELEC', 'Power Socket 13A', 'ELC-SKT-13A', 'PCS', 12.00, 20.00, 'MK Electric', 'MK-SKT-13', 'British standard socket'),
    ('ELEC', 'Cable 2.5mm 3C', 'ELC-CBL-25', 'RMT', 6.00, 10.00, 'Ducab', 'DC-CBL-25', 'Power cable 2.5mm'),
    ('ELEC', 'Distribution Board 12-Way', 'ELC-DB-12W', 'PCS', 450.00, 620.00, 'Schneider', 'SE-DB-12', '12-way consumer unit'),

    ('PLUMB', 'PPR Pipe 25mm', 'PLM-PPR-25', 'RMT', 8.00, 14.00, 'Pilsa', 'PL-PPR-25', 'Hot/cold water PPR pipe'),
    ('PLUMB', 'WC Set Wall-Hung', 'PLM-WC-WH', 'SET', 650.00, 920.00, 'Roca', 'RC-WC-WH', 'Wall-hung WC complete set'),
    ('PLUMB', 'Basin Mixer', 'PLM-BSN-MIX', 'PCS', 180.00, 260.00, 'Grohe', 'GR-BSN-MIX', 'Single lever basin mixer'),
    ('PLUMB', 'Floor Drain SS', 'PLM-DRN-SS', 'PCS', 35.00, 55.00, 'McAlpine', 'MC-DRN-SS', 'Stainless floor drain'),

    ('JOIN', 'MDF 18mm Laminated', 'JNY-MDF-18', 'SQM', 95.00, 140.00, 'Kastamonu', 'KS-MDF-18', 'Laminated MDF for joinery'),
    ('JOIN', 'Solid Wood Veneer', 'JNY-VNR-SWD', 'SQM', 180.00, 250.00, 'Decospan', 'DC-VNR', 'Natural wood veneer sheet'),
    ('JOIN', 'Soft-Close Hinge', 'JNY-HNG-SC', 'PCS', 12.00, 20.00, 'Blum', 'BL-HNG-SC', 'Cabinet soft-close hinge'),
    ('JOIN', 'Drawer Runner Set', 'JNY-DRW-RUN', 'SET', 28.00, 42.00, 'Hettich', 'HT-DRW-RUN', 'Full extension drawer runner'),

    ('STONE', 'Quartz Countertop 20mm', 'STN-QTZ-20', 'SQM', 320.00, 450.00, 'Caesarstone', 'CS-QTZ-20', 'Engineered quartz countertop'),
    ('STONE', 'Granite Countertop 30mm', 'STN-GRN-30', 'SQM', 280.00, 390.00, 'Al Habtoor Marble', 'AH-GRN-30', 'Polished granite countertop'),
    ('STONE', 'Stone Skirting 100mm', 'STN-SKT-100', 'RMT', 45.00, 65.00, 'Al Habtoor Marble', 'AH-SKT-100', 'Marble skirting profile'),

    ('GLASS', 'Tempered Glass 10mm', 'GLS-TMP-10', 'SQM', 145.00, 210.00, 'Guardian Glass', 'GD-TMP-10', 'Clear tempered glass'),
    ('GLASS', 'Aluminum Shop Front', 'GLS-SHF-ALU', 'SQM', 420.00, 580.00, 'Schuco', 'SC-SHF', 'Powder coated shopfront system'),
    ('GLASS', 'Sliding Door Track Set', 'GLS-SLD-TRK', 'SET', 380.00, 520.00, 'Dorma', 'DR-SLD-TRK', 'Top hung sliding door hardware'),

    ('FIX', 'Door Handle SS', 'FIX-HND-SS', 'PCS', 45.00, 68.00, 'Yale', 'YL-HND-SS', 'Stainless lever handle'),
    ('FIX', 'Kitchen Sink SS', 'FIX-SNK-SS', 'PCS', 220.00, 310.00, 'Franke', 'FR-SNK-SS', 'Undermount stainless sink'),
    ('FIX', 'Silicone Sealant', 'FIX-SIL-300', 'PCS', 15.00, 24.00, 'Dow Corning', 'DC-SIL-300', 'Sanitary silicone cartridge'),
    ('FIX', 'Tile Adhesive 20kg', 'FIX-ADH-20', 'BAG', 22.00, 32.00, 'Laticrete', 'LT-ADH-20', 'Flexible tile adhesive')
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
JOIN material_categories mc ON mc.company_id = c.uuid AND mc.code = v.cat_code AND mc.deleted = FALSE
ON CONFLICT (company_id, material_code) DO NOTHING;

INSERT INTO material_stock (company_id, material_id, quantity_on_hand)
SELECT m.company_id, m.id, 0
FROM materials m
WHERE m.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1 FROM material_stock ms WHERE ms.material_id = m.id
  );
