<script setup>
import { computed } from 'vue'
import { useThemeStore } from '../../stores/theme.js'

const theme = useThemeStore()
const isDark = computed(() => theme.state.resolvedTheme === 'dark')

function toggle() {
  const next = isDark.value ? 'light' : 'dark'
  theme.setMode(next)
}
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="isDark ? '切换到亮色' : '切换到暗色'"
    :title="isDark ? '切换到亮色' : '切换到暗色'"
    @click="toggle"
  >
    <span aria-hidden="true">{{ isDark ? '☀' : '◐' }}</span>
  </button>
</template>

<style scoped lang="scss">
.theme-toggle {
  width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text-muted);
  font-size: 14px;
  cursor: pointer;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.theme-toggle:hover { background: var(--ckqa-surface-muted); color: var(--ckqa-text); }
.theme-toggle:focus-visible { outline: none; box-shadow: var(--ckqa-focus-ring); }
</style>
