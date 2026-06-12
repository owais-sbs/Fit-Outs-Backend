ALTER TABLE site_visits ALTER COLUMN assigned_to DROP NOT NULL;

UPDATE site_visits SET assigned_to = NULL WHERE assigned_to IS NOT NULL AND assigned_to NOT IN (SELECT id FROM accounts);
