<!-- 模块壳：顶栏 + 左侧副导航 + 主内容 -->
<!-- 副导航组件按 route.meta.moduleNav 动态装载 -->
<script setup>
import { computed, defineAsyncComponent, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import { useCurrentModule } from '@/composables/useCurrentModule'

const route = useRoute()
const { moduleKey, colors } = useCurrentModule()

// 侧边栏折叠状态
const sidebarCollapsed = ref(false)
provide('sidebarCollapsed', sidebarCollapsed)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
provide('toggleSidebar', toggleSidebar)

// 按模块懒加载副导航
const sideNavMap = {
  course: defineAsyncComponent(() => import('@/components/module-nav/CourseSideNav.vue')),
  qa: defineAsyncComponent(() => import('@/components/module-nav/QASideNav.vue')),
  knowledge: defineAsyncComponent(() => import('@/components/module-nav/KnowledgeSideNav.vue')),
  user: defineAsyncComponent(() => import('@/components/module-nav/UserSideNav.vue')),
}

const SideNav = computed(() => sideNavMap[moduleKey.value] || null)

// 为主区注入模块色 CSS 变量
const moduleStyle = computed(() => ({
  '--module-color-50': colors.value[50],
  '--module-color-500': colors.value[500],
  '--module-color-700': colors.value[700],
}))
</script>

<template>
  <div class="module-layout" :style="moduleStyle">
    <NavHeader />
    <div class="module-body">
      <aside class="module-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <component :is="SideNav" v-if="SideNav" />
      </aside>
      <main class="module-main">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.module-layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.module-body {
  display: flex;
  padding-top: 64px; // NavHeader
  min-height: 100vh;
}

.module-sidebar {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;
  transition: width $duration-base $ease-out;

  &.collapsed {
    width: 52px;
    overflow: hidden;
  }

  @media (max-width: $bp-laptop) {
    width: 240px;
  }

  @media (max-width: $bp-tablet) {
    display: none;
  }
}

.module-main {
  flex: 1;
  min-width: 0;
  padding: 24px 32px;

  @media (max-width: $bp-tablet) {
    padding: 16px;
  }
}

.page-enter-active,
.page-leave-active {
  transition: opacity $duration-base $ease-out, transform $duration-base $ease-out;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
