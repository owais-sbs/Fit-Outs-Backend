ALTER TABLE variation_request
    ADD COLUMN IF NOT EXISTS source_boq_id UUID REFERENCES boq_documents(id),
    ADD COLUMN IF NOT EXISTS result_boq_id UUID REFERENCES boq_documents(id);

CREATE TABLE IF NOT EXISTS variation_boq_change (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variation_uuid UUID NOT NULL REFERENCES variation_request(uuid) ON DELETE CASCADE,
    variation_line_uuid UUID REFERENCES variation_line(uuid) ON DELETE SET NULL,
    source_boq_uuid UUID NOT NULL REFERENCES boq_documents(id),
    resulting_boq_uuid UUID NOT NULL REFERENCES boq_documents(id),
    source_boq_line_id UUID REFERENCES boq_lines(id),
    resulting_boq_line_id UUID REFERENCES boq_lines(id),
    change_type VARCHAR(20) NOT NULL,
    description TEXT,
    unit VARCHAR(20),
    previous_quantity NUMERIC(14,4),
    new_quantity NUMERIC(14,4),
    previous_rate NUMERIC(12,2),
    new_rate NUMERIC(12,2),
    previous_amount NUMERIC(14,2),
    new_amount NUMERIC(14,2),
    delta_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    applied_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_vbc_change_type CHECK (change_type IN ('NEW', 'MODIFY', 'LUMP_SUM'))
);

CREATE INDEX IF NOT EXISTS idx_vbc_variation
    ON variation_boq_change(variation_uuid, created_at);
CREATE INDEX IF NOT EXISTS idx_vbc_resulting_boq
    ON variation_boq_change(resulting_boq_uuid);
