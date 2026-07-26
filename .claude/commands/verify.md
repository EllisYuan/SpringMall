---
description: 触发 acceptance-validator 子 agent 做编译/构建门禁 + 需求验收
argument-hint: "[可选：变更名 openspec/changes/<feature>]"
---

使用 **acceptance-validator** 子 agent 验证当前变更：

**L1 编译/构建门禁**（失败即止）
- 后端：`cd mall-backend ; .\mvnw.cmd compile`
- 前端：`cd mall-frontend ; pnpm run build`

**L2 需求验收（黑盒功能测试）**
- 依据 `openspec/changes/$ARGUMENTS/specs`（验收场景）与 `tasks.md`，对实际运行的接口做功能验证：
  - 必要时先 `POST /api/v1/auth/login` 取 token，再用 `curl` 命中新增/改动接口；
  - 断言响应结构、状态码、`Result<T>` 合约与 specs 一致；
  - 涉及数据副作用（下单扣库存、取消恢复库存、支付状态流转等）用 MySQL MCP 核对数据库实际变化。
- 产出"需求符合性"结论，而非仅"能编译"。

输出格式：L1 / L2 各自的 通过 | 失败（附错误行/不符项）+ 总体结论。**不修改源代码让测试通过。**
