-- Invited @fitouts.demo vendor accounts use password 123456 (same hash as V15 demo users).
UPDATE accounts
SET password = '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq',
    is_active = TRUE
WHERE email LIKE '%@fitouts.demo'
  AND email NOT IN (
      'superadmin@fitouts.demo',
      'admin@fitouts.demo',
      'qs@fitouts.demo',
      'seniorqs@fitouts.demo',
      'pm@fitouts.demo',
      'director@fitouts.demo',
      'client@fitouts.demo',
      'designer@fitouts.demo',
      'qas@fitouts.demo',
      'finance@fitouts.demo',
      'sales@fitouts.demo',
      'employee@fitouts.demo',
      'subcontractor@fitouts.demo',
      'estimator@fitouts.demo',
      'supervisor@fitouts.demo',
      'scqs@fitouts.demo',
      'doccontroller@fitouts.demo'
  );

-- Ensure invited demo vendors can open the subcontractor portal.
INSERT INTO account_roles (account_id, role)
SELECT a.id, 'SUBCONTRACTOR'
FROM accounts a
WHERE a.email LIKE '%@fitouts.demo'
  AND NOT EXISTS (
      SELECT 1 FROM account_roles ar
      WHERE ar.account_id = a.id AND ar.role = 'SUBCONTRACTOR'
  );
