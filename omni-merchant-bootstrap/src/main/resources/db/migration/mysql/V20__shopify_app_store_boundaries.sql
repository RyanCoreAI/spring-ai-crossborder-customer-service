-- Shopify privacy, ordering, and bulk-operation evidence tables.

ALTER TABLE `webhook_event`
  ADD COLUMN `resource_version` VARCHAR(128) DEFAULT NULL AFTER `resource_id`,
  ADD COLUMN `occurred_at` DATETIME(3) DEFAULT NULL AFTER `resource_version`;

CREATE TABLE IF NOT EXISTS `shopify_resource_checkpoint` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `resource_type` VARCHAR(32) NOT NULL,
  `resource_id` VARCHAR(128) NOT NULL,
  `latest_occurred_at` DATETIME(3) NOT NULL,
  `latest_event_uuid` VARCHAR(128) NOT NULL,
  `resource_version` VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shopify_resource_checkpoint` (`tenant_id`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopify webhook ordering checkpoint';

CREATE TABLE IF NOT EXISTS `shopify_privacy_request` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `request_uuid` CHAR(36) NOT NULL,
  `topic` VARCHAR(64) NOT NULL,
  `shop_domain` VARCHAR(255) NOT NULL,
  `customer_external_id` VARCHAR(128) DEFAULT NULL,
  `customer_email_hash` CHAR(64) DEFAULT NULL,
  `payload_hash` CHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `affected_records` INT NOT NULL DEFAULT 0,
  `completed_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shopify_privacy_request` (`tenant_id`, `request_uuid`),
  KEY `idx_shopify_privacy_topic` (`tenant_id`, `topic`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopify mandatory privacy webhook processing';

CREATE TABLE IF NOT EXISTS `shopify_bulk_operation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `shop_domain` VARCHAR(255) NOT NULL,
  `resource` VARCHAR(64) NOT NULL,
  `external_operation_id` VARCHAR(255) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `object_count` BIGINT NOT NULL DEFAULT 0,
  `file_size` BIGINT DEFAULT NULL,
  `result_url_encrypted` LONGTEXT DEFAULT NULL,
  `partial_url_encrypted` LONGTEXT DEFAULT NULL,
  `error_code` VARCHAR(128) DEFAULT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `completed_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shopify_bulk_external` (`tenant_id`, `external_operation_id`),
  KEY `idx_shopify_bulk_status` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shopify asynchronous bulk initial sync';
