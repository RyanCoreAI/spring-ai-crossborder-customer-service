CREATE TABLE IF NOT EXISTS `agent_conversation_state` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_uuid` VARCHAR(64) NOT NULL,
  `state` VARCHAR(48) NOT NULL DEFAULT 'NEW',
  `last_trace_id` VARCHAR(64) DEFAULT NULL,
  `last_reason` VARCHAR(256) DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_conversation_state` (`tenant_id`, `conversation_uuid`),
  KEY `idx_agent_conversation_status` (`tenant_id`, `state`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Authoritative state for the deterministic supervisor workflow';

CREATE TABLE IF NOT EXISTS `agent_state_transition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_uuid` VARCHAR(64) NOT NULL,
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `from_state` VARCHAR(48) NOT NULL,
  `to_state` VARCHAR(48) NOT NULL,
  `trigger_type` VARCHAR(48) NOT NULL,
  `trigger_name` VARCHAR(128) DEFAULT NULL,
  `reason_redacted` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_agent_transition_conversation` (`tenant_id`, `conversation_uuid`, `created_at`),
  KEY `idx_agent_transition_trace` (`tenant_id`, `trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Replayable state transitions for supervisor-worker execution';
