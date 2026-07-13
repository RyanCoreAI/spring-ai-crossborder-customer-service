-- OmniMerchant SLA policies.
-- Run after sql/db_helpdesk.sql and before sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `sla_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `policy_name` VARCHAR(128) NOT NULL,
  `priority` TINYINT NOT NULL DEFAULT 2,
  `channel` VARCHAR(32) NOT NULL DEFAULT 'ALL',
  `first_response_minutes` INT NOT NULL DEFAULT 5,
  `resolution_minutes` INT NOT NULL DEFAULT 240,
  `business_hours` VARCHAR(128) NOT NULL DEFAULT 'MON-FRI 09:00-18:00',
  `timezone` VARCHAR(64) NOT NULL DEFAULT 'UTC',
  `holiday_calendar` JSON DEFAULT NULL,
  `escalation_rule` VARCHAR(512) DEFAULT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sla_policy` (`tenant_id`, `policy_name`, `priority`, `channel`),
  KEY `idx_sla_active` (`tenant_id`, `active`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant configurable support SLA policies';
