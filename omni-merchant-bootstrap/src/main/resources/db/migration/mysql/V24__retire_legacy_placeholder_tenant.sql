-- V1 historically created a data-less development tenant in every profile.
-- Keep the row for referential safety, but hide and disable it so platform admins
-- land on an explicitly provisioned tenant with real scoped data.
UPDATE tenant
SET status = 0,
    status_reason = 'Legacy bootstrap placeholder retired by V24',
    is_deleted = 1,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE tenant_code = 'OM-DEMO001'
  AND external_store_id = 'omnidemo.myshopify.com';
