---
description: 触发 acceptance-validator 子 agent 做编译/构建门禁 + 需求验收
argument-hint: "[可选：验收标准，或 GitHub Issue 编号]"
---

使用 **acceptance-validator** 子 agent 验证当前变更——L1/L2 的执行步骤与报告格式见其 agent 定义。

1. 收集变更范围：`git status`、`git diff`。
2. 转交验收依据：本轮对话中已确立的验收标准、backend-dev 交接的 API 合约，或 $ARGUMENTS 指定的 Issue 编号。
3. 依据缺失时，要求它报告"缺少验收依据"而非臆断通过。
