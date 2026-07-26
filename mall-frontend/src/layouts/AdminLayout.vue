<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <h2 v-if="!sidebarCollapsed">Spring Mall</h2>
        <h2 v-else>SM</h2>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/admin">
          <el-icon><DataLine /></el-icon>
          <template #title>数据概览</template>
        </el-menu-item>

        <el-menu-item index="/admin/products">
          <el-icon><Goods /></el-icon>
          <template #title>商品管理</template>
        </el-menu-item>

        <el-menu-item index="/admin/categories">
          <el-icon><Grid /></el-icon>
          <template #title>分类管理</template>
        </el-menu-item>

        <el-menu-item index="/admin/orders">
          <el-icon><ShoppingCart /></el-icon>
          <template #title>订单管理</template>
        </el-menu-item>

        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>

        <el-menu-item index="/admin/seckill">
          <el-icon><AlarmClock /></el-icon>
          <template #title>秒杀管理</template>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- 主内容区 -->
    <div class="main-container">
      <!-- 顶部导航 -->
      <header class="header">
        <div class="header-left">
          <el-icon class="toggle-btn" @click="toggleSidebar">
            <Expand v-if="sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="home">返回首页</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useAppStore } from '@/store/app'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const appStore = useAppStore()

// 计算属性
const username = computed(() => authStore.username)
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)

// 切换侧边栏
const toggleSidebar = () => {
  appStore.toggleSidebar()
}

// 下拉菜单操作
const handleCommand = (command) => {
  switch (command) {
    case 'home':
      router.push('/')
      break
    case 'logout':
      authStore.logout()
      break
  }
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 200px;
  background: #304156;
  transition: width 0.3s;
  overflow-x: hidden;

  &.collapsed {
    width: 64px;
  }

  .sidebar-header {
    height: 60px;
    @include flex-center;
    color: #fff;
    font-size: 20px;
    font-weight: bold;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);

    h2 {
      margin: 0;
    }
  }

  :deep(.el-menu) {
    border: none;
  }

  :deep(.el-menu-item) {
    &:hover {
      background-color: rgba(0, 0, 0, 0.2) !important;
    }
  }
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  background: $bg-color;
  border-bottom: 1px solid $border-light;
  @include flex-between;
  padding: 0 $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

  .header-left {
    .toggle-btn {
      font-size: 20px;
      cursor: pointer;
      color: $text-regular;
      transition: color 0.3s;

      &:hover {
        color: $primary-color;
      }
    }
  }

  .header-right {
    .user-info {
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 4px;
      color: $text-primary;

      &:hover {
        color: $primary-color;
      }
    }
  }
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg;
  background: $bg-page;
}
</style>
