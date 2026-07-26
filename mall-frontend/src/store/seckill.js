/**
 * 秒杀状态管理（Pinia Options 风格）
 *
 * 抢购结果获取：POST 抢购返回 QUEUING 后，立即建立 SSE 连接监听最终结果（SUCCESS/FAIL）；
 * SSE 建连失败、连接中断或看门狗超时，降级为一次性调用 getSeckillResult 查询兜底（不做持续轮询）。
 */
import { defineStore } from 'pinia'
import {
  getSeckillActivities,
  getSeckillActivity,
  participateSeckill,
  getSeckillResult
} from '@/api/seckill'
import { subscribeSeckillResult } from '@/api/seckillStream'
import { BusinessError } from '@/api/request'

// SSE 关闭句柄（模块级，不放入响应式 state；store 为单例，全局同一时刻只保留一个在途连接）
let activeSseClose = null

// 看门狗超时：SSE 长时间未推送终态时主动降级查询（后端 SSE 超时约 5min，用户不宜久等）
const RESULT_WATCHDOG_MS = 20000

export const useSeckillStore = defineStore('seckill', {
  state: () => ({
    /** 秒杀活动列表 */
    activities: [],
    /** 当前查看的活动详情 */
    currentActivity: null,
    /** 列表加载状态 */
    listLoading: false,
    /** 详情加载状态 */
    detailLoading: false,
    /**
     * 抢购状态
     * status: 'idle' | 'submitting' | 'queuing' | 'success' | 'fail'
     */
    purchaseStatus: 'idle',
    /** 抢购成功后的订单号 */
    purchaseOrderNo: null,
    /** 抢购失败原因 */
    purchaseFailReason: null,
    /**
     * step-up 验证码状态
     * 当后端返回 40810 时置为 true，前端弹出 Turnstile widget
     */
    captchaRequired: false
  }),

  getters: {
    /** 进行中的活动 */
    activeActivities: (state) => {
      return state.activities.filter(a => a.activityStatus === 'IN_PROGRESS')
    },
    /** 未开始的活动 */
    upcomingActivities: (state) => {
      return state.activities.filter(a => a.activityStatus === 'NOT_STARTED')
    }
  },

  actions: {
    /**
     * 获取秒杀活动列表
     */
    async fetchActivities() {
      this.listLoading = true
      try {
        const data = await getSeckillActivities()
        this.activities = Array.isArray(data) ? data : []
      } catch (error) {
        console.error('获取秒杀活动列表失败:', error)
        this.activities = []
      } finally {
        this.listLoading = false
      }
    },

    /**
     * 获取秒杀活动详情
     * @param {string} id - 活动 ID（雪花 ID 字符串，按不透明字符串透传）
     */
    async fetchActivity(id) {
      this.detailLoading = true
      try {
        const data = await getSeckillActivity(id)
        this.currentActivity = data
        return data
      } catch (error) {
        console.error('获取秒杀活动详情失败:', error)
        this.currentActivity = null
        throw error
      } finally {
        this.detailLoading = false
      }
    },

    /**
     * 提交抢购请求
     * @param {string} activityId - 活动 ID（雪花 ID 字符串，按不透明字符串透传）
     * @param {Object} payload - { quantity, addressId, skuId?, cfToken? }
     * @returns {{ needCaptcha: boolean, inFlight?: boolean }} — needCaptcha=true 时调用方应弹 Turnstile
     */
    async participate(activityId, payload) {
      // 并发守卫——已在提交/排队中时拒绝重复发起，防快速连点产生多条 MQ 消息
      if (this.purchaseStatus === 'submitting' || this.purchaseStatus === 'queuing') {
        return { needCaptcha: false, inFlight: true }
      }
      this.purchaseStatus = 'submitting'
      this.captchaRequired = false
      this.purchaseOrderNo = null
      this.purchaseFailReason = null

      try {
        await participateSeckill(activityId, payload)
        this.purchaseStatus = 'queuing'
        return { needCaptcha: false }
      } catch (error) {
        if (error instanceof BusinessError && error.code === 40810) {
          // step-up：需要人机验证，恢复空闲态并弹 Turnstile
          this.purchaseStatus = 'idle'
          this.captchaRequired = true
          return { needCaptcha: true }
        }
        // 其他错误（40811 闸门拒绝、售罄、42900 限流等）——拦截器可能已弹 toast
        this.purchaseStatus = 'idle'
        throw error
      }
    },

    /**
     * 建立 SSE 订阅并等待抢购最终结果；建连失败/中断/看门狗超时降级为一次性查询兜底。
     * 该方法始终 resolve（不 reject）为终态对象 { status, orderNo?, failReason? }：
     *   status ∈ 'SUCCESS' | 'FAIL' | 'PENDING'（PENDING = 仍排队中，提示用户稍后在订单中查看）
     * @param {string} activityId - 活动 ID（雪花 ID 字符串，按不透明字符串透传）
     * @returns {Promise<{ status: string, orderNo?: string, failReason?: string }>}
     */
    async awaitResult(activityId) {
      // 兜底关掉可能残留的旧连接
      this.closeSse()

      return new Promise((resolve) => {
        let done = false
        let watchdog = null

        const finish = (result) => {
          if (done) return
          done = true
          if (watchdog) { clearTimeout(watchdog); watchdog = null }
          this.closeSse()

          if (result.status === 'SUCCESS') {
            this.purchaseStatus = 'success'
            this.purchaseOrderNo = result.orderNo
          } else if (result.status === 'FAIL') {
            this.purchaseStatus = 'fail'
            this.purchaseFailReason = result.failReason || '抢购失败'
          } else {
            // PENDING：仍排队中，恢复可操作态，由组件提示稍后查看
            this.purchaseStatus = 'idle'
          }
          resolve(result)
        }

        // 一次性降级查询兜底（不做持续轮询）
        const fallbackQuery = async () => {
          if (done) return
          try {
            const r = await getSeckillResult(activityId)
            if (r.status === 'SUCCESS' || r.status === 'FAIL') {
              finish(r)
            } else {
              // QUEUING / NOT_FOUND → 仍在排队
              finish({ status: 'PENDING', activityId })
            }
          } catch (e) {
            console.error('降级查询抢购结果失败:', e)
            finish({ status: 'PENDING', activityId })
          }
        }

        activeSseClose = subscribeSeckillResult(activityId, {
          onResult: (vo) => finish(vo),
          onError: () => fallbackQuery()
        })

        // 看门狗：超时仍无终态 → 触发一次降级查询
        watchdog = setTimeout(fallbackQuery, RESULT_WATCHDOG_MS)
      })
    },

    /**
     * 关闭在途 SSE 连接（组件卸载 / 拿到终态 / 重新发起时调用）
     */
    closeSse() {
      if (activeSseClose) {
        try { activeSseClose() } catch (e) { /* 关闭异常忽略 */ }
        activeSseClose = null
      }
    },

    /**
     * 重置抢购状态（切换活动或组件卸载时调用）
     */
    resetPurchase() {
      this.closeSse()
      this.purchaseStatus = 'idle'
      this.purchaseOrderNo = null
      this.purchaseFailReason = null
      this.captchaRequired = false
    }
  }
})
