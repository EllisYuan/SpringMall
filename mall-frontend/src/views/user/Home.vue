<template>
  <div class="home">

    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <p class="hero-eyebrow">2026 新品上市</p>
        <h1 class="hero-title">精选好物<br>品质生活</h1>
        <p class="hero-desc">汇聚各类优质商品，每一件都经过严格品控</p>
        <div class="hero-actions">
          <router-link to="/products" class="btn-primary">立即选购</router-link>
          <router-link to="/products" class="btn-ghost">浏览分类</router-link>
        </div>
      </div>
      <div class="hero-visual">
        <div class="hero-image-grid">
          <div class="grid-block grid-block--large" style="background: #f0ece6;"></div>
          <div class="grid-block" style="background: #e8e4dc;"></div>
          <div class="grid-block" style="background: #dedad2;"></div>
        </div>
      </div>
    </section>

    <!-- Categories Strip -->
    <section class="categories-strip">
      <div class="container">
        <div class="strip-inner">
          <span class="strip-label">分类浏览</span>
          <div class="category-pills">
            <button
              v-for="category in categories"
              :key="category.id"
              class="pill"
              @click="goToCategory(category.id)"
            >
              {{ category.name }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Featured Products -->
    <section class="products-section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">热门商品</h2>
          <router-link to="/products" class="section-more">查看全部 →</router-link>
        </div>

        <Loading v-if="loading" />
        <Empty v-else-if="!products.length" type="product" text="暂无商品" />
        <div v-else class="product-grid">
          <div
            v-for="(product, index) in products"
            :key="product.id"
            class="product-card"
            :class="{ 'product-card--featured': index === 0 }"
            @click="goToDetail(product.id)"
          >
            <div class="product-card__image">
              <img
                :src="product.mainImage || '/placeholder.png'"
                :alt="product.name"
                loading="lazy"
              />
              <div class="product-card__overlay">
                <button class="btn-overlay" @click.stop="addToCart(product)">
                  加入购物车
                </button>
              </div>
            </div>
            <div class="product-card__body">
              <h3 class="product-card__name">{{ product.name }}</h3>
              <p class="product-card__price">¥{{ formatPrice(product.price) }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Value Props -->
    <section class="value-props">
      <div class="container">
        <div class="props-grid">
          <div class="prop-item">
            <span class="prop-icon">✦</span>
            <h4>品质保证</h4>
            <p>每件商品都由站长亲自导来</p>
          </div>
          <div class="prop-item">
            <span class="prop-icon">✦</span>
            <h4>全场包邮</h4>
            <p>因为全场商品没有一个需要邮寄</p>
          </div>
          <div class="prop-item">
            <span class="prop-icon">✦</span>
            <h4>7 天不换</h4>
            <p>多发一秒都不给退</p>
          </div>
          <div class="prop-item">
            <span class="prop-icon">✦</span>
            <h4>安全支付</h4>
            <p>接口调用, 神仙都盗不了</p>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAllProducts } from '@/api/product'
import { useAppStore } from '@/store/app'
import { useCartStore } from '@/store/cart'
import { formatPrice } from '@/utils/format'
import Loading from '@/components/common/Loading.vue'
import Empty from '@/components/common/Empty.vue'

const router = useRouter()
const appStore = useAppStore()
const cartStore = useCartStore()

const categories = ref([])
const products = ref([])
const loading = ref(false)

const fetchCategories = async () => {
  await appStore.fetchCategories()
  categories.value = appStore.categories.slice(0, 10)
}

// 获取热门商品列表
const fetchProducts = async () => {
  loading.value = true
  try {
    const data = await getAllProducts({ sortBy: 'sales', sortDir: 'desc', size: 9 })
    products.value = data.list || data || []
  } catch (error) {
    console.error('获取热门商品失败:', error)
  } finally {
    loading.value = false
  }
}

const goToCategory = (categoryId) => router.push({ path: '/products', query: { categoryId } })
const goToDetail = (productId) => router.push(`/products/${productId}`)

const addToCart = async (product) => {
  try {
    await cartStore.addItem(product.id, 1)
  } catch (error) {
    console.error('加入购物车失败:', error)
  }
}

onMounted(() => {
  fetchCategories()
  fetchProducts()
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

// ─── Hero ────────────────────────────────────────────
.hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 600px;
  overflow: hidden;

  @include mobile {
    grid-template-columns: 1fr;
    min-height: auto;
  }
}

.hero-content {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: $spacing-xxl $spacing-xl $spacing-xxl max($spacing-xxl, calc((100vw - 1280px) / 2 + $spacing-xl + 8px));
  background: $bg-color;

  @include tablet {
    padding: $spacing-xl;
  }
  @include mobile {
    padding: $spacing-xl $spacing-md;
    order: 2;
  }
}

.hero-eyebrow {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

.hero-title {
  font-size: clamp(36px, 5vw, 64px);
  font-weight: $font-weight-bold;
  line-height: $line-height-tight;
  color: $text-primary;
  letter-spacing: -0.03em;
  margin-bottom: $spacing-lg;
}

.hero-desc {
  font-size: $font-size-md;
  color: $text-secondary;
  line-height: $line-height-loose;
  max-width: 380px;
  margin-bottom: $spacing-xl;
}

.hero-actions {
  display: flex;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  background: $primary-color;
  color: #fff;
  padding: 14px 32px;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  text-decoration: none;
  transition: background $transition-base;

  &:hover { background: #333; }
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  border: 1px solid $border-base;
  color: $text-primary;
  padding: 14px 32px;
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  text-decoration: none;
  transition: border-color $transition-base, background $transition-base;

  &:hover {
    border-color: $text-primary;
    background: $bg-gray;
  }
}

.hero-visual {
  overflow: hidden;
  @include mobile { display: none; }
}

.hero-image-grid {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: 2fr 1fr;
  grid-template-rows: 1fr 1fr;

  .grid-block { width: 100%; height: 100%; }
  .grid-block--large { grid-row: 1 / 3; }
}

// ─── Categories Strip ────────────────────────────────
.categories-strip {
  border-top: 1px solid $border-light;
  border-bottom: 1px solid $border-light;
  padding: $spacing-md 0;
  background: $bg-color;
  overflow-x: auto;
}

.strip-inner {
  display: flex;
  align-items: center;
  gap: $spacing-lg;

  @include mobile { gap: $spacing-md; }
}

.strip-label {
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: $text-secondary;
  white-space: nowrap;
  flex-shrink: 0;
}

.category-pills {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: nowrap;
  overflow-x: auto;
  padding-bottom: 2px;

  &::-webkit-scrollbar { display: none; }
}

.pill {
  flex-shrink: 0;
  padding: 6px 16px;
  border: 1px solid $border-light;
  background: none;
  font-size: $font-size-sm;
  color: $text-regular;
  cursor: pointer;
  transition: all $transition-base;
  font-family: $font-family;
  letter-spacing: 0.02em;

  &:hover {
    border-color: $text-primary;
    color: $text-primary;
    background: $bg-gray;
  }
}

// ─── Products Section ────────────────────────────────
.products-section {
  padding: $spacing-xxl 0;
  background: $bg-color;
}

.section-header {
  @include flex-between;
  margin-bottom: $spacing-xl;
}

.section-title {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.02em;
  color: $text-primary;
}

.section-more {
  font-size: $font-size-sm;
  color: $text-secondary;
  text-decoration: none;
  letter-spacing: 0.02em;
  transition: color $transition-base;

  &:hover { color: $text-primary; }
}

// ─── Product Grid ────────────────────────────────────
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: $border-light;
  border: 1px solid $border-light;

  @include tablet { grid-template-columns: repeat(3, 1fr); }
  @include mobile { grid-template-columns: repeat(2, 1fr); }
}

.product-card {
  background: $bg-color;
  cursor: pointer;
  position: relative;
  overflow: hidden;

  &__image {
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
  }

  &__overlay {
    position: absolute;
    inset: 0;
    background: rgba(0,0,0,0.2);
    display: flex;
    align-items: flex-end;
    padding: $spacing-md;
    opacity: 0;
    transition: opacity $transition-base;
  }

  .btn-overlay {
    width: 100%;
    padding: 12px;
    background: #fff;
    border: none;
    font-size: $font-size-sm;
    font-weight: $font-weight-medium;
    letter-spacing: 0.04em;
    cursor: pointer;
    font-family: $font-family;
    transition: background $transition-base;

    &:hover { background: $bg-gray; }
  }

  &:hover {
    .product-card__image img { transform: scale(1.04); }
    .product-card__overlay { opacity: 1; }
  }

  &__body {
    padding: $spacing-md $spacing-md $spacing-lg;
  }

  &__name {
    font-size: $font-size-base;
    font-weight: $font-weight-medium;
    color: $text-primary;
    margin-bottom: 4px;
    @include text-ellipsis;
    letter-spacing: -0.01em;
  }

  &__price {
    font-size: $font-size-base;
    color: $text-regular;
    font-weight: $font-weight-regular;
  }

  // 第一个商品 — 占两列两行
  &--featured {
    grid-column: span 2;
    grid-row: span 2;

    .product-card__image {
      aspect-ratio: auto;
      height: 100%;
      min-height: 500px;

      @include mobile { min-height: 300px; }
    }

    .product-card__name { font-size: $font-size-md; }
    .product-card__price { font-size: $font-size-md; }

    @include mobile {
      grid-column: span 2;
      grid-row: span 1;

      .product-card__image { min-height: 240px; aspect-ratio: 2/1.4; }
    }
  }
}

// ─── Value Props ─────────────────────────────────────
.value-props {
  padding: $spacing-xxl 0;
  border-top: 1px solid $border-light;
  background: $bg-color;
}

.props-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-xl;

  @include tablet { grid-template-columns: repeat(2, 1fr); }
  @include mobile { grid-template-columns: 1fr 1fr; gap: $spacing-md; }
}

.prop-item {
  .prop-icon {
    display: block;
    font-size: 18px;
    margin-bottom: $spacing-md;
    color: $text-primary;
  }

  h4 {
    font-size: $font-size-base;
    font-weight: $font-weight-bold;
    color: $text-primary;
    margin-bottom: $spacing-sm;
    letter-spacing: -0.01em;
  }

  p {
    font-size: $font-size-sm;
    color: $text-secondary;
    line-height: $line-height-loose;
  }
}
</style>
