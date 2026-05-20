<script setup>
// 学生端注册页
// 表单同时校验账号、邮箱、密码强度、二次确认与服务条款勾选
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, EditPen, Lock, Message, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { useUserStore } from '@/stores/user'
import { sendEmailVerificationCode } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'
import { evaluatePasswordStrength } from '@/utils/password-strength'

import AuthShell from './AuthShell.vue'
import PasswordStrengthMeter from './PasswordStrengthMeter.vue'
import ThirdPartyLogin from './ThirdPartyLogin.vue'

const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  username: '',
  displayName: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
  agreement: false,
})

const formRef = ref(null)
const loading = ref(false)
const codeSending = ref(false)
const errorMessage = ref('')

const { remaining: codeRemaining, start: startCountdown } = useCountdown(60)
const codeBtnText = computed(() => (codeRemaining.value > 0 ? `${codeRemaining.value}s 后重发` : '获取验证码'))

const rules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 32, message: '账号长度需在 4 到 32 个字符之间', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9._-]*$/,
      message: '账号需以字母开头，可包含字母、数字、点、下划线、短横线',
      trigger: 'blur',
    },
  ],
  displayName: [
    { required: true, message: '请输入展示名称', trigger: 'blur' },
    { min: 2, max: 24, message: '展示名称长度需在 2 到 24 个字符之间', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置登录密码', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (!value) {
          callback()
          return
        }
        if (value.length < 8) {
          callback(new Error('密码至少 8 位'))
          return
        }
        const score = evaluatePasswordStrength(value).score
        if (score < 2) {
          callback(new Error('密码强度过低，请混合数字、字母与符号'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (value && value !== form.password) {
          callback(new Error('两次密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  agreement: [
    {
      validator(_rule, value, callback) {
        if (!value) {
          callback(new Error('请阅读并同意《用户协议》与《隐私政策》'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

async function handleSendCode() {
  if (codeRemaining.value > 0) return
  if (!formRef.value) return
  try {
    await formRef.value.validateField('email')
  } catch {
    return
  }

  codeSending.value = true
  try {
    await sendEmailVerificationCode({
      email: form.email.trim(),
      scene: 'register',
    })
    ElMessage.success('验证码已发送，10 分钟内有效')
    startCountdown(60)
  } catch (error) {
    const message = error?.message || '验证码发送失败，请稍后重试'
    if (error?.status === 404 || /not found|未实现/i.test(message)) {
      ElMessage.warning('邮箱验证码服务尚未开放，请联系管理员')
    } else {
      ElMessage.error(message)
    }
  } finally {
    codeSending.value = false
  }
}

async function submit() {
  errorMessage.value = ''
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      emailCode: form.emailCode,
      password: form.password,
    })
    ElMessage.success('注册成功，欢迎加入智课问答')
    router.replace('/home')
  } catch (error) {
    errorMessage.value = error?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthShell
    hero-kicker="CKQA Student"
    hero-title="开启你的学习空间"
    hero-subtitle="完成注册即可使用课程问答、知识图谱与个性化学习记录。"
    card-kicker="Register"
    card-title="学生注册"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      class="auth-form"
      label-position="top"
      hide-required-asterisk
      @submit.prevent="submit"
    >
      <el-form-item label="账号" prop="username">
        <el-input
          v-model.trim="form.username"
          autocomplete="username"
          size="large"
          placeholder="字母开头，4-32 位"
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="展示名称" prop="displayName">
        <el-input
          v-model.trim="form.displayName"
          autocomplete="name"
          size="large"
          placeholder="将显示给老师和同学"
        >
          <template #prefix>
            <el-icon><EditPen /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="邮箱" prop="email">
        <el-input
          v-model.trim="form.email"
          autocomplete="email"
          size="large"
          placeholder="用于接收验证码与找回密码"
        >
          <template #prefix>
            <el-icon><Message /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item label="邮箱验证码" prop="emailCode">
        <div class="auth-code">
          <el-input
            v-model.trim="form.emailCode"
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

      <el-form-item label="登录密码" prop="password">
        <el-input
          v-model="form.password"
          autocomplete="new-password"
          show-password
          size="large"
          type="password"
          placeholder="至少 8 位，建议混合数字、字母与符号"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
        <PasswordStrengthMeter :password="form.password" />
      </el-form-item>

      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          autocomplete="new-password"
          show-password
          size="large"
          type="password"
          placeholder="再次输入登录密码"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item prop="agreement" class="auth-form__agreement">
        <el-checkbox v-model="form.agreement">
          我已阅读并同意
          <a href="/docs/agreement" class="auth-link" target="_blank" rel="noopener">《用户协议》</a>
          与
          <a href="/docs/privacy" class="auth-link" target="_blank" rel="noopener">《隐私政策》</a>
        </el-checkbox>
      </el-form-item>
    </el-form>

    <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

    <el-button
      class="auth-submit"
      :loading="loading"
      type="primary"
      size="large"
      @click="submit"
    >
      完成注册
      <el-icon class="auth-submit__icon"><ArrowRight /></el-icon>
    </el-button>

    <ThirdPartyLogin />

    <template #footer>
      <p>
        已有账号？
        <RouterLink class="auth-link" to="/login">直接登录</RouterLink>
      </p>
    </template>
  </AuthShell>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/breakpoints' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

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

.auth-form__agreement :deep(.el-form-item__content) {
  align-items: flex-start;
}

.auth-form__agreement :deep(.el-checkbox__label) {
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
  white-space: normal;
  line-height: 1.6;
}

.auth-form__agreement :deep(.el-checkbox__inner) {
  background: rgba(255, 255, 255, 0.18);
  border-color: rgba(255, 255, 255, 0.28);
}

.auth-link {
  color: #bfdbfe;
  font-weight: 700;
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

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

@media (max-width: $bp-tablet) {
  .auth-code {
    grid-template-columns: 1fr;
  }
  .auth-code__btn {
    width: 100%;
  }
}
</style>
