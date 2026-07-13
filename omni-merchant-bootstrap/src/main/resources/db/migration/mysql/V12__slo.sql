-- OmniMerchant SLO and runbook policy tables.
-- Run after sql/db_rbac_audit.sql and before sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `slo_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `slo_key` VARCHAR(64) NOT NULL,
  `slo_label` VARCHAR(128) NOT NULL,
  `target_value` DECIMAL(15,4) NOT NULL,
  `unit` VARCHAR(32) NOT NULL,
  `window_minutes` INT NOT NULL DEFAULT 60,
  `severity_on_breach` VARCHAR(16) NOT NULL DEFAULT 'WARN',
  `runbook` VARCHAR(1024) DEFAULT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slo_policy` (`tenant_id`, `slo_key`),
  KEY `idx_slo_active` (`tenant_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant SLO policy and alert/runbook binding';
