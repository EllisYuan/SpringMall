<template>
  <div class="product-detail-page">
    <div class="container">

      <Loading v-if="loading" />

      <template v-else-if="product">
        <!-- Breadcrumb -->
        <nav class="breadcrumb">
          <router-link to="/" class="crumb">首页</router-link>
          <span class="crumb-sep">/</span>
          <router-link to="/products" class="crumb">全部商品</router-link>
          <span class="crumb-sep">/</span>
          <span class="crumb crumb--current">{{ product.name }}</span>
        </nav>

        <!-- Main Layout -->
        <div class="detail-grid">

          <!-- Left: Image -->
          <div class="image-panel">
            <div class="image-frame">
              <img
                :src="displayImage || '/placeholder.png'"
                :alt="product.name"
              />
            </div>
          </div>

          <!-- Right: Info -->
          <div class="info-panel">
            <h1 class="product-name">{{ product.name }}</h1>

            <!-- 价格：有 SKU 且未选完规格时显示区间 -->
            <div class="product-price">
              <template v-if="product.hasSku">
                <template v-if="selectedSku">
                  ¥{{ formatPrice(selectedSku.price) }}
                </template>
                <template v-else>
                  ¥{{ formatPrice(product.minPrice) }}
                  <span v-if="product.minPrice !== product.maxPrice"> - ¥{{ formatPrice(product.maxPrice) }}</span>
                </template>
              </template>
              <template v-else>
                ¥{{ formatPrice(product.price) }}
              </template>
            </div>

            <p v-if="product.description" class="product-description">
              {{ product.description }}
            </p>

            <!-- SKU 规格选择器 -->
            <template v-if="product.hasSku && product.specs && product.specs.length">
              <div class="spec-section">
                <div
                  v-for="spec in product.specs"
                  :key="spec.id"
                  class="spec-row"
                >
                  <span class="spec-label">{{ spec.name }}</span>
                  <div class="spec-values">
                    <button
                      v-for="val in spec.values"
                      :key="val.id"
                      class="spec-btn"
                      :class="{ 'spec-btn--active': selectedSpecValues[spec.id] === val.id }"
                      @click="selectSpecValue(spec.id, val.id)"
                    >
                      {{ val.value }}
                    </button>
                  </div>
                </div>
              </div>
            </template>

            <div class="divider"></div>

            <div class="stock-row">
              <span class="meta-label">库存</span>
              <template v-if="product.hasSku">
                <template v-if="selectedSku">
                  <span v-if="selectedSku.stock > 0" class="stock-count">{{ selectedSku.stock }} 件</span>
                  <span v-else class="out-of-stock">缺货</span>
                </template>
                <span v-else class="stock-count stock-count--muted">请先选择规格</span>
              </template>
              <template v-else>
                <span v-if="product.stock > 0" class="stock-count">{{ product.stock }} 件</span>
                <span v-else class="out-of-stock">缺货</span>
              </template>
            </div>

            <div class="quantity-row">
              <span class="meta-label">数量</span>
              <div class="quantity-control">
                <button
                  class="qty-btn"
                  @click="quantity > 1 && quantity--"
                  :disabled="quantity <= 1 || effectiveStock === 0"
                >−</button>
                <span class="qty-value">{{ quantity }}</span>
                <button
                  class="qty-btn"
                  @click="quantity < effectiveStock && quantity++"
                  :disabled="quantity >= effectiveStock || effectiveStock === 0"
                >+</button>
              </div>
            </div>

            <div class="action-group">
              <button
                class="btn-primary-full"
                :disabled="addToCartDisabled"
                @click="handleAddToCart"
              >
                {{ addToCartLabel }}
              </button>
              <button
                class="btn-buy-now"
                :disabled="addToCartDisabled"
                @click="handleBuyNow"
              >
                立即购买
              </button>
            </div>
          </div>
        </div>

        <!-- Product Detail Section -->
        <div class="detail-section">
          <h2 class="section-heading">商品详情</h2>
          <div class="detail-body">{{ product.detail || '暂无详细信息' }}</div>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProductDetail } from '@/api/product'
import { useCartStore } from '@/store/cart'
import { useAuthStore } from '@/store/auth'
import { formatPrice } from '@/utils/format'
import { ElMessage } from 'element-plus'
import Loading from '@/components/common/Loading.vue'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const authStore = useAuthStore()

const product = ref(null)
const loading = ref(false)
const quantity = ref(1)

// key: spec.id, value: specValue.id
const selectedSpecValues = ref({})

const preselectDefaultSku = () => {
  const p = product.value
  if (!p || !p.hasSku || !p.skuList || !p.specs) return

  const defaultSku = p.skuList.find(sku => sku.isDefault === true)
  if (!defaultSku || !defaultSku.specValueIds) return

  // 将默认 SKU 的 specValueIds 反查到对应的 spec 维度
  const specValueIdSet = new Set(defaultSku.specValueIds)
  const preselected = {}

  for (const spec of p.specs) {
    for (const val of spec.values) {
      if (specValueIdSet.has(val.id)) {
        preselected[spec.id] = val.id
        break
      }
    }
  }

  selectedSpecValues.value = preselected
}

const fetchProductDetail = async () => {
  loading.value = true
  try {
    product.value = await getProductDetail(route.params.id)
    // 预选默认 SKU 对应的规格值
    preselectDefaultSku()
  } catch (error) {
    console.error('获取商品详情失败:', error)
    ElMessage.error('商品不存在')
    router.push('/products')
  } finally {
    loading.value = false
  }
}

// 选择规格值
const selectSpecValue = (specId, valueId) => {
  if (selectedSpecValues.value[specId] === valueId) {
    // 取消选中
    const updated = { ...selectedSpecValues.value }
    delete updated[specId]
    selectedSpecValues.value = updated
  } else {
    selectedSpecValues.value = { ...selectedSpecValues.value, [specId]: valueId }
  }
  // 规格变更后重置数量为 1
  quantity.value = 1
}

// 是否所有规格维度都已选
const allSpecsSelected = computed(() => {
  if (!product.value || !product.value.hasSku || !product.value.specs) return true
  return product.value.specs.every(spec => selectedSpecValues.value[spec.id] !== undefined)
})

// 根据选中的规格值匹配 SKU
const selectedSku = computed(() => {
  if (!allSpecsSelected.value || !product.value || !product.value.skuList) return null
  const selectedIds = Object.values(selectedSpecValues.value).sort((a, b) => a - b)
  return product.value.skuList.find(sku => {
    const skuIds = [...(sku.specValueIds || [])].sort((a, b) => a - b)
    return skuIds.length === selectedIds.length && skuIds.every((id, i) => id === selectedIds[i])
  }) || null
})

// 当前有效库存
const effectiveStock = computed(() => {
  if (product.value && product.value.hasSku) {
    return selectedSku.value ? selectedSku.value.stock : 0
  }
  return product.value ? product.value.stock : 0
})

// 显示的主图：SKU 图片优先
const displayImage = computed(() => {
  if (selectedSku.value && selectedSku.value.image) return selectedSku.value.image
  return product.value ? product.value.mainImage : null
})

// 加入购物车按钮是否禁用
const addToCartDisabled = computed(() => {
  if (!product.value) return true
  if (product.value.hasSku) {
    // 有 SKU：必须选完所有规格，且该 SKU 有库存
    if (!allSpecsSelected.value) return true
    if (!selectedSku.value) return true
    return selectedSku.value.stock === 0
  }
  return product.value.stock === 0
})

// 加入购物车按钮文字
const addToCartLabel = computed(() => {
  if (!product.value) return '加入购物车'
  if (product.value.hasSku) {
    if (!allSpecsSelected.value) return '请选择规格'
    if (!selectedSku.value || selectedSku.value.stock === 0) return '暂时缺货'
    return '加入购物车'
  }
  return product.value.stock === 0 ? '暂时缺货' : '加入购物车'
})

const handleAddToCart = async () => {
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  const skuId = selectedSku.value ? selectedSku.value.id : undefined
  try {
    await cartStore.addItem(product.value.id, quantity.value, skuId)
    ElMessage.success('已加入购物车')
  } catch (error) {
    console.error('加入购物车失败:', error)
  }
}

const handleBuyNow = async () => {
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  const skuId = selectedSku.value ? selectedSku.value.id : undefined
  try {
    await cartStore.addItem(product.value.id, quantity.value, skuId)
    router.push('/cart')
  } catch (error) {
    console.error('加入购物车失败:', error)
  }
}

onMounted(fetchProductDetail)
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.product-detail-page {
  background: $bg-color;
  min-height: calc(100vh - 68px);
  padding: $spacing-lg 0 $spacing-xxl;
}

// Breadcrumb
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: $spacing-xl;
  font-size: $font-size-sm;
}

.crumb {
  color: $text-secondary;
  text-decoration: none;
  transition: color $transition-base;

  &:hover { color: $text-primary; }
  &--current { color: $text-primary; }
}

.crumb-sep { color: $border-base; }

// Two-column Detail Grid
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xxl;
  margin-bottom: $spacing-xxl;

  @include mobile {
    grid-template-columns: 1fr;
    gap: $spacing-xl;
  }
}

// Image Panel
.image-frame {
  background: $bg-gray;
  aspect-ratio: 1 / 1;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

// Info Panel
.info-panel {
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: clamp(22px, 3vw, 32px);
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
  color: $text-primary;
  margin-bottom: $spacing-md;
  line-height: 1.2;
}

.product-price {
  font-size: 28px;
  font-weight: $font-weight-medium;
  color: $text-primary;
  margin-bottom: $spacing-lg;
}

.product-description {
  font-size: $font-size-base;
  color: $text-secondary;
  line-height: 1.7;
  margin-bottom: $spacing-lg;
}

// SKU Spec Selector
.spec-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.spec-row {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
}

.spec-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: $text-secondary;
  min-width: 60px;
  padding-top: 8px;
  flex-shrink: 0;
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.spec-btn {
  padding: 6px 14px;
  font-size: $font-size-sm;
  font-family: $font-family;
  background: $bg-color;
  color: $text-regular;
  border: 1px solid $border-base;
  cursor: pointer;
  transition: border-color $transition-base, color $transition-base, background $transition-base;
  line-height: 1.4;

  &:hover {
    border-color: $primary-color;
    color: $text-primary;
  }

  &--active {
    background: $primary-color;
    color: $text-inverse;
    border-color: $primary-color;

    &:hover {
      color: $text-inverse;
      border-color: $primary-color;
    }
  }
}

.divider {
  height: 1px;
  background: $border-lighter;
  margin-bottom: $spacing-lg;
}

.meta-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: $text-secondary;
  min-width: 60px;
}

// Stock Row
.stock-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.stock-count {
  font-size: $font-size-sm;
  color: $text-regular;

  &--muted {
    color: $text-placeholder;
    font-style: italic;
  }
}
.out-of-stock { font-size: $font-size-sm; color: $danger-color; }

// Quantity Control
.quantity-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-xl;
}

.quantity-control {
  display: flex;
  align-items: center;
  border: 1px solid $border-base;
}

.qty-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  cursor: pointer;
  background: none;
  border: none;
  font-family: $font-family;
  color: $text-regular;
  transition: background $transition-base, color $transition-base;

  &:hover:not(:disabled) { background: $bg-gray; color: $text-primary; }
  &:disabled { opacity: 0.35; cursor: not-allowed; }
}

.qty-value {
  width: 48px;
  text-align: center;
  font-size: $font-size-base;
  border-left: 1px solid $border-base;
  border-right: 1px solid $border-base;
  line-height: 40px;
}

// Action Buttons
.action-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-top: auto;
  padding-top: $spacing-lg;
}

.btn-primary-full {
  width: 100%;
  padding: 16px;
  background: $primary-color;
  color: #fff;
  border: 1px solid $primary-color;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  transition: background $transition-base, color $transition-base;

  &:hover:not(:disabled) { background: #333; border-color: #333; }
  &:disabled {
    background: $border-base;
    border-color: $border-base;
    cursor: not-allowed;
  }
}

.btn-buy-now {
  width: 100%;
  padding: 16px;
  background: transparent;
  color: $text-primary;
  border: 1px solid $text-primary;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  transition: background $transition-base, color $transition-base;

  &:hover:not(:disabled) { background: $text-primary; color: #fff; }
  &:disabled { opacity: 0.35; cursor: not-allowed; }
}

// Detail Section
.detail-section {
  border-top: 1px solid $border-light;
  padding-top: $spacing-xl;
}

.section-heading {
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  letter-spacing: -0.02em;
  margin-bottom: $spacing-lg;
}

.detail-body {
  font-size: $font-size-base;
  color: $text-regular;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
