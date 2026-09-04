-- One live BOQ per project. Extra documents become obsolete history.
-- Prefer the latest approved version; otherwise keep the latest document live.

WITH ranked AS (
    SELECT
        id,
        project_id,
        parent_boq_id,
        ROW_NUMBER() OVER (
            PARTITION BY project_id
            ORDER BY
                CASE WHEN status IN ('APPROVED', 'FINAL') THEN 0 ELSE 1 END,
                created_at DESC NULLS LAST
        ) AS rn
    FROM boq_documents
), live AS (
    SELECT
        id,
        project_id,
        COALESCE(parent_boq_id, id) AS root_id
    FROM ranked
    WHERE rn = 1
)
UPDATE boq_documents b
SET
    status = 'OBSOLETE',
    current_approval_step = NULL,
    parent_boq_id = COALESCE(b.parent_boq_id, live.root_id),
    updated_at = NOW()
FROM live
WHERE b.project_id = live.project_id
  AND b.id <> live.id
  AND b.status <> 'OBSOLETE';
