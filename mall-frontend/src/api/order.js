/**
 * 订单相关 API
 */
import request from './request'

/**
 * 创建订单
 * @param {Object} data - 订单信息
 */
export const createOrder = (data) => {
  return request({
    url: '/orders',
    method: 'POST',
    data
  })
}

/**
 * 获取订单列表
 * @param {Object} params - 查询参数 { page, size }
 */
export const getOrders = (params) => {
  return request({
    url: '/orders',
    method: 'GET',
    params
  })
}

/**
 * 根据状态获取订单列表
 * @param {string} status - 订单状态
 * @param {Object} params - 查询参数 { page, size }
 */
export const getOrdersByStatus = (status, params) => {
  return request({
    url: `/orders/status/${status}`,
    method: 'GET',
    params
  })
}

/**
 * 获取订单详情
 * @param {string} orderNo - 订单号
 */
export const getOrderDetail = (orderNo) => {
  return request({
    url: `/orders/${orderNo}`,
    method: 'GET'
  })
}

/**
 * 取消订单
 * @param {string} orderNo - 订单号
 */
export const cancelOrder = (orderNo) => {
  return request({
    url: `/orders/${orderNo}/cancel`,
    method: 'PUT'
  })
}

/**
 * 确认收货
 * @param {string} orderNo - 订单号
 */
export const confirmOrder = (orderNo) => {
  return request({
    url: `/orders/${orderNo}/confirm`,
    method: 'PUT'
  })
}

/**
 * 获取历史归档订单
 * @param {Object} params - 查询参数 { page, size }
 */
export const getArchivedOrders = (params) => {
  return request({
    url: '/orders/archive',
    method: 'GET',
    params
  })
}
