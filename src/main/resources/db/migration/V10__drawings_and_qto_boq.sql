CREATE TABLE IF NOT EXISTS project_drawings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    category VARCHAR(30) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    original_path VARCHAR(1000) NOT NULL,
    preview_pdf_path VARCHAR(1000),
    mime_type VARCHAR(100),
    file_size BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE IF NOT EXISTS qto_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    drawing_id UUID REFERENCES project_drawings(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    scale_ratio NUMERIC(14, 8),
    scale_unit VARCHAR(10) DEFAULT 'M',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE TABLE IF NOT EXISTS qto_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES qto_sessions(id) ON DELETE CASCADE,
    line_type VARCHAR(30) NOT NULL,
    label VARCHAR(300),
    quantity NUMERIC(14, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL DEFAULT 'SQM',
    work_item_id UUID REFERENCES work_items(id),
    rate NUMERIC(12, 2),
    amount NUMERIC(14, 2),
    geometry_json TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    editable BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS boq_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    company_id UUID NOT NULL REFERENCES companies(uuid),
    qto_session_id UUID REFERENCES qto_sessions(id),
    version VARCHAR(20) NOT NULL DEFAULT '1.0',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal NUMERIC(14, 2) DEFAULT 0,
    vat_amount NUMERIC(14, 2) DEFAULT 0,
    grand_total NUMERIC(14, 2) DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID
);

CREATE TABLE IF NOT EXISTS boq_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    boq_id UUID NOT NULL REFERENCES boq_documents(id) ON DELETE CASCADE,
    category_code VARCHAR(20),
    category_name VARCHAR(200),
    description TEXT,
    unit VARCHAR(20),
    quantity NUMERIC(14, 4) NOT NULL DEFAULT 0,
    rate NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    qto_line_id UUID REFERENCES qto_lines(id),
    floor_label VARCHAR(100),
    room_label VARCHAR(200),
    sort_order INT NOT NULL DEFAULT 0,
    source VARCHAR(30) DEFAULT 'QTO',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_project_drawings_project ON project_drawings(project_id);
CREATE INDEX IF NOT EXISTS idx_project_drawings_company ON project_drawings(company_id);
CREATE INDEX IF NOT EXISTS idx_qto_sessions_project ON qto_sessions(project_id);
CREATE INDEX IF NOT EXISTS idx_qto_lines_session ON qto_lines(session_id);
CREATE INDEX IF NOT EXISTS idx_boq_documents_project ON boq_documents(project_id);
CREATE INDEX IF NOT EXISTS idx_boq_lines_boq ON boq_lines(boq_id);
