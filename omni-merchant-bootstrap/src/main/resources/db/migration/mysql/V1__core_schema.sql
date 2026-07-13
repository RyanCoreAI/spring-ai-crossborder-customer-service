-- ==============================================================================
-- OmniMerchant - 跨境电商智能客服 Agent 平台
-- MySQL 业务库建表脚本 (db_main.sql)
-- 数据库版本: MySQL 8.0+
-- 字符集: utf8mb4_unicode_ci (支持 emoji 和多语言)
-- 存储引擎: InnoDB
-- ==============================================================================

-- 创建数据库

-- 关闭外键检查方便重建

-- ==============================================================================
-- 表 1: tenant (租户/店铺表)
-- ==============================================================================
-- 业务背景:
--   每个跨境电商店铺一个租户。一个店铺主可能有多家店铺(同一邮箱注册不同店),
--   所以 owner_email 不唯一。租户支持订阅升级、API 凭证存储、月度预算等。
--   每个租户绑定一个外部平台店铺 ID,平台+店铺ID 是业务唯一键。
-- ==============================================================================
CREATE TABLE `tenant` (
  `id`                       BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT       COMMENT '租户ID(主键)',
  `tenant_code`              VARCHAR(64)      NOT NULL                       COMMENT '租户业务编码(对外展示,如 OM-A1B2C3),全局唯一',

  -- ===== 店铺基本信息 =====
  `store_name`               VARCHAR(128)     NOT NULL                       COMMENT '店铺名称(用户可见)',
  `store_logo_url`           VARCHAR(512)     DEFAULT NULL                   COMMENT '店铺 Logo URL',
  `store_description`        VARCHAR(1024)    DEFAULT NULL                   COMMENT '店铺简介',
  `industry`                 VARCHAR(64)      DEFAULT NULL                   COMMENT '行业类目(electronics/apparel/home/...)',
  `country_code`             VARCHAR(8)       DEFAULT NULL                   COMMENT '店铺归属国家 ISO-3166(US/CN/UK)',
  `timezone`                 VARCHAR(64)      DEFAULT 'UTC'                  COMMENT '店铺时区(Asia/Shanghai)',
  `currency`                 VARCHAR(8)       DEFAULT 'USD'                  COMMENT '默认结算币种 ISO-4217',

  -- ===== 平台对接 =====
  `platform`                 VARCHAR(32)      NOT NULL                       COMMENT '平台:shopify/amazon/woocommerce/tiktok_shop/temu/shein/aliexpress/custom',
  `external_store_id`        VARCHAR(128)     NOT NULL                       COMMENT '平台店铺ID(如 Shopify shop domain: xxx.myshopify.com)',
  `external_store_url`       VARCHAR(512)     DEFAULT NULL                   COMMENT '店铺前台 URL',
  `webhook_secret`           VARCHAR(256)     DEFAULT NULL                   COMMENT 'Webhook HMAC 签名密钥(加密存储)',
  `api_credentials_encrypted` TEXT            DEFAULT NULL                   COMMENT '平台 API 凭证(AES-256 加密,JSON 格式存 token/refresh_token/expires_at)',
  `api_credentials_updated_at` DATETIME       DEFAULT NULL                   COMMENT '凭证最后更新时间(用于刷新提醒)',

  -- ===== 店主信息 =====
  `owner_name`               VARCHAR(128)     DEFAULT NULL                   COMMENT '店主姓名',
  `owner_email`              VARCHAR(128)     NOT NULL                       COMMENT '店主邮箱(登录用)',
  `owner_phone`              VARCHAR(32)      DEFAULT NULL                   COMMENT '店主手机(含国家码,如 +86138...)',
  `owner_country`            VARCHAR(8)       DEFAULT NULL                   COMMENT '店主国籍',

  -- ===== AI 客服配置 =====
  `default_lang`             VARCHAR(8)       NOT NULL DEFAULT 'en'          COMMENT '客服默认语言(en/es/ja/...)',
  `support_langs`            JSON             DEFAULT NULL                   COMMENT '支持的语言列表 ["en","es","fr"]',
  `auto_reply_enabled`       TINYINT(1)       NOT NULL DEFAULT 1             COMMENT '是否启用 AI 自动回复:0否1是',
  `business_hours`           JSON             DEFAULT NULL                   COMMENT '工作时段配置 [{"day":1,"start":"09:00","end":"18:00"}]',
  `escalation_threshold`     DECIMAL(3,2)     NOT NULL DEFAULT 0.75          COMMENT '人工升级置信度阈值[0,1]',
  `escalation_amount_limit`  DECIMAL(15,4)    DEFAULT 100.0000               COMMENT '金额超此值自动升级(美元)',
  `welcome_message`          VARCHAR(1024)    DEFAULT NULL                   COMMENT '欢迎语模板(可含变量 {customer_name})',
  `signature`                VARCHAR(512)     DEFAULT NULL                   COMMENT 'AI 回复签名(如 "— Powered by OmniMerchant")',

  -- ===== 订阅 & 计费 =====
  `subscription_plan`        VARCHAR(32)      NOT NULL DEFAULT 'FREE'        COMMENT '订阅:FREE/BASIC/PRO/ENTERPRISE/CUSTOM',
  `subscription_started_at`  DATETIME         DEFAULT NULL                   COMMENT '订阅生效时间',
  `subscription_expires_at`  DATETIME         DEFAULT NULL                   COMMENT '订阅到期时间(NULL=永久)',
  `monthly_token_budget`     BIGINT           NOT NULL DEFAULT 100000        COMMENT '月度Token预算上限',
  `monthly_message_quota`    INT              NOT NULL DEFAULT 1000          COMMENT '月度消息条数上限',
  `qps_limit`                INT              NOT NULL DEFAULT 5             COMMENT 'API 请求 QPS 上限',
  `concurrent_session_limit` INT              NOT NULL DEFAULT 50            COMMENT '同时在线会话数上限',

  -- ===== 状态 & 审计 =====
  `status`                   TINYINT          NOT NULL DEFAULT 1             COMMENT '0:停用 1:启用 2:试用中 3:欠费暂停 4:封禁',
  `status_reason`            VARCHAR(256)     DEFAULT NULL                   COMMENT '当前状态原因',
  `last_active_at`           DATETIME         DEFAULT NULL                   COMMENT '最后活跃时间',
  `ext_attr`                 JSON             DEFAULT NULL                   COMMENT '扩展属性(避免频繁加字段)',
  `remark`                   VARCHAR(512)     DEFAULT NULL                   COMMENT '内部备注',

  `is_deleted`               TINYINT(1)       NOT NULL DEFAULT 0             COMMENT '逻辑删除:0否1是',
  `version`                  INT              NOT NULL DEFAULT 0             COMMENT '乐观锁版本号',
  `created_by`               BIGINT           DEFAULT NULL                   COMMENT '创建人ID(后台用户)',
  `updated_by`               BIGINT           DEFAULT NULL                   COMMENT '更新人ID',
  `created_at`               DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)         COMMENT '创建时间',
  `updated_at`               DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code`        (`tenant_code`)                                          COMMENT '租户编码全局唯一',
  UNIQUE KEY `uk_platform_store`     (`platform`, `external_store_id`, `is_deleted`)          COMMENT '同平台店铺ID唯一(支持删除后复用)',
  KEY        `idx_owner_email`       (`owner_email`)                                          COMMENT '店主登录查询',
  KEY        `idx_status`            (`status`, `is_deleted`)                                 COMMENT '状态过滤',
  KEY        `idx_subscription`      (`subscription_plan`, `subscription_expires_at`)         COMMENT '订阅到期检查',
  KEY        `idx_last_active`       (`last_active_at`)                                       COMMENT '活跃统计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户/店铺表';


-- ==============================================================================
-- 表 2: customer (终端客户表)
-- ==============================================================================
-- 业务背景:
--   买家(终端客户)在不同店铺的身份是隔离的,所以同一个 email 在不同 tenant 下
--   会有不同的 customer 记录。但同 tenant 下,平台客户ID应唯一。
--   这张表是会话和订单的客户主体,缓存平台客户信息减少 API 调用。
-- ==============================================================================
CREATE TABLE `customer` (
  `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT     COMMENT '客户ID',
  `tenant_id`           BIGINT UNSIGNED  NOT NULL                     COMMENT '租户ID',
  `external_customer_id` VARCHAR(128)    NOT NULL                     COMMENT '平台客户ID',

  -- ===== 客户基本信息 =====
  `email`               VARCHAR(128)     DEFAULT NULL                 COMMENT '邮箱(主联系方式)',
  `phone`               VARCHAR(32)      DEFAULT NULL                 COMMENT '手机(含国家码)',
  `first_name`          VARCHAR(64)      DEFAULT NULL                 COMMENT '名',
  `last_name`           VARCHAR(64)      DEFAULT NULL                 COMMENT '姓',
  `display_name`        VARCHAR(128)     DEFAULT NULL                 COMMENT '显示名(优先用)',
  `avatar_url`          VARCHAR(512)     DEFAULT NULL                 COMMENT '头像 URL',

  -- ===== 客户画像 =====
  `country_code`        VARCHAR(8)       DEFAULT NULL                 COMMENT '客户所在国 ISO-3166',
  `state_province`      VARCHAR(64)      DEFAULT NULL                 COMMENT '州/省',
  `city`                VARCHAR(64)      DEFAULT NULL                 COMMENT '城市',
  `language_pref`       VARCHAR(8)       DEFAULT NULL                 COMMENT '客户偏好语言(基于历史对话推断)',
  `timezone`            VARCHAR(64)      DEFAULT NULL                 COMMENT '客户时区',
  `currency_pref`       VARCHAR(8)       DEFAULT NULL                 COMMENT '客户偏好币种',

  -- ===== 客户价值统计 =====
  `total_orders`        INT              NOT NULL DEFAULT 0           COMMENT '历史订单总数',
  `total_spent`         DECIMAL(15,4)    NOT NULL DEFAULT 0.0000      COMMENT '累计消费金额(以 tenant.currency 计)',
  `avg_order_value`     DECIMAL(15,4)    NOT NULL DEFAULT 0.0000      COMMENT '平均客单价',
  `last_order_at`       DATETIME         DEFAULT NULL                 COMMENT '最后下单时间',
  `customer_tier`       VARCHAR(16)      DEFAULT NULL                 COMMENT '客户等级:NEW/REGULAR/VIP/CHURN',

  -- ===== 客服历史 =====
  `total_conversations` INT              NOT NULL DEFAULT 0           COMMENT '累计会话数',
  `total_complaints`    INT              NOT NULL DEFAULT 0           COMMENT '累计投诉次数',
  `satisfaction_avg`    DECIMAL(3,2)     DEFAULT NULL                 COMMENT '平均满意度[1,5]',
  `last_contact_at`     DATETIME         DEFAULT NULL                 COMMENT '最后联系时间',
  `is_blacklisted`      TINYINT(1)       NOT NULL DEFAULT 0           COMMENT '是否拉黑(恶意/欺诈):0否1是',
  `blacklist_reason`    VARCHAR(256)     DEFAULT NULL                 COMMENT '拉黑原因',

  -- ===== 同步信息 =====
  `synced_at`           DATETIME         DEFAULT NULL                 COMMENT '从平台最后同步时间',
  `sync_status`         TINYINT          NOT NULL DEFAULT 0           COMMENT '同步状态:0未同步 1成功 2失败',
  `sync_error`          VARCHAR(512)     DEFAULT NULL                 COMMENT '同步失败原因',

  `ext_attr`            JSON             DEFAULT NULL                 COMMENT '扩展属性(平台原始 customer 数据缓存)',
  `is_deleted`          TINYINT(1)       NOT NULL DEFAULT 0           COMMENT '逻辑删除',
  `version`             INT              NOT NULL DEFAULT 0           COMMENT '乐观锁',
  `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_external`    (`tenant_id`, `external_customer_id`)         COMMENT '租户内平台客户ID唯一',
  KEY        `idx_tenant_email`      (`tenant_id`, `email`)                         COMMENT '邮箱查询',
  KEY        `idx_tenant_phone`      (`tenant_id`, `phone`)                         COMMENT '手机查询',
  KEY        `idx_tenant_tier`       (`tenant_id`, `customer_tier`)                 COMMENT '按等级筛选',
  KEY        `idx_tenant_blacklist`  (`tenant_id`, `is_blacklisted`)                COMMENT '黑名单过滤',
  KEY        `idx_tenant_last_order` (`tenant_id`, `last_order_at`)                 COMMENT '客户活跃度排序',
  KEY        `idx_synced_at`         (`synced_at`)                                  COMMENT '同步任务调度'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='终端客户表';


-- ==============================================================================
-- 表 3: conversation (会话表)
-- ==============================================================================
-- 业务背景:
--   一次客服对话 = 一个 conversation。会话有完整生命周期(开始→处理中→升级/结束),
--   会话粒度做指标统计(满意度/Token/时长)。一个客户可能有多个会话。
--   会话可关联订单(查询订单咨询)或不关联(纯商品咨询)。
-- ==============================================================================
CREATE TABLE `conversation` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT     COMMENT '会话ID',
  `conversation_uuid`    VARCHAR(64)     NOT NULL                     COMMENT '会话UUID(对外暴露,不暴露自增ID)',
  `tenant_id`            BIGINT UNSIGNED NOT NULL                     COMMENT '租户ID',
  `customer_id`          BIGINT UNSIGNED DEFAULT NULL                 COMMENT '客户ID(关联 customer.id,首次咨询可能为空)',

  -- ===== 客户身份(冗余存储,首次咨询时未关联 customer) =====
  `external_customer_id` VARCHAR(128)    DEFAULT NULL                 COMMENT '平台客户ID',
  `customer_email`       VARCHAR(128)    DEFAULT NULL                 COMMENT '客户邮箱(用于身份验证)',
  `customer_name`        VARCHAR(128)    DEFAULT NULL                 COMMENT '客户名(快照)',

  -- ===== 业务关联 =====
  `related_order_id`     VARCHAR(128)    DEFAULT NULL                 COMMENT '关联的平台订单号(可空)',
  `related_product_ids`  JSON            DEFAULT NULL                 COMMENT '咨询涉及的商品ID列表',

  -- ===== 渠道与会话属性 =====
  `channel`              VARCHAR(32)     NOT NULL DEFAULT 'WEB'       COMMENT '渠道:WEB/EMAIL/WHATSAPP/MESSENGER/PLATFORM_IM',
  `language`             VARCHAR(8)      DEFAULT NULL                 COMMENT '会话主语言(首条消息检测)',
  `intent_primary`       VARCHAR(32)     DEFAULT NULL                 COMMENT '主意图:ORDER_QUERY/LOGISTICS/REFUND/PRODUCT/COMPLAINT/GREETING/UNCLEAR',
  `intent_history`       JSON            DEFAULT NULL                 COMMENT '历史意图变化轨迹',
  `sentiment`            VARCHAR(16)     DEFAULT NULL                 COMMENT '情感倾向:POSITIVE/NEUTRAL/NEGATIVE/ANGRY',
  `sentiment_score`      DECIMAL(4,3)    DEFAULT NULL                 COMMENT '情感分[-1,1]',

  -- ===== 状态机 =====
  `status`               TINYINT         NOT NULL DEFAULT 1           COMMENT '1:进行中(AI) 2:已完成 3:已升级人工 4:人工处理中 5:已关闭 6:已超时',
  `escalated`            TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否升级人工:0否1是',
  `escalation_reason`    VARCHAR(64)     DEFAULT NULL                 COMMENT '升级原因:LOW_CONFIDENCE/AMOUNT_LIMIT/NEGATIVE_SENTIMENT/MAX_ITER/USER_REQUEST/AI_PROACTIVE',
  `escalated_at`         DATETIME(3)     DEFAULT NULL                 COMMENT '升级时间',
  `human_agent_id`       BIGINT UNSIGNED DEFAULT NULL                 COMMENT '接管的人工客服ID',
  `priority`             TINYINT         NOT NULL DEFAULT 2           COMMENT '优先级:1低 2中 3高 4紧急',

  -- ===== 统计指标(汇总更新,避免每次 SUM message) =====
  `message_count`        INT             NOT NULL DEFAULT 0           COMMENT '消息总数(user+assistant)',
  `tool_call_count`      INT             NOT NULL DEFAULT 0           COMMENT '工具调用总数',
  `total_prompt_tokens`  BIGINT          NOT NULL DEFAULT 0           COMMENT '会话累计 prompt token',
  `total_completion_tokens` BIGINT       NOT NULL DEFAULT 0           COMMENT '会话累计 completion token',
  `total_cost_usd`       DECIMAL(12,6)   NOT NULL DEFAULT 0.000000    COMMENT '会话总成本 USD',
  `first_response_ms`    INT             DEFAULT NULL                 COMMENT '首字响应时间 ms',
  `avg_response_ms`      INT             DEFAULT NULL                 COMMENT '平均响应时间 ms',

  -- ===== 满意度 =====
  `csat_score`           TINYINT         DEFAULT NULL                 COMMENT '客户满意度[1,5]',
  `csat_comment`         VARCHAR(1024)   DEFAULT NULL                 COMMENT '客户评论',
  `csat_submitted_at`    DATETIME        DEFAULT NULL                 COMMENT '评分提交时间',
  `resolved`             TINYINT(1)      DEFAULT NULL                 COMMENT '问题是否解决:NULL未知 0否 1是',

  -- ===== 时间节点 =====
  `started_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话开始时间',
  `last_message_at`      DATETIME(3)     DEFAULT NULL                 COMMENT '最后消息时间',
  `ended_at`             DATETIME(3)     DEFAULT NULL                 COMMENT '会话结束时间',
  `duration_seconds`     INT             DEFAULT NULL                 COMMENT '会话时长秒(ended-started)',

  `ext_attr`             JSON            DEFAULT NULL                 COMMENT '扩展属性',
  `tags`                 JSON            DEFAULT NULL                 COMMENT '标签 ["urgent","vip","followup"]',
  `is_deleted`           TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '逻辑删除',
  `version`              INT             NOT NULL DEFAULT 0           COMMENT '乐观锁',
  `created_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_uuid`      (`conversation_uuid`)                                       COMMENT 'UUID 唯一',
  KEY        `idx_tenant_customer`       (`tenant_id`, `customer_id`, `started_at`)                  COMMENT '客户的会话列表',
  KEY        `idx_tenant_status`         (`tenant_id`, `status`, `started_at`)                       COMMENT '按状态筛选',
  KEY        `idx_tenant_started`        (`tenant_id`, `started_at`)                                 COMMENT '时间倒序列表',
  KEY        `idx_tenant_escalated`      (`tenant_id`, `escalated`, `escalated_at`)                  COMMENT '人工工单查询',
  KEY        `idx_tenant_intent`         (`tenant_id`, `intent_primary`)                             COMMENT '按意图统计',
  KEY        `idx_human_agent`           (`human_agent_id`, `status`)                                COMMENT '客服我的工单',
  KEY        `idx_related_order`         (`tenant_id`, `related_order_id`)                           COMMENT '订单关联会话查询',
  KEY        `idx_last_message`          (`last_message_at`)                                         COMMENT '超时会话扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';


-- ==============================================================================
-- 表 4: chat_message (消息表)
-- ==============================================================================
-- 业务背景:
--   消息是写入最频繁的表。500 店铺 x 200 msg/day x 30 day = 300 万条/月。
--   生产环境可按月分表(message_yyyyMM),用 ShardingSphere 或定时任务自动建表。
--   存储原始消息 + 翻译后内容 + 工具调用结构化数据。
--   role 字段对齐 OpenAI/Anthropic 规范:user/assistant/system/tool。
-- ==============================================================================
CREATE TABLE `chat_message` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT     COMMENT '消息ID',
  `message_uuid`        VARCHAR(64)     NOT NULL                     COMMENT '消息UUID',
  `conversation_uuid`   VARCHAR(64)     NOT NULL                     COMMENT '会话UUID(冗余便于跨表查询)',
  `conversation_id`     BIGINT UNSIGNED NOT NULL                     COMMENT '会话ID',
  `tenant_id`           BIGINT UNSIGNED NOT NULL                     COMMENT '租户ID',

  -- ===== 消息基础属性 =====
  `role`                VARCHAR(16)     NOT NULL                     COMMENT '角色:user/assistant/system/tool',
  `seq_no`              INT             NOT NULL                     COMMENT '会话内消息序号(从1开始)',
  `content`             MEDIUMTEXT      NOT NULL                     COMMENT '消息内容(原文,可能含富文本/markdown)',
  `content_type`        VARCHAR(16)     NOT NULL DEFAULT 'TEXT'      COMMENT '内容类型:TEXT/IMAGE/FILE/VOICE/MARKDOWN/HTML',
  `attachments`         JSON            DEFAULT NULL                 COMMENT '附件列表 [{"type":"image","url":"...","size":1024}]',

  -- ===== 多语言相关 =====
  `original_lang`       VARCHAR(8)      DEFAULT NULL                 COMMENT '原始语言',
  `translated_content`  MEDIUMTEXT      DEFAULT NULL                 COMMENT '翻译为英语后的内容(用于 LLM 处理)',
  `translation_lang`    VARCHAR(8)      DEFAULT NULL                 COMMENT '翻译目标语言',
  `is_translated`       TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否翻译过',

  -- ===== 工具调用相关(role=assistant 时可能有) =====
  `tool_calls`          JSON            DEFAULT NULL                 COMMENT '工具调用列表 [{"id":"xxx","name":"queryOrder","args":{...}}]',
  `tool_call_id`        VARCHAR(64)     DEFAULT NULL                 COMMENT '本消息是哪次工具调用的结果(role=tool 时)',
  `tool_name`           VARCHAR(64)     DEFAULT NULL                 COMMENT '工具名(role=tool 时)',

  -- ===== LLM 元数据(role=assistant 时) =====
  `model_provider`      VARCHAR(32)     DEFAULT NULL                 COMMENT 'LLM 提供商:openai/anthropic/deepseek',
  `model_name`          VARCHAR(64)     DEFAULT NULL                 COMMENT '具体模型名:gpt-4o-mini/claude-haiku-4-5',
  `prompt_tokens`       INT             NOT NULL DEFAULT 0           COMMENT '输入 token 数',
  `completion_tokens`   INT             NOT NULL DEFAULT 0           COMMENT '输出 token 数',
  `total_tokens`        INT             NOT NULL DEFAULT 0           COMMENT '总 token 数',
  `cost_usd`            DECIMAL(12,8)   NOT NULL DEFAULT 0.00000000  COMMENT '本条消息成本 USD(精度高,单条可能很小)',
  `latency_ms`          INT             DEFAULT NULL                 COMMENT '生成耗时 ms',
  `ttfb_ms`             INT             DEFAULT NULL                 COMMENT '首字延迟 ms(流式)',
  `finish_reason`       VARCHAR(32)     DEFAULT NULL                 COMMENT '终止原因:stop/length/tool_calls/content_filter',
  `confidence`          DECIMAL(4,3)    DEFAULT NULL                 COMMENT '置信度[0,1](意图分类/工具选择)',

  -- ===== 安全 & 审计 =====
  `is_filtered`         TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否被过滤(敏感内容):0否1是',
  `filter_reason`       VARCHAR(128)    DEFAULT NULL                 COMMENT '过滤原因:PII/INJECTION/TOXIC/SPAM',
  `is_streamed`         TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否流式输出',

  -- ===== ReAct 迭代追踪 =====
  `iteration_index`     TINYINT         DEFAULT NULL                 COMMENT 'ReAct 第几轮迭代(1-5)',
  `parent_message_id`   BIGINT UNSIGNED DEFAULT NULL                 COMMENT '父消息ID(工具结果指向触发它的 assistant 消息)',

  `ext_attr`            JSON            DEFAULT NULL                 COMMENT '扩展属性',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  -- 注意:消息表不需要 updated_at(消息一旦创建不可改) 也不需要逻辑删除(归档而非删除)

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_uuid`      (`message_uuid`)                                COMMENT '消息UUID唯一',
  UNIQUE KEY `uk_conv_seq`          (`conversation_id`, `seq_no`)                   COMMENT '会话内序号唯一',
  KEY        `idx_conversation`     (`conversation_uuid`, `created_at`)             COMMENT '加载会话历史(最常用)',
  KEY        `idx_tenant_created`   (`tenant_id`, `created_at`)                     COMMENT '租户消息流',
  KEY        `idx_tenant_role`      (`tenant_id`, `role`, `created_at`)             COMMENT '统计 role 分布',
  KEY        `idx_tool_call`        (`tool_call_id`)                                COMMENT '工具调用追踪',
  KEY        `idx_model`            (`model_name`, `created_at`)                    COMMENT '模型用量统计',
  KEY        `idx_filtered`         (`is_filtered`, `created_at`)                   COMMENT '安全审计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';


-- ==============================================================================
-- 表 5: order_info (订单信息缓存表)
-- ==============================================================================
-- 业务背景:
--   不存储完整订单系统(那是平台的事),只缓存 AI 客服可能用到的字段。
--   订单数据通过定时同步 + 实时查询 + Webhook 推送三种方式更新。
--   存储 order_items JSON 而非独立表,因为客服场景只读不写,JSON 查询足够。
--   tracking_history 存物流轨迹 JSON,用于 trackLogistics 工具直接返回。
-- ==============================================================================
CREATE TABLE `order_info` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '订单本地ID',
  `tenant_id`             BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `external_order_id`     VARCHAR(128)    NOT NULL                  COMMENT '平台订单号',
  `external_order_number` VARCHAR(64)     DEFAULT NULL              COMMENT '订单展示号(如 #1001,客户常用此搜索)',
  `platform`              VARCHAR(32)     NOT NULL                  COMMENT '平台',

  -- ===== 客户信息(冗余便于查询) =====
  `customer_id`           BIGINT UNSIGNED DEFAULT NULL              COMMENT '本地客户ID',
  `external_customer_id`  VARCHAR(128)    DEFAULT NULL              COMMENT '平台客户ID',
  `customer_email`        VARCHAR(128)    DEFAULT NULL              COMMENT '客户邮箱(下单时填写,可能与 customer.email 不同)',
  `customer_name`         VARCHAR(128)    DEFAULT NULL              COMMENT '收件人姓名',
  `customer_phone`        VARCHAR(32)     DEFAULT NULL              COMMENT '收件人电话',

  -- ===== 收货地址(JSON 存完整结构) =====
  `shipping_address`      JSON            DEFAULT NULL              COMMENT '收货地址 {country,state,city,street,zip}',
  `shipping_country`      VARCHAR(8)      DEFAULT NULL              COMMENT '收货国家(独立字段便于索引)',
  `shipping_state`        VARCHAR(64)     DEFAULT NULL              COMMENT '州/省',
  `shipping_zip`          VARCHAR(32)     DEFAULT NULL              COMMENT '邮编',
  `billing_address`       JSON            DEFAULT NULL              COMMENT '账单地址',

  -- ===== 订单状态 =====
  `order_status`          VARCHAR(32)     NOT NULL                  COMMENT '订单状态:pending/paid/processing/shipped/delivered/cancelled/refunded/returned',
  `payment_status`        VARCHAR(32)     DEFAULT NULL              COMMENT '支付状态:unpaid/paid/partially_paid/refunded/partially_refunded',
  `fulfillment_status`    VARCHAR(32)     DEFAULT NULL              COMMENT '履约状态:unfulfilled/partial/fulfilled',

  -- ===== 金额字段(全部 DECIMAL 防止精度丢失) =====
  `currency`              VARCHAR(8)      NOT NULL DEFAULT 'USD'    COMMENT '订单币种',
  `subtotal_amount`       DECIMAL(15,4)   DEFAULT NULL              COMMENT '商品小计',
  `shipping_amount`       DECIMAL(15,4)   DEFAULT NULL              COMMENT '运费',
  `tax_amount`            DECIMAL(15,4)   DEFAULT NULL              COMMENT '税费',
  `discount_amount`       DECIMAL(15,4)   DEFAULT NULL              COMMENT '折扣金额',
  `total_amount`          DECIMAL(15,4)   NOT NULL                  COMMENT '订单总金额(客户实付)',
  `refunded_amount`       DECIMAL(15,4)   NOT NULL DEFAULT 0.0000   COMMENT '已退款金额',

  -- ===== 商品列表(JSON,客服查询不需要 JOIN) =====
  `order_items`           JSON            DEFAULT NULL              COMMENT '商品列表 [{"sku","title","quantity","price","image_url"}]',
  `item_count`            INT             NOT NULL DEFAULT 0        COMMENT '商品种类数',
  `total_quantity`        INT             NOT NULL DEFAULT 0        COMMENT '商品总件数',

  -- ===== 物流信息 =====
  `tracking_number`       VARCHAR(128)    DEFAULT NULL              COMMENT '物流单号(主)',
  `tracking_numbers`      JSON            DEFAULT NULL              COMMENT '多包裹物流单号列表',
  `tracking_carrier`      VARCHAR(64)     DEFAULT NULL              COMMENT '承运商:USPS/UPS/DHL/FedEx/YunExpress',
  `tracking_url`          VARCHAR(512)    DEFAULT NULL              COMMENT '物流追踪 URL',
  `tracking_status`       VARCHAR(64)     DEFAULT NULL              COMMENT '物流状态:in_transit/out_for_delivery/delivered/exception',
  `tracking_history`      JSON            DEFAULT NULL              COMMENT '物流轨迹 [{time,location,status,desc}]',
  `tracking_updated_at`   DATETIME        DEFAULT NULL              COMMENT '物流最后更新时间',
  `estimated_delivery_at` DATETIME        DEFAULT NULL              COMMENT '预计送达时间',
  `actual_delivery_at`    DATETIME        DEFAULT NULL              COMMENT '实际送达时间',

  -- ===== 标签和优惠 =====
  `tags`                  JSON            DEFAULT NULL              COMMENT '订单标签 ["vip","wholesale"]',
  `discount_codes`        JSON            DEFAULT NULL              COMMENT '使用的优惠码',
  `note`                  VARCHAR(1024)   DEFAULT NULL              COMMENT '客户下单备注',

  -- ===== 时间节点 =====
  `placed_at`             DATETIME        NOT NULL                  COMMENT '下单时间(平台数据)',
  `paid_at`               DATETIME        DEFAULT NULL              COMMENT '支付时间',
  `shipped_at`            DATETIME        DEFAULT NULL              COMMENT '发货时间',
  `cancelled_at`          DATETIME        DEFAULT NULL              COMMENT '取消时间',

  -- ===== 同步信息 =====
  `synced_at`             DATETIME        DEFAULT NULL              COMMENT '从平台最后同步时间',
  `sync_source`           VARCHAR(16)     DEFAULT NULL              COMMENT '同步来源:WEBHOOK/POLL/MANUAL',
  `sync_version`          INT             NOT NULL DEFAULT 0        COMMENT '同步次数(每次+1)',

  `ext_attr`              JSON            DEFAULT NULL              COMMENT '扩展(平台原始数据快照)',
  `is_deleted`            TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '逻辑删除',
  `version`               INT             NOT NULL DEFAULT 0        COMMENT '乐观锁',
  `created_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_order`         (`tenant_id`, `external_order_id`)                   COMMENT '租户内订单号唯一',
  KEY        `idx_tenant_order_number` (`tenant_id`, `external_order_number`)               COMMENT '客户常用订单展示号搜索',
  KEY        `idx_tenant_email`        (`tenant_id`, `customer_email`, `placed_at`)         COMMENT '按邮箱查订单',
  KEY        `idx_tenant_customer`     (`tenant_id`, `customer_id`, `placed_at`)            COMMENT '客户的订单列表',
  KEY        `idx_tenant_status`       (`tenant_id`, `order_status`, `placed_at`)           COMMENT '按状态筛选',
  KEY        `idx_tenant_placed`       (`tenant_id`, `placed_at`)                           COMMENT '时间倒序',
  KEY        `idx_tracking_number`     (`tracking_number`)                                  COMMENT '物流单号反查订单',
  KEY        `idx_synced_at`           (`synced_at`)                                        COMMENT '同步任务调度',
  KEY        `idx_phone`               (`tenant_id`, `customer_phone`)                      COMMENT '电话查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单信息缓存表';


-- ==============================================================================
-- 表 6: product (商品表)
-- ==============================================================================
-- 业务背景:
--   商品也是缓存平台数据,主要用于 RAG 检索和工具查询。
--   一个 product 可能有多个 SKU(变体),variants 字段存所有变体,简化模型。
--   vector_synced 标记是否已索引到 PGVector,避免重复 embedding。
--   全文索引 ft_title_desc 支持基础关键词搜索作为向量检索的退化方案。
-- ==============================================================================
CREATE TABLE `product` (
  `id`                       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '商品ID',
  `tenant_id`                BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `external_product_id`      VARCHAR(128)    NOT NULL                  COMMENT '平台商品ID',
  `handle`                   VARCHAR(255)    DEFAULT NULL              COMMENT '商品 URL slug(如 cotton-tshirt)',

  -- ===== 商品核心信息 =====
  `title`                    VARCHAR(512)    NOT NULL                  COMMENT '商品标题',
  `subtitle`                 VARCHAR(512)    DEFAULT NULL              COMMENT '副标题',
  `description`              TEXT            DEFAULT NULL              COMMENT '商品详情(可能含 HTML)',
  `description_plain`        TEXT            DEFAULT NULL              COMMENT '商品详情纯文本(去 HTML,用于 embedding)',
  `vendor`                   VARCHAR(128)    DEFAULT NULL              COMMENT '供应商/品牌',
  `brand`                    VARCHAR(128)    DEFAULT NULL              COMMENT '品牌',
  `product_type`             VARCHAR(128)    DEFAULT NULL              COMMENT '商品类型',

  -- ===== 分类 =====
  `category_l1`              VARCHAR(64)     DEFAULT NULL              COMMENT '一级类目',
  `category_l2`              VARCHAR(64)     DEFAULT NULL              COMMENT '二级类目',
  `category_l3`              VARCHAR(64)     DEFAULT NULL              COMMENT '三级类目',
  `categories`               JSON            DEFAULT NULL              COMMENT '完整分类路径数组',
  `tags`                     JSON            DEFAULT NULL              COMMENT '标签数组',

  -- ===== SKU & 变体(用 JSON 存所有变体) =====
  `default_sku`              VARCHAR(128)    DEFAULT NULL              COMMENT '默认 SKU(简化场景使用)',
  `barcode`                  VARCHAR(64)     DEFAULT NULL              COMMENT '条形码/UPC/EAN',
  `variants`                 JSON            DEFAULT NULL              COMMENT '变体列表 [{"sku","options":{"color":"red","size":"M"},"price","stock","barcode"}]',
  `variant_count`            INT             NOT NULL DEFAULT 1        COMMENT '变体数量',
  `options_schema`           JSON            DEFAULT NULL              COMMENT '选项定义 [{"name":"Color","values":["red","blue"]}]',

  -- ===== 价格 & 库存 =====
  `currency`                 VARCHAR(8)      NOT NULL DEFAULT 'USD'    COMMENT '币种',
  `price`                    DECIMAL(12,4)   DEFAULT NULL              COMMENT '默认价格',
  `compare_at_price`         DECIMAL(12,4)   DEFAULT NULL              COMMENT '划线价(原价)',
  `cost_per_item`            DECIMAL(12,4)   DEFAULT NULL              COMMENT '成本价(店主可见)',
  `total_stock`              INT             NOT NULL DEFAULT 0        COMMENT '总库存',
  `stock_status`             VARCHAR(16)     DEFAULT NULL              COMMENT 'in_stock/low_stock/out_of_stock/preorder',

  -- ===== 物理属性(运费/退货政策依据) =====
  `weight_grams`             INT             DEFAULT NULL              COMMENT '重量(克)',
  `dimensions`               JSON            DEFAULT NULL              COMMENT '尺寸 {length,width,height,unit}',
  `requires_shipping`        TINYINT(1)      NOT NULL DEFAULT 1        COMMENT '是否需要发货:0数字商品 1实物',
  `is_taxable`               TINYINT(1)      NOT NULL DEFAULT 1        COMMENT '是否计税',

  -- ===== 媒体资源 =====
  `featured_image_url`       VARCHAR(1024)   DEFAULT NULL              COMMENT '主图 URL',
  `images`                   JSON            DEFAULT NULL              COMMENT '所有图片 URL 数组',
  `videos`                   JSON            DEFAULT NULL              COMMENT '视频 URL 数组',

  -- ===== 多语言(用于 RAG 检索时按语言匹配) =====
  `language`                 VARCHAR(8)      DEFAULT 'en'              COMMENT '商品默认语言',
  `translations`             JSON            DEFAULT NULL              COMMENT '多语言翻译 {"es":{"title","description"},"ja":{...}}',

  -- ===== SEO & 评价(辅助 RAG) =====
  `seo_title`                VARCHAR(255)    DEFAULT NULL,
  `seo_description`          VARCHAR(512)    DEFAULT NULL,
  `keywords`                 JSON            DEFAULT NULL              COMMENT 'SEO 关键词',
  `rating_avg`               DECIMAL(3,2)    DEFAULT NULL              COMMENT '平均评分[1,5]',
  `rating_count`             INT             NOT NULL DEFAULT 0        COMMENT '评价总数',
  `review_summary`           TEXT            DEFAULT NULL              COMMENT '评价摘要(用于 RAG)',

  -- ===== 向量索引状态 =====
  `vector_synced`            TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否已索引到 PGVector',
  `vector_chunk_count`       INT             NOT NULL DEFAULT 0        COMMENT '索引的 chunk 数',
  `vector_synced_at`         DATETIME        DEFAULT NULL              COMMENT '最后向量化时间',
  `content_hash`             CHAR(32)        DEFAULT NULL              COMMENT '内容MD5(变化才重新 embedding)',

  -- ===== 状态 =====
  `status`                   TINYINT         NOT NULL DEFAULT 1        COMMENT '0草稿 1已发布 2已归档 3已售罄下架',
  `published_at`             DATETIME        DEFAULT NULL              COMMENT '上架时间',
  `synced_at`                DATETIME        DEFAULT NULL              COMMENT '从平台同步时间',

  `ext_attr`                 JSON            DEFAULT NULL              COMMENT '扩展(原始平台数据)',
  `is_deleted`               TINYINT(1)      NOT NULL DEFAULT 0,
  `version`                  INT             NOT NULL DEFAULT 0,
  `created_at`               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`               DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_product`     (`tenant_id`, `external_product_id`)                COMMENT '租户内商品ID唯一',
  KEY        `idx_tenant_handle`     (`tenant_id`, `handle`)                              COMMENT 'URL slug 查询',
  KEY        `idx_tenant_sku`        (`tenant_id`, `default_sku`)                         COMMENT 'SKU 查询',
  KEY        `idx_tenant_category`   (`tenant_id`, `category_l1`, `category_l2`)          COMMENT '类目筛选',
  KEY        `idx_tenant_status`     (`tenant_id`, `status`, `published_at`)              COMMENT '上架状态',
  KEY        `idx_vector_sync`       (`tenant_id`, `vector_synced`, `updated_at`)         COMMENT '待索引商品扫描',
  KEY        `idx_brand`             (`tenant_id`, `brand`)                               COMMENT '品牌筛选',
  FULLTEXT KEY `ft_title_desc`       (`title`, `description_plain`)                       COMMENT '全文检索退化方案'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';


-- ==============================================================================
-- 表 7: knowledge_doc (知识库文档表)
-- ==============================================================================
-- 业务背景:
--   存储店铺上传的政策、FAQ、说明手册等结构化知识。
--   doc 是文档元信息,实际切分后的 chunk 存在 PGVector 中。
--   支持文档版本管理(政策更新时保留旧版),version 字段递增。
--   parent_doc_id 支持父子关系(如多语言版本共享 parent)。
-- ==============================================================================
CREATE TABLE `knowledge_doc` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT     COMMENT '文档ID',
  `doc_uuid`          VARCHAR(64)     NOT NULL                     COMMENT '文档UUID(对外暴露)',
  `tenant_id`         BIGINT UNSIGNED NOT NULL                     COMMENT '租户ID',

  -- ===== 文档分类 =====
  `doc_type`          VARCHAR(32)     NOT NULL                     COMMENT '文档类型:REFUND_POLICY/SHIPPING_POLICY/FAQ/PRODUCT_GUIDE/PRIVACY_POLICY/TERMS_OF_SERVICE/CUSTOM',
  `doc_category`      VARCHAR(64)     DEFAULT NULL                 COMMENT '业务分类(用户自定义)',
  `priority`          INT             NOT NULL DEFAULT 0           COMMENT '检索优先级(数字大优先)',

  -- ===== 文档元信息 =====
  `title`             VARCHAR(255)    NOT NULL                     COMMENT '文档标题',
  `summary`           VARCHAR(1024)   DEFAULT NULL                 COMMENT '文档摘要(可手填或 LLM 生成)',
  `language`          VARCHAR(8)      NOT NULL DEFAULT 'en'        COMMENT '文档语言',
  `tags`              JSON            DEFAULT NULL                 COMMENT '文档标签',

  -- ===== 内容来源 =====
  `source_type`       VARCHAR(16)     NOT NULL                     COMMENT '来源:UPLOAD/URL/MANUAL/SYSTEM',
  `source_trust_level` VARCHAR(32)    NOT NULL DEFAULT 'MEDIUM'    COMMENT '来源可信度:LOW/MEDIUM/HIGH/TRUSTED',
  `source_url`        VARCHAR(1024)   DEFAULT NULL                 COMMENT '原始 URL(若来自爬取)',
  `source_file_name`  VARCHAR(255)    DEFAULT NULL                 COMMENT '原始文件名',
  `source_file_size`  BIGINT          DEFAULT NULL                 COMMENT '文件大小(字节)',
  `source_mime_type`  VARCHAR(64)     DEFAULT NULL                 COMMENT 'MIME 类型',
  `file_storage_path` VARCHAR(512)    DEFAULT NULL                 COMMENT '原文件存储路径(OSS/S3)',

  -- ===== 内容 =====
  `raw_content`       LONGTEXT        DEFAULT NULL                 COMMENT '原始文本内容(切分前)',
  `content_hash`      CHAR(32)        DEFAULT NULL                 COMMENT '内容 MD5(变化才重新切分)',
  `char_count`        INT             NOT NULL DEFAULT 0           COMMENT '字符数',

  -- ===== 切分 & 索引 =====
  `chunk_size`        INT             NOT NULL DEFAULT 500         COMMENT '切分块大小(字符)',
  `chunk_overlap`     INT             NOT NULL DEFAULT 50          COMMENT '块重叠字符数',
  `chunk_count`       INT             NOT NULL DEFAULT 0           COMMENT '实际切分块数',
  `vector_synced`     TINYINT(1)      NOT NULL DEFAULT 0           COMMENT '是否已向量化',
  `vector_synced_at`  DATETIME        DEFAULT NULL                 COMMENT '向量化完成时间',
  `index_error`       VARCHAR(1024)   DEFAULT NULL                 COMMENT '索引失败原因',

  -- ===== 版本管理 =====
  `parent_doc_id`     BIGINT UNSIGNED DEFAULT NULL                 COMMENT '父文档ID(版本/翻译关系)',
  `doc_version`       INT             NOT NULL DEFAULT 1           COMMENT '文档版本(每次内容变更+1)',
  `is_latest`         TINYINT(1)      NOT NULL DEFAULT 1           COMMENT '是否最新版本',
  `effective_from`    DATETIME        DEFAULT NULL                 COMMENT '政策生效起始时间',
  `effective_until`   DATETIME        DEFAULT NULL                 COMMENT '政策生效终止时间',

  -- ===== 状态 =====
  `status`            TINYINT         NOT NULL DEFAULT 1           COMMENT '0草稿 1已发布 2索引中 3索引失败 4已归档',
  `published_at`      DATETIME        DEFAULT NULL                 COMMENT '发布时间',
  `approved_by`       BIGINT          DEFAULT NULL                 COMMENT '审核人',
  `approved_at`       DATETIME        DEFAULT NULL                 COMMENT '审核时间',

  -- ===== 使用统计 =====
  `retrieval_count`   INT             NOT NULL DEFAULT 0           COMMENT '被检索次数',
  `last_retrieved_at` DATETIME        DEFAULT NULL                 COMMENT '最后被检索时间',

  `ext_attr`          JSON            DEFAULT NULL,
  `is_deleted`        TINYINT(1)      NOT NULL DEFAULT 0,
  `version`           INT             NOT NULL DEFAULT 0           COMMENT '乐观锁',
  `created_by`        BIGINT          DEFAULT NULL,
  `updated_by`        BIGINT          DEFAULT NULL,
  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_uuid`            (`doc_uuid`),
  KEY        `idx_tenant_type`        (`tenant_id`, `doc_type`, `is_latest`)          COMMENT '按类型查最新',
  KEY        `idx_tenant_status`      (`tenant_id`, `status`)                          COMMENT '索引任务扫描',
  KEY        `idx_tenant_lang`        (`tenant_id`, `language`, `doc_type`)            COMMENT '多语言文档查询',
  KEY        `idx_parent_doc`         (`parent_doc_id`)                                COMMENT '版本族查询',
  KEY        `idx_vector_sync`        (`vector_synced`, `updated_at`)                  COMMENT '待索引扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';


-- ==============================================================================
-- 表 8: tool_call_log (工具调用日志表)
-- ==============================================================================
-- 业务背景:
--   每次 LLM 调用工具都记录,用于:
--   1) 调试:追踪 ReAct 链路
--   2) 计费:工具调用次数计费
--   3) 优化:统计成功率/延迟/失败原因
--   4) 安全:审计敏感操作
--   写入量大但读取相对少,可考虑后期分表或归档到 OLAP 系统(如 ClickHouse)。
-- ==============================================================================
CREATE TABLE `tool_call_log` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '日志ID',
  `trace_id`          VARCHAR(64)     NOT NULL                  COMMENT '链路追踪ID',
  `span_id`           VARCHAR(64)     DEFAULT NULL              COMMENT 'Span ID(若集成 OpenTelemetry)',
  `tenant_id`         BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `conversation_uuid` VARCHAR(64)     NOT NULL                  COMMENT '会话UUID',
  `message_uuid`      VARCHAR(64)     DEFAULT NULL              COMMENT '关联的 assistant 消息UUID',

  -- ===== 工具调用信息 =====
  `tool_call_id`      VARCHAR(64)     NOT NULL                  COMMENT '工具调用ID(LLM 生成)',
  `tool_name`         VARCHAR(64)     NOT NULL                  COMMENT '工具名',
  `tool_version`      VARCHAR(16)     DEFAULT 'v1'              COMMENT '工具版本(支持灰度)',
  `params`            JSON            DEFAULT NULL              COMMENT '入参 JSON',
  `params_hash`       CHAR(32)        DEFAULT NULL              COMMENT '入参MD5(用于缓存命中判断)',

  -- ===== 执行结果 =====
  `success`           TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否成功',
  `result`            JSON            DEFAULT NULL              COMMENT '返回结果',
  `result_size_bytes` INT             DEFAULT NULL              COMMENT '返回数据大小(便于发现异常大返回)',
  `error_code`        VARCHAR(64)     DEFAULT NULL              COMMENT '错误码',
  `error_message`     VARCHAR(2048)   DEFAULT NULL              COMMENT '错误信息',
  `error_stack`       TEXT            DEFAULT NULL              COMMENT '异常堆栈(仅 ERROR 级别才存)',

  -- ===== 性能指标 =====
  `started_at`        DATETIME(3)     NOT NULL                  COMMENT '开始时间',
  `ended_at`          DATETIME(3)     DEFAULT NULL              COMMENT '结束时间',
  `latency_ms`        INT             DEFAULT NULL              COMMENT '耗时 ms',
  `cache_hit`         TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否命中缓存',

  -- ===== 重试相关 =====
  `retry_count`       INT             NOT NULL DEFAULT 0        COMMENT '重试次数',
  `is_retry`          TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '本次是否是重试',
  `original_call_id`  VARCHAR(64)     DEFAULT NULL              COMMENT '首次调用ID(若是重试)',

  -- ===== 上下文 =====
  `iteration_index`   TINYINT         DEFAULT NULL              COMMENT 'ReAct 第几轮',
  `triggered_by_model` VARCHAR(64)    DEFAULT NULL              COMMENT '触发本次调用的 LLM 模型',
  `client_ip`         VARCHAR(64)     DEFAULT NULL              COMMENT '客户端 IP',

  `created_at`        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  -- 工具调用日志只追加,不需要 updated_at / 逻辑删除

  PRIMARY KEY (`id`),
  KEY        `idx_trace`           (`trace_id`)                                       COMMENT '链路追踪',
  KEY        `idx_tenant_tool`     (`tenant_id`, `tool_name`, `created_at`)           COMMENT '工具调用统计',
  KEY        `idx_tenant_success`  (`tenant_id`, `success`, `created_at`)             COMMENT '失败率统计',
  KEY        `idx_conversation`    (`conversation_uuid`, `created_at`)                COMMENT '会话工具链',
  KEY        `idx_message`         (`message_uuid`)                                   COMMENT '消息关联工具',
  KEY        `idx_created`         (`created_at`)                                     COMMENT '时间范围扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用日志';


-- ==============================================================================
-- 表 9: token_usage_daily (Token 用量日表)
-- ==============================================================================
-- 业务背景:
--   按 (租户, 日期, 模型) 维度聚合 token 用量。每天的数据更新而非追加。
--   月度计费查询时按 SUM 聚合,避免扫描 message 表(数据量太大)。
--   新一天的数据通过 INSERT...ON DUPLICATE KEY UPDATE 累加。
-- ==============================================================================
CREATE TABLE `token_usage_daily` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '主键',
  `tenant_id`           BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `usage_date`          DATE            NOT NULL                  COMMENT '使用日期',
  `model_provider`      VARCHAR(32)     NOT NULL                  COMMENT '提供商',
  `model_name`          VARCHAR(64)     NOT NULL                  COMMENT '模型名',
  `model_type`          VARCHAR(16)     NOT NULL DEFAULT 'CHAT'   COMMENT 'CHAT/EMBEDDING/RERANK',

  -- ===== Token 用量 =====
  `prompt_tokens`       BIGINT          NOT NULL DEFAULT 0        COMMENT 'Prompt token 累计',
  `completion_tokens`   BIGINT          NOT NULL DEFAULT 0        COMMENT 'Completion token 累计',
  `total_tokens`        BIGINT          NOT NULL DEFAULT 0        COMMENT '总 token',
  `cached_tokens`       BIGINT          NOT NULL DEFAULT 0        COMMENT '命中 prompt cache 的 token(便宜)',

  -- ===== 成本(以租户币种和 USD 都存) =====
  `cost_usd`            DECIMAL(15,8)   NOT NULL DEFAULT 0        COMMENT '成本 USD',
  `cost_cny`            DECIMAL(15,8)   NOT NULL DEFAULT 0        COMMENT '成本 CNY',

  -- ===== 调用统计 =====
  `request_count`       INT             NOT NULL DEFAULT 0        COMMENT '调用次数',
  `success_count`       INT             NOT NULL DEFAULT 0        COMMENT '成功次数',
  `error_count`         INT             NOT NULL DEFAULT 0        COMMENT '失败次数',
  `timeout_count`       INT             NOT NULL DEFAULT 0        COMMENT '超时次数',
  `rate_limit_count`    INT             NOT NULL DEFAULT 0        COMMENT '限流次数',

  -- ===== 性能指标 =====
  `total_latency_ms`    BIGINT          NOT NULL DEFAULT 0        COMMENT '总延迟 ms(用于算平均)',
  `max_latency_ms`      INT             DEFAULT NULL              COMMENT '最大延迟',

  `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_date_model` (`tenant_id`, `usage_date`, `model_name`)         COMMENT '聚合维度唯一',
  KEY        `idx_tenant_date`      (`tenant_id`, `usage_date`)                       COMMENT '租户日报',
  KEY        `idx_date`             (`usage_date`)                                    COMMENT '全平台日报',
  KEY        `idx_provider`         (`model_provider`, `usage_date`)                  COMMENT '提供商对账'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Token用量日聚合表';


-- ==============================================================================
-- 表 10: human_agent (人工客服表)
-- ==============================================================================
-- 业务背景:
--   接管 AI 升级会话的人工客服。一个客服可服务多个租户(平台员工)
--   或属于单一租户(店铺自有客服)。online_status 决定派单。
-- ==============================================================================
CREATE TABLE `human_agent` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '客服ID',
  `agent_code`            VARCHAR(64)     NOT NULL                  COMMENT '客服工号',
  `tenant_id`             BIGINT UNSIGNED DEFAULT NULL              COMMENT '所属租户(NULL=平台共享客服)',

  -- ===== 基本信息 =====
  `name`                  VARCHAR(64)     NOT NULL                  COMMENT '客服姓名',
  `display_name`          VARCHAR(64)     DEFAULT NULL              COMMENT '对客户显示名',
  `email`                 VARCHAR(128)    NOT NULL                  COMMENT '邮箱(登录用)',
  `phone`                 VARCHAR(32)     DEFAULT NULL,
  `avatar_url`            VARCHAR(512)    DEFAULT NULL,
  `password_hash`         VARCHAR(128)    DEFAULT NULL              COMMENT 'BCrypt 密码哈希',

  -- ===== 能力配置 =====
  `supported_langs`       JSON            DEFAULT NULL              COMMENT '支持的语言列表',
  `supported_intents`     JSON            DEFAULT NULL              COMMENT '擅长的意图类型',
  `skill_tags`            JSON            DEFAULT NULL              COMMENT '技能标签',
  `level`                 TINYINT         NOT NULL DEFAULT 1        COMMENT '等级:1初级 2中级 3高级 4专家',
  `max_concurrent`        INT             NOT NULL DEFAULT 5        COMMENT '最大同时处理会话数',

  -- ===== 状态 =====
  `online_status`         VARCHAR(16)     NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/BUSY/AWAY/OFFLINE',
  `current_load`          INT             NOT NULL DEFAULT 0        COMMENT '当前处理中会话数',
  `last_active_at`        DATETIME(3)     DEFAULT NULL              COMMENT '最后活跃时间',
  `auto_assign`           TINYINT(1)      NOT NULL DEFAULT 1        COMMENT '是否自动派单',

  -- ===== 工作时段 =====
  `work_schedule`         JSON            DEFAULT NULL              COMMENT '排班 [{"day":1,"start":"09:00","end":"18:00"}]',
  `timezone`              VARCHAR(64)     DEFAULT 'UTC',

  -- ===== 绩效统计 =====
  `total_handled`         INT             NOT NULL DEFAULT 0        COMMENT '累计处理会话数',
  `avg_response_seconds`  INT             DEFAULT NULL              COMMENT '平均响应秒数',
  `avg_handle_seconds`    INT             DEFAULT NULL              COMMENT '平均处理秒数',
  `csat_avg`              DECIMAL(3,2)    DEFAULT NULL              COMMENT '平均满意度',
  `resolved_rate`         DECIMAL(4,3)    DEFAULT NULL              COMMENT '一次解决率[0,1]',

  -- ===== 状态 =====
  `is_active`             TINYINT(1)      NOT NULL DEFAULT 1        COMMENT '账号是否启用',
  `last_login_at`         DATETIME        DEFAULT NULL,
  `last_login_ip`         VARCHAR(64)     DEFAULT NULL,

  `ext_attr`              JSON            DEFAULT NULL,
  `is_deleted`            TINYINT(1)      NOT NULL DEFAULT 0,
  `version`               INT             NOT NULL DEFAULT 0,
  `created_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_code`        (`agent_code`),
  UNIQUE KEY `uk_email`             (`email`, `is_deleted`),
  KEY        `idx_tenant_status`    (`tenant_id`, `online_status`, `current_load`)    COMMENT '派单查询(找空闲客服)',
  KEY        `idx_tenant_active`    (`tenant_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工客服表';


-- ==============================================================================
-- 表 11: escalation_record (人工升级工单表)
-- ==============================================================================
-- 业务背景:
--   AI 升级到人工时创建工单,记录升级原因、派单、SLA、处理结果。
--   一个 conversation 可能多次升级(人工又交还 AI),所以是 1:N 关系。
-- ==============================================================================
CREATE TABLE `escalation_record` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '工单ID',
  `ticket_no`           VARCHAR(64)     NOT NULL                  COMMENT '工单编号(对外展示)',
  `tenant_id`           BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `conversation_uuid`   VARCHAR(64)     NOT NULL                  COMMENT '会话UUID',
  `customer_id`         BIGINT UNSIGNED DEFAULT NULL              COMMENT '客户ID',

  -- ===== 升级原因 =====
  `escalation_type`     VARCHAR(32)     NOT NULL                  COMMENT 'AUTO_AI/AI_PROACTIVE/USER_REQUEST',
  `escalation_reason`   VARCHAR(64)     NOT NULL                  COMMENT 'LOW_CONFIDENCE/AMOUNT_LIMIT/NEGATIVE_SENTIMENT/MAX_ITER/TOOL_FAILURE/USER_REQUEST',
  `reason_detail`       VARCHAR(1024)   DEFAULT NULL              COMMENT '详细原因',
  `confidence_score`    DECIMAL(4,3)    DEFAULT NULL              COMMENT '触发时的置信度',
  `sentiment_score`     DECIMAL(4,3)    DEFAULT NULL              COMMENT '触发时的情感分',
  `involved_amount`     DECIMAL(15,4)   DEFAULT NULL              COMMENT '涉及金额',
  `currency`            VARCHAR(8)      DEFAULT NULL,
  `summary`             TEXT            DEFAULT NULL              COMMENT 'AI 生成的会话摘要(给人工看)',
  `customer_intent`     VARCHAR(32)     DEFAULT NULL              COMMENT '客户主诉意图',

  -- ===== 优先级 & 派单 =====
  `priority`            TINYINT         NOT NULL DEFAULT 2        COMMENT '1低 2中 3高 4紧急',
  `assigned_agent_id`   BIGINT UNSIGNED DEFAULT NULL              COMMENT '分配的客服ID',
  `assigned_at`         DATETIME(3)     DEFAULT NULL              COMMENT '分配时间',
  `assignment_strategy` VARCHAR(32)     DEFAULT NULL              COMMENT 'AUTO_LEAST_BUSY/MANUAL/SKILL_MATCH',

  -- ===== SLA =====
  `sla_response_seconds` INT            NOT NULL DEFAULT 300      COMMENT 'SLA 响应时长',
  `sla_resolve_seconds`  INT            NOT NULL DEFAULT 3600     COMMENT 'SLA 解决时长',
  `sla_response_due_at`  DATETIME       DEFAULT NULL              COMMENT 'SLA 响应截止时间',
  `sla_resolve_due_at`   DATETIME       DEFAULT NULL              COMMENT 'SLA 解决截止时间',
  `sla_response_breached` TINYINT(1)    NOT NULL DEFAULT 0        COMMENT '响应是否违约',
  `sla_resolve_breached`  TINYINT(1)    NOT NULL DEFAULT 0        COMMENT '解决是否违约',

  -- ===== 状态 =====
  `status`              TINYINT         NOT NULL DEFAULT 1        COMMENT '1待分配 2待响应 3处理中 4已解决 5已关闭 6已取消',
  `first_response_at`   DATETIME(3)     DEFAULT NULL              COMMENT '首次响应时间',
  `resolved_at`         DATETIME(3)     DEFAULT NULL              COMMENT '解决时间',
  `closed_at`           DATETIME(3)     DEFAULT NULL              COMMENT '关闭时间',
  `resolution`          VARCHAR(32)     DEFAULT NULL              COMMENT '解决方式:RESOLVED/REFUNDED/REPLACED/EXPLAINED/UNRESOLVED',
  `resolution_note`     TEXT            DEFAULT NULL              COMMENT '解决备注',

  -- ===== 客户反馈 =====
  `csat_score`          TINYINT         DEFAULT NULL              COMMENT '客户满意度',
  `csat_comment`        VARCHAR(1024)   DEFAULT NULL,

  -- ===== 二次升级 =====
  `parent_ticket_id`    BIGINT UNSIGNED DEFAULT NULL              COMMENT '父工单ID(二次升级)',
  `escalated_back_to_ai` TINYINT(1)     NOT NULL DEFAULT 0        COMMENT '是否再次交回 AI',

  `tags`                JSON            DEFAULT NULL,
  `ext_attr`            JSON            DEFAULT NULL,
  `version`             INT             NOT NULL DEFAULT 0,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no`         (`ticket_no`),
  KEY        `idx_tenant_status`    (`tenant_id`, `status`, `priority`, `created_at`)   COMMENT '工单池查询',
  KEY        `idx_assigned`         (`assigned_agent_id`, `status`)                     COMMENT '客服我的工单',
  KEY        `idx_conversation`     (`conversation_uuid`)                               COMMENT '会话工单关联',
  KEY        `idx_sla_breach`       (`tenant_id`, `sla_response_breached`, `sla_resolve_breached`)  COMMENT 'SLA 监控',
  KEY        `idx_priority_created` (`priority`, `created_at`)                          COMMENT '紧急工单优先'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工升级工单表';


-- ==============================================================================
-- 表 12: webhook_event (Webhook 事件表)
-- ==============================================================================
-- 业务背景:
--   接收平台 Webhook 后先入库,异步处理。这样可以:
--   1) 幂等(基于 event_uuid)
--   2) 失败重试(状态机)
--   3) 审计/回放(出问题可重新处理)
--   raw_payload 必须保留,处理逻辑变更后可重新跑。
-- ==============================================================================
CREATE TABLE `webhook_event` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT     COMMENT '事件ID',
  `event_uuid`      VARCHAR(128)    NOT NULL                     COMMENT '事件唯一ID(平台提供或本地生成)',
  `tenant_id`       BIGINT UNSIGNED DEFAULT NULL                 COMMENT '租户ID(可能为空,需根据 store_id 解析)',
  `platform`        VARCHAR(32)     NOT NULL                     COMMENT '来源平台',
  `external_store_id` VARCHAR(128)  DEFAULT NULL                 COMMENT '平台店铺ID',

  -- ===== 事件信息 =====
  `event_type`      VARCHAR(64)     NOT NULL                     COMMENT '事件类型:order/created,order/updated,fulfillment/created,...',
  `event_source`    VARCHAR(32)     NOT NULL DEFAULT 'WEBHOOK'   COMMENT 'WEBHOOK/POLL/MANUAL',
  `topic`           VARCHAR(128)    DEFAULT NULL                 COMMENT '原始 topic 名(平台规范)',
  `resource_type`   VARCHAR(32)     DEFAULT NULL                 COMMENT 'order/customer/product/inventory',
  `resource_id`     VARCHAR(128)    DEFAULT NULL                 COMMENT '资源ID',

  -- ===== 请求信息 =====
  `request_headers` JSON            DEFAULT NULL                 COMMENT '原始请求头(便于验签)',
  `signature`       VARCHAR(512)    DEFAULT NULL                 COMMENT '签名',
  `signature_valid` TINYINT(1)      DEFAULT NULL                 COMMENT '签名是否有效',
  `client_ip`       VARCHAR(64)     DEFAULT NULL,
  `raw_payload`     LONGTEXT        NOT NULL                     COMMENT '原始 payload(必须保留)',
  `payload_size`    INT             DEFAULT NULL                 COMMENT 'payload 大小(字节)',

  -- ===== 处理状态 =====
  `status`          TINYINT         NOT NULL DEFAULT 0           COMMENT '0待处理 1处理中 2成功 3失败 4已忽略 5重试中',
  `process_attempts` INT            NOT NULL DEFAULT 0           COMMENT '处理尝试次数',
  `last_error`      VARCHAR(2048)   DEFAULT NULL                 COMMENT '最后失败原因',
  `processed_at`    DATETIME(3)     DEFAULT NULL                 COMMENT '处理完成时间',
  `next_retry_at`   DATETIME        DEFAULT NULL                 COMMENT '下次重试时间(指数退避)',

  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_uuid`        (`platform`, `event_uuid`)                    COMMENT '幂等键',
  KEY        `idx_tenant_type`      (`tenant_id`, `event_type`, `created_at`)     COMMENT '租户事件流',
  KEY        `idx_status_retry`     (`status`, `next_retry_at`)                   COMMENT '重试任务扫描',
  KEY        `idx_platform_store`   (`platform`, `external_store_id`, `created_at`) COMMENT '店铺事件查询',
  KEY        `idx_resource`         (`resource_type`, `resource_id`)              COMMENT '资源关联事件'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 事件表';


-- ==============================================================================
-- 表 13: rate_limit_record (限流记录/审计表)
-- ==============================================================================
-- 业务背景:
--   实际限流在 Redis 做(原子+性能),此表只记录被限流的事件用于:
--   1) 客户告警(连续被限可能需要升级套餐)
--   2) 销售推荐(数据驱动 upsell)
--   3) 安全审计(异常请求模式)
--   不影响主流程,异步写入。
-- ==============================================================================
CREATE TABLE `rate_limit_record` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
  `tenant_id`       BIGINT UNSIGNED NOT NULL                  COMMENT '租户ID',
  `limit_type`      VARCHAR(32)     NOT NULL                  COMMENT 'QPS/MONTHLY_BUDGET/MODEL_CONCURRENT/MESSAGE_QUOTA',
  `limit_key`       VARCHAR(128)    NOT NULL                  COMMENT '限流 Key',
  `limit_value`     BIGINT          NOT NULL                  COMMENT '限制阈值',
  `current_value`   BIGINT          NOT NULL                  COMMENT '当前值',
  `requested_value` BIGINT          DEFAULT NULL              COMMENT '本次请求量(token 数等)',

  `model_name`      VARCHAR(64)     DEFAULT NULL              COMMENT '模型名(模型并发限流时)',
  `client_ip`       VARCHAR(64)     DEFAULT NULL,
  `user_agent`      VARCHAR(512)    DEFAULT NULL,
  `request_path`    VARCHAR(255)    DEFAULT NULL,
  `trace_id`        VARCHAR(64)     DEFAULT NULL,

  `action_taken`    VARCHAR(32)     NOT NULL                  COMMENT 'REJECTED/QUEUED/THROTTLED/DEGRADED',
  `notified`        TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否已通知店主',

  `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  KEY        `idx_tenant_type`     (`tenant_id`, `limit_type`, `created_at`)        COMMENT '租户限流统计',
  KEY        `idx_tenant_notified` (`tenant_id`, `notified`, `created_at`)          COMMENT '待通知扫描',
  KEY        `idx_created`         (`created_at`)                                   COMMENT '日期范围查询(归档用)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流记录表';


-- ==============================================================================
-- 恢复外键检查
-- ==============================================================================


-- ==============================================================================
-- 初始化数据(用于开发/测试)
-- ==============================================================================
INSERT INTO `tenant` (
  `tenant_code`, `store_name`, `platform`, `external_store_id`,
  `owner_email`, `default_lang`, `support_langs`,
  `subscription_plan`, `monthly_token_budget`, `qps_limit`,
  `escalation_threshold`
) VALUES (
  'OM-DEMO001', 'OmniMerchant Demo Store', 'shopify', 'omnidemo.myshopify.com',
  'demo@omnimerchant.com', 'en', '["en","es","ja","zh","de","fr"]',
  'PRO', 5000000, 50,
  0.75
);
