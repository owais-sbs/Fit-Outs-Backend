-- Milestone billing: PM then Director approval before client notification

ALTER TABLE billing_milestone DROP CONSTRAINT IF EXISTS chk_billing_milestone_status;
ALTER TABLE billing_milestone ADD CONSTRAINT chk_billing_milestone_status
    CHECK (status IN ('DRAFT', 'PENDING_PM', 'PENDING_DIRECTOR', 'ISSUED', 'PAID', 'PART_PAID'));

ALTER TABLE payment_request DROP CONSTRAINT IF EXISTS chk_payment_request_status;
ALTER TABLE payment_request ADD CONSTRAINT chk_payment_request_status
    CHECK (status IN ('DRAFT', 'PENDING_PM', 'PENDING_DIRECTOR', 'ISSUED', 'PAID', 'PART_PAID'));
