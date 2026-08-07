-- Align demo portal users with the company that owns existing site-visit / CRM data.
-- Demo accounts were seeded onto the first company by created_at, which left them
-- on a different tenant than historical leads/visits (empty Upcoming Visits).

DO $$
DECLARE
    v_company_id UUID;
    v_company_name TEXT;
BEGIN
    SELECT sv.company_id, c.company_name
    INTO v_company_id, v_company_name
    FROM site_visits sv
    JOIN companies c ON c.uuid = sv.company_id
    WHERE sv.company_id IS NOT NULL
    GROUP BY sv.company_id, c.company_name
    ORDER BY COUNT(*) DESC, c.company_name
    LIMIT 1;

    IF v_company_id IS NULL THEN
        SELECT c.uuid, c.company_name
        INTO v_company_id, v_company_name
        FROM companies c
        ORDER BY c.created_at NULLS LAST, c.company_name
        LIMIT 1;
    END IF;

    IF v_company_id IS NULL THEN
        RAISE NOTICE 'V21 skipped: no company found';
        RETURN;
    END IF;

    UPDATE accounts
    SET company_id = v_company_id,
        company_name = v_company_name
    WHERE email LIKE '%@fitouts.demo'
      AND (company_id IS DISTINCT FROM v_company_id
           OR company_name IS DISTINCT FROM v_company_name);

    UPDATE employees e
    SET company_id = v_company_id
    FROM accounts a
    WHERE e.account_id = a.id
      AND a.email LIKE '%@fitouts.demo'
      AND e.company_id IS DISTINCT FROM v_company_id;

    -- Orphan visits (no tenant) join the demo operational company
    UPDATE site_visits
    SET company_id = v_company_id
    WHERE company_id IS NULL;

    RAISE NOTICE 'V21 demo users aligned to company % (%)', v_company_name, v_company_id;
END $$;
