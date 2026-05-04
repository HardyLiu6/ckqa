<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import { NAV_GROUPS, buildNavigationGroups } from '../components/shell/navigation-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { routeRecords } from '../router/routes.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const LIST_ROUTE_BY_GROUP = {
  courses: { label: '课程列表', name: 'courses', to: '/app/courses' },
  knowledge: { label: '知识库列表', name: 'knowledge-bases', to: '/app/knowledge-bases' },
  qa: { label: '问答会话', name: 'qa-sessions', to: '/app/qa-sessions' },
  users: { label: '用户列表', name: 'users', to: '/app/users' },
  system: { label: '系统健康', name: 'health', to: '/app/health' },
}

const navigationGroups = computed(() => buildNavigationGroups(routeRecords, authStore.canAccess))
const activeGroup = computed(() => route.meta.navGroup)
const breadcrumbItems = computed(() => {
  const group = NAV_GROUPS.find((item) => item.key === route.meta.navGroup)
  const items = []

  if (group) {
    items.push({ label: group.label, kind: 'section' })
  }

  const listRoute = LIST_ROUTE_BY_GROUP[route.meta.navGroup]
  if (listRoute && route.name !== listRoute.name) {
    items.push({ ...listRoute, kind: 'link' })
  }

  items.push({ label: route.meta.title || '当前页面', kind: 'current' })

  return items
})
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
          <nav class="breadcrumb" aria-label="面包屑导航">
            <ol class="breadcrumb-list">
              <li
                v-for="item in breadcrumbItems"
                :key="`${item.kind}-${item.label}`"
                class="breadcrumb-item"
                :data-kind="item.kind"
              >
                <RouterLink v-if="item.to" :to="item.to">{{ item.label }}</RouterLink>
                <span v-else>{{ item.label }}</span>
              </li>
            </ol>
          </nav>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <span class="status-badge" :data-status="route.meta.status">{{ route.meta.status }}</span>
      </div>
      <slot />
    </main>
  </div>
</template>
