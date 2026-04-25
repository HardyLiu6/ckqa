<!-- 模块色荧光按钮 · 替代部分 el-button 主按钮场景 -->
<script setup>
const props = defineProps({
  variant: { type: String, default: 'primary' }, // 'primary' | 'secondary' | 'ghost'
  size: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg'
  block: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['click'])
</script>

<template>
  <button
    :class="['glow-btn', `variant-${variant}`, `size-${size}`, { block, disabled }]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot name="prefix" />
    <span class="btn-text"><slot /></span>
    <slot name="suffix" />
  </button>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.glow-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: $radius-lg;
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:active {
    transform: translateY(0) scale(0.98);
    transition-duration: $duration-instant;
  }

  &.disabled {
    opacity: 0.5;
    cursor: not-allowed;
    pointer-events: none;
  }

  &.block { width: 100%; }
}

.size-sm { height: 32px; padding: 0 14px; font-size: 12px; }
.size-md { height: 40px; padding: 0 20px; font-size: 14px; }
.size-lg { height: 48px; padding: 0 28px; font-size: 16px; }

.variant-primary {
  background: linear-gradient(135deg, var(--module-color-500, #6366f1), var(--module-color-700, #4338ca));
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.5), 0 0 0 3px rgba(99, 102, 241, 0.15);
  }
}

.variant-secondary {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  color: var(--module-color-500, #6366f1);
  border: 1px solid rgba(99, 102, 241, 0.3);

  &:hover {
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
  }
}

.variant-ghost {
  background: transparent;
  color: var(--module-color-500, #6366f1);

  &:hover {
    background: rgba(99, 102, 241, 0.08);
  }
}
</style>
