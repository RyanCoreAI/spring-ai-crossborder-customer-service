# OmniMerchant v4 代码质量审计

## 结论

本轮对仓库 Java、Vue、TypeScript 和 SQL 文件做了全仓静态盘点，并深读认证、租户、Agent、RAG、客服工作流、运营统计、前端路由和首页数据链路。项目适合继续采用模块化单体，不需要为了展示技术拆成微服务；本轮已经处理主要超大服务、页面、DTO 容器和前端宽泛类型热点。

## 登录身份模型

- `ADMIN_EMAIL` / `ADMIN_PASSWORD` 只在首次启动时创建数据库平台管理员，密码使用 BCrypt 存储。
- 商户员工也是后台账号，但必须通过 `user_tenant_membership` 绑定租户及角色。
- 后台角色由服务端 JWT 派生，前端不允许自行选择或提交角色。
- 买家不登录后台，只使用 `/widget` 创建的短期 `WIDGET_CUSTOMER` token。

## 本轮已修复

1. 首页统计改为读取 `/api/operations/summary` 的真实意图和渠道维度，使用 ECharts 环形图；没有后端数据时显示空状态。
2. 登录页明确区分平台人员、商户员工和买家入口，后台顶部继续展示服务端签发的真实角色。
3. 导航配置从 `AdminLayout.vue` 抽到 `adminNavigation.ts`，宏回复改为独立页面，删除未引用的旧 `CommercialOpsView.vue`。
4. 将旧 `escalation_record` 到正式 `ticket` / QA 的兼容投影抽到 `HelpdeskProjectionService`，统一 SLA 状态为 `NORMAL / DUE_SOON / BREACHED`。
5. 运营和 SLO 聚合抽到 `OperationsAnalyticsService`，`CommercialOpsService` 只保留兼容门面，定时 SRE 评估直接依赖统计服务。
6. 删除没有业务实现的 `omni-merchant-intent` 空 Maven 模块；真正的意图路由仍由 Agent 编排模块负责。
7. 认证和首页关键 DTO 开始使用显式 TypeScript 类型，并新增统一 HTTP 错误提取工具。
8. 将 1202 行 `CommerceDtos` 按基础交易、评测、观测、渠道集成、Helpdesk 和生产治理分成 6 个契约容器；HTTP JSON 字段保持兼容。
9. 前端 `any` 使用从 148 处降为 0，24 个旧 API 页面全部改为显式导入后端契约对应的 TypeScript 类型。

## 本轮结构治理结果

| 原热点 | 原规模 | 当前结果 |
|---|---:|---|---|
| `ShopifyIntegrationService.java` | 1633 行 | 降至 760 行；OAuth、Webhook 协议、Webhook 投影分别进入独立服务 |
| `CommercialOpsService.java` | 1096 行 | 降至 781 行；审批、QA、审计、发布就绪度分离 |
| `AgentEvalRunnerService.java` | 870 行 | 降至 799 行；报告映射和阈值策略分离 |
| `RagWorkbenchView.vue` | 959 行 | 降至 543 行；候选表和治理面板独立并使用显式 DTO 类型 |
| `InboxView.vue` | 773 行 | 降至 672 行；业务上下文面板独立并对应后端 DTO |
| `EvalsView.vue` | 754 行 | 降至 560 行；GOLD 用例/数据集/审核弹窗独立 |
| `ObservabilityView.vue` | 737 行 | 降至 650 行；工具质量和评测趋势表独立 |
| `CommerceDtos.java` | 1202 行 | 降至 193 行；其余记录按 eval、observability、integration、helpdesk、governance 分域 |

前端 API 页面现已全部显式引用 `@/types/` 契约，质量门禁不再使用历史预算。

## 明确不做的重构

- 不引入 Spring Modulith、LangGraph 或微服务作为装饰性依赖。
- 不用一个万能索引类型替代业务 DTO；只有配置驱动的通用资源表使用 `Record<string, unknown>`。
- 不移除 `escalation_record`，它仍是旧接口兼容来源；新统计和工作流只以 `ticket` 为事实模型。
- 不用前端静态数据填充图表，演示数据必须由 Flyway demo seed 写入后端数据库。

## 下一轮质量门禁

- 新增页面必须先定义后端 DTO 和 TypeScript 类型。
- 新统计不得在请求时隐式创建/回填业务记录。
- 单个应用服务超过 800 行时必须说明职责或拆分。
- 单个 Vue 页面超过 700 行时优先拆可测试组件和 composable。
- 任何旧表兼容逻辑必须放在明确的 projection/adapter 层。

门禁已固化为 `scripts/verify-code-quality.ps1` 并接入 CI。宽泛 TypeScript 类型和未显式引入 DTO 类型的 API 页面预算均为 0；新增页面不得扩大预算。
