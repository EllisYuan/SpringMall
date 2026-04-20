---
name: spring-mall-api
description: 用于查询后端API接口定义。
---

# Spring Mall API 

## 项目配置

### 基础信息
- **后端**: Spring Boot 3.3.6 + MyBatis + Spring Security + JWT
- **API基础路径**: `http://localhost:8080/api/v1`
- **认证方式**: JWT Token (Bearer)
- **Token有效期**: 24小时(生产)、1年(测试)
  - User: eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ1c2VyIiwiaWF0IjoxNzcwOTgzODk1LCJleHAiOjE4MDI1MTk4OTV9.jJFk4lM1F7scTgBmLOr3AGgw-BtNn9acq1urgHaviSlD7VHs71_sW9dirz9s9JFZ
  - Admin: eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc3MDk4Mzg5NSwiZXhwIjoxODAyNTE5ODk1fQ.itRJaHoe6rLoEUzw27IPH3hBvxykxKhYo-HISEc8WKXg6TwDCnqD5MdymI3ERuDO
  - 过期时间: Sat Feb 13 19:58:15 HKT 2027


### 快速配置
```javascript
const BASE_URL = 'http://localhost:8080/api/v1';

// 请求头格式
headers: {
  'Authorization': `Bearer ${token}`,
  'Content-Type': 'application/json'
}
```

## 核心工作流程

### 1. 认证流程
```javascript
// 登录 → 获取Token → 存储 → 后续请求携带
POST /auth/login { username, password }
→ 存储 accessToken
→ 所有请求添加 Authorization header
→ 401错误 → 清除Token → 跳转登录
```

### 2. 完整购物流程
```
浏览商品 (GET /products)
→ 加入购物车 (POST /cart)
→ 查看购物车 (GET /cart)
→ 创建订单 (POST /orders)
→ 支付订单 (POST /payment/pay)
→ 查看订单 (GET /orders/{orderNo})
```

### 3. 权限控制
- **PUBLIC**: 商品、分类浏览（无需Token）
- **USER**: 购物车、订单、地址（需要Token）
- **ADMIN**: 后台管理（需要Token + ADMIN角色）

## 响应格式

### 统一响应结构
```typescript
// 成功
{ code: 200, message: "success", data: T }

// 失败
{ code: 错误码, message: "错误信息", data: null }

// 分页
{ code: 200, message: "success", 
  data: { list: [], total, page, size, pages } }
```

### 关键错误码
- `401` / `40701` / `40702`: Token问题 → 重新登录
- `40103`: 库存不足
- `40503`: 订单无法取消

## API集成建议

### Axios封装模式
参考 `scripts/axios-setup.js` 中的完整封装示例，包含：
- 请求拦截器（添加Token）
- 响应拦截器（统一错误处理）
- Token过期自动跳转

### 常用API模式
所有API端点的详细文档请查看 `references/api-endpoints.md`

**快速查询**：
- 认证相关: `/auth/*`
- 用户操作: `/user/*`, `/cart/*`, `/addresses/*`, `/orders/*`
- 商品浏览: `/products/*`, `/categories/*`
- 管理后台: `/admin/*`

### 前端路由建议
```
前台:
/                    - 首页
/products            - 商品列表
/products/:id        - 商品详情
/cart                - 购物车
/orders              - 我的订单
/profile             - 个人中心

后台:
/admin/products      - 商品管理
/admin/orders        - 订单管理
/admin/users         - 用户管理
```

## 开发步骤

### 创建页面时的标准流程

1. **确定页面需求**
   - 需要哪些API接口？查阅 `references/api-endpoints.md`
   - 是否需要认证？确认权限级别

2. **实现API调用**
   - 使用 `scripts/axios-setup.js` 中的封装
   - 处理loading状态和错误提示

3. **处理响应数据**
   - 解构响应: `const { data } = response.data`
   - 错误处理: 检查code和message

4. **实现UI交互**
   - 成功/失败提示
   - 加载状态
   - 表单验证

### 示例：创建登录页面

```javascript
import request from './axios-setup';

// 1. API调用
const login = async (username, password) => {
  try {
    const response = await request.post('/auth/login', {
      username, password
    });
    
    // 2. 存储Token
    localStorage.setItem('accessToken', response.data.accessToken);
    
    // 3. 跳转
    router.push('/');
  } catch (error) {
    // 4. 错误处理
    alert(error.message);
  }
};
```

## 注意事项

### Token管理
- 登录后立即存储Token
- 每次请求自动携带（在拦截器中处理）
- 401错误自动清除Token并跳转登录
- 登出时清除本地Token

### 订单状态流转
```
UNPAID (待支付) → 支付 → PAID (已支付)
       ↓                      ↓
    取消(用户)           发货/取消(用户/管理员)
       ↓                      ↓
CANCELLED (已取消)    SHIPPED (已发货)
                             ↓
                         确认收货
                             ↓
                    COMPLETED (已完成)

用户可取消：UNPAID（待支付）和 PAID（待发货）状态
管理员可取消：UNPAID 和 PAID 状态
已发货（SHIPPED）和已完成（COMPLETED）订单无法取消
取消后自动恢复库存
```

### 库存管理
- 下单时扣减库存
- 取消订单时恢复库存
- 添加购物车前检查库存

## 详细参考

- **完整API文档**: 查看 `references/api-endpoints.md`
- **错误码列表**: 查看 `references/error-codes.md`
- **Axios封装**: 查看 `scripts/axios-setup.js`

## 测试账号

```javascript
// 管理员
{ username: "admin", password: "admin123" }

// 普通用户
{ username: "testuser", password: "123456" }
```