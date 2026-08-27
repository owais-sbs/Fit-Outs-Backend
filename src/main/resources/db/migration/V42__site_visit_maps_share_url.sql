ALTER TABLE site_visit_location_details
    ADD COLUMN IF NOT EXISTS maps_share_url VARCHAR(500);
