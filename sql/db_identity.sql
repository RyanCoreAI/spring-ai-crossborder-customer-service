-- OmniMerchant database-backed identity, tenant memberships, and token lifecycle.
-- Global identity tables are intentionally not tenant-filtered; memberships carry tenant_id.

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `email` VARCHAR(191) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `display_name` VARCHAR(128) DEFAULT NULL,
  `platform_admin` TINYINT(1) NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `token_version` INT NOT NULL DEFAULT 1,
  `last_login_at` DATETIME(3) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_email` (`email`),
  KEY `idx_app_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台用户身份';

CREATE TABLE IF NOT EXISTS `user_tenant_membership` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `role_key` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tenant_role` (`user_id`, `tenant_id`, `role_key`),
  KEY `idx_membership_tenant_user` (`tenant_id`, `user_id`, `status`),
  CONSTRAINT `fk_membership_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户租户成员关系';

CREATE TABLE IF NOT EXISTS `role_permission` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_key` VARCHAR(64) NOT NULL,
  `permission_key` VARCHAR(96) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_key`, `permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限定义';

CREATE TABLE IF NOT EXISTS `refresh_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `token_hash` CHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) DEFAULT NULL,
  `replaced_by_hash` CHAR(64) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_user_expiry` (`user_id`, `expires_at`, `revoked_at`),
  CONSTRAINT `fk_refresh_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮换式刷新令牌';

CREATE TABLE IF NOT EXISTS `revoked_access_token` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `token_jti` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revoked_token_jti` (`token_jti`),
  KEY `idx_revoked_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问令牌吊销列表';

CREATE TABLE IF NOT EXISTS `security_audit_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED DEFAULT NULL,
  `tenant_id` BIGINT UNSIGNED DEFAULT NULL,
  `action` VARCHAR(96) NOT NULL,
  `outcome` VARCHAR(32) NOT NULL,
  `subject_hash` CHAR(64) DEFAULT NULL,
  `detail` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_security_audit_user` (`user_id`, `created_at`),
  KEY `idx_security_audit_tenant` (`tenant_id`, `created_at`),
  KEY `idx_security_audit_action` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局认证与权限审计';

INSERT IGNORE INTO `role_permission` (`role_key`, `permission_key`) VALUES
  ('TENANT_ADMIN', 'tenant:manage'),
  ('TENANT_ADMIN', 'ticket:assign'),
  ('TENANT_ADMIN', 'ticket:resolve'),
  ('TENANT_ADMIN', 'inbox:takeover'),
  ('TENANT_ADMIN', 'inbox:reply'),
  ('TENANT_ADMIN', 'action:approve'),
  ('TENANT_ADMIN', 'knowledge:write'),
  ('TENANT_ADMIN', 'knowledge:review'),
  ('TENANT_ADMIN', 'integration:manage'),
  ('TENANT_ADMIN', 'webhook:replay'),
  ('TENANT_ADMIN', 'audit:read'),
  ('TENANT_ADMIN', 'qa:review'),
  ('TENANT_ADMIN', 'eval:run'),
  ('SUPPORT_SUPERVISOR', 'ticket:assign'),
  ('SUPPORT_SUPERVISOR', 'ticket:resolve'),
  ('SUPPORT_SUPERVISOR', 'inbox:takeover'),
  ('SUPPORT_SUPERVISOR', 'inbox:reply'),
  ('SUPPORT_SUPERVISOR', 'action:approve'),
  ('SUPPORT_SUPERVISOR', 'knowledge:review'),
  ('SUPPORT_SUPERVISOR', 'audit:read'),
  ('SUPPORT_SUPERVISOR', 'qa:review'),
  ('SUPPORT_SUPERVISOR', 'eval:run'),
  ('SUPPORT_AGENT', 'ticket:resolve'),
  ('SUPPORT_AGENT', 'inbox:takeover'),
  ('SUPPORT_AGENT', 'inbox:reply'),
  ('READ_ONLY_AUDITOR', 'audit:read');
