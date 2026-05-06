<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowRight, ChatDotRound, EditPen, Lock, User } from '@element-plus/icons-vue'

import { DEMO_STUDENT_ACCOUNT, useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: DEMO_STUDENT_ACCOUNT.username,
  displayName: '',
  password: DEMO_STUDENT_ACCOUNT.password,
})

const isRegister = computed(() => route.name === 'Register')
const title = computed(() => (isRegister.value ? '创建学习账号' : '欢迎回来'))
const subtitle = computed(() => (isRegister.value
  ? '注册后即可进入课程问答与知识图谱学习空间'
  : '使用学生账号进入你的课程学习空间'))
const submitText = computed(() => (isRegister.value ? '完成注册' : '登录学习空间'))
const switchTarget = computed(() => (isRegister.value ? '/login' : '/register'))
const switchText = computed(() => (isRegister.value ? '已有账号，去登录' : '还没有账号，去注册'))

watch(isRegister, () => {
  errorMessage.value = ''
  if (!isRegister.value) {
    form.displayName = ''
  }
})

function fillDemoAccount() {
  form.username = DEMO_STUDENT_ACCOUNT.username
  form.password = DEMO_STUDENT_ACCOUNT.password
  form.displayName = ''
  errorMessage.value = ''
}

async function submit() {
  errorMessage.value = ''
  loading.value = true

  try {
    if (isRegister.value) {
      await userStore.register(form)
    } else {
      await userStore.login(form)
    }
    router.replace(resolveRedirect())
  } catch (error) {
    errorMessage.value = error?.message || '账号请求失败，请稍后重试'
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
  <main class="auth-access">
    <RouterLink class="auth-brand" to="/" aria-label="返回介绍页">
      <span class="auth-brand__mark">
        <el-icon :size="19"><ChatDotRound /></el-icon>
      </span>
      <span>智课问答</span>
    </RouterLink>

    <section class="auth-hero" aria-labelledby="student-auth-title">
      <div class="auth-hero__copy">
        <p class="auth-kicker">CKQA Student</p>
        <h1 id="student-auth-title">{{ title }}</h1>
        <p>{{ subtitle }}</p>
      </div>

      <div class="auth-card">
        <div class="auth-card__header">
          <div>
            <p class="auth-kicker">{{ isRegister ? 'Register' : 'Login' }}</p>
            <h2>{{ isRegister ? '学生注册' : '学生登录' }}</h2>
          </div>
          <button v-if="!isRegister" class="auth-demo" type="button" @click="fillDemoAccount">
            测试账号
          </button>
        </div>

        <form class="auth-form" @submit.prevent="submit">
          <label class="auth-field">
            <span>用户名</span>
            <el-input v-model.trim="form.username" autocomplete="username" size="large">
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </label>

          <label v-if="isRegister" class="auth-field">
            <span>展示名称</span>
            <el-input v-model.trim="form.displayName" autocomplete="name" size="large">
              <template #prefix>
                <el-icon><EditPen /></el-icon>
              </template>
            </el-input>
          </label>

          <label class="auth-field">
            <span>密码</span>
            <el-input
              v-model="form.password"
              autocomplete="current-password"
              show-password
              size="large"
              type="password"
            >
              <template #prefix>
                <el-icon><Lock /></el-icon>
              </template>
            </el-input>
          </label>

          <p v-if="errorMessage" class="auth-error" role="alert">{{ errorMessage }}</p>

          <el-button class="auth-submit" native-type="submit" :loading="loading" type="primary">
            {{ submitText }}
            <el-icon class="auth-submit__icon"><ArrowRight /></el-icon>
          </el-button>
        </form>

        <RouterLink class="auth-switch" :to="switchTarget">{{ switchText }}</RouterLink>
      </div>
    </section>
  </main>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/breakpoints' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;

.auth-access {
  position: relative;
  min-height: 100vh;
  padding: 28px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 14% 18%, rgba(45, 212, 191, 0.22), transparent 28%),
    radial-gradient(circle at 86% 14%, rgba(251, 146, 60, 0.18), transparent 24%),
    linear-gradient(135deg, #111827 0%, #17142a 48%, #0f172a 100%);
}

.auth-access::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.7), transparent 78%);
}

.auth-brand {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-weight: 800;
}

.auth-brand__mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.22);
}

.auth-hero {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(340px, 430px);
  align-items: center;
  gap: 56px;
  width: min(1120px, 100%);
  min-height: calc(100vh - 96px);
  margin: 0 auto;
}

.auth-hero__copy {
  display: grid;
  gap: 18px;
  max-width: 610px;
}

.auth-kicker {
  margin: 0;
  color: #5eead4;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
  text-transform: uppercase;
}

.auth-hero h1 {
  margin: 0;
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: clamp(42px, 6vw, 76px);
  line-height: 0.96;
  letter-spacing: 0;
}

.auth-hero__copy > p:last-child {
  max-width: 560px;
  margin: 0;
  color: rgba(255, 255, 255, 0.74);
  font-size: 18px;
  line-height: 1.8;
}

.auth-card {
  display: grid;
  gap: 22px;
  padding: 24px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: $radius-xl;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(22px);
}

.auth-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.auth-card h2 {
  margin: 4px 0 0;
  color: #fff;
  font-size: 24px;
}

.auth-demo {
  padding: 8px 12px;
  border: 1px solid rgba(94, 234, 212, 0.34);
  border-radius: $radius-full;
  color: #99f6e4;
  background: rgba(20, 184, 166, 0.12);
  cursor: pointer;
  font-weight: 800;
}

.auth-form {
  display: grid;
  gap: 16px;
}

.auth-field {
  display: grid;
  gap: 8px;
}

.auth-field span {
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
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

.auth-switch {
  color: #bfdbfe;
  font-weight: 800;
  text-align: center;
}

:deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: $radius-lg;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: none;
}

@media (max-width: $bp-tablet) {
  .auth-access {
    padding: 20px;
  }

  .auth-hero {
    grid-template-columns: 1fr;
    gap: 28px;
    align-content: center;
    min-height: calc(100vh - 84px);
  }

  .auth-hero h1 {
    font-size: 42px;
  }
}
</style>
