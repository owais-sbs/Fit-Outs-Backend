-- Marketing feature bullets for subscription plans (landing-page copy)
CREATE TABLE IF NOT EXISTS subscription_plan_features (
    plan_uuid UUID NOT NULL REFERENCES subscription_plans (uuid) ON DELETE CASCADE,
    feature_text VARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (plan_uuid, display_order)
);

-- Manual SaaS subscription payment log
CREATE TABLE IF NOT EXISTS subscription_payments (
    uuid UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (uuid),
    plan_id UUID NOT NULL REFERENCES subscription_plans (uuid),
    amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at TIMESTAMPTZ,
    decided_by_account_id BIGINT REFERENCES accounts (id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_company
    ON subscription_payments (company_id);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_status
    ON subscription_payments (status);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_created
    ON subscription_payments (created_at DESC);

-- Allow creating a company before a plan is assigned via payment
ALTER TABLE companies
    ALTER COLUMN subscription_plan_id DROP NOT NULL;

-- Existing tenants remain usable under the ACTIVE-only login gate
UPDATE companies
SET status = 'ACTIVE'
WHERE status IS NULL OR status IN ('TRIAL', 'ACTIVE');
