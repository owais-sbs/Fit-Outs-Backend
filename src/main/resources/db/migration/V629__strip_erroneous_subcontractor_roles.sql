-- V80 incorrectly granted SUBCONTRACTOR to every @fitouts.demo account.
-- That made Site Engineer / Employee / Admin / Client / etc. show two portals on login.
-- Keep SUBCONTRACTOR only on accounts that do not also hold another portal role.

DELETE FROM account_roles ar
WHERE ar.role = 'SUBCONTRACTOR'
  AND EXISTS (
      SELECT 1
      FROM account_roles other
      WHERE other.account_id = ar.account_id
        AND other.role IN (
            'SUPER_ADMIN',
            'ADMIN',
            'BUSINESS_OWNER',
            'PROJECT_MANAGER',
            'DESIGNER',
            'QAS',
            'QS',
            'SENIOR_QS',
            'FINANCE',
            'SALES',
            'EMPLOYEE',
            'SITE_ENGINEER',
            'CLIENT'
        )
  );
