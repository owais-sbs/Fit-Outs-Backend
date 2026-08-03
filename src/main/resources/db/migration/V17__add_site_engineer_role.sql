-- Add SITE_ENGINEER to account_roles check constraint and seed demo user + employee

ALTER TABLE account_roles DROP CONSTRAINT IF EXISTS account_roles_role_check;

ALTER TABLE account_roles ADD CONSTRAINT account_roles_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'ADMIN',
        'BUSINESS_OWNER',
        'PROJECT_MANAGER',
        'DESIGNER',
        'QAS',
        'QS',
        'SENIOR_QS',
        'FINANCE',
        'SUBCONTRACTOR',
        'CLIENT',
        'SALES',
        'EMPLOYEE',
        'SITE_ENGINEER'
    ));

DO $$
DECLARE
    v_company_id UUID;
    v_company_name TEXT;
    v_account_id BIGINT;
    v_employee_id BIGINT;
    v_hash TEXT := '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq';
BEGIN
    SELECT c.uuid, c.company_name
    INTO v_company_id, v_company_name
    FROM companies c
    ORDER BY c.created_at NULLS LAST, c.company_name
    LIMIT 1;

    IF v_company_id IS NULL THEN
        RAISE NOTICE 'V17 site engineer seed skipped: no company found';
        RETURN;
    END IF;

    INSERT INTO accounts (full_name, email, password, phone, company_name, company_id, is_active)
    VALUES ('Site Engineer', 'siteengineer@fitouts.demo', v_hash, '+971500000014', v_company_name, v_company_id, TRUE)
    ON CONFLICT (email) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        password = EXCLUDED.password,
        phone = EXCLUDED.phone,
        company_name = EXCLUDED.company_name,
        company_id = EXCLUDED.company_id,
        is_active = TRUE;

    SELECT id INTO v_account_id FROM accounts WHERE email = 'siteengineer@fitouts.demo';

    DELETE FROM account_roles WHERE account_id = v_account_id;
    INSERT INTO account_roles (account_id, role) VALUES (v_account_id, 'SITE_ENGINEER');

    IF NOT EXISTS (
        SELECT 1 FROM employees WHERE email = 'siteengineer@fitouts.demo' AND is_deleted = FALSE
    ) THEN
        INSERT INTO employees (
            employee_name, email, phone, designation,
            is_active, is_deleted, account_id, company_id, created_at, updated_at
        ) VALUES (
            'Site Engineer', 'siteengineer@fitouts.demo', '+971500000014', 'Site Engineer',
            TRUE, FALSE, v_account_id, v_company_id, NOW(), NOW()
        )
        RETURNING id INTO v_employee_id;
    ELSE
        UPDATE employees
        SET account_id = v_account_id,
            designation = 'Site Engineer',
            is_active = TRUE,
            is_deleted = FALSE,
            updated_at = NOW()
        WHERE email = 'siteengineer@fitouts.demo';
    END IF;
END $$;
