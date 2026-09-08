-- Idempotent in case V59 applied without the project columns, or Hibernate
-- did not add them before the first GET /api/projects.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS jurisdiction_pack_id UUID;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS approval_scope_kitchen BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS approval_scope_cctv BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS approval_scope_rta BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS approval_scope_demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS approval_scope_load BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'jurisdiction_packs'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'projects_jurisdiction_pack_id_fkey'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT projects_jurisdiction_pack_id_fkey
            FOREIGN KEY (jurisdiction_pack_id) REFERENCES jurisdiction_packs (id);
    END IF;
END $$;
