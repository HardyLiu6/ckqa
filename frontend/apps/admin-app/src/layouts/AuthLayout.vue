<template>
  <main class="auth-layout">
    <slot />
  </main>
</template>

<style scoped>
.auth-layout {
  /* AuthLayout 不再套居中卡片，把视图本身完整铺满全屏。
     LoginView / 其它认证视图自带背景与版面。 */
  display: block;
  min-height: 100vh;
  background: transparent;
}
</style>
