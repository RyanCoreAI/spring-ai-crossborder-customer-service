-- OmniMerchant commercial helpdesk core tables.
-- Run after sql/db_channels.sql and before sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `ticket` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ticket_no` VARCHAR(64) NOT NULL,
  `conversation_uuid` VARCHAR(64) DEFAULT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'ESCALATION',
  `source_id` BIGINT UNSIGNED DEFAULT NULL,
  `channel` VARCHAR(32) DEFAULT NULL,
  `customer_id` BIGINT UNSIGNED DEFAULT NULL,
  `customer_email` VARCHAR(128) DEFAULT NULL,
  `subject` VARCHAR(256) NOT NULL,
  `summary` TEXT DEFAULT NULL,
  `intent` VARCHAR(64) DEFAULT NULL,
  `priority` TINYINT NOT NULL DEFAULT 2,
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ASSIGNED/WAITING_CUSTOMER/PENDING_APPROVAL/RESOLVED/CLOSED/CANCELLED',
  `assigned_agent_id` BIGINT UNSIGNED DEFAULT NULL,
  `assigned_at` DATETIME(3) DEFAULT NULL,
  `first_response_at` DATETIME(3) DEFAULT NULL,
  `resolved_at` DATETIME(3) DEFAULT NULL,
  `closed_at` DATETIME(3) DEFAULT NULL,
  `sla_response_due_at` DATETIME(3) DEFAULT NULL,
  `sla_resolve_due_at` DATETIME(3) DEFAULT NULL,
  `sla_state` VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
  `csat_score` TINYINT DEFAULT NULL,
  `csat_comment` VARCHAR(512) DEFAULT NULL,
  `close_reason` VARCHAR(128) DEFAULT NULL,
  `tags` JSON DEFAULT NULL,
  `version` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`tenant_id`, `ticket_no`),
  UNIQUE KEY `uk_ticket_source` (`tenant_id`, `source_type`, `source_id`),
  KEY `idx_ticket_status` (`tenant_id`, `status`, `priority`, `updated_at`),
  KEY `idx_ticket_assignee` (`tenant_id`, `assigned_agent_id`, `status`),
  KEY `idx_ticket_conversation` (`tenant_id`, `conversation_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Unified helpdesk ticket independent from escalation_record';

CREATE TABLE IF NOT EXISTS `ticket_note` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `ticket_id` BIGINT UNSIGNED NOT NULL,
  `note_type` VARCHAR(32) NOT NULL COMMENT 'INTERNAL/CUSTOMER/AI_SUMMARY/SYSTEM',
  `author_id` BIGINT UNSIGNED DEFAULT NULL,
  `author_role` VARCHAR(64) DEFAULT NULL,
  `body` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_ticket_note` (`tenant_id`, `ticket_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Internal notes and handoff summaries for helpdesk tickets';

CREATE TABLE IF NOT EXISTS `support_macro` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `macro_code` VARCHAR(64) NOT NULL,
  `title` VARCHAR(128) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `channel` VARCHAR(32) NOT NULL DEFAULT 'ALL',
  `content` TEXT NOT NULL,
  `requires_approval` TINYINT(1) NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_support_macro` (`tenant_id`, `macro_code`),
  KEY `idx_support_macro_category` (`tenant_id`, `category`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant-managed macro replies for human support workflows';

CREATE TABLE IF NOT EXISTS `agent_idempotency_guard` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `conversation_uuid` VARCHAR(64) NOT NULL,
  `guard_key` VARCHAR(128) NOT NULL,
  `tool_name` VARCHAR(128) NOT NULL,
  `request_hash` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECORDED' COMMENT 'RECORDED/COMPLETED/FAILED',
  `first_seen_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_seen_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_guard` (`tenant_id`, `conversation_uuid`, `guard_key`),
  KEY `idx_agent_guard_tool` (`tenant_id`, `tool_name`, `last_seen_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Duplicate tool-call guard for approval and ticket creation flows';
