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
  path: route.path, meta: route.meta, contextChain: route.meta?.contextChain || [],
}))
const currentUser = computed(() => authStore.state.currentUser)
const dataScopeLabel = computed(() => currentUser.value?.dataScope || '未登录')
function logout() { authStore.logout(); router.push('/login') }
</script>

<template>
  <div class="workflow-layout">
    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :data-scope-label="dataScopeLabel"
      @logout="logout"
    />
    <div class="workflow-layout-body">
      <SideNavigation :sections="sections" :section-labels="sectionLabels" :active-path="activePath" />
      <main class="workflow-layout-main">
        <CkBreadcrumbs v-if="breadcrumbItems.length" :items="breadcrumbItems" />
        <div class="workflow-layout-grid">
          <section class="workflow-layout-form">
            <slot name="form">
              <slot />
            </slot>
          </section>
          <aside class="workflow-layout-live">
            <slot name="live" />
          </aside>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-layout { min-height: 100vh; background: var(--ckqa-bg); color: var(--ckqa-text); }
.workflow-layout-body { display: flex; min-height: calc(100vh - 52px); }
.workflow-layout-main { flex: 1; min-width: 0; padding: 22px 28px 40px; max-width: 1440px; margin: 0 auto; }
.workflow-layout-grid {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: var(--ckqa-space-6);
  margin-top: var(--ckqa-space-4);
}
.workflow-layout-live {
  position: sticky; top: calc(52px + 22px);
  align-self: start;
  max-height: calc(100vh - 52px - 44px);
  overflow-y: auto;
}
@media (max-width: 1280px) {
  .workflow-layout-grid { grid-template-columns: 1fr; }
  .workflow-layout-live { position: static; max-height: none; }
}
</style>
