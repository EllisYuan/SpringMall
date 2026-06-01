/**
 * 认证状态管理
 */
import { defineStore } from 'pinia'
import {
  login as loginApi,
  register as registerApi,
  logout as logoutApi,
  sendOtp as sendOtpApi,
  verifyOtp as verifyOtpApi,
  forgotPassword as forgotPasswordApi,
  resetPassword as resetPasswordApi
} from '@/api/auth'
import { getUser, setUser, getToken, setToken, clearStorage } from '@/utils/storage'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: getUser(),
    token: getToken(),
    isLoggedIn: !!getToken()
  }),

  getters: {
    /**
     * 判断是否为管理员
     */
    isAdmin: (state) => {
      return state.user?.role === 'ADMIN'
    },

    /**
     * 获取用户名
     */
    username: (state) => {
      return state.user?.username || ''
    },

    /**
     * 获取用户角色
     */
    userRole: (state) => {
      return state.user?.role || ''
    }
  },

  actions: {
    /**
     * 用户登录
     * @param {Object} credentials - 登录凭证 { account, password, cfToken }
     */
    async login(credentials) {
      try {
        const data = await loginApi(credentials)

        // 保存 token 和用户信息
        this.token = data.token
        this.user = data.user
        this.isLoggedIn = true

        setToken(data.token)
        setUser(data.user)

        ElMessage.success('登录成功')

        // 根据角色跳转到不同页面
        if (this.isAdmin) {
          router.push('/admin')
        } else {
          router.push('/')
        }

        return data
      } catch (error) {
        console.error('登录失败:', error)
        throw error
      }
    },

    /**
     * 用户注册（注册即登录）
     * 后端注册成功后直接颁发 JWT，前端写入登录态并跳转主页
     * @param {Object} userInfo - 注册信息 { username, email/phone, password, cfToken, verificationToken }
     */
    async register(userInfo) {
      try {
        const data = await registerApi(userInfo)

        // 后端返回 LoginVO：写入登录态并持久化
        this.token = data.token
        this.user = data.user
        this.isLoggedIn = true

        setToken(data.token)
        setUser(data.user)

        ElMessage.success('注册成功，欢迎加入 Spring Mall')

        // 新注册用户固定为 USER 角色，直接跳主页
        router.push('/')

        return data
      } catch (error) {
        console.error('注册失败:', error)
        throw error
      }
    },

    /**
     * 用户登出
     */
    async logout() {
      try {
        await logoutApi()
      } catch (error) {
        console.error('登出请求失败:', error)
      } finally {
        // 无论请求是否成功，都清除本地状态
        this.token = null
        this.user = null
        this.isLoggedIn = false

        clearStorage()

        ElMessage.success('已退出登录')
        router.push('/login')
      }
    },

    /**
     * 初始化认证状态
     * 用于页面刷新后恢复登录状态
     */
    initAuth() {
      const token = getToken()
      const user = getUser()

      if (token && user) {
        this.token = token
        this.user = user
        this.isLoggedIn = true
      } else {
        this.token = null
        this.user = null
        this.isLoggedIn = false
      }
    },

    /**
     * 更新用户信息
     * @param {Object} user - 用户信息
     */
    updateUser(user) {
      this.user = user
      setUser(user)
    },

    /**
     * 发送 OTP 验证码
     * @param {Object} payload - { target, type, purpose }
     */
    async sendOtp(payload) {
      try {
        return await sendOtpApi(payload)
      } catch (error) {
        console.error('发送验证码失败:', error)
        throw error
      }
    },

    /**
     * 校验注册场景的 OTP，返回 verificationToken
     * @param {Object} payload - { target, code, purpose }
     * @returns {Promise<string>} verificationToken
     */
    async verifyOtpForRegister(payload) {
      try {
        return await verifyOtpApi(payload)
      } catch (error) {
        console.error('验证码校验失败:', error)
        throw error
      }
    },

    /**
     * 校验重置密码场景的 OTP，返回 verificationToken
     * 与 verifyOtpForRegister 复用同一后端接口，仅作语义化区分
     * @param {Object} payload - { target, code, purpose }
     * @returns {Promise<string>} verificationToken
     */
    async verifyOtpForReset(payload) {
      try {
        return await verifyOtpApi(payload)
      } catch (error) {
        console.error('验证码校验失败:', error)
        throw error
      }
    },

    /**
     * 忘记密码 — 发送重置验证码
     * @param {Object} payload - { target, type }
     */
    async forgotPassword(payload) {
      try {
        return await forgotPasswordApi(payload)
      } catch (error) {
        console.error('发送重置验证码失败:', error)
        throw error
      }
    },

    /**
     * 重置密码
     * @param {Object} payload - { verificationToken, newPassword }
     */
    async resetPassword(payload) {
      try {
        return await resetPasswordApi(payload)
      } catch (error) {
        console.error('重置密码失败:', error)
        throw error
      }
    }
  }
})
