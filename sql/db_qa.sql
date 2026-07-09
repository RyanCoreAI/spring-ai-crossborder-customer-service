-- OmniMerchant commercial helpdesk QA queue.
-- Run after sql/db_main.sql and sql/db_shopify_v2.sql.

CREATE TABLE IF NOT EXISTS `qa_review_queue` (
  `id` BIGINT NOT NULL,
  `tenant_id` BIGINT NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `source_id` BIGINT NOT NULL,
  `conversation_uuid` VARCHAR(64) DEFAULT NULL,
  `ticket_no` VARCHAR(64) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `auto_score` INT DEFAULT NULL,
  `reviewer_score` INT DEFAULT NULL,
  `review_flags` VARCHAR(512) DEFAULT NULL,
  `findings` TEXT DEFAULT NULL,
  `action_items` TEXT DEFAULT NULL,
  `reviewer_id` BIGINT DEFAULT NULL,
  `reviewed_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qa_source` (`tenant_id`, `source_type`, `source_id`),
  KEY `idx_qa_status` (`tenant_id`, `status`, `created_at`),
  KEY `idx_qa_ticket` (`tenant_id`, `ticket_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服质检任务队列';
