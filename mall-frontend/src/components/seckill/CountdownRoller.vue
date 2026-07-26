<template>
  <div v-if="hasTime" class="countdown-roller" :class="{ 'countdown-roller--urgent': isUrgent }">
    <span class="cd-label">{{ label }}</span>
    <div class="cd-units">
      <!-- 时 -->
      <div class="cd-group">
        <div class="cd-digits">
          <OdometerDigit v-for="(d, i) in hourDigits" :key="'h' + i" :value="d" />
        </div>
        <span class="cd-unit">时</span>
      </div>
      <span class="cd-colon">:</span>
      <!-- 分 -->
      <div class="cd-group">
        <div class="cd-digits">
          <OdometerDigit v-for="(d, i) in minDigits" :key="'m' + i" :value="d" />
        </div>
        <span class="cd-unit">分</span>
      </div>
      <span class="cd-colon">:</span>
      <!-- 秒 -->
      <div class="cd-group cd-group--sec">
        <div class="cd-digits">
          <OdometerDigit v-for="(d, i) in secDigits" :key="'s' + i" :value="d" fast />
        </div>
        <span class="cd-unit">秒</span>
      </div>
      <span class="cd-colon cd-colon--ms">.</span>
      <!-- 毫秒（1 位，飞速滚动） -->
      <div class="cd-group cd-group--ms">
        <div class="cd-digits">
          <OdometerDigit :value="deci" fast ms />
        </div>
        <span class="cd-unit">毫秒</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import dayjs from 'dayjs'
import OdometerDigit from './OdometerDigit.vue'

const props = defineProps({
  // 秒杀活动对象（需含 startTime/endTime/serverTime）
  activity: { type: Object, default: null },
  // 当前相位：'not_started' | 'in_progress' | 'ended'（来自父级 useCountdown）
  phase: { type: String, default: 'in_progress' },
  label: { type: String, default: '' }
})

// ── 与服务器时钟对齐（同 useCountdown 的 offset 思路） ──
let clockOffset = 0
const syncOffset = () => {
  if (props.activity?.serverTime) {
    clockOffset = dayjs(props.activity.serverTime).valueOf() - Date.now()
  } else {
    clockOffset = 0
  }
}

// ── 倒计时分量（仅在变化时赋值，减少渲染） ──
const hours = ref(0)
const minutes = ref(0)
const seconds = ref(0)
const deci = ref(0)        // 1 位毫秒（十分之一秒，0-9）
const totalMs = ref(0)
const hasTime = ref(false)

let rafId = null

const targetMs = () => {
  if (!props.activity) return null
  const t = props.phase === 'not_started' ? props.activity.startTime : props.activity.endTime
  return t ? dayjs(t).valueOf() : null
}

const frame = () => {
  const target = targetMs()
  if (target == null) {
    hasTime.value = false
    return
  }
  hasTime.value = true
  const now = Date.now() + clockOffset
  let rem = target - now
  if (rem < 0) rem = 0
  totalMs.value = rem

  const h = Math.floor(rem / 3600000)
  const m = Math.floor((rem % 3600000) / 60000)
  const s = Math.floor((rem % 60000) / 1000)
  const d = Math.floor((rem % 1000) / 100)

  if (h !== hours.value) hours.value = h
  if (m !== minutes.value) minutes.value = m
  if (s !== seconds.value) seconds.value = s
  if (d !== deci.value) deci.value = d

  if (rem > 0) {
    rafId = requestAnimationFrame(frame)
  } else {
    stop()
  }
}

const stop = () => {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
}

const start = () => {
  stop()
  syncOffset()
  if (targetMs() != null) frame()
}

// 活动/相位变化时重启
watch(() => [props.activity, props.phase], start, { immediate: true })

onUnmounted(stop)

// ── 数字位拆分 ──
const pad2 = (n) => String(n).padStart(2, '0')
const toDigits = (str) => str.split('').map(Number)

// 时位至少 2 位，时间很长时自动扩展位数
const hourDigits = computed(() => toDigits(pad2(hours.value)))
const minDigits = computed(() => toDigits(pad2(minutes.value)))
const secDigits = computed(() => toDigits(pad2(seconds.value)))

// 进行中且剩余不足 1 分钟 → 紧迫强化
const isUrgent = computed(() => props.phase === 'in_progress' && totalMs.value > 0 && totalMs.value < 60000)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.countdown-roller {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.cd-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  white-space: nowrap;
}

.cd-units {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}

.cd-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.cd-digits {
  display: flex;
  background: $text-primary;
  color: #fff;
  padding: 6px 6px;
  border-radius: 3px;
  font-size: 26px;
  font-weight: $font-weight-bold;
  font-variant-numeric: tabular-nums;
  box-shadow: inset 0 -6px 12px rgba(0, 0, 0, 0.25);
}

.cd-group--ms .cd-digits {
  background: $danger-color;
}

.cd-unit {
  font-size: $font-size-xs;
  color: $text-secondary;
  letter-spacing: 0.04em;
}

.cd-colon {
  align-self: flex-start;
  margin-top: 8px;
  font-size: 22px;
  font-weight: $font-weight-bold;
  color: $text-primary;

  &--ms { color: $danger-color; }
}

// ── 紧迫强化：最后一分钟仅"秒/毫秒"变红+脉冲，时/分保持黑色 ──
.countdown-roller--urgent {
  .cd-group--sec .cd-digits,
  .cd-group--ms .cd-digits {
    background: $danger-color;
    animation: cd-pulse 0.8s ease-in-out infinite;
  }
}

@keyframes cd-pulse {
  0%, 100% { transform: scale(1); box-shadow: inset 0 -6px 12px rgba(0, 0, 0, 0.25); }
  50% { transform: scale(1.06); box-shadow: inset 0 -6px 12px rgba(0, 0, 0, 0.25), 0 0 12px rgba(192, 57, 43, 0.6); }
}
</style>
