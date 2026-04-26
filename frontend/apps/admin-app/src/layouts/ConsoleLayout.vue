<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { authStore } from '../stores/auth.js'
import { primaryNavigation } from '../router/routes.js'

const route = useRoute()
const router = useRouter()

const visibleNavigation = computed(() =>
  primaryNavigation.filter((item) => authStore.canAccess(item.permissions)),
)

const activeGroup = computed(() => route.meta.navGroup)
const currentUser = computed(() => authStore.state.currentUser)

function switchRole(event) {
  authStore.loginAs(event.target.value)

  if (!authStore.canAccess(route.meta.permissions)) {
    router.push('/app/dashboard')
  }
}

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="console-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <header class="topbar">
      <RouterLink class="brand" to="/app/dashboard" aria-label="返回工作台">
        <span class="brand-mark">CK</span>
        <span>
          <strong>CKQA 运维台</strong>
          <small>课程知识库构建与运维平台</small>
        </span>
      </RouterLink>

      <div class="topbar-actions">
        <div class="runtime-chip" title="当前请求基线">
          <span>API</span>
          <strong>/api/v1</strong>
        </div>
        <RouterLink class="health-pill" to="/app/system">系统健康</RouterLink>
        <label class="role-switch">
          <span>身份</span>
          <select :value="currentUser?.role" @change="switchRole">
            <option value="admin">平台管理员</option>
            <option value="teacher">教师</option>
          </select>
        </label>
        <button class="plain-button" type="button" @click="logout">退出</button>
      </div>
    </header>

    <aside class="sidebar" aria-label="一级导航">
      <nav class="primary-nav">
        <RouterLink
          v-for="item in visibleNavigation"
          :key="item.key"
          class="nav-link"
          :class="{ active: item.key === activeGroup }"
          :to="item.path"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.description }}</small>
        </RouterLink>
      </nav>

      <div class="scope-panel">
        <span>数据范围</span>
        <strong>{{ currentUser?.dataScope }}</strong>
      </div>
    </aside>

    <main id="main-content" class="workspace">
      <div class="workspace-heading">
        <div>
          <p class="breadcrumb">{{ route.meta.navGroup || 'app' }}</p>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <span class="status-badge" :data-status="route.meta.status">{{ route.meta.status }}</span>
      </div>
      <slot />
    </main>
  </div>
</template>
