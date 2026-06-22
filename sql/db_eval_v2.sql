-- OmniMerchant v2 golden conversation eval run tables.
-- Run after sql/db_main.sql, sql/db_extensions.sql, and sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `agent_eval_run` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `run_uuid` VARCHAR(64) NOT NULL,
  `run_mode` VARCHAR(32) NOT NULL DEFAULT 'DETERMINISTIC',
  `git_commit` VARCHAR(64) DEFAULT NULL,
  `model_config` VARCHAR(512) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  `total_cases` INT NOT NULL DEFAULT 0,
  `passed_cases` INT NOT NULL DEFAULT 0,
  `failed_cases` INT NOT NULL DEFAULT 0,
  `pass_rate` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `tool_precision` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `tool_recall` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `citation_coverage` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `poisoning_block_rate` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `failure_summary` TEXT DEFAULT NULL,
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `finished_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eval_run_uuid` (`run_uuid`),
  KEY `idx_eval_tenant_started` (`tenant_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Golden conversation eval run';

CREATE TABLE IF NOT EXISTS `agent_eval_result` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `run_id` BIGINT NOT NULL,
  `case_id` BIGINT DEFAULT NULL,
  `case_code` VARCHAR(64) NOT NULL,
  `intent` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `expected_outcome` VARCHAR(2048) DEFAULT NULL,
  `actual_observation` VARCHAR(2048) DEFAULT NULL,
  `expected_tools` VARCHAR(512) DEFAULT NULL,
  `actual_tools` VARCHAR(512) DEFAULT NULL,
  `forbidden_tools` VARCHAR(512) DEFAULT NULL,
  `tool_precision` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `tool_recall` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `argument_match` TINYINT(1) NOT NULL DEFAULT 1,
  `forbidden_tool_violation` TINYINT(1) NOT NULL DEFAULT 0,
  `citation_required` TINYINT(1) NOT NULL DEFAULT 0,
  `citation_passed` TINYINT(1) NOT NULL DEFAULT 1,
  `poisoning_case` TINYINT(1) NOT NULL DEFAULT 0,
  `safety_passed` TINYINT(1) NOT NULL DEFAULT 1,
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `failure_category` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_eval_result_run` (`run_id`, `status`),
  KEY `idx_eval_result_case` (`tenant_id`, `case_code`),
  KEY `idx_eval_result_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Golden conversation eval case result';

CREATE TABLE IF NOT EXISTS `agent_eval_step_result` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `run_id` BIGINT NOT NULL,
  `result_id` BIGINT NOT NULL,
  `step_name` VARCHAR(128) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `expected_value` VARCHAR(1024) DEFAULT NULL,
  `actual_value` VARCHAR(1024) DEFAULT NULL,
  `message` VARCHAR(2048) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_eval_step_result` (`result_id`, `step_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Golden conversation eval checker step result';
