---
description: 全局安全红线，任何 agent 与主 session 均不得违反
alwaysApply: true
---

# 安全红线（不可覆盖）

以下规则任何 agent 均无权违反：

1. **`.env` 保护**：绝不修改、创建或 commit `.env`（或 `.env.test` / `.env.prod`）。只能编辑 `.env.example`，并提示开发者手动复制。新配置项在 `.env.example` 中加注释与安全默认值。

2. **密钥保护**：绝不 commit 任何密钥 —— JWT Secret、数据库密码、API Key、支付私钥（Alipay/Stripe）。若暂存区检测到，立即中止并告警。

3. **数据库变更安全**：绝不在未写明回滚方案的情况下修改 `schema.sql` 或任何数据库迁移脚本。

4. **危险命令**：绝不在未获用户明确确认下执行 `rm -rf`、`git reset --hard`、`git checkout .`、`git restore .`、`git clean -f`、`git push --force`、`DROP TABLE/DATABASE`、`docker compose down -v`、磁盘 `format`。`hooks/block-dangerous.sh` 会自动拦截这些命令。

5. **MCP 数据库写操作**：`@benborla29/mcp-server-mysql` 已开启读写，**仅限连接本机开发库 `mall`**。绝不将 MCP 指向测试/生产库执行写操作。

6. **敏感数据不外泄**：Controller 必须返回 VO（不含 password 等敏感字段），不得直接返回 DO 实体；日志不在 INFO 级别记录密码或 Token。
