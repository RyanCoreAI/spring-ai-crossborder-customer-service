-- OmniMerchant v3 RAG evidence-quality deepening.
-- Run after sql/db_main.sql and sql/db_rag_safety.sql on the MySQL database.

SET @schema_name = DATABASE();

SET @add_source_trust = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'source_trust_level') = 0,
  'ALTER TABLE `knowledge_doc` ADD COLUMN `source_trust_level` VARCHAR(32) NOT NULL DEFAULT ''MEDIUM'' AFTER `source_type`',
  'SELECT 1'
);
PREPARE stmt FROM @add_source_trust;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_approved_by = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'approved_by') = 0,
  'ALTER TABLE `knowledge_doc` ADD COLUMN `approved_by` BIGINT DEFAULT NULL AFTER `published_at`',
  'SELECT 1'
);
PREPARE stmt FROM @add_approved_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_approved_at = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'knowledge_doc' AND COLUMN_NAME = 'approved_at') = 0,
  'ALTER TABLE `knowledge_doc` ADD COLUMN `approved_at` DATETIME DEFAULT NULL AFTER `approved_by`',
  'SELECT 1'
);
PREPARE stmt FROM @add_approved_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_review_source_trust = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rag_safety_review' AND COLUMN_NAME = 'source_trust_level') = 0,
  'ALTER TABLE `rag_safety_review` ADD COLUMN `source_trust_level` VARCHAR(32) NOT NULL DEFAULT ''MEDIUM'' AFTER `source_type`',
  'SELECT 1'
);
PREPARE stmt FROM @add_review_source_trust;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_risk_rules = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rag_safety_review' AND COLUMN_NAME = 'risk_rules') = 0,
  'ALTER TABLE `rag_safety_review` ADD COLUMN `risk_rules` TEXT DEFAULT NULL AFTER `matched_rules`',
  'SELECT 1'
);
PREPARE stmt FROM @add_risk_rules;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_approval_history = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rag_safety_review' AND COLUMN_NAME = 'approval_history') = 0,
  'ALTER TABLE `rag_safety_review` ADD COLUMN `approval_history` TEXT DEFAULT NULL AFTER `review_note`',
  'SELECT 1'
);
PREPARE stmt FROM @add_approval_history;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_index_version = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rag_safety_review' AND COLUMN_NAME = 'index_version') = 0,
  'ALTER TABLE `rag_safety_review` ADD COLUMN `index_version` VARCHAR(32) NOT NULL DEFAULT ''v1'' AFTER `approval_history`',
  'SELECT 1'
);
PREPARE stmt FROM @add_index_version;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_rag_review_status_idx = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rag_safety_review' AND INDEX_NAME = 'idx_rag_review_trust') = 0,
  'ALTER TABLE `rag_safety_review` ADD INDEX `idx_rag_review_trust` (`tenant_id`, `source_trust_level`, `status`)',
  'SELECT 1'
);
PREPARE stmt FROM @add_rag_review_status_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
