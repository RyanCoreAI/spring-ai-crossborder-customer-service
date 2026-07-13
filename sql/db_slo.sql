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

CREATE TABLE IF NOT EXISTS `slo_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `slo_key` VARCHAR(64) NOT NULL,
  `slo_label` VARCHAR(128) NOT NULL,
  `target_value` DECIMAL(15,4) NOT NULL,
  `actual_value` DECIMAL(15,4) DEFAULT NULL,
  `unit` VARCHAR(32) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `window_minutes` INT NOT NULL DEFAULT 60,
  `captured_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_slo_snapshot_tenant_time` (`tenant_id`, `captured_at`),
  KEY `idx_slo_snapshot_key_time` (`tenant_id`, `slo_key`, `captured_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persisted tenant SLO measurements';

CREATE TABLE IF NOT EXISTS `alert_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `alert_key` VARCHAR(128) NOT NULL,
  `severity` VARCHAR(16) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  `message` VARCHAR(512) NOT NULL,
  `runbook` VARCHAR(1024) DEFAULT NULL,
  `occurrence_count` BIGINT NOT NULL DEFAULT 1,
  `first_observed_at` DATETIME(3) NOT NULL,
  `last_observed_at` DATETIME(3) NOT NULL,
  `acknowledged_by` BIGINT DEFAULT NULL,
  `acknowledged_at` DATETIME(3) DEFAULT NULL,
  `closed_at` DATETIME(3) DEFAULT NULL,
  `resolution_note` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_alert_event_key` (`tenant_id`, `alert_key`),
  KEY `idx_alert_event_status_time` (`tenant_id`, `status`, `last_observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Deduplicated tenant alert lifecycle';

CREATE TABLE IF NOT EXISTS `rollout_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `config_type` VARCHAR(32) NOT NULL,
  `config_key` VARCHAR(96) NOT NULL,
  `stable_version` VARCHAR(96) NOT NULL,
  `candidate_version` VARCHAR(96) DEFAULT NULL,
  `traffic_percentage` INT NOT NULL DEFAULT 0,
  `enforcement_mode` VARCHAR(32) NOT NULL DEFAULT 'OBSERVE_ONLY',
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `notes` VARCHAR(512) DEFAULT NULL,
  `activated_by` BIGINT DEFAULT NULL,
  `activated_at` DATETIME(3) DEFAULT NULL,
  `rolled_back_by` BIGINT DEFAULT NULL,
  `rolled_back_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rollout_config` (`tenant_id`, `config_type`, `config_key`),
  KEY `idx_rollout_status` (`tenant_id`, `status`, `config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Version rollout registry with explicit enforcement boundary';
