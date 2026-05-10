<script setup>
import { computed } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import CkSkeleton from '../../../components/common/CkSkeleton.vue'

import QaMessageBubble from './QaMessageBubble.vue'
import { QA_SESSION_PAGE_COPY } from '../qa-session-copy.js'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  // 右栏锁定的 AI 消息 id；可为 null（尚未选中）
  activeMessageId: { type: [Number, String, null], default: null },
  loading: { type: Boolean, default: false },
  // 'idle' | 'polling'；polling 时顶部显示"正在接收新消息…"
  pollingMode: { type: String, default: 'idle' },
  // 由父级传入的 "id → 是否有 retrievalTrace" map；
  // M6a 默认为空 Map（全不可检查），M6b 会传入真实数据
  retrievalAvailability: { type: [Map, Object], default: () => new Map() },
})

const emit = defineEmits(['select-for-diagnosis'])

const isEmpty = computed(() => !props.loading && (!props.messages || props.messages.length === 0))

function isActive(id) {
  if (props.activeMessageId == null) return false
  return String(props.activeMessageId) === String(id)
}

function canInspect(message) {
  if (!message) return false
  if (String(message.role).toLowerCase() !== 'assistant') return false
  const availability = props.retrievalAvailability
  if (availability instanceof Map) return availability.get(String(message.id)) === true
  if (availability && typeof availability === 'object') return availability[String(message.id)] === true
  return false
}

function handleSelect(id) {
  emit('select-for-diagnosis', id)
}
</script>

<template>
  <section class="qa-message-stream" aria-label="问答消息流" data-testid="qa-message-stream">
    <div v-if="pollingMode === 'polling'" class="qa-message-stream__polling" aria-live="polite">
      <span class="qa-message-stream__pulse" aria-hidden="true"></span>
      {{ QA_SESSION_PAGE_COPY.detail.pollingHint }}
    </div>

    <CkSkeleton v-if="loading && messages.length === 0" variant="row" :count="3" />

    <CkEmptyState
      v-else-if="isEmpty"
      icon="◻"
      :title="QA_SESSION_PAGE_COPY.detail.emptyTitle"
      :description="QA_SESSION_PAGE_COPY.detail.emptyDescription"
    />

    <ol v-else class="qa-message-stream__list">
      <li v-for="message in messages" :key="message.id">
        <QaMessageBubble
          :message="message"
          :is-active="isActive(message.id)"
          :can-inspect="canInspect(message)"
          @select-for-diagnosis="handleSelect"
        />
      </li>
    </ol>
  </section>
</template>

<style scoped lang="scss">
.qa-message-stream {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  min-height: 360px;
}
.qa-message-stream__polling {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  align-self: flex-start;
  border-radius: var(--ckqa-radius-full);
  background: var(--ckqa-running-soft);
  color: var(--ckqa-running);
  font-size: var(--ckqa-text-xs-size);
}
.qa-message-stream__pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: qa-stream-pulse 1.4s ease-in-out infinite;
}
@keyframes qa-stream-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(0.6); opacity: 0.5; }
}
.qa-message-stream__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.qa-message-stream__list li {
  display: flex;
  flex-direction: column;
}
@media (prefers-reduced-motion: reduce) {
  .qa-message-stream__pulse { animation: none; }
}
</style>
