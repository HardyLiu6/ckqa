<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, LogOut } from 'lucide-vue-next'

import OfflineBanner from '../components/common/OfflineBanner.vue'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const currentUser = computed(() => authStore.state.currentUser)
const pageTitle = computed(() => route.meta?.title || '')
const userLabel = computed(
  () => currentUser.value?.displayName || currentUser.value?.username || '',
)

function logout() {
  authStore.logout()
  router.push('/login')
}

function goBack() {
  // 优先回退到来源页；没有 history 时退回工作台兜底。
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/app/dashboard')
}
</script>

<template>
  <div class="fullscreen-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <OfflineBanner />

    <!-- 极简顶部栏：仅返回 + 标题 + 退出，不渲染主 Topbar -->
    <header class="fullscreen-layout__bar">
      <div class="fullscreen-layout__bar-inner">
        <button
          type="button"
          class="fullscreen-layout__back-btn"
          :aria-label="'返回上一页'"
          @click="goBack"
        >
          <ArrowLeft :size="16" aria-hidden="true" />
          <span>返回</span>
        </button>

        <div class="fullscreen-layout__title-block">
          <h1 v-if="pageTitle" class="fullscreen-layout__title">{{ pageTitle }}</h1>
        </div>

        <div class="fullscreen-layout__bar-meta">
          <span v-if="userLabel" class="fullscreen-layout__user" :title="userLabel">
            {{ userLabel }}
          </span>
          <button
            type="button"
            class="fullscreen-layout__logout"
            aria-label="退出登录"
            title="退出登录"
            @click="logout"
          >
            <LogOut :size="14" aria-hidden="true" />
          </button>
        </div>
      </div>
    </header>

    <main id="main-content" class="fullscreen-layout__content">
      <div class="fullscreen-layout__inner">
        <slot />
      </div>
    </main>
  </div>
</template>

<style scoped>
.fullscreen-layout {
  display: grid;
  grid-template-rows: 56px 1fr;
  height: 100vh;
  background:
    radial-gradient(circle at 0% 0%, color-mix(in srgb, var(--ckqa-accent) 12%, transparent), transparent 38%),
    radial-gradient(circle at 100% 100%, color-mix(in srgb, var(--ckqa-accent) 8%, transparent), transparent 32%),
    var(--ckqa-bg);
  overflow: hidden;
}

.fullscreen-layout__bar {
  border-bottom: 1px solid var(--ckqa-border);
  background: color-mix(in srgb, var(--ckqa-surface) 92%, transparent);
  backdrop-filter: blur(12px);
}

.fullscreen-layout__bar-inner {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 16px;
  height: 100%;
  max-width: 1180px;
  padding: 0 32px;
  margin: 0 auto;
}

.fullscreen-layout__back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  height: 34px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full, 999px);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  transition: border-color 0.18s ease, color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
  white-space: nowrap;
}
.fullscreen-layout__back-btn:hover {
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent-strong);
  transform: translateX(-2px);
  box-shadow: 0 2px 8px color-mix(in srgb, var(--ckqa-accent) 18%, transparent);
}
.fullscreen-layout__back-btn:focus-visible {
  outline: 2px solid var(--ckqa-accent);
  outline-offset: 2px;
}

.fullscreen-layout__title-block {
  text-align: center;
  min-width: 0;
}
.fullscreen-layout__title {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
  color: var(--ckqa-text);
  letter-spacing: 0.02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fullscreen-layout__bar-meta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  justify-content: flex-end;
}

.fullscreen-layout__user {
  font-size: 12px;
  color: var(--ckqa-text-muted);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fullscreen-layout__logout {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full, 999px);
  background: var(--ckqa-surface);
  color: var(--ckqa-text-muted);
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background 0.18s ease;
}
.fullscreen-layout__logout:hover {
  border-color: var(--ckqa-danger, #dc2626);
  color: var(--ckqa-danger, #dc2626);
  background: color-mix(in srgb, var(--ckqa-danger, #dc2626) 8%, var(--ckqa-surface));
}

.fullscreen-layout__content {
  min-height: 0;
  overflow-y: auto;
}

.fullscreen-layout__inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px;
  width: 100%;
}

.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
}
.skip-link:focus {
  position: fixed;
  left: 12px;
  top: 12px;
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  padding: 6px 12px;
  border-radius: 6px;
  z-index: 1000;
  border: 2px solid var(--ckqa-accent);
}

@media (max-width: 720px) {
  .fullscreen-layout__bar-inner,
  .fullscreen-layout__inner {
    padding-left: 16px;
    padding-right: 16px;
  }
  .fullscreen-layout__user {
    display: none;
  }
}
</style>
