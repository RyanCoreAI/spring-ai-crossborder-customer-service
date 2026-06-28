# OmniMerchant 项目环境配置与启动指南

## 一、环境准备

### 1.1 必需软件
| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| Docker Desktop | 最新版 | https://www.docker.com/products/docker-desktop/ |
| JDK | 17+ | https://adoptium.net/ |
| Maven | 3.8+ | https://maven.apache.org/download.cgi |
| Node.js | 18+ | https://nodejs.org/ |

### 1.2 验证环境
```powershell
# 验证 Docker
docker --version
docker-compose --version

# 验证 Java
java -version

# 验证 Maven
mvn -version

# 验证 Node.js
node --version
npm --version
```

---

## 二、配置步骤

### 2.1 配置环境变量
复制 `.env.example` 为 `.env`，填入管理员账号和 JWT 密钥。AI API 密钥可稍后再配置；不配置时后台、订单、商品、工单和 eval 可运行，AI 聊天会返回配置提示。

```env
MYSQL_ROOT_PASSWORD=请设置强密码
MYSQL_PASSWORD=请设置强密码
PG_PASSWORD=请设置强密码
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=请设置强密码
JWT_SECRET=请设置至少 32 字节的随机密钥
INTEGRATION_ENCRYPTION_KEY=请设置 Base64 AES 密钥，例如 openssl rand -base64 32
OPENAI_API_KEY=sk-xxxxxx
ANTHROPIC_API_KEY=sk-ant-xxxxxx
DEEPSEEK_API_KEY=sk-xxxxxx

# 无 LLM key 的本地 demo 建议显式关闭自动模型
SPRING_AI_MODEL_CHAT=none
SPRING_AI_MODEL_EMBEDDING=none
SPRING_AI_MODEL_AUDIO_SPEECH=none
SPRING_AI_MODEL_AUDIO_TRANSCRIPTION=none
SPRING_AI_MODEL_IMAGE=none
SPRING_AI_MODEL_MODERATION=none
```

**获取 API Key**：
- OpenAI: https://platform.openai.com/account/api-keys
- Anthropic: https://console.anthropic.com/settings/keys
- DeepSeek: https://platform.deepseek.com/api_keys

---

## 三、启动中间件

### 3.1 启动 Docker 容器

在项目根目录执行：

```powershell
# 启动所有中间件（MySQL/Redis/PostgreSQL/RocketMQ）
docker-compose up -d
```

### 3.2 验证容器状态
```powershell
# 查看运行状态
docker-compose ps
```

应该看到以下容器健康状态为 `healthy`：
- `omni-mysql` (端口 3306)
- `omni-redis` (端口 6379)
- `omni-postgres` (端口 5432)
- `omni-rocketmq-namesrv` (端口 9876)
- `omni-rocketmq-broker`

### 3.3 初始化数据库

**初始化 MySQL 业务库**：
```powershell
# 方式 1：使用容器内 MySQL 客户端
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_main.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_extensions.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_observability.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_eval_v2.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_shopify_v2.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/db_rag_safety.sql
docker exec -i omni-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < sql/demo_seed.sql

# 方式 2：使用 MySQL 工具（如 Navicat/DBeaver）连接 localhost:3306
# 用户名/密码：使用 .env 中的 MYSQL_USER / MYSQL_PASSWORD
# 数据库：omni_merchant
# 依次执行上面的 MySQL SQL 文件
```

**初始化 PostgreSQL 向量库**：
```powershell
# 使用 psql 执行
docker exec -i omni-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < sql/db_vector.sql
```

**一键 demo 初始化**：
```powershell
.\scripts\demo.ps1
```

---

## 四、编译与启动后端

### 4.1 编译项目
```powershell
# 在项目根目录执行
mvn -q -pl omni-merchant-bootstrap -am -DskipTests package
```

### 4.2 启动后端应用

#### 方式 1：命令行启动
```powershell
mvn -pl omni-merchant-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 方式 2：IDE 启动
在 IDEA/Eclipse 中运行：
`omni-merchant-bootstrap/src/main/java/com/omnimerchant/OmniMerchantApplication.java`

### 4.3 验证后端启动
访问：
- 健康检查：http://localhost:8090/api/health
- Druid 监控默认关闭。如需本地调试，显式设置 `DRUID_STAT_ENABLED=true`、`DRUID_LOGIN_USERNAME`、`DRUID_LOGIN_PASSWORD` 后再访问：http://localhost:8090/druid/

---

## 五、安装与启动前端

### 5.1 安装依赖
```powershell
cd omnimerchant-web
npm install
```

### 5.2 启动开发服务器
```powershell
npm run dev
```

### 5.3 访问前端
浏览器打开：http://localhost:5173

如果 Vite 自动切到 `http://127.0.0.1:5188` 或其它端口，请后续登录、Widget 测试都固定使用同一个 host，不要在 `localhost` 和 `127.0.0.1` 之间混用旧页面。

---

## 六、测试完整流程

### 6.1 访问管理后台
1. 打开 http://localhost:5173/login
2. 使用 `.env` 中的 `ADMIN_EMAIL` 和 `ADMIN_PASSWORD` 登录
3. 进入 `/admin`、`/admin/inbox`、`/admin/orders`、`/admin/products`、`/admin/evals` 检查 seed 数据

### 6.2 测试买家 Widget
1. 打开 http://localhost:5173/widget
2. 使用 seed 租户编码 `OM-FASHION` 或 `OM-ELECTRO` 创建公开会话
3. Widget 会先调用 `POST /api/widget/session` 获取 2 小时 `customerSessionToken`
4. 后续 `POST /api/widget/chat/stream` 必须携带 `Authorization: Bearer <customerSessionToken>`
5. 无 LLM key 时会返回配置提示；订单、商品、工单和 eval 数据仍可在后台验证

### 6.3 核心 smoke
```powershell
# 健康检查
curl http://localhost:8090/api/health

# 管理员登录
curl -X POST http://localhost:8090/api/admin/login `
  -H "Content-Type: application/json" `
  -d '{"email":"admin@example.com","password":"你的 ADMIN_PASSWORD"}'

# Widget session
curl -X POST http://localhost:8090/api/widget/session `
  -H "Content-Type: application/json" `
  -d '{"tenantCode":"OM-FASHION","customerEmail":"ava@example.com"}'

# Widget stream 缺 token 必须返回 401
curl -i -X POST http://localhost:8090/api/widget/chat/stream `
  -H "Content-Type: application/json" `
  -d '{"tenantCode":"OM-FASHION","conversationUuid":"任意会话","message":"hello"}'
```

---

## 七、常见问题排查

### 7.1 端口被占用
如果端口 3306/6379/5432/8090/5173 被占用：
```powershell
# 修改 docker-compose.yml 中的端口映射
# 修改 application-dev.yml 中的端口配置
```

### 7.2 Docker 启动失败
```powershell
# 查看容器日志
docker-compose logs mysql
docker-compose logs postgres
```

### 7.3 Maven 依赖下载慢
配置阿里云 Maven 镜像，编辑 `~/.m2/settings.xml`：
```xml
<mirrors>
    <mirror>
        <id>aliyun</id>
        <url>https://maven.aliyun.com/repository/public</url>
        <mirrorOf>*</mirrorOf>
    </mirror>
</mirrors>
```

### 7.4 AI 调用失败
检查：
1. API Key 是否正确
2. 网络连接是否正常
3. API 额度是否充足

---

## 八、停止服务

### 8.1 停止后端
在运行终端按 `Ctrl + C`

### 8.2 停止前端
在运行终端按 `Ctrl + C`

### 8.3 停止中间件
```powershell
# 停止但保留数据
docker-compose down

# 停止并删除数据
docker-compose down -v
```

---

## 九、开发建议

### 推荐 IDE
- 后端：IntelliJ IDEA
- 前端：VS Code

### 项目结构说明
```
omni-merchant/
├── omni-merchant-common/       # 通用模块（DTO、异常、工具类）
├── omni-merchant-tenant/       # 多租户管理
├── omni-merchant-agent/        # AI Agent 主模块
├── omni-merchant-intent/       # 意图识别
├── omni-merchant-knowledge/    # 知识库 RAG
├── omni-merchant-channel/      # 渠道对接
├── omni-merchant-message/      # 消息队列
├── omni-merchant-bootstrap/    # Spring Boot 启动入口
├── omnimerchant-web/          # Vue 3 前端
├── sql/                       # 数据库建表脚本
└── docker-compose.yml         # 中间件编排
```

---

## 十、下一步

配置完成后，先按 README 的本地质量门和 [`docs/v1-release-checklist.md`](docs/v1-release-checklist.md) 跑一遍。v1 当前目标是本地可复现展示和安全边界可信；生产部署、完整 Shopify App 安装流、在线 LLM eval dashboard 属于后续 v2。
