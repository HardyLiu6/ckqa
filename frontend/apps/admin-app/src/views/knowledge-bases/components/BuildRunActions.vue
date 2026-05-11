<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 当前阶段 key，用于在事件里回传
  stageKey: { type: String, default: '' },
  // 当前 buildRun 状态：running / failed / done 等
  status: { type: String, default: 'idle' },
  // 是否允许当前用户控制构建（只读运维时为 false）
  canAct: { type: Boolean, default: true },
})

const emit = defineEmits(['retry', 'skip', 'cancel'])

const normalized = computed(() => String(props.status ?? '').toLowerCase())

// 按 status 决定显示哪些按钮：
// - running / processing / indexing → 仅 "取消构建"
// - failed → 重试 / 跳过 / 取消
// - 其它（done / idle）→ 不渲染按钮组，由上游容器决定
const buttons = computed(() => {
  if (!props.canAct) return []
  if (normalized.value === 'failed') {
    return [
      { key: 'retry', label: '重试当前阶段', variant: 'primary' },
      { key: 'skip', label: '跳过当前阶段', variant: 'ghost' },
      { key: 'cancel', label: '取消构建', variant: 'danger' },
    ]
  }
  if (['running', 'processing', 'indexing'].includes(normalized.value)) {
    return [{ key: 'cancel', label: '取消构建', variant: 'danger' }]
  }
  return []
})

function handleClick(key) {
  if (key === 'retry') emit('retry', props.stageKey)
  else if (key === 'skip') emit('skip', props.stageKey)
  else if (key === 'cancel') emit('cancel')
}
</script>

<template>
  <div v-if="buttons.length" class="build-run-actions" data-testid="build-run-actions">
    <button
      v-for="btn in buttons"
      :key="btn.key"
      type="button"
      class="ck-pressable"
      :class="`is-${btn.variant}`"
      @click="handleClick(btn.key)"
    >
      {{ btn.label }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.build-run-actions {
  display: flex;
  gap: var(--ckqa-space-2);
  flex-wrap: wrap;
}
.build-run-actions button {
  padding: 6px 14px;
  font-size: var(--ckqa-text-sm-size);
  border-radius: var(--ckqa-radius-md);
  border: 1px solid var(--ckqa-border-strong);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  cursor: pointer;
}
.build-run-actions button.is-primary {
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  border-color: var(--ckqa-accent-strong);
}
.build-run-actions button.is-primary:hover {
  background: var(--ckqa-accent-strong);
}
.build-run-actions button.is-ghost:hover {
  background: var(--ckqa-surface-muted);
}
.build-run-actions button.is-danger {
  color: var(--ckqa-danger);
  border-color: var(--ckqa-danger);
}
.build-run-actions button.is-danger:hover {
  background: var(--ckqa-danger-soft);
}
</style>
