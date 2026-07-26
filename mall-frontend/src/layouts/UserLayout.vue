<template>
  <div class="user-layout">
    <!-- 顶部通告栏 -->
    <div class="announcement-bar">
      <span>欢迎来到Spring Mall, 内测状态 更多功能 敬请期待</span>
    </div>

    <!-- 主导航 -->
    <header class="header" :class="{ 'header--scrolled': isScrolled }">
      <div class="container">
        <div class="header-inner">

          <!-- 左：导航链接 -->
          <nav class="nav-left">
            <router-link to="/" :class="['nav-link', { 'nav-link--active': isHomeActive }]">首页</router-link>
            <router-link to="/products" :class="['nav-link', { 'nav-link--active': isAllProductsActive }]">全部商品</router-link>
            <router-link to="/products?categoryId=new" :class="['nav-link', { 'nav-link--active': isNewProductsActive }]">新品</router-link>
            <router-link to="/seckills" :class="['nav-link nav-link--seckill', { 'nav-link--active': isSeckillActive }]">限时秒杀</router-link>
          </nav>

          <!-- 中：Logo -->
          <router-link to="/" class="logo">
            <span class="logo-text">SPRING MALL</span>
          </router-link>

          <!-- 右：操作区 -->
          <div class="nav-right">
            <template v-if="isLoggedIn">
              <router-link to="/orders" class="icon-btn" title="我的订单">
                <el-icon :size="20"><List /></el-icon>
              </router-link>
              <router-link to="/cart" class="icon-btn cart-btn" title="购物车">
                <el-icon :size="20"><ShoppingCart /></el-icon>
                <span v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
              </router-link>
              <el-dropdown @command="handleCommand" placement="bottom-end">
                <button class="icon-btn user-btn" title="账户">
                  <el-icon :size="20"><User /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <div class="dropdown-header">{{ username }}</div>
                    <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                    <el-dropdown-item command="address">地址管理</el-dropdown-item>
                    <el-dropdown-item v-if="isAdmin" command="admin">管理后台</el-dropdown-item>
                    <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <router-link to="/login" class="nav-link">登录</router-link>
              <router-link to="/register" class="nav-link nav-link--cta">注册</router-link>
            </template>
          </div>

        </div>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <div class="container">
        <div class="footer-inner">
          <div class="footer-brand">
            <span class="footer-logo">SPRING MALL</span>
            <p class="footer-tagline">精选好物，品质生活</p>
          </div>
          <div class="footer-links">
            <div class="footer-col">
              <h4>购物</h4>
              <router-link to="/products">全部商品</router-link>
              <router-link to="/seckills">限时秒杀</router-link>
              <router-link to="/orders">我的订单</router-link>
              <router-link to="/cart">购物车</router-link>
            </div>
            <div class="footer-col">
              <h4>账户</h4>
              <router-link to="/profile">个人中心</router-link>
              <router-link to="/address">地址管理</router-link>
            </div>
          </div>
        </div>
        <div class="footer-bottom">
          <p>&copy; 2026 Spring Mall. All rights reserved.</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useCartStore } from '@/store/cart'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const cartStore = useCartStore()

const isLoggedIn = computed(() => authStore.isLoggedIn)
const username = computed(() => authStore.username)
const isAdmin = computed(() => authStore.isAdmin)
const cartCount = computed(() => cartStore.cartCount)
const isHomeActive = computed(() => route.path === '/')
const isNewProductsActive = computed(() => route.path === '/products' && route.query.categoryId === 'new')
const isAllProductsActive = computed(() => route.path === '/products' && !isNewProductsActive.value)
const isSeckillActive = computed(() => route.path.startsWith('/seckills'))

// 滚动状态
const isScrolled = ref(false)
const handleScroll = () => { isScrolled.value = window.scrollY > 40 }
onMounted(() => window.addEventListener('scroll', handleScroll))
onUnmounted(() => window.removeEventListener('scroll', handleScroll))

authStore.initAuth()
if (isLoggedIn.value && !isAdmin.value) {
  cartStore.fetchCart()
}

const handleCommand = (command) => {
  switch (command) {
    case 'profile': router.push('/profile'); break
    case 'address': router.push('/address'); break
    case 'admin':   router.push('/admin');   break
    case 'logout':  authStore.logout();      break
  }
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

// ─── Layout Shell ───────────────────────────────────
.user-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: $bg-color;
}

// ─── Announcement Bar ───────────────────────────────
.announcement-bar {
  background: $bg-dark;
  color: rgba(255,255,255,0.85);
  text-align: center;
  padding: 8px $spacing-md;
  font-size: $font-size-xs;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

// ─── Header ─────────────────────────────────────────
.header {
  background: $bg-color;
  border-bottom: 1px solid $border-light;
  position: sticky;
  top: 0;
  z-index: 200;
  transition: box-shadow $transition-base;

  &--scrolled {
    box-shadow: 0 2px 16px rgba(0,0,0,0.06);
  }
}

.header-inner {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 68px;
}

// ─── Logo ────────────────────────────────────────────
.logo {
  justify-self: center;
  text-decoration: none;

  .logo-text {
    font-size: 17px;
    font-weight: $font-weight-bold;
    letter-spacing: 0.18em;
    color: $text-primary;
    text-transform: uppercase;
    user-select: none;
  }
}

// ─── Nav Left ────────────────────────────────────────
.nav-left {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
}

// ─── Nav Right ───────────────────────────────────────
.nav-right {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
}

// ─── Nav Links ───────────────────────────────────────
.nav-link {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-regular;
  letter-spacing: 0.02em;
  padding: 4px 8px;
  text-decoration: none;
  transition: color $transition-base;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    bottom: -1px;
    left: 8px;
    right: 8px;
    height: 1px;
    background: $text-primary;
    transform: scaleX(0);
    transition: transform $transition-base;
  }

  &:hover,
  &.nav-link--active {
    color: $text-primary;
    &::after { transform: scaleX(1); }
  }

  &--cta {
    background: $primary-color;
    color: $text-inverse !important;
    padding: 6px 14px;
    font-size: $font-size-xs;
    letter-spacing: 0.06em;
    text-transform: uppercase;

    &::after { display: none; }
    &:hover { background: #333; }
  }

  &--seckill {
    color: $danger-color !important;
    font-weight: $font-weight-bold;

    &::after { background: $danger-color; }
    &:hover { color: #8e2318 !important; }
  }
}

// ─── Icon Buttons ────────────────────────────────────
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: $text-primary;
  background: none;
  border: none;
  cursor: pointer;
  transition: opacity $transition-base;
  text-decoration: none;
  position: relative;

  &:hover { opacity: 0.6; }
}

.cart-badge {
  position: absolute;
  top: 4px;
  right: 2px;
  background: $primary-color;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 3px;
}

// Dropdown header username
.dropdown-header {
  padding: 10px 16px 6px;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  border-bottom: 1px solid $border-lighter;
  margin-bottom: 4px;
}

// ─── Main ────────────────────────────────────────────
.main-content {
  flex: 1;
  background: $bg-color;
}

// ─── Footer ──────────────────────────────────────────
.footer {
  background: $bg-dark;
  color: rgba(255,255,255,0.6);

  .footer-inner {
    display: grid;
    grid-template-columns: 1fr 2fr;
    gap: $spacing-xxl;
    padding: $spacing-xxl 0 $spacing-xl;
    border-bottom: 1px solid rgba(255,255,255,0.1);

    @include mobile {
      grid-template-columns: 1fr;
      gap: $spacing-xl;
      padding: $spacing-xl 0;
    }
  }

  .footer-brand {
    .footer-logo {
      display: block;
      font-size: $font-size-md;
      font-weight: $font-weight-bold;
      letter-spacing: 0.18em;
      color: #fff;
      margin-bottom: $spacing-sm;
    }
    .footer-tagline {
      font-size: $font-size-sm;
      color: rgba(255,255,255,0.4);
    }
  }

  .footer-links {
    display: flex;
    gap: $spacing-xxl;

    @include mobile { gap: $spacing-xl; }
  }

  .footer-col {
    h4 {
      font-size: $font-size-xs;
      font-weight: $font-weight-bold;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: #fff;
      margin-bottom: $spacing-md;
    }
    a {
      display: block;
      font-size: $font-size-sm;
      color: rgba(255,255,255,0.5);
      margin-bottom: $spacing-sm;
      text-decoration: none;
      transition: color $transition-base;

      &:hover { color: #fff; }
    }
  }

  .footer-bottom {
    padding: $spacing-lg 0;
    font-size: $font-size-xs;
    color: rgba(255,255,255,0.3);
    letter-spacing: 0.04em;
  }
}

// ─── Mobile ──────────────────────────────────────────
@include mobile {
  .header-inner {
    grid-template-columns: 1fr auto 1fr;
    height: 56px;
  }

  .nav-left { display: none; }

  .nav-right {
    .nav-link:not(.nav-link--cta) { display: none; }
  }
}
</style>
