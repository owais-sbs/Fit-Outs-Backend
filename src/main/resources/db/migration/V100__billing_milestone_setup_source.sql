ALTER TABLE billing_milestone
    ADD COLUMN setup_source VARCHAR(32);

UPDATE billing_milestone
SET setup_source = 'MANUAL'
WHERE setup_source IS NULL;
