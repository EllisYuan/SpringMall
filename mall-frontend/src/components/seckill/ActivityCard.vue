<template>
  <div class="activity-card" @click="$emit('click')">
    <div class="card-image">
      <img
        :src="activity.productImage || '/placeholder.png'"
        :alt="activity.activityName"
        loading="lazy"
      />
      <span class="status-badge" :class="statusBadgeClass">
        {{ statusLabel }}
      </span>
    </div>
    <div class="card-body">
      <h3 class="card-name">{{ activity.activityName }}</h3>
      <div class="card-price">
        <span class="price-seckill">¥{{ formatPrice(activity.seckillPrice) }}</span>
        <span class="price-original">¥{{ formatPrice(activity.originalPrice) }}</span>
      </div>
      <div class="card-stock">
        <div class="stock-bar">
          <div class="stock-bar__fill" :style="{ width: stockRate + '%' }"></div>
        </div>
        <span class="stock-text">剩余 {{ activity.availableStock }}</span>
      </div>
      <div v-if="showCountdown" class="card-countdown">
        <span class="countdown-label">{{ countdownLabel }}</span>
        <span class="countdown-time">{{ formattedTime }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useCountdown } from '@/composables/useCountdown'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  activity: { type: Object, required: true }
})

defineEmits(['click'])

// 让 useCountdown 能响应 props 变化
const activityRef = ref(props.activity)
watch(() => props.activity, (v) => { activityRef.value = v })

const { formattedTime, phase } = useCountdown(activityRef)

const statusLabel = computed(() => {
  const s = props.activity?.activityStatus
  if (s === 'NOT_STARTED') return '即将开始'
  if (s === 'IN_PROGRESS') return '抢购中'
  if (s === 'SOLD_OUT') return '已售罄'
  return '已结束'
})

const statusBadgeClass = computed(() => {
  const s = (props.activity?.activityStatus || '').toLowerCase()
  return `status-badge--${s}`
})

const countdownLabel = computed(() => {
  const s = props.activity?.activityStatus
  if (s === 'NOT_STARTED') return '距开始'
  if (s === 'IN_PROGRESS') return '距结束'
  return ''
})

const showCountdown = computed(() => {
  const s = props.activity?.activityStatus
  return (s === 'NOT_STARTED' || s === 'IN_PROGRESS') && formattedTime.value
})

const stockRate = computed(() => {
  const a = props.activity
  if (!a || !a.seckillStock) return 0
  return Math.max(0, Math.min(100, Math.round((a.availableStock / a.seckillStock) * 100)))
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.activity-card {
  background: $bg-color;
  cursor: pointer;
  transition: opacity $transition-base;

  &:hover { opacity: 0.85; }
}

.card-image {
  position: relative;
  aspect-ratio: 3 / 4;
  background: $bg-gray;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-slow;
  }

  &:hover img { transform: scale(1.04); }
}

.status-badge {
  position: absolute;
  top: $spacing-sm;
  left: $spacing-sm;
  padding: 3px 8px;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.04em;
  color: #fff;
  background: $text-primary;

  &--in_progress { background: $danger-color; }
  &--not_started { background: $warning-color; }
  &--sold_out, &--ended { background: $text-secondary; }
}

.card-body {
  padding: $spacing-md;
}

.card-name {
  font-size: $font-size-base;
  font-weight: $font-weight-medium;
  color: $text-primary;
  margin-bottom: $spacing-sm;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.price-seckill {
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  color: $danger-color;
}

.price-original {
  font-size: $font-size-sm;
  color: $text-placeholder;
  text-decoration: line-through;
}

.card-stock {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.stock-bar {
  flex: 1;
  height: 3px;
  background: $border-light;
  overflow: hidden;

  .stock-bar__fill {
    height: 100%;
    background: $danger-color;
    transition: width 0.3s ease;
  }
}

.stock-text {
  font-size: $font-size-xs;
  color: $text-secondary;
  white-space: nowrap;
}

.card-countdown {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-xs;
}

.countdown-label {
  color: $text-secondary;
}

.countdown-time {
  font-weight: $font-weight-bold;
  font-variant-numeric: tabular-nums;
  color: $text-primary;
  letter-spacing: 0.04em;
}
</style>
