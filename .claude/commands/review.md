---
description: 触发 code-reviewer 子 agent 对当前改动做只读安全/规范审查
argument-hint: "[可选：聚焦的文件或模块]"
---

使用 **code-reviewer** 子 agent 审查当前工作区改动。

1. 收集变更：`git status`、`git diff`、`git diff --cached`。
2. 将变更文件清单交给 code-reviewer——审查基线、安全视角与输出格式见其 agent 定义。
3. 若本轮对话中已明确验收标准，一并转给它对照检查。

聚焦范围（若提供）：$ARGUMENTS
