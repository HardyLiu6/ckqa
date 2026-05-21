<!-- 模块壳：顶栏 + 左侧副导航 + 主内容 -->
<!-- 副导航组件按 route.meta.moduleNav 动态装载 -->
<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import { useCurrentModule } from '@/composables/useCurrentModule'
import { moduleSideNavLoaders } from '@/layouts/moduleSideNavLoaders'
import { resolveRouteViewKey } from '@/layouts/route-view-key'

const route = useRoute()
const { moduleKey, colors } = useCurrentModule()

// 按模块懒加载副导航；loader 同时供顶栏预加载复用。
const sideNavMap = Object.fromEntries(
  Object.entries(moduleSideNavLoaders).map(([key, loader]) => [key, defineAsyncComponent(loader)]),
)

const SideNav = computed(() => sideNavMap[moduleKey.value] || null)
const routeViewKey = computed(() => resolveRouteViewKey(route))

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
      <aside class="module-sidebar">
        <component :is="SideNav" v-if="SideNav" />
      </aside>
      <main class="module-main">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="routeViewKey" />
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
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;

  @media (max-width: $bp-laptop) {
    width: 60px; // 只显示图标
  }

  @media (max-width: $bp-tablet) {
    // 改为抽屉，实际抽屉交互在 NavHeader 里触发
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
