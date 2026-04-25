<!-- 通用毛玻璃卡片容器 · 支持三档档位 + 深色变体 + 模块色边框 -->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  tier: {
    type: String,
    default: 'base', // 'light' | 'base' | 'strong'
    validator: (v) => ['light', 'base', 'strong'].includes(v),
  },
  dark: { type: Boolean, default: false }, // 深色场景用
  padding: { type: String, default: 'md' }, // 'none' | 'sm' | 'md' | 'lg'
  hover: { type: Boolean, default: false }, // 是否开启 hover 抬升
})

const classes = computed(() => [
  'glass-card',
  `tier-${props.tier}`,
  `padding-${props.padding}`,
  { dark: props.dark, hoverable: props.hover },
])
</script>

<template>
  <div :class="classes">
    <slot />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/shadow' as *;
@use '@/styles/tokens/motion' as *;

.glass-card {
  border-radius: $radius-xl;
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;
}

.tier-light { @include glass.glass-light; }
.tier-base { @include glass.glass-base; }
.tier-strong { @include glass.glass-strong; }

.dark.tier-light { @include glass.glass-dark-light; }
.dark.tier-base { @include glass.glass-dark-base; }
.dark.tier-strong { @include glass.glass-dark-strong; }

.padding-none { padding: 0; }
.padding-sm { padding: 12px; }
.padding-md { padding: 20px; }
.padding-lg { padding: 28px; }

.hoverable {
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-md;
    border-color: rgba(var(--module-color-500-rgb, 99, 102, 241), 0.4);
  }
}
</style>
