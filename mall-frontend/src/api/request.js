/**
 * Axios 请求封装
 * 统一处理请求和响应
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getToken, removeToken, clearStorage } from '@/utils/storage'

/**
 * 字段校验错误类
 * 当后端返回 code=400 且 message="Validation failed" 时抛出
 * fields 属性包含字段名到错误信息的映射，例如 { username: '用户名不能为空' }
 */
export class ValidationError extends Error {
  constructor(message, fields) {
    super(message)
    this.name = 'ValidationError'
    this.fields = fields // { fieldName: errorMessage }
  }
}

/**
 * 业务异常类
 * 当后端返回特定业务错误码（如秒杀相关 408xx）时抛出
 * 调用方可通过 error instanceof BusinessError && error.code === 40810 做精细处理
 * 注意：抛出此类时拦截器不会自动弹 ElMessage，由调用方决定如何提示
 */
export class BusinessError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'BusinessError'
    this.code = code
  }
}

// 不自动弹 Toast、交由调用方处理的业务错误码集合
const SILENT_CODES = new Set([40810, 40811])

// 创建 axios 实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: import.meta.env.DEV ? 6000000000000 : 10000, // 开发模式 60s，生产模式 10s
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 自动添加 Token
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data

    // 成功响应
    if (code === 200) {
      return data
    }

    // 字段校验错误：不弹全局提示，传递给表单组件处理
    if (code === 400 && message === 'Validation failed' && data && typeof data === 'object') {
      return Promise.reject(new ValidationError(message, data))
    }

    // 静默业务错误码：不弹 Toast，由调用方自行处理
    if (SILENT_CODES.has(code)) {
      return Promise.reject(new BusinessError(code, message))
    }

    // 其他业务错误
    ElMessage.error(message || '操作失败')
    return Promise.reject(new Error(message || '操作失败'))
  },
  (error) => {
    // HTTP 错误处理
    if (error.response) {
      const { status, data, config } = error.response

      switch (status) {
        case 401:
          // 区分登录请求失败和 Token 过期
          if (config.url.includes('/auth/login')) {
            // 登录请求失败，显示后端返回的错误信息
            ElMessage.error(data?.message || '用户名或密码错误')
          } else {
            // Token 过期或无效，清除 token 并跳转登录
            ElMessage.error('登录已过期，请重新登录')
            clearStorage()
            router.push('/login')
          }
          break

        case 403:
          // 无权限
          ElMessage.error(data?.message || '无权访问')
          break

        case 404:
          // 资源不存在
          ElMessage.error('请求的资源不存在')
          break

        case 500:
          // 服务器错误
          ElMessage.error('服务器错误，请稍后重试')
          break

        default:
          ElMessage.error(data?.message || '请求失败')
      }
    } else if (error.request) {
      // 请求已发出但没有收到响应
      ElMessage.error('网络错误，请检查网络连接')
    } else {
      // 请求配置错误
      ElMessage.error(error.message || '请求失败')
    }

    return Promise.reject(error)
  }
)

export default request
