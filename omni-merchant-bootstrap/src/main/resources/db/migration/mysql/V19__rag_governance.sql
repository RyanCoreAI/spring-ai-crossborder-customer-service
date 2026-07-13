-- OmniMerchant RAG dataset, feedback, retrieval experiment, and index-release governance.

ALTER TABLE `agent_eval_case`
  ADD COLUMN `dataset_kind` VARCHAR(24) NOT NULL DEFAULT 'CONTRACT' AFTER `attack_type`,
  ADD COLUMN `dataset_version` VARCHAR(64) NOT NULL DEFAULT 'contract-v1' AFTER `dataset_kind`,
  ADD COLUMN `annotation_status` VARCHAR(24) NOT NULL DEFAULT 'GENERATED' AFTER `dataset_version`,
  ADD COLUMN `annotated_by` BIGINT UNSIGNED DEFAULT NULL AFTER `annotation_status`,
  ADD COLUMN `annotated_at` DATETIME(3) DEFAULT NULL AFTER `annotated_by`;

ALTER TABLE `agent_eval_run`
  ADD COLUMN `dataset_kind` VARCHAR(24) NOT NULL DEFAULT 'CONTRACT' AFTER `run_mode`,
  ADD COLUMN `dataset_version` VARCHAR(64) NOT NULL DEFAULT 'contract-v1' AFTER `dataset_kind`,
  ADD COLUMN `index_version` VARCHAR(64) DEFAULT NULL AFTER `dataset_version`,
  ADD COLUMN `embedding_model` VARCHAR(128) DEFAULT NULL AFTER `index_version`,
  ADD COLUMN `query_planner_version` VARCHAR(64) DEFAULT NULL AFTER `embedding_model`,
  ADD COLUMN `prompt_version` VARCHAR(64) DEFAULT NULL AFTER `query_planner_version`,
  ADD COLUMN `retrieval_mode` VARCHAR(32) DEFAULT NULL AFTER `prompt_version`;

CREATE TABLE IF NOT EXISTS `rag_dataset_version` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `dataset_key` VARCHAR(96) NOT NULL,
  `dataset_kind` VARCHAR(24) NOT NULL,
  `version` VARCHAR(64) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `case_count` INT NOT NULL DEFAULT 0,
  `language_distribution` TEXT DEFAULT NULL,
  `checksum` CHAR(64) DEFAULT NULL,
  `approved_by` BIGINT UNSIGNED DEFAULT NULL,
  `approved_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_dataset_version` (`tenant_id`, `dataset_key`, `version`),
  KEY `idx_rag_dataset_status` (`tenant_id`, `dataset_kind`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG evaluation dataset versions';

CREATE TABLE IF NOT EXISTS `rag_feedback` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `feedback_uuid` CHAR(36) NOT NULL,
  `conversation_uuid` VARCHAR(64) DEFAULT NULL,
  `trace_id` VARCHAR(64) DEFAULT NULL,
  `question_hash` CHAR(64) NOT NULL,
  `feedback_type` VARCHAR(32) NOT NULL,
  `doc_uuid` VARCHAR(64) DEFAULT NULL,
  `chunk_uuid` VARCHAR(64) DEFAULT NULL,
  `comment_redacted` VARCHAR(1000) DEFAULT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  `submitted_by` BIGINT UNSIGNED NOT NULL,
  `resolved_by` BIGINT UNSIGNED DEFAULT NULL,
  `resolved_at` DATETIME(3) DEFAULT NULL,
  `resolution_note` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_feedback_uuid` (`tenant_id`, `feedback_uuid`),
  KEY `idx_rag_feedback_queue` (`tenant_id`, `status`, `feedback_type`, `created_at`),
  KEY `idx_rag_feedback_trace` (`tenant_id`, `trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Human RAG quality feedback queue';

CREATE TABLE IF NOT EXISTS `rag_index_release` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `index_version` VARCHAR(64) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  `embedding_model` VARCHAR(128) NOT NULL,
  `reranker_mode` VARCHAR(64) NOT NULL,
  `query_planner_version` VARCHAR(64) NOT NULL,
  `previous_version` VARCHAR(64) DEFAULT NULL,
  `release_note` VARCHAR(1000) DEFAULT NULL,
  `activated_by` BIGINT UNSIGNED DEFAULT NULL,
  `activated_at` DATETIME(3) DEFAULT NULL,
  `rolled_back_by` BIGINT UNSIGNED DEFAULT NULL,
  `rolled_back_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_index_release` (`tenant_id`, `index_version`),
  KEY `idx_rag_index_status` (`tenant_id`, `status`, `activated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tenant-scoped RAG index releases';

CREATE TABLE IF NOT EXISTS `rag_retrieval_experiment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `run_uuid` CHAR(36) NOT NULL,
  `dataset_key` VARCHAR(96) NOT NULL,
  `dataset_kind` VARCHAR(24) NOT NULL,
  `dataset_version` VARCHAR(64) NOT NULL,
  `index_version` VARCHAR(64) DEFAULT NULL,
  `retrieval_mode` VARCHAR(32) NOT NULL,
  `status` VARCHAR(24) NOT NULL DEFAULT 'RUNNING',
  `case_count` INT NOT NULL DEFAULT 0,
  `context_precision` DECIMAL(8,4) DEFAULT NULL,
  `context_recall` DECIMAL(8,4) DEFAULT NULL,
  `mrr` DECIMAL(8,4) DEFAULT NULL,
  `ndcg_at_k` DECIMAL(8,4) DEFAULT NULL,
  `citation_coverage` DECIMAL(8,4) DEFAULT NULL,
  `faithfulness` DECIMAL(8,4) DEFAULT NULL,
  `no_answer_accuracy` DECIMAL(8,4) DEFAULT NULL,
  `poisoning_block_rate` DECIMAL(8,4) DEFAULT NULL,
  `p95_retrieval_latency_ms` INT DEFAULT NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_experiment_mode` (`tenant_id`, `run_uuid`, `retrieval_mode`),
  KEY `idx_rag_experiment_dataset` (`tenant_id`, `dataset_key`, `dataset_version`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Comparable RAG retrieval experiments';

INSERT IGNORE INTO `role_permission` (`role_key`, `permission_key`) VALUES
  ('TENANT_ADMIN', 'rag:feedback'),
  ('TENANT_ADMIN', 'rag:dataset'),
  ('TENANT_ADMIN', 'rag:release'),
  ('SUPPORT_SUPERVISOR', 'rag:feedback'),
  ('SUPPORT_SUPERVISOR', 'rag:dataset'),
  ('SUPPORT_SUPERVISOR', 'rag:release'),
  ('SUPPORT_AGENT', 'rag:feedback'),
  ('READ_ONLY_AUDITOR', 'rag:read');
