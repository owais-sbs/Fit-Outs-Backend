ALTER TABLE sc_portal_user
    ADD COLUMN IF NOT EXISTS permissions_json TEXT;
