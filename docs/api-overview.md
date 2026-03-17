# API 总览

SpringMall 后端提供 RESTful API，所有路径以 `/api/v1/` 为前缀

---

## 统一响应格式

所有接口返回 `Result<T>` 统一包装：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 状态码，200 表示成功 |
| `message` | `string` | 状态消息 |
| `data` | `T` | 响应数据，可为 null |

### 分页响应

分页查询返回 `Result<PageResult<T>>`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [ ... ],
    "total": 100,
    "page": 1,
    "size": 10,
    "pages": 10
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `list` | `List<T>` | 当前页数据列表 |
| `total` | `long` | 总记录数 |
| `page` | `int` | 当前页码 |
| `size` | `int` | 每页大小 |
| `pages` | `int` | 总页数（自动计算） |

分页请求参数：`?page=1&size=10`（由 PageHelper 处理）

---

## 错误码

### 通用状态码

| 状态码 | 常量名 | 说明 |
|--------|--------|------|
| `200` | `SUCCESS` | 成功 |
| `400` | `BAD_REQUEST` / `INVALID_PARAMETER` | 错误请求 / 无效参数 |
| `401` | `UNAUTHORIZED` | 未授权 |
| `403` | `FORBIDDEN` | 禁止访问 |
| `404` | `NOT_FOUND` | 资源未找到 |
| `500` | `INTERNAL_SERVER_ERROR` | 服务器内部错误 |

### 业务错误码分段

| 范围 | 模块 | 示例 |
|------|------|------|
| `40001` - `40099` | 用户 | `40001` 用户名已存在、`40005` 用户名或密码错误、`40006` 账户已禁用 |
| `40101` - `40199` | 商品 | `40101` 商品不存在、`40102` 已售罄、`40103` 库存不足、`40104` 已下架 |
| `40201` - `40299` | 分类 | `40201` 分类不存在、`40202` 有子分类不能删除、`40205` 名称重复 |
| `40301` - `40399` | 购物车 | `40301` 购物车项不存在、`40302` 购物车为空、`40303` 无已选中商品 |
| `40401` - `40499` | 地址 | `40401` 地址不存在 |
| `40501` - `40599` | 订单 | `40501` 订单不存在、`40502` 无效状态、`40505` 下单过于频繁 |
| `40601` - `40699` | 支付 | `40601` 支付失败、`40609` 锁获取失败、`40610` 已被其他方式支付 |
| `40701` - `40799` | 认证 | `40701` 无效 Token、`40702` Token 已过期 |
| `42900` | 限流 | 请求过于频繁 |

---

## 认证方式

采用 **JWT Bearer Token** 认证：

1. 调用 `POST /api/v1/auth/login` 获取 Token
2. 后续请求在 Header 中携带：`Authorization: Bearer <token>`
3. Token 有效期 24 小时(可自行修改)
4. 登出后 Token 加入黑名单（Redis 存储，TTL = 剩余有效期）

### 无需认证的接口

| 路径 | 说明 |
|------|------|
| `POST /api/v1/auth/login` | 登录 |
| `POST /api/v1/auth/register` | 注册 |
| `GET /api/v1/products/**` | 商品浏览 |
| `GET /api/v1/categories/**` | 分类浏览 |
| `GET /api/v1/health` | 健康检查 |
| `POST /api/v1/payment/*/notify` | 支付异步通知回调 |
| `POST /api/v1/payment/*/webhook` | Stripe Webhook |
| `/swagger-ui/**`、`/api-docs/**` | 接口文档 |

### 需要 ADMIN 角色的接口

所有 `/api/v1/admin/**` 路径需要 ADMIN 角色。

---

## API 端点汇总

### 认证模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/auth/register` | 用户注册 | 无 |
| POST | `/api/v1/auth/login` | 用户登录 | 无 |
| POST | `/api/v1/auth/logout` | 用户登出 | 需要 |

### 用户模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/user/profile` | 获取当前用户信息 | 需要 |
| GET | `/api/v1/user/{id}` | 获取指定用户信息 | 需要 |
| PUT | `/api/v1/user/profile` | 更新个人信息 | 需要 |
| PUT | `/api/v1/user/password` | 修改密码 | 需要 |

### 商品模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/products` | 商品列表（分页） | 无 |
| GET | `/api/v1/products/{id}` | 商品详情 | 无 |
| GET | `/api/v1/products/category/{categoryId}` | 按分类查询 | 无 |
| GET | `/api/v1/products/search` | 搜索商品 | 无 |

### 分类模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/categories` | 分类列表 | 无 |
| GET | `/api/v1/categories/tree` | 分类树形结构 | 无 |
| GET | `/api/v1/categories/parent/{parentId}` | 子分类列表 | 无 |
| GET | `/api/v1/categories/{id}` | 分类详情 | 无 |

### 购物车模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/cart` | 购物车列表 | 需要 |
| POST | `/api/v1/cart` | 添加商品到购物车 | 需要 |
| PUT | `/api/v1/cart/{id}/quantity` | 修改数量 | 需要 |
| PUT | `/api/v1/cart/{id}/checked` | 切换选中状态 | 需要 |
| PUT | `/api/v1/cart/checked` | 全选/取消全选 | 需要 |
| DELETE | `/api/v1/cart/{id}` | 删除单个 | 需要 |
| DELETE | `/api/v1/cart/batch` | 批量删除 | 需要 |
| DELETE | `/api/v1/cart` | 清空购物车 | 需要 |
| GET | `/api/v1/cart/total` | 购物车总价 | 需要 |
| GET | `/api/v1/cart/count` | 购物车数量 | 需要 |

### 收货地址模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/addresses` | 地址列表 | 需要 |
| GET | `/api/v1/addresses/default` | 默认地址 | 需要 |
| GET | `/api/v1/addresses/{id}` | 地址详情 | 需要 |
| POST | `/api/v1/addresses` | 新增地址 | 需要 |
| PUT | `/api/v1/addresses/{id}` | 更新地址 | 需要 |
| DELETE | `/api/v1/addresses/{id}` | 删除地址 | 需要 |
| PUT | `/api/v1/addresses/{id}/default` | 设为默认 | 需要 |

### 订单模块

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/orders` | 创建订单 | 需要 |
| GET | `/api/v1/orders` | 订单列表（分页） | 需要 |
| GET | `/api/v1/orders/status/{status}` | 按状态查询 | 需要 |
| GET | `/api/v1/orders/{orderNo}` | 订单详情 | 需要 |
| PUT | `/api/v1/orders/{orderNo}/cancel` | 取消订单 | 需要 |
| PUT | `/api/v1/orders/{orderNo}/confirm` | 确认收货 | 需要 |

### 支付模块 — 通用

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/payment/{paymentNo}` | 查询支付状态 | 需要 |

### 支付模块 — 支付宝

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/payment/alipay/create` | 创建支付宝支付 | 需要 |
| POST | `/api/v1/payment/alipay/notify` | 异步通知回调 | 无 |
| GET | `/api/v1/payment/alipay/return` | 同步跳转 | 无 |
| GET | `/api/v1/payment/alipay/{paymentNo}` | 查询支付状态 | 需要 |

### 支付模块 — Stripe

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/payment/stripe/create` | 创建 Stripe 支付 | 需要 |
| GET | `/api/v1/payment/stripe/{paymentNo}` | 查询支付状态 | 需要 |
| POST | `/api/v1/payment/stripe/webhook` | Webhook 回调 | 无 |
| POST | `/api/v1/payment/stripe/refund` | 申请退款 | 需要 |
| POST | `/api/v1/payment/stripe/refund/webhook` | 退款 Webhook | 无 |

### 支付模块 — 微信支付

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/v1/payment/wechat/native` | 创建 Native 支付 | 需要 |
| GET | `/api/v1/payment/wechat/{paymentNo}` | 查询支付状态 | 需要 |
| POST | `/api/v1/payment/wechat/notify` | 支付通知回调 | 无 |
| POST | `/api/v1/payment/wechat/refund` | 申请退款 | 需要 |
| POST | `/api/v1/payment/wechat/refund/notify` | 退款通知回调 | 无 |

### 管理端 — 用户管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/users` | 用户列表（分页） | ADMIN |
| GET | `/api/v1/admin/users/{id}` | 用户详情 | ADMIN |
| PUT | `/api/v1/admin/users/{id}/status` | 启用/禁用用户 | ADMIN |
| PUT | `/api/v1/admin/users/{id}/role` | 变更用户角色 | ADMIN |

### 管理端 — 商品管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/products` | 商品列表（分页） | ADMIN |
| GET | `/api/v1/admin/products/{id}` | 商品详情 | ADMIN |
| POST | `/api/v1/admin/products` | 新增商品 | ADMIN |
| PUT | `/api/v1/admin/products/{id}` | 更新商品 | ADMIN |
| DELETE | `/api/v1/admin/products/{id}` | 删除商品 | ADMIN |
| PUT | `/api/v1/admin/products/{id}/status` | 上架/下架 | ADMIN |
| PUT | `/api/v1/admin/products/{id}/stock` | 调整库存 | ADMIN |

### 管理端 — 分类管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/categories` | 分类列表 | ADMIN |
| GET | `/api/v1/admin/categories/{id}` | 分类详情 | ADMIN |
| POST | `/api/v1/admin/categories` | 新增分类 | ADMIN |
| PUT | `/api/v1/admin/categories/{id}` | 更新分类 | ADMIN |
| DELETE | `/api/v1/admin/categories/{id}` | 删除分类 | ADMIN |

### 管理端 — 订单管理

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/admin/orders` | 订单列表（分页） | ADMIN |
| GET | `/api/v1/admin/orders/status/{status}` | 按状态查询 | ADMIN |
| GET | `/api/v1/admin/orders/{orderNo}` | 订单详情 | ADMIN |
| PUT | `/api/v1/admin/orders/{orderNo}/ship` | 发货 | ADMIN |
| PUT | `/api/v1/admin/orders/{orderNo}/cancel` | 取消订单 | ADMIN |
| GET | `/api/v1/admin/orders/stats/total-sales` | 销售总额统计 | ADMIN |

### 系统

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/health` | 健康检查 | 无 |

---

## Swagger 文档

开发环境启用 Swagger UI：

- **Swagger UI**：`http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**：`http://localhost:8080/api-docs`

Docker 部署环境通过 Nginx 代理访问：`http://localhost:26115/swagger-ui/index.html`

> 注意：生产环境可通过 `application-prod.yml` 关闭 Swagger。
