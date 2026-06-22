-- OmniMerchant v2 Shopify production connector tables.
-- Run after sql/db_main.sql and sql/db_extensions.sql.

CREATE TABLE IF NOT EXISTS `shopify_sync_job` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `shop_domain` VARCHAR(255) NOT NULL,
  `resource` VARCHAR(64) NOT NULL,
  `cursor_value` VARCHAR(512) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `attempts` INT NOT NULL DEFAULT 0,
  `last_error` VARCHAR(2048) DEFAULT NULL,
  `next_run_at` DATETIME DEFAULT NULL,
  `last_run_at` DATETIME DEFAULT NULL,
  `imported_count` INT NOT NULL DEFAULT 0,
  `throttle_status_json` TEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_resource` (`tenant_id`, `shop_domain`, `resource`),
  KEY `idx_shopify_job_status` (`tenant_id`, `status`, `next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Shopify cursor sync checkpoint';

CREATE TABLE IF NOT EXISTS `commerce_action_request` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `request_no` VARCHAR(64) NOT NULL,
  `action_type` VARCHAR(64) NOT NULL,
  `platform` VARCHAR(32) NOT NULL DEFAULT 'shopify',
  `external_order_id` VARCHAR(128) DEFAULT NULL,
  `external_order_number` VARCHAR(64) DEFAULT NULL,
  `customer_email` VARCHAR(128) DEFAULT NULL,
  `requested_payload` TEXT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
  `risk_reason` VARCHAR(512) DEFAULT NULL,
  `approved_by` BIGINT DEFAULT NULL,
  `approved_at` DATETIME DEFAULT NULL,
  `executed_at` DATETIME DEFAULT NULL,
  `external_result` TEXT DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_request_no` (`request_no`),
  KEY `idx_action_tenant_status` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Approval-gated external commerce action request';
