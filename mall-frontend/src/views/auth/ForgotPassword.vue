<template>
  <div class="auth-page">
    <!-- Left brand panel -->
    <div class="auth-brand">
      <router-link to="/" class="brand-logo">SPRING MALL</router-link>
      <div class="brand-content">
        <h2 class="brand-headline">找回密码<br />重拾安全</h2>
        <p class="brand-sub">通过您的邮箱或手机号验证身份，快速重置密码</p>
      </div>
      <p class="brand-footer">© 2025 Spring Mall</p>
    </div>

    <!-- Right form panel -->
    <div class="auth-form-panel">
      <div class="form-wrap">
        <div class="form-header">
          <h1 class="form-title">找回密码</h1>
          <p class="form-sub">
            想起密码了？<router-link to="/login" class="form-link">返回登录</router-link>
          </p>
        </div>

        <!-- 步骤指示器 -->
        <el-steps :active="currentStep" finish-status="success" class="steps-bar">
          <el-step title="发送验证码" />
          <el-step title="验证身份" />
          <el-step title="设置新密码" />
        </el-steps>

        <!-- Step 1：选择联系方式 + 发送验证码 -->
        <div v-if="currentStep === 0" class="step-content">
          <el-form
            ref="step1FormRef"
            :model="form"
            :rules="step1Rules"
            class="auth-form"
          >
            <el-form-item :prop="contactType === 'email' ? 'email' : 'phone'">
              <div class="contact-label-row">
                <label class="field-label">{{ contactType === 'email' ? '邮箱地址' : '手机号码' }}</label>
                <button type="button" class="btn-switch-contact" @click="onContactTypeChange">
                  {{ contactType === 'email' ? '用手机号找回' : '用邮箱找回' }}
                </button>
              </div>
              <el-input
                v-if="contactType === 'email'"
                v-model="form.email"
                placeholder="请输入注册时使用的邮箱"
                size="large"
                clearable
              />
              <!-- 手机号：国家码 + 本地号 -->
              <div v-else class="phone-input-row">
                <el-select
                  v-model="selectedCountryCode"
                  class="country-select"
                  size="large"
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
                  class="phone-input"
                  @input="onLocalPhoneInput"
                />
              </div>
            </el-form-item>

            <el-form-item>
              <button
                type="button"
                class="btn-submit"
                :disabled="sendingOtp"
                @click="handleSendOtp"
              >
                <span v-if="sendingOtp">发送中…</span>
                <span v-else>发送验证码</span>
              </button>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 2：验证身份 -->
        <div v-if="currentStep === 1" class="step-content">
          <p class="step-desc">
            验证码已发送至
            <strong>{{ maskedTarget }}</strong>
            ，请在5分钟内完成验证
          </p>

          <el-form
            ref="step2FormRef"
            :model="form"
            :rules="step2Rules"
            class="auth-form"
          >
            <el-form-item prop="otpCode">
              <label class="field-label">验证码</label>
              <el-input
                v-model="form.otpCode"
                placeholder="请输入6位验证码"
                size="large"
                maxlength="6"
              />
            </el-form-item>

            <el-form-item>
              <button
                type="button"
                class="btn-submit"
                :disabled="verifyingOtp"
                @click="handleVerifyStep"
              >
                <span v-if="verifyingOtp">验证中…</span>
                <span v-else>验证并继续</span>
              </button>
            </el-form-item>

            <el-form-item>
              <div class="resend-row">
                <span class="resend-text">没有收到验证码？</span>
                <button
                  type="button"
                  class="btn-resend"
                  :disabled="otpCountdown > 0 || sendingOtp"
                  @click="handleResendOtp"
                >
                  <span v-if="otpCountdown > 0">{{ otpCountdown }}s 后重新发送</span>
                  <span v-else-if="sendingOtp">发送中…</span>
                  <span v-else>重新发送</span>
                </button>
              </div>
            </el-form-item>
          </el-form>
        </div>

        <!-- Step 3：设置新密码 -->
        <div v-if="currentStep === 2" class="step-content">
          <el-form
            ref="step3FormRef"
            :model="form"
            :rules="step3Rules"
            class="auth-form"
          >
            <el-form-item prop="newPassword">
              <label class="field-label">新密码</label>
              <el-input
                v-model="form.newPassword"
                type="password"
                placeholder="至少6位"
                size="large"
                show-password
                clearable
              />
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <label class="field-label">确认新密码</label>
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                size="large"
                show-password
                clearable
                @keyup.enter="handleResetPassword"
              />
            </el-form-item>

            <el-form-item>
              <button
                type="button"
                class="btn-submit"
                :disabled="resetting"
                @click="handleResetPassword"
              >
                <span v-if="resetting">重置中…</span>
                <span v-else>确认重置</span>
              </button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'
import { PHONE_COUNTRIES, DEFAULT_COUNTRY, toE164, maskPhone } from '@/utils/phoneCountries'

const router = useRouter()
const authStore = useAuthStore()

// 步骤状态
const currentStep = ref(0)

// 联系方式类型（默认邮箱）
const contactType = ref('email')

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
  step1FormRef.value?.clearValidate('phone')
}

const onLocalPhoneInput = () => {
  form.phone = localPhone.value ? toE164(selectedCountry.value.dialCode, localPhone.value) : ''
}

// OTP 发送状态
const sendingOtp = ref(false)
const otpCountdown = ref(0)
const verifyingOtp = ref(false)
const resetting = ref(false)
let countdownTimer = null

// Step 2 验证成功后由后端颁发的一次性凭证（10 分钟有效）
const verificationToken = ref('')

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// 表单数据
const form = reactive({
  email: '',
  phone: '',
  otpCode: '',
  newPassword: '',
  confirmPassword: ''
})

// 当前联系方式目标值
const contactTarget = computed(() =>
  contactType.value === 'email' ? form.email : form.phone
)

// 脱敏显示目标地址
const maskedTarget = computed(() => {
  const t = contactTarget.value
  if (!t) return ''
  if (contactType.value === 'email') {
    const [local, domain] = t.split('@')
    if (!local || !domain) return t
    const masked = local.length > 2
      ? local[0] + '***' + local[local.length - 1]
      : local[0] + '***'
    return `${masked}@${domain}`
  }
  // E.164 手机号脱敏
  return maskPhone(t)
})

// 切换联系方式时重置
const onContactTypeChange = () => {
  contactType.value = contactType.value === 'email' ? 'sms' : 'email'
  form.email = ''
  form.phone = ''
  localPhone.value = ''
  form.otpCode = ''
  otpCountdown.value = 0
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  step1FormRef.value?.clearValidate()
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

// 表单引用
const step1FormRef = ref(null)
const step2FormRef = ref(null)
const step3FormRef = ref(null)

// Step 1 验证规则
const step1Rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^\+[1-9]\d{6,14}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

// Step 2 验证规则
const step2Rules = {
  otpCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为6位数字', trigger: 'blur' }
  ]
}

// Step 3 验证规则
const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const step3Rules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// Step 1：发送验证码
const handleSendOtp = async () => {
  if (!step1FormRef.value) return

  const contactProp = contactType.value === 'email' ? 'email' : 'phone'
  try {
    await step1FormRef.value.validateField(contactProp)
  } catch {
    return
  }

  sendingOtp.value = true
  try {
    await authStore.forgotPassword({
      target: contactTarget.value,
      type: contactType.value
    })
    startCountdown()
    currentStep.value = 1
    ElMessage.success('验证码已发送')
  } catch (error) {
    console.error('发送验证码失败:', error)
  } finally {
    sendingOtp.value = false
  }
}

// Step 2：调用 verify-otp 获取 verificationToken，成功后进入 Step 3
const handleVerifyStep = async () => {
  if (!step2FormRef.value) return

  try {
    await step2FormRef.value.validate()
  } catch {
    return
  }

  verifyingOtp.value = true
  try {
    const token = await authStore.verifyOtpForReset({
      target: contactTarget.value,
      code: form.otpCode,
      purpose: 'reset_password'
    })
    verificationToken.value = token
    currentStep.value = 2
  } catch (error) {
    console.error('验证码校验失败:', error)
    // 拦截器已弹出 ElMessage.error，此处无需重复提示
  } finally {
    verifyingOtp.value = false
  }
}

// 重新发送验证码
const handleResendOtp = async () => {
  sendingOtp.value = true
  try {
    await authStore.forgotPassword({
      target: contactTarget.value,
      type: contactType.value
    })
    startCountdown()
    form.otpCode = ''
    ElMessage.success('验证码已重新发送')
  } catch (error) {
    console.error('重新发送失败:', error)
  } finally {
    sendingOtp.value = false
  }
}

// Step 3：重置密码
const handleResetPassword = async () => {
  if (!step3FormRef.value) return

  try {
    await step3FormRef.value.validate()
  } catch {
    return
  }

  resetting.value = true
  try {
    await authStore.resetPassword({
      verificationToken: verificationToken.value,
      newPassword: form.newPassword
    })
    ElMessage.success('密码重置成功，请重新登录')
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (error) {
    console.error('重置密码失败:', error)
    // Token 可能已过期（超过 10 分钟）或已被使用，引导用户从 Step 1 重新开始
    ElMessage.error('重置链接已失效，请重新获取验证码')
    verificationToken.value = ''
    form.otpCode = ''
    currentStep.value = 0
  } finally {
    resetting.value = false
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

.steps-bar {
  margin-bottom: $spacing-xl;
}

.step-content {
  margin-top: $spacing-lg;
}

.step-desc {
  font-size: $font-size-sm;
  color: $text-regular;
  margin-bottom: $spacing-lg;
  line-height: $line-height-base;

  strong {
    color: $text-primary;
    font-weight: $font-weight-medium;
  }
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

  &:hover { color: $text-primary; }
}

.phone-input-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  width: 100%;
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

.resend-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  justify-content: center;
}

.resend-text {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.btn-resend {
  background: none;
  border: none;
  padding: 0;
  font-size: $font-size-sm;
  font-family: $font-family;
  color: $text-primary;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color $transition-base;

  &:disabled {
    color: $text-placeholder;
    cursor: not-allowed;
    text-decoration: none;
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

:deep(.el-steps) {
  --el-color-primary: #{$primary-color};
}

:deep(.el-step__title) {
  font-size: $font-size-xs;
  font-weight: $font-weight-medium;
}

:deep(.el-radio__label) {
  font-size: $font-size-sm;
  color: $text-regular;
}
</style>
