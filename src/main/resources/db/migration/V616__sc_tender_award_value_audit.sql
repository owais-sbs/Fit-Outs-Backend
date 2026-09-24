ALTER TABLE sc_package_award
    ADD COLUMN IF NOT EXISTS award_value_reason TEXT,
    ADD COLUMN IF NOT EXISTS awarded_by_account_id BIGINT;
