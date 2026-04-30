<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import { buildNavigationGroups } from '../components/shell/navigation-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { routeRecords } from '../router/routes.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const navigationGroups = computed(() => buildNavigationGroups(routeRecords, authStore.canAccess))
const activeGroup = computed(() => route.meta.navGroup)
const currentUser = computed(() => authStore.state.currentUser)
const dataScopeLabel = computed(() => currentUser.value?.dataScope || '未登录')

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="console-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :data-scope-label="dataScopeLabel"
      @logout="logout"
    />

    <SideNavigation
      :groups="navigationGroups"
      :active-group="activeGroup || ''"
      :current-path="route.path"
    />

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
