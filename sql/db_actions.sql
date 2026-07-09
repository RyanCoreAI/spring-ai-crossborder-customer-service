-- OmniMerchant commerce action approval policies.
-- Run after sql/db_sla.sql and before sql/demo_seed.sql.

CREATE TABLE IF NOT EXISTS `commerce_action_policy` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `action_type` VARCHAR(32) NOT NULL COMMENT 'RETURN/REFUND/REPLACEMENT/ADDRESS_CHANGE/CANCEL_ORDER/COUPON',
  `approval_required` TINYINT(1) NOT NULL DEFAULT 1,
  `min_approver_role` VARCHAR(64) NOT NULL DEFAULT 'SUPPORT_SUPERVISOR',
  `amount_threshold` DECIMAL(15,4) DEFAULT NULL,
  `requires_identity_verification` TINYINT(1) NOT NULL DEFAULT 1,
  `idempotency_window_minutes` INT NOT NULL DEFAULT 60,
  `external_write_enabled` TINYINT(1) NOT NULL DEFAULT 0,
  `policy_note` VARCHAR(512) DEFAULT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_policy` (`tenant_id`, `action_type`),
  KEY `idx_action_policy_active` (`tenant_id`, `active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Approval gate policy for high-risk commerce actions';
