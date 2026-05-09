<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import {
  createMaterialParseStreamToken,
  openMaterialParseEventStream,
} from '../../../api/materials.js'
import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

const props = defineProps({
  material: { type: Object, required: true },
})

const snapshot = ref(null)
const logs = ref([])
const streamError = ref(null)
const showTimeoutBanner = ref(false)
const connecting = ref(false)

let streamHandle = null

// 解析阶段渲染：优先使用后端下发的 stages；否则根据 material.parseProgress 合成
const stages = computed(() => {
  const raw = snapshot.value?.stages ?? snapshot.value?.progress?.stages
  if (Array.isArray(raw) && raw.length > 0) return raw
  return []
})

function appendLog(line) {
  if (!line) return
  logs.value.push(line)
  if (logs.value.length > 500) {
    logs.value.splice(0, logs.value.length - 500)
  }
}

function applySnapshot(next) {
  if (!next) return
  snapshot.value = next
  if (next.message) appendLog(next.message)
  if (next.timeoutRetried) showTimeoutBanner.value = true
}

async function connect() {
  if (!props.material?.id) return
  connecting.value = true
  try {
    const token = await createMaterialParseStreamToken(props.material.id)
    if (!token?.token) {
      streamError.value = { message: '无法获取解析事件 token' }
      return
    }

    streamHandle = openMaterialParseEventStream(props.material.id, {
      token: token.token,
      onSnapshot: applySnapshot,
      onDone: (final) => {
        applySnapshot(final)
        appendLog('解析已完成。')
      },
      onFailed: (final) => {
        applySnapshot(final)
        streamError.value = { message: final?.error || '解析失败' }
      },
      onError: () => {
        streamError.value = { message: MATERIAL_PAGE_COPY.progress.connectionLost }
      },
    })
  } catch (error) {
    streamError.value = error
  } finally {
    connecting.value = false
  }
}

onMounted(connect)
onBeforeUnmount(() => {
  streamHandle?.close?.()
  streamHandle = null
})
</script>

<template>
  <div class="material-parse-progress-tab" data-testid="material-parse-progress-tab">
    <p v-if="showTimeoutBanner" class="material-parse-progress-tab-banner">
      {{ MATERIAL_PAGE_COPY.detail.parseTimeoutBanner }}
    </p>

    <div class="material-parse-progress-tab-body">
      <div class="material-parse-progress-tab-stages">
        <CkEmptyState
          v-if="!stages.length && !connecting"
          icon="·"
          :title="MATERIAL_PAGE_COPY.progress.emptyTitle"
          :description="MATERIAL_PAGE_COPY.progress.emptyDescription"
        />
        <ol v-else class="stage-list" aria-label="解析阶段">
          <li
            v-for="stage in stages"
            :key="stage.key || stage.name"
            :class="['stage', `tone-${String(stage.status || 'pending').toLowerCase()}`]"
          >
            <span class="stage-label">{{ stage.label || stage.name || stage.key }}</span>
            <small v-if="stage.startedAtLabel || stage.startedAt">
              {{ stage.startedAtLabel || stage.startedAt }}
            </small>
          </li>
        </ol>
      </div>

      <div class="material-parse-progress-tab-log" aria-live="polite">
        <p v-if="streamError" class="material-parse-progress-tab-log-error">
          {{ streamError?.message || MATERIAL_PAGE_COPY.progress.connectionLost }}
        </p>
        <ul v-else-if="logs.length">
          <li v-for="(line, idx) in logs" :key="idx">{{ line }}</li>
        </ul>
        <p v-else class="material-parse-progress-tab-log-placeholder">
          {{ MATERIAL_PAGE_COPY.progress.logPlaceholder }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.material-parse-progress-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.material-parse-progress-tab-banner {
  margin: 0;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-warning-soft);
  border-left: 3px solid var(--ckqa-warning);
  font-size: var(--ckqa-text-sm-size);
  border-radius: var(--ckqa-radius-md);
}
.material-parse-progress-tab-body {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--ckqa-space-4);
  min-height: 360px;
}
@media (max-width: 960px) {
  .material-parse-progress-tab-body {
    grid-template-columns: 1fr;
  }
}
.stage-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.stage {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.stage.tone-running {
  border-color: var(--ckqa-accent);
  background: var(--ckqa-accent-soft);
}
.stage.tone-success,
.stage.tone-done {
  border-color: var(--ckqa-success);
}
.stage.tone-failed,
.stage.tone-error {
  border-color: var(--ckqa-danger);
}
.stage small {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
.material-parse-progress-tab-log {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  padding: var(--ckqa-space-3);
  font-family: var(--ckqa-font-mono);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  max-height: 360px;
  overflow-y: auto;
}
.material-parse-progress-tab-log-error {
  color: var(--ckqa-danger);
  margin: 0;
}
.material-parse-progress-tab-log-placeholder {
  margin: 0;
  color: var(--ckqa-text-weak);
}
.material-parse-progress-tab-log ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.material-parse-progress-tab-log li {
  padding: 2px 0;
}
</style>
