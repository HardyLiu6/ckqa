<script setup>
import { computed, reactive, ref } from 'vue'
import {
  GraduationCap,
  KeyRound,
  LogIn,
  ShieldCheck,
  Sparkles,
  UserRound,
  Workflow,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import { LOGIN_PRESETS, authStore } from '../../stores/auth.js'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitError = ref('')
const activePreset = ref(LOGIN_PRESETS[0].role)
const form = reactive({
  username: LOGIN_PRESETS[0].username,
  password: LOGIN_PRESETS[0].password,
})
const rememberMe = ref(true)

const productHighlights = [
  { icon: Workflow, label: 'PDF 解析 → GraphRAG 建图', detail: '一站式课程资料图谱化' },
  { icon: Sparkles, label: '提示词自动调优', detail: '比基线候选评分提升 30%+' },
  { icon: ShieldCheck, label: '细粒度权限', detail: '管理员 / 教师 / 学生分层' },
  { icon: GraduationCap, label: '智能问答', detail: '本地 / 全局 / 漂移多模式' },
]

const activePresetDetail = computed(() =>
  LOGIN_PRESETS.find((preset) => preset.role === activePreset.value) ?? LOGIN_PRESETS[0],
)

function applyPreset(preset) {
  activePreset.value = preset.role
  form.username = preset.username
  form.password = preset.password
  submitError.value = ''
}

async function submit() {
  submitError.value = ''
  loading.value = true

  try {
    await authStore.login(form)
    router.replace(resolveRedirect())
  } catch (error) {
    submitError.value = createApiError(error).message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}

function resolveRedirect() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/app') ? redirect : '/app/dashboard'
}
</script>

<template>
  <div class="login-shell">
    <!-- 左侧：品牌叙事 -->
    <aside class="login-aside" aria-labelledby="login-product-title">
      <div class="login-aside__brand">
        <img class="login-aside__logo" src="/logo.png" alt="智课问答" />
        <div class="login-aside__brand-text">
          <strong>智课问答</strong>
          <span>CourseKG · 教学数据底座</span>
        </div>
      </div>

      <div class="login-aside__hero">
        <p class="login-aside__eyebrow">CONSOLE · ADMIN & TEACHER</p>
        <h1 id="login-product-title" class="login-aside__title">
          让课程资料成为<br />
          <em>可被检索的知识图谱</em>
        </h1>
        <p class="login-aside__lede">
          管理员与教师共用一套控制台，统一管理课程、资料、知识库与问答会话。
          登录后按角色权限进入对应工作区。
        </p>
      </div>

      <ul class="login-aside__highlights">
        <li v-for="item in productHighlights" :key="item.label">
          <span class="login-aside__highlight-icon">
            <component :is="item.icon" :size="16" aria-hidden="true" />
          </span>
          <div class="login-aside__highlight-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.detail }}</small>
          </div>
        </li>
      </ul>

      <footer class="login-aside__footer">
        © 2026 CKQA · 仅限管理员与教师登录
      </footer>
    </aside>

    <!-- 右侧：登录卡 -->
    <main class="login-main">
      <div class="login-card">
        <header class="login-card__header">
          <span class="login-card__icon">
            <ShieldCheck :size="22" aria-hidden="true" />
          </span>
          <div>
            <h2 class="login-card__title">欢迎回来</h2>
            <p class="login-card__subtitle">使用账号与密码登录管理控制台</p>
          </div>
        </header>

        <!-- preset 角色切换 -->
        <div class="login-preset" role="tablist" aria-label="测试身份切换">
          <button
            v-for="preset in LOGIN_PRESETS"
            :key="preset.role"
            type="button"
            role="tab"
            class="login-preset__tab"
            :class="{ 'is-active': activePreset === preset.role }"
            :aria-selected="activePreset === preset.role"
            @click="applyPreset(preset)"
          >
            <strong>{{ preset.label }}</strong>
            <small>{{ preset.description }}</small>
          </button>
        </div>

        <form class="login-form" @submit.prevent="submit">
          <label class="login-field">
            <span>账号 / 用户名</span>
            <el-input
              v-model.trim="form.username"
              size="large"
              autocomplete="username"
              placeholder="如：admin.heqh"
            >
              <template #prefix>
                <UserRound :size="16" aria-hidden="true" />
              </template>
            </el-input>
          </label>

          <label class="login-field">
            <span>密码</span>
            <el-input
              v-model="form.password"
              size="large"
              autocomplete="current-password"
              show-password
              type="password"
              placeholder="请输入登录密码"
            >
              <template #prefix>
                <KeyRound :size="16" aria-hidden="true" />
              </template>
            </el-input>
          </label>

          <div class="login-form__row">
            <label class="login-remember">
              <el-checkbox v-model="rememberMe">下次自动填充</el-checkbox>
            </label>
            <button
              type="button"
              class="login-help-link"
              title="本期忘记密码暂未上线，请联系管理员重置"
              @click.prevent
            >
              忘记密码？
            </button>
          </div>

          <p v-if="submitError" class="login-form__error" role="alert">
            {{ submitError }}
          </p>

          <el-button
            class="ckqa-el-button ckqa-el-button--primary login-submit"
            type="primary"
            native-type="submit"
            :loading="loading"
            size="large"
          >
            <LogIn class="button-icon" :size="16" aria-hidden="true" />
            进入控制台
          </el-button>
        </form>

        <footer class="login-card__footer">
          <span>当前身份：</span>
          <strong>{{ activePresetDetail.label }}</strong>
          <em>{{ activePresetDetail.description }}</em>
        </footer>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  min-height: 100vh;
  width: 100%;
  background:
    radial-gradient(circle at -10% -10%, color-mix(in srgb, var(--ckqa-accent) 25%, transparent), transparent 38%),
    radial-gradient(circle at 110% 110%, color-mix(in srgb, var(--ckqa-accent) 18%, transparent), transparent 32%),
    var(--ckqa-bg);
}

/* ─── 左侧品牌叙事 ────────────────────────── */
.login-aside {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  gap: 32px;
  padding: 48px 56px 32px;
  color: var(--ckqa-text);
  overflow: hidden;
}

.login-aside::before {
  content: '';
  position: absolute;
  inset: 24px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--ckqa-accent) 12%, transparent), transparent 60%),
    var(--ckqa-surface);
  border: 1px solid color-mix(in srgb, var(--ckqa-accent) 18%, var(--ckqa-border));
  border-radius: 24px;
  box-shadow: 0 24px 60px color-mix(in srgb, var(--ckqa-accent) 12%, transparent);
  pointer-events: none;
  z-index: 0;
}
.login-aside > * {
  position: relative;
  z-index: 1;
}

.login-aside__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.login-aside__logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  object-fit: contain;
}
.login-aside__brand-text strong {
  display: block;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.login-aside__brand-text span {
  font-size: 12px;
  color: var(--ckqa-text-muted);
  font-family: var(--ckqa-font-mono);
}

.login-aside__hero {
  align-self: center;
  max-width: 480px;
}
.login-aside__eyebrow {
  margin: 0 0 12px;
  font-size: 11px;
  letter-spacing: 0.18em;
  font-weight: 700;
  color: var(--ckqa-accent-strong);
}
.login-aside__title {
  margin: 0 0 16px;
  font-size: 36px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.01em;
  color: var(--ckqa-text);
}
.login-aside__title em {
  font-style: normal;
  background: linear-gradient(135deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.login-aside__lede {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--ckqa-text-muted);
}

.login-aside__highlights {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.login-aside__highlights li {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--ckqa-surface) 92%, transparent);
  border: 1px solid var(--ckqa-border);
}
.login-aside__highlight-icon {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--ckqa-accent) 14%, var(--ckqa-surface));
  color: var(--ckqa-accent-strong);
  flex-shrink: 0;
}
.login-aside__highlight-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.login-aside__highlight-copy strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--ckqa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.login-aside__highlight-copy small {
  font-size: 11px;
  color: var(--ckqa-text-muted);
}

.login-aside__footer {
  font-size: 11px;
  color: var(--ckqa-text-muted);
  font-family: var(--ckqa-font-mono);
}

/* ─── 右侧登录卡 ────────────────────────── */
.login-main {
  display: grid;
  place-items: center;
  padding: 40px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px;
  border-radius: 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  box-shadow:
    0 1px 0 0 color-mix(in srgb, var(--ckqa-accent) 8%, transparent),
    0 24px 60px color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
}

.login-card__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.login-card__icon {
  display: inline-grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--ckqa-accent) 14%, var(--ckqa-surface));
  color: var(--ckqa-accent-strong);
  flex-shrink: 0;
}
.login-card__title {
  margin: 0 0 2px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--ckqa-text);
}
.login-card__subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--ckqa-text-muted);
}

.login-preset {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  margin-bottom: 20px;
  padding: 4px;
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  background: var(--ckqa-surface-muted);
}
.login-preset__tab {
  display: grid;
  gap: 1px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: var(--ckqa-text-muted);
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: background 0.18s ease, color 0.18s ease, border-color 0.18s ease,
    box-shadow 0.18s ease;
}
.login-preset__tab strong {
  font-size: 13px;
  font-weight: 700;
}
.login-preset__tab small {
  font-size: 11px;
  color: var(--ckqa-text-muted);
}
.login-preset__tab:hover {
  background: color-mix(in srgb, var(--ckqa-accent) 6%, var(--ckqa-surface));
  color: var(--ckqa-text);
}
.login-preset__tab.is-active {
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-color: var(--ckqa-accent);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--ckqa-accent) 18%, transparent);
}
.login-preset__tab.is-active strong {
  color: var(--ckqa-accent-strong);
}

.login-form {
  display: grid;
  gap: 14px;
}
.login-field {
  display: grid;
  gap: 6px;
}
.login-field > span {
  font-size: 12px;
  font-weight: 500;
  color: var(--ckqa-text-muted);
}
.login-field :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 0 14px;
}

.login-form__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}
.login-remember :deep(.el-checkbox__label) {
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
.login-help-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: var(--ckqa-accent-strong);
  cursor: pointer;
  text-decoration: none;
}
.login-help-link:hover {
  text-decoration: underline;
}

.login-form__error {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--ckqa-danger, #dc2626) 10%, var(--ckqa-surface));
  border: 1px solid color-mix(in srgb, var(--ckqa-danger, #dc2626) 30%, transparent);
  color: var(--ckqa-danger, #dc2626);
  font-size: 12px;
}

.login-submit {
  height: 44px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.login-card__footer {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed var(--ckqa-border);
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
.login-card__footer strong {
  color: var(--ckqa-accent-strong);
  font-weight: 700;
}
.login-card__footer em {
  font-style: normal;
  color: var(--ckqa-text-muted);
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
  .login-aside {
    padding: 32px 24px 16px;
    grid-template-rows: auto auto auto auto;
  }
  .login-aside::before {
    inset: 16px;
  }
  .login-aside__title {
    font-size: 28px;
  }
  .login-aside__highlights {
    grid-template-columns: 1fr;
  }
  .login-main {
    padding: 24px 16px 40px;
  }
}
</style>
