<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import CkBreadcrumbs from '../components/common/CkBreadcrumbs.vue'

import { buildNavigationSections, findActiveNavigationPath } from '../components/shell/navigation-model.js'
import { primaryNavigation, NAV_SECTIONS } from '../router/routes.js'
import { buildConsoleBreadcrumbItems } from './console-breadcrumb-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const sections = computed(() => buildNavigationSections(primaryNavigation, authStore.canAccess))
const sectionLabels = computed(() => {
  const map = {}
  for (const section of NAV_SECTIONS) {
    if (section.label) map[section.key] = section.label
  }
  return map
})
const activePath = computed(() => findActiveNavigationPath(sections.value, route.path))
const breadcrumbItems = computed(() => buildConsoleBreadcrumbItems({
  path: route.path,
  meta: route.meta,
  contextChain: route.meta?.contextChain || [],
}))
const currentUser = computed(() => authStore.state.currentUser)
const dataScopeLabel = computed(() => currentUser.value?.dataScope || '未登录')
function logout() { authStore.logout(); router.push('/login') }
</script>

<template>
  <div class="detail-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :data-scope-label="dataScopeLabel"
      @logout="logout"
    />
    <div class="detail-layout-body">
      <SideNavigation :sections="sections" :section-labels="sectionLabels" :active-path="activePath" />
      <main id="main-content" class="detail-layout-main">
        <CkBreadcrumbs v-if="breadcrumbItems.length" :items="breadcrumbItems" />
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.detail-layout { min-height: 100vh; background: var(--ckqa-bg); color: var(--ckqa-text); }
.detail-layout-body { display: flex; min-height: calc(100vh - 52px); }
.detail-layout-main { flex: 1; min-width: 0; padding: 22px 28px 40px; max-width: 1280px; margin: 0 auto; }
.skip-link { position: absolute; top: -40px; left: 8px; padding: 6px 10px; background: var(--ckqa-surface); border-radius: 6px; }
.skip-link:focus { top: 8px; }
</style>
