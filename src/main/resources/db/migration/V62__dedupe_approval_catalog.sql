-- Collapse catalog rows created by repeated first-load seeds.
-- Keep the earliest live row per company+code, remap FKs, then delete extras.

CREATE TEMP TABLE authority_keep AS
SELECT company_id, code, (ARRAY_AGG(id ORDER BY deleted, created_at, id))[1] AS keep_id
FROM approval_authorities
GROUP BY company_id, code;

CREATE TEMP TABLE authority_dupe AS
SELECT a.id AS dupe_id, k.keep_id
FROM approval_authorities a
JOIN authority_keep k ON k.company_id = a.company_id AND k.code = a.code
WHERE a.id <> k.keep_id;

UPDATE jurisdiction_packs p
SET community_authority_id = d.keep_id
FROM authority_dupe d
WHERE p.community_authority_id = d.dupe_id;

UPDATE jurisdiction_packs p
SET primary_authority_id = d.keep_id
FROM authority_dupe d
WHERE p.primary_authority_id = d.dupe_id;

UPDATE jurisdiction_pack_permits p
SET issuing_authority_id = d.keep_id
FROM authority_dupe d
WHERE p.issuing_authority_id = d.dupe_id;

UPDATE project_permit_cases c
SET issuing_authority_id = d.keep_id
FROM authority_dupe d
WHERE c.issuing_authority_id = d.dupe_id;

DELETE FROM approval_authorities WHERE id IN (SELECT dupe_id FROM authority_dupe);

CREATE TEMP TABLE permit_keep AS
SELECT company_id, permit_code, (ARRAY_AGG(id ORDER BY deleted, created_at, id))[1] AS keep_id
FROM approval_permit_types
GROUP BY company_id, permit_code;

CREATE TEMP TABLE permit_dupe AS
SELECT p.id AS dupe_id, k.keep_id
FROM approval_permit_types p
JOIN permit_keep k ON k.company_id = p.company_id AND k.permit_code = p.permit_code
WHERE p.id <> k.keep_id;

UPDATE jurisdiction_pack_permits p
SET permit_type_id = d.keep_id
FROM permit_dupe d
WHERE p.permit_type_id = d.dupe_id;

UPDATE project_permit_cases c
SET permit_type_id = d.keep_id
FROM permit_dupe d
WHERE c.permit_type_id = d.dupe_id;

DELETE FROM approval_permit_types WHERE id IN (SELECT dupe_id FROM permit_dupe);

CREATE TEMP TABLE document_keep AS
SELECT company_id, doc_code, (ARRAY_AGG(id ORDER BY deleted, created_at, id))[1] AS keep_id
FROM approval_document_types
GROUP BY company_id, doc_code;

CREATE TEMP TABLE document_dupe AS
SELECT t.id AS dupe_id, k.keep_id
FROM approval_document_types t
JOIN document_keep k ON k.company_id = t.company_id AND k.doc_code = t.doc_code
WHERE t.id <> k.keep_id;

UPDATE jurisdiction_pack_documents p
SET document_type_id = d.keep_id
FROM document_dupe d
WHERE p.document_type_id = d.dupe_id;

UPDATE project_permit_documents p
SET document_type_id = d.keep_id
FROM document_dupe d
WHERE p.document_type_id = d.dupe_id;

DELETE FROM approval_document_types WHERE id IN (SELECT dupe_id FROM document_dupe);

CREATE TEMP TABLE pack_keep AS
SELECT company_id, code, (ARRAY_AGG(id ORDER BY deleted, created_at, id))[1] AS keep_id
FROM jurisdiction_packs
GROUP BY company_id, code;

CREATE TEMP TABLE pack_dupe AS
SELECT p.id AS dupe_id, k.keep_id
FROM jurisdiction_packs p
JOIN pack_keep k ON k.company_id = p.company_id AND k.code = p.code
WHERE p.id <> k.keep_id;

UPDATE projects p
SET jurisdiction_pack_id = d.keep_id
FROM pack_dupe d
WHERE p.jurisdiction_pack_id = d.dupe_id;

DELETE FROM jurisdiction_pack_documents
WHERE pack_permit_id IN (
    SELECT pp.id FROM jurisdiction_pack_permits pp
    JOIN pack_dupe d ON d.dupe_id = pp.pack_id
);

DELETE FROM jurisdiction_pack_permits
WHERE pack_id IN (SELECT dupe_id FROM pack_dupe);

DELETE FROM jurisdiction_packs
WHERE id IN (SELECT dupe_id FROM pack_dupe);

CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_authorities_company_code
    ON approval_authorities (company_id, code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_permit_types_company_code
    ON approval_permit_types (company_id, permit_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_approval_document_types_company_code
    ON approval_document_types (company_id, doc_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_jurisdiction_packs_company_code
    ON jurisdiction_packs (company_id, code);
