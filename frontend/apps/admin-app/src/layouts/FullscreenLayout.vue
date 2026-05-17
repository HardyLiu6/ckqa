<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'

import AppTopbar from '../components/shell/AppTopbar.vue'
import OfflineBanner from '../components/common/OfflineBanner.vue'
import { API_BASE_URL } from '../axios/index.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const currentUser = computed(() => authStore.state.currentUser)
const dataScopeLabel = computed(() => currentUser.value?.dataScope || '未登录')
const pageTitle = computed(() => route.meta?.title || '')

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
    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :data-scope-label="dataScopeLabel"
      @logout="logout"
    />

    <header class="fullscreen-layout__page-header">
      <button
        type="button"
        class="fullscreen-layout__back-btn"
        :aria-label="'返回上一页'"
        @click="goBack"
      >
        <ArrowLeft :size="16" aria-hidden="true" />
        <span>返回</span>
      </button>
      <h1 v-if="pageTitle" class="fullscreen-layout__title">{{ pageTitle }}</h1>
    </header>

    <main id="main-content" class="fullscreen-layout__content">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.fullscreen-layout {
  display: grid;
  grid-template-rows: auto auto 1fr;
  min-height: 100vh;
  background: var(--ckqa-surface-base, var(--ckqa-surface));
}

.fullscreen-layout__page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 32px 0;
  max-width: 1180px;
  margin: 0 auto;
  width: 100%;
}

.fullscreen-layout__back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  height: 32px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full, 999px);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  transition: border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}
.fullscreen-layout__back-btn:hover {
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent-strong);
  transform: translateX(-2px);
}
.fullscreen-layout__back-btn:focus-visible {
  outline: 2px solid var(--ckqa-accent);
  outline-offset: 2px;
}

.fullscreen-layout__title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: var(--ckqa-text);
}

.fullscreen-layout__content {
  padding: 0 32px 32px;
  max-width: 1180px;
  margin: 0 auto;
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
  .fullscreen-layout__page-header,
  .fullscreen-layout__content {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
