---
description: 触发 devops-deploy 子 agent 做部署前配置一致性检查
---

使用 **devops-deploy** 子 agent 对部署配置做一致性检查（仅校验，不执行破坏性部署操作）：

1. `docker-compose.yml` 端口映射与 `docs/docker-deploy.md` 是否一致（后端 25116、前端 26115）。
2. healthcheck 端点在应用中是否真实存在（注意：应用未引入 actuator，正确端点为 `/api/v1/categories`）。
3. `docker-compose.yml` 环境变量名与 `.env.example` 是否一致。
4. Nginx 是否代理了全部必需路径（`/api/`、`/swagger-ui/`、`/api-docs/`、`/v3/api-docs/`、`/webjars/`）。
5. Dockerfile/compose 中是否有硬编码密钥（必须经 `.env` 插值）。
6. `extra_hosts`、`depends_on: service_healthy` 等关键项未被破坏。

输出问题清单与是否可部署的结论。
