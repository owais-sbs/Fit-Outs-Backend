-- Self-serve tenant onboarding gate (company name + logo)
ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing tenants already operate in the portal; do not force them through onboarding
UPDATE companies
SET onboarding_completed = TRUE
WHERE status = 'ACTIVE';
