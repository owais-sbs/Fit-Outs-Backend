-- Restore platform SUPER_ADMIN email to superadmin@fitouts.demo if V627 renamed it to "superadmin".

DO $$
DECLARE
    v_hash TEXT := '$2a$10$vZtJ98U3hn1/H4gFeavg4OczgDfpxjGc8d1yGdO0QOBeEZnyQnVTq';
    v_account_id BIGINT;
BEGIN
    -- If bare "superadmin" exists and demo email does not, rename it back
    IF EXISTS (SELECT 1 FROM accounts WHERE LOWER(email) = LOWER('superadmin'))
       AND NOT EXISTS (SELECT 1 FROM accounts WHERE LOWER(email) = LOWER('superadmin@fitouts.demo')) THEN
        UPDATE accounts
        SET email = 'superadmin@fitouts.demo',
            full_name = 'Super Admin',
            password = v_hash,
            company_id = NULL,
            company_name = 'Platform',
            is_active = TRUE
        WHERE LOWER(email) = LOWER('superadmin');
    END IF;

    -- Ensure platform account is detached from any tenant company
    UPDATE accounts
    SET password = v_hash,
        company_id = NULL,
        company_name = 'Platform',
        is_active = TRUE,
        full_name = 'Super Admin'
    WHERE LOWER(email) = LOWER('superadmin@fitouts.demo');

    -- Drop orphan bare username if both somehow exist
    DELETE FROM account_roles
    WHERE account_id IN (SELECT id FROM accounts WHERE LOWER(email) = LOWER('superadmin'));
    DELETE FROM accounts WHERE LOWER(email) = LOWER('superadmin');

    SELECT id INTO v_account_id
    FROM accounts
    WHERE LOWER(email) = LOWER('superadmin@fitouts.demo');

    IF v_account_id IS NOT NULL THEN
        DELETE FROM account_roles WHERE account_id = v_account_id;
        INSERT INTO account_roles (account_id, role) VALUES (v_account_id, 'SUPER_ADMIN');
    END IF;
END $$;
