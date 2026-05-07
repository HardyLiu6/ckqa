<template>
  <div class="auth-layout">
    <div class="auth-layout-glow auth-layout-glow--top" aria-hidden="true" />
    <div class="auth-layout-glow auth-layout-glow--bottom" aria-hidden="true" />
    <main class="auth-layout-content auth-panel">
      <slot />
    </main>
    <footer class="auth-layout-footer">
      <span>CKQA Console</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.auth-layout {
  position: relative; min-height: 100vh;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
  overflow: hidden;
}
.auth-layout-glow {
  position: absolute;
  width: 480px; height: 480px; border-radius: 50%;
  background: radial-gradient(circle, var(--ckqa-accent-soft) 0%, transparent 70%);
  pointer-events: none;
  filter: blur(20px);
  opacity: 0.7;
}
.auth-layout-glow--top    { top: -180px; right: -120px; }
.auth-layout-glow--bottom { bottom: -200px; left: -140px; }
.auth-layout-content { position: relative; z-index: 1; width: min(420px, 92vw); padding: 32px; }
.auth-layout-footer {
  position: absolute; bottom: 18px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
</style>
