---
description: 触发 code-reviewer 子 agent 对当前改动做只读安全/规范审查
argument-hint: "[可选：聚焦的文件或模块]"
---

使用 **code-reviewer** 子 agent 审查当前工作区改动。

1. 先收集变更：`git status` 与 `git diff`（如有暂存改动也包含 `git diff --cached`）。
2. 将变更文件清单交给 code-reviewer 子 agent，要求其：
   - 对照 `.claude/rules/` 检查偏差（规范基线）；
   - 重点安全视角：MyBatis `${}` 注入、`SecurityConfig` 的 `permitAll` 错配、Vue `v-html` XSS、前端对已解包响应的双重 `.data` 解包、VO 敏感字段泄露、`Result<T>` 合约对接；
   - 必要时用 MySQL MCP 核对 Mapper/Entity 列名与类型是否与真实表一致；
   - 若存在 `openspec/changes/<feature>/`，核对实现是否覆盖其 specs/tasks 验收项。
3. 输出 BLOCKER / WARNING / INFO 分级报告与裁定结果。**只读，不修改任何文件。**

聚焦范围（若提供）：$ARGUMENTS
