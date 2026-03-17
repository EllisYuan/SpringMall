# SpringMall — 全栈电商平台

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen?logo=springboot)
![Vue 3](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs)
![MySQL 8](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8.4.0-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/Rabbit-4.2.4-FF6600?logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

---
## 项目介绍
> Demo: https://shop4d.ellisyuan.com

SpringMall 是一个前后端分离的单体全栈电商应用，完整实现电商业务所需所有链路：分类检索、多渠道支付支持（Alipay/ Stripe）、订单管理、调单漏单检测等。技术层面，Redis 承担认证缓存、接口限流、商品缓存和分布式锁； RabbitMQ 实现订单超时关单与支付掉单补偿；JWT 无状态认证 + Token 黑名单 + AOP 切面日志审计。

### 首页
![image](https://img.geekie.site/i/adImg/2026/03/17/160048.png)



### 商品页

![image](https://img.geekie.site/i/adImg/2026/03/17/160157.png)

### 管理后台

![image](https://img.geekie.site/i/adImg/2026/03/17/160229.png)

<p>
  <img src="https://img.geekie.site/i/adImg/2026/03/17/160245.png" width="49%" />
  <img src="https://img.geekie.site/i/adImg/2026/03/17/160314.png" width="49%" />
</p>

## Docker 快速启动

### Linux / macOS

```bash
# 1. 克隆仓库
git clone https://github.com/geekie-yuan/springMall.git
cd springMall

# 2. 配置环境变量
cp .env.example .env

# 3. 填入数据库密码、JWT Secret、支付密钥(支付密钥非必需，但是会存在无法支付等情况）
nano .env    

# 4. docker启动
docker compose --env-file .env up -d --build --wait
```

### Windows

```powershell
# 1. 克隆仓库
git clone https://github.com/geekie-yuan/springMall.git
cd springMall

# 2. 配置环境变量
Copy-Item .env.example .env

# 3.填入数据库密码、JWT Secret、支付密钥(支付密钥非必需，但是会存在无法支付等情况）

# 4. docker启动
docker compose --env-file .env up -d --build --wait
```

### 服务访问地址

启动完成后（所有健康检查通过），可访问：

| 服务 | 地址 |
|------|------|
| 前端商城 | http://localhost:26115 |
| 后端 API | http://localhost:25116/api/v1 |
| Swagger 文档 | http://localhost:26115/swagger-ui/index.html |
| RabbitMQ 管理 | http://localhost:15672 |

> 本地开发请参考 [环境准备与本地开发](docs/getting-started.md)

---
## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `user` | `123456` 

---

## 项目结构

```
springMall/
├── mall-backend/                  # Spring Boot 后端
│   ├── src/main/java/.../
│   │   ├── controller/            # HTTP 接口层（含 admin/ 子目录）
│   │   ├── service/               # 业务逻辑（接口 + impl/）
│   │   ├── mapper/                # MyBatis Mapper 接口
│   │   ├── entity/                # 数据库实体类 (DO)
│   │   ├── dto/                   # 请求入参 (DTO)
│   │   ├── vo/                    # 响应视图对象 (VO)
│   │   ├── converter/             # MapStruct 转换器
│   │   ├── config/                # 配置类（Security、Swagger、Redis 等）
│   │   ├── security/              # JWT 认证、Token 黑名单、用户缓存
│   │   ├── mq/                    # RabbitMQ 生产者 + 消费者
│   │   ├── aspect/                # AOP 切面（日志、限流、脱敏）
│   │   ├── annotation/            # 自定义注解
│   │   ├── enums/                 # 枚举（订单状态、支付状态）
│   │   ├── exception/             # 全局异常处理
│   │   └── util/                  # 工具类（分布式锁等）
│   ├── src/main/resources/
│   │   ├── db/schema.sql          # 数据库建表脚本
│   │   ├── mapper/*.xml           # MyBatis XML 映射
│   │   └── application*.yml       # 多环境配置
│   ├── docs/                      # 后端技术文档
│   └── Dockerfile                 # 多阶段构建
├── mall-frontend/                 # Vue 3 前端
│   ├── src/
│   │   ├── api/                   # Axios 接口模块（按业务领域拆分）
│   │   ├── views/                 # 页面组件（user/、admin/、auth/）
│   │   ├── components/            # 可复用组件
│   │   ├── layouts/               # 布局组件（UserLayout、AdminLayout）
│   │   ├── store/                 # Pinia 状态管理
│   │   ├── router/                # Vue Router + 导航守卫
│   │   ├── utils/                 # 工具函数
│   │   └── assets/                # SCSS 样式
│   ├── docs/                      # 前端技术文档
│   ├── nginx.conf                 # Nginx 反向代理配置
│   └── Dockerfile                 # 多阶段构建
├── docker-compose.yml             # 基础编排（5 个服务）
├── docker-compose.test.yml        # 测试环境 override
├── docker-compose.prod.yml        # 生产环境 override
├── deploy.sh                      # 部署脚本（Linux/macOS）
├── deploy.ps1                     # 部署脚本（Windows PowerShell）
├── .env.example                   # 环境变量模板
└── docs/                          # 全局文档
```
---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.6、Spring Security、Spring AOP |
| 数据层 | MyBatis 3.5、PageHelper、MapStruct 1.5 |
| 数据库 | MySQL 8.0、Redis |
| 消息队列 | RabbitMQ（DLQ ） |
| 认证 | JWT、无状态会话 |
| 支付 | 支付宝 SDK 4.40、Stripe SDK 31.3 |
| 前端框架 | Vue 3.5、Vue Router 4、Pinia 3 |
| UI 组件 | Element Plus 2.13 |
| 构建工具 | Maven、Vite 7 + pnpm |
| 部署 | Docker Compose、Nginx |
| 接口文档 | Swagger |

## 文档索引

### 全局文档

| 文档 | 说明 |
|------|------|
| [系统架构](docs/architecture.md) | 分层架构图、Docker 拓扑图、订单生命周期流程图 |
| [环境准备与本地开发](docs/getting-started.md) | 前置依赖、后端/前端本地启动、数据库初始化 |
| [Docker 部署手册](docs/docker-deploy.md) | 环境变量、多环境部署、deploy.sh 使用、故障排查 |
| [API 总览](docs/api-overview.md) | 统一响应格式、错误码、认证方式、API 端点汇总 |

### 后端技术文档

| 文档 | 说明 |
|------|------|
| [分层架构](mall-backend/docs/architecture.md) | 分层设计、请求链路、命名规范、MapStruct 模式 |
| [数据库设计](mall-backend/docs/database-design.md) | ER 图、9 张表详细说明、设计决策 |
| [订单状态机](mall-backend/docs/order-state-machine.md) | 订单/支付状态转换、并发保障机制 |
| [支付系统设计](mall-backend/docs/payment-design.md) | 多渠道支付、幂等设计、掉单补偿、自动退款 |
| [Redis 缓存设计](mall-backend/docs/redis-design.md) | Key 清单、缓存策略、分布式锁、限流、降级 |
| [消息队列设计](mall-backend/docs/rabbitmq-design.md) | 两条 DLQ 链路、时序图、事务保障 |
| [安全与横切设计](mall-backend/docs/security-design.md) | JWT 认证链路、AOP 切面、权限矩阵 |

---
### 前端技术文档

| 文档 | 说明 |
|------|------|
| [前端架构](mall-frontend/docs/architecture.md) | 目录结构、技术选型、Axios 封装、Vite 优化 |
| [路由与导航守卫](mall-frontend/docs/router-and-guards.md) | 路由表、导航守卫逻辑、懒加载策略 |
| [状态管理](mall-frontend/docs/state-management.md) | 4 个 Pinia Store 详解、持久化、交互关系 |

