-- Seed opening stock via purchase receipts for catalog materials (idempotent).

INSERT INTO stock_movements (
    company_id,
    material_id,
    movement_type,
    quantity,
    unit_cost,
    total_cost,
    reference_no,
    notes,
    movement_date,
    created_at
)
SELECT
    m.company_id,
    m.id,
    'RECEIPT',
    v.quantity,
    m.cost_price,
    ROUND(m.cost_price * v.quantity, 2),
    v.reference_no,
    'Seeded opening purchase — warehouse receipt',
    NOW() - INTERVAL '14 days',
    NOW()
FROM materials m
JOIN (VALUES
    ('FLR-POR-600', 320.000, 'PO-SEED-2026-001'),
    ('FLR-VNL-PLK', 280.000, 'PO-SEED-2026-002'),
    ('FLR-MRB-TILE', 150.000, 'PO-SEED-2026-003'),
    ('FLR-EPX-COAT', 200.000, 'PO-SEED-2026-004'),
    ('CEL-GYP-125', 450.000, 'PO-SEED-2026-005'),
    ('CEL-GRD-MTL', 380.000, 'PO-SEED-2026-006'),
    ('CEL-ACO-TILE', 220.000, 'PO-SEED-2026-007'),
    ('CEL-COVE-LED', 600.000, 'PO-SEED-2026-008'),
    ('PRT-GYP-100', 180.000, 'PO-SEED-2026-009'),
    ('PRT-GLS-SNG', 45.000, 'PO-SEED-2026-010'),
    ('PRT-INS-RW50', 300.000, 'PO-SEED-2026-011'),
    ('PRT-STUD-75', 800.000, 'PO-SEED-2026-012'),
    ('PNT-EMU-INT', 500.000, 'PO-SEED-2026-013'),
    ('PNT-EPX-2C', 120.000, 'PO-SEED-2026-014'),
    ('PNT-PRM-SEL', 400.000, 'PO-SEED-2026-015'),
    ('PNT-TEX-FIN', 90.000, 'PO-SEED-2026-016'),
    ('ELC-DNL-12W', 180.000, 'PO-SEED-2026-017'),
    ('ELC-SKT-13A', 120.000, 'PO-SEED-2026-018'),
    ('ELC-CBL-25', 1500.000, 'PO-SEED-2026-019'),
    ('ELC-DB-12W', 15.000, 'PO-SEED-2026-020'),
    ('PLM-PPR-25', 900.000, 'PO-SEED-2026-021'),
    ('PLM-WC-WH', 24.000, 'PO-SEED-2026-022'),
    ('PLM-BSN-MIX', 36.000, 'PO-SEED-2026-023'),
    ('PLM-DRN-SS', 48.000, 'PO-SEED-2026-024'),
    ('JNY-MDF-18', 95.000, 'PO-SEED-2026-025'),
    ('JNY-VNR-SWD', 60.000, 'PO-SEED-2026-026'),
    ('JNY-HNG-SC', 200.000, 'PO-SEED-2026-027'),
    ('JNY-DRW-RUN', 80.000, 'PO-SEED-2026-028'),
    ('STN-QTZ-20', 35.000, 'PO-SEED-2026-029'),
    ('STN-GRN-30', 28.000, 'PO-SEED-2026-030'),
    ('STN-SKT-100', 400.000, 'PO-SEED-2026-031'),
    ('GLS-TMP-10', 55.000, 'PO-SEED-2026-032'),
    ('GLS-SHF-ALU', 22.000, 'PO-SEED-2026-033'),
    ('GLS-SLD-TRK', 12.000, 'PO-SEED-2026-034'),
    ('FIX-HND-SS', 75.000, 'PO-SEED-2026-035'),
    ('FIX-SNK-SS', 18.000, 'PO-SEED-2026-036'),
    ('FIX-SIL-300', 150.000, 'PO-SEED-2026-037'),
    ('FIX-ADH-20', 80.000, 'PO-SEED-2026-038')
) AS v(material_code, quantity, reference_no)
    ON m.material_code = v.material_code
WHERE m.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM stock_movements sm
      WHERE sm.material_id = m.id
        AND sm.reference_no = v.reference_no
  );

UPDATE material_stock ms
SET
    quantity_on_hand = totals.receipt_qty,
    updated_at = NOW()
FROM (
    SELECT
        sm.material_id,
        COALESCE(SUM(
            CASE
                WHEN sm.movement_type = 'RECEIPT' THEN sm.quantity
                WHEN sm.movement_type = 'ISSUE' THEN -sm.quantity
                WHEN sm.movement_type = 'RETURN' THEN sm.quantity
                WHEN sm.movement_type = 'ADJUSTMENT' THEN sm.quantity
                ELSE 0
            END
        ), 0) AS receipt_qty
    FROM stock_movements sm
    GROUP BY sm.material_id
) AS totals
WHERE ms.material_id = totals.material_id;
