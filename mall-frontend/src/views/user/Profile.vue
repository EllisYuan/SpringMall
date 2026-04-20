<template>
  <div class="profile-page">
    <div class="container">

      <!-- Profile Hero -->
      <header class="profile-hero">
        <div
          class="hero-avatar"
          role="button"
          tabindex="0"
          @click="handleAvatarClick"
          @keydown.enter.prevent="handleAvatarClick"
          @keydown.space.prevent="handleAvatarClick"
        >
          <img
            v-if="showAvatarImage"
            :src="userForm.avatar"
            alt="用户头像"
            class="hero-avatar-image"
            @error="handleAvatarImageError"
          />
          <span v-else>{{ userInitial }}</span>
          <span class="avatar-edit-tip">更换头像</span>
        </div>
        <div class="hero-info">
          <div class="hero-name-row">
            <h1 class="hero-name">{{ userForm.username || '未命名用户' }}</h1>
            <span class="role-badge" :class="userForm.role === 'ADMIN' ? 'role-admin' : 'role-user'">
              {{ userForm.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </span>
          </div>
          <p class="hero-email">{{ userForm.email || '尚未设置邮箱' }}</p>
          <p class="hero-meta">注册于 {{ formatDate(userForm.createdAt) || '—' }}</p>
        </div>
      </header>

      <div class="profile-layout">

        <!-- Basic Info Section -->
        <section class="profile-section">
          <header class="section-head">
            <h2 class="section-title">基本信息</h2>
            <p class="section-hint">更新你的个人资料，修改用户名后将需重新登录</p>
          </header>

          <el-form
            ref="profileFormRef"
            :model="userForm"
            :rules="profileRules"
            label-position="top"
            class="custom-form"
          >
            <el-form-item label="用户名" prop="username">
              <el-input v-model="userForm.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="userForm.email" placeholder="example@domain.com" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="userForm.phone" placeholder="请输入手机号" />
            </el-form-item>
            <el-form-item class="form-actions">
              <button
                class="btn-save"
                :disabled="profileSubmitting"
                @click="handleUpdateProfile"
              >
                <span>{{ profileSubmitting ? '保存中' : '保存修改' }}</span>
                <span v-if="!profileSubmitting" class="btn-arrow">→</span>
              </button>
            </el-form-item>
          </el-form>
        </section>

        <!-- Change Password Section -->
        <section class="profile-section">
          <header class="section-head">
            <h2 class="section-title">修改密码</h2>
            <p class="section-hint">出于安全考虑，密码修改成功后会自动退出登录</p>
          </header>

          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
            class="custom-form"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入原密码"
                show-password
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码（至少 6 位）"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                show-password
              />
            </el-form-item>
            <el-form-item class="form-actions">
              <button
                class="btn-save"
                :disabled="submitting"
                @click="handleUpdatePassword"
              >
                <span>{{ submitting ? '提交中' : '修改密码' }}</span>
                <span v-if="!submitting" class="btn-arrow">→</span>
              </button>
            </el-form-item>
          </el-form>
        </section>

      </div>

      <el-dialog
        v-model="avatarDialogVisible"
        title="更换头像"
        width="460px"
        destroy-on-close
      >
        <div class="avatar-dialog-body">
          <div class="avatar-preview" aria-label="头像预览">
            <img
              v-if="avatarUrlDraft && !avatarPreviewError"
              :src="avatarUrlDraft"
              alt="头像预览"
              class="avatar-preview-image"
              @error="avatarPreviewError = true"
            />
            <span v-else class="avatar-preview-fallback">{{ userInitial }}</span>
          </div>

          <el-input
            v-model.trim="avatarUrlDraft"
            placeholder="请输入头像 URL（http/https）"
            @input="avatarPreviewError = false"
          />
          <p class="avatar-dialog-hint">*仅支持以http/https开头的图床链接</p>
        </div>

        <template #footer>
          <button
            type="button"
            class="btn-ghost"
            :disabled="avatarSubmitting"
            @click="avatarDialogVisible = false"
          >取消</button>
          <button
            type="button"
            class="btn-save"
            :disabled="avatarSubmitting"
            @click="handleAvatarSave"
          >
            {{ avatarSubmitting ? '保存中' : '保存头像' }}
          </button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import { useUserStore } from '@/store/user'
import { updatePassword } from '@/api/user'
import { formatDate } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { ValidationError } from '@/api/request'

const authStore = useAuthStore()
const userStore = useUserStore()
const profileFormRef = ref(null)
const passwordFormRef = ref(null)
const submitting = ref(false)
const profileSubmitting = ref(false)
const avatarDialogVisible = ref(false)
const avatarSubmitting = ref(false)
const avatarUrlDraft = ref('')
const avatarLoadError = ref(false)
const avatarPreviewError = ref(false)

const userForm = reactive({
  username: '',
  email: '',
  phone: '',
  avatar: '',
  role: '',
  createdAt: ''
})

const userInitial = computed(() => {
  const name = userForm.username?.trim()
  return name ? name.charAt(0).toUpperCase() : '?'
})

const showAvatarImage = computed(() => {
  return Boolean(userForm.avatar) && !avatarLoadError.value
})

const isValidAvatarUrl = (url) => {
  if (!url) return true
  return /^https?:\/\/\S+$/i.test(url)
}

const profileRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 30, message: '用户名长度为2-30个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { max: 20, message: '手机号不能超过20个字符', trigger: 'blur' }
  ]
}

const handleUpdateProfile = async () => {
  if (!profileFormRef.value) return
  const originalUsername = authStore.user?.username
  try {
    await profileFormRef.value.validate()
    profileSubmitting.value = true
    await userStore.updateUserInfo({
      username: userForm.username,
      email: userForm.email,
      phone: userForm.phone,
      avatar: userForm.avatar
    })
    if (userForm.username !== originalUsername) {
      ElMessage.success('用户名已修改，请重新登录')
      setTimeout(() => { authStore.logout() }, 1500)
    }
  } catch (error) {
    if (error !== false) {
      console.error('更新个人信息失败:', error)
    }
  } finally {
    profileSubmitting.value = false
  }
}

const handleAvatarClick = () => {
  avatarUrlDraft.value = userForm.avatar || ''
  avatarPreviewError.value = false
  avatarDialogVisible.value = true
}

const handleAvatarImageError = () => {
  avatarLoadError.value = true
}

const handleAvatarSave = async () => {
  const nextAvatar = avatarUrlDraft.value.trim()
  if (!isValidAvatarUrl(nextAvatar)) {
    ElMessage.warning('请上传正确文件路径（仅支持 http/https）')
    return
  }

  try {
    avatarSubmitting.value = true
    const updatedUser = await userStore.updateUserInfo({
      username: userForm.username,
      email: userForm.email,
      phone: userForm.phone,
      avatar: nextAvatar
    })

    userForm.avatar = updatedUser?.avatar || nextAvatar
    avatarLoadError.value = false
    avatarDialogVisible.value = false
  } catch (error) {
    if (error !== false) {
      console.error('更新头像失败:', error)
    }
  } finally {
    avatarSubmitting.value = false
  }
}

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入新密码'))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const applyServerErrors = (fields) => {
  if (!passwordFormRef.value) return
  const unmatchedErrors = []
  Object.entries(fields).forEach(([field, message]) => {
    const formItem = passwordFormRef.value.fields?.find((item) => item.prop === field)
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

const handleUpdatePassword = async () => {
  if (!passwordFormRef.value) return
  passwordFormRef.value.clearValidate()
  try {
    await passwordFormRef.value.validate()
    submitting.value = true
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value.resetFields()
    setTimeout(() => { authStore.logout() }, 1500)
  } catch (error) {
    if (error instanceof ValidationError && error.fields) {
      applyServerErrors(error.fields)
    } else if (error !== false) {
      console.error('修改密码失败:', error)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  const user = authStore.user
  if (user) {
    userForm.username = user.username || ''
    userForm.email = user.email || ''
    userForm.phone = user.phone || ''
    userForm.avatar = user.avatar || ''
    userForm.role = user.role || ''
    userForm.createdAt = user.createdAt || ''
    avatarLoadError.value = false
  }
})
</script>

<style scoped lang="scss">
@import '@/assets/styles/variables.scss';

.profile-page {
  background: $bg-page;
  min-height: calc(100vh - 68px);
  padding: $spacing-xl 0 $spacing-xxl;
}

// --- Hero ---
.profile-hero {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
  padding: $spacing-xl;
  margin-bottom: $spacing-xl;
  background: $bg-color;
  border: 1px solid $border-light;

  @include mobile {
    flex-direction: column;
    align-items: flex-start;
    gap: $spacing-lg;
    padding: $spacing-lg;
  }
}

.hero-avatar {
  @include flex-center;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  width: 88px;
  height: 88px;
  background: $bg-dark;
  color: $text-inverse;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.02em;
  text-transform: uppercase;
  cursor: pointer;
  transition: transform $transition-base;

  &:hover {
    transform: translateY(-1px);

    .avatar-edit-tip {
      opacity: 1;
    }
  }

  &:focus-visible {
    outline: 2px solid $primary-color;
    outline-offset: 2px;
  }
}

.hero-avatar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-edit-tip {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 4px 6px;
  font-size: 10px;
  text-align: center;
  letter-spacing: 0.06em;
  color: $text-inverse;
  background: rgba(0, 0, 0, 0.55);
  opacity: 0;
  transition: opacity $transition-base;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.hero-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  flex-wrap: wrap;
  margin-bottom: $spacing-sm;
}

.hero-name {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  letter-spacing: -0.03em;
  color: $text-primary;
  margin: 0;
  line-height: $line-height-tight;
}

.hero-email {
  font-size: $font-size-sm;
  color: $text-regular;
  margin: 0 0 $spacing-xs;
}

.hero-meta {
  font-size: $font-size-xs;
  color: $text-secondary;
  letter-spacing: 0.04em;
  margin: 0;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 $spacing-sm;
  font-size: 10px;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  border-radius: $border-radius-sm;

  &.role-admin {
    background: #111;
    color: #fff;
  }

  &.role-user {
    background: $bg-gray;
    color: $text-regular;
    border: 1px solid $border-light;
  }
}

// --- Layout ---
.profile-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xl;
  align-items: start;

  @include mobile {
    grid-template-columns: 1fr;
    gap: $spacing-lg;
  }
}

.profile-section {
  background: $bg-color;
  border: 1px solid $border-light;
  padding: $spacing-xl;
  transition: border-color $transition-base;

  &:hover {
    border-color: $border-base;
  }

  @include mobile {
    padding: $spacing-lg;
  }
}

.section-head {
  margin-bottom: $spacing-lg;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $border-lighter;
}

.section-title {
  font-size: $font-size-md;
  font-weight: $font-weight-bold;
  letter-spacing: -0.02em;
  color: $text-primary;
  margin: 0 0 $spacing-xs;
}

.section-hint {
  font-size: $font-size-xs;
  color: $text-secondary;
  margin: 0;
  line-height: $line-height-base;
}

// --- Form ---
.custom-form {
  :deep(.el-form-item) {
    margin-bottom: $spacing-md;
  }

  :deep(.el-form-item__label) {
    font-size: 10px;
    font-weight: $font-weight-bold;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: $text-secondary;
    padding-bottom: 6px;
    line-height: 1;
  }

  :deep(.el-input__wrapper) {
    border-radius: 0;
    box-shadow: 0 0 0 1px $border-base;
    background: $bg-color;
    transition: box-shadow $transition-base;
    padding: 2px 12px;

    &:hover {
      box-shadow: 0 0 0 1px $text-regular;
    }

    &.is-focus {
      box-shadow: 0 0 0 1px $primary-color !important;
    }
  }

  :deep(.el-input__inner) {
    height: 38px;
    font-size: $font-size-sm;
    color: $text-primary;

    &::placeholder {
      color: $text-placeholder;
    }
  }
}

.form-actions {
  margin-top: $spacing-sm;
  margin-bottom: 0 !important;

  :deep(.el-form-item__content) {
    justify-content: flex-start;
  }
}

.avatar-dialog-body {
  display: grid;
  gap: $spacing-md;
}

.avatar-preview {
  @include flex-center;
  width: 96px;
  height: 96px;
  margin: 0 auto;
  background: $bg-dark;
  color: $text-inverse;
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  overflow: hidden;
}

.avatar-preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-preview-fallback {
  text-transform: uppercase;
}

.avatar-dialog-hint {
  margin: 0;
  font-size: $font-size-xs;
  color: $text-secondary;
}

// --- Button ---
.btn-save {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 12px 28px;
  background: $primary-color;
  color: $text-inverse;
  border: none;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  cursor: pointer;
  font-family: $font-family;
  transition: all $transition-base;

  .btn-arrow {
    transition: transform $transition-base;
  }

  &:hover:not(:disabled) {
    background: $primary-hover;

    .btn-arrow {
      transform: translateX(4px);
    }
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 20px;
  margin-right: $spacing-sm;
  border: 1px solid $border-base;
  background: $bg-color;
  color: $text-regular;
  font-size: $font-size-xs;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all $transition-base;

  &:hover:not(:disabled) {
    border-color: $text-regular;
    color: $text-primary;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}
</style>
