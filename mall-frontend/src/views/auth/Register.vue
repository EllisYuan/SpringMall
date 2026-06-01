<template>
  <div class="auth-page">
    <!-- Left brand panel -->
    <div class="auth-brand">
      <router-link to="/" class="brand-logo">SPRING MALL</router-link>
      <div class="brand-content">
        <h2 class="brand-headline">加入我们<br />开启购物之旅</h2>
        <p class="brand-sub">注册即享专属优惠，每次购物都是一次发现</p>
      </div>
      <p class="brand-footer">© 2025 Spring Mall</p>
    </div>

    <!-- Right form panel -->
    <div class="auth-form-panel">
      <div class="form-wrap">
        <div class="form-header">
          <h1 class="form-title">创建账号</h1>
          <p class="form-sub">已有账号？<router-link to="/login" class="form-link">立即登录</router-link></p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="auth-form"
          @submit.prevent="handleRegister"
        >
          <!-- 用户名 -->
          <el-form-item prop="username">
            <label class="field-label">用户名</label>
            <el-input
              v-model="form.username"
              placeholder="3-20位字母、数字或下划线"
              size="large"
              clearable
            />
          </el-form-item>

          <!-- 邮箱 / 手机号输入 + 发送验证码 -->
          <el-form-item :prop="contactType === 'email' ? 'email' : 'phone'">
            <div class="contact-label-row">
              <label class="field-label">{{ contactType === 'email' ? '邮箱' : '手机号' }}</label>
              <button type="button" class="btn-switch-contact" :disabled="otpVerified" @click="onContactTypeChange">
                {{ contactType === 'email' ? '用手机号注册' : '用邮箱注册' }}
              </button>
            </div>
            <div class="contact-input-row">
              <el-input
                v-if="contactType === 'email'"
                v-model="form.email"
                placeholder="请输入邮箱"
                size="large"
                clearable
                :disabled="otpVerified"
              />
              <!-- 手机号：国家码选择器 + 本地号输入 -->
              <template v-else>
                <el-select
                  v-model="selectedCountryCode"
                  class="country-select"
                  size="large"
                  :disabled="otpVerified"
                  popper-class="country-select-popper"
                  @change="onCountryChange"
                >
                  <el-option
                    v-for="c in phoneCountries"
                    :key="c.code"
                    :value="c.code"
                    :label="`${c.flag} ${c.dialCode}`"
                  >
                    <span class="country-option">
                      <span class="country-flag">{{ c.flag }}</span>
                      <span class="country-name">{{ c.name }}</span>
                      <span class="country-dial">{{ c.dialCode }}</span>
                    </span>
                  </el-option>
                </el-select>
                <el-input
                  v-model="localPhone"
                  :placeholder="selectedCountry.placeholder"
                  size="large"
                  clearable
                  :disabled="otpVerified"
                  class="phone-input"
                  @input="onLocalPhoneInput"
                />
              </template>
              <span v-if="otpVerified" class="verified-icon">
                <el-icon color="#2d7738"><CircleCheck /></el-icon>
              </span>
              <button
                v-if="!otpVerified"
                type="button"
                class="btn-send-otp"
                :disabled="otpCountdown > 0 || sendingOtp"
                @click="handleSendOtp"
              >
                <span v-if="otpCountdown > 0">{{ otpCountdown }}s</span>
                <span v-else-if="sendingOtp">发送中…</span>
                <span v-else>发送验证码</span>
              </button>
            </div>
          </el-form-item>

          <!-- 验证码输入（发送后显示，验证成功后隐藏） -->
          <template v-if="otpSent && !otpVerified">
            <p class="otp-hint">
              验证码已发送至 <strong>{{ maskedTarget }}</strong>，请在5分钟内完成验证
            </p>
            <el-form-item prop="otpCode">
              <label class="field-label">验证码</label>
              <div class="otp-row">
                <el-input
                  v-model="form.otpCode"
                  placeholder="请输入6位数字验证码"
                  size="large"
                  maxlength="6"
                  @input="onOtpInput"
                >
                  <template #suffix>
                    <span class="otp-counter">{{ form.otpCode.length }}/6</span>
                  </template>
                </el-input>
                <button
                  type="button"
                  class="btn-verify-otp"
                  :disabled="verifyingOtp || form.otpCode.length < 6"
                  @click="handleVerifyOtp"
                >
                  <span v-if="verifyingOtp">验证中…</span>
                  <span v-else>验 证</span>
                </button>
              </div>
            </el-form-item>
          </template>

          <!-- 密码 -->
          <el-form-item prop="password">
            <label class="field-label">密码</label>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="至少6位"
              size="large"
              show-password
              clearable
            />
          </el-form-item>

          <!-- 确认密码 -->
          <el-form-item prop="confirmPassword">
            <label class="field-label">确认密码</label>
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              size="large"
              show-password
              clearable
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <!-- 用户协议 -->
          <el-form-item prop="agreement">
            <el-checkbox v-model="form.agreement">
              我已阅读并同意
              <span class="form-link">《用户协议》</span>
              和
              <span class="form-link">《隐私政策》</span>
            </el-checkbox>
          </el-form-item>

          <!-- Cloudflare Turnstile -->
          <el-form-item>
            <div ref="turnstileContainer" style="margin: 4px 0;"></div>
          </el-form-item>

          <!-- 提交按钮 -->
          <el-form-item>
            <button
              type="button"
              class="btn-submit"
              :disabled="loading"
              @click="handleRegister"
            >
              <span v-if="!loading">创建账号</span>
              <span v-else>注册中…</span>
            </button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { CircleCheck } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'
import { ValidationError } from '@/api/request'
import { PHONE_COUNTRIES, DEFAULT_COUNTRY, toE164, maskPhone } from '@/utils/phoneCountries'

const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)

// Turnstile 相关
const turnstileContainer = ref(null)
const cfToken = ref('')
let widgetId = null

onMounted(() => {
  const init = () => {
    widgetId = window.turnstile?.render(turnstileContainer.value, {
      sitekey: '0x4AAAAAAC_iBZYfs4zqBg5x',
      callback: (token) => { cfToken.value = token },
      'expired-callback': () => { cfToken.value = '' },
      'error-callback': () => { cfToken.value = '' },
    })
  }
  if (window.turnstile) {
    init()
  } else {
    window.addEventListener('turnstile:loaded', init, { once: true })
  }
})

onUnmounted(() => {
  if (widgetId !== null) window.turnstile?.remove(widgetId)
  if (countdownTimer) clearInterval(countdownTimer)
})

// OTP 相关状态
const contactType = ref('email')
const otpSent = ref(false)
const otpVerified = ref(false)
const sendingOtp = ref(false)
const verifyingOtp = ref(false)
const otpCountdown = ref(0)
const verificationToken = ref('')
let countdownTimer = null

// 手机号：国家码 + 本地号
const phoneCountries = PHONE_COUNTRIES
const selectedCountryCode = ref(DEFAULT_COUNTRY.code)
const selectedCountry = computed(() =>
  phoneCountries.find(c => c.code === selectedCountryCode.value) || DEFAULT_COUNTRY
)
const localPhone = ref('')

const onCountryChange = () => {
  localPhone.value = ''
  form.phone = ''
  otpSent.value = false
  otpVerified.value = false
  verificationToken.value = ''
  form.otpCode = ''
  formRef.value?.clearValidate('phone')
}

const onLocalPhoneInput = () => {
  const newE164 = localPhone.value ? toE164(selectedCountry.value.dialCode, localPhone.value) : ''
  // 号码变更时重置 OTP 状态，防止旧验证码被复用
  if (newE164 !== form.phone && otpSent.value) {
    otpSent.value = false
    otpVerified.value = false
    verificationToken.value = ''
    form.otpCode = ''
  }
  form.phone = newE164
}

// 切换联系方式类型并重置 OTP 状态
const onContactTypeChange = () => {
  contactType.value = contactType.value === 'email' ? 'sms' : 'email'
  otpSent.value = false
  otpVerified.value = false
  verificationToken.value = ''
  form.otpCode = ''
  form.email = ''
  form.phone = ''
  localPhone.value = ''
  otpCountdown.value = 0
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  formRef.value?.clearValidate()
}

// 启动倒计时
const startCountdown = () => {
  otpCountdown.value = 60
  countdownTimer = setInterval(() => {
    otpCountdown.value--
    if (otpCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

// 表单数据
const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  agreement: false,
  otpCode: ''
})

// 当前联系方式目标值
const contactTarget = computed(() =>
  contactType.value === 'email' ? form.email : form.phone
)

// 脱敏显示目标地址（用于 OTP 提示语）
const maskedTarget = computed(() => {
  const t = contactTarget.value
  if (!t) return ''
  if (contactType.value === 'email') {
    const [local, domain] = t.split('@')
    if (!local || !domain) return t
    const masked = local.length > 2 ? local[0] + '***' + local[local.length - 1] : local[0] + '***'
    return `${masked}@${domain}`
  }
  return maskPhone(t)
})

// OTP 输入处理：过滤非数字 + 输满6位自动验证
const onOtpInput = () => {
  form.otpCode = form.otpCode.replace(/\D/g, '')
  if (form.otpCode.length === 6 && !verifyingOtp.value) {
    handleVerifyOtp()
  }
}

// 自定义验证：确认密码
const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 自定义验证：用户协议
const validateAgreement = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请阅读并同意用户协议和隐私政策'))
  } else {
    callback()
  }
}

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^\+[1-9]\d{6,14}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  otpCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ],
  agreement: [
    { validator: validateAgreement, trigger: 'change' }
  ]
}

// 发送验证码
const handleSendOtp = async () => {
  if (!formRef.value) return

  // 先验证联系方式字段
  const contactProp = contactType.value === 'email' ? 'email' : 'phone'
  try {
    await formRef.value.validateField(contactProp)
  } catch {
    return
  }

  const target = contactTarget.value
  if (!target) return

  sendingOtp.value = true
  try {
    await authStore.sendOtp({
      target,
      type: contactType.value,
      purpose: 'register'
    })
    otpSent.value = true
    startCountdown()
    ElMessage.success('验证码已发送，请查收')
  } catch (error) {
    console.error('发送验证码失败:', error)
  } finally {
    sendingOtp.value = false
  }
}

// 验证 OTP
const handleVerifyOtp = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validateField('otpCode')
  } catch {
    return
  }

  verifyingOtp.value = true
  try {
    const token = await authStore.verifyOtpForRegister({
      target: contactTarget.value,
      code: form.otpCode,
      purpose: 'register'
    })
    verificationToken.value = token
    otpVerified.value = true
    ElMessage.success('验证成功')
  } catch (error) {
    console.error('验证失败:', error)
  } finally {
    verifyingOtp.value = false
  }
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

// 注册处理
const handleRegister = async () => {
  if (!formRef.value) return

  if (!otpVerified.value) {
    ElMessage.warning('请先完成联系方式验证')
    return
  }

  // 重置之前的服务端错误状态
  formRef.value.clearValidate()

  try {
    // 验证表单（跳过 otpCode，因为已验证通过）
    await formRef.value.validate()

    if (!cfToken.value) {
      ElMessage.warning('请完成人机验证')
      return
    }

    loading.value = true

    const registerPayload = {
      username: form.username,
      password: form.password,
      cfToken: cfToken.value,
      verificationToken: verificationToken.value
    }

    if (contactType.value === 'email') {
      registerPayload.email = form.email
    } else {
      registerPayload.phone = form.phone
    }

    await authStore.register(registerPayload)

    // 注册成功后会显示成功提示并跳转登录页
  } catch (error) {
    // 注册失败后重置 Turnstile Widget
    window.turnstile?.reset(widgetId)
    cfToken.value = ''

    if (error instanceof ValidationError && error.fields) {
      applyServerErrors(error.fields)
    } else if (error !== false) {
      console.error('注册失败:', error)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.auth-page {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;

  @include mobile { grid-template-columns: 1fr; }
}

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

.auth-form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl $spacing-lg;
  background: $bg-color;
  overflow-y: auto;
}

.form-wrap {
  width: 100%;
  max-width: 420px;
  padding: $spacing-lg 0;
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

.field-label {
  display: block;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: $text-secondary;
  margin-bottom: 6px;
}

.contact-label-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;

  .field-label { margin-bottom: 0; }
}

.btn-switch-contact {
  background: none;
  border: none;
  padding: 0;
  font-size: $font-size-xs;
  color: $text-secondary;
  cursor: pointer;
  font-family: $font-family;
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color $transition-base;

  &:hover:not(:disabled) { color: $text-primary; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}

.contact-input-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  width: 100%;

  .el-input {
    flex: 1;
  }
}

.country-select {
  width: 110px;
  flex-shrink: 0;
}

.phone-input {
  flex: 1;
}

.country-option {
  display: flex;
  align-items: center;
  gap: 8px;

  .country-flag { font-size: 18px; flex-shrink: 0; }
  .country-name { flex: 1; font-size: $font-size-sm; color: $text-primary; }
  .country-dial { font-size: $font-size-xs; color: $text-secondary; flex-shrink: 0; }
}

.verified-icon {
  display: flex;
  align-items: center;
  font-size: 20px;
  flex-shrink: 0;
}

.btn-send-otp,
.btn-verify-otp {
  flex-shrink: 0;
  padding: 0 $spacing-md;
  height: 40px;
  background: $primary-color;
  color: #fff;
  border: none;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.06em;
  cursor: pointer;
  font-family: $font-family;
  white-space: nowrap;
  transition: background $transition-base;

  &:hover:not(:disabled) { background: #333; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.otp-hint {
  font-size: $font-size-xs;
  color: $text-secondary;
  margin: -#{$spacing-sm} 0 $spacing-sm;
  line-height: $line-height-base;

  strong {
    color: $text-primary;
    font-weight: $font-weight-medium;
  }
}

.otp-counter {
  font-size: $font-size-xs;
  color: $text-placeholder;
  font-variant-numeric: tabular-nums;
}

.otp-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  width: 100%;

  .el-input {
    flex: 1;
  }
}

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

:deep(.el-radio__label) {
  font-size: $font-size-sm;
  color: $text-regular;
}
</style>
