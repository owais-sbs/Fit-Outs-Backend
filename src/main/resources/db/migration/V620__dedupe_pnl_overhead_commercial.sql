-- Deduplicate P&L-related rows that break Single-Result Optional queries
-- ("Query did not return a unique result: 2 results").

-- 1) overhead_rule: keep newest active row per company
WITH ranked AS (
    SELECT uuid,
           ROW_NUMBER() OVER (
               PARTITION BY company_id
               ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, uuid
           ) AS rn
    FROM overhead_rule
    WHERE active = TRUE
)
UPDATE overhead_rule o
SET active = FALSE,
    updated_at = now()
FROM ranked r
WHERE o.uuid = r.uuid
  AND r.rn > 1;

-- 2) project_pnl_snapshot: keep newest calculated_at per company/project/period
WITH ranked AS (
    SELECT uuid,
           ROW_NUMBER() OVER (
               PARTITION BY company_id, project_id, period_year_month
               ORDER BY calculated_at DESC NULLS LAST, uuid
           ) AS rn
    FROM project_pnl_snapshot
)
DELETE FROM project_pnl_snapshot s
USING ranked r
WHERE s.uuid = r.uuid
  AND r.rn > 1;

-- 3) project_commercial: keep one row per project/company (lowest uuid)
WITH ranked AS (
    SELECT uuid,
           ROW_NUMBER() OVER (
               PARTITION BY project_id, company_id
               ORDER BY uuid
           ) AS rn
    FROM project_commercial
)
DELETE FROM project_commercial c
USING ranked r
WHERE c.uuid = r.uuid
  AND r.rn > 1;

-- Re-assert uniqueness (safe if already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_project_pnl_snapshot'
    ) THEN
        ALTER TABLE project_pnl_snapshot
            ADD CONSTRAINT uq_project_pnl_snapshot UNIQUE (company_id, project_id, period_year_month);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_project_commercial'
    ) THEN
        ALTER TABLE project_commercial
            ADD CONSTRAINT uq_project_commercial UNIQUE (project_id, company_id);
    END IF;
END $$;
