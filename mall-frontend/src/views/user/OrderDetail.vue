<template>
  <div class="order-detail-page">
    <div class="container">
      <Loading v-if="loading" />
      <div v-else-if="order" class="order-detail">
        <h2 class="page-title">订单详情</h2>

        <!-- 订单状态 -->
        <div class="order-status-card">
          <el-tag :type="statusTagType[order.status]" size="large">
            {{ statusText[order.status] }}
          </el-tag>
        </div>

        <!-- 订单信息 -->
        <div class="section">
          <h3 class="section-title">订单信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">订单号：</span>
              <span>{{ order.orderNo }}</span>
            </div>
            <div class="info-item">
              <span class="label">下单时间：</span>
              <span>{{ formatDate(order.createdAt) }}</span>
            </div>
            <div v-if="order.paymentMethod" class="info-item">
              <span class="label">支付方式：</span>
              <span>{{ paymentMethodText[order.paymentMethod] || order.paymentMethod }}</span>
            </div>
            <div v-if="order.paymentTime" class="info-item">
              <span class="label">支付时间：</span>
              <span>{{ formatDate(order.paymentTime) }}</span>
            </div>
            <div v-if="order.shipTime" class="info-item">
              <span class="label">发货时间：</span>
              <span>{{ formatDate(order.shipTime) }}</span>
            </div>
          </div>
        </div>

        <!-- 收货地址 -->
        <div class="section">
          <h3 class="section-title">收货信息</h3>
          <div class="address-info">
            <p><strong>{{ order.receiverName }}</strong> {{ order.receiverPhone }}</p>
            <p>{{ order.receiverAddress }}</p>
          </div>
        </div>

        <!-- 商品清单 -->
        <div class="section">
          <h3 class="section-title">商品清单</h3>
          <div class="product-list">
            <div
              v-for="item in order.items"
              :key="item.id"
              class="product-item"
            >
              <img :src="item.productImage || '/placeholder.png'" :alt="item.productName" />
              <div class="product-info">
                <h4>{{ item.productName }}</h4>
                <p v-if="item.specDesc" class="spec-desc">{{ item.specDesc }}</p>
                <p>¥{{ formatPrice(item.unitPrice) }} × {{ item.quantity }}</p>
              </div>
              <span class="product-total">¥{{ formatPrice(item.totalPrice) }}</span>
            </div>
          </div>

          <div class="order-summary">
            <div class="summary-item">
              <span>商品总计：</span>
              <span>¥{{ formatPrice(order.totalAmount) }}</span>
            </div>
            <div class="summary-item">
              <span>运费：</span>
              <span>¥0.00</span>
            </div>
            <div class="summary-item total">
              <span>实付款：</span>
              <span class="total-price">¥{{ formatPrice(order.totalAmount) }}</span>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-section">
          <el-button size="large" @click="$router.push('/orders')">
            返回订单列表
          </el-button>
          <el-button
            v-if="order.status === 'UNPAID'"
            type="primary"
            size="large"
            @click="goToPay"
          >
            去支付
          </el-button>
          <el-button
            v-if="order.status === 'UNPAID' || order.status === 'PAID'"
            type="danger"
            size="large"
            @click="handleCancel"
          >
            取消订单
          </el-button>


          <el-button
            v-if="order.status === 'SHIPPED'"
            type="primary"
            size="large"
            @click="handleConfirm"
          >
            确认收货
          </el-button>
          <el-button
            v-if="order.status === 'SHIPPED' || order.status === 'COMPLETED'"
            type="info"
            size="large"
            plain
            @click="handleCancelShipped"
          >
            取消订单
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrderDetail, cancelOrder, confirmOrder } from '@/api/order'

import { formatPrice, formatDate } from '@/utils/format'
import { ORDER_STATUS_TEXT, ORDER_STATUS_TAG_TYPE } from '@/utils/constants'
import { ElMessage, ElMessageBox } from 'element-plus'
import Loading from '@/components/common/Loading.vue'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const loading = ref(false)

const statusText = ORDER_STATUS_TEXT
const statusTagType = ORDER_STATUS_TAG_TYPE

// 支付方式文本映射
const paymentMethodText = {
  ALIPAY: '支付宝支付',
  WECHAT: '微信支付',
  STRIPE: 'Stripe 支付',
  MOCK: '模拟支付'
}

// 获取订单详情
const fetchOrderDetail = async () => {
  loading.value = true
  try {
    const orderNo = route.params.orderNo
    order.value = await getOrderDetail(orderNo)
  } catch (error) {
    console.error('获取订单详情失败:', error)
    ElMessage.error('订单不存在')
    router.push('/orders')
  } finally {
    loading.value = false
  }
}

// 取消订单
const handleCancel = async () => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await cancelOrder(order.value.orderNo)
    ElMessage.success('订单已取消')
    fetchOrderDetail()
  } catch (error) {
    // 用户取消
  }
}

// 去支付
const goToPay = () => {
  router.push(`/payment/${order.value.orderNo}`)
}

// 确认收货
const handleConfirm = async () => {
  try {
    await ElMessageBox.confirm('确认已收到货物吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })

    await confirmOrder(order.value.orderNo)
    ElMessage.success('确认收货成功')
    fetchOrderDetail()
  } catch (error) {
    // 用户取消
  }
}

// 已发货订单取消提醒
const handleCancelShipped = () => {
  ElMessageBox.alert('商品已发货，无法取消订单。如需退货，请联系客服。', '无法取消', {
    confirmButtonText: '我知道了',
    type: 'warning'
  })
}

onMounted(() => {
  fetchOrderDetail()
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.order-detail-page {
  padding: $spacing-xl 0;
  min-height: calc(100vh - 60px);
}

.order-detail {
  max-width: 900px;

  .page-title {
    font-size: 24px;
    color: $text-primary;
    margin-bottom: $spacing-lg;
  }

  .order-status-card {
    text-align: center;
    padding: $spacing-xl;
    background: $bg-color;
    border-radius: $border-radius-base;
    margin-bottom: $spacing-lg;
  }

  .section {
    background: $bg-color;
    padding: $spacing-lg;
    border-radius: $border-radius-base;
    margin-bottom: $spacing-lg;

    .section-title {
      font-size: 18px;
      color: $text-primary;
      margin: 0 0 $spacing-md 0;
      padding-bottom: $spacing-sm;
      border-bottom: 2px solid $border-light;
    }

    .info-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: $spacing-md;

      .info-item {
        .label {
          color: $text-secondary;
          margin-right: $spacing-sm;
        }
      }

      @include mobile {
        grid-template-columns: 1fr;
      }
    }

    .address-info {
      p {
        color: $text-regular;
        margin: 0 0 $spacing-sm 0;
        line-height: 1.6;

        &:last-child {
          margin-bottom: 0;
        }

        strong {
          color: $text-primary;
          margin-right: $spacing-sm;
        }
      }
    }

    .product-list {
      .product-item {
        display: flex;
        align-items: center;
        gap: $spacing-md;
        padding: $spacing-md 0;
        border-bottom: 1px solid $border-lighter;

        &:last-child {
          border-bottom: none;
        }

        img {
          width: 80px;
          height: 80px;
          object-fit: cover;
          border-radius: $border-radius-base;
        }

        .product-info {
          flex: 1;

          h4 {
            font-size: 16px;
            color: $text-primary;
            margin: 0 0 $spacing-sm 0;
          }

          .spec-desc {
            font-size: 13px;
            color: $text-placeholder;
            margin: 0 0 4px 0;
          }

          p {
            font-size: 14px;
            color: $text-secondary;
            margin: 0;
          }
        }

        .product-total {
          color: $danger-color;
          font-weight: 500;
        }
      }
    }

    .order-summary {
      margin-top: $spacing-lg;
      padding-top: $spacing-lg;
      border-top: 1px solid $border-light;

      .summary-item {
        @include flex-between;
        padding: $spacing-sm 0;
        color: $text-regular;

        &.total {
          font-size: 18px;
          color: $text-primary;
          padding-top: $spacing-md;
          border-top: 1px solid $border-light;

          .total-price {
            font-size: 24px;
            color: $danger-color;
            font-weight: bold;
          }
        }
      }
    }
  }

  .action-section {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-md;

    @include mobile {
      flex-direction: column;

      .el-button {
        width: 100%;
      }
    }
  }
}
</style>
