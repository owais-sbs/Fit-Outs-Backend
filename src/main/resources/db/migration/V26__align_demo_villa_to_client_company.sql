-- After V21 moved demo accounts to the site-visit company, Demo Villa Fit-Out
-- may still sit on the original seed company. Align project tenant + client_id
-- so client@fitouts.demo can list and open it.

DO $$
DECLARE
    v_client_id BIGINT;
    v_company_id UUID;
BEGIN
    SELECT id, company_id
    INTO v_client_id, v_company_id
    FROM accounts
    WHERE email = 'client@fitouts.demo'
    LIMIT 1;

    IF v_client_id IS NULL OR v_company_id IS NULL THEN
        RAISE NOTICE 'V26 skipped: demo client account not found';
        RETURN;
    END IF;

    UPDATE projects
    SET company_id = v_company_id,
        client_id = v_client_id,
        updated_at = NOW()
    WHERE name = 'Demo Villa Fit-Out'
      AND (company_id IS DISTINCT FROM v_company_id
           OR client_id IS DISTINCT FROM v_client_id);

    RAISE NOTICE 'V26 Demo Villa aligned to client % company %', v_client_id, v_company_id;
END $$;
