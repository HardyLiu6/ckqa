<!-- 骨架屏方块 · shimmer 动画 -->
<script setup>
defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '16px' },
  rounded: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg' | 'full'
})
</script>

<template>
  <div class="skeleton-block" :class="`rounded-${rounded}`" :style="{ width, height }"></div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.skeleton-block {
  background: linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.rounded-sm { border-radius: $radius-sm; }
.rounded-md { border-radius: $radius-md; }
.rounded-lg { border-radius: $radius-lg; }
.rounded-full { border-radius: $radius-full; }

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
