<template>
  <div class="auth-page">
    <!-- Left brand panel -->
    <div class="auth-brand">
      <router-link to="/" class="brand-logo">SPRING MALL</router-link>
      <div class="brand-content">
        <h2 class="brand-headline">发现好物<br />品质生活</h2>
        <p class="brand-sub">精选全球优质商品，为您的每一次消费增添价值</p>
      </div>
      <p class="brand-footer">© 2025 Spring Mall</p>
    </div>

    <!-- Right form panel -->
    <div class="auth-form-panel">
      <div class="form-wrap">
        <div class="form-header">
          <h1 class="form-title">登录</h1>
          <p class="form-sub">还没有账号？<router-link to="/register" class="form-link">立即注册</router-link></p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="auth-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <label class="field-label">用户名 / 邮箱</label>
            <el-input
              v-model="form.username"
              placeholder="请输入用户名或邮箱"
              size="large"
              clearable
            />
          </el-form-item>

          <el-form-item prop="password">
            <label class="field-label">密码</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              size="large"
              show-password
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
          </el-form-item>

          <el-form-item>
            <div ref="turnstileContainer" style="margin-bottom: 4px;"></div>
          </el-form-item>

          <el-form-item>
            <button
              type="button"
              class="btn-submit"
              :disabled="loading"
              @click="handleLogin"
            >
              <span v-if="!loading">登录</span>
              <span v-else>登录中…</span>
            </button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'
import { ValidationError } from '@/api/request'

const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)
const turnstileContainer = ref(null)
const cfToken = ref('')
let widgetId = null

// 表单数据
const form = reactive({
  username: '',
  password: '',
  remember: false
})

onMounted(() => {
  const initWidget = () => {
    widgetId = window.turnstile.render(turnstileContainer.value, {
      sitekey: '0x4AAAAAAC_iBZYfs4zqBg5x',
      callback: (token) => { cfToken.value = token },
      'expired-callback': () => { cfToken.value = '' },
      'error-callback': () => { cfToken.value = '' }
    })
  }
  if (window.turnstile) {
    initWidget()
  } else {
    window.addEventListener('turnstile:loaded', initWidget, { once: true })
  }
})

onUnmounted(() => {
  if (widgetId !== null) window.turnstile?.remove(widgetId)
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

// 将服务端字段错误应用到表单
const applyServerErrors = (fields) => {
  if (!formRef.value) return
  const unmatchedErrors = []
  Object.entries(fields).forEach(([field, message]) => {
    const formItem = formRef.value.fields?.find((item) => item.prop === field)
    if (formItem) {
      formItem.validateState = 'error'
      formItem.validateMessage = message
    } else {
      unmatchedErrors.push(message)
    }
  })
  if (unmatchedErrors.length > 0) {
    ElMessage.error(unmatchedErrors.join('；'))
  }
}

// 登录处理
const handleLogin = async () => {
  if (!formRef.value) return

  // 重置之前的服务端错误状态
  formRef.value.clearValidate()

  try {
    // 验证表单
    await formRef.value.validate()

    if (!cfToken.value) {
      ElMessage.warning('请完成人机验证')
      return
    }

    loading.value = true

    // 调用登录接口
    await authStore.login({
      account: form.username,
      password: form.password,
      cfToken: cfToken.value
    })

    // 登录成功后会在 store 中自动跳转
  } catch (error) {
    // 登录失败后重置 Widget，Token 一次性不可复用
    window.turnstile?.reset(widgetId)
    cfToken.value = ''

    if (error instanceof ValidationError && error.fields) {
      applyServerErrors(error.fields)
    } else if (error !== false) {
      console.error('登录失败:', error)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

// ─── Shared auth layout ───────────────────────────────
.auth-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;

  @include mobile {
    grid-template-columns: 1fr;
  }
}

// ─── Brand panel (left) ───────────────────────────────
.auth-brand {
  background: $bg-dark;
  color: $text-inverse;
  display: flex;
  flex-direction: column;
  padding: $spacing-xl;

  @include mobile { display: none; }
}

.brand-logo {
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.18em;
  color: $text-inverse;
  text-decoration: none;
}

.brand-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-headline {
  font-size: clamp(32px, 3.5vw, 52px);
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
  line-height: 1.15;
  margin-bottom: $spacing-lg;
}

.brand-sub {
  font-size: $font-size-base;
  color: rgba(255, 255, 255, 0.6);
  line-height: $line-height-base;
  max-width: 320px;
}

.brand-footer {
  font-size: $font-size-xs;
  color: rgba(255, 255, 255, 0.35);
}

// ─── Form panel (right) ───────────────────────────────
.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl $spacing-lg;
  background: $bg-color;
}

.form-wrap {
  width: 100%;
  max-width: 380px;
}

.form-header {
  margin-bottom: $spacing-xl;
}

.form-title {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.form-sub {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.form-link {
  color: $text-primary;
  font-weight: $font-weight-medium;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

// ─── Field labels ─────────────────────────────────────
.field-label {
  display: block;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  margin-bottom: 6px;
}

// ─── Submit button ────────────────────────────────────
.btn-submit {
  width: 100%;
  padding: 15px;
  background: $primary-color;
  color: #fff;
  border: none;
  font-size: $font-size-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  transition: background $transition-base;
  margin-top: $spacing-sm;

  &:hover:not(:disabled) { background: #333; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

// ─── El Plus overrides ────────────────────────────────
:deep(.el-form-item) {
  margin-bottom: $spacing-lg;
  display: flex;
  flex-direction: column;

  .el-form-item__content { flex-direction: column; align-items: stretch; }
  .el-form-item__error { font-size: $font-size-xs; }
}

:deep(.el-input__wrapper) {
  border-radius: 0;
  box-shadow: 0 0 0 1px $border-base inset;

  &:hover { box-shadow: 0 0 0 1px $text-regular inset; }
  &.is-focus { box-shadow: 0 0 0 1px $text-primary inset !important; }
}

:deep(.el-checkbox__label) {
  font-size: $font-size-sm;
  color: $text-regular;
}

:deep(.el-checkbox__inner) {
  border-radius: 0;
}
</style>
