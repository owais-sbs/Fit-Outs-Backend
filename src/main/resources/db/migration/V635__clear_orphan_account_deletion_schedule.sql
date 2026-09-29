-- Clear incomplete deletion lifecycle rows that should not appear in the deletion queue.
UPDATE accounts
SET purge_at = NULL,
    deletion_requested_by = NULL
WHERE purge_at IS NOT NULL
  AND deletion_scheduled_at IS NULL;
