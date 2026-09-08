-- Maps permit types to the scope tags that trigger them (OR: any linked tag applies).
-- created_by / created_at record who set each link, because this table is questioned later.

CREATE TABLE approval_permit_scope_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    permit_type_id UUID NOT NULL REFERENCES approval_permit_types(id) ON DELETE CASCADE,
    scope_tag_id UUID NOT NULL REFERENCES approval_scope_tags(id) ON DELETE CASCADE,
    created_by BIGINT,
    created_by_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (permit_type_id, scope_tag_id)
);

CREATE INDEX idx_approval_permit_scope_tags_company ON approval_permit_scope_tags (company_id);
CREATE INDEX idx_approval_permit_scope_tags_permit ON approval_permit_scope_tags (permit_type_id);
CREATE INDEX idx_approval_permit_scope_tags_tag ON approval_permit_scope_tags (scope_tag_id);

-- Seed only scope-tag-driven catalogue triggers. Community, property-type, chain
-- (e.g. P-DCD-FINAL after design NOC) and always-on permits stay unlinked.

INSERT INTO approval_permit_scope_tags (company_id, permit_type_id, scope_tag_id, created_by_name)
SELECT p.company_id, p.id, t.id, 'seed'
FROM approval_permit_types p
JOIN (VALUES
    ('P-DEMO', 'DEMOLITION'),
    ('P-MOD', 'LAYOUT'),
    ('P-MOD', 'STRUCTURAL'),
    ('P-MOD', 'FACADE'),
    ('P-MOD', 'MEP_LOAD'),
    ('P-DCD-NOC', 'FIRE_LIFE'),
    ('P-DISCONNECT', 'DEMOLITION'),
    ('P-DISCONNECT', 'MEP_LOAD'),
    ('P-LOAD', 'MEP_LOAD'),
    ('P-SIRA', 'SECURITY'),
    ('P-RTA', 'HOARDING'),
    ('P-NIGHT', 'NIGHT'),
    ('P-HOT', 'HOT_WORKS'),
    ('P-SIGN', 'SIGNAGE'),
    ('P-KITCHEN', 'KITCHEN')
) AS v(permit_code, tag_code)
  ON p.permit_code = v.permit_code
 AND p.deleted = FALSE
JOIN approval_scope_tags t
  ON t.company_id = p.company_id
 AND t.deleted = FALSE
 AND t.code = v.tag_code
ON CONFLICT (permit_type_id, scope_tag_id) DO NOTHING;
