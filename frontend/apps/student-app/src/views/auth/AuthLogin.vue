<script setup>
// 学生端登录页
// 支持账号 + 密码、邮箱 + 验证码两种方式，"记住我"勾选后会话保留 7 天
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowRight, Lock, Message, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import {
  DEMO_STUDENT_ACCOUNT,
  REMEMBER_ME_DAYS,
  readRememberedProfile,
  useUserStore,
} from '@/stores/user'
import { sendEmailVerificationCode } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'

import AuthShell from './AuthShell.vue'
import ThirdPartyLogin from './ThirdPartyLogin.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs = [
  { id: 'username', label: '账号登录' },
  { id: 'email', label: '邮箱验证码' },
]
const activeTab = ref('username')

const usernameForm = reactive({
  username: '',
  password: '',
  remember: true,
})

const emailForm = reactive({
  email: '',
  code: '',
  remember: true,
})

const usernameRules = {
  username: [
    { required: true, message: '请输入账号或学号', trigger: 'blur' },
    { min: 3, max: 64, message: '账号长度需在 3 到 64 个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度需在 6 到 64 个字符之间', trigger: 'blur' },
  ],
}

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' },
  ],
}

const usernameFormRef = ref(null)
const emailFormRef = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const codeSending = ref(false)

const { remaining: codeRemaining, start: startCountdown } = useCountdown(60)
const codeBtnText = computed(() => (codeRemaining.value > 0 ? `${codeRemaining.value}s 后重发` : '获取验证码'))
const isUsernameMode = computed(() => activeTab.value === 'username')

onMounted(() => {
  // 回填上次"记住我"保留的标识
  const remembered = readRememberedProfile()
  if (remembered?.identifierType === 'username' && remembered.identifier) {
    usernameForm.username = remembered.identifier
    activeTab.value = 'username'
  } else if (remembered?.identifierType === 'email' && remembered.identifier) {
    emailForm.email = remembered.identifier
    activeTab.value = 'email'
  }
})

watch(activeTab, () => {
  errorMessage.value = ''
})

function fillDemoAccount() {
  usernameForm.username = DEMO_STUDENT_ACCOUNT.username
  usernameForm.password = DEMO_STUDENT_ACCOUNT.password
  usernameForm.remember = true
  activeTab.value = 'username'
  errorMessage.value = ''
}

async function handleSendCode() {
  if (codeRemaining.value > 0) return
  if (!emailFormRef.value) return
  try {
    await emailFormRef.value.validateField('email')
  } catch {
    return
  }

  codeSending.value = true
  try {
    await sendEmailVerificationCode({
      email: emailForm.email.trim(),
      scene: 'login',
    })
    ElMessage.success('验证码已发送，请注意查收邮箱')
    startCountdown(60)
  } catch (error) {
    // 占位接口可能没有真实实现，给出友好提示但不阻塞演示
    const message = error?.message || '验证码发送失败，请稍后重试'
    if (error?.status === 404 || /not found|未实现/i.test(message)) {
      ElMessage.warning('邮箱验证码服务尚未开放，请改用账号密码登录')
    } else {
      ElMessage.error(message)
    }
  } finally {
    codeSending.value = false
  }
}

async function submit() {
  errorMessage.value = ''
  const formRef = isUsernameMode.value ? usernameFormRef.value : emailFormRef.value
  if (!formRef) return
  try {
    await formRef.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    if (isUsernameMode.value) {
      await userStore.login({
        username: usernameForm.username,
        password: usernameForm.password,
        remember: usernameForm.remember,
      })
    } else {
      await userStore.loginByEmailCode({
        email: emailForm.email,
        code: emailForm.code,
        remember: emailForm.remember,
      })
    }
    ElMessage.success('登录成功，欢迎回来')
    router.replace(resolveRedirect())
  } catch (error) {
    errorMessage.value = error?.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function resolveRedirect() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('/login')
    ? redirect
    : '/home'
}
</script>

<template>
  <AuthShell
    hero-kicker="CKQA Student"
    hero-title="欢迎回来"
    hero-subtitle="使用学生账号进入你的课程问答与知识图谱学习空间。"
    card-kicker="Login"
    card-title="学生登录"
  >
    <template #card-actions>
      <button class="auth-demo" type="button" @click="fillDemoAccount">体验账号一键填入</button>
    </template>

    <div class="auth-tabs" role="tablist" aria-label="登录方式">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="['auth-tabs__item', { 'auth-tabs__item--active': activeTab === tab.id }]"
        :aria-selected="activeTab === tab.id"
        type="button"
        role="tab"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </div>

    <el-form
      v-show="isUsernameMode"
      ref="usernameFormRef"
      :model="usernameForm"
      :rules="usernameRules"
      class="auth-form"
      label-position="top"
      hide-required-asterisk
      @submit.prevent="submit"
    >
      <el-form-item label="账号 / 学号" prop="username">
        <el-input
          v-model.trim="usernameForm.username"
          autocomplete="username"
          size="large"
          placeholder="请输入账号或学号"
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="usernameForm.password"
          autocomplete="current-password"
          show-password
          size="large"
          type="password"
          placeholder="请输入密码"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <div class="auth-row">
        <el-checkbox v-model="usernameForm.remember">
          {{ `${REMEMBER_ME_DAYS} 天内自动登录` }}
        </el-checkbox>
        <RouterLink class="auth-link" to="/forgot-password">忘记密码？</RouterLink>
      </div>
    </el-form>

    <el-form
      v-show="!isUsernameMode"
      ref="emailFormRef"
      :model="emailForm"
      :rules="emailRules"
      class="auth-form"
      label-position="top"
      hide-required-asterisk
      @submit.prevent="submit"
    >
      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model.trim="emailForm.email"
          autocomplete="email"
          size="large"
          placeholder="请输入注册邮箱"
        >
          <template #prefix>
            <el-icon><Message /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="邮箱验证码" prop="code">
        <div class="auth-code">
          <el-input
            v-model.trim="emailForm.code"
            size="large"
            maxlength="6"
            placeholder="请输入 6 位验证码"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
          <el-button
            class="auth-code__btn"
            :loading="codeSending"
            :disabled="codeRemaining > 0"
            type="primary"
            plain
            @click="handleSendCode"
          >
            {{ codeBtnText }}
          </el-button>
        </div>
      </el-form-item>
      <div class="auth-row">
        <el-checkbox v-model="emailForm.remember">
          {{ `${REMEMBER_ME_DAYS} 天内自动登录` }}
        </el-checkbox>
        <RouterLink class="auth-link" to="/forgot-password">忘记密码？</RouterLink>
      </div>
    </el-form>

    <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

    <el-button
      class="auth-submit"
      :loading="loading"
      type="primary"
      size="large"
      @click="submit"
    >
      登录学习空间
      <el-icon class="auth-submit__icon"><ArrowRight /></el-icon>
    </el-button>

    <ThirdPartyLogin />

    <template #footer>
      <p class="auth-shell__footer-text">
        还没有账号？
        <RouterLink class="auth-link" to="/register">立即注册</RouterLink>
      </p>
    </template>
  </AuthShell>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/breakpoints' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

.auth-demo {
  justify-self: end;
  padding: 8px 14px;
  border: 1px solid rgba(94, 234, 212, 0.34);
  border-radius: $radius-full;
  color: #99f6e4;
  background: rgba(20, 184, 166, 0.12);
  cursor: pointer;
  font-weight: 800;
  font-size: 12px;
  transition: background $duration-fast $ease-out;
}

.auth-demo:hover {
  background: rgba(20, 184, 166, 0.22);
}

.auth-tabs {
  display: inline-flex;
  padding: 4px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: $radius-full;
  background: rgba(15, 23, 42, 0.32);
  width: fit-content;
}

.auth-tabs__item {
  padding: 8px 18px;
  border: 0;
  border-radius: $radius-full;
  color: rgba(255, 255, 255, 0.7);
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;
}

.auth-tabs__item--active {
  background: linear-gradient(135deg, #14b8a6, #3b82f6);
  color: #fff;
  box-shadow: 0 8px 18px rgba(20, 184, 166, 0.32);
}

.auth-form {
  display: grid;
  gap: 4px;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.auth-form :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.78);
  font-weight: 700;
  font-size: 13px;
  padding: 0 0 6px;
}

.auth-form :deep(.el-form-item__error) {
  color: #fecaca;
}

.auth-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.auth-row :deep(.el-checkbox__label) {
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
}

.auth-row :deep(.el-checkbox__inner) {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.28);
}

.auth-link {
  color: #bfdbfe;
  font-weight: 700;
  font-size: 13px;
}

.auth-link:hover {
  color: #93c5fd;
}

.auth-code {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.auth-code__btn {
  height: 44px;
  border-radius: $radius-lg;
  font-weight: 700;
}

.auth-error {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid rgba(248, 113, 113, 0.35);
  border-radius: $radius-md;
  color: #fecaca;
  background: rgba(127, 29, 29, 0.2);
  font-size: 13px;
  font-weight: 700;
}

.auth-submit {
  width: 100%;
  height: 46px;
  border: 0;
  border-radius: $radius-lg;
  background: linear-gradient(135deg, #14b8a6, #3b82f6);
  font-weight: 900;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;
}

.auth-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 18px 38px rgba(20, 184, 166, 0.28);
}

.auth-submit__icon {
  margin-left: 6px;
}

.auth-shell__footer-text {
  margin: 0;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

@media (max-width: $bp-tablet) {
  .auth-row {
    flex-direction: column;
    align-items: flex-start;
  }
  .auth-code {
    grid-template-columns: 1fr;
  }
  .auth-code__btn {
    width: 100%;
  }
}
</style>
