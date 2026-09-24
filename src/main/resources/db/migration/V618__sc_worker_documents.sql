-- Worker identity / compliance document file paths (passport, visa, EID, etc.)
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS passport_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS visa_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS emirates_id_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS insurance_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS induction_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS access_card_file_path TEXT;
ALTER TABLE sc_worker ADD COLUMN IF NOT EXISTS trade_cert_file_path TEXT;
