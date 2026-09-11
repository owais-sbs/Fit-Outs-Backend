-- Permit trigger mechanism, property/project classifiers, and company-level registrations.
-- Does not migrate projects.project_type (sales/portfolio label; mixed values).
-- Numbered V71 because V70 is payment_request_approval_events.

ALTER TABLE approval_permit_types
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(40) NOT NULL DEFAULT 'SCOPE_TAG';

ALTER TABLE approval_authorities
    ADD COLUMN IF NOT EXISTS requires_company_registration BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE approval_permit_types SET trigger_type = 'COMPANY'
WHERE permit_code = 'P-COMM-REG' AND deleted = FALSE;

UPDATE approval_permit_types SET trigger_type = 'LOCATION'
WHERE permit_code IN ('P-COMM-NOC', 'P-ACCESS', 'P-WASTE') AND deleted = FALSE;

UPDATE approval_permit_types SET trigger_type = 'PROPERTY_PROJECT'
WHERE permit_code IN ('P-BLDG-NOC', 'P-FITOUT', 'P-GREEN', 'P-DEWA-TEMP', 'P-LIFT') AND deleted = FALSE;

UPDATE approval_permit_types SET trigger_type = 'PREREQUISITE'
WHERE permit_code IN ('P-DCD-FINAL', 'P-RECONNECT', 'P-COMPLETE', 'P-DEPOSIT') AND deleted = FALSE;

UPDATE approval_authorities SET requires_company_registration = TRUE
WHERE deleted = FALSE
  AND (
      type ILIKE 'Master Developer'
      OR code IN ('DDA', 'TRK', 'DMCC', 'JAFZA')
  );

CREATE TABLE approval_property_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, code)
);

CREATE INDEX idx_approval_property_types_company ON approval_property_types (company_id) WHERE deleted = FALSE;

CREATE TABLE approval_project_natures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company_id, code)
);

CREATE INDEX idx_approval_project_natures_company ON approval_project_natures (company_id) WHERE deleted = FALSE;

CREATE TABLE approval_permit_property_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types(id) ON DELETE CASCADE,
    property_type_id UUID NOT NULL REFERENCES approval_property_types(id) ON DELETE CASCADE,
    created_by BIGINT,
    created_by_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (permit_type_id, property_type_id)
);

CREATE INDEX idx_approval_permit_property_types_company ON approval_permit_property_types (company_id);
CREATE INDEX idx_approval_permit_property_types_permit ON approval_permit_property_types (permit_type_id);

CREATE TABLE approval_permit_project_natures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types(id) ON DELETE CASCADE,
    project_nature_id UUID NOT NULL REFERENCES approval_project_natures(id) ON DELETE CASCADE,
    created_by BIGINT,
    created_by_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (permit_type_id, project_nature_id)
);

CREATE INDEX idx_approval_permit_project_natures_company ON approval_permit_project_natures (company_id);
CREATE INDEX idx_approval_permit_project_natures_permit ON approval_permit_project_natures (permit_type_id);

CREATE TABLE approval_company_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    authority_id UUID NOT NULL REFERENCES approval_authorities(id),
    reference_no VARCHAR(120),
    registration_date DATE,
    renewal_date DATE,
    status VARCHAR(40) NOT NULL DEFAULT 'Active',
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_company_registrations_company
    ON approval_company_registrations (company_id) WHERE deleted = FALSE;

CREATE UNIQUE INDEX uq_approval_company_registrations_authority
    ON approval_company_registrations (company_id, authority_id) WHERE deleted = FALSE;

INSERT INTO approval_property_types (company_id, code, name, description)
SELECT c.uuid, v.code, v.name, v.description
FROM companies c
CROSS JOIN (VALUES
    ('VILLA', 'Villa', 'Detached or townhouse villa, not a tower unit.'),
    ('APARTMENT', 'Apartment / tower unit', 'Unit inside a managed tower or apartment building.'),
    ('COMMERCIAL_SHELL', 'Commercial / retail shell', 'Shop, restaurant or retail unit inside a shell.'),
    ('OFFICE', 'Office', 'Office floor or suite, including fitted office space.')
) AS v(code, name, description)
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO approval_project_natures (company_id, code, name, description)
SELECT c.uuid, v.code, v.name, v.description
FROM companies c
CROSS JOIN (VALUES
    ('NEW_BUILD', 'New build', 'Construction of a new building or villa.'),
    ('MAJOR_REFURB', 'Major refurbishment', 'Substantial strip-out and rebuild of an existing property.'),
    ('RENOVATION', 'Renovation', 'Alteration of layout, structure or services in an existing property.'),
    ('FITOUT', 'Minor works / fit-out', 'Fit-out or finishes inside an existing shell or unit.')
) AS v(code, name, description)
ON CONFLICT (company_id, code) DO NOTHING;

INSERT INTO approval_permit_property_types (company_id, permit_type_id, property_type_id, created_by_name)
SELECT p.company_id, p.id, t.id, 'seed'
FROM approval_permit_types p
JOIN (VALUES
    ('P-BLDG-NOC', 'APARTMENT'),
    ('P-FITOUT', 'COMMERCIAL_SHELL'),
    ('P-FITOUT', 'OFFICE'),
    ('P-LIFT', 'APARTMENT')
) AS v(permit_code, item_code)
  ON p.permit_code = v.permit_code AND p.deleted = FALSE
JOIN approval_property_types t
  ON t.company_id = p.company_id AND t.deleted = FALSE AND t.code = v.item_code
ON CONFLICT (permit_type_id, property_type_id) DO NOTHING;

INSERT INTO approval_permit_project_natures (company_id, permit_type_id, project_nature_id, created_by_name)
SELECT p.company_id, p.id, t.id, 'seed'
FROM approval_permit_types p
JOIN (VALUES
    ('P-GREEN', 'NEW_BUILD'),
    ('P-GREEN', 'MAJOR_REFURB'),
    ('P-DEWA-TEMP', 'NEW_BUILD')
) AS v(permit_code, item_code)
  ON p.permit_code = v.permit_code AND p.deleted = FALSE
JOIN approval_project_natures t
  ON t.company_id = p.company_id AND t.deleted = FALSE AND t.code = v.item_code
ON CONFLICT (permit_type_id, project_nature_id) DO NOTHING;
