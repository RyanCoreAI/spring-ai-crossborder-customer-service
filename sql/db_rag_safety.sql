-- OmniMerchant v3 RAG ingestion safety review table.
-- Run after sql/db_main.sql.

CREATE TABLE IF NOT EXISTS `rag_safety_review` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `doc_uuid` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL DEFAULT 'KNOWLEDGE_DOC',
  `source_trust_level` VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'LOW',
  `status` VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
  `index_allowed` TINYINT(1) NOT NULL DEFAULT 1,
  `matched_rules` TEXT DEFAULT NULL,
  `risk_rules` TEXT DEFAULT NULL,
  `redacted_excerpt` VARCHAR(2048) DEFAULT NULL,
  `review_note` VARCHAR(1024) DEFAULT NULL,
  `approval_history` TEXT DEFAULT NULL,
  `index_version` VARCHAR(32) NOT NULL DEFAULT 'v1',
  `reviewed_by` BIGINT DEFAULT NULL,
  `reviewed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_review_doc` (`tenant_id`, `doc_uuid`),
  KEY `idx_rag_review_status` (`tenant_id`, `status`, `risk_level`),
  KEY `idx_rag_review_trust` (`tenant_id`, `source_trust_level`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG ingestion safety review';
