-- Persistent multilingual evidence without storing raw PII in traces.
-- Run after sql/db_channel_runtime.sql. Flyway installs the same schema as V18.

ALTER TABLE `chat_message`
  ADD COLUMN `detection_confidence` DECIMAL(6,5) DEFAULT NULL AFTER `original_lang`,
  ADD COLUMN `translation_provider` VARCHAR(32) DEFAULT NULL AFTER `is_translated`,
  ADD COLUMN `translation_model` VARCHAR(64) DEFAULT NULL AFTER `translation_provider`,
  ADD COLUMN `translation_status` VARCHAR(32) DEFAULT NULL AFTER `translation_model`,
  ADD COLUMN `translation_latency_ms` INT DEFAULT NULL AFTER `translation_status`,
  ADD COLUMN `translation_fallback_reason` VARCHAR(128) DEFAULT NULL AFTER `translation_latency_ms`;

CREATE TABLE IF NOT EXISTS `translation_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_uuid` VARCHAR(64) DEFAULT NULL,
  `message_uuid` VARCHAR(64) DEFAULT NULL,
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `direction` VARCHAR(16) NOT NULL COMMENT 'IN/OUT/DEBUG',
  `source_language` VARCHAR(8) NOT NULL,
  `target_language` VARCHAR(8) NOT NULL,
  `detection_confidence` DECIMAL(6,5) DEFAULT NULL,
  `source_text_redacted` VARCHAR(2048) DEFAULT NULL,
  `translated_text_redacted` VARCHAR(2048) DEFAULT NULL,
  `source_hash` VARCHAR(64) DEFAULT NULL,
  `translated_hash` VARCHAR(64) DEFAULT NULL,
  `provider` VARCHAR(32) NOT NULL,
  `model` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `latency_ms` INT NOT NULL DEFAULT 0,
  `fallback_reason` VARCHAR(128) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_translation_conversation` (`tenant_id`, `conversation_uuid`, `created_at`),
  KEY `idx_translation_trace` (`tenant_id`, `trace_id`),
  KEY `idx_translation_status` (`tenant_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Redacted language detection and translation execution evidence';
