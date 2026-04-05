<template>
  <div class="order-list-page">
    <div class="container">

      <h1 class="page-title">我的订单</h1>

      <!-- Archive mode banner -->
      <div v-if="showingArchive" class="archive-banner">
        <span>正在查看历史订单</span>
        <button class="archive-back-btn" @click="exitArchiveMode">返回当前订单</button>
      </div>

      <!-- Status filter -->
      <div v-if="!showingArchive" class="status-tabs">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          class="tab-btn"
          :class="{ active: activeStatus === tab.value }"
          @click="handleTabChange(tab.value)"
        >{{ tab.label }}</button>
      </div>

      <Loading v-if="loading" />
      <Empty v-else-if="!orders.length" type="order" text="暂无订单" />

      <div v-else class="order-list">
        <div v-for="order in orders" :key="order.id" class="order-card">

          <div class="order-head">
            <div class="head-meta">
              <span class="order-no">{{ order.orderNo }}</span>
              <span class="order-date">{{ formatDate(order.createdAt) }}</span>
            </div>
            <span :class="['status-badge', 'status-' + order.status.toLowerCase()]">
              {{ statusText[order.status] }}
            </span>
          </div>

          <div class="order-items">
            <div v-for="item in order.items" :key="item.id" class="order-item">
              <div class="item-img">
                <img :src="item.productImage || '/placeholder.png'" :alt="item.productName" />
              </div>
              <div class="item-details">
                <h4 class="item-name">{{ item.productName }}</h4>
                <p v-if="item.specDesc" class="item-spec-desc">{{ item.specDesc }}</p>
                <p class="item-price">¥{{ formatPrice(item.unitPrice) }} × {{ item.quantity }}</p>
              </div>
            </div>
          </div>

          <div class="order-foot">
            <div class="order-total">
              合计 <strong>¥{{ formatPrice(order.totalAmount) }}</strong>
            </div>
            <div class="order-actions">
              <button class="link-btn" @click="goToDetail(order.orderNo)">查看详情</button>
              <button
                v-if="order.status === 'UNPAID' && !showingArchive"
                class="action-btn action-btn--fill"
                @click="goToPay(order.orderNo)"
              >去支付</button>
              <button
                v-if="(order.status === 'UNPAID' || order.status === 'PAID') && !showingArchive"
                class="action-btn action-btn--outline"
                @click="handleCancel(order.orderNo)"
              >取消订单</button>
              <button
                v-if="order.status === 'SHIPPED' && !showingArchive"
                class="action-btn action-btn--fill"
                @click="handleConfirm(order.orderNo)"
              >确认收货</button>
              <button
                v-if="(order.status === 'SHIPPED' || order.status === 'COMPLETED') && !showingArchive"
                class="action-btn action-btn--muted"
                @click="handleCancelShipped"
              >取消订单</button>
            </div>
          </div>

        </div>
      </div>

      <!-- Archive entry: shown on last page of normal mode when tab is ALL -->
      <div
        v-if="isLastPage && !showingArchive && activeStatus === 'ALL' && orders.length > 0"
        class="archive-entry"
        @click="enterArchiveMode"
      >
        查看更早的历史订单 &gt;
      </div>

      <!-- Pagination -->
      <div v-if="total > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getOrders, getOrdersByStatus, cancelOrder, confirmOrder, getArchivedOrders } from '@/api/order'
import { formatPrice, formatDate } from '@/utils/format'
import { ORDER_STATUS_TEXT, ORDER_STATUS_TAG_TYPE } from '@/utils/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'

const router = useRouter()

const activeStatus = ref('ALL')
const orders = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showingArchive = ref(false)

const statusText = ORDER_STATUS_TEXT
const statusTagType = ORDER_STATUS_TAG_TYPE

const statusTabs = [
  { value: 'ALL', label: '全部' },
  { value: 'UNPAID', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' }
]

const isLastPage = computed(() => {
  return total.value > 0 && currentPage.value >= Math.ceil(total.value / pageSize.value)
})

const fetchOrders = async () => {
  loading.value = true
  try {
    let data
    if (activeStatus.value === 'ALL') {
      data = await getOrders({ page: currentPage.value, size: pageSize.value })
    } else {
      data = await getOrdersByStatus(activeStatus.value, { page: currentPage.value, size: pageSize.value })
    }
    orders.value = data.list || data.content || data || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取订单失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchArchiveOrders = async () => {
  loading.value = true
  try {
    const data = await getArchivedOrders({ page: currentPage.value, size: pageSize.value })
    orders.value = data.list || data.content || data || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取归档订单失败:', error)
  } finally {
    loading.value = false
  }
}

const enterArchiveMode = () => {
  showingArchive.value = true
  currentPage.value = 1
  fetchArchiveOrders()
}

const exitArchiveMode = () => {
  showingArchive.value = false
  currentPage.value = 1
  fetchOrders()
}

const handleTabChange = (status) => {
  activeStatus.value = status
  currentPage.value = 1
  fetchOrders()
}

const handlePageChange = (page) => {
  currentPage.value = page
  if (showingArchive.value) {
    fetchArchiveOrders()
  } else {
    fetchOrders()
  }
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  if (showingArchive.value) {
    fetchArchiveOrders()
  } else {
    fetchOrders()
  }
}

const goToDetail = (orderNo) => router.push(`/orders/${orderNo}`)
const goToPay = (orderNo) => router.push(`/payment/${orderNo}`)

const handleCancel = async (orderNo) => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelOrder(orderNo)
    ElMessage.success('订单已取消')
    fetchOrders()
  } catch {}
}

const handleConfirm = async (orderNo) => {
  try {
    await ElMessageBox.confirm('确认已收到货物吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
    await confirmOrder(orderNo)
    ElMessage.success('确认收货成功')
    fetchOrders()
  } catch {}
}

const handleCancelShipped = () => {
  ElMessageBox.alert('商品已发货，无法取消订单。如需退货，请联系客服。', '无法取消', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

onMounted(fetchOrders)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.order-list-page {
  background: $bg-color;
  min-height: calc(100vh - 68px);
  padding: $spacing-lg 0 $spacing-xxl;
}

.page-title {
  font-size: $font-size-xxl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
  margin-bottom: $spacing-xl;
}

// Status tabs
.status-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid $border-light;
  margin-bottom: $spacing-xl;
}

.tab-btn {
  padding: 10px 20px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  font-size: $font-size-sm;
  color: $text-secondary;
  cursor: pointer;
  font-family: $font-family;
  transition: color $transition-base, border-color $transition-base;

  &:hover { color: $text-primary; }

  &.active {
    color: $text-primary;
    font-weight: $font-weight-bold;
    border-bottom-color: $primary-color;
  }
}

// Order cards
.order-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.order-card {
  border: 1px solid $border-light;
}

.order-head {
  @include flex-between;
  padding: $spacing-md $spacing-lg;
  background: $bg-page;
  border-bottom: 1px solid $border-lighter;
}

.head-meta {
  display: flex;
  gap: $spacing-lg;
  align-items: center;
}

.order-no {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.03em;
  color: $text-primary;
}

.order-date {
  font-size: $font-size-xs;
  color: $text-placeholder;
}

.status-badge {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;

  &.status-unpaid { color: #d97706; }
  &.status-paid   { color: #2563eb; }
  &.status-shipped { color: #7c3aed; }
  &.status-completed { color: #059669; }
  &.status-cancelled { color: $text-placeholder; }
}

.order-items {
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.order-item {
  display: flex;
  gap: $spacing-md;
  align-items: flex-start;
}

.item-img {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  background: $bg-gray;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.item-details { flex: 1; }

.item-name {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-primary;
  margin: 0 0 4px;
}

.item-spec-desc {
  font-size: $font-size-xs;
  color: $text-placeholder;
  margin: 0 0 2px;
}

.item-price {
  font-size: $font-size-xs;
  color: $text-secondary;
  margin: 0;
}

.order-foot {
  @include flex-between;
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid $border-lighter;
  background: $bg-page;
}

.order-total {
  font-size: $font-size-sm;
  color: $text-secondary;

  strong {
    font-size: $font-size-md;
    color: $text-primary;
    margin-left: 4px;
  }
}

.order-actions {
  display: flex;
  gap: $spacing-sm;
  align-items: center;
}

.link-btn {
  background: none;
  border: none;
  font-size: $font-size-xs;
  color: $text-secondary;
  cursor: pointer;
  font-family: $font-family;
  text-decoration: underline;
  padding: 0;

  &:hover { color: $text-primary; }
}

.action-btn {
  padding: 7px 16px;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.04em;
  cursor: pointer;
  font-family: $font-family;
  border: 1px solid transparent;
  transition: background $transition-base, color $transition-base;

  &--fill {
    background: $primary-color;
    color: #fff;
    &:hover { background: #333; }
  }

  &--outline {
    background: transparent;
    color: $text-primary;
    border-color: $border-base;
    &:hover { border-color: $primary-color; }
  }

  &--muted {
    background: transparent;
    color: $text-placeholder;
    border-color: $border-lighter;
    &:hover { color: $text-secondary; border-color: $border-light; }
  }
}

// Archive entry
.archive-entry {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 12px 0;
  margin-top: 16px;
  color: #999;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: #666;
  }
}

// Archive mode banner
.archive-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: $spacing-xl;
  background: #f5f5f5;
  border: 1px solid $border-light;
  color: $text-secondary;
  font-size: $font-size-sm;
}

.archive-back-btn {
  background: none;
  border: 1px solid $border-base;
  padding: 4px 12px;
  font-size: $font-size-xs;
  color: $text-primary;
  cursor: pointer;
  font-family: $font-family;
  transition: border-color $transition-base;

  &:hover {
    border-color: $primary-color;
  }
}

// Pagination
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: $spacing-xl;
}
</style>
