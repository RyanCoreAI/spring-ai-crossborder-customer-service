ALTER TABLE `agent_eval_case`
  ADD COLUMN `annotation_note` VARCHAR(512) DEFAULT NULL AFTER `annotated_at`;

INSERT IGNORE INTO `role_permission` (`role_key`, `permission_key`) VALUES
  ('TENANT_ADMIN', 'eval:gold:manage'),
  ('SUPPORT_SUPERVISOR', 'eval:gold:manage');
