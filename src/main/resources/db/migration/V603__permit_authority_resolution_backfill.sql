-- Renumbered from V81 after merging main (main owns V73–V80 for subcontractor portal).
-- V75 was recorded applied on shared RDS while Hibernate ddl-auto skipped
-- NOT NULL columns on the already-populated permit table (same class of gap as V73).
-- Idempotent: add anything still missing, then backfill catalogue rows.

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS authority_resolution_mechanism VARCHAR(40);

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS fixed_authority_id UUID REFERENCES approval_authorities (id);

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS inherit_authority_from_permit_code VARCHAR(40);

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS resolution_mode VARCHAR(40);

UPDATE approval_permit_types SET resolution_mode = 'ANY_ONE_APPLIES' WHERE resolution_mode IS NULL;

ALTER TABLE approval_permit_types
    ALTER COLUMN resolution_mode SET DEFAULT 'ANY_ONE_APPLIES';
ALTER TABLE approval_permit_types
    ALTER COLUMN resolution_mode SET NOT NULL;

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS resolution_mode_confirmed_by VARCHAR(120);

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS resolution_mode_confirmed_at TIMESTAMP;

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS allow_internal_hse_signoff BOOLEAN;

UPDATE approval_permit_types SET allow_internal_hse_signoff = FALSE
WHERE allow_internal_hse_signoff IS NULL;

ALTER TABLE approval_permit_types
    ALTER COLUMN allow_internal_hse_signoff SET DEFAULT FALSE;
ALTER TABLE approval_permit_types
    ALTER COLUMN allow_internal_hse_signoff SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_approval_permit_types_fixed_authority
    ON approval_permit_types (fixed_authority_id);

CREATE TABLE IF NOT EXISTS approval_permit_authority_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies (uuid),
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types (id) ON DELETE CASCADE,
    authority_role VARCHAR(40) NOT NULL,
    created_by BIGINT,
    created_by_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (permit_type_id, authority_role)
);

CREATE INDEX IF NOT EXISTS idx_approval_permit_authority_roles_company
    ON approval_permit_authority_roles (company_id);
CREATE INDEX IF NOT EXISTS idx_approval_permit_authority_roles_permit
    ON approval_permit_authority_roles (permit_type_id);

ALTER TABLE approval_case
    ADD COLUMN IF NOT EXISTS candidate_authority_codes TEXT;

DO $$
BEGIN
    BEGIN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_case_project_permit_authority_live
            ON approval_case (project_id, company_id, permit_type_code, COALESCE(authority_code, ''))
            WHERE status NOT IN ('WITHDRAWN', 'REJECTED', 'CLOSED');
    EXCEPTION
        WHEN unique_violation THEN
            RAISE NOTICE 'Skipped live case unique index; duplicate live permit+authority rows exist';
    END;
END $$;

UPDATE approval_permit_types p
SET authority_resolution_mechanism = 'FIXED',
    fixed_authority_id = a.id
FROM approval_authorities a
WHERE p.deleted = FALSE
  AND p.authority_resolution_mechanism IS NULL
  AND a.company_id = p.company_id
  AND a.deleted = FALSE
  AND (
      (p.permit_code IN ('P-DCD-NOC', 'P-DCD-FINAL') AND UPPER(a.code) = 'DCD')
      OR (p.permit_code = 'P-SIRA' AND UPPER(a.code) = 'SIRA')
      OR (p.permit_code = 'P-RTA' AND UPPER(a.code) = 'RTA')
      OR (p.permit_code = 'P-KITCHEN' AND UPPER(a.code) = 'DMFS')
      OR (p.permit_code = 'P-SIGN' AND UPPER(a.code) = 'DET')
  );

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'JURISDICTION_MASTER_DEVELOPER'
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code IN ('P-COMM-REG', 'P-COMM-NOC');

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'JURISDICTION_BUILDING_MANAGEMENT'
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code IN ('P-BLDG-NOC', 'P-LIFT');

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'JURISDICTION_REGULATOR'
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code IN ('P-DEMO', 'P-MOD', 'P-FITOUT', 'P-WASTE', 'P-GREEN', 'P-COMPLETE');

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'EMIRATE_UTILITY'
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code IN ('P-DEWA-TEMP', 'P-DISCONNECT', 'P-RECONNECT', 'P-LOAD');

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'INHERIT_FROM_PERMIT',
    inherit_authority_from_permit_code = 'P-COMM-NOC'
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code = 'P-DEPOSIT';

UPDATE approval_permit_types
SET authority_resolution_mechanism = 'MULTI_AUTHORITY',
    resolution_mode = 'ANY_ONE_APPLIES',
    resolution_mode_confirmed_by = NULL,
    resolution_mode_confirmed_at = NULL
WHERE deleted = FALSE
  AND authority_resolution_mechanism IS NULL
  AND permit_code IN ('P-ACCESS', 'P-NIGHT', 'P-HOT');

UPDATE approval_permit_types
SET allow_internal_hse_signoff = TRUE
WHERE deleted = FALSE
  AND permit_code = 'P-HOT';

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_permit_authority_roles_pair
    ON approval_permit_authority_roles (permit_type_id, authority_role);

ALTER TABLE approval_permit_authority_roles
    ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE approval_permit_authority_roles
    ALTER COLUMN created_at SET DEFAULT NOW();

INSERT INTO approval_permit_authority_roles (id, company_id, permit_type_id, authority_role, created_by_name, created_at)
SELECT gen_random_uuid(), p.company_id, p.id, v.role, 'seed', NOW()
FROM approval_permit_types p
JOIN (VALUES
    ('P-ACCESS', 'MASTER_DEVELOPER'),
    ('P-ACCESS', 'BUILDING_MANAGEMENT'),
    ('P-NIGHT', 'MASTER_DEVELOPER'),
    ('P-NIGHT', 'BUILDING_MANAGEMENT'),
    ('P-NIGHT', 'REGULATOR'),
    ('P-HOT', 'BUILDING_MANAGEMENT')
) AS v(permit_code, role)
  ON p.permit_code = v.permit_code AND p.deleted = FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM approval_permit_authority_roles existing
    WHERE existing.permit_type_id = p.id AND existing.authority_role = v.role
);
