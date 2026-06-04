<!-- 亮色业务页默认壳：顶栏 + 主内容 -->
<script setup>
import NavHeader from '@/components/NavHeader.vue'
</script>

<template>
  <div class="product-layout">
    <NavHeader />
    <main class="product-main">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;

.product-layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.product-main {
  padding-top: 64px; // NavHeader 高度
  min-height: 100vh;
  box-sizing: border-box;
  overflow-x: hidden;
}

// 路由切换过渡
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
