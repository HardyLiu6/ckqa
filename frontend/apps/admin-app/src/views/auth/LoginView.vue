<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ArrowRight,
  Brain,
  FileText,
  KeyRound,
  MessageSquareText,
  ShieldCheck,
  UserRound,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import { LOGIN_PRESETS, authStore } from '../../stores/auth.js'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitError = ref('')

/**
 * "记住密码 7 天" 草案：
 * 仅前端 localStorage 保存账号 + 密码（明文）+ 过期时间戳，到期或退出登录后清除。
 * 不传到后端、不写 cookie，避免越权场景下 cookie 自动随请求泄漏。
 * 这是登录便利性 trade-off；安全要求更高的场景请关闭此开关。
 */
const REMEMBER_STORAGE_KEY = 'ckqa.admin.remember-credentials'
const REMEMBER_DURATION_DAYS = 7
const REMEMBER_DURATION_MS = REMEMBER_DURATION_DAYS * 24 * 60 * 60 * 1000

function readRememberedCredentials() {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(REMEMBER_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    if (typeof parsed.expiresAt !== 'number' || parsed.expiresAt < Date.now()) {
      window.localStorage.removeItem(REMEMBER_STORAGE_KEY)
      return null
    }
    if (typeof parsed.username !== 'string' || typeof parsed.password !== 'string') {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

function writeRememberedCredentials(username, password) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(
      REMEMBER_STORAGE_KEY,
      JSON.stringify({
        username,
        password,
        expiresAt: Date.now() + REMEMBER_DURATION_MS,
      }),
    )
  } catch {
    // 忽略 localStorage 写入异常（隐私模式 / 配额超限）
  }
}

function clearRememberedCredentials() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(REMEMBER_STORAGE_KEY)
  } catch {
    // 忽略
  }
}

// 默认账号优先级：localStorage 持久化 > LOGIN_PRESETS[0]
const remembered = readRememberedCredentials()
const activePreset = ref(LOGIN_PRESETS[0].role)
const form = reactive({
  username: remembered?.username ?? LOGIN_PRESETS[0].username,
  password: remembered?.password ?? LOGIN_PRESETS[0].password,
})
const rememberMe = ref(Boolean(remembered))

const productPipeline = [
  {
    step: '01',
    icon: FileText,
    title: 'PDF 解析与导出',
    detail: 'MinerU · MinIO · MySQL',
  },
  {
    step: '02',
    icon: Brain,
    title: '知识图谱构建',
    detail: 'GraphRAG · 提示词调优',
  },
  {
    step: '03',
    icon: MessageSquareText,
    title: '智能问答与运维',
    detail: '4 种检索模式 · 验证溯源',
  },
]

const activePresetDetail = computed(() =>
  LOGIN_PRESETS.find((preset) => preset.role === activePreset.value) ?? LOGIN_PRESETS[0],
)

function applyPreset(preset) {
  activePreset.value = preset.role
  // 如果用户已经修改过账号密码，preset 切换不应该覆盖；只在初始 / preset 当前账号匹配时切换
  const matchingExisting = LOGIN_PRESETS.find(
    (p) => p.username === form.username && p.password === form.password,
  )
  if (matchingExisting) {
    form.username = preset.username
    form.password = preset.password
  }
  submitError.value = ''
}

async function submit() {
  submitError.value = ''
  loading.value = true

  try {
    await authStore.login(form)
    if (rememberMe.value) {
      writeRememberedCredentials(form.username, form.password)
    } else {
      clearRememberedCredentials()
    }
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

onMounted(() => {
  // 路由层 logout 已经清掉 token 与 currentUser；这里只负责"取消勾选记住密码 → 立即清空持久化"。
  if (!rememberMe.value) {
    clearRememberedCredentials()
  }
})
</script>

<template>
  <div class="login-shell">
    <!-- 左侧：品牌叙事 + 生产链路卡片 -->
    <aside class="login-aside" aria-labelledby="login-product-title">
      <!-- 知识图谱节点装饰（绝对定位） -->
      <div class="login-aside__graph" aria-hidden="true">
        <span class="login-aside__node login-aside__node--1"></span>
        <span class="login-aside__node login-aside__node--2"></span>
        <span class="login-aside__node login-aside__node--3"></span>
        <span class="login-aside__node login-aside__node--4"></span>
        <span class="login-aside__node login-aside__node--5"></span>
        <span class="login-aside__line login-aside__line--1">
          <span class="login-aside__pulse"></span>
        </span>
        <span class="login-aside__line login-aside__line--2">
          <span class="login-aside__pulse"></span>
        </span>
        <span class="login-aside__line login-aside__line--3">
          <span class="login-aside__pulse"></span>
        </span>
        <span class="login-aside__line login-aside__line--4">
          <span class="login-aside__pulse"></span>
        </span>
        <!-- 浅景深漂浮粒子 -->
        <span class="login-aside__particle login-aside__particle--1"></span>
        <span class="login-aside__particle login-aside__particle--2"></span>
        <span class="login-aside__particle login-aside__particle--3"></span>
        <span class="login-aside__particle login-aside__particle--4"></span>
        <span class="login-aside__particle login-aside__particle--5"></span>
        <span class="login-aside__particle login-aside__particle--6"></span>
      </div>

      <header class="login-aside__brand">
        <img class="login-aside__logo" src="/logo.png" alt="智课问答" />
        <div class="login-aside__brand-text">
          <strong>智课问答</strong>
          <span>CourseKG · GraphRAG</span>
        </div>
      </header>

      <div class="login-aside__hero">
        <span class="login-aside__eyebrow">从资料到问答 · 全链路工作台</span>
        <h1 id="login-product-title" class="login-aside__title">
          让课程资料<br />
          成为<em>可被检索的图谱</em>
        </h1>
        <p class="login-aside__lede">
          PDF 解析、GraphRAG 建图、提示词调优、智能问答，管理员与教师在同一控制台协作。
        </p>

        <ol class="login-aside__pipeline">
          <li v-for="item in productPipeline" :key="item.step" class="login-aside__pipeline-item">
            <span class="login-aside__pipeline-step">{{ item.step }}</span>
            <span class="login-aside__pipeline-icon">
              <component :is="item.icon" :size="14" aria-hidden="true" />
            </span>
            <span class="login-aside__pipeline-text">
              <strong>{{ item.title }}</strong>
              <small>{{ item.detail }}</small>
            </span>
            <ArrowRight :size="14" class="login-aside__pipeline-arrow" aria-hidden="true" />
          </li>
        </ol>
      </div>
    </aside>

    <!-- 右侧：乳白色登录卡 -->
    <main class="login-main">
      <section class="login-card" aria-labelledby="login-form-title">
        <header class="login-card__header">
          <span class="login-card__icon">
            <ShieldCheck :size="22" aria-hidden="true" />
          </span>
          <div>
            <h2 id="login-form-title" class="login-card__title">欢迎回来</h2>
            <p class="login-card__subtitle">使用账号与密码登录控制台</p>
          </div>
        </header>

        <!-- 角色 preset -->
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
            <span class="login-field__label">账号 / 用户名</span>
            <el-input
              v-model.trim="form.username"
              size="large"
              autocomplete="username"
              placeholder="例如：admin.heqh"
            >
              <template #prefix>
                <UserRound :size="16" aria-hidden="true" />
              </template>
            </el-input>
          </label>

          <label class="login-field">
            <span class="login-field__label">密码</span>
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
              <el-checkbox v-model="rememberMe">记住密码 7 天</el-checkbox>
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
            <ArrowRight class="button-icon" :size="16" aria-hidden="true" />
            进入控制台
          </el-button>
        </form>

        <footer class="login-card__footer">
          <span>当前身份：</span>
          <strong>{{ activePresetDetail.label }}</strong>
          <em>{{ activePresetDetail.description }}</em>
        </footer>
      </section>
    </main>
  </div>
</template>

<style scoped>
.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 1fr);
  min-height: 100vh;
  width: 100%;
  background: #0f172a;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

/* ──────────────────────────────────────────────
 * 左侧：深紫极光 + 知识图谱装饰
 * ────────────────────────────────────────────── */
.login-aside {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 28px;
  padding: 48px 56px;
  color: #fff;
  overflow: hidden;
  background:
    radial-gradient(circle at 70% 30%, rgba(56, 189, 248, 0.42) 0%, transparent 38%),
    radial-gradient(circle at 30% 70%, rgba(192, 132, 252, 0.36) 0%, transparent 42%),
    radial-gradient(circle at 50% 100%, rgba(236, 72, 153, 0.22) 0%, transparent 50%),
    linear-gradient(160deg, #1e1b4b 0%, #312e81 60%, #4338ca 130%);
}

/* 知识图谱节点装饰 */
.login-aside__graph {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.login-aside__node {
  position: absolute;
  border-radius: 50%;
  background: rgba(56, 189, 248, 0.65);
  box-shadow: 0 0 14px rgba(56, 189, 248, 0.7);
  animation: login-pulse 4s ease-in-out infinite;
}
.login-aside__node--1 { width: 10px; height: 10px; top: 12%; left: 8%; }
.login-aside__node--2 {
  width: 14px; height: 14px; top: 26%; left: 62%;
  background: rgba(192, 132, 252, 0.78);
  box-shadow: 0 0 18px rgba(192, 132, 252, 0.92);
  animation-delay: 0.6s;
}
.login-aside__node--3 {
  width: 12px; height: 12px; top: 56%; left: 78%;
  background: rgba(56, 189, 248, 0.72);
  box-shadow: 0 0 14px rgba(56, 189, 248, 0.85);
  animation-delay: 1.2s;
}
.login-aside__node--4 {
  width: 8px; height: 8px; top: 70%; left: 22%;
  background: rgba(236, 72, 153, 0.85);
  box-shadow: 0 0 14px rgba(236, 72, 153, 0.7);
  animation-delay: 1.8s;
}
.login-aside__node--5 {
  width: 16px; height: 16px; top: 84%; left: 56%;
  background: rgba(99, 102, 241, 0.66);
  box-shadow: 0 0 18px rgba(99, 102, 241, 0.7);
  animation-delay: 2.4s;
}
.login-aside__line {
  position: absolute;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(147, 197, 253, 0.55), transparent);
  overflow: visible;
}
.login-aside__line--1 { width: 44%; top: 15%; left: 12%; transform: rotate(8deg); }
.login-aside__line--2 { width: 24%; top: 36%; left: 60%; transform: rotate(38deg); }
.login-aside__line--3 { width: 32%; top: 73%; left: 24%; transform: rotate(-12deg); }
.login-aside__line--4 { width: 18%; top: 60%; left: 70%; transform: rotate(-22deg); }

/* 流光：脉冲点沿连线移动 */
.login-aside__pulse {
  position: absolute;
  top: 50%;
  left: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(147, 197, 253, 0.95);
  box-shadow: 0 0 14px rgba(147, 197, 253, 0.95);
  transform: translate(-4px, -4px);
  animation: login-pulse-flow 3.6s linear infinite;
}
.login-aside__line--1 .login-aside__pulse { animation-delay: 0s; }
.login-aside__line--2 .login-aside__pulse {
  animation-delay: 1.0s;
  animation-duration: 4.2s;
  background: rgba(216, 180, 254, 0.95);
  box-shadow: 0 0 14px rgba(192, 132, 252, 0.95);
}
.login-aside__line--3 .login-aside__pulse {
  animation-delay: 1.8s;
  animation-duration: 4.6s;
}
.login-aside__line--4 .login-aside__pulse {
  animation-delay: 2.4s;
  animation-duration: 3.2s;
  background: rgba(244, 114, 182, 0.92);
  box-shadow: 0 0 14px rgba(236, 72, 153, 0.95);
}

/* 浅景深漂浮粒子（背景气氛） */
.login-aside__particle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  opacity: 0;
  animation: login-particle-drift 14s ease-in-out infinite;
}
.login-aside__particle--1 {
  width: 4px; height: 4px;
  top: 40%; left: 18%;
  background: rgba(147, 197, 253, 0.7);
  box-shadow: 0 0 10px rgba(147, 197, 253, 0.6);
  animation-delay: 0s;
}
.login-aside__particle--2 {
  width: 3px; height: 3px;
  top: 22%; left: 42%;
  background: rgba(216, 180, 254, 0.7);
  box-shadow: 0 0 8px rgba(192, 132, 252, 0.6);
  animation-delay: 2s;
  animation-duration: 16s;
}
.login-aside__particle--3 {
  width: 5px; height: 5px;
  top: 78%; left: 38%;
  background: rgba(244, 114, 182, 0.6);
  box-shadow: 0 0 10px rgba(236, 72, 153, 0.55);
  animation-delay: 4s;
  animation-duration: 18s;
}
.login-aside__particle--4 {
  width: 2px; height: 2px;
  top: 50%; left: 84%;
  background: rgba(147, 197, 253, 0.85);
  box-shadow: 0 0 6px rgba(147, 197, 253, 0.7);
  animation-delay: 6s;
  animation-duration: 12s;
}
.login-aside__particle--5 {
  width: 4px; height: 4px;
  top: 88%; left: 18%;
  background: rgba(216, 180, 254, 0.65);
  box-shadow: 0 0 8px rgba(192, 132, 252, 0.55);
  animation-delay: 8s;
  animation-duration: 20s;
}
.login-aside__particle--6 {
  width: 3px; height: 3px;
  top: 12%; left: 88%;
  background: rgba(165, 180, 252, 0.7);
  box-shadow: 0 0 8px rgba(99, 102, 241, 0.6);
  animation-delay: 10s;
  animation-duration: 15s;
}

@keyframes login-pulse {
  0%, 100% { opacity: 0.85; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.18); }
}

/* 流光沿线条移动并淡入淡出 */
@keyframes login-pulse-flow {
  0% { left: 0; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { left: 100%; opacity: 0; }
}

/* 漂浮粒子：先升起、左右漂移、再消散 */
@keyframes login-particle-drift {
  0% {
    transform: translate(0, 0) scale(0.6);
    opacity: 0;
  }
  20% {
    opacity: 0.85;
  }
  50% {
    transform: translate(40px, -60px) scale(1);
    opacity: 1;
  }
  80% {
    opacity: 0.6;
  }
  100% {
    transform: translate(-30px, -120px) scale(0.4);
    opacity: 0;
  }
}

/* 整体场景缓慢漂移：极光层呼吸 */
.login-aside::before {
  content: '';
  position: absolute;
  inset: -10%;
  background:
    radial-gradient(circle at 70% 30%, rgba(56, 189, 248, 0.18) 0%, transparent 38%),
    radial-gradient(circle at 30% 70%, rgba(192, 132, 252, 0.16) 0%, transparent 42%);
  pointer-events: none;
  z-index: 0;
  animation: login-aurora-drift 18s ease-in-out infinite;
}

@keyframes login-aurora-drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(2%, -3%) scale(1.06); }
  66% { transform: translate(-2%, 2%) scale(1.03); }
}

/* 用户开启减少动画偏好时全部停下 */
@media (prefers-reduced-motion: reduce) {
  .login-aside__node,
  .login-aside__pulse,
  .login-aside__particle,
  .login-aside::before {
    animation: none !important;
  }
}

/* 左侧 brand */
.login-aside__brand {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 12px;
}
.login-aside__logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  object-fit: contain;
  box-shadow: 0 4px 14px rgba(56, 189, 248, 0.42);
}
.login-aside__brand-text strong {
  display: block;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.login-aside__brand-text span {
  display: block;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.62);
  font-family: 'JetBrains Mono', 'SF Mono', monospace;
  letter-spacing: 0.04em;
}

/* 左侧 hero */
.login-aside__hero {
  position: relative;
  z-index: 1;
  align-self: center;
  max-width: 480px;
}
.login-aside__eyebrow {
  display: inline-block;
  font-size: 10px;
  letter-spacing: 0.32em;
  color: #93c5fd;
  font-weight: 700;
  margin-bottom: 14px;
  text-transform: uppercase;
}
.login-aside__title {
  margin: 0 0 16px;
  font-size: 36px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.01em;
  color: #fff;
}
.login-aside__title em {
  font-style: normal;
  background: linear-gradient(135deg, #38bdf8, #c084fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.login-aside__lede {
  margin: 0 0 28px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(203, 213, 225, 0.92);
  max-width: 380px;
}

/* 生产链路卡片 */
.login-aside__pipeline {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-width: 420px;
}
.login-aside__pipeline-item {
  display: grid;
  grid-template-columns: 24px 26px 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.10);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  transition: background 0.22s ease, border-color 0.22s ease, transform 0.22s ease;
}
.login-aside__pipeline-item:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(147, 197, 253, 0.32);
  transform: translateX(2px);
}
.login-aside__pipeline-step {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(99, 102, 241, 0.32);
  color: #c7d2fe;
  display: grid;
  place-items: center;
  font-size: 10px;
  font-weight: 800;
  font-family: 'JetBrains Mono', monospace;
  border: 1px solid rgba(99, 102, 241, 0.5);
}
.login-aside__pipeline-icon {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: rgba(56, 189, 248, 0.18);
  border: 1px solid rgba(56, 189, 248, 0.32);
  color: #93c5fd;
}
.login-aside__pipeline-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}
.login-aside__pipeline-text strong {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}
.login-aside__pipeline-text small {
  font-size: 10px;
  color: rgba(203, 213, 225, 0.7);
}
.login-aside__pipeline-arrow {
  color: rgba(147, 197, 253, 0.6);
  flex-shrink: 0;
}

/* ──────────────────────────────────────────────
 * 右侧：极光底色 + 乳白卡片
 * ────────────────────────────────────────────── */
.login-main {
  display: grid;
  place-items: center;
  padding: 40px;
  background:
    radial-gradient(circle at 30% 20%, rgba(99, 102, 241, 0.16) 0%, transparent 60%),
    radial-gradient(circle at 80% 80%, rgba(236, 72, 153, 0.12) 0%, transparent 55%),
    linear-gradient(160deg, #1e1b4b 0%, #312e81 100%);
}

/* 乳白卡（B 档：94% 半透 + 20px 模糊） */
.login-card {
  width: 100%;
  max-width: 380px;
  padding: 32px 30px;
  border-radius: 22px;
  background: rgba(252, 252, 254, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.95);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 1),
    0 28px 70px rgba(15, 23, 42, 0.5);
  color: #1e1b4b;
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
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.14), rgba(236, 72, 153, 0.14));
  border: 1px solid rgba(99, 102, 241, 0.18);
  color: #6366f1;
  flex-shrink: 0;
}
.login-card__title {
  margin: 0 0 2px;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: #1e1b4b;
}
.login-card__subtitle {
  margin: 0;
  font-size: 12px;
  color: rgba(30, 27, 75, 0.66);
}

/* 角色 preset */
.login-preset {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-bottom: 20px;
  padding: 4px;
  border: 1px solid rgba(99, 102, 241, 0.14);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.78);
}
.login-preset__tab {
  display: grid;
  gap: 1px;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: transparent;
  color: rgba(30, 27, 75, 0.6);
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
  font-size: 10px;
  color: rgba(30, 27, 75, 0.55);
}
.login-preset__tab:hover {
  background: rgba(99, 102, 241, 0.06);
  color: #1e1b4b;
}
.login-preset__tab.is-active {
  background: #fff;
  color: #1e1b4b;
  border-color: rgba(99, 102, 241, 0.42);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.18);
}
.login-preset__tab.is-active strong {
  color: #6366f1;
}

/* form */
.login-form {
  display: grid;
  gap: 14px;
}
.login-field {
  display: grid;
  gap: 6px;
}
.login-field__label {
  font-size: 12px;
  font-weight: 500;
  color: rgba(30, 27, 75, 0.66);
}
.login-field :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 0 14px;
  background: rgba(248, 250, 252, 0.85);
  box-shadow: 0 0 0 1px #e0e7ff inset;
}
.login-field :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset, 0 0 0 4px rgba(99, 102, 241, 0.12);
  background: #fff;
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
  color: rgba(30, 27, 75, 0.7);
}
.login-help-link {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: #6366f1;
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
  background: color-mix(in srgb, var(--ckqa-danger, #dc2626) 8%, #fff);
  border: 1px solid color-mix(in srgb, var(--ckqa-danger, #dc2626) 32%, transparent);
  color: var(--ckqa-danger, #dc2626);
  font-size: 12px;
}

.login-submit {
  margin-top: 4px;
  height: 44px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: linear-gradient(135deg, #38bdf8 0%, #6366f1 50%, #c084fc 100%) !important;
  border: none !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.36),
    0 8px 24px rgba(99, 102, 241, 0.42) !important;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.login-submit:hover {
  transform: translateY(-1px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.42),
    0 12px 32px rgba(99, 102, 241, 0.5) !important;
}

.login-card__footer {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px dashed rgba(99, 102, 241, 0.14);
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: rgba(30, 27, 75, 0.62);
}
.login-card__footer strong {
  color: #6366f1;
  font-weight: 700;
}
.login-card__footer em {
  font-style: normal;
  color: rgba(30, 27, 75, 0.55);
}

/* ──────────────────────────────────────────────
 * 响应式
 * ────────────────────────────────────────────── */
@media (max-width: 1080px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
  .login-aside {
    padding: 32px 24px;
  }
  .login-aside__title {
    font-size: 28px;
  }
  .login-aside__pipeline {
    max-width: none;
  }
  .login-main {
    padding: 24px 16px 40px;
  }
}

@media (max-width: 720px) {
  .login-aside__pipeline-item {
    grid-template-columns: 22px 24px 1fr;
  }
  .login-aside__pipeline-arrow {
    display: none;
  }
}
</style>
