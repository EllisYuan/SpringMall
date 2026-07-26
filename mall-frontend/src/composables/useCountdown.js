/**
 * useCountdown —— 服务器时钟校准倒计时 Composable
 *
 * 原理：
 *   1. 初始化时用活动 VO 中携带的 serverTime 计算 offset = serverTime - 本地时间（ms）。
 *   2. 后续每秒递减均以 "本地时间 + offset" 作为"校准时间"，与服务器时钟对齐。
 *   3. 当剩余时间 > resyncThreshold（默认 5 分钟）时，每 resyncInterval（默认 60 秒）
 *      调用一次 GET /seckill/time 重新对齐，防止本地时钟漂移导致倒计时偏差。
 *
 * 用法：
 *   const { timeLeft, phase, formattedTime } = useCountdown(activity, options)
 *
 *   activity: Ref<SeckillActivityVO | null>，响应式活动对象
 *   options:
 *     onStart?:  () => void  — 活动开始时回调
 *     onEnd?:    () => void  — 活动结束时回调
 *     resyncInterval?:   number  — 重新对齐间隔（秒），默认 60
 *     resyncThreshold?:  number  — 剩余多少秒以上才重新对齐，默认 300（5 分钟）
 *
 * 返回：
 *   timeLeft:      Ref<number>  — 剩余秒数（到下一个关键时间点）
 *   phase:         Ref<string>  — 'not_started' | 'in_progress' | 'ended'
 *   formattedTime: ComputedRef<string>  — 格式化倒计时字符串 "HH:MM:SS"
 */
import { ref, computed, watch, onUnmounted } from 'vue'
import dayjs from 'dayjs'
import { getServerTime } from '@/api/seckill'

export function useCountdown(activity, options = {}) {
  const {
    onStart,
    onEnd,
    resyncInterval = 60,
    resyncThreshold = 300
  } = options

  // 服务器与本地时钟的偏差（毫秒），正值表示服务器快于本地
  let clockOffset = 0

  const timeLeft = ref(0)
  const phase = ref('not_started')

  // 各定时器句柄
  let tickTimer = null
  let resyncTimer = null

  // ── 校准时间获取 ──────────────────────────────────────
  const getAdjustedNow = () => Date.now() + clockOffset

  // ── 初始化 offset ─────────────────────────────────────
  const initOffset = (serverTimeStr) => {
    if (!serverTimeStr) return
    const serverMs = dayjs(serverTimeStr).valueOf()
    clockOffset = serverMs - Date.now()
  }

  // ── 计算当前 phase 和 timeLeft ──────────────────────────
  const computeState = () => {
    if (!activity.value) {
      timeLeft.value = 0
      phase.value = 'not_started'
      return
    }

    const now = getAdjustedNow()
    const startMs = dayjs(activity.value.startTime).valueOf()
    const endMs = dayjs(activity.value.endTime).valueOf()

    if (now < startMs) {
      phase.value = 'not_started'
      timeLeft.value = Math.max(0, Math.floor((startMs - now) / 1000))
    } else if (now < endMs) {
      phase.value = 'in_progress'
      timeLeft.value = Math.max(0, Math.floor((endMs - now) / 1000))
    } else {
      phase.value = 'ended'
      timeLeft.value = 0
    }
  }

  // ── 重新对齐服务器时间 ─────────────────────────────────
  const resync = async () => {
    // 仅在剩余时间较长时重新对齐，短倒计时避免网络延迟扰动
    if (timeLeft.value < resyncThreshold) return
    try {
      const serverTimeStr = await getServerTime()
      initOffset(serverTimeStr)
      computeState()
    } catch {
      // 网络问题静默失败，继续用已有 offset
    }
  }

  // ── 每秒 tick ─────────────────────────────────────────
  let prevPhase = ''
  const tick = () => {
    computeState()

    // 相位切换回调
    if (phase.value !== prevPhase) {
      if (phase.value === 'in_progress' && prevPhase === 'not_started') {
        onStart?.()
      }
      if (phase.value === 'ended' && prevPhase === 'in_progress') {
        onEnd?.()
        stopAll()
        return
      }
      prevPhase = phase.value
    }

    if (phase.value === 'ended') {
      stopAll()
    }
  }

  // ── 启动定时器 ─────────────────────────────────────────
  const start = () => {
    stopAll()

    if (!activity.value) return

    // 用活动 VO 中的 serverTime 初始化 offset
    if (activity.value.serverTime) {
      initOffset(activity.value.serverTime)
    }

    computeState()
    prevPhase = phase.value

    if (phase.value === 'ended') return

    tickTimer = setInterval(tick, 1000)
    resyncTimer = setInterval(resync, resyncInterval * 1000)
  }

  // ── 停止所有定时器 ─────────────────────────────────────
  const stopAll = () => {
    if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
    if (resyncTimer) { clearInterval(resyncTimer); resyncTimer = null }
  }

  // ── 监听 activity 变化（列表页多个卡片各自传入不同活动）──
  watch(
    () => activity.value,
    (newVal) => {
      if (newVal) start()
      else stopAll()
    },
    { immediate: true }
  )

  onUnmounted(stopAll)

  // ── 格式化输出 ──────────────────────────────────────────
  const formattedTime = computed(() => {
    const s = timeLeft.value
    const h = Math.floor(s / 3600)
    const m = Math.floor((s % 3600) / 60)
    const sec = s % 60
    if (h > 0) {
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
    }
    return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  })

  return { timeLeft, phase, formattedTime, resync, stopAll }
}
