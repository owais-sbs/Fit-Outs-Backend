-- Platform SUPER_ADMIN login: email/username "superadmin", password "123456"
-- Idempotent: rename existing demo superadmin if present, else insert.
-- NOTE: V628 restores email to superadmin@fitouts.demo for the login form.

DO $$
DECLARE
    v_hash TEXT := '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq';
    v_account_id BIGINT;
BEGIN
    -- Prefer renaming the existing demo superadmin so we do not create a duplicate
    UPDATE accounts
    SET email = 'superadmin',
        full_name = 'Super Admin',
        password = v_hash,
        company_id = NULL,
        company_name = 'Platform',
        is_active = TRUE
    WHERE LOWER(email) = LOWER('superadmin@fitouts.demo');

    IF NOT FOUND THEN
        INSERT INTO accounts (full_name, email, password, phone, company_name, company_id, is_active)
        VALUES ('Super Admin', 'superadmin', v_hash, NULL, 'Platform', NULL, TRUE)
        ON CONFLICT (email) DO UPDATE SET
            full_name = EXCLUDED.full_name,
            password = EXCLUDED.password,
            company_id = NULL,
            company_name = 'Platform',
            is_active = TRUE;
    END IF;

    -- Also handle case where "superadmin" already exists from a prior run
    UPDATE accounts
    SET password = v_hash,
        company_id = NULL,
        company_name = 'Platform',
        is_active = TRUE,
        full_name = 'Super Admin'
    WHERE LOWER(email) = LOWER('superadmin');

    SELECT id INTO v_account_id FROM accounts WHERE LOWER(email) = LOWER('superadmin');

    IF v_account_id IS NOT NULL THEN
        DELETE FROM account_roles WHERE account_id = v_account_id;
        INSERT INTO account_roles (account_id, role) VALUES (v_account_id, 'SUPER_ADMIN');
    END IF;
END $$;
