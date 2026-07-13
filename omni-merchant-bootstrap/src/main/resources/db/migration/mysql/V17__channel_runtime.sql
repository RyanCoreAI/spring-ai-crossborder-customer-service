ALTER TABLE `channel_account`
  ADD COLUMN `callback_key` VARCHAR(64) DEFAULT NULL AFTER `external_account_id`,
  ADD COLUMN `credential_id` BIGINT UNSIGNED DEFAULT NULL AFTER `callback_key`,
  ADD UNIQUE KEY `uk_channel_callback_key` (`callback_key`);

ALTER TABLE `integration_credential`
  ADD COLUMN `credential_payload_encrypted` LONGTEXT DEFAULT NULL AFTER `webhook_secret_encrypted`;

CREATE TABLE IF NOT EXISTS `channel_webhook_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_account_id` BIGINT UNSIGNED NOT NULL,
  `provider_event_key` VARCHAR(128) NOT NULL,
  `payload_hash` VARCHAR(128) NOT NULL,
  `encrypted_payload` LONGTEXT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
  `attempts` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME(3) DEFAULT NULL,
  `last_error` VARCHAR(1024) DEFAULT NULL,
  `received_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` DATETIME(3) DEFAULT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_provider_event` (`tenant_id`, `channel_account_id`, `provider_event_key`),
  KEY `idx_channel_webhook_retry` (`status`, `next_attempt_at`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Encrypted provider callbacks with idempotent processing and DLQ state';

CREATE TABLE IF NOT EXISTS `channel_outbox_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `event_uuid` VARCHAR(64) NOT NULL,
  `aggregate_type` VARCHAR(64) NOT NULL,
  `aggregate_id` VARCHAR(128) NOT NULL,
  `event_type` VARCHAR(128) NOT NULL,
  `payload_json` JSON NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `attempts` INT NOT NULL DEFAULT 0,
  `available_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `published_at` DATETIME(3) DEFAULT NULL,
  `last_error` VARCHAR(1024) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_outbox_uuid` (`event_uuid`),
  KEY `idx_channel_outbox_pending` (`status`, `available_at`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Transactional outbox for channel webhook and outbound processing';

CREATE TABLE IF NOT EXISTS `channel_sync_cursor` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `channel_account_id` BIGINT UNSIGNED NOT NULL,
  `cursor_encrypted` LONGTEXT DEFAULT NULL,
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_sync_cursor` (`tenant_id`, `channel_account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Encrypted provider synchronization cursor per channel account';
