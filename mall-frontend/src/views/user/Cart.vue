<template>
  <div class="cart-page">
    <div class="container">

      <div class="page-header">
        <h1 class="page-title">购物车</h1>
        <span v-if="cartItems.length" class="item-count">{{ cartItems.length }} 件商品</span>
      </div>

      <Loading v-if="loading" />
      <Empty v-else-if="!cartItems.length" type="cart" text="购物车空空如也">
        <template #action>
          <button class="btn-shop" @click="$router.push('/products')">去逛逛</button>
        </template>
      </Empty>

      <div v-else class="cart-layout">

        <!-- Items table -->
        <div class="items-section">
          <!-- Table header -->
          <div class="table-head">
            <div class="col-check">
              <el-checkbox :model-value="isAllChecked" @change="handleCheckAll" />
            </div>
            <div class="col-product">商品</div>
            <div class="col-price">单价</div>
            <div class="col-qty">数量</div>
            <div class="col-subtotal">小计</div>
            <div class="col-action"></div>
          </div>

          <div v-for="item in cartItems" :key="item.id" class="table-row">
            <div class="col-check">
              <el-checkbox
                :model-value="item.checked"
                @change="(val) => handleCheck(item.id, val)"
              />
            </div>

            <div class="col-product item-info" @click="goToDetail(item.productId)">
              <div class="item-image">
                <img :src="item.skuImage || item.productImage || '/placeholder.png'" :alt="item.productName" />
              </div>
              <div class="item-meta">
                <h4 class="item-name">{{ item.productName }}</h4>
                <p v-if="item.specDesc" class="item-sub item-spec">{{ item.specDesc }}</p>
                <p v-else-if="item.productSubtitle" class="item-sub">{{ item.productSubtitle }}</p>
              </div>
            </div>

            <div class="col-price">¥{{ formatPrice(item.productPrice) }}</div>

            <div class="col-qty">
              <div class="qty-control">
                <button
                  class="qty-btn"
                  @click="handleQuantityChange(item.id, item.quantity - 1)"
                  :disabled="item.quantity <= 1"
                >−</button>
                <span class="qty-val">{{ item.quantity }}</span>
                <button
                  class="qty-btn"
                  @click="handleQuantityChange(item.id, item.quantity + 1)"
                  :disabled="item.quantity >= item.productStock"
                >+</button>
              </div>
            </div>

            <div class="col-subtotal">¥{{ formatPrice(item.productPrice * item.quantity) }}</div>

            <div class="col-action">
              <button class="remove-btn" @click="handleRemove(item.id)">×</button>
            </div>
          </div>
        </div>

        <!-- Order summary sidebar -->
        <aside class="summary-panel">
          <h2 class="summary-title">订单摘要</h2>

          <div class="summary-row">
            <span>已选商品</span>
            <span>{{ checkedCount }} 件</span>
          </div>
          <div class="summary-row summary-row--subtotal">
            <span>小计</span>
            <span>¥{{ formatPrice(checkedTotal) }}</span>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-row summary-row--total">
            <span>合计</span>
            <span class="total-price">¥{{ formatPrice(checkedTotal) }}</span>
          </div>

          <button
            class="btn-checkout"
            :disabled="checkedCount === 0"
            @click="handleCheckout"
          >
            结算 ({{ checkedCount }})
          </button>

          <button class="btn-remove-sel" @click="handleRemoveSelected">
            删除选中商品
          </button>
        </aside>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/store/cart'
import { formatPrice } from '@/utils/format'
import { ElMessageBox } from 'element-plus'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'

const router = useRouter()
const cartStore = useCartStore()

const loading = ref(false)

const cartItems = computed(() => cartStore.items)
const isAllChecked = computed(() => cartStore.isAllChecked)
const checkedCount = computed(() => cartStore.checkedCount)
const checkedTotal = computed(() => cartStore.checkedTotal)

const fetchCart = async () => {
  loading.value = true
  try {
    await cartStore.fetchCart()
  } finally {
    loading.value = false
  }
}

const handleCheckAll = (checked) => cartStore.toggleCheckAll(checked)
const handleCheck = (id, checked) => cartStore.toggleCheck(id, checked)
const handleQuantityChange = (id, quantity) => cartStore.updateQuantity(id, quantity)

const handleRemove = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除该商品吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cartStore.removeItem(id)
  } catch {}
}

const handleRemoveSelected = async () => {
  const checkedItems = cartStore.checkedItems
  if (!checkedItems.length) return
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${checkedItems.length} 件商品吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    for (const item of checkedItems) {
      await cartStore.removeItem(item.id)
    }
  } catch {}
}

const goToDetail = (productId) => router.push(`/products/${productId}`)
const handleCheckout = () => {
  if (checkedCount.value === 0) return
  router.push('/checkout')
}

onMounted(fetchCart)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.cart-page {
  background: $bg-color;
  min-height: calc(100vh - 68px);
  padding: $spacing-lg 0 $spacing-xxl;
}

.page-header {
  @include flex-between;
  margin-bottom: $spacing-xl;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $border-light;
}

.page-title {
  font-size: $font-size-xxl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
}

.item-count {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.btn-shop {
  padding: 12px 24px;
  background: $primary-color;
  color: #fff;
  border: none;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
}

// Two-column layout
.cart-layout {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: $spacing-xl;
  align-items: start;

  @include mobile {
    grid-template-columns: 1fr;
  }
}

// Table
.items-section {
  border: 1px solid $border-light;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 40px 1fr 100px 130px 100px 40px;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;

  @include mobile {
    grid-template-columns: 36px 1fr 72px 36px;
    gap: $spacing-sm;
    padding: $spacing-md;
  }
}

.table-head {
  background: $bg-page;
  border-bottom: 1px solid $border-light;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;

  @include mobile {
    .col-price, .col-subtotal { display: none; }
  }
}

.table-row {
  border-bottom: 1px solid $border-lighter;

  &:last-child { border-bottom: none; }

  @include mobile {
    .col-price, .col-subtotal { display: none; }
  }
}

.col-price,
.col-subtotal {
  text-align: center;
  font-size: $font-size-sm;
  color: $text-regular;
}

.col-action { text-align: right; }

// Product info cell
.item-info {
  display: flex;
  gap: $spacing-md;
  cursor: pointer;
  min-width: 0;
}

.item-image {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  background: $bg-gray;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-slow;
  }

  .item-info:hover & img { transform: scale(1.05); }
}

.item-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.item-name {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-primary;
  margin-bottom: 3px;
  @include text-ellipsis;
}

.item-sub {
  font-size: $font-size-xs;
  color: $text-secondary;
  @include text-ellipsis;
}

.item-spec {
  color: $text-placeholder;
  font-size: $font-size-xs;
}

// Quantity control
.qty-control {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid $border-base;
  width: fit-content;
  margin: 0 auto;
}

.qty-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  cursor: pointer;
  background: none;
  border: none;
  color: $text-regular;
  font-family: $font-family;
  transition: background $transition-base;

  &:hover:not(:disabled) { background: $bg-gray; }
  &:disabled { opacity: 0.35; cursor: not-allowed; }
}

.qty-val {
  width: 36px;
  text-align: center;
  font-size: $font-size-sm;
  border-left: 1px solid $border-base;
  border-right: 1px solid $border-base;
  line-height: 32px;
}

.remove-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: $text-placeholder;
  background: none;
  border: none;
  cursor: pointer;
  transition: color $transition-base;
  margin-left: auto;

  &:hover { color: $danger-color; }
}

// Summary panel
.summary-panel {
  border: 1px solid $border-light;
  padding: $spacing-lg;
  position: sticky;
  top: 80px;
}

.summary-title {
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  letter-spacing: -0.02em;
  margin-bottom: $spacing-lg;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $border-lighter;
}

.summary-row {
  @include flex-between;
  font-size: $font-size-sm;
  color: $text-regular;
  margin-bottom: $spacing-sm;

  &--subtotal { color: $text-secondary; }
  &--total {
    font-weight: $font-weight-medium;
    color: $text-primary;
    margin-top: $spacing-sm;
  }
}

.total-price {
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
}

.summary-divider {
  height: 1px;
  background: $border-lighter;
  margin: $spacing-md 0;
}

.btn-checkout {
  width: 100%;
  padding: 14px;
  background: $primary-color;
  color: #fff;
  border: none;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  margin-top: $spacing-lg;
  transition: background $transition-base;

  &:hover:not(:disabled) { background: #333; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}

.btn-remove-sel {
  width: 100%;
  padding: 10px;
  background: transparent;
  color: $text-secondary;
  border: 1px solid $border-light;
  font-size: $font-size-xs;
  cursor: pointer;
  font-family: $font-family;
  margin-top: $spacing-sm;
  transition: border-color $transition-base, color $transition-base;

  &:hover { border-color: $danger-color; color: $danger-color; }
}

:deep(.el-checkbox__inner) {
  border-radius: 0;
}
</style>

