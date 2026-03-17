# 环境准备与本地开发

本文档指导你在本地环境启动 SpringMall 项目进行开发和调试。

---

## 前置依赖

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| Java | 21+ | Oracle官网下载LTS版本即可 |
| Maven | 3.9+ | 项目自带 `mvnw` 包装器，无需单独安装 |
| Node.js | 20+ | 前端构建环境 |
| pnpm | 最新版 | 前端包管理器 |
| MySQL | 8.0 | 字符集 utf8mb4 |
| Redis | 8.4.0 | 默认端口 6379 |
| RabbitMQ | 4.2.4 | 默认端口 5672（AMQP）/ 15672（管理界面） |

---

## 数据库初始化

1. 创建数据库并导入建表脚本：

```bash
mysql -u root -p mall < C:\你的路径\mall-backend\src\main\resources\db\schema.sql
```

脚本会自动创建 `mall` 数据库（如不存在）、建立 9 张表并插入默认数据：
- 管理员账号：`admin` / `admin123`
- 普通用户：`user` / `123456`
- 示例分类数据

---

## 后端本地启动

### 1. 配置文件

后端使用 Spring Profiles 管理多环境配置：

| 文件 | 用途 |
|------|------|
| `application.yml` | 通用配置（数据源、Redis、RabbitMQ、JWT、支付等） |
| `application-dev.yml` | 开发环境（开启 debug 日志、SQL 日志、Swagger） |
| `application-test.yml` | 测试环境   (调整了一些连接池的设定) |
| `application-prod.yml` | 生产环境   (调整了一些连接池的设定) |

本地开发默认使用环境变量的 fallback 值连接本机服务：

| 配置项 | 默认值 |
|--------|--------|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `mall` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `123456` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `REDIS_PASSWORD` | `123456` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` | `admin` |
| `RABBITMQ_PASS` | `123456` |

如果本地服务的密码/端口不同，通过环境变量或修改 `application.yml` 中的 fallback 值覆盖。

### 2. 启动

```bash
# 启动所需要依赖环境后(mysql、redis、rabbitmq)
cd mall-backend

# 使用 Maven Wrapper 启动后端（开发环境 Profile）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 或 在IDEA中导入 环境变量/自行修改application.yml覆盖值 后启动后端
```

启动成功后：
- API 基础路径：`http://localhost:8080/api/v1/`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- 健康检查：`http://localhost:8080/api/v1/health`

---

## 前端本地启动

### 1. 安装依赖

```bash
cd mall-frontend
pnpm install
```

### 2. 启动开发服务器

```bash
pnpm run dev
```

开发服务器启动后：
- 访问地址：`http://localhost:3000`

---

## 开发工作流

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   MySQL     │     │   Redis     │     │  RabbitMQ   │
│  :3306      │     │  :6379      │     │  :5672      │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────┬───────┴───────────────────┘
                   │
            ┌──────┴──────┐
            │  Backend    │
            │  :8080      │
            └──────┬──────┘
                   │ /api/ 代理
            ┌──────┴──────┐
            │  Frontend   │
            │  :3000      │
            └─────────────┘
```

1. 启动 MySQL、Redis、RabbitMQ
2. 启动后端（端口 8080）
3. 启动前端（端口 3000，代理 `/api/` 到后端）
4. 浏览器访问 `http://localhost:3000`

---

## 常见问题

### Q: 后端启动报数据库连接失败

确认 MySQL 已启动，数据库 `mall` 已创建。检查 `application.yml` 中 `DB_HOST`、`DB_PASSWORD` 等配置是否与本地一致。

### Q: Redis 连接被拒绝

确认 Redis 已启动并设置了密码。默认密码为 `123456`，如不同请设置环境变量 `REDIS_PASSWORD`。

### Q: RabbitMQ 连接失败

确认 RabbitMQ 已启动且 management 插件已启用。默认用户名/密码为 `admin`/`123456`。

### Q: 前端页面显示空白

确认后端已启动并能正常访问 `http://localhost:8080/api/v1/health`。Vite 开发代理依赖后端服务可用。

### Q: 端口被占用

```bash
# 查看端口占用
netstat -ano | findstr :8080

# 如果有进程占用，根据 PID 结束进程
taskkill /F /PID <PID>

# 如果端口被 Hyper-V 保留（输出为空但仍无法绑定）
net stop winnat
net start winnat
```

### Q: 支付功能无法使用

支付需要配置对应的密钥：
- **支付宝**：需要在支付宝开放平台创建应用，获取 `ALIPAY_APP_ID`、`ALIPAY_PRIVATE_KEY`、`ALIPAY_PUBLIC_KEY`
- **Stripe**：需要在 Stripe Dashboard 获取 `STRIPE_SECRET_KEY`、`STRIPE_WEBHOOK_SECRET`
- **微信支付**：默认禁用（`WXPAY_ENABLED=false`），需要真实商户信息才能启用（开发都开发不了）
