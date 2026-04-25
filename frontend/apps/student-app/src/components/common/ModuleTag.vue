<!-- 按模块色渲染的 chip/tag -->
<script setup>
import { computed } from 'vue'
import { MODULE_COLORS } from '@/composables/useCurrentModule'

const props = defineProps({
  module: { type: String, default: 'home' },
  size: { type: String, default: 'md' }, // 'sm' | 'md'
  active: { type: Boolean, default: false },
})

const style = computed(() => {
  const c = MODULE_COLORS[props.module] || MODULE_COLORS.home
  return props.active
    ? {
        background: `linear-gradient(135deg, ${c[500]}, ${c[700]})`,
        color: '#fff',
        border: `1px solid ${c[500]}`,
      }
    : {
        background: c[50],
        color: c[500],
        border: `1px solid rgba(${hexToRgb(c[500])}, 0.25)`,
      }
})

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `${r}, ${g}, ${b}`
}
</script>

<template>
  <span :class="['module-tag', `size-${size}`]" :style="style">
    <slot />
  </span>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.module-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: $radius-full;
  font-weight: 500;
  white-space: nowrap;
}

.size-sm { font-size: 11px; padding: 2px 8px; }
.size-md { font-size: 12px; padding: 4px 12px; }
</style>
