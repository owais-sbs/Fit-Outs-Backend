-- Seed demo portal users (password for all: 123456)
-- BCrypt hash generated with Spring BCryptPasswordEncoder strength 10

DO $$
DECLARE
    v_company_id UUID;
    v_company_name TEXT;
    v_client_id BIGINT;
    v_project_id BIGINT;
    v_hash TEXT := '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq';
BEGIN
    SELECT c.uuid, c.company_name
    INTO v_company_id, v_company_name
    FROM companies c
    ORDER BY c.created_at NULLS LAST, c.company_name
    LIMIT 1;

    IF v_company_id IS NULL THEN
        RAISE NOTICE 'V15 seed skipped: no company found';
        RETURN;
    END IF;

    -- Upsert accounts (idempotent by email)
    INSERT INTO accounts (full_name, email, password, phone, company_name, company_id, is_active)
    VALUES
        ('Super Admin',    'superadmin@fitouts.demo', v_hash, '+971500000001', v_company_name, v_company_id, TRUE),
        ('Admin User',     'admin@fitouts.demo',      v_hash, '+971500000002', v_company_name, v_company_id, TRUE),
        ('Quantity Surveyor', 'qs@fitouts.demo',      v_hash, '+971500000003', v_company_name, v_company_id, TRUE),
        ('Senior QS',      'seniorqs@fitouts.demo',   v_hash, '+971500000004', v_company_name, v_company_id, TRUE),
        ('Project Manager','pm@fitouts.demo',         v_hash, '+971500000005', v_company_name, v_company_id, TRUE),
        ('Project Director','director@fitouts.demo',  v_hash, '+971500000006', v_company_name, v_company_id, TRUE),
        ('Demo Client',    'client@fitouts.demo',     v_hash, '+971500000007', v_company_name, v_company_id, TRUE),
        ('Designer',       'designer@fitouts.demo',   v_hash, '+971500000008', v_company_name, v_company_id, TRUE),
        ('QAS Inspector',  'qas@fitouts.demo',        v_hash, '+971500000009', v_company_name, v_company_id, TRUE),
        ('Finance User',   'finance@fitouts.demo',    v_hash, '+971500000010', v_company_name, v_company_id, TRUE),
        ('Sales User',     'sales@fitouts.demo',      v_hash, '+971500000011', v_company_name, v_company_id, TRUE),
        ('Employee',       'employee@fitouts.demo',   v_hash, '+971500000012', v_company_name, v_company_id, TRUE),
        ('Subcontractor',  'subcontractor@fitouts.demo', v_hash, '+971500000013', v_company_name, v_company_id, TRUE)
    ON CONFLICT (email) DO UPDATE SET
        full_name = EXCLUDED.full_name,
        password = EXCLUDED.password,
        phone = EXCLUDED.phone,
        company_name = EXCLUDED.company_name,
        company_id = EXCLUDED.company_id,
        is_active = TRUE;

    -- Assign roles (replace roles for seeded demo accounts)
    DELETE FROM account_roles
    WHERE account_id IN (
        SELECT id FROM accounts
        WHERE email IN (
            'superadmin@fitouts.demo', 'admin@fitouts.demo', 'qs@fitouts.demo',
            'seniorqs@fitouts.demo', 'pm@fitouts.demo', 'director@fitouts.demo',
            'client@fitouts.demo', 'designer@fitouts.demo', 'qas@fitouts.demo',
            'finance@fitouts.demo', 'sales@fitouts.demo', 'employee@fitouts.demo',
            'subcontractor@fitouts.demo'
        )
    );

    INSERT INTO account_roles (account_id, role)
    SELECT a.id, r.role
    FROM accounts a
    JOIN (VALUES
        ('superadmin@fitouts.demo', 'SUPER_ADMIN'),
        ('admin@fitouts.demo', 'ADMIN'),
        ('qs@fitouts.demo', 'QS'),
        ('seniorqs@fitouts.demo', 'SENIOR_QS'),
        ('pm@fitouts.demo', 'PROJECT_MANAGER'),
        ('director@fitouts.demo', 'BUSINESS_OWNER'),
        ('client@fitouts.demo', 'CLIENT'),
        ('designer@fitouts.demo', 'DESIGNER'),
        ('qas@fitouts.demo', 'QAS'),
        ('finance@fitouts.demo', 'FINANCE'),
        ('sales@fitouts.demo', 'SALES'),
        ('employee@fitouts.demo', 'EMPLOYEE'),
        ('subcontractor@fitouts.demo', 'SUBCONTRACTOR')
    ) AS r(email, role) ON r.email = a.email;

    -- Link client to a demo project
    SELECT id INTO v_client_id FROM accounts WHERE email = 'client@fitouts.demo';

    SELECT id INTO v_project_id
    FROM projects
    WHERE company_id = v_company_id
      AND is_deleted = FALSE
      AND name = 'Demo Villa Fit-Out'
    LIMIT 1;

    IF v_project_id IS NULL THEN
        INSERT INTO projects (name, company_id, client_id, is_active, is_deleted, created_at, updated_at)
        VALUES ('Demo Villa Fit-Out', v_company_id, v_client_id, TRUE, FALSE, NOW(), NOW())
        RETURNING id INTO v_project_id;
    ELSE
        UPDATE projects
        SET client_id = v_client_id, updated_at = NOW()
        WHERE id = v_project_id;
    END IF;

    RAISE NOTICE 'V15 demo users seeded for company % (project id %)', v_company_name, v_project_id;
END $$;
