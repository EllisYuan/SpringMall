---
description: Vue 3 前端编码规约（API 层/request 拦截器/Pinia/组件/路由/Element Plus）
paths:
  - "mall-frontend/src/**"
  - "mall-frontend/vite.config.js"
---

# 前端 Vue 3 编码规约

## API 层（`src/api/`，每业务领域一个文件）
```javascript
import request from '@/api/request'

export async function getAllProducts(params) {
  return request.get('/products', { params })
}
export async function getProductById(id) {
  return request.get(`/products/${id}`)
}
```

### 关于 `request`（`src/api/request.js`）—— 必须了解
- Axios 实例，`baseURL` 来自 `import.meta.env.VITE_API_BASE_URL`。
- **响应拦截器已自动解包 `Result<T>`**：`code === 200` 时返回 `response.data.data`，因此 API 函数直接返回**数据实体**，而非 `{ code, message, data }` 信封。
- 业务错误（`code !== 200`）：自动 `ElMessage.error` 提示并 reject。
- HTTP 401（除 `/auth/login`）：自动清存储并跳 `/login`。
- **不要**在 `request` 调用外再加冗余错误处理；拦截器已处理面向用户的提示。

## Pinia Store（**Options 风格**，不用 setup/函数风格）
```javascript
export const useSomethingStore = defineStore('something', {
  state: () => ({ items: [], loading: false }),
  getters: { itemCount: (s) => s.items.length },
  actions: {
    async fetchItems() {
      this.loading = true
      try { this.items = await someApi() }   // 拦截器已解包，直接是数据
      catch (e) { console.error('Failed:', e) }
      finally { this.loading = false }
    }
  }
})
```
- 多组件共享状态 → Store；单页面内异步状态 → 页面 `<script setup>` 的 `ref`。
- **不要**把完整 `Result<T>` 信封存入 state。

## Vue 组件（`<script setup>`，不用 Options API）
```vue
<script setup>
import { ref, onMounted } from 'vue'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'

const items = ref([])
const loading = ref(false)
const fetchData = async () => {
  loading.value = true
  try { items.value = await someApi() }
  catch (e) { console.error('Error:', e) }
  finally { loading.value = false }
}
onMounted(() => fetchData())
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';
</style>
```

## 路由（懒加载 + meta 鉴权）
- `meta: { requiresAuth: true }` — 无 Token 跳 `/login`。
- `meta: { requiresAuth: true, requiresAdmin: true }` — 同时校验 ADMIN。
- 动态导入：`component: () => import('@/views/path/Component.vue')`；每条路由加 `meta: { title }`。

## Element Plus
- 提示：`ElMessage.success/error`；确认：`await ElMessageBox.confirm('…', '标题')`。
- 常用：`el-input/button/select/option/table/table-column/form/form-item/pagination/card/tag`。

## 工具函数（来自 `@/utils`）
- `formatPrice`（`@/utils/format`）、`debounce`（`@/utils/helpers`）。
- Token 管理用 `getToken/setToken/clearStorage`（`@/utils/storage`），**不要**直接操作 `localStorage`。

## 构建验证
```bash
cd mall-frontend ; pnpm run build
```

## 禁止事项
- 不直接 `import axios`，始终用 `@/api/request`。
- 新组件不用 Options API；不创建 setup/函数风格 Store。
- 不硬编码 API 地址（来自 `request` 的 `baseURL`）。
