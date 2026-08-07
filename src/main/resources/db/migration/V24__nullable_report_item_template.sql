-- Room-scope checklist reports store question/section/room on the item itself
-- and no longer require a checklist_template_items FK.

ALTER TABLE site_visit_report_items
    ALTER COLUMN template_item_uuid DROP NOT NULL;

DO $$
DECLARE
    fk_name text;
BEGIN
    SELECT tc.constraint_name
    INTO fk_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'site_visit_report_items'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'template_item_uuid'
    LIMIT 1;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE site_visit_report_items DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;
