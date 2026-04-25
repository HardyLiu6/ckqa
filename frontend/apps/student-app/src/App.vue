<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { userLoadingStore } from './stores'

const userLoading = userLoadingStore()
const route = useRoute()

// 按 route.meta.layout 选择外壳
const LandingLayout = defineAsyncComponent(() => import('@/layouts/LandingLayout.vue'))
const ProductLayout = defineAsyncComponent(() => import('@/layouts/ProductLayout.vue'))
const ModuleLayout = defineAsyncComponent(() => import('@/layouts/ModuleLayout.vue'))

const layoutComponent = computed(() => {
  const layout = route.meta.layout
  if (layout === 'landing') return LandingLayout
  if (layout === 'module') return ModuleLayout
  // 默认 product
  return ProductLayout
})
</script>

<template>
  <component :is="layoutComponent" v-loading.fullscreen.lock="userLoading.loading" />
</template>

<style>
/* 全局样式由 styles/index.scss 注入，这里保持干净 */
</style>
