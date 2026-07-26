/**
 * 秒杀抢购结果 SSE 订阅封装
 *
 * 为什么不走 Axios / 原生 EventSource：
 *   - 后端 SSE 接口鉴权走标准 `Authorization: Bearer <token>` 头（不支持 query token）；
 *     原生 EventSource 无法自定义请求头，故改用 @microsoft/fetch-event-source 以 fetch 流建连。
 *   - token 取法与 `@/api/request` 一致（均取自 `@/utils/storage` 的 getToken）。
 *
 * 事件契约（对齐后端 SeckillSseService）：
 *   event: result   data: SeckillResultVO 的 JSON（status 只会是 SUCCESS / FAIL）
 *   ": ping" 注释心跳由库自动忽略；服务端推送结果后主动关闭连接；连接超时约 5 分钟。
 *   建连时若结果已出会立即补推，可能收到重复 result 事件——本封装以 settled 标记幂等处理，首个终态即关闭。
 */
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getToken } from '@/utils/storage'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

/**
 * 订阅指定活动的抢购结果流
 *
 * @param {string} activityId 活动 ID（雪花 ID 字符串，原样拼进 SSE 路径）
 * @param {Object}   handlers
 * @param {(result: Object) => void} handlers.onResult 收到终态结果事件（SeckillResultVO，status=SUCCESS/FAIL）
 * @param {(err?: Error) => void}    handlers.onError  建连失败/鉴权失败/连接中断/服务端关闭却未推送结果——调用方据此降级一次性查询
 * @returns {() => void} 关闭连接的函数（组件卸载或已拿到终态后调用）
 */
export function subscribeSeckillResult(activityId, { onResult, onError } = {}) {
  const ctrl = new AbortController()
  // 收到终态或已关闭后置 true，避免 onResult/onError 重复触发（含建连补推的重复 result 事件）
  let settled = false

  const close = () => {
    if (!ctrl.signal.aborted) ctrl.abort()
  }

  fetchEventSource(`${BASE_URL}/seckill/activities/${activityId}/stream`, {
    signal: ctrl.signal,
    headers: {
      Authorization: `Bearer ${getToken()}`,
      Accept: 'text/event-stream'
    },
    // 页面切到后台时保持连接（默认会在 visibilitychange 时断开重连，秒杀结果需持续等待）
    openWhenHidden: true,

    async onopen(response) {
      const contentType = response.headers.get('content-type') || ''
      if (response.ok && contentType.includes('text/event-stream')) {
        return // 建连成功
      }
      // 鉴权失败为非 SSE 的 401 JSON，或其他异常响应 → 抛出触发 onerror → 降级查询兜底
      throw new Error(`SSE 建连失败：status=${response.status}, content-type=${contentType}`)
    },

    onmessage(ev) {
      // 非 result 事件（含心跳注释）忽略；心跳为注释行不会进入此回调
      if (ev.event !== 'result' || settled) return
      try {
        const vo = JSON.parse(ev.data)
        settled = true
        onResult?.(vo)
      } catch (e) {
        console.error('解析 SSE 结果事件失败:', e)
      } finally {
        close()
      }
    },

    onclose() {
      // 服务端正常关闭：若尚未收到终态（异常关闭），触发降级
      if (!settled) {
        settled = true
        onError?.(new Error('SSE 连接已关闭但未收到结果'))
      }
    },

    onerror(err) {
      if (!settled) {
        settled = true
        onError?.(err instanceof Error ? err : new Error('SSE 连接错误'))
      }
      // 抛出以阻止库自动重连，交由一次性降级查询兜底（不做持续重连轮询）
      throw err
    }
  }).catch(() => {
    // abort 或 onerror 抛出后 promise 会 reject，回调已处理，这里静默吞掉
  })

  return close
}
