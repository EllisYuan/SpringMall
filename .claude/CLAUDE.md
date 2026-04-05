# springMall — Claude Code Agents 项目宪章

---

## 1. 项目架构概览

springMall 是一个全栈电商应用项目。

### 后端（`mall-backend/`）
- **框架**：Spring Boot 3.3.6，Java 21
- **ORM**：MyBatis，XML Mapper（`classpath:mapper/*.xml`）
- **安全**：Spring Security + JWT（JJWT 0.12.6），无状态会话
- **数据库**：MySQL 8，HikariCP 连接池
- **接口风格**：RESTful，所有路径以 `/api/v1/` 为前缀
- **响应格式**：统一 `Result<T>` 包装 — `{ code, message, data }`
- **校验**：Jakarta Bean Validation（`@Valid` 作用于 `@RequestBody`）
- **接口文档**：Swagger，由 springdoc-openapi 2.3.0 提供
- **构建**：Maven（`mvnw` 包装器）

包根路径：`site.geekie.shop.shoppingmall`

| 包目录 | 职责 |
|---|---|
| `controller/` | HTTP 接口层。返回 `Result<T>` |
| `controller/admin/` | 管理员专用接口（需 ADMIN 角色） |
| `service/` | 业务逻辑接口定义 |
| `service/impl/` | 业务逻辑实现 |
| `mapper/` | MyBatis Mapper 接口 |
| `entity/` | 映射到数据库实体类，Do对象 |
| `dto/` | 请求入参，DTO对象 |
| `vo/` | 返回给前端的VO响应对象 |
| `common/` |
| `config/` | `SecurityConfig`、`SwaggerConfig`等 配置文件路径 |
| `security/` |
| `exception/` | `BusinessException`、`GlobalExceptionHandler`异常处理捕获层 |
| `util/` | 通用工具类 |

### 前端（`mall-frontend/`）
- **框架**：Vue 3（`<script setup>` / Composition API）
- **状态管理**：Pinia — Options 风格 `defineStore`（`state`/`getters`/`actions`）
- **UI 组件库**：Element Plus 2.13+
- **HTTP 客户端**：Axios，封装在 `src/api/request.js`
- **构建工具**：Vite 7 + Sass
- **生产构建**：`pnpm run build` → `dist/`
- **开发服务器**：`pnpm run dev`（端口 3000，`/api/` 代理到 localhost:8080）

| 目录 | 职责 |
|---|---|
| `src/api/` | Axios 接口调用模块，按业务领域一个文件（`product.js`、`cart.js`…） |
| `src/store/` | Pinia Store（`auth.js`、`cart.js`、`app.js`、`user.js`） |
| `src/views/` | 页面级组件。子目录：`user/`、`admin/`、`auth/` |
| `src/components/` | 可复用组件。`common/` 中有 `Loading.vue`、`Empty.vue` |
| `src/layouts/` | 布局包装组件（`UserLayout`、`AdminLayout`） |
| `src/router/` | Vue Router 配置 + 导航守卫 |
| `src/utils/` | 纯工具函数：`storage.js`、`format.js`、`constants.js`、`validate.js` |
| `src/assets/` | SCSS 变量、重置样式、公共样式 |

### 部署（项目根目录）
- `docker-compose.yml`：后端宿主端口 **25116**，前端宿主端口 **26115**
- 前端 Nginx 将 `/api/` 代理到 `backend:8080`
- 后端通过 `host.docker.internal` 连接宿主机 MySQL
- 密钥和凭据通过 `.env` → docker-compose 变量插值传入

---

## 2. 团队协作流程

### 简单提问或修改
直接调用主agent进行回答

### 复杂功能交付流程
```
用户发起功能需求
    │
    ▼
主 Session — 分析需求范围
    │
    ├── 需要后端变更？  ──►  backend-dev 实现
    │                            │
    │                            ├──► doc-writer — 生成/更新 DevDoc
    │                            │
    ├── 需要前端变更？  ──►  frontend-dev 实现
    │                            │
    ▼                            ▼
    └──────────┬─────────────────┘
               ▼
        code-reviewer — 审查所有变更文件（仅读不写）
               │
               ▼
        test-validator — mvnw test + pnpm run build(用户请求测试时才触发)
               │
               ▼
  （若涉及基础设施）devops-deploy — 验证 Docker 配置(用户请求测试时才触发)
               │
               ▼
    主 Session 将结果汇报给用户
```

### 交接协议
- **backend-dev** 完成后 → 说明：修改了哪些文件、API 合约（接口路径、请求/响应结构）、是否需要数据库迁移。
- **frontend-dev** 在开始前读取上述交接信息，确保拿到正确的 API 结构。
- **doc-writer** 收到 backend-dev 交接信息后启动。自行读取代码验证接口细节，生成或更新 `mall-backend/DevDoc/` 文档。可与 code-reviewer 并行执行。
- **code-reviewer** 收到所有变更文件的清单，逐一阅读。不修改任何文件，仅输出按 BLOCKER / WARNING / INFO 分级的审查报告。
- **test-validator** 用户请求测试时才触发，不阅读源代码。仅运行构建和测试命令，报告退出码和错误输出。
- **devops-deploy** 用户请求测试时才触发。

### 并行与顺序执行规则
- `backend-dev` 和 `frontend-dev` **仅当** API 合约已存在（已记录在 `.claude/skills/API_document/references/api-endpoints.md` 中）时才可并行执行。
- 若 API 合约是新增的 → `backend-dev` 必须先完成。
- `code-reviewer` 必须在两位开发 Agent 都完成之后才运行，不可与之并行。
- `test-validator` 必须在 `code-reviewer` 之后运行，不可跳过。

### Agent Teams 工作流（复杂功能）

```
用户发起功能需求
    │
    ▼
Lead Session — 分析需求，判断使用 Subagent 还是 Agent Teams
    │
    ├── 简单单方向任务 → 使用 Subagent（保持现有流程）
    │
    ├── 复杂跨层任务 → 创建 Agent Team
    │       │
    │       ├── 创建任务列表（含依赖关系）
    │       ├── Spawn backend 队友
    │       ├── Spawn frontend 队友（API 合约已知时可并行）
    │       ├── 队友互相通信 API 变更
    │       ├── Spawn reviewer 队友（dev 完成后）
    │       ├── Spawn test/doc 队友（reviewer 通过后）
    │       └── Lead 综合结果，清理团队
    │
    ▼
Lead Session 将结果汇报给用户
```


## 3. SubAgent 团队

| Agent | 模型 | 负责范围 | 委托时机 |
|---|---|---|---|
| `backend-dev` | sonnet | Controller、Service、Mapper、Entity、DTO、Config、Security | 任何服务端变更：新接口、业务逻辑、数据库查询、安全配置 |
| `frontend-dev` | sonnet | Vue 页面、组件、Pinia Store、Router、API 层、SCSS | 任何 UI 变更：新页面、组件、Store Action、API 对接 |
| `code-reviewer` | opus | 安全审查、OWASP 检查、代码规范一致性 | backend-dev 或 frontend-dev 完成变更之后。合并前必经环节。仅读不写。 |
| `test-validator` | haiku | 执行 `mvnw test` 和 `pnpm run build`，报告通过/失败 | code-reviewer 审批通过后。确认无回归。 |
| `devops-deploy` | haiku  | Dockerfile、docker-compose、nginx.conf、.env 管理 | 任何基础设施或部署相关变更。也在 test-validator 通过且涉及部署文件时启动。 |
| `doc-writer` | haiku | `mall-backend/DevDoc/` 下所有开发文档 | backend-dev 完成变更后。生成阶段交付文档或规划/设计文档，也负责更新已有文档。 |

### 委托决策树
1. 纯基础设施 / Docker / 部署 → **devops-deploy**
2. 涉及 Java 源文件 → **backend-dev**
3. 涉及 Vue / JS / SCSS 文件 → **frontend-dev**
4. 同时涉及前后端 → **先 backend-dev**（API 合约是源头），再 **frontend-dev**，最后 **code-reviewer** 审查两者
5. backend-dev 完成变更后 → **doc-writer** 根据交接信息生成或更新 `DevDoc/` 文档（可与 code-reviewer 并行）

---

## 4.Agent Teams 模式

Agent Teams 允许多个队友（Teammate）在同一项目中并行工作并直接通信

### 4.1 模式选择指南

| 场景 | 推荐模式 | 原因 |
|------|---------|------|
| 单一后端/前端修改 | Subagent | 简单任务无需队友通信，Token 开销低 |
| 全栈新功能（前后端联动） | Agent Teams | 前后端队友可直接通信 API 合约 |
| 多角度需求分析/架构评审 | Agent Teams | 队友可互相挑战方案 |
| 跨层 Bug 调查 | Agent Teams | 并行假设验证 |
| 纯测试/纯文档 | Subagent | 单向任务，不需协作 |
| 代码审查（单次） | Subagent | 仅需结果，无需讨论 |

### 4.2 团队模板

**模板 A：全栈功能开发**（3-4 队友）

```
创建 Agent Team 来开发 [功能名称]：
- backend 队友：负责 Controller/Service/Mapper/Entity/DTO 实现，遵循 Result<T> 响应规范和分层领域模型规约
- frontend 队友：负责 Vue 页面/组件/Store/API 层实现，使用 Element Plus + Composition API
- reviewer 队友：在前两位完成后审查所有变更，检查 OWASP Top-10、规范一致性、API 合约对接
- （可选）doc 队友：根据 backend 队友的实现生成 DevDoc 文档

要求：
1. backend 队友完成 API 合约后，直接通知 frontend 队友接口路径和响应结构
2. reviewer 队友需要在 backend 和 frontend 都完成后才开始审查
3. 所有队友遵循 CLAUDE.md 中的编码规范
```

**模板 B：需求分析/架构评审**（3 队友）

```
创建 Agent Team 来分析 [需求/模块名称]：
- 架构队友：从技术架构角度评估方案可行性、扩展性、与现有代码的兼容性
- 安全性能队友：从安全漏洞和性能瓶颈角度审视方案
- 质疑者队友：扮演 Devil's Advocate，挑战其他队友的结论

要求：
1. 队友之间互相挑战对方的结论
2. 最终由 Lead 综合各方意见形成决策
```

**模板 C：重构优化**（2-3 队友）
```
创建 Agent Team 来重构 [模块名称]：
- 分析队友：分析当前实现的问题和改进空间，给出重构方案
- 实现队友：根据分析队友的方案执行重构，需要分析队友先完成并获得 Lead 审批
- （可选）测试队友：重构完成后运行构建和测试验证

要求：
1. 实现队友需要等分析队友完成并获得 Lead 批准后才开始
2. 分析队友需要在实现队友的方案上做 plan approval
```

**模板 D：跨层 Bug 调查**（2-3 队友）
```
创建 Agent Team 来调查 [Bug 描述]：
- 前端调查队友：从前端请求、状态管理、组件渲染角度排查
- 后端调查队友：从 API 处理、Service 逻辑、数据库查询角度排查
- （可选）基础设施队友：从 Docker 配置、Nginx 代理、网络层角度排查

要求：
1. 队友之间互相分享发现，尝试证伪对方的假设
2. 收敛到一个根因后由 Lead 确认
```

### 4.3 队友 Spawn 提示词规范

每个队友的 spawn prompt 中必须包含：
1. **角色定义**：明确职责范围和对应的文件目录
2. **编码规范引用**：提醒遵循 CLAUDE.md §3 编码规范
3. **安全红线**：提醒 §4 安全策略中的禁止事项
4. **通信要求**：何时需要通知其他队友

### 4.4 Agent Teams 使用注意事项

1. **Token 消耗**：Agent Teams 消耗显著高于 subagent，简单任务优先使用 subagent
2. **文件冲突**：确保不同队友负责不同的文件集，避免同时编辑同一文件
3. **Plan Approval**：对有风险的重构任务，要求队友先规划再实施
4. **团队清理**：任务完成后始终由 Lead 执行清理，不要让队友清理

---

## 5. 安全策略

以下规则不得违反，任何 Agent 均无权覆盖。

1. **绝不**修改、创建或 commit `.env` 文件。只能编辑 `.env.example`，并手动通知开发者复制。
2. **绝不** commit 密钥：JWT Secret、数据库密码、API Key。若暂存区中检测到，立即中止。
3. **绝不**在未写明回滚方案的情况下修改 `schema.sql` 或任何数据库迁移脚本。
4. **绝不**在未获得用户明确确认的情况下执行 `rm -rf`、`git reset --hard`、`git checkout .`、`DROP TABLE` 或 `format` 命令。（`block-dangerous.sh` hook 自动执行此拦截。）

---

