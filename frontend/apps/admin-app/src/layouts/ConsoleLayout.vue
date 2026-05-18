<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu, X, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'

import AppTopbar from '../components/shell/AppTopbar.vue'
import OfflineBanner from '../components/common/OfflineBanner.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import { buildNavigationGroups } from '../components/shell/navigation-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { buildConsoleBreadcrumbItems } from './console-breadcrumb-model.js'
import { routeRecords } from '../router/routes.js'
import { authStore } from '../stores/auth.js'
import { layoutStore } from '../stores/layout.js'

const route = useRoute()
const router = useRouter()

const navigationGroups = computed(() => buildNavigationGroups(routeRecords, authStore.canAccess))
const activeGroup = computed(() => route.meta.navGroup)
const breadcrumbItems = computed(() => buildConsoleBreadcrumbItems(route))
const currentUser = computed(() => authStore.state.currentUser)
const dataScopeLabel = computed(() => currentUser.value?.dataScope || '未登录')

// 响应式布局状态（综合视口 + 用户手动折叠）
const sidebarMode = computed(() => layoutStore.state.sidebarMode)
const isMobileMenuOpen = computed(() => layoutStore.state.isMobileMenuOpen)

// 根据 sidebarMode 动态设置 grid-template-columns
const layoutGridColumns = computed(() => {
  if (sidebarMode.value === 'full') return '256px minmax(0, 1fr)'
  if (sidebarMode.value === 'icon') return '76px minmax(0, 1fr)'
  return 'minmax(0, 1fr)' // hidden 模式：侧边导航隐藏
})

onMounted(() => {
  layoutStore.initLayout()
})

onUnmounted(() => {
  layoutStore.destroy()
})

function logout() {
  authStore.logout()
  router.push('/login')
}

function toggleMobileMenu() {
  layoutStore.toggleMobileMenu()
}

function closeMobileMenu() {
  if (isMobileMenuOpen.value) {
    layoutStore.toggleMobileMenu()
  }
}

function toggleCollapse() {
  layoutStore.toggleCollapse()
}
</script>

<template>
  <div
    class="console-layout"
    :style="{ gridTemplateColumns: layoutGridColumns }"
  >
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <OfflineBanner />
    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :data-scope-label="dataScopeLabel"
      @logout="logout"
    >
      <!-- 汉堡菜单按钮：仅在 hidden 模式下显示 -->
      <template v-if="sidebarMode === 'hidden'" #prepend>
        <button
          class="hamburger-btn"
          aria-label="打开导航菜单"
          @click="toggleMobileMenu"
        >
          <Menu :size="22" aria-hidden="true" />
        </button>
      </template>
    </AppTopbar>

    <!-- 桌面/平板侧边导航：full 或 icon 模式 -->
    <SideNavigation
      v-if="sidebarMode !== 'hidden'"
      :groups="navigationGroups"
      :active-group="activeGroup || ''"
      :current-path="route.path"
      :compact="sidebarMode === 'icon'"
      class="side-navigation--responsive"
      @toggle-collapse="toggleCollapse"
    />

    <!-- 移动端 overlay 导航菜单 -->
    <Teleport to="body">
      <Transition name="nav-collapse">
        <div
          v-if="sidebarMode === 'hidden' && isMobileMenuOpen"
          class="mobile-nav-overlay"
          @click.self="closeMobileMenu"
        >
          <aside class="mobile-nav-drawer">
            <div class="mobile-nav-header">
              <span class="mobile-nav-title">导航</span>
              <button
                class="mobile-nav-close"
                aria-label="关闭导航菜单"
                @click="closeMobileMenu"
              >
                <X :size="20" aria-hidden="true" />
              </button>
            </div>
            <SideNavigation
              :groups="navigationGroups"
              :active-group="activeGroup || ''"
              :current-path="route.path"
              class="mobile-nav-content"
              @click="closeMobileMenu"
            />
          </aside>
        </div>
      </Transition>
    </Teleport>

    <main id="main-content" class="workspace">
      <div class="workspace-heading">
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
      </div>
      <slot />
    </main>
  </div>
</template>
