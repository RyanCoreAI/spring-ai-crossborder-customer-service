-- OmniMerchant extension tables for platform v1.
-- Run after sql/db_main.sql.

CREATE TABLE IF NOT EXISTS `return_request` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_no` VARCHAR(64) NOT NULL,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `request_type` VARCHAR(32) NOT NULL COMMENT 'RETURN/REFUND/REPLACEMENT/ADDRESS_CHANGE',
  `external_order_number` VARCHAR(64) NOT NULL,
  `customer_email` VARCHAR(128) DEFAULT NULL,
  `reason` VARCHAR(512) DEFAULT NULL,
  `requested_items` JSON DEFAULT NULL,
  `amount` DECIMAL(15,4) DEFAULT NULL,
  `currency` VARCHAR(8) DEFAULT NULL,
  `priority` TINYINT NOT NULL DEFAULT 2,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1 pending 2 reviewing 3 approved 4 rejected 5 done',
  `approval_required_reason` VARCHAR(256) DEFAULT NULL,
  `resolution` VARCHAR(64) DEFAULT NULL,
  `resolution_note` TEXT DEFAULT NULL,
  `ext_attr` JSON DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_tenant_status` (`tenant_id`, `status`, `created_at`),
  KEY `idx_tenant_order` (`tenant_id`, `external_order_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI-created customer action requests';

CREATE TABLE IF NOT EXISTS `integration_credential` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `platform` VARCHAR(32) NOT NULL,
  `shop_domain` VARCHAR(255) NOT NULL,
  `access_token_encrypted` TEXT NOT NULL,
  `webhook_secret_encrypted` TEXT DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `last_sync_at` DATETIME DEFAULT NULL,
  `last_sync_status` VARCHAR(32) DEFAULT NULL,
  `last_sync_error` VARCHAR(2048) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_platform_shop` (`tenant_id`, `platform`, `shop_domain`),
  KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Encrypted ecommerce integration credentials';

CREATE TABLE IF NOT EXISTS `channel_installation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel` VARCHAR(32) NOT NULL COMMENT 'WEB_WIDGET/EMAIL/WHATSAPP/SHOPIFY_CHAT',
  `public_channel_key` VARCHAR(128) NOT NULL,
  `allowed_origins` JSON DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_public_channel_key` (`public_channel_key`),
  KEY `idx_tenant_channel` (`tenant_id`, `channel`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Customer-facing channel installations';

CREATE TABLE IF NOT EXISTS `agent_eval_case` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `case_code` VARCHAR(64) NOT NULL,
  `intent` VARCHAR(32) NOT NULL,
  `user_message` TEXT NOT NULL,
  `expected_tools` JSON DEFAULT NULL,
  `expected_outcome` VARCHAR(1024) NOT NULL,
  `attack_type` VARCHAR(64) DEFAULT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_case` (`tenant_id`, `case_code`),
  KEY `idx_tenant_intent` (`tenant_id`, `intent`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Golden eval cases for ecommerce agent behavior';
