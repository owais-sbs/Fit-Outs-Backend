-- Hard-delete a company tenant and dependent rows (FK-safe order for common tables).
CREATE OR REPLACE FUNCTION purge_company_tenant(p_company_uuid UUID)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    v_project_id BIGINT;
BEGIN
    DELETE FROM subscription_payments WHERE company_id = p_company_uuid;
    DELETE FROM auth_session_records WHERE company_id = p_company_uuid;

    FOR v_project_id IN SELECT id FROM projects WHERE company_id = p_company_uuid
    LOOP
        DELETE FROM schedule_activity_boq_lines WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM schedule_duration_extensions WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM activity_resource_assignments WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM activity_material_issues WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM schedule_dependencies WHERE company_id = p_company_uuid
            AND (predecessor_uuid IN (SELECT uuid FROM schedule_activities WHERE project_id = v_project_id)
              OR successor_uuid IN (SELECT uuid FROM schedule_activities WHERE project_id = v_project_id));
        DELETE FROM schedule_activities WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM project_team_assignments WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM project_documents WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM snags WHERE project_id = v_project_id AND company_id = p_company_uuid;
        DELETE FROM site_visits WHERE project_id = v_project_id AND company_id = p_company_uuid;
    END LOOP;

    DELETE FROM projects WHERE company_id = p_company_uuid;
    DELETE FROM leads WHERE company_id = p_company_uuid;
    DELETE FROM site_visits WHERE company_id = p_company_uuid;

    DELETE FROM account_roles
    WHERE account_id IN (SELECT id FROM accounts WHERE company_id = p_company_uuid);
    DELETE FROM accounts WHERE company_id = p_company_uuid;

    DELETE FROM companies WHERE uuid = p_company_uuid;
END;
$$;
