-- Ensure demo SC portal team accounts exist (password 123456).
-- V69 may have been skipped if schema version 69 was already taken by another migration.

DO $$
DECLARE
    v_hash TEXT := '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq';
    v_company_id UUID;
    v_company_name TEXT;
    v_org_uuid UUID;
    v_admin_account_id BIGINT;
    v_account_id BIGINT;
    r RECORD;
BEGIN
    SELECT a.company_id, a.company_name, a.id
    INTO v_company_id, v_company_name, v_admin_account_id
    FROM accounts a
    WHERE a.email = 'subcontractor@fitouts.demo'
    LIMIT 1;

    IF v_admin_account_id IS NULL THEN
        RAISE NOTICE 'V70 skipped: demo SC admin not found';
        RETURN;
    END IF;

    SELECT pu.organization_uuid INTO v_org_uuid
    FROM sc_portal_user pu
    WHERE pu.account_id = v_admin_account_id
    LIMIT 1;

    IF v_org_uuid IS NULL THEN
        SELECT p.organization_uuid INTO v_org_uuid
        FROM sc_company_profile p
        WHERE p.admin_account_id = v_admin_account_id
        LIMIT 1;
    END IF;

    IF v_org_uuid IS NULL THEN
        SELECT tm.organization_uuid INTO v_org_uuid
        FROM sc_tenant_membership tm
        WHERE tm.company_id = v_company_id
        ORDER BY tm.updated_at DESC
        LIMIT 1;
    END IF;

    IF v_org_uuid IS NULL THEN
        v_org_uuid := gen_random_uuid();
        INSERT INTO sc_organization (
            uuid, legal_company_name, is_cross_tenant_visible, created_at, updated_at
        ) VALUES (
            v_org_uuid, COALESCE(v_company_name, 'Demo Subcontractor'), FALSE, now(), now()
        );

        UPDATE sc_company_profile
        SET organization_uuid = v_org_uuid
        WHERE admin_account_id = v_admin_account_id
          AND organization_uuid IS NULL;

        INSERT INTO sc_portal_user (
            uuid, organization_uuid, account_id, portal_role, status, created_at, updated_at
        )
        SELECT gen_random_uuid(), v_org_uuid, v_admin_account_id, 'SC_ADMIN', 'ACTIVE', now(), now()
        WHERE NOT EXISTS (SELECT 1 FROM sc_portal_user WHERE account_id = v_admin_account_id);
    END IF;

    FOR r IN
        SELECT *
        FROM (VALUES
            ('SC Estimator',         'estimator@fitouts.demo',     '+971500000014', 'SC_ESTIMATOR'),
            ('SC Supervisor',        'supervisor@fitouts.demo',    '+971500000015', 'SC_SUPERVISOR'),
            ('SC Quantity Surveyor', 'scqs@fitouts.demo',          '+971500000016', 'SC_QS'),
            ('SC Doc Controller',    'doccontroller@fitouts.demo', '+971500000017', 'SC_DOC_CONTROLLER')
        ) AS t(full_name, email, phone, portal_role)
    LOOP
        INSERT INTO accounts (full_name, email, password, phone, company_name, company_id, is_active)
        VALUES (r.full_name, r.email, v_hash, r.phone, v_company_name, v_company_id, TRUE)
        ON CONFLICT (email) DO UPDATE SET
            full_name = EXCLUDED.full_name,
            password = EXCLUDED.password,
            phone = EXCLUDED.phone,
            company_name = EXCLUDED.company_name,
            company_id = EXCLUDED.company_id,
            is_active = TRUE;

        SELECT id INTO v_account_id FROM accounts WHERE email = r.email;

        DELETE FROM account_roles WHERE account_id = v_account_id;
        INSERT INTO account_roles (account_id, role) VALUES (v_account_id, 'SUBCONTRACTOR');

        IF NOT EXISTS (SELECT 1 FROM sc_portal_user WHERE account_id = v_account_id) THEN
            INSERT INTO sc_portal_user (
                uuid, organization_uuid, account_id, portal_role, status,
                invited_by_account_id, created_at, updated_at
            ) VALUES (
                gen_random_uuid(), v_org_uuid, v_account_id, r.portal_role, 'ACTIVE',
                v_admin_account_id, now(), now()
            );
        ELSE
            UPDATE sc_portal_user
            SET organization_uuid = v_org_uuid,
                portal_role = r.portal_role,
                status = 'ACTIVE',
                updated_at = now()
            WHERE account_id = v_account_id;
        END IF;
    END LOOP;

    RAISE NOTICE 'V70 demo SC portal team seeded for organization %', v_org_uuid;
END $$;
