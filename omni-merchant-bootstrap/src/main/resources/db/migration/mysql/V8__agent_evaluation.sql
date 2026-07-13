-- OmniMerchant v3 golden conversation eval run tables.
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
  `retrieval_precision_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `recall_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `mrr` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `ndcg_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `unsupported_claim_rate` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `no_answer_accuracy` DECIMAL(6,2) NOT NULL DEFAULT 0.00,
  `p95_retrieval_latency_ms` INT DEFAULT NULL,
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
  `retrieval_hit` TINYINT(1) NOT NULL DEFAULT 0,
  `retrieval_rank` INT DEFAULT NULL,
  `reciprocal_rank` DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
  `ndcg_score` DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
  `no_answer_expected` TINYINT(1) NOT NULL DEFAULT 0,
  `no_answer_passed` TINYINT(1) NOT NULL DEFAULT 1,
  `retrieval_latency_ms` INT DEFAULT NULL,
  `reranker_mode` VARCHAR(32) DEFAULT NULL,
  `expected_evidence` VARCHAR(2048) DEFAULT NULL,
  `actual_evidence` VARCHAR(2048) DEFAULT NULL,
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

SET @schema_name = DATABASE();
SET @add_retrieval_precision = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'retrieval_precision_at_k') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `retrieval_precision_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `poisoning_block_rate`',
  'SELECT 1'
);
PREPARE stmt FROM @add_retrieval_precision;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_unsupported_claim = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'unsupported_claim_rate') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `unsupported_claim_rate` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `retrieval_precision_at_k`',
  'SELECT 1'
);
PREPARE stmt FROM @add_unsupported_claim;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_recall_at_k = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'recall_at_k') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `recall_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `retrieval_precision_at_k`',
  'SELECT 1'
);
PREPARE stmt FROM @add_recall_at_k;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_mrr = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'mrr') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `mrr` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `recall_at_k`',
  'SELECT 1'
);
PREPARE stmt FROM @add_mrr;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_ndcg_at_k = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'ndcg_at_k') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `ndcg_at_k` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `mrr`',
  'SELECT 1'
);
PREPARE stmt FROM @add_ndcg_at_k;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_no_answer_accuracy = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'no_answer_accuracy') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `no_answer_accuracy` DECIMAL(6,2) NOT NULL DEFAULT 0.00 AFTER `unsupported_claim_rate`',
  'SELECT 1'
);
PREPARE stmt FROM @add_no_answer_accuracy;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_p95_retrieval_latency = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_run'
     AND COLUMN_NAME = 'p95_retrieval_latency_ms') = 0,
  'ALTER TABLE `agent_eval_run` ADD COLUMN `p95_retrieval_latency_ms` INT DEFAULT NULL AFTER `no_answer_accuracy`',
  'SELECT 1'
);
PREPARE stmt FROM @add_p95_retrieval_latency;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_retrieval_hit = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'retrieval_hit') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `retrieval_hit` TINYINT(1) NOT NULL DEFAULT 0 AFTER `safety_passed`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_retrieval_hit;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_retrieval_rank = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'retrieval_rank') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `retrieval_rank` INT DEFAULT NULL AFTER `retrieval_hit`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_retrieval_rank;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_rr = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'reciprocal_rank') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `reciprocal_rank` DECIMAL(8,4) NOT NULL DEFAULT 0.0000 AFTER `retrieval_rank`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_rr;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_ndcg = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'ndcg_score') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `ndcg_score` DECIMAL(8,4) NOT NULL DEFAULT 0.0000 AFTER `reciprocal_rank`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_ndcg;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_no_answer_expected = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'no_answer_expected') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `no_answer_expected` TINYINT(1) NOT NULL DEFAULT 0 AFTER `ndcg_score`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_no_answer_expected;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_no_answer_passed = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'no_answer_passed') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `no_answer_passed` TINYINT(1) NOT NULL DEFAULT 1 AFTER `no_answer_expected`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_no_answer_passed;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_retrieval_latency = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'retrieval_latency_ms') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `retrieval_latency_ms` INT DEFAULT NULL AFTER `no_answer_passed`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_retrieval_latency;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_reranker = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'reranker_mode') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `reranker_mode` VARCHAR(32) DEFAULT NULL AFTER `retrieval_latency_ms`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_reranker;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_expected_evidence = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'expected_evidence') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `expected_evidence` VARCHAR(2048) DEFAULT NULL AFTER `reranker_mode`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_expected_evidence;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_result_actual_evidence = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name
     AND TABLE_NAME = 'agent_eval_result'
     AND COLUMN_NAME = 'actual_evidence') = 0,
  'ALTER TABLE `agent_eval_result` ADD COLUMN `actual_evidence` VARCHAR(2048) DEFAULT NULL AFTER `expected_evidence`',
  'SELECT 1'
);
PREPARE stmt FROM @add_result_actual_evidence;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
