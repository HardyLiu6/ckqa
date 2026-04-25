<!-- 深色落地页专用壳，不渲染顶栏，让落地页自己掌控首屏 -->
<script setup>
</script>

<template>
  <div class="landing-layout">
    <RouterView />
  </div>
</template>

<style scoped lang="scss">
.landing-layout {
  min-height: 100vh;
  background: #0f0f1a;
  color: #fff;
}
</style>
