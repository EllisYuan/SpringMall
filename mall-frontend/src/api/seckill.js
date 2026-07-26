/**
 * 秒杀相关 API
 */
import request from './request'

// ─── 用户端接口 ───────────────────────────────────────────

/**
 * 获取秒杀活动列表
 * @returns {Promise<SeckillActivityVO[]>}
 */
export const getSeckillActivities = () => {
  return request({
    url: '/seckill/activities',
    method: 'GET'
  })
}

/**
 * 获取秒杀活动详情
 * @param {string} id - 活动 ID（雪花 ID，JSON 以字符串下发，按不透明字符串透传）
 * @returns {Promise<SeckillActivityVO>}
 */
export const getSeckillActivity = (id) => {
  return request({
    url: `/seckill/activities/${id}`,
    method: 'GET'
  })
}

/**
 * 参与秒杀抢购
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @param {Object} data - 请求体 { quantity, addressId, skuId?, cfToken? }
 * @returns {Promise<{ status: 'QUEUING', activityId: string }>}
 *
 * 可能抛出：
 *   BusinessError(40810) — 需要 Turnstile step-up 验证，前端弹 widget 后带 cfToken 重试
 *   BusinessError(40811) — 流量闸门拒绝，提示稍后重试
 *   Error                — 其他业务错误（拦截器已弹 toast）
 */
export const participateSeckill = (id, data) => {
  return request({
    url: `/seckill/activities/${id}/orders`,
    method: 'POST',
    data
  })
}

/**
 * 查询抢购排队结果
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<{ activityId: string, status: 'QUEUING'|'SUCCESS'|'FAIL'|'NOT_FOUND', orderNo?: string, failReason?: string }>}
 */
export const getSeckillResult = (id) => {
  return request({
    url: `/seckill/activities/${id}/result`,
    method: 'GET'
  })
}

/**
 * 获取我的秒杀订单列表
 * @param {Object} params - { page, size }
 * @returns {Promise<PageResult<SeckillOrderVO>>}
 *   SeckillOrderVO.activityId 为雪花 ID 字符串；无自增 id 字段，行操作与 :key 一律用 orderNo。
 */
export const getMySeckillOrders = (params) => {
  return request({
    url: '/seckill/orders',
    method: 'GET',
    params
  })
}

/**
 * 获取服务器当前时间（供倒计时校准使用，需 USER 登录，与其他用户端秒杀接口一致）
 * @returns {Promise<string>} 服务器时间字符串，如 "2026-06-20T17:04:14"
 */
export const getServerTime = () => {
  return request({
    url: '/seckill/time',
    method: 'GET'
  })
}

// ─── 管理端接口 ───────────────────────────────────────────

/**
 * 获取秒杀活动列表（管理端）
 * @param {Object} params - { page, size, keyword?, status? }
 * @returns {Promise<PageResult<SeckillActivityVO>>}
 */
export const adminGetSeckillActivities = (params) => {
  return request({
    url: '/admin/seckill/activities',
    method: 'GET',
    params
  })
}

/**
 * 获取秒杀活动详情（管理端）
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<SeckillActivityVO>}
 */
export const adminGetSeckillActivity = (id) => {
  return request({
    url: `/admin/seckill/activities/${id}`,
    method: 'GET'
  })
}

/**
 * 创建秒杀活动
 * @param {Object} data - SeckillActivityDTO
 * @returns {Promise<SeckillActivityVO>}
 */
export const adminCreateSeckillActivity = (data) => {
  return request({
    url: '/admin/seckill/activities',
    method: 'POST',
    data
  })
}

/**
 * 更新秒杀活动
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @param {Object} data - SeckillActivityDTO
 * @returns {Promise<SeckillActivityVO>}
 */
export const adminUpdateSeckillActivity = (id, data) => {
  return request({
    url: `/admin/seckill/activities/${id}`,
    method: 'PUT',
    data
  })
}

/**
 * 上线秒杀活动（同事务预留商品库存，提交后自动完成 Redis 预热与闸门初始化）
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<SeckillActivityVO>}
 */
export const adminActivateSeckillActivity = (id) => {
  return request({
    url: `/admin/seckill/activities/${id}/activate`,
    method: 'POST'
  })
}

/**
 * 下线秒杀活动（归还未售库存回商品库存，清活动 Redis 键）
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<SeckillActivityVO>}
 */
export const adminOfflineSeckillActivity = (id) => {
  return request({
    url: `/admin/seckill/activities/${id}/offline`,
    method: 'POST'
  })
}

/**
 * 删除秒杀活动（物理删除，仅草稿/下线态允许）
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<void>}
 */
export const adminDeleteSeckillActivity = (id) => {
  return request({
    url: `/admin/seckill/activities/${id}`,
    method: 'DELETE'
  })
}

/**
 * 手动重新预热秒杀活动（以最新可用库存覆盖写 Redis 分桶并重置闸门，仅已上线）
 * @param {string} id - 活动 ID（雪花 ID 字符串，原样拼进路径）
 * @returns {Promise<void>}
 */
export const adminPreheatSeckillActivity = (id) => {
  return request({
    url: `/admin/seckill/activities/${id}/preheat`,
    method: 'POST'
  })
}
