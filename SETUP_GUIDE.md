# OmniMerchant v4 本地启动指南

本指南只描述当前仓库可复现的 Gate A。企业微信 live 和抖店测试店铺需要外部凭据，未配置时状态必须保持 `FIXTURE` 或 `WAITING_CREDENTIALS`。

## 1. 环境

- Docker Desktop
- 源码开发另需 Java 21、Maven 3.8+、Node.js 20+
- LLM key 可选；缺少 key 不影响后台、商业客服数据、确定性评测和安全门禁

## 2. 准备环境变量

```powershell
Copy-Item .env.example .env
```

至少填写：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `PG_PASSWORD`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `JWT_SECRET`，至少 32 字节
- `INTEGRATION_ENCRYPTION_KEY`，至少 32 字节

仓库没有可用于部署的默认管理员密码。`ADMIN_EMAIL/ADMIN_PASSWORD` 仅用于首次 bootstrap，密码入库前会被 BCrypt 哈希。

## 3. 一键启动

```powershell
.\scripts\demo.ps1
```

脚本执行 `compose.demo.yml`。后端启动时 Flyway 自动完成：

- MySQL `V1..V22` 迁移；
- PostgreSQL/pgvector 迁移；
- `demo` profile 的确定性 seed 数据。

无需也不应再按顺序手工执行 `sql/*.sql`。

访问：

- 管理后台：`http://localhost:5188/login`
- 买家咨询：`http://localhost:5188/widget`
- 后端健康：`http://localhost:8090/actuator/health`

## 4. 源码开发

```powershell
docker compose -f compose.demo.yml up -d mysql redis postgres rocketmq-namesrv rocketmq-broker

$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:SPRING_PROFILES_ACTIVE='local,demo'
mvn -pl omni-merchant-bootstrap -am spring-boot:run
```

另开终端：

```powershell
cd omnimerchant-web
npm ci
npm run dev -- --host 127.0.0.1 --port 5188
```

## 5. 质量门

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
mvn -q test
mvn -q -DskipTests package

cd omnimerchant-web
npm ci
npm run test
npm run build
npm audit --omit=dev --audit-level=high
npx playwright test
cd ..

docker compose -f compose.demo.yml config --quiet
.\scripts\verify-openapi.ps1
.\scripts\verify-evidence.ps1
```

Docker 可用时执行集成测试：

```powershell
mvn -q -Pintegration verify
```

## 6. 真实运行证据

启动 demo 后：

```powershell
.\scripts\run-evals.ps1
.\scripts\run-rag-evals.ps1
.\scripts\capture-screenshots.ps1
```

输出位于 `reports/` 和 `docs/assets/screenshots/`。前端截图读取真实后端 DTO；没有数据时显示空状态，不注入静态业务指标。

## 7. 常见问题

### 登录 401/403

- 确认使用 `.env` 中首次 bootstrap 的账号；旧数据库里的用户不会被环境变量反复覆盖。
- 清除当前标签页的 session storage 后重新登录。
- 管理 API 除 JWT 外还需要 `X-Tenant-Id`；租户必须属于 token membership。

### 数据库迁移失败

```powershell
docker compose -f compose.demo.yml logs app
docker compose -f compose.demo.yml ps
```

Gate A 的 fresh clone 可以重建 demo volume。不要对包含真实数据的环境执行清理；生产迁移失败按 `docs/runbooks/incident-response.md` 处理。

### AI 聊天提示模型未配置

这是无凭据 demo 的预期行为。设置对应 provider key，并将 `SPRING_AI_MODEL_CHAT` / `SPRING_AI_MODEL_EMBEDDING` 改为实际 provider 后重启应用。

## 8. 明确边界

- 企业微信运行时已具备 callbackKey、验签/AES、去重、outbox、出站重试与 receipt，但无真实凭据时不能声明 live。
- 抖店当前只有 fixture-backed cache sync；OAuth 与测试店铺只读同步是 Gate B。
- Shopify 是 connector backbone；不执行真实退款、取消、改地址或补发。
- `compose.prod.yml` 是配置边界模板，不代表已经完成托管双实例和公开生产 SLA。
