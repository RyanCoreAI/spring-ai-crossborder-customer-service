-- OmniMerchant commercial helpdesk channel model.
-- Run after sql/db_extensions.sql and before sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `channel_account` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel` VARCHAR(32) NOT NULL COMMENT 'WEB_WIDGET/EMAIL/WHATSAPP/INSTAGRAM/FACEBOOK/SMS/VOICE',
  `account_name` VARCHAR(128) NOT NULL,
  `external_account_id` VARCHAR(128) NOT NULL DEFAULT '',
  `adapter_status` VARCHAR(32) NOT NULL DEFAULT 'CONFIGURED' COMMENT 'CONNECTED/CONFIGURED/ADAPTER_READY/PLANNED/DISABLED/ERROR',
  `inbound_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `outbound_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `auth_mode` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'PUBLIC_KEY/OAUTH/API_KEY/NONE',
  `webhook_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED',
  `last_event_at` DATETIME(3) DEFAULT NULL,
  `last_error` VARCHAR(1024) DEFAULT NULL,
  `config_json` JSON DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_account` (`tenant_id`, `channel`, `external_account_id`),
  KEY `idx_channel_account_status` (`tenant_id`, `channel`, `adapter_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant-owned support channel accounts';

CREATE TABLE IF NOT EXISTS `channel_conversation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_account_id` BIGINT UNSIGNED NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `conversation_uuid` VARCHAR(64) NOT NULL,
  `external_thread_id` VARCHAR(128) NOT NULL,
  `customer_external_id` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  `last_inbound_at` DATETIME(3) DEFAULT NULL,
  `last_outbound_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_thread` (`tenant_id`, `channel`, `external_thread_id`),
  KEY `idx_channel_conversation` (`tenant_id`, `conversation_uuid`),
  KEY `idx_channel_status` (`tenant_id`, `channel`, `status`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='External channel thread to OmniMerchant conversation mapping';

CREATE TABLE IF NOT EXISTS `channel_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_account_id` BIGINT UNSIGNED NOT NULL,
  `conversation_uuid` VARCHAR(64) NOT NULL,
  `message_uuid` VARCHAR(64) NOT NULL,
  `external_message_id` VARCHAR(128) NOT NULL DEFAULT '',
  `direction` VARCHAR(16) NOT NULL COMMENT 'INBOUND/OUTBOUND',
  `sender_type` VARCHAR(32) NOT NULL COMMENT 'CUSTOMER/AI/HUMAN/SYSTEM',
  `body_preview` VARCHAR(512) DEFAULT NULL,
  `delivery_status` VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  `idempotency_key` VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_message` (`tenant_id`, `channel_account_id`, `external_message_id`),
  KEY `idx_channel_message_conversation` (`tenant_id`, `conversation_uuid`, `created_at`),
  KEY `idx_channel_message_idempotency` (`tenant_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cross-channel message delivery envelope';

CREATE TABLE IF NOT EXISTS `channel_customer_identity` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_account_id` BIGINT UNSIGNED NOT NULL,
  `customer_id` BIGINT UNSIGNED DEFAULT NULL,
  `identity_type` VARCHAR(32) NOT NULL COMMENT 'EMAIL/PHONE/SHOPIFY_CUSTOMER_ID/SOCIAL_HANDLE',
  `identity_value_hash` VARCHAR(128) NOT NULL,
  `display_value_masked` VARCHAR(128) DEFAULT NULL,
  `verified_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_identity` (`tenant_id`, `channel_account_id`, `identity_type`, `identity_value_hash`),
  KEY `idx_channel_customer` (`tenant_id`, `customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Verified customer identities per support channel';

CREATE TABLE IF NOT EXISTS `channel_delivery_receipt` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_message_id` BIGINT UNSIGNED NOT NULL,
  `receipt_type` VARCHAR(32) NOT NULL COMMENT 'SENT/DELIVERED/READ/BOUNCED/FAILED',
  `provider_event_id` VARCHAR(128) NOT NULL DEFAULT '',
  `provider_payload_hash` VARCHAR(128) DEFAULT NULL,
  `observed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_receipt` (`tenant_id`, `channel_message_id`, `receipt_type`, `provider_event_id`),
  KEY `idx_channel_receipt_observed` (`tenant_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Delivery/read/bounce receipts from external support channels';
