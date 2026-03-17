# 前端架构文档

> 前端页面为Vibe coding产物，未经验证，仅供参考

## 目录

- [技术选型](#技术选型)
- [目录结构与职责](#目录结构与职责)
- [Axios 封装](#axios-封装)
- [API 模块组织](#api-模块组织)
- [布局系统](#布局系统)
- [Vite 构建优化](#vite-构建优化)

---

## 技术选型

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.x | 使用 `<script setup>` + Composition API，更简洁的组件编写方式 |
| Element Plus | 2.13+ | 企业级 UI 组件库，提供表单、表格、弹窗等常用组件 |
| Pinia | 2.x | 官方推荐的 Vue 3 状态管理库，Options 风格 `defineStore` |
| Vue Router | 4.x | 客户端路由，支持懒加载和导航守卫 |
| Axios | 1.x | HTTP 请求库，封装在 `src/api/request.js` |
| Vite | 7.x | 现代构建工具，开发服务器极速响应，生产构建使用 Rollup |
| Sass | 1.x | CSS 预处理器，使用变量、混入、嵌套语法 |
| dayjs | 1.x | 轻量日期处理库 |
| vue3-lazyload | 2.x | 图片懒加载指令 |

**选型理由：**

- **Vue 3 Composition API**：逻辑复用更灵活，与 TypeScript 友好，响应式系统经过重写性能更好
- **Element Plus**：与 Vue 3 原生适配，组件丰富，管理后台开发效率高
- **Pinia**：相比 Vuex 4 代码更简洁，不需要 mutations，支持 DevTools

---

## 目录结构与职责

```
mall-frontend/
├── src/
│   ├── api/              # Axios 接口调用模块（按业务领域拆分）
│   │   ├── request.js    # Axios 实例、拦截器（核心）
│   │   ├── auth.js       # 认证相关：登录、注册、登出
│   │   ├── product.js    # 商品：列表、详情、搜索
│   │   ├── cart.js       # 购物车：增删改查、选中
│   │   ├── order.js      # 订单：创建、查询、取消
│   │   ├── payment.js    # 支付：发起支付、查询状态
│   │   ├── user.js       # 用户：个人信息获取与更新
│   │   ├── address.js    # 收货地址：增删改查
│   │   ├── category.js   # 商品分类
│   │   └── admin/        # 管理端专用接口
│   │       ├── product.js    # 管理端商品管理
│   │       ├── category.js   # 管理端分类管理
│   │       ├── order.js      # 管理端订单管理
│   │       └── user.js       # 管理端用户管理
│   │
│   ├── store/            # Pinia 状态管理
│   │   ├── index.js      # Pinia 实例创建
│   │   ├── auth.js       # 认证状态（Token、用户信息、登录态）
│   │   ├── cart.js       # 购物车状态（商品列表、选中、价格计算）
│   │   ├── user.js       # 用户详情（个人信息、地址列表）
│   │   └── app.js        # 全局 UI 状态（加载态、分类缓存、侧边栏）
│   │
│   ├── views/            # 页面级组件
│   │   ├── user/         # 用户端页面
│   │   │   ├── Home.vue           # 首页
│   │   │   ├── ProductList.vue    # 商品列表
│   │   │   ├── ProductDetail.vue  # 商品详情
│   │   │   ├── Cart.vue           # 购物车
│   │   │   ├── Checkout.vue       # 结算
│   │   │   ├── OrderList.vue      # 我的订单
│   │   │   ├── OrderDetail.vue    # 订单详情
│   │   │   ├── Payment.vue        # 支付
│   │   │   ├── PaymentResult.vue  # 支付结果
│   │   │   ├── StripePaymentResult.vue  # Stripe 支付结果
│   │   │   ├── Profile.vue        # 个人中心
│   │   │   └── Address.vue        # 地址管理
│   │   ├── admin/        # 管理端页面
│   │   │   ├── Dashboard.vue      # 数据概览
│   │   │   ├── ProductManage.vue  # 商品管理
│   │   │   ├── CategoryManage.vue # 分类管理
│   │   │   ├── OrderManage.vue    # 订单管理
│   │   │   ├── AdminOrderDetail.vue # 订单详情
│   │   │   └── UserManage.vue     # 用户管理
│   │   ├── auth/         # 认证页面
│   │   │   ├── Login.vue          # 登录
│   │   │   └── Register.vue       # 注册
│   │   └── NotFound.vue  # 404 页面
│   │
│   ├── components/       # 可复用组件
│   │   └── common/       # 公共组件
│   │       ├── Loading.vue    # 加载状态组件
│   │       └── Empty.vue      # 空状态组件
│   │
│   ├── layouts/          # 布局包装组件
│   │   ├── UserLayout.vue    # 用户端布局（顶部导航 + 页脚）
│   │   └── AdminLayout.vue   # 管理端布局（侧边栏 + 顶部栏）
│   │
│   ├── router/           # Vue Router 配置
│   │   └── index.js          # 路由表 + 导航守卫
│   │
│   ├── utils/            # 纯工具函数
│   │   ├── storage.js    # localStorage 封装（Token、用户信息存取）
│   │   ├── format.js     # 格式化工具（日期、金额等）
│   │   ├── constants.js  # 常量定义
│   │   └── validate.js   # 表单校验规则
│   │
│   └── assets/           # 静态资源
│       └── styles/       # SCSS 样式
│           ├── variables.scss    # 颜色、间距、字体等设计变量
│           ├── reset.scss        # 浏览器默认样式重置
│           └── common.scss       # 公共样式
│
├── vite.config.js        # Vite 配置
└── package.json
```

---

## Axios 封装

文件路径：`src/api/request.js`

### 实例配置

```javascript
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: import.meta.env.DEV ? 6000000000000 : 10000, // 开发模式极大超时，生产 10s
  headers: {
    'Content-Type': 'application/json'
  }
})
```

- `baseURL` 从环境变量读取，开发环境由 Vite proxy 代理到 `localhost:8080`
- 开发模式设置超长超时，方便断点调试

### 请求拦截器：自动注入 Token

```javascript
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

每次请求自动从 localStorage 读取 Token，添加到 `Authorization` 请求头。

### 响应拦截器：统一处理业务码

后端响应格式统一为 `{ code, message, data }`。

```javascript
request.interceptors.response.use((response) => {
  const { code, message, data } = response.data

  // code=200：成功，直接返回 data，组件拿到的是业务数据而非整个响应
  if (code === 200) {
    return data
  }

  // code=400 + message='Validation failed'：字段校验错误，抛出 ValidationError
  // 由表单组件捕获后逐字段显示错误，不显示全局提示
  if (code === 400 && message === 'Validation failed' && data && typeof data === 'object') {
    return Promise.reject(new ValidationError(message, data))
  }

  // 其他业务错误：弹出全局提示
  ElMessage.error(message || '操作失败')
  return Promise.reject(new Error(message || '操作失败'))
})
```

### 响应拦截器：HTTP 错误状态码处理

| HTTP 状态码 | 处理行为 |
|-------------|---------|
| 401（登录请求） | 显示后端返回的错误信息（用户名或密码错误） |
| 401（其他请求） | 清除 Token，跳转 `/login`，提示"登录已过期" |
| 403 | 提示"无权访问" |
| 404 | 提示"请求的资源不存在" |
| 500 | 提示"服务器错误，请稍后重试" |
| 网络错误 | 提示"网络错误，请检查网络连接" |

### ValidationError 类

当后端返回字段校验错误时，抛出 `ValidationError` 而非普通 `Error`，方便表单组件逐字段显示错误：

```javascript
export class ValidationError extends Error {
  constructor(message, fields) {
    super(message)
    this.name = 'ValidationError'
    this.fields = fields // { fieldName: '错误信息' }
  }
}
```

表单组件中可通过 `instanceof ValidationError` 判断并处理：

```javascript
try {
  await authStore.login(form)
} catch (error) {
  if (error instanceof ValidationError) {
    // 逐字段显示错误
    Object.entries(error.fields).forEach(([field, msg]) => {
      formErrors[field] = msg
    })
  }
}
```

---

## API 模块组织

所有 API 模块按业务领域拆分，每个文件只调用同一领域的接口。

### 用户端 API 模块

| 文件 | 职责 | 主要方法 |
|------|------|---------|
| `auth.js` | 用户认证 | `login`、`register`、`logout` |
| `product.js` | 商品查询 | `getAllProducts`、`getProductsByCategory`、`searchProducts`、`getProductDetail` |
| `cart.js` | 购物车管理 | `getCart`、`addToCart`、`updateCartItem`、`deleteCartItem`、`checkCartItem`、`checkAllCart` |
| `order.js` | 订单管理 | 创建、查询、取消订单 |
| `payment.js` | 支付 | 发起支付、查询支付状态 |
| `user.js` | 用户信息 | `getUserInfo`、`updateUserInfo` |
| `address.js` | 收货地址 | `getAddresses`，增删改查 |
| `category.js` | 商品分类 | `getAllCategories` |

### 管理端 API 模块（`api/admin/`）

| 文件 | 职责 |
|------|------|
| `admin/product.js` | 商品的增删改查、上下架 |
| `admin/category.js` | 分类的增删改查 |
| `admin/order.js` | 订单列表查询、状态更新 |
| `admin/user.js` | 用户列表查询、禁用/启用 |

### 调用示例

```javascript
// 在组件或 Store 中使用
import { getAllProducts } from '@/api/product'

const loadProducts = async () => {
  const data = await getAllProducts({ page: 1, size: 12, categoryId: 1 })
  // data 直接是业务数据，无需再解构 response.data
  products.value = data.records
}
```

---

## 布局系统

项目有两套独立的布局组件，分别服务于用户端和管理端。

### UserLayout（用户端布局）

文件：`src/layouts/UserLayout.vue`

```
┌─────────────────────────────────────────┐
│         公告栏（欢迎信息）                │
├─────────────────────────────────────────┤
│  导航链接  │  SPRING MALL  │  操作图标   │  ← 吸顶，滚动后加阴影
├─────────────────────────────────────────┤
│                                         │
│           <router-view />               │
│         （页面内容插槽）                 │
│                                         │
├─────────────────────────────────────────┤
│              页脚（购物/账户链接）        │
└─────────────────────────────────────────┘
```

**功能特性：**

- 顶部导航吸顶（`position: sticky`），滚动超过 40px 后显示阴影
- 右侧操作区根据登录状态动态显示：已登录显示订单、购物车（带数量徽章）、用户下拉菜单；未登录显示登录/注册按钮
- 购物车徽章数量来自 `cartStore.cartCount`，超过 99 显示"99+"
- 管理员用户下拉菜单中额外显示"管理后台"入口
- 移动端隐藏左侧导航链接

初始化逻辑（`UserLayout.vue` 组件 setup 阶段）：
- 调用 `authStore.initAuth()` 从 localStorage 恢复登录状态
- 已登录且非管理员时自动调用 `cartStore.fetchCart()` 加载购物车

### AdminLayout（管理端布局）

文件：`src/layouts/AdminLayout.vue`

```
┌──────────┬──────────────────────────────┐
│          │   顶部栏（折叠按钮 + 用户名）  │
│  侧边栏  ├──────────────────────────────┤
│  200px   │                              │
│ (可折叠  │      <router-view />         │
│  至64px) │     （管理页面内容）          │
│          │                              │
└──────────┴──────────────────────────────┘
```

**功能特性：**

- 侧边栏宽度 200px，折叠后缩至 64px（仅显示图标），折叠状态由 `appStore.sidebarCollapsed` 控制
- 菜单项与路由绑定（`router` 属性），当前路由自动高亮对应菜单项
- 顶部栏显示当前用户名，下拉菜单提供"返回首页"和"退出登录"选项
- 整体高度 `100vh`，内容区域独立滚动

---

## Vite 构建优化

文件：`vite.config.js`

### 路径别名

```javascript
resolve: {
  alias: {
    '@': path.resolve(__dirname, 'src')
  }
}
```

所有模块导入可使用 `@/` 替代 `src/` 的相对路径。

### 开发代理

```javascript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

开发模式下，所有 `/api/` 前缀请求被代理到后端 `8080` 端口，解决跨域问题。

### 生产代码分割（Manual Chunks）

```javascript
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        'vue-vendor':    ['vue', 'vue-router', 'pinia'],       // Vue 生态
        'element-plus':  ['element-plus', '@element-plus/icons-vue'], // UI 库
        'utils':         ['axios', 'dayjs'],                    // 工具库
        'lazyload':      ['vue3-lazyload']                      // 图片懒加载
      }
    }
  },
  chunkSizeWarningLimit: 1000,  // chunk 大小警告阈值（KB）
  cssCodeSplit: true,            // CSS 代码分割（按路由拆分 CSS）
  sourcemap: false               // 生产环境不生成 sourcemap
}
```

**分割策略说明：**

| Chunk 名称 | 包含库 | 说明 |
|-----------|-------|------|
| `vue-vendor` | vue、vue-router、pinia | Vue 生态核心，变化少，可长效缓存 |
| `element-plus` | element-plus、@element-plus/icons-vue | UI 库体积大，独立缓存减少重复下载 |
| `utils` | axios、dayjs | 通用工具，独立缓存 |
| `lazyload` | vue3-lazyload | 图片懒加载库 |

### 依赖预构建

```javascript
optimizeDeps: {
  include: [
    'vue', 'vue-router', 'pinia',
    'element-plus', '@element-plus/icons-vue',
    'axios', 'dayjs', 'vue3-lazyload'
  ]
}
```

明确指定需要预构建的依赖，Vite 在首次启动时将其转为 ESM 格式，大幅加速开发服务器冷启动。
