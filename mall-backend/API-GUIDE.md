# Spring Mall API 接口文档

> 最后更新时间：2026-04-05

## 项目概述

Spring Mall 是一个完整的在线商城后端系统，基于 Spring Boot 3.3.6 + MyBatis 3.5.17 开发。

**技术栈**: Spring Boot, Spring Security, JWT, MyBatis, MySQL

**基础路径**: `http://localhost:8080/api/v1`

**openapi:** `http://localhost:8080/api-docs`

---

## 统一响应格式

### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### 错误响应
```json
{
  "code": 40001,
  "message": "用户名已存在",
  "data": null
}
```

### 分页响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [],
    "total": 100,
    "page": 1,
    "size": 10,
    "pages": 10
  }
}
```

---

## 认证说明

### Token 获取
登录成功后获得 JWT Token，在后续请求中携带：

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 角色权限
- **公开接口**: 无需认证
- **USER**: 普通用户权限
- **ADMIN**: 管理员权限

---

## API 接口清单

### 1. 认证模块 `/api/v1/auth`

#### 1.1 用户注册
```http
POST /api/v1/auth/register
```

**请求体**:
```json
{
  "username": "testuser",
  "password": "123456",
  "email": "test@example.com",
  "phone": "13800138000"
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

#### 1.2 用户登录
```http
POST /api/v1/auth/login
```

**请求体**:
```json
{
  "username": "testuser",
  "password": "123456"
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

#### 1.3 用户登出
```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

---

### 2. 用户模块 `/api/v1/user`

#### 2.1 获取个人信息
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
    "status": 1
  }
}
```

#### 2.2 修改个人信息
```http
PUT /api/v1/user/profile
Authorization: Bearer <token>
```

**请求体**:
```json
{
  "email": "newemail@example.com",
  "phone": "13900139000",
  "avatar": "https://example.com/avatar.jpg"
}
```

#### 2.3 修改密码
```http
PUT /api/v1/user/password
Authorization: Bearer <token>
```

**请求体**:
```json
{
  "oldPassword": "123456",
  "newPassword": "654321"
}
```

---

### 3. 商品模块 `/api/v1/products`

#### 3.1 商品列表
```http
GET /api/v1/products?page=1&size=10&sortBy=sales&sortDir=desc
```

**查询参数**:
- `page`（页码，默认1）
- `size`（每页数量，默认10，最大100）
- `keyword`（商品名称/副标题模糊搜索，可选）
- `categoryId`（分类ID过滤，可选）
- `sortBy`（排序字段，可选，支持：`price`、`sales`、`created_at`，默认 `created_at`）
- `sortDir`（排序方向，可选，`asc` 或 `desc`，默认 `desc`）

**说明**: 用户端接口硬编码 `status=1`，仅返回上架商品

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
        "salesCount": 156,
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

#### 3.2 商品详情
```http
GET /api/v1/products/{id}
```

**响应**（启用 SKU 的商品）:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 107,
    "categoryId": 1,
    "name": "测试多规格商品",
    "price": 299.00,
    "stock": 100,
    "status": 1,
    "salesCount": 10,
    "hasSku": 1,
    "minPrice": 199.00,
    "maxPrice": 399.00,
    "specs": [
      {
        "id": 1,
        "name": "颜色",
        "values": [
          { "id": 55, "value": "红色" },
          { "id": 56, "value": "蓝色" }
        ]
      },
      {
        "id": 2,
        "name": "尺寸",
        "values": [
          { "id": 60, "value": "M" },
          { "id": 61, "value": "L" }
        ]
      }
    ],
    "skuList": [
      {
        "id": 1,
        "skuCode": "SKU-001",
        "specValueIds": [55, 60],
        "specDesc": "红色,M",
        "price": 199.00,
        "stock": 10,
        "image": null,
        "status": 1,
        "isDefault": true
      }
    ]
  }
}
```

**说明**:
- `hasSku=1` 时，前端应使用 `specs` + `skuList` 渲染规格选择器，价格和库存取 SKU 维度
- `hasSku=0` 时，不返回 `specs`/`skuList`，价格和库存取商品维度
- `minPrice`/`maxPrice` 为所有启用 SKU 的价格区间

#### 3.3 搜索商品
```http
GET /api/v1/products/search?keyword=iPhone&page=1&size=10
```
**查询参数**: `keyword`（必填）、`page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**搜索实现**: 使用 MySQL ngram 全文索引（`MATCH(name, subtitle) AGAINST(keyword IN BOOLEAN MODE)`），支持中文 2 字符分词。结果按相关性排序，次排序按销量降序。

**响应**: PageResult（结构同 3.1）

#### 3.4 按分类查询
```http
GET /api/v1/products/category/{categoryId}
```

---

### 4. 分类模块 `/api/v1/categories`

#### 4.1 分类列表
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

#### 4.2 分类树结构
```http
GET /api/v1/categories/tree
```

**响应** (带子分类):
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

#### 4.3 获取子分类
```http
GET /api/v1/categories/parent/{parentId}
```

---

### 5. 购物车模块 `/api/v1/cart` 🔒 USER

#### 5.1 获取购物车
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
      "productId": 1,
      "productName": "iPhone 15 Pro",
      "productImage": "/images/iphone15pro.jpg",
      "productPrice": 7999.00,
      "productStock": 100,
      "quantity": 2,
      "subtotal": 15998.00,
      "checked": 1,
      "skuId": 0,
      "specDesc": null,
      "skuImage": null
    },
    {
      "id": 2,
      "productId": 107,
      "productName": "测试多规格商品",
      "productImage": "/images/test.jpg",
      "productPrice": 199.00,
      "productStock": 10,
      "quantity": 1,
      "subtotal": 199.00,
      "checked": 1,
      "skuId": 1,
      "specDesc": "红色,M",
      "skuImage": null
    }
  ]
}
```

**说明**: 有 SKU 的购物车项，`productPrice`/`productStock` 取 SKU 维度数据，`specDesc` 显示规格描述

#### 5.2 添加商品
```http
POST /api/v1/cart
Authorization: Bearer <token>
```

**请求体**（无 SKU 商品）:
```json
{
  "productId": 1,
  "quantity": 2
}
```

**请求体**（有 SKU 商品）:
```json
{
  "productId": 107,
  "quantity": 1,
  "skuId": 1
}
```

**说明**: 有 SKU 的商品（`hasSku=1`）必须传 `skuId`，否则返回 `40153 SKU_REQUIRED`

#### 5.3 修改数量
```http
PUT /api/v1/cart/{id}
Authorization: Bearer <token>
```

**请求体**:
```json
{
  "quantity": 3
}
```

#### 5.4 删除商品
```http
DELETE /api/v1/cart/{id}
Authorization: Bearer <token>
```

#### 5.5 清空购物车
```http
DELETE /api/v1/cart
Authorization: Bearer <token>
```

#### 5.6 选中/取消选中
```http
PUT /api/v1/cart/check/{id}?checked=true
Authorization: Bearer <token>
```

#### 5.7 全选/取消全选
```http
PUT /api/v1/cart/check-all?checked=true
Authorization: Bearer <token>
```

---

### 6. 收货地址模块 `/api/v1/addresses` 🔒 USER

#### 6.1 地址列表
```http
GET /api/v1/addresses
Authorization: Bearer <token>
```

#### 6.2 地址详情
```http
GET /api/v1/addresses/{id}
Authorization: Bearer <token>
```

#### 6.3 新增地址
```http
POST /api/v1/addresses
Authorization: Bearer <token>
```

**请求体**:
```json
{
  "receiverName": "张三",
  "phone": "13800138000",
  "province": "北京市",
  "city": "北京市",
  "district": "朝阳区",
  "detailAddress": "某某街道123号",
  "isDefault": false
}
```

#### 6.4 修改地址
```http
PUT /api/v1/addresses/{id}
Authorization: Bearer <token>
```

#### 6.5 删除地址
```http
DELETE /api/v1/addresses/{id}
Authorization: Bearer <token>
```

#### 6.6 设为默认地址
```http
PUT /api/v1/addresses/{id}/default
Authorization: Bearer <token>
```

---

### 7. 订单模块 `/api/v1/orders` 🔒 USER

#### 7.1 创建订单
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
        "skuId": 0,
        "specDesc": null,
        "productName": "iPhone 15 Pro",
        "quantity": 2,
        "unitPrice": 7999.00,
        "totalPrice": 15998.00
      }
    ]
  }
}
```

**说明**: 订单明细中 `skuId > 0` 表示该商品为 SKU 商品，`specDesc` 显示规格描述（如"红色,M"）

#### 7.2 订单列表
```http
GET /api/v1/orders?page=1&size=10
Authorization: Bearer <token>
```

**查询参数**:
- `page`（页码，默认1）
- `size`（每页数量，默认10）

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
        "items": [
          {
            "productId": 1,
            "skuId": 0,
            "specDesc": null,
            "productName": "iPhone 15 Pro",
            "quantity": 2,
            "unitPrice": 7999.00,
            "totalPrice": 15998.00
          }
        ]
      }
    ],
    "total": 25,
    "page": 1,
    "size": 10,
    "pages": 3
  }
}
```

**说明**: 订单列表仅返回热表（近期）数据。已归档的历史订单需通过订单详情接口（7.4）按 orderNo 精确查询获取。

#### 7.3 按状态查询
```http
GET /api/v1/orders/status/{status}?page=1&size=10
Authorization: Bearer <token>
```

**查询参数**:
- `page`（页码，默认1）
- `size`（每页数量，默认10）

**响应**: PageResult（结构同 7.2）

**订单状态**:
- `UNPAID` - 待支付
- `PAID` - 已支付
- `SHIPPED` - 已发货
- `COMPLETED` - 已完成
- `CANCELLED` - 已取消

#### 7.4 订单详情
```http
GET /api/v1/orders/{orderNo}
Authorization: Bearer <token>
```

**说明**: 先查热表，若未找到则自动回查归档表（`mall_order_archive`），对用户透明。

#### 7.5 取消订单
```http
PUT /api/v1/orders/{orderNo}/cancel
Authorization: Bearer <token>
```

**路径参数**：
- `orderNo`：订单号

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**业务逻辑**：
1. 如果订单状态为 `UNPAID`（未支付）：
   - 恢复库存
   - 订单状态变为 `CANCELLED`
2. 如果订单状态为 `PAID`（已支付）：
   - **自动调用退款接口**（根据支付方式自动选择支付宝/Stripe/微信）
   - 创建退款记录
   - 更新支付记录状态为 `REFUNDED`
   - 恢复库存
   - **扣减对应商品的累计销量（salesCount）**
   - 订单状态变为 `CANCELLED`

**限制**:
- 用户可以取消待支付（UNPAID）和待发货（PAID）状态的订单
- 取消后订单状态变更为 CANCELLED
- 自动恢复所有商品库存
- 已发货（SHIPPED）和已完成（COMPLETED）订单无法取消

**错误响应**：
- `40501` - 订单不存在
- `40301` - 无权限访问该订单
- `40503` - 订单无法取消（订单已发货）
- `40604` - 退款失败（支付宝退款接口调用失败）
- `40606` - 支付已退款（重复退款）

**注意事项**：
- 退款为全额退款，金额等于订单实际支付金额
- 退款成功后，用户支付宝账户将收到退款
- 退款处理时间：实时到账

#### 7.6 确认收货
```http
PUT /api/v1/orders/{orderNo}/confirm
Authorization: Bearer <token>
```

**限制**: 只能确认已发货订单

#### 7.7 获取历史归档订单

- **URL**: `GET /api/v1/orders/archive`
- **认证**: 需要 USER 角色
- **描述**: 查询已归档的历史订单（3个月前的已完成/已取消订单）

**请求参数**:
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 1 | 页码 |
| size | int | 否 | 10 | 每页大小 |

**响应示例**:
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

### 8. 支付模块 `/api/v1/payment`

#### 8.1 发起支付（模拟）
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

#### 8.2 创建支付宝支付
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
    "paymentUrl": "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"utf-8\">\n</head>\n<body>\n<form name=\"punchout_form\" method=\"post\" action=\"https://openapi-sandbox.dl.alipaydev.com/gateway.do\">\n    <input type=\"hidden\" name=\"biz_content\" value=\"...\">\n    <input type=\"hidden\" name=\"sign\" value=\"...\">\n    ...\n</form>\n<script>document.forms[0].submit();</script>\n</body>\n</html>"
  }
}
```

**说明**：
- 返回的 `paymentUrl` 字段包含支付宝表单 HTML
- 前端收到响应后，将 HTML 写入页面并自动提交
- 用户浏览器将跳转到支付宝收银台

**使用示例**：
```javascript
// 前端代码示例
async function createPayment(orderNo) {
  const response = await fetch('/api/v1/payment/alipay/create', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ orderNo })
  });

  const result = await response.json();
  const html = result.data.paymentUrl;

  // 将支付宝表单写入页面并自动提交
  const div = document.createElement('div');
  div.innerHTML = html;
  document.body.appendChild(div);
}
```

#### 8.3 查询支付状态
```http
GET /api/v1/payment/{paymentNo}
Authorization: Bearer <token>
```

**路径参数**：
- `paymentNo`：支付流水号

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
- `paymentNo`：支付流水号
- `orderNo`：订单号
- `amount`：支付金额
- `paymentMethod`：支付方式（`MOCK` / `ALIPAY` / `WECHAT`）
- `paymentStatus`：支付状态
  - `PENDING`：待支付
  - `SUCCESS`：支付成功
  - `FAILED`：支付失败
  - `CLOSED`：已关闭
  - `REFUNDED`：已退款
- `tradeNo`：第三方交易号（支付宝/微信支付）
- `codeUrl`：支付二维码链接（仅微信Native支付）
- `createdAt`：支付创建时间

**使用场景**：
- 用户从支付宝收银台返回后，前端轮询查询支付结果
- 建议轮询间隔：2 秒
- 建议最大轮询次数：30 次（1 分钟）

#### 8.4 支付宝异步通知（回调）
```http
POST /api/v1/payment/alipay/notify
```

**说明**：
- 该接口为公开接口，由支付宝服务器调用
- 接收 `application/x-www-form-urlencoded` 格式请求
- 自动验证签名并更新订单状态

**响应**：
- 成功：`"success"` 字符串
- 失败：`"failure"` 字符串

#### 8.5 支付宝同步返回
```http
GET /api/v1/payment/alipay/return
```

**说明**：
- 该接口为公开接口，支付宝支付完成后跳转回商户网站
- 自动重定向到前端结果页：`/payment/result?paymentNo={paymentNo}`

#### 8.6 创建微信支付 🔒 USER
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
- 返回的 `codeUrl` 字段为微信支付二维码链接
- 前端需要将此链接生成二维码供用户扫码支付
- 用户使用微信扫码后完成支付

**使用示例**：
```javascript
// 前端代码示例（使用 qrcode 库）
import QRCode from 'qrcode';

async function createWxPayment(orderNo) {
  const response = await fetch('/api/v1/payment/wechat/native', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      orderNo,
      description: 'Spring Mall订单支付'
    })
  });

  const result = await response.json();
  const codeUrl = result.data.codeUrl;

  // 生成二维码
  const qrCodeDataUrl = await QRCode.toDataURL(codeUrl);

  // 显示二维码供用户扫描
  document.getElementById('qrcode').src = qrCodeDataUrl;
}
```

**注意事项**：
- 微信支付没有沙箱环境，需要真实商户号才能测试
- 模块默认禁用（`wxpay.enabled=false`），需要在配置中启用
- 启用方法：在 `.env` 文件中设置 `WXPAY_ENABLED=true` 并配置真实的商户信息

#### 8.7 微信支付异步通知（回调）
```http
POST /api/v1/payment/wechat/notify
```

**说明**：
- 该接口为公开接口，由微信支付服务器调用
- 接收微信支付平台证书加密的通知数据
- 自动验证签名并更新订单状态

**响应**：
- 成功：`{"code": "SUCCESS", "message": "成功"}`
- 失败：`{"code": "FAIL", "message": "失败原因"}`

#### 8.8 微信退款
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

#### 8.9 微信退款异步通知（回调）
```http
POST /api/v1/payment/wechat/refund/notify
```

**说明**：
- 该接口为公开接口，由微信支付服务器调用
- 接收退款结果通知并更新退款状态

#### 8.10 支付回调（模拟）
```http
POST /api/v1/payment/notify
```

**请求体**:
```json
{
  "orderNo": "20260108123456789",
  "transactionNo": "TEST123456789",
  "paymentStatus": "SUCCESS",
  "timestamp": 1767846694000
}
```

#### 8.11 创建 Stripe 支付 🔒 USER
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
- 返回的 `clientSecret` 用于前端 Stripe.js 确认支付
- 返回的 `publishableKey` 用于初始化 Stripe.js
- 前端需要集成 `@stripe/stripe-js` 库处理支付流程

**使用示例**：
```javascript
// 前端代码示例（使用 @stripe/stripe-js）
import { loadStripe } from '@stripe/stripe-js';

async function createStripePayment(orderNo) {
  // 调用后端接口创建支付
  const response = await fetch('/api/v1/payment/stripe/create', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ orderNo })
  });

  const result = await response.json();
  const { clientSecret, publishableKey } = result.data;

  // 初始化 Stripe
  const stripe = await loadStripe(publishableKey);

  // 创建支付元素
  const elements = stripe.elements({ clientSecret });
  const paymentElement = elements.create('payment');
  paymentElement.mount('#payment-element');

  // 确认支付
  const { error } = await stripe.confirmPayment({
    elements,
    confirmParams: {
      return_url: 'https://your-domain.com/payment/result'
    }
  });

  if (error) {
    console.error('支付失败:', error.message);
  }
}
```

#### 8.12 查询 Stripe 支付状态 🔒 USER
```http
GET /api/v1/payment/stripe/{paymentNo}
Authorization: Bearer <token>
```

**路径参数**：
- `paymentNo`：支付流水号

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

**字段说明**：
- `tradeNo`：Stripe Payment Intent ID（格式：`pi_xxx`）
- 其他字段与 8.3 相同

**使用场景**：
- 用户从 Stripe 支付页面返回后，前端查询支付结果
- 建议查询间隔：2 秒
- 建议最大查询次数：30 次（1 分钟）

#### 8.13 Stripe Webhook 回调
```http
POST /api/v1/payment/stripe/webhook
Stripe-Signature: t=1492774577,v1=5257a...
```

**说明**：
- 该接口为公开接口，由 Stripe 服务器调用
- 自动验证 Webhook 签名并更新订单状态
- 处理事件：`payment_intent.succeeded`、`payment_intent.payment_failed`、`payment_intent.canceled`

**响应**：
- 成功：`"success"` 字符串
- 失败：`"success"` 字符串（Stripe 要求始终返回 200，避免重试）

**配置方法**：
1. 在 Stripe Dashboard → Developers → Webhooks 中添加端点
2. 设置端点 URL：`https://your-domain.com/api/v1/payment/stripe/webhook`
3. 选择监听事件：`payment_intent.succeeded`、`payment_intent.payment_failed`
4. 复制 Webhook 签名密钥到环境变量 `STRIPE_WEBHOOK_SECRET`

#### 8.14 创建 Stripe 退款 🔒 ADMIN
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

#### 8.15 Stripe 退款 Webhook 回调
```http
POST /api/v1/payment/stripe/refund/webhook
Stripe-Signature: t=1492774577,v1=5257a...
```

**说明**：
- 该接口为公开接口，由 Stripe 服务器调用
- 处理退款结果通知并更新退款状态
- 处理事件：`charge.refunded`

**响应**：
- 成功：`"success"` 字符串

**配置方法**：
1. 在 Stripe Dashboard → Developers → Webhooks 中添加退款端点
2. 设置端点 URL：`https://your-domain.com/api/v1/payment/stripe/refund/webhook`
3. 选择监听事件：`charge.refunded`
4. 复制 Webhook 签名密钥到环境变量 `STRIPE_REFUND_WEBHOOK_SECRET`

---

### 9. 后台管理 - 商品 `/api/v1/admin/products` 🔒 ADMIN

#### 9.1 商品列表（管理员）
```http
GET /api/v1/admin/products?page=1&size=10&keyword=iPhone&categoryId=1&status=ON_SALE
Authorization: Bearer <admin-token>
```

**查询参数**:
- `page`（页码，默认1）、`size`（每页数量，默认10，最大100）
- `keyword`（商品名称/副标题模糊搜索，可选）
- `categoryId`（分类ID过滤，可选）
- `status`（状态过滤，可选，值为 `ON_SALE` 或 `OFF_SALE`）

**响应**: PageResult（结构同 3.1）

#### 9.2 商品详情（管理员）
```http
GET /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

#### 9.3 新增商品
```http
POST /api/v1/admin/products
Authorization: Bearer <admin-token>
```

**请求体**:
```json
{
  "categoryId": 1,
  "name": "iPhone 15 Pro",
  "subtitle": "A17 Pro芯片，钛金属设计",
  "mainImage": "/images/iphone15pro.jpg",
  "images": "['/images/1.jpg', '/images/2.jpg']",
  "detail": "<p>商品详情HTML</p>",
  "price": 7999.00,
  "stock": 100,
  "status": 1
}
```

#### 9.4 修改商品
```http
PUT /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

#### 9.5 删除商品
```http
DELETE /api/v1/admin/products/{id}
Authorization: Bearer <admin-token>
```

#### 9.6 修改商品状态
```http
PUT /api/v1/admin/products/{id}/status?status=1
Authorization: Bearer <admin-token>
```
- status: 0-下架, 1-上架

#### 9.7 修改商品库存
```http
PUT /api/v1/admin/products/{id}/stock?stock=200
Authorization: Bearer <admin-token>
```

#### 9.8 保存商品 SKU 配置（全量替换）
```http
PUT /api/v1/admin/products/{id}/sku-config
Authorization: Bearer <admin-token>
```

**请求体**:
```json
{
  "specs": [
    {
      "name": "颜色",
      "values": ["红色", "蓝色"]
    },
    {
      "name": "尺寸",
      "values": ["M", "L"]
    }
  ],
  "skuList": [
    {
      "specValueIds": [55, 60],
      "price": 199.00,
      "stock": 10,
      "skuCode": "SKU-001",
      "image": null,
      "isDefault": true
    },
    {
      "specValueIds": [55, 61],
      "price": 219.00,
      "stock": 5,
      "skuCode": "SKU-002",
      "image": null,
      "isDefault": false
    }
  ]
}
```

**说明**:
- 全量替换：每次保存会删除旧的规格/SKU 数据，重新写入
- 保存成功后，商品 `has_sku` 自动设为 1
- `specValueIds` 中的 ID 为规格值 ID（对应 `mall_product_spec_value.id`）
- 首次保存时前端不知道 specValueId，应由后端根据规格名+值自动匹配或创建

#### 9.9 获取商品 SKU 配置
```http
GET /api/v1/admin/products/{id}/sku-config
Authorization: Bearer <admin-token>
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "specs": [
      {
        "name": "颜色",
        "values": ["红色", "蓝色"]
      }
    ],
    "skuList": [
      {
        "specValueIds": [55, 60],
        "price": 199.00,
        "stock": 10,
        "skuCode": "SKU-001",
        "image": null,
        "isDefault": true
      }
    ]
  }
}
```

#### 9.10 删除商品 SKU 配置
```http
DELETE /api/v1/admin/products/{id}/sku-config
Authorization: Bearer <admin-token>
```

**说明**: 删除后商品 `has_sku` 自动设为 0，商品恢复为无规格模式

---

### 10. 后台管理 - 分类 `/api/v1/admin/categories` 🔒 ADMIN

#### 10.1 分类列表（管理员）
```http
GET /api/v1/admin/categories
Authorization: Bearer <admin-token>
```

#### 10.2 分类详情（管理员）
```http
GET /api/v1/admin/categories/{id}
Authorization: Bearer <admin-token>
```

#### 10.3 新增分类
```http
POST /api/v1/admin/categories
Authorization: Bearer <admin-token>
```

**请求体**:
```json
{
  "name": "家用电器",
  "parentId": 0,
  "sortOrder": 3
}
```

#### 10.4 修改分类
```http
PUT /api/v1/admin/categories/{id}
Authorization: Bearer <admin-token>
```

#### 10.5 删除分类
```http
DELETE /api/v1/admin/categories/{id}
Authorization: Bearer <admin-token>
```

**限制**:
- 有子分类的分类不能删除
- 有商品的分类不能删除

---

### 11. 后台管理 - 订单 `/api/v1/admin/orders` 🔒 ADMIN

#### 11.1 所有订单列表
```http
GET /api/v1/admin/orders?page=1&size=10
Authorization: Bearer <admin-token>
```

**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**响应**: PageResult。列表项**不包含 `items`**（订单明细），仅详情接口（11.3）返回。

#### 11.2 按状态查询订单
```http
GET /api/v1/admin/orders/status/{status}?page=1&size=10
Authorization: Bearer <admin-token>
```

**查询参数**: `page`（页码，默认1）、`size`（每页数量，默认10，最大100）

**响应**: PageResult。列表项**不包含 `items`**，同 11.1。

#### 11.3 订单详情（管理员）
```http
GET /api/v1/admin/orders/{orderNo}
Authorization: Bearer <admin-token>
```

**说明**: 管理员可查看所有用户的订单

#### 11.4 订单发货
```http
PUT /api/v1/admin/orders/{orderNo}/ship
Authorization: Bearer <admin-token>
```

**限制**: 只能发货已支付订单

#### 11.5 取消订单（管理员）
```http
PUT /api/v1/admin/orders/{orderNo}/cancel
Authorization: Bearer <admin-token>
```

**说明**: 管理员可取消待支付和待发货状态的订单，取消后自动恢复库存

**限制**:
- 只能取消 UNPAID（待支付）或 PAID（待发货）状态的订单
- 取消后订单状态变更为 CANCELLED
- 自动恢复所有商品库存
- 已付款订单取消时自动扣减对应商品的累计销量（salesCount）

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 12. 后台管理 - 用户 `/api/v1/admin/users` 🔒 ADMIN

#### 12.1 用户列表
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

#### 12.2 用户详情
```http
GET /api/v1/admin/users/{id}
Authorization: Bearer <admin-token>
```

#### 12.3 修改用户状态
```http
PUT /api/v1/admin/users/{id}/status?status=0
Authorization: Bearer <admin-token>
```
- status: 0-禁用, 1-启用

#### 12.4 修改用户角色
```http
PUT /api/v1/admin/users/{id}/role?role=ADMIN
Authorization: Bearer <admin-token>
```
- role: USER, ADMIN

---

## 常见错误码

### 通用错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权（未登录或Token过期） |
| 403 | 禁止访问（无权限） |
| 404 | 资源未找到 |
| 500 | 服务器内部错误 |

### 用户相关错误码

| 错误码 | 说明 |
|--------|------|
| 40001 | 用户名已存在 |
| 40002 | 邮箱已存在 |
| 40003 | 手机号已存在 |
| 40004 | 用户不存在 |
| 40005 | 用户名或密码错误 |
| 40006 | 账户已被禁用 |

### 商品相关错误码

| 错误码 | 说明 |
|--------|------|
| 40101 | 商品不存在 |
| 40103 | 库存不足 |
| 40104 | 商品已下架 |

### SKU 相关错误码

| 错误码 | 说明 |
|--------|------|
| 40151 | SKU不存在 |
| 40152 | SKU库存不足 |
| 40153 | 该商品需要选择SKU |
| 40154 | SKU配置无效 |

### 分类相关错误码

| 错误码 | 说明 |
|--------|------|
| 40201 | 分类不存在 |

### 购物车相关错误码

| 错误码 | 说明 |
|--------|------|
| 40301 | 购物车项不存在 |
| 40302 | 购物车为空 |
| 40303 | 购物车中无已选中商品 |

### 地址相关错误码

| 错误码 | 说明 |
|--------|------|
| 40401 | 地址不存在 |

### 订单相关错误码

| 错误码 | 说明 |
|--------|------|
| 40501 | 订单不存在 |
| 40502 | 无效的订单状态 |
| 40503 | 订单无法取消 |
| 40504 | 订单商品明细不存在 |

### 支付相关错误码

| 错误码 | 说明 |
|--------|------|
| 40601 | 支付失败 |
| 40602 | 支付记录不存在 |
| 40603 | 支付关闭失败 |
| 40604 | 退款失败 |
| 40605 | 退款记录不存在 |
| 40606 | 支付已退款 |

### Token相关错误码

| 错误码 | 说明 |
|--------|------|
| 40701 | 无效的Token |
| 40702 | Token已过期 |

---

## 使用示例

### 完整购物流程

```bash
# 1. 注册用户
POST /api/v1/auth/register
{
  "username": "buyer",
  "password": "123456",
  "email": "buyer@example.com"
}

# 2. 登录获取Token
POST /api/v1/auth/login
{
  "username": "buyer",
  "password": "123456"
}
# 获得: {"accessToken": "xxx..."}

# 3. 添加收货地址
POST /api/v1/addresses
Authorization: Bearer xxx...
{
  "receiverName": "张三",
  "phone": "13800138000",
  "province": "北京市",
  "city": "北京市",
  "district": "朝阳区",
  "detailAddress": "某某街道123号"
}

# 4. 浏览商品
GET /api/v1/products

# 5. 添加到购物车
POST /api/v1/cart
Authorization: Bearer xxx...
{
  "productId": 1,
  "quantity": 2
}

# 6. 查看购物车
GET /api/v1/cart
Authorization: Bearer xxx...

# 7. 创建订单
POST /api/v1/orders
Authorization: Bearer xxx...
{
  "addressId": 1,
  "remark": "请尽快发货"
}
# 获得: {"orderNo": "20260108123456789"}

# 8. 支付订单
POST /api/v1/payment/pay
Authorization: Bearer xxx...
{
  "orderNo": "20260108123456789",
  "paymentMethod": "MOCK"
}

# 9. 查看订单
GET /api/v1/orders/20260108123456789
Authorization: Bearer xxx...

# 10. 确认收货（发货后）
PUT /api/v1/orders/20260108123456789/confirm
Authorization: Bearer xxx...
```

---

## 注意事项

### 1. Token管理
- Token有效期为24小时
- Token过期后需要重新登录
- Token需要在请求头中携带：`Authorization: Bearer <token>`

### 2. 权限控制
- 公开接口：商品查询、分类查询等
- USER权限：购物车、订单、地址等个人操作
- ADMIN权限：所有后台管理接口

### 3. 数据隔离
- 用户只能操作自己的数据（购物车、订单、地址）
- 管理员可以查看和管理所有数据

### 4. 库存与销量管理
- **双层库存**：有 SKU 的商品库存在 `mall_sku` 表维护，无 SKU 的商品库存在 `mall_product` 表维护
- **下单扣减**：无 SKU 商品走 Redis 预扣 + DB 乐观锁；有 SKU 商品直接走 DB 乐观锁扣减 SKU 库存
- **支付成功**：增加商品累计销量（salesCount），有 SKU 时同时增加 SKU 销量
- **取消订单**：恢复对应维度库存（SKU 或 Product），已付款订单同时扣减销量
- **掉单补偿**：延迟队列超时取消时，同样区分 SKU/Product 维度恢复库存
- 使用乐观锁防止超卖和并发重复更新销量

### 5. 订单状态流转
```
UNPAID（待支付） → 支付 → PAID（已支付）
                              ↓
                            发货
                              ↓
                       SHIPPED（已发货）
                              ↓
                          确认收货
                              ↓
                      COMPLETED（已完成）

UNPAID（待支付） → 取消 → CANCELLED（已取消）
```

### 6. 数据库优化（V2/V3 迁移）

#### 组合索引（`db/V2_add_indexes.sql`）

| 表 | 索引名 | 列 | 用途 |
|----|--------|-----|------|
| `mall_order` | `idx_user_id_status` | `(user_id, status)` | 优化按状态查询用户订单 |
| `mall_order` | `idx_user_id_created_at` | `(user_id, created_at)` | 优化用户订单列表排序，避免 filesort |
| `mall_order_item` | `idx_order_id_product_id` | `(order_id, product_id)` | 优化订单明细关联查询 |
| `mall_product` | `idx_name_prefix` | `name(50)` | 优化商品名称前缀搜索 |
| `mall_product` | `ft_name_subtitle` | `FULLTEXT(name, subtitle) WITH PARSER ngram` | 中文全文搜索，需 `ngram_token_size=2` |

#### 冷热数据分离 — 归档表（`db/V3_archive_tables.sql`）

| 热表 | 归档表 | 归档条件 |
|------|--------|---------|
| `mall_order` | `mall_order_archive` | status IN ('COMPLETED','CANCELLED') AND created_at < 3 个月前 |
| `mall_order_item` | `mall_order_item_archive` | 关联 order 已归档 |
| `mall_payment` | `mall_payment_archive` | 关联 order 已归档 |
| `mall_refund` | `mall_refund_archive` | 关联 order 已归档 |

- **定时归档**: 每天凌晨 2:00 自动执行（`OrderArchiveTask`），每批 500 条，分批事务
- **查询路由**: 订单详情接口先查热表，无结果时自动回查归档表，对用户透明
- **列表接口不查归档表**: 订单列表（7.2/7.3）仅返回热表数据

#### 聚合缓存

| 接口 | 缓存 Key | TTL | 说明 |
|------|----------|-----|------|
| `GET /admin/orders/stats/total-sales` | `stats:order:total_sales` | 5 分钟 | Redis 缓存总销售额，降级为 DB 查询 |

---

## 开发调试

### Swagger文档
```
http://localhost:8080/swagger-ui.html
```

### 健康检查
```
GET http://localhost:8080/api/v1/health
```

### 数据库
```
数据库: mall
默认管理员: admin / admin123
```

