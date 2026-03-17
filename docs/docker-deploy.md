# Docker 部署手册

SpringMall 使用 Docker Compose 编排 5 个服务，支持 test/prod 多环境部署。

---

## 服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Network (mall-network)            │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐              │
│  │  MySQL   │  │  Redis   │  │  RabbitMQ    │              │
│  │  :3306   │  │  :6379   │  │  :5672/15672 │              │
│  └────┬─────┘  └────┬─────┘  └──────┬───────┘              │
│       │              │               │                      │
│       └──────┬───────┴───────────────┘                      │
│              │  depends_on (healthcheck)                     │
│       ┌──────┴──────┐                                       │
│       │  Backend    │                                       │
│       │  :8080      │                                       │
│       └──────┬──────┘                                       │
│              │  depends_on                                  │
│       ┌──────┴──────┐                                       │
│       │  Frontend   │                                       │
│       │  :80        │  Nginx → /api/ → backend:8080         │
│       └─────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

启动顺序：MySQL/Redis/RabbitMQ（健康检查通过）→ Backend → Frontend

---

## 端口映射

| 服务 | 容器端口 | 宿主机端口（默认） | 环境变量 |
|------|---------|-------------------|---------|
| Frontend (Nginx) | 80 | **26115** | `FRONTEND_PORT` |
| Backend (Spring Boot) | 8080 | **25116** | `BACKEND_PORT` |
| MySQL | 3306 | 3306 | `MYSQL_PORT` |
| Redis | 6379 | 6379 | `REDIS_PORT` |
| RabbitMQ AMQP | 5672 | 5672 | `RABBITMQ_PORT`（docker-compose 内部） |
| RabbitMQ 管理界面 | 15672 | 15672 | `RABBITMQ_MGMT_PORT` |

---

## 环境变量说明

从 `.env.example` 复制为 `.env.test` 或 `.env.prod` 后修改：

### 端口配置

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `BACKEND_PORT` | 后端宿主机端口 | `25116` |
| `FRONTEND_PORT` | 前端宿主机端口 | `26115` |
| `MYSQL_PORT` | MySQL 宿主机端口 | `3306` |
| `REDIS_PORT` | Redis 宿主机端口 | `6379` |
| `RABBITMQ_MGMT_PORT` | RabbitMQ 管理界面端口 | `15672` |

### 数据库

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `DB_HOST` | MySQL 主机名 | `mysql`（容器内部服务名） |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名 | `mall` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `test123456` |

### JWT

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `JWT_SECRET` | JWT 签名密钥（Base64 编码，至少 64 字符） | 开发用默认值 |

> **生产环境必须**替换为足够长的随机字符串。

### Redis

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `REDIS_HOST` | Redis 主机名 | `redis`（容器内部服务名） |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | `123456` |

### RabbitMQ

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `RABBITMQ_HOST` | RabbitMQ 主机名 | `rabbitmq`（容器内部服务名） |
| `RABBITMQ_PORT` | AMQP 端口 | `5672` |
| `RABBITMQ_USER` | 管理用户名 | `admin` |
| `RABBITMQ_PASS` | 管理密码 | `123456` |

### 支付宝

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `ALIPAY_APP_ID` | 应用 ID | 空 |
| `ALIPAY_PRIVATE_KEY` | 应用私钥 | 空 |
| `ALIPAY_PUBLIC_KEY` | 支付宝公钥 | 空 |
| `ALIPAY_NOTIFY_URL` | 异步通知回调 URL | 空 |
| `ALIPAY_RETURN_URL` | 同步跳转 URL | 空 |
| `FRONTEND_URL` | 前端地址（支付成功跳转） | `http://localhost:3000` |

### Stripe

| 变量 | 用途 | 默认值 |
|------|------|--------|
| `STRIPE_ENABLED` | 是否启用 Stripe | `true` |
| `STRIPE_SECRET_KEY` | Secret Key（`sk_` 开头） | 空 |
| `STRIPE_WEBHOOK_SECRET` | Webhook 签名密钥（`whsec_` 开头） | 空 |
| `STRIPE_REFUND_WEBHOOK_SECRET` | 退款 Webhook 签名密钥 | 空 |
| `STRIPE_SUCCESS_URL` | 支付成功跳转 URL | 空 |
| `STRIPE_CANCEL_URL` | 支付取消跳转 URL | 空 |
| `STRIPE_TEST_MODE` | 是否测试模式 | `true` |
| `PRODUCT_IMAGE_BASE_URL` | 商品图片基础 URL | 空 |

---

## 部署流程

### 使用 deploy.sh（推荐）

```bash
# 1. 准备环境变量文件
cp .env.example .env.test    # 测试环境
cp .env.example .env.prod    # 生产环境

# 2. 编辑环境变量
vim .env.test

# 3. 执行部署
./deploy.sh test    # 部署测试环境
./deploy.sh prod    # 部署生产环境
```

`deploy.sh` 自动执行三步操作：
1. `git pull` — 拉取最新代码
2. `docker compose build` — 构建镜像（多阶段构建）
3. `docker compose up -d --wait` — 启动服务并等待健康检查通过

### 手动部署

```bash
# 测试环境
docker compose -f docker-compose.yml -f docker-compose.test.yml \
  --env-file .env.test up -d --build

# 生产环境
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  --env-file .env.prod up -d --build
```

---

## 多环境 Override 配置

基础配置在 `docker-compose.yml`，各环境通过 override 文件覆盖差异化配置：

| 文件 | Spring Profile | 差异 |
|------|---------------|------|
| `docker-compose.test.yml` | `test` | 较小资源限制，healthcheck 3 次重试 |
| `docker-compose.prod.yml` | `prod` | `restart: always`、更大资源限制、healthcheck 5 次重试 |

### 资源限制对比

| 服务 | 测试环境 | 生产环境 |
|------|---------|---------|
| MySQL | 1 CPU / 1024M | 2 CPU / 2048M |
| Redis | 0.5 CPU / 256M | 1 CPU / 512M |
| RabbitMQ | 0.5 CPU / 512M | 1 CPU / 1024M |
| Backend | 2 CPU / 2048M | 2 CPU / 2048M |
| Frontend | 1 CPU / 1024M | 1 CPU / 1024M |

---

## Docker 构建说明

### 后端 Dockerfile

多阶段构建：
1. **Build 阶段**：`maven:3.9-eclipse-temurin-21-alpine` → `mvn package -DskipTests`
2. **Runtime 阶段**：`eclipse-temurin:21-jre-alpine` → 以非 root 用户 `appuser` 运行

JVM 参数：`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`

### 前端 Dockerfile

多阶段构建：
1. **Build 阶段**：`node:20-alpine` + pnpm → `pnpm run build`
2. **Serve 阶段**：`nginx:alpine` → 静态文件 + 反向代理

---

## Nginx 反向代理

前端容器内的 Nginx 配置（`mall-frontend/nginx.conf`）：

| 路径 | 目标 | 说明 |
|------|------|------|
| `/api/*` | `http://backend:8080/api/` | API 请求代理到后端容器 |
| `/api-docs` | `http://backend:8080/api-docs` | Swagger API 文档 |
| `/swagger-ui/*` | `http://backend:8080/swagger-ui/` | Swagger UI |
| `/*` | 静态文件 / `index.html` | Vue Router history 模式 fallback |

额外优化：
- **Gzip 压缩**：对 JS/CSS/JSON/XML/SVG 等启用
- **静态资源缓存**：JS/CSS/图片等 30 天过期，`Cache-Control: public, immutable`

---

## 数据持久化

Docker Compose 使用 named volumes 持久化数据：

| Volume | 挂载路径 | 说明 |
|--------|---------|------|
| `mysql-data` | `/var/lib/mysql` | MySQL 数据 |
| `redis-data` | `/data` | Redis 持久化 |
| `rabbitmq-data` | `/var/lib/rabbitmq` | RabbitMQ 数据和配置 |

数据库初始化脚本 `schema.sql` 以只读方式挂载到 MySQL 容器的 `/docker-entrypoint-initdb.d/`，仅在首次启动时执行。

---

## 故障排查

### 后端无法连接 MySQL

```bash
# 检查 MySQL 容器健康状态
docker inspect mysql-mall | grep -A5 Health

# 进入 MySQL 容器测试连接
docker exec -it mysql-mall mysql -u root -p
```

### 后端启动超时

后端需要等待 MySQL、Redis、RabbitMQ 全部健康检查通过后才启动。如果基础服务启动慢，可增大 `start_period`。

### 前端显示 502 Bad Gateway

Nginx 无法连接后端容器。检查后端是否正常运行：

```bash
docker logs mall-backend --tail 50
```

### 端口冲突

```bash
# 查看端口占用
netstat -ano | findstr :25116

# 修改 .env 文件中的端口映射
BACKEND_PORT=25117
```
