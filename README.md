# OmniMerchant

[![CI](https://github.com/RyanCoreAI/spring-ai-crossborder-customer-service/actions/workflows/ci.yml/badge.svg)](https://github.com/RyanCoreAI/spring-ai-crossborder-customer-service/actions/workflows/ci.yml)
[![CodeQL](https://github.com/RyanCoreAI/spring-ai-crossborder-customer-service/actions/workflows/codeql.yml/badge.svg)](https://github.com/RyanCoreAI/spring-ai-crossborder-customer-service/actions/workflows/codeql.yml)

跨境电商多语言 AI 客服平台。基于 Spring AI Tool Calling / ReAct 风格编排，提供买家聊天入口、商家客服工作台、订单查询、物流追踪、商品推荐、退换货政策 RAG、退货/退款审批请求、多语言翻译、人工升级、Shopify 接入骨架、租户隔离、用量计费和 Agent 评测。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.5 |
| AI | Spring AI (OpenAI / Anthropic / DeepSeek) | 1.0.9 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL 8.0 / PostgreSQL 16 + pgvector | — |
| 缓存 | Redis 7 | — |
| 消息 | RocketMQ | 5.1 |
| 熔断 | Resilience4j | 2.2.0 |
| 前端 | Vue 3 + Element Plus + TypeScript | 3.5 / 2.9 / 5.7 |
| 构建 | Vite 6 / Maven 3.9 | — |

## 项目结构

```
omnimerchant/
├── omni-merchant-common/       # 共享模块：DTO、异常、JWT 工具、TraceId
├── omni-merchant-tenant/       # 租户管理：CRUD、多租户上下文、拦截器
├── omni-merchant-agent/        # Agent 核心：ReAct、工具调用、模型路由、限流、计费
├── omni-merchant-intent/       # 意图识别模块
├── omni-merchant-knowledge/    # 知识库：RAG 混合检索、文档管理、向量索引
├── omni-merchant-channel/      # 渠道接入模块
├── omni-merchant-message/      # 消息模块：RocketMQ 消费 Token 用量
├── omni-merchant-bootstrap/    # 启动模块：配置、过滤器、全局异常处理
├── omnimerchant-web/           # Vue 3 前端：聊天、管理后台
├── sql/                        # 建表脚本（MySQL + PGVector）
├── docker-compose.yml          # 本地开发中间件
└── Dockerfile                  # 后端多阶段构建
```

## 快速启动

> v1 收口标准：fresh clone 后按本节启动，管理员登录、Widget session、Widget SSE、租户 fail-closed smoke 和前端 build 都能复现。完整验收清单见 [`docs/v1-release-checklist.md`](docs/v1-release-checklist.md)。

### 环境要求

- Java 17+
- Maven 3.8+
- Docker Desktop
- OpenAI / Anthropic / DeepSeek API Key（可选；不配置时后台、订单、商品、工单和 eval 可运行，AI 聊天返回配置提示）

### 1. 启动中间件

```bash
cp .env.example .env
# 填写 .env 中的 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD、PG_PASSWORD、ADMIN_PASSWORD、JWT_SECRET、INTEGRATION_ENCRYPTION_KEY
docker-compose up -d
```

启动 MySQL、Redis、PostgreSQL (pgvector)、RocketMQ (namesrv + broker + dashboard)。
如果本机已有 MySQL 占用 `3306`，可改用 `MYSQL_PORT=13306 docker compose up -d mysql redis postgres rocketmq-namesrv rocketmq-broker`，后端启动时同步设置 `MYSQL_PORT=13306`。

### 2. 初始化数据库

```bash
# MySQL 建表
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_main.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_extensions.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/demo_seed.sql

# PGVector 建表
docker exec -i omni-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sql/db_vector.sql
```

也可以使用脚本执行完整 demo 初始化：

```powershell
.\scripts\demo.ps1
```

Linux/macOS:

```bash
./scripts/demo.sh
```

### 3. 配置环境变量

```bash
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD='set-a-strong-password'
export JWT_SECRET='set-a-strong-256-bit-secret'
export INTEGRATION_ENCRYPTION_KEY="$(openssl rand -base64 32)"

# 无 LLM key 的本地 demo：禁用 Spring AI 自动模型，后台和业务数据仍可体验
export SPRING_AI_MODEL_CHAT=none
export SPRING_AI_MODEL_EMBEDDING=none
export SPRING_AI_MODEL_AUDIO_SPEECH=none
export SPRING_AI_MODEL_AUDIO_TRANSCRIPTION=none
export SPRING_AI_MODEL_IMAGE=none
export SPRING_AI_MODEL_MODERATION=none

# 启用 AI chat/RAG 时再配置：
# export SPRING_AI_MODEL_CHAT=openai
# export SPRING_AI_MODEL_EMBEDDING=openai
# export OPENAI_API_KEY=sk-your-key
# export ANTHROPIC_API_KEY=sk-your-key      # 可选
# export DEEPSEEK_API_KEY=sk-your-key       # 可选
```

### 4. 启动后端

```bash
mvn compile -pl omni-merchant-bootstrap -am
mvn -pl omni-merchant-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=dev
```

应用默认运行在 `http://localhost:8090`。

### 5. 启动前端（可选）

```bash
cd omnimerchant-web
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，API 请求自动代理到 `localhost:8090`。本地调试时请固定使用同一个 host；如果浏览器打开 `http://127.0.0.1:5188`，就不要混用 `http://localhost:5188` 的旧页面或 token。

### 6. 验证

```bash
# 健康检查
curl http://localhost:8090/api/health

# 管理员登录
curl -X POST http://localhost:8090/api/admin/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"set-a-strong-password"}'

# 测试对话
curl -X POST http://localhost:8090/api/test/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_FROM_LOGIN>" \
  -H "X-Tenant-Id: 1" \
  -d '{"message":"hello"}'
```

### 7. 本地质量门

```bash
mvn -q test
mvn -q -DskipTests package

cd omnimerchant-web
npm ci
npm run build
npm audit --omit=dev --audit-level=high

cd ..
docker compose config --quiet
```

Testcontainers 集成回归默认不跑，需要 Docker：

```bash
mvn -q -Pintegration verify
```

运行 deterministic Agent eval：

```powershell
.\scripts\run-evals.ps1
```

该 eval runner 是基于 seed 数据和业务服务的 keyless deterministic checker；真实 LLM 输出质量评测仍属于后续 v2/CI 扩展项。

输出：

- `reports/agent-eval-report.json`
- `reports/agent-eval-report.md`

## API 概览

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| POST | `/api/admin/login` | 管理员登录，返回 JWT |
| POST | `/api/widget/session` | 创建买家公开聊天会话，返回 2 小时 `customerSessionToken` |
| POST | `/api/widget/chat/stream` | 买家公开 SSE 对话，需携带 `Authorization: Bearer <customerSessionToken>` |
| POST | `/api/webhooks/shopify` | Shopify Webhook 验签与入库 |

### 管理接口（需 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tenants` | 租户列表 |
| POST | `/api/tenants` | 创建租户 |
| GET/PUT/DELETE | `/api/tenants/{id}` | 租户详情/更新/删除 |
| GET | `/api/knowledge/docs` | 知识文档列表 |
| POST | `/api/knowledge/docs` | 创建文档 |
| GET/PUT/DELETE | `/api/knowledge/docs/{docUuid}` | 文档详情/更新/删除 |
| GET | `/api/conversations` | 会话列表 |
| GET | `/api/conversations/{uuid}` | 会话详情 |
| GET | `/api/conversations/{uuid}/messages` | 会话消息回放 |
| GET | `/api/billing/usage` | 当月用量 |
| GET | `/api/billing/usage/range` | 按日期范围查询用量 |
| GET | `/api/customers` | 客户列表 |
| GET | `/api/orders` | 订单列表 |
| GET | `/api/orders/by-number/{orderNumber}` | 订单号查询 |
| GET | `/api/products` | 商品列表 |
| POST | `/api/products/reindex` | 标记商品待重建向量索引 |
| GET/POST | `/api/escalations` | 人工工单列表/创建 |
| PUT | `/api/escalations/{id}/assign` | 接管工单 |
| PUT | `/api/escalations/{id}/resolve` | 解决工单 |
| GET | `/api/tool-calls` | 工具调用审计 |
| GET | `/api/dashboard/commerce` | 客服运营指标 |
| POST | `/api/integrations/shopify/connect` | 保存加密 Shopify 凭证 |
| POST | `/api/integrations/shopify/sync` | 同步 Shopify 商品/客户/订单 |
| GET | `/api/evals` | Agent golden case 列表 |
| POST | `/api/evals/run` | 执行当前租户 deterministic Agent eval |

### 对话接口（需 JWT + X-Tenant-Id 头）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/stream` | SSE 流式对话（核心接口） |
| POST | `/api/test/chat` | 测试对话（非流式） |

## 核心能力

**客服工作台** — `/admin/inbox`、`/admin/orders`、`/admin/products`、`/admin/customers`、`/admin/tickets`、`/admin/integrations`、`/admin/usage`、`/admin/evals` 覆盖商家客服日常操作。

**买家 Widget** — `/widget` 公开聊天入口，不依赖管理员 JWT；创建 session 后使用短期 `WIDGET_CUSTOMER` token 绑定 tenant 与 conversation，订单敏感信息必须通过订单邮箱或手机号校验。

**ReAct Agent** — ReAct 风格工具编排，自动调用工具获取真实数据。置信度低于 75%、金额争议 > $100、强负面情绪或客户请求人工时升级人工。

**9 大业务工具** — `queryOrder`、`trackLogistics`、`searchProductCatalog`、`refundPolicyRAG`、`createReturnRequest`、`requestRefundOrReplacement`、`requestAddressChange`、`translate`、`escalateToHuman`。查询类可自动执行；退款、补发、改地址只创建内部审批请求，不让 LLM 直接修改外部平台。

**Demo 数据闭环** — `sql/demo_seed.sql` 提供 2 个租户、10 个客户、20 个商品、30 个订单、物流轨迹、政策文档和 10 条 Agent 评测用例。推荐演示问题：

- `Where is my order #1001? My email is ava@example.com.`
- `Can I return my rain jacket from #1002? lucia@example.es`
- `Recommend a waterproof travel backpack under $80.`
- `I am angry because tracking VL2004US is late.`

**混合检索 RAG** — HNSW 向量检索 + BM25 关键词检索 + RRF 融合 + Cross-Encoder BGE Reranker 重排序。支持退换货政策和商品信息双知识库。

**多语言** — Lingua 自动识别 12 种语言，中转英语处理（非英语→翻译为英语→LLM 处理→翻译回原语言），降低 Token 成本。

**模型路由** — 根据意图和复杂度自动选择模型：简单请求走 gpt-4o-mini（低成本），中等复杂度走 claude-haiku-4-5（降级），兜底走 deepseek-chat（最低成本）。

**三层限流** — Token 速率限制（Redis Lua 令牌桶）→ 模型并发限制（信号量）→ 熔断降级（Resilience4j）。Redis 或租户限流状态不可用时默认拒绝付费 LLM 调用，避免 fail-open。

**多租户隔离** — JWT claims 绑定平台管理员或租户授权，X-Tenant-Id 必须通过 membership 校验后才写入上下文；MyBatis-Plus TenantLineInnerInterceptor 自动注入 WHERE tenant_id = ?，缺租户上下文时拒绝业务表 SQL。PGVector 查询手动带 tenant_id。

### v1 Scope Notes

- Shopify 是 Custom App token + webhook HMAC + 同步导入骨架，不是完整 Shopify App Store 安装流程，也不会由 LLM 直接执行退款/取消/改地址等外部写操作。
- Agent eval 是 deterministic service-level 回归，用来证明工具选择边界、身份校验和拒绝策略；端到端 LLM judge/在线指标看板留到 v2。
- Testcontainers profile 已提供 MySQL、Redis、PostgreSQL/pgvector 回归入口；本地 Docker 不可用时默认单测和 package 不依赖真实外部服务。

## Security model

- **认证先行**：`/api/chat/**`、`/api/test/**`、`/api/knowledge/**`、`/api/conversations/**`、`/api/billing/**` 都必须携带 Bearer JWT。
- **租户授权**：JWT 中包含 `role`、`tenantIds`、`platformAdmin`。普通租户用户只能访问 token membership 内的 `X-Tenant-Id`；平台管理员显式使用 `platformAdmin=true`。
- **公开 Widget 会话**：`/api/widget/session` 公开创建短期客户 token；`/api/widget/chat/stream` 校验 token 中的 `role=WIDGET_CUSTOMER`、`tenantIds`、`tenantCode` 和 `conversationUuid`，不再只信任请求体。
- **Fail closed**：缺 `X-Tenant-Id` 返回 400，JWT 无效返回 401，tenant mismatch 返回 403；tenant-scoped SQL 缺租户上下文直接拒绝。
- **付费 LLM 保护**：限流依赖 Redis Lua + 租户预算；Redis 或租户限流状态不可用时拒绝请求，不自动放行。
- **流式韧性**：SSE LLM 调用使用 Reactor timeout、有限重试和 Resilience4j Reactor circuit breaker，并在流结束时清理 tenant/call context。
- **LLM 输出处理**：前端 Markdown 渲染使用 DOMPurify allowlist 清洗后再进入 `v-html`，阻断模型输出 HTML/事件属性注入。
- **工具边界**：订单、物流、商品、退货和人工升级工具都走租户隔离服务并写入 `tool_call_log`；退款、改地址等高风险动作只创建内部审批/工单，不让 LLM 直接改外部系统。

完整说明见 [`docs/security-hardening.md`](docs/security-hardening.md)。

## 架构与公开展示

- 架构图与数据边界：[`docs/architecture.md`](docs/architecture.md)
- OpenAPI 草案：[`docs/openapi.yaml`](docs/openapi.yaml)
- Demo 发布脚本与截图清单：[`docs/demo-launch.md`](docs/demo-launch.md)
- 开源可信度审计：[`docs/open-source-audit-2026-06-20.md`](docs/open-source-audit-2026-06-20.md)

截图生成：

```powershell
.\scripts\capture-screenshots.ps1
```

![Buyer widget](docs/assets/screenshots/widget.png)

![Merchant login](docs/assets/screenshots/login.png)

## 配置参考

### Spring AI 版本策略

当前项目基于 Spring Boot 3.2.5，默认保持 Spring AI `1.0.9` 稳定线。Spring AI 2.0.x 对应 Spring Boot 4.x；后续升级路径应先评估 Boot 3.5，再评估 Boot 4 + Spring AI 2.0，不在当前安全加固中直接跨大版本迁移。

核心配置项（`application-dev.yml`）：

```yaml
spring:
  datasource:
    druid:
      url: jdbc:mysql://localhost:3306/omni_merchant?...
      username: omnimerchant
      password: ${MYSQL_PASSWORD}
  data.redis:
    host: localhost
    port: 6379
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options: {model: gpt-4o-mini, temperature: 0.3}
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat.options: {model: claude-haiku-4-5-20251001, temperature: 0.3}

omnimerchant:
  llm.deepseek:
    api-key: ${DEEPSEEK_API_KEY}
    base-url: https://api.deepseek.com
    model: deepseek-chat
  knowledge.reranker:
    url: http://localhost:8001/rerank
    timeout-seconds: 5

admin:
  email: ${ADMIN_EMAIL:}
  password: ${ADMIN_PASSWORD:}
  jwt-secret: ${JWT_SECRET:}

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5188,http://127.0.0.1:5188}
```

完整配置参见 `omni-merchant-bootstrap/src/main/resources/application-dev.yml`。

## Docker 部署

```bash
# 构建并启动全部服务（后端 + 前端 + 中间件）
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=your-password
export JWT_SECRET=your-256-bit-secret
export INTEGRATION_ENCRYPTION_KEY=$(openssl rand -base64 32)
# 无 LLM key 先跑业务 demo；有 key 时再启用对应模型
export SPRING_AI_MODEL_CHAT=none
export SPRING_AI_MODEL_EMBEDDING=none
docker-compose up -d --build
```

服务端口：
- 前端：`80` / `443`
- 后端：`8090`
- MySQL：`3306`
- Redis：`6379`
- PostgreSQL：`5432`
- RocketMQ Dashboard：`18080`

## License

MIT
