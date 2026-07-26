import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/storage'
import { useAuthStore } from '@/store/auth'

const routes = [
  // 用户端路由
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: '/products',
        name: 'ProductList',
        component: () => import('@/views/user/ProductList.vue'),
        meta: { title: '商品列表' }
      },
      {
        path: '/products/:id',
        name: 'ProductDetail',
        component: () => import('@/views/user/ProductDetail.vue'),
        meta: { title: '商品详情' }
      },
      {
        path: '/cart',
        name: 'Cart',
        component: () => import('@/views/user/Cart.vue'),
        meta: { title: '购物车', requiresAuth: true }
      },
      {
        path: '/checkout',
        name: 'Checkout',
        component: () => import('@/views/user/Checkout.vue'),
        meta: { title: '结算', requiresAuth: true }
      },
      {
        path: '/orders',
        name: 'OrderList',
        component: () => import('@/views/user/OrderList.vue'),
        meta: { title: '我的订单', requiresAuth: true }
      },
      {
        path: '/orders/:orderNo',
        name: 'OrderDetail',
        component: () => import('@/views/user/OrderDetail.vue'),
        meta: { title: '订单详情', requiresAuth: true }
      },
      {
        path: '/payment/:orderNo',
        name: 'Payment',
        component: () => import('@/views/user/Payment.vue'),
        meta: { title: '订单支付', requiresAuth: true }
      },
      {
        path: '/payment/stripe/result',
        name: 'StripePaymentResult',
        component: () => import('@/views/user/StripePaymentResult.vue'),
        meta: { requiresAuth: true, title: '支付结果' }
      },
      {
        path: '/payment/result',
        name: 'PaymentResult',
        component: () => import('@/views/user/PaymentResult.vue'),
        meta: { title: '支付结果', requiresAuth: true }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { title: '个人中心', requiresAuth: true }
      },
      {
        path: '/address',
        name: 'Address',
        component: () => import('@/views/user/Address.vue'),
        meta: { title: '地址管理', requiresAuth: true }
      },
      {
        path: '/seckills',
        name: 'SeckillList',
        component: () => import('@/views/user/SeckillList.vue'),
        meta: { title: '限时秒杀', requiresAuth: true }
      },
      {
        path: '/seckills/:id',
        name: 'SeckillDetail',
        component: () => import('@/views/user/SeckillDetail.vue'),
        meta: { title: '秒杀详情', requiresAuth: true }
      }
    ]
  },

  // 认证路由
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { title: '找回密码' }
  },

  // 管理端路由
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '管理后台' }
      },
      {
        path: 'products',
        name: 'ProductManage',
        component: () => import('@/views/admin/ProductManage.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'categories',
        name: 'CategoryManage',
        component: () => import('@/views/admin/CategoryManage.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: 'orders',
        name: 'OrderManage',
        component: () => import('@/views/admin/OrderManage.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'orders/:orderNo',
        name: 'AdminOrderDetail',
        component: () => import('@/views/admin/AdminOrderDetail.vue'),
        meta: { title: '订单详情' }
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'seckill',
        name: 'SeckillManage',
        component: () => import('@/views/admin/SeckillManage.vue'),
        meta: { title: '秒杀管理' }
      }
    ]
  },

  // 404 页面
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = getToken()

  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - Spring Mall` : 'Spring Mall'

  // 检查是否需要登录
  if (to.meta.requiresAuth && !token) {
    ElMessage.warning('请先登录')
    next('/login')
    return
  }

  // 检查是否需要管理员权限
  if (to.meta.requiresAdmin) {
    const authStore = useAuthStore()
    if (!authStore.isAdmin) {
      ElMessage.error('无权访问管理后台')
      next('/')
      return
    }
  }

  // 已登录用户访问登录/注册/找回密码页面，跳转到首页
  if ((to.path === '/login' || to.path === '/register' || to.path === '/forgot-password') && token) {
    next('/')
    return
  }

  next()
})

export default router
