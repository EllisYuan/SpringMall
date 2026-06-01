/**
 * 认证相关 API
 */
import request from './request'

/**
 * 用户注册
 * @param {Object} data - 注册信息 { username, email, password, cfToken }
 */
export const register = (data) => {
  return request({
    url: '/auth/register',
    method: 'POST',
    data
  })
}

/**
 * 用户登录
 * @param {Object} data - 登录信息 { account, password, cfToken }
 */
export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'POST',
    data
  })
}

/**
 * 用户登出
 */
export const logout = () => {
  return request({
    url: '/auth/logout',
    method: 'POST'
  })
}

/**
 * 发送 OTP 验证码
 * @param {Object} data - { target, type, purpose }
 */
export const sendOtp = (data) => {
  return request({
    url: '/auth/send-otp',
    method: 'POST',
    data
  })
}

/**
 * 验证 OTP
 * @param {Object} data - { target, code, purpose }
 * @returns {Promise<string>} verificationToken
 */
export const verifyOtp = (data) => {
  return request({
    url: '/auth/verify-otp',
    method: 'POST',
    data
  })
}

/**
 * 忘记密码 — 发送重置验证码
 * @param {Object} data - { target, type }
 */
export const forgotPassword = (data) => {
  return request({
    url: '/auth/forgot-password',
    method: 'POST',
    data
  })
}

/**
 * 重置密码
 * @param {Object} data - { verificationToken, newPassword }
 */
export const resetPassword = (data) => {
  return request({
    url: '/auth/reset-password',
    method: 'POST',
    data
  })
}
