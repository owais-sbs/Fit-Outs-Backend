-- Renumbered from V83 after merging main (main owns V73–V80 for subcontractor portal).
-- Withdrawn is no longer a permit status. Reopen those cases so they can be worked again.
-- Drop withdrawn rows that would collide with a live (or older withdrawn) duplicate.

DELETE FROM approval_case w
WHERE w.status = 'WITHDRAWN'
  AND EXISTS (
      SELECT 1
      FROM approval_case other
      WHERE other.project_id = w.project_id
        AND other.company_id = w.company_id
        AND other.permit_type_code = w.permit_type_code
        AND COALESCE(other.authority_code, '') = COALESCE(w.authority_code, '')
        AND other.uuid <> w.uuid
        AND (
            other.status NOT IN ('WITHDRAWN', 'REJECTED', 'CLOSED')
            OR (other.status = 'WITHDRAWN' AND other.created_at < w.created_at)
            OR (other.status = 'WITHDRAWN' AND other.created_at = w.created_at AND other.uuid < w.uuid)
        )
  );

INSERT INTO approval_case_event (uuid, case_uuid, from_status, to_status, action, detail, created_at)
SELECT gen_random_uuid(),
       uuid,
       'WITHDRAWN',
       'NOT_STARTED',
       'REOPENED',
       'Withdrawn status removed; case reopened as not started',
       now()
FROM approval_case
WHERE status = 'WITHDRAWN';

UPDATE approval_case
SET status = 'NOT_STARTED',
    closed_reason = NULL,
    updated_at = now()
WHERE status = 'WITHDRAWN';
