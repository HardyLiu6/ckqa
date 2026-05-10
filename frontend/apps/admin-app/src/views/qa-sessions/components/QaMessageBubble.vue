<script setup>
import { computed } from 'vue'

import CkStatusPill from '../../../components/common/CkStatusPill.vue'
import { QA_SESSION_PAGE_COPY } from '../qa-session-copy.js'

const props = defineProps({
  // 消息对象，字段对齐 QaMessageResponse：
  //   id / role('user'|'assistant') / content / createdAt / taskStatus / progressStage / retrievalTrace
  message: { type: Object, required: true },
  // 右栏当前锁定的 AI 消息 id；assistant 气泡上的"查看检索过程"按钮据此显示是否选中
  isActive: { type: Boolean, default: false },
  // 是否可点击"查看检索过程"，由父级基于 retrievalTrace 字段是否存在决定
  // 若 false，按钮禁用并显示兜底 tooltip
  canInspect: { type: Boolean, default: false },
})

const emit = defineEmits(['select-for-diagnosis'])

const role = computed(() => {
  const raw = String(props.message?.role ?? '').toLowerCase()
  return raw === 'assistant' ? 'assistant' : 'user'
})

const roleLabel = computed(() =>
  role.value === 'assistant'
    ? QA_SESSION_PAGE_COPY.message.assistantRole
    : QA_SESSION_PAGE_COPY.message.userRole,
)

const content = computed(() => String(props.message?.content ?? ''))
const createdAt = computed(() => props.message?.createdAt ?? '')

const taskStatus = computed(() => {
  const status = String(props.message?.taskStatus ?? '').toLowerCase()
  if (!status) return null
  if (status === 'failed' || status === 'error') return { tone: 'danger', label: '失败' }
  if (status === 'running' || status === 'processing') return { tone: 'running', label: '生成中' }
  if (status === 'completed' || status === 'success' || status === 'done') return null // 成功态不冗余展示
  return { tone: 'neutral', label: String(props.message.taskStatus) }
})

function handleInspect() {
  if (!props.canInspect) return
  emit('select-for-diagnosis', props.message?.id)
}
</script>

<template>
  <article
    class="qa-message-bubble"
    :class="{ 'is-active': isActive && role === 'assistant' }"
    :data-role="role"
    :data-active="isActive && role === 'assistant' ? 'true' : null"
    :data-testid="`qa-message-bubble-${message.id}`"
  >
    <header class="qa-message-bubble__meta">
      <strong>{{ roleLabel }}</strong>
      <CkStatusPill v-if="taskStatus" :tone="taskStatus.tone" :label="taskStatus.label" size="sm" />
      <time v-if="createdAt">{{ createdAt }}</time>
    </header>

    <div class="qa-message-bubble__content">{{ content }}</div>

    <footer
      v-if="role === 'assistant'"
      class="qa-message-bubble__footer"
    >
      <button
        type="button"
        class="qa-message-bubble__inspect ck-pressable"
        :disabled="!canInspect"
        :title="canInspect ? '' : QA_SESSION_PAGE_COPY.detail.viewDiagnosisDisabled"
        :data-testid="`qa-message-inspect-${message.id}`"
        @click="handleInspect"
      >
        {{ QA_SESSION_PAGE_COPY.detail.viewDiagnosisCta }} →
      </button>
    </footer>
  </article>
</template>

<style scoped lang="scss">
.qa-message-bubble {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  border-radius: var(--ckqa-radius-lg);
  border: 1px solid var(--ckqa-border-soft);
  background: var(--ckqa-surface);
  max-width: min(680px, 92%);
  position: relative;
}
.qa-message-bubble[data-role='user'] {
  align-self: flex-end;
  background: var(--ckqa-surface-muted);
}
.qa-message-bubble[data-role='assistant'] {
  align-self: flex-start;
  background: var(--ckqa-bg-elevated);
  border-color: var(--ckqa-border);
}
.qa-message-bubble.is-active {
  border-color: var(--ckqa-accent);
  box-shadow: 0 0 0 2px var(--ckqa-accent-soft);
}
.qa-message-bubble__meta {
  display: flex;
  align-items: center;
  gap: var(--ckqa-space-2);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.qa-message-bubble__meta strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
  font-size: var(--ckqa-text-sm-size);
}
.qa-message-bubble__meta time {
  margin-left: auto;
}
.qa-message-bubble__content {
  font-size: var(--ckqa-text-sm-size);
  line-height: 1.7;
  color: var(--ckqa-text);
  white-space: pre-wrap;
  word-break: break-word;
}
.qa-message-bubble__footer {
  display: flex;
  justify-content: flex-end;
}
.qa-message-bubble__inspect {
  padding: 4px 10px;
  border-radius: var(--ckqa-radius-full);
  border: 1px solid var(--ckqa-border);
  background: transparent;
  color: var(--ckqa-accent-strong);
  font-size: var(--ckqa-text-xs-size);
  cursor: pointer;
}
.qa-message-bubble__inspect:hover:not(:disabled) {
  background: var(--ckqa-accent-soft);
  border-color: var(--ckqa-accent);
}
.qa-message-bubble__inspect:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
