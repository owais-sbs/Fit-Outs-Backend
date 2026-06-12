ALTER TABLE account_roles DROP CONSTRAINT IF EXISTS account_roles_role_check;

ALTER TABLE account_roles ADD CONSTRAINT account_roles_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'ADMIN',
        'BUSINESS_OWNER',
        'PROJECT_MANAGER',
        'DESIGNER',
        'QAS',
        'FINANCE',
        'SUBCONTRACTOR',
        'CLIENT',
        'SALES',
        'EMPLOYEE'
    ));
