<script setup>
// CkQuickActions —— 工作台快捷入口卡片化展示。
// 视觉打磨迭代（2026-05-09）新增。
// Props 契约（严格遵循 design doc §9.3）：
//   actions: Array<{ id, label, hint, icon, to }>
// Events: 'select' —— 点击后冒泡，便于上层做埋点

import { useRouter } from 'vue-router'

const props = defineProps({
  actions: {
    type: Array,
    required: true,
  },
})

const emit = defineEmits(['select'])

const router = useRouter()

// 9 种图标 glyph（用 unicode 符号兜底，真正的线性 SVG 由 P4a 的 sidebar 图标库接入）
// 这里保留组件 props.icon 字段以便后续切换到 SVG 名字映射时无需改接口
const ICON_GLYPHS = {
  book: '📘',
  file: '📄',
  database: '🗄',
  shield: '🛡',
  dashboard: '◧',
  chat: '💬',
  list: '☰',
  users: '👥',
  heart: '♥',
}

function renderIcon(name) {
  return ICON_GLYPHS[name] || '▸'
}

function go(action) {
  emit('select', action)
  if (action.to) router.push(action.to)
}
</script>

<template>
  <div class="ck-quick-actions" role="list">
    <button
      v-for="action in actions"
      :key="action.id"
      class="ck-glass-card ck-pressable ck-quick-action"
      type="button"
      role="listitem"
      :aria-label="action.label"
      @click="go(action)"
    >
      <span class="ck-quick-action-icon" aria-hidden="true">{{ renderIcon(action.icon) }}</span>
      <span class="ck-quick-action-label">{{ action.label }}</span>
      <span class="ck-quick-action-hint">{{ action.hint }}</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.ck-quick-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.ck-quick-action {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  background: var(--ckqa-surface-glass);
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 14px;
  text-align: left;
  cursor: pointer;
  color: inherit;
  font: inherit;
}

.ck-quick-action:focus-visible {
  outline: none;
  box-shadow: var(--ckqa-focus-ring);
}

.ck-quick-action-icon {
  position: relative;
  z-index: 1;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
  font-size: 16px;
  line-height: 1;
}

.ck-quick-action-label {
  position: relative;
  z-index: 1;
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}

.ck-quick-action-hint {
  position: relative;
  z-index: 1;
  font-size: var(--ckqa-text-sm-size);
  line-height: var(--ckqa-text-sm-line);
  color: var(--ckqa-text-muted);
}
</style>
