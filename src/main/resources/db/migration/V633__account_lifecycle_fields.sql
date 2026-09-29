ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS purge_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deletion_requested_by BIGINT REFERENCES accounts (id);

UPDATE accounts SET created_at = NOW() WHERE created_at IS NULL;

ALTER TABLE accounts
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN created_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_accounts_purge_at ON accounts (purge_at) WHERE purge_at IS NOT NULL;
