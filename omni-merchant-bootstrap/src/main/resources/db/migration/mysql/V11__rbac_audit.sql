-- OmniMerchant tenant-scoped audit events for support operations and approval gates.
-- Run after sql/db_main.sql and sql/db_shopify_v2.sql.

CREATE TABLE IF NOT EXISTS `audit_event` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `actor_id` BIGINT DEFAULT NULL,
  `actor_role` VARCHAR(64) DEFAULT NULL,
  `action` VARCHAR(96) NOT NULL,
  `resource_type` VARCHAR(64) NOT NULL,
  `resource_id` VARCHAR(128) DEFAULT NULL,
  `summary` VARCHAR(1024) DEFAULT NULL,
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
  `metadata_json` TEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_tenant_created` (`tenant_id`, `created_at`),
  KEY `idx_audit_resource` (`tenant_id`, `resource_type`, `resource_id`),
  KEY `idx_audit_action` (`tenant_id`, `action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户级客服与审批审计日志';

CREATE TABLE IF NOT EXISTS `support_role_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `role_key` VARCHAR(64) NOT NULL,
  `role_label` VARCHAR(128) NOT NULL,
  `permissions_json` JSON NOT NULL,
  `tool_policy_json` JSON DEFAULT NULL,
  `approval_limit` DECIMAL(15,4) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_support_role` (`tenant_id`, `role_key`),
  KEY `idx_support_role_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户客服角色、页面权限、工具权限和审批额度策略';

CREATE TABLE IF NOT EXISTS `data_retention_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `data_set` VARCHAR(128) NOT NULL,
  `retention_days` INT NOT NULL,
  `masking_default` VARCHAR(128) NOT NULL DEFAULT 'REDACTED_SUMMARY',
  `export_support` VARCHAR(32) NOT NULL DEFAULT 'ROADMAP',
  `deletion_support` VARCHAR(32) NOT NULL DEFAULT 'ROADMAP',
  `status` VARCHAR(32) NOT NULL DEFAULT 'POLICY_DECLARED',
  `notes` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_retention_policy` (`tenant_id`, `data_set`),
  KEY `idx_retention_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户数据保留、脱敏、导出和删除策略';
