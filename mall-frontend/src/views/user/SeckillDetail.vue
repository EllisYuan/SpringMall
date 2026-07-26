<template>
  <div class="seckill-detail-page">
    <div class="container">
      <Loading v-if="seckillStore.detailLoading" />

      <template v-else-if="seckillStore.currentActivity">
        <div class="detail-layout">
          <!-- 左：商品图 -->
          <div class="detail-image">
            <img
              :src="activity.productImage || '/placeholder.png'"
              :alt="activity.activityName"
            />
          </div>

          <!-- 右：购买面板 -->
          <div class="detail-panel">
            <!-- 活动状态标签 -->
            <div class="activity-status-row">
              <span class="activity-badge" :class="activityBadgeClass">{{ activityStatusLabel }}</span>
              <span class="limit-hint">限购 {{ activity.limitPerUser }} 件 / 人</span>
            </div>

            <h1 class="activity-name">{{ activity.activityName }}</h1>

            <!-- 价格区 -->
            <div class="price-block">
              <div class="price-main">
                <span class="price-label">秒杀价</span>
                <span class="price-value">¥{{ formatPrice(activity.seckillPrice) }}</span>
              </div>
              <div class="price-original">
                原价 <span>¥{{ formatPrice(activity.originalPrice) }}</span>
              </div>
              <div class="price-save" v-if="savedAmount > 0">
                节省 ¥{{ formatPrice(savedAmount) }}
              </div>
            </div>

            <!-- 库存进度 -->
            <div class="stock-block">
              <div class="stock-header">
                <span class="stock-label">剩余库存</span>
                <span class="stock-count">{{ activity.availableStock }} / {{ activity.seckillStock }}</span>
              </div>
              <div class="stock-bar">
                <div class="stock-bar__fill" :style="{ width: stockRate + '%' }"></div>
              </div>
            </div>

            <!-- 倒计时（老虎机滚动 + 毫秒，凸显抢单紧迫） -->
            <CountdownRoller
              v-if="showCountdown"
              class="detail-countdown"
              :activity="activity"
              :phase="phase"
              :label="countdownLabel"
            />

            <!-- 地址选择 -->
            <div class="address-block" v-if="isActionable">
              <div class="block-title">收货地址</div>
              <div v-if="!addresses.length" class="no-address">
                <span>您还没有收货地址</span>
                <router-link to="/address" class="addr-link">去添加</router-link>
              </div>
              <div v-else class="address-list">
                <div
                  v-for="addr in addresses"
                  :key="addr.id"
                  class="address-item"
                  :class="{ active: selectedAddressId === addr.id }"
                  @click="selectedAddressId = addr.id"
                >
                  <div class="addr-info">
                    <span class="addr-name">{{ addr.receiverName }}</span>
                    <span class="addr-phone">{{ addr.receiverPhone }}</span>
                    <el-tag v-if="addr.isDefault" size="small" type="info">默认</el-tag>
                  </div>
                  <p class="addr-detail">
                    {{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}
                  </p>
                </div>
              </div>
            </div>

            <!-- step-up Turnstile 区域：当 captchaRequired=true 时显示 -->
            <div v-if="captchaVisible" class="captcha-block">
              <p class="captcha-hint">为保障您的权益，请完成人机验证后再次抢购</p>
              <div ref="turnstileContainer"></div>
            </div>

            <!-- 抢购按钮 -->
            <div class="action-block">
              <button
                v-if="activity.activityStatus === 'IN_PROGRESS'"
                class="btn-seckill"
                :disabled="btnDisabled"
                :class="{ 'btn-seckill--loading': isSubmitting }"
                @click="handleParticipate"
              >
                <span v-if="isSubmitting" class="btn-spinner"></span>
                <span>{{ btnText }}</span>
              </button>

              <button
                v-else-if="activity.activityStatus === 'NOT_STARTED'"
                class="btn-seckill btn-seckill--upcoming"
                disabled
              >
                {{ formattedTime ? `距开始 ${formattedTime}` : '即将开始' }}
              </button>

              <button
                v-else-if="activity.activityStatus === 'SOLD_OUT'"
                class="btn-seckill btn-seckill--disabled"
                disabled
              >
                已售罄
              </button>

              <button
                v-else
                class="btn-seckill btn-seckill--disabled"
                disabled
              >
                活动已结束
              </button>
            </div>

            <!-- 抢购结果提示 -->
            <div v-if="seckillStore.purchaseStatus === 'queuing'" class="result-hint result-hint--queuing">
              排队中，请稍候…
            </div>
            <div v-else-if="seckillStore.purchaseStatus === 'success'" class="result-hint result-hint--success">
              抢购成功！正在跳转到支付页…
            </div>
            <div v-else-if="seckillStore.purchaseStatus === 'fail'" class="result-hint result-hint--fail">
              {{ seckillStore.purchaseFailReason || '很遗憾，本次抢购未成功' }}
            </div>
          </div>
        </div>
      </template>

      <Empty v-else type="order" text="活动不存在或已下线" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSeckillStore } from '@/store/seckill'
import { useUserStore } from '@/store/user'
import { useCountdown } from '@/composables/useCountdown'
import { BusinessError } from '@/api/request'
import { formatPrice } from '@/utils/format'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'
import CountdownRoller from '@/components/seckill/CountdownRoller.vue'

const route = useRoute()
const router = useRouter()
const seckillStore = useSeckillStore()
const userStore = useUserStore()

// ── 活动数据 ─────────────────────────────────────────────
const activityRef = computed(() => seckillStore.currentActivity)
const activity = computed(() => seckillStore.currentActivity || {})

// ── 倒计时 ────────────────────────────────────────────────
const { formattedTime, phase } = useCountdown(activityRef, {
  onStart: () => {
    // 活动开始时刷新详情，更新 activityStatus
    seckillStore.fetchActivity(route.params.id)
  },
  onEnd: () => {
    seckillStore.fetchActivity(route.params.id)
  }
})

const showCountdown = computed(() => {
  const s = activity.value?.activityStatus
  return (s === 'NOT_STARTED' || s === 'IN_PROGRESS') && formattedTime.value
})

const countdownLabel = computed(() => {
  const s = activity.value?.activityStatus
  if (s === 'NOT_STARTED') return '距开始'
  if (s === 'IN_PROGRESS') return '距结束'
  return ''
})

// 详情页倒计时改用 CountdownRoller（毫秒级 rAF）；formattedTime 仍用于"距开始"按钮文案

// ── 状态标签 ─────────────────────────────────────────────
const activityStatusLabel = computed(() => {
  const s = activity.value?.activityStatus
  if (s === 'NOT_STARTED') return '即将开始'
  if (s === 'IN_PROGRESS') return '抢购中'
  if (s === 'SOLD_OUT') return '已售罄'
  return '已结束'
})

const activityBadgeClass = computed(() => {
  const s = (activity.value?.activityStatus || '').toLowerCase()
  return `activity-badge--${s}`
})

// ── 价格计算 ─────────────────────────────────────────────
const savedAmount = computed(() => {
  const a = activity.value
  if (!a.originalPrice || !a.seckillPrice) return 0
  return Math.max(0, a.originalPrice - a.seckillPrice)
})

const stockRate = computed(() => {
  const a = activity.value
  if (!a.seckillStock) return 0
  return Math.max(0, Math.min(100, Math.round((a.availableStock / a.seckillStock) * 100)))
})

const isActionable = computed(() => activity.value?.activityStatus === 'IN_PROGRESS')

// ── 地址 ─────────────────────────────────────────────────
const addresses = ref([])
const selectedAddressId = ref(null)

const fetchAddresses = async () => {
  try {
    await userStore.fetchAddresses()
    addresses.value = userStore.addresses
    const def = addresses.value.find(a => a.isDefault)
    selectedAddressId.value = (def || addresses.value[0])?.id || null
  } catch (e) {
    console.error('获取地址失败:', e)
  }
}

// ── Turnstile step-up ────────────────────────────────────
const turnstileContainer = ref(null)
const captchaVisible = ref(false)
const cfToken = ref('')
let turnstileWidgetId = null

const TURNSTILE_SITEKEY = '0x4AAAAAAC_iBZYfs4zqBg5x'

const renderTurnstile = () => {
  if (!turnstileContainer.value) return
  if (turnstileWidgetId !== null) {
    window.turnstile?.reset(turnstileWidgetId)
    return
  }
  const doRender = () => {
    turnstileWidgetId = window.turnstile.render(turnstileContainer.value, {
      sitekey: TURNSTILE_SITEKEY,
      callback: (token) => { cfToken.value = token },
      'expired-callback': () => { cfToken.value = '' },
      'error-callback': () => { cfToken.value = '' }
    })
  }
  if (window.turnstile) {
    doRender()
  } else {
    window.addEventListener('turnstile:loaded', doRender, { once: true })
  }
}

const destroyTurnstile = () => {
  if (turnstileWidgetId !== null) {
    window.turnstile?.remove(turnstileWidgetId)
    turnstileWidgetId = null
  }
  cfToken.value = ''
}

// 当 captchaRequired 变化时显示/隐藏 widget
watch(() => seckillStore.captchaRequired, async (required) => {
  if (required) {
    captchaVisible.value = true
    // 等 DOM 更新后再渲染 widget
    await nextTick()
    renderTurnstile()
  } else {
    captchaVisible.value = false
    destroyTurnstile()
  }
})

// ── 按钮状态（防抖+禁用） ───────────────────────────────────
const isSubmitting = computed(() =>
  seckillStore.purchaseStatus === 'submitting' || seckillStore.purchaseStatus === 'queuing'
)

const btnDisabled = computed(() => {
  if (isSubmitting.value) return true
  if (seckillStore.purchaseStatus === 'success') return true
  // step-up 待完成验证码
  if (captchaVisible.value && !cfToken.value) return true
  if (!selectedAddressId.value) return true
  return false
})

const btnText = computed(() => {
  if (seckillStore.purchaseStatus === 'submitting') return '提交中…'
  if (seckillStore.purchaseStatus === 'queuing') return '排队中…'
  if (seckillStore.purchaseStatus === 'success') return '抢购成功'
  if (captchaVisible.value && !cfToken.value) return '请先完成验证'
  return '立即抢购'
})

// ── 防抖：上次点击时间戳（500ms 内不可再点） ──────────────
let lastClickTs = 0

const handleParticipate = async () => {
  const now = Date.now()
  if (now - lastClickTs < 500) return  // 防抖
  lastClickTs = now

  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }

  const payload = {
    quantity: 1,
    addressId: selectedAddressId.value,
    skuId: activity.value.skuId || undefined
  }

  // 若 step-up 验证码已完成，携带 cfToken
  if (captchaVisible.value && cfToken.value) {
    payload.cfToken = cfToken.value
    // 重置 step-up 状态，让本次提交正常走
    seckillStore.$patch({ captchaRequired: false })
    captchaVisible.value = false
    destroyTurnstile()
  }

  try {
    const { needCaptcha, inFlight } = await seckillStore.participate(activity.value.id, payload)

    // 已有抢购在途（并发守卫拦截），忽略本次重复触发
    if (inFlight) return

    if (needCaptcha) {
      // store 已将 captchaRequired 设为 true，watch 会触发 renderTurnstile
      return
    }

    // 排队中 → 建立 SSE 监听最终结果（断线/超时自动降级一次性查询兜底）
    const result = await seckillStore.awaitResult(activity.value.id)

    if (result.status === 'SUCCESS') {
      ElMessage.success('抢购成功！正在跳转支付…')
      router.push(`/payment/${result.orderNo}`)
    } else if (result.status === 'FAIL') {
      ElMessage.error(result.failReason || '抢购失败')
    } else {
      // PENDING：仍在排队，提示稍后查看
      ElMessage.warning('排队处理中，请稍后在「我的秒杀订单」查看结果')
    }
  } catch (error) {
    if (error instanceof BusinessError && error.code === 40811) {
      ElMessage.warning('当前参与人数过多，请稍后重试')
    } else {
      // 其他抢购业务错误（如 42900 限流、售罄等）：拦截器已弹 toast
      console.error('抢购出错:', error)
    }
  }
}

// ── 初始化 ────────────────────────────────────────────────
onMounted(async () => {
  seckillStore.resetPurchase()
  await seckillStore.fetchActivity(route.params.id)
  if (isActionable.value) {
    fetchAddresses()
  }
})

onUnmounted(() => {
  destroyTurnstile()
  seckillStore.resetPurchase()
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.seckill-detail-page {
  padding: $spacing-xl 0 $spacing-xxl;
  min-height: 60vh;
}

.detail-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xxl;
  align-items: start;

  @include mobile {
    grid-template-columns: 1fr;
    gap: $spacing-xl;
  }
}

.detail-image {
  img {
    width: 100%;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    display: block;
  }
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

// ── 状态标签行 ────────────────────────────────────────────
.activity-status-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.activity-badge {
  display: inline-block;
  padding: 3px 10px;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  color: #fff;
  background: $text-primary;

  &--in_progress { background: $danger-color; }
  &--not_started { background: $warning-color; }
  &--sold_out, &--ended { background: $text-secondary; }
}

.limit-hint {
  font-size: $font-size-xs;
  color: $text-secondary;
  letter-spacing: 0.02em;
}

// ── 活动名 ────────────────────────────────────────────────
.activity-name {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  color: $text-primary;
  letter-spacing: -0.02em;
  line-height: $line-height-tight;
}

// ── 价格 ─────────────────────────────────────────────────
.price-block {
  padding: $spacing-lg 0;
  border-top: 1px solid $border-light;
  border-bottom: 1px solid $border-light;
}

.price-main {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;
}

.price-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
}

.price-value {
  font-size: 36px;
  font-weight: $font-weight-bold;
  color: $danger-color;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.price-original {
  font-size: $font-size-sm;
  color: $text-placeholder;

  span {
    text-decoration: line-through;
  }
}

.price-save {
  margin-top: $spacing-xs;
  font-size: $font-size-xs;
  color: $danger-color;
  font-weight: $font-weight-medium;
}

// ── 库存 ─────────────────────────────────────────────────
.stock-block {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.stock-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stock-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
}

.stock-count {
  font-size: $font-size-xs;
  color: $text-secondary;
  font-variant-numeric: tabular-nums;
}

.stock-bar {
  height: 4px;
  background: $border-light;
  overflow: hidden;

  .stock-bar__fill {
    height: 100%;
    background: $danger-color;
    transition: width 0.3s ease;
  }
}

// ── 倒计时 ────────────────────────────────────────────────
.countdown-block {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.countdown-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  white-space: nowrap;
}

.countdown-digits {
  display: flex;
  align-items: center;
  gap: 2px;
}

.digit-group {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 36px;
  padding: 6px 4px;
  background: $text-primary;
  color: #fff;
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.digit-sep {
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  color: $text-primary;
  padding: 0 2px;
}

// ── 地址 ─────────────────────────────────────────────────
.address-block {
  border-top: 1px solid $border-light;
  padding-top: $spacing-lg;
}

.block-title {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

.no-address {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  font-size: $font-size-sm;
  color: $text-secondary;
}

.addr-link {
  font-size: $font-size-sm;
  color: $text-primary;
  font-weight: $font-weight-medium;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  max-height: 240px;
  overflow-y: auto;
}

.address-item {
  padding: $spacing-md;
  border: 1px solid $border-light;
  cursor: pointer;
  transition: border-color $transition-base;

  &:hover { border-color: $border-base; }

  &.active {
    border-color: $text-primary;
    border-width: 2px;
  }
}

.addr-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: 4px;
}

.addr-name {
  font-size: $font-size-base;
  font-weight: $font-weight-medium;
  color: $text-primary;
}

.addr-phone {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.addr-detail {
  font-size: $font-size-sm;
  color: $text-secondary;
  line-height: $line-height-base;
}

// ── Captcha ───────────────────────────────────────────────
.captcha-block {
  padding: $spacing-md;
  background: $bg-gray;
  border: 1px solid $border-light;
}

.captcha-hint {
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

// ── 抢购按钮 ─────────────────────────────────────────────
.action-block {
  padding-top: $spacing-md;
}

.btn-seckill {
  width: 100%;
  padding: 18px;
  background: $danger-color;
  color: #fff;
  border: none;
  font-size: $font-size-base;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  transition: background $transition-base, opacity $transition-base;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;

  &:hover:not(:disabled) { background: #a53126; }
  &:disabled { opacity: 0.55; cursor: not-allowed; }

  &--upcoming {
    background: $warning-color;
    &:hover:not(:disabled) { background: #92420a; }
  }

  &--disabled {
    background: $text-secondary;
  }

  &--loading { opacity: 0.85; }
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

// ── 结果提示 ─────────────────────────────────────────────
.result-hint {
  padding: $spacing-md;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  text-align: center;

  &--queuing {
    background: $bg-gray;
    color: $text-secondary;
  }

  &--success {
    background: rgba(45, 119, 56, 0.08);
    color: $success-color;
  }

  &--fail {
    background: rgba(199, 58, 45, 0.08);
    color: $danger-color;
  }
}
</style>
