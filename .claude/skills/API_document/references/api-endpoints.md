# API 接口详细文档

> 最后更新时间：2026-03-24

## 1. 认证模块 `/auth`

### 用户注册
```http
POST /api/v1/auth/register
```
**请求体**:
```json
{
  "username": "string",   // 必填，唯一
  "password": "string",   // 必填，最少6位
  "email": "string",      // 必填，邮箱格式
  "phone": "string"       // 可选，11位手机号
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com"
  }
}
```

### 用户登录
```http
POST /api/v1/auth/login
```
**请求体**:
```json
{
  "username": "string",
  "password": "string"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

### 用户登出
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

---

## 2. 用户模块 `/user` 🔒 USER

### 获取个人信息
```http
GET /api/v1/user/profile
Authorization: Bearer <token>
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "phone": "13800138000",
    "role": "USER",
    "status": 1,
    "avatar": "url"
  }
}
```

### 修改个人信息
```http
PUT /api/v1/user/profile
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "email": "string",
  "phone": "string",
  "avatar": "string"
}
```

### 修改密码
```http
PUT /api/v1/user/password
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "oldPassword": "string",
  "newPassword": "string"
}
```

---

## 3. 商品模块 `/products`

### 商品列表
```http
GET /api/v1/products?page=1&size=10
```
**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**响应**: PageResult
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "categoryId": 1,
        "categoryName": "手机数码",
        "name": "iPhone 15 Pro",
        "subtitle": "A17 Pro芯片，钛金属设计",
        "mainImage": "/images/iphone15pro.jpg",
        "price": 7999.00,
        "stock": 100,
        "status": 1
      }
    ],
    "total": 50,
    "page": 1,
    "size": 10,
    "pages": 5
  }
}
```

### 商品详情
```http
GET /api/v1/products/{id}
```
**响应** (包含额外字段):
```json
{
  "images": "['/images/1.jpg', '/images/2.jpg']",
  "detail": "<p>商品详情HTML</p>"
}
```

### 搜索商品
```http
GET /api/v1/products/search?keyword=iPhone&page=1&size=10
```
**查询参数**: `keyword`（必填）、`page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**搜索实现**: 使用 MySQL ngram 全文索引（`MATCH...AGAINST IN BOOLEAN MODE`），支持中文 2 字符分词，结果按相关性排序，次排序按销量降序。

**响应**: PageResult（结构同商品列表）

### 按分类查询
```http
GET /api/v1/products/category/{categoryId}
```

### 按状态查询
```http
GET /api/v1/products/status/{status}
```
- status: 0-下架, 1-上架

---

## 4. 分类模块 `/categories`

### 分类列表
```http
GET /api/v1/categories
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "电子产品",
      "parentId": 0,
      "level": 1,
      "sortOrder": 1,
      "status": 1
    }
  ]
}
```

### 分类树结构
```http
GET /api/v1/categories/tree
```
**响应** (递归结构):
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "电子产品",
      "children": [
        {
          "id": 4,
          "name": "手机",
          "children": []
        }
      ]
    }
  ]
}
```

### 获取子分类
```http
GET /api/v1/categories/parent/{parentId}
```

---

## 5. 购物车模块 `/cart` 🔒 USER

### 获取购物车
```http
GET /api/v1/cart
Authorization: Bearer <token>
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": 3,
      "productId": 1,
      "productName": "iPhone 15 Pro",
      "productSubtitle": "A17 Pro芯片，钛金属设计",
      "productImage": "/images/iphone15pro.jpg",
      "productPrice": 7999.00,
      "productStock": 100,
      "quantity": 2,
      "checked": 1,
      "subtotal": 15998.00,
      "createdAt": "2026-01-08T10:00:00"
    }
  ]
}
```

### 添加商品
```http
POST /api/v1/cart
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "productId": 1,
  "quantity": 2
}
```
**响应**: 返回单个 `CartItemResponse`（字段结构同上）。若商品已在购物车中，自动合并数量。

### 更新数量
```http
PUT /api/v1/cart/{id}/quantity?quantity=3
Authorization: Bearer <token>
```
**响应**: 返回更新后的 `CartItemResponse`。更新前校验库存是否充足。

### 更新选中状态
```http
PUT /api/v1/cart/{id}/checked?checked=0
Authorization: Bearer <token>
```
- `checked`: `0`-未选中，`1`-已选中

### 全选/取消全选
```http
PUT /api/v1/cart/checked?checked=1
Authorization: Bearer <token>
```
- `checked`: `0`-取消全选，`1`-全选

### 删除商品
```http
DELETE /api/v1/cart/{id}
Authorization: Bearer <token>
```

### 批量删除
```http
DELETE /api/v1/cart/batch?ids=1,2,3
Authorization: Bearer <token>
```

### 清空购物车
```http
DELETE /api/v1/cart
Authorization: Bearer <token>
```

### 获取已选中商品总价
```http
GET /api/v1/cart/total
Authorization: Bearer <token>
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": 15998.00
}
```

### 获取购物车商品种类数
```http
GET /api/v1/cart/count
Authorization: Bearer <token>
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": 2
}
```

---

## 6. 收货地址模块 `/addresses` 🔒 USER

### 地址列表
```http
GET /api/v1/addresses
Authorization: Bearer <token>
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "receiverName": "张三",
      "phone": "13800138000",
      "province": "北京市",
      "city": "北京市",
      "district": "朝阳区",
      "detailAddress": "某某街道123号",
      "isDefault": true
    }
  ]
}
```

### 地址详情
```http
GET /api/v1/addresses/{id}
Authorization: Bearer <token>
```

### 新增地址
```http
POST /api/v1/addresses
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "receiverName": "string",
  "phone": "string",
  "province": "string",
  "city": "string",
  "district": "string",
  "detailAddress": "string",
  "isDefault": false
}
```

### 修改地址
```http
PUT /api/v1/addresses/{id}
Authorization: Bearer <token>
```

### 删除地址
```http
DELETE /api/v1/addresses/{id}
Authorization: Bearer <token>
```

### 设为默认地址
```http
PUT /api/v1/addresses/{id}/default
Authorization: Bearer <token>
```

---

## 7. 订单模块 `/orders` 🔒 USER

### 创建订单
```http
POST /api/v1/orders
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "addressId": 1,
  "remark": "请尽快发货"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "orderNo": "20260108123456789",
    "totalAmount": 15998.00,
    "payAmount": 15998.00,
    "freight": 0.00,
    "status": "UNPAID",
    "statusDesc": "待支付",
    "items": [
      {
        "productId": 1,
        "productName": "iPhone 15 Pro",
        "quantity": 2,
        "unitPrice": 7999.00,
        "totalPrice": 15998.00
      }
    ]
  }
}
```

### 订单列表
```http
GET /api/v1/orders?page=1&size=10
Authorization: Bearer <token>
```
**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10）

**响应**: PageResult
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "orderNo": "20260108123456789",
        "totalAmount": 15998.00,
        "payAmount": 15998.00,
        "freight": 0.00,
        "status": "UNPAID",
        "statusDesc": "待支付",
        "items": [...]
      }
    ],
    "total": 25,
    "page": 1,
    "size": 10,
    "pages": 3
  }
}
```

### 按状态查询
```http
GET /api/v1/orders/status/{status}?page=1&size=10
Authorization: Bearer <token>
```
**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10）

**响应**: PageResult（结构同订单列表）

**订单状态**:
- `UNPAID` - 待支付
- `PAID` - 已支付
- `SHIPPED` - 已发货
- `COMPLETED` - 已完成
- `CANCELLED` - 已取消

### 订单详情
```http
GET /api/v1/orders/{orderNo}
Authorization: Bearer <token>
```

### 取消订单
```http
PUT /api/v1/orders/{orderNo}/cancel
Authorization: Bearer <token>
```
**路径参数**：`orderNo` - 订单号

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**：
1. 订单状态为 `UNPAID`（未支付）：
   - 恢复库存
   - 订单状态变为 `CANCELLED`
2. 订单状态为 `PAID`（已支付）：
   - **自动调用支付宝退款接口**
   - 创建退款记录
   - 更新支付状态为 `REFUNDED`
   - 恢复库存
   - 订单状态变为 `CANCELLED`

**限制**:
- 可取消待支付（UNPAID）和待发货（PAID）状态的订单
- 取消后订单状态变更为 CANCELLED
- 自动恢复所有商品库存
- 已发货（SHIPPED）和已完成（COMPLETED）订单无法取消

**错误响应**：
- `40501` - 订单不存在
- `40301` - 无权限访问该订单
- `40503` - 订单无法取消
- `40604` - 退款失败
- `40606` - 支付已退款

**注意**：退款为全额退款，金额等于订单实际支付金额，实时到账

### 确认收货
```http
PUT /api/v1/orders/{orderNo}/confirm
Authorization: Bearer <token>
```
**限制**: 只能确认已发货订单

### 获取历史归档订单
```http
GET /api/v1/orders/archive?page=1&size=10
Authorization: Bearer <token>
```
**查询参数**: `page`（页码，默认1）、`size`（每页大小，默认10）

**描述**: 查询已归档的历史订单（3个月前的已完成/已取消订单）

**响应**: PageResult
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "list": [
      {
        "id": 1,
        "orderNo": "ORD20240101001",
        "status": "COMPLETED",
        "totalAmount": 299.00,
        "items": [...],
        "createdAt": "2024-01-01T10:00:00"
      }
    ],
    "total": 50,
    "page": 1,
    "size": 10,
    "pages": 5
  }
}
```

---

## 8. 支付模块 `/payment` 🔒 USER

### 发起支付（模拟）
```http
POST /api/v1/payment/pay
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "orderNo": "20260108123456789",
  "paymentMethod": "MOCK"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "20260108123456789",
    "payAmount": 15998.00,
    "paymentMethod": "MOCK",
    "paymentStatus": "SUCCESS",
    "message": "支付成功",
    "transactionNo": "PAY1767846618551..."
  }
}
```

---

### 创建支付宝支付
```http
POST /api/v1/payment/alipay/create
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "orderNo": "20260108123456789"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "paymentNo": "PY20260215165739181457",
    "orderNo": "20260108123456789",
    "amount": 15998.00,
    "paymentMethod": "ALIPAY",
    "paymentStatus": "PENDING",
    "paymentUrl": "<!DOCTYPE html>..."
  }
}
```
**说明**：
- `paymentUrl` 字段包含支付宝表单 HTML
- 前端将 HTML 写入页面并自动提交，跳转到支付宝收银台

---

### 查询支付状态
```http
GET /api/v1/payment/{paymentNo}
Authorization: Bearer <token>
```
**路径参数**：`paymentNo` - 支付流水号

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "paymentNo": "PY20260215165739181457",
    "orderNo": "20260108123456789",
    "amount": 15998.00,
    "paymentMethod": "ALIPAY",
    "paymentStatus": "SUCCESS",
    "tradeNo": "2026021522001449950507527811",
    "createdAt": "2026-02-15T16:57:39"
  }
}
```
**字段说明**：
- `paymentMethod`：支付方式（`MOCK` / `ALIPAY` / `WECHAT`）
- `paymentStatus`：支付状态
  - `PENDING`：待支付
  - `SUCCESS`：支付成功
  - `FAILED`：支付失败
  - `CLOSED`：已关闭
  - `REFUNDED`：已退款
- `tradeNo`：第三方交易号（支付宝/微信支付）
- `codeUrl`：支付二维码链接（仅微信Native支付）

**使用场景**：用户从支付宝返回后，前端轮询查询支付结果（建议轮询间隔2秒，最大30次）

---

### 支付宝异步通知（回调）
```http
POST /api/v1/payment/alipay/notify
```
**说明**：
- 公开接口，由支付宝服务器调用
- 接收 `application/x-www-form-urlencoded` 格式
- 自动验证签名并更新订单状态

**响应**：`"success"` 或 `"failure"` 字符串

---

### 支付宝同步返回
```http
GET /api/v1/payment/alipay/return
```
**说明**：
- 公开接口，支付完成后跳转回商户网站
- 自动重定向到前端结果页：`/payment/result?paymentNo={paymentNo}`

---

### 创建微信支付 🔒 USER
```http
POST /api/v1/payment/wechat/native
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "orderNo": "20260108123456789",
  "description": "Spring Mall订单支付"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "paymentNo": "PY20260217123456789012",
    "orderNo": "20260108123456789",
    "amount": 15998.00,
    "paymentMethod": "WECHAT",
    "paymentStatus": "PENDING",
    "codeUrl": "weixin://wxpay/bizpayurl?pr=xxx"
  }
}
```
**说明**：
- `codeUrl` 为微信支付二维码链接，前端需生成二维码供用户扫码
- 微信支付没有沙箱环境，默认禁用（`wxpay.enabled=false`）
- 启用需配置真实商户信息

---

### 微信支付异步通知
```http
POST /api/v1/payment/wechat/notify
```
**说明**：公开接口，由微信支付服务器调用，自动验证签名并更新订单状态

---

### 微信退款
```http
POST /api/v1/payment/wechat/refund
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "paymentNo": "PY20260217123456789012",
  "refundAmount": 15998.00,
  "reason": "用户取消订单"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "refundNo": "RF20260217123456789012",
    "paymentNo": "PY20260217123456789012",
    "orderNo": "20260108123456789",
    "refundAmount": 15998.00,
    "refundStatus": "SUCCESS",
    "reason": "用户取消订单"
  }
}
```

---

### 微信退款异步通知
```http
POST /api/v1/payment/wechat/refund/notify
```
**说明**：公开接口，由微信支付服务器调用，接收退款结果通知

---

### 创建 Stripe 支付 🔒 USER
```http
POST /api/v1/payment/stripe/create
Authorization: Bearer <token>
```
**请求体**:
```json
{
  "orderNo": "20260108123456789"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "创建支付成功",
  "data": {
    "paymentNo": "PY20260220123456789012",
    "orderNo": "20260108123456789",
    "amount": 15998.00,
    "paymentMethod": "STRIPE",
    "paymentStatus": "PENDING",
    "clientSecret": "pi_3MtwBwLkdIwHu7ix28a3tqPa_secret_YrKJUKribcBjcG8HVhfZluoGH",
    "publishableKey": "pk_test_51SoiqALMrSCu04rI..."
  }
}
```
**说明**：
- `clientSecret`：用于前端 Stripe.js 确认支付
- `publishableKey`：用于初始化 Stripe.js
- 前端需要集成 `@stripe/stripe-js` 库处理支付流程

---

### 查询 Stripe 支付状态 🔒 USER
```http
GET /api/v1/payment/stripe/{paymentNo}
Authorization: Bearer <token>
```
**路径参数**：`paymentNo` - 支付流水号

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "paymentNo": "PY20260220123456789012",
    "orderNo": "20260108123456789",
    "amount": 15998.00,
    "paymentMethod": "STRIPE",
    "paymentStatus": "SUCCESS",
    "tradeNo": "pi_3MtwBwLkdIwHu7ix28a3tqPa",
    "createdAt": "2026-02-20T10:30:45"
  }
}
```
**说明**：
- `tradeNo`：Stripe Payment Intent ID（格式：`pi_xxx` 或 `cs_xxx`）
- 用于用户从 Stripe 返回后查询支付结果
- 建议轮询间隔：2 秒，最大轮询次数：30 次

---

### Stripe Webhook 回调
```http
POST /api/v1/payment/stripe/webhook
Stripe-Signature: t=1492774577,v1=5257a...
```
**说明**：
- 公开接口，由 Stripe 服务器调用
- 自动验证 Webhook 签名并更新订单状态
- 处理事件：`payment_intent.succeeded`、`payment_intent.payment_failed`、`payment_intent.canceled`

**响应**：`"success"` 字符串

**配置方法**：
1. Stripe Dashboard → Developers → Webhooks 添加端点
2. 设置端点 URL：`https://your-domain.com/api/v1/payment/stripe/webhook`
3. 选择监听事件：`payment_intent.succeeded`、`payment_intent.payment_failed`
4. 复制 Webhook 签名密钥到环境变量 `STRIPE_WEBHOOK_SECRET`

---

### 创建 Stripe 退款 🔒 ADMIN
```http
POST /api/v1/payment/stripe/refund
Authorization: Bearer <admin-token>
```
**请求体**:
```json
{
  "paymentNo": "PY20260220123456789012",
  "refundAmount": 15998.00,
  "reason": "用户取消订单"
}
```
**响应**:
```json
{
  "code": 200,
  "message": "退款申请成功",
  "data": {
    "refundNo": "RF20260220123456789012",
    "paymentNo": "PY20260220123456789012",
    "orderNo": "20260108123456789",
    "refundAmount": 15998.00,
    "refundStatus": "PROCESSING",
    "stripeRefundId": "re_3MtwBwLkdIwHu7ix28a3tqPa",
    "reason": "用户取消订单"
  }
}
```
**说明**：
- 支持全额退款和部分退款
- 退款金额不能超过支付金额
- 退款需要管理员权限（ADMIN 角色）
- Stripe 退款通常在 5-10 个工作日内到账

**退款状态**：
- `PROCESSING`：退款处理中
- `SUCCESS`：退款成功
- `FAILED`：退款失败

---

### Stripe 退款 Webhook 回调
```http
POST /api/v1/payment/stripe/refund/webhook
Stripe-Signature: t=1492774577,v1=5257a...
```
**说明**：
- 公开接口，由 Stripe 服务器调用
- 处理退款结果通知并更新退款状态
- 处理事件：`charge.refunded`

**响应**：`"success"` 字符串

**配置方法**：
1. Stripe Dashboard → Developers → Webhooks 添加退款端点
2. 设置端点 URL：`https://your-domain.com/api/v1/payment/stripe/refund/webhook`
3. 选择监听事件：`charge.refunded`
4. 复制 Webhook 签名密钥到环境变量 `STRIPE_REFUND_WEBHOOK_SECRET`

---

## 9. 后台 - 商品管理 `/admin/products` 🔒 ADMIN

### 商品列表（管理员）
```http
GET /api/v1/admin/products?page=1&size=10&keyword=iPhone&categoryId=1&status=ON_SALE
Authorization: Bearer <admin-token>
```
**查询参数**:
- `page`（页码，默认1）、`size`（每页数量，默认10，最大100）
- `keyword`（商品名称/副标题模糊搜索，可选）
- `categoryId`（分类ID过滤，可选）
- `status`（状态过滤，可选，值为 `ON_SALE` 或 `OFF_SALE`）

**响应**: PageResult（结构同公开商品列表）

### 商品详情（管理员）
```http
GET /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

### 新增商品
```http
POST /api/v1/admin/products
Authorization: Bearer <admin-token>
```
**请求体**:
```json
{
  "categoryId": 1,
  "name": "string",
  "subtitle": "string",
  "mainImage": "string",
  "images": "string",
  "detail": "string",
  "price": 7999.00,
  "stock": 100,
  "status": 1
}
```

### 修改商品
```http
PUT /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

### 删除商品
```http
DELETE /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

### 修改商品状态
```http
PUT /api/v1/admin/products/{id}/status?status=1
Authorization: Bearer <admin-token>
```

### 修改商品库存
```http
PUT /api/v1/admin/products/{id}/stock?stock=200
Authorization: Bearer <admin-token>
```

---

## 10. 后台 - 分类管理 `/admin/categories` 🔒 ADMIN

### 新增分类
```http
POST /api/v1/admin/categories
Authorization: Bearer <admin-token>
```
**请求体**:
```json
{
  "name": "string",
  "parentId": 0,
  "sortOrder": 1
}
```

### 修改分类
```http
PUT /api/v1/admin/categories/{id}
Authorization: Bearer <admin-token>
```

### 删除分类
```http
DELETE /api/v1/admin/categories/{id}
Authorization: Bearer <admin-token>
```
**限制**: 有子分类或有商品的分类不能删除

---

## 11. 后台 - 订单管理 `/admin/orders` 🔒 ADMIN

### 所有订单列表
```http
GET /api/v1/admin/orders?page=1&size=10
Authorization: Bearer <admin-token>
```
**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**响应**: PageResult。列表项**不包含 `items`**（订单明细），仅详情接口返回。

### 按状态查询订单
```http
GET /api/v1/admin/orders/status/{status}?page=1&size=10
Authorization: Bearer <admin-token>
```
**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**响应**: PageResult。列表项**不包含 `items`**，同上。

### 订单详情（管理员）
```http
GET /api/v1/admin/orders/{orderNo}
Authorization: Bearer <admin-token>
```

### 订单发货
```http
PUT /api/v1/admin/orders/{orderNo}/ship
Authorization: Bearer <admin-token>
```
**限制**: 只能发货已支付订单

### 取消订单（管理员）
```http
PUT /api/v1/admin/orders/{orderNo}/cancel
Authorization: Bearer <admin-token>
```
**路径参数**：`orderNo` - 订单号

**说明**: 管理员可取消待支付和待发货状态的订单，取消后自动恢复库存

**业务逻辑**：
1. 订单状态为 `UNPAID`（未支付）：
   - 恢复库存
   - 订单状态变为 `CANCELLED`
2. 订单状态为 `PAID`（已支付）：
   - **自动调用支付宝退款接口**
   - 创建退款记录
   - 更新支付状态为 `REFUNDED`
   - 恢复库存
   - 订单状态变为 `CANCELLED`

**限制**:
- 只能取消 UNPAID（待支付）或 PAID（待发货）状态的订单
- 取消后订单状态变更为 CANCELLED
- 自动恢复所有商品库存

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**错误响应**：
- `40501` - 订单不存在
- `40503` - 订单无法取消
- `40604` - 退款失败
- `40606` - 支付已退款

---

## 12. 后台 - 用户管理 `/admin/users` 🔒 ADMIN

### 用户列表
```http
GET /api/v1/admin/users?page=1&size=10&keyword=test&role=USER&status=1
Authorization: Bearer <admin-token>
```
**查询参数**:
- `page`（页码，默认1）、`size`（每页数量，默认10，最大100）
- `keyword`（用户名/邮箱模糊搜索，可选）
- `role`（角色过滤，可选，值为 `USER` 或 `ADMIN`）
- `status`（状态过滤，可选，`1`-启用，`0`-禁用）

**响应**: PageResult
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "username": "testuser",
        "email": "test@example.com",
        "phone": "13800138000",
        "role": "USER",
        "status": 1,
        "createdAt": "2026-01-08T10:00:00"
      }
    ],
    "total": 3,
    "page": 1,
    "size": 10,
    "pages": 1
  }
}
```

### 用户详情
```http
GET /api/v1/admin/users/{id}
Authorization: Bearer <admin-token>
```

### 修改用户状态
```http
PUT /api/v1/admin/users/{id}/status?status=0
Authorization: Bearer <admin-token>
```
- status: 0-禁用, 1-启用

### 修改用户角色
```http
PUT /api/v1/admin/users/{id}/role?role=ADMIN
Authorization: Bearer <admin-token>
```
- role: USER, ADMIN

---

## 使用示例

### 完整购物流程代码
```javascript
// 1. 注册/登录
const auth = await request.post('/auth/login', {
  username: 'testuser',
  password: '123456'
});
localStorage.setItem('accessToken', auth.data.accessToken);

// 2. 浏览商品
const products = await request.get('/products');

// 3. 添加到购物车
await request.post('/cart', {
  productId: 1,
  quantity: 2
});

// 4. 查看购物车
const cart = await request.get('/cart');

// 5. 创建订单
const order = await request.post('/orders', {
  addressId: 1,
  remark: '请尽快发货'
});

// 6. 支付
const payment = await request.post('/payment/pay', {
  orderNo: order.data.orderNo,
  paymentMethod: 'MOCK'
});

// 7. 查看订单
const orderDetail = await request.get(`/orders/${order.data.orderNo}`);
```