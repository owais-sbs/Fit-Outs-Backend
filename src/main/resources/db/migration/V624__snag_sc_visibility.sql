-- Module 17: optional subcontractor portal visibility + recipient account ids
ALTER TABLE snag ADD COLUMN IF NOT EXISTS sc_visible BOOLEAN;
UPDATE snag SET sc_visible = FALSE WHERE sc_visible IS NULL;
ALTER TABLE snag ALTER COLUMN sc_visible SET DEFAULT FALSE;
ALTER TABLE snag ALTER COLUMN sc_visible SET NOT NULL;

ALTER TABLE snag ADD COLUMN IF NOT EXISTS sc_recipient_account_ids TEXT;

COMMENT ON COLUMN snag.sc_visible IS 'When true, selected SC portal accounts can see this snag';
COMMENT ON COLUMN snag.sc_recipient_account_ids IS 'Comma-separated account ids of appointed SCs who can see the snag';
