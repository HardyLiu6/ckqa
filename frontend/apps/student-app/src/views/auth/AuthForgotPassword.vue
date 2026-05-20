<script setup>
// 忘记密码 / 重置密码页
// 三步流程：1) 输入邮箱并发送验证码 2) 设置新密码并提交 3) 成功提示并引导回登录
import { computed, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowRight, Lock, Message, SuccessFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import { useUserStore } from '@/stores/user'
import { sendEmailVerificationCode } from '@/api/auth'
import { useCountdown } from '@/composables/useCountdown'
import { evaluatePasswordStrength } from '@/utils/password-strength'

import AuthShell from './AuthShell.vue'
import PasswordStrengthMeter from './PasswordStrengthMeter.vue'

const router = useRouter()
const userStore = useUserStore()

const STEPS = [
  { id: 'identify', label: '验证邮箱' },
  { id: 'reset', label: '设置新密码' },
  { id: 'done', label: '完成' },
]
const stepIndex = ref(0)
const currentStep = computed(() => STEPS[stepIndex.value]?.id ?? 'identify')

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const formRef = ref(null)
const loading = ref(false)
const codeSending = ref(false)
const errorMessage = ref('')

const { remaining: codeRemaining, start: startCountdown } = useCountdown(60)
const codeBtnText = computed(() => (codeRemaining.value > 0 ? `${codeRemaining.value}s 后重发` : '获取验证码'))

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '请输入 6 位数字验证码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请设置新密码', trigger: 'blur' },
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
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator(_rule, value, callback) {
        if (value && value !== form.newPassword) {
          callback(new Error('两次密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
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
      scene: 'reset-password',
    })
    ElMessage.success('验证码已发送，10 分钟内有效')
    startCountdown(60)
  } catch (error) {
    const message = error?.message || '验证码发送失败，请稍后重试'
    if (error?.status === 404 || /not found|未实现/i.test(message)) {
      ElMessage.warning('找回密码服务尚未开放，请联系管理员')
    } else {
      ElMessage.error(message)
    }
  } finally {
    codeSending.value = false
  }
}

async function handleNext() {
  errorMessage.value = ''
  if (!formRef.value) return
  try {
    await formRef.value.validateField(['email', 'code'])
  } catch {
    return
  }
  stepIndex.value = 1
}

async function handleSubmitReset() {
  errorMessage.value = ''
  if (!formRef.value) return
  try {
    await formRef.value.validateField(['newPassword', 'confirmPassword'])
  } catch {
    return
  }

  loading.value = true
  try {
    await userStore.resetPassword({
      email: form.email,
      code: form.code,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
    stepIndex.value = 2
  } catch (error) {
    errorMessage.value = error?.message || '重置失败，请确认验证码后重试'
  } finally {
    loading.value = false
  }
}

function backToLogin() {
  router.replace('/login')
}
</script>

<template>
  <AuthShell
    hero-kicker="CKQA Student"
    hero-title="找回密码"
    hero-subtitle="忘记密码也别担心，使用注册邮箱即可在几分钟内重置。"
    card-kicker="Reset"
    card-title="重置登录密码"
  >
    <ol class="auth-stepper" aria-label="重置流程进度">
      <li
        v-for="(step, index) in STEPS"
        :key="step.id"
        :class="[
          'auth-stepper__item',
          { 'auth-stepper__item--active': index === stepIndex },
          { 'auth-stepper__item--done': index < stepIndex },
        ]"
      >
        <span class="auth-stepper__index">{{ index + 1 }}</span>
        <span class="auth-stepper__label">{{ step.label }}</span>
      </li>
    </ol>

    <el-form
      v-if="currentStep !== 'done'"
      ref="formRef"
      :model="form"
      :rules="rules"
      class="auth-form"
      label-position="top"
      hide-required-asterisk
    >
      <template v-if="currentStep === 'identify'">
        <el-form-item label="注册邮箱" prop="email">
          <el-input
            v-model.trim="form.email"
            autocomplete="email"
            size="large"
            placeholder="请输入注册时使用的邮箱"
          >
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="邮箱验证码" prop="code">
          <div class="auth-code">
            <el-input
              v-model.trim="form.code"
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
      </template>

      <template v-else>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
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
          <PasswordStrengthMeter :password="form.newPassword" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            autocomplete="new-password"
            show-password
            size="large"
            type="password"
            placeholder="再次输入新密码"
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
      </template>
    </el-form>

    <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

    <div v-if="currentStep === 'identify'" class="auth-actions">
      <el-button class="auth-submit" type="primary" size="large" @click="handleNext">
        下一步
        <el-icon class="auth-submit__icon"><ArrowRight /></el-icon>
      </el-button>
    </div>

    <div v-else-if="currentStep === 'reset'" class="auth-actions">
      <el-button size="large" plain @click="stepIndex = 0">上一步</el-button>
      <el-button class="auth-submit" :loading="loading" type="primary" size="large" @click="handleSubmitReset">
        提交并重置
        <el-icon class="auth-submit__icon"><ArrowRight /></el-icon>
      </el-button>
    </div>

    <div v-else class="auth-success">
      <el-icon class="auth-success__icon" :size="44"><SuccessFilled /></el-icon>
      <h3>密码已成功重置</h3>
      <p>你可以使用新密码登录学习空间，或者继续浏览介绍页。</p>
      <div class="auth-actions">
        <el-button class="auth-submit" type="primary" size="large" @click="backToLogin">返回登录</el-button>
      </div>
    </div>

    <template #footer>
      <p>
        想起密码了？
        <RouterLink class="auth-link" to="/login">直接登录</RouterLink>
      </p>
    </template>
  </AuthShell>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/breakpoints' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

.auth-stepper {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.auth-stepper__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 700;
}

.auth-stepper__item--active {
  border-color: rgba(94, 234, 212, 0.5);
  background: rgba(20, 184, 166, 0.18);
  color: #fff;
}

.auth-stepper__item--done {
  background: rgba(59, 130, 246, 0.18);
  color: #bfdbfe;
}

.auth-stepper__index {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-weight: 900;
  font-size: 12px;
}

.auth-stepper__item--active .auth-stepper__index {
  background: linear-gradient(135deg, #14b8a6, #3b82f6);
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

.auth-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.auth-actions > .el-button {
  flex: 1;
}

.auth-submit {
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

.auth-success {
  display: grid;
  gap: 10px;
  justify-items: center;
  text-align: center;
  padding: 12px 4px;
}

.auth-success__icon {
  color: #5eead4;
}

.auth-success h3 {
  margin: 0;
  color: #fff;
  font-size: 22px;
}

.auth-success p {
  margin: 0;
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
}

.auth-link {
  color: #bfdbfe;
  font-weight: 700;
}

.auth-link:hover {
  color: #93c5fd;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

@media (max-width: $bp-tablet) {
  .auth-stepper {
    grid-template-columns: 1fr;
  }
  .auth-code {
    grid-template-columns: 1fr;
  }
  .auth-code__btn {
    width: 100%;
  }
  .auth-actions {
    flex-direction: column;
  }
}
</style>
