<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'

import { useQaSessionMessages } from '../../composables/useQaSessionMessages.js'
import {
  buildQaSessionContextChain,
  buildRetrievalAvailabilityMap,
  resolveDefaultActiveMessageId,
} from './qa-session-detail-model.js'
import { QA_SESSION_PAGE_COPY } from './qa-session-copy.js'

import QaMessageStream from './components/QaMessageStream.vue'
import QaRetrievalPanelPlaceholder from './components/QaRetrievalPanelPlaceholder.vue'
import QaSessionHeader from './components/QaSessionHeader.vue'

// M6a：问答会话详情页双栏骨架
// - 左：QaMessageStream 消息流 + 气泡
// - 右：QaRetrievalPanelPlaceholder（M6b 后端 retrievalTrace 字段就绪后会接入 CkRetrievalPanel）
// - 数据：useQaSessionMessages 合并加载 + 5s 轮询；terminal 自动停止
// - activeMessageId 默认锁定到最新一条 AI 回答，用户点气泡里"查看检索过程"可切换

const route = useRoute()
const sessionId = computed(() => route.params.sessionId)

const { state, start } = useQaSessionMessages({ sessionId: sessionId.value })

// 由用户操作或自动推导出的活跃消息 id；null 代表"未选中"
const activeMessageId = ref(null)

// 首次消息加载后自动选中最新 assistant；已选中用户手动的消息后不再自动覆盖
const userHasSelected = ref(false)

watch(
  () => state.messages,
  (messages) => {
    if (userHasSelected.value) return
    const next = resolveDefaultActiveMessageId(messages)
    if (next != null) activeMessageId.value = next
  },
  { deep: true },
)

// 会话基础信息变化时同步面包屑 contextChain
watch(
  () => state.session,
  (session) => {
    if (!session) return
    route.meta.contextChain = buildQaSessionContextChain(session)
  },
  { immediate: false },
)

watch(sessionId, (next) => {
  if (!next) return
  userHasSelected.value = false
  activeMessageId.value = null
  start(next)
})

onMounted(() => {
  if (!sessionId.value) return
  start(sessionId.value)
})

// 传给 QaMessageStream 用的 id → 是否可检索 Map
const retrievalAvailability = computed(() => buildRetrievalAvailabilityMap(state.messages))

function handleSelectForDiagnosis(id) {
  if (id == null) return
  userHasSelected.value = true
  activeMessageId.value = id
}
</script>

<template>
  <div class="qa-session-detail-page" data-testid="qa-session-detail-page">
    <CkSkeleton v-if="state.loading && !state.session" variant="card" :count="1" />

    <CkEmptyState
      v-else-if="state.error && !state.session"
      icon="!"
      :title="QA_SESSION_PAGE_COPY.detail.loadError"
      :description="state.error?.message || QA_SESSION_PAGE_COPY.detail.sessionNotFound"
    />

    <template v-else-if="state.session">
      <QaSessionHeader :session="state.session" />

      <div class="qa-session-detail-page__columns">
        <section class="qa-session-detail-page__messages" aria-label="消息流">
          <QaMessageStream
            :messages="state.messages"
            :active-message-id="activeMessageId"
            :loading="state.loading"
            :polling-mode="state.pollingMode"
            :retrieval-availability="retrievalAvailability"
            @select-for-diagnosis="handleSelectForDiagnosis"
          />
        </section>
        <aside class="qa-session-detail-page__panel" aria-label="检索诊断面板">
          <!--
            M6a：固定显示占位。
            M6b 合并后，这里会根据 activeMessage.retrievalTrace 在 CkRetrievalPanel 与
            QaRetrievalPanelPlaceholder 之间切换。
          -->
          <QaRetrievalPanelPlaceholder />
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.qa-session-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.qa-session-detail-page__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ckqa-space-5);
  align-items: flex-start;
}
.qa-session-detail-page__messages {
  min-width: 0;
}
.qa-session-detail-page__panel {
  position: sticky;
  top: 92px; // topbar + breadcrumb 留白
  align-self: flex-start;
  max-height: calc(100vh - 92px - 24px);
  overflow-y: auto;
}
@media (max-width: 1280px) {
  .qa-session-detail-page__columns {
    grid-template-columns: 1fr;
  }
  .qa-session-detail-page__panel {
    position: static;
    max-height: none;
  }
}
</style>
