# springMall — Claude Code 项目宪章

全栈电商项目。后端 `mall-backend/`（Spring Boot 3.3.6 / Java 21），前端 `mall-frontend/`（Vue 3 / Vite 7）。

---

## 1. 架构速览

### 后端技术栈
- **框架**：Spring Boot 3.3.6，Java 21；构建 Maven（`mvnw`）
- **ORM**：MyBatis，XML Mapper（`classpath:mapper/*.xml`）
- **分页**：PageHelper（`PageHelper.startPage` + `PageInfo`，统一包装为 `PageResult<>`）
- **安全**：Spring Security + JWT（JJWT 0.12.6），无状态会话
- **存储**：MySQL 8（HikariCP） + Redis（缓存/会话）
- **消息**：RabbitMQ（异步/解耦）
- **定时任务**：XXL-JOB
- **支付**：支付宝 + Stripe
- **接口**：RESTful，前缀 `/api/v1/`；统一 `Result<T>` 包装 `{ code, message, data }`
- **校验**：Jakarta Bean Validation；
- **接口文档**：springdoc-openapi（Swagger）
- 包根 `site.geekie.shop.shoppingmall`：`controller/`(含 `admin/`)、`service/`+`service/impl/`、`mapper/`、`entity/`(DO)、`dto/`、`vo/`、`converter/`(MapStruct)、`config/`、`security/`、`exception/`、`common/`、`util/`

### 前端技术栈
- Vue 3（`<script setup>` / Composition API）+ Pinia（**Options 风格**）+ Element Plus + Axios（封装于 `src/api/request.js`）+ Vite 7 + Sass
- 目录：`api/` `store/` `views/`(user,admin,auth) `components/`(common) `layouts/` `router/` `utils/`

---

## 2. 开发工作流

涉及需求新增功能交付：主 session 先澄清需求、拆解任务（复杂需求可开 GitHub Issue 记录，见 `docs/agents/issue-tracker.md`），再委派对应 agent 实施 → 审查 → 验收。简单提问或小修改用主 session 直接处理，无需走完整流程。

### 委派硬规则（不可违反）
> **实施阶段，业务代码的增删改一律经对应子 agent；主 session 只做澄清、拆任务与调度，不直接编辑 `mall-backend/src`、`mall-frontend/src` 下的业务代码。**

| 链路环节 | 负责 |
|---|---|
| 需求澄清 / 拆任务 | 主 session |
| 后端代码（Java / Mapper XML / yml / pom） | **backend-dev** |
| 前端代码（Vue / JS / SCSS / Pinia / 路由） | **frontend-dev** |
| 代码审查 | **code-reviewer**（仅读） |
| 需求验收 + 编译构建 | **acceptance-validator** |

**并行/顺序**：API 合约已确定时 backend / frontend 可并行；新增合约则 backend 先行（合约是源头）。审查必在两位 dev 完成后；验收必在审查后，不可跳过。

---

## 3. SubAgent 团队

> 权威清单见 `.claude/agents/*.md`；下表为速查，新增/删除 agent 时两处一起改。

| Agent | 模型 | 负责范围 |
|---|---|---|
| `backend-dev` | opus | Controller、Service、Mapper、Entity、DTO、Config、Security |
| `frontend-dev` | opus | Vue 页面/组件、Pinia Store、Router、API 层、SCSS |
| `code-reviewer` | opus | 安全审查、对照 Rules 检查、前后端合约对接（仅读不写） |
| `acceptance-validator` | sonnet | L1 编译/构建门禁 + L2 按验收标准做需求验收 |

> 编码规约按文件路径**自动加载**（见 `.claude/rules/`），不在此重复。

---

## 4. 安全红线

见 `.claude/rules/security-redlines.md`（全局自动加载，任何 agent 无权违反）。危险命令由 `hooks/block-dangerous.sh` 在执行前拦截。

---

## 5. 文档地图

- `docs/` — **唯一**长青设计文档（架构 / 数据库 / Redis / RabbitMQ / 支付 / 安全 / 部署 / 入门）。
- `docs/agents/` — Agent 协作配置（issue tracker、领域文档消费规则），见 `## Agent skills`。
- **API** — 以代码 + Swagger（`/swagger-ui`、`/v3/api-docs`）为准，不再手维护 API 文档；可用 MySQL MCP inspect 真实表结构。

---

## 6. 数据库 MCP

已配置 `@benborla29/mcp-server-mysql`（见根目录 `.mcp.json`），连接本机**开发库** `mall`，读写开启。写 Mapper/Entity 前可 inspect schema，列名/类型不靠猜；改数据后可核对副作用。**仅连开发库。**

---

## 7. Agent skills

### Issue tracker

GitHub Issues（`gh` CLI），仓库 `geekie-yuan/SpringMall`。见 `docs/agents/issue-tracker.md`。

### Domain docs

single-context 布局：`CONTEXT.md`（领域词汇表）+ `docs/adr/`（架构决策记录），与 `docs/`（长青设计文档）并行、职责不同——`docs/` 描述系统现状，`CONTEXT.md`/ADR 记录术语定义与某次决策的取舍过程。见 `docs/agents/domain.md`。

