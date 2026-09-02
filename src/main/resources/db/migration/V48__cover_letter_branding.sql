ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS stamp_image_path VARCHAR(512),
    ADD COLUMN IF NOT EXISTS signature_image_path VARCHAR(512);

ALTER TABLE site_visit_estimates
    ADD COLUMN IF NOT EXISTS include_stamp BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS include_signature BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS stamp_image_path VARCHAR(512),
    ADD COLUMN IF NOT EXISTS signature_image_path VARCHAR(512);
