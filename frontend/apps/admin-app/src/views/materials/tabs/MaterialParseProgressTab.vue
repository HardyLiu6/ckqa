<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import CkLogStream from '../../../components/common/CkLogStream.vue'
import CkSplitProgress from '../../../components/common/CkSplitProgress.vue'

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
let logSequence = 0

// 把后端 stages 映射为 CkSplitProgress 期望的格式：
// { key, title, state, currentPct, detail }
const stages = computed(() => {
  const raw = snapshot.value?.stages ?? snapshot.value?.progress?.stages
  if (!Array.isArray(raw) || raw.length === 0) return []
  return raw.map((stage) => ({
    key: stage.key ?? stage.name ?? String(stage.label ?? ''),
    title: stage.label ?? stage.name ?? stage.key ?? '',
    state: normalizeStageState(stage),
    currentPct: resolveStagePercent(stage),
    detail: stage.startedAtLabel ?? stage.startedAt ?? stage.detail ?? '',
  }))
})

// 当前激活阶段（第一个 running；否则 stages 末尾）
const activeStage = computed(() => {
  const list = stages.value
  return list.find((s) => s.state === 'running') ?? list.at(-1) ?? null
})

function normalizeStageState(stage) {
  const status = String(stage?.status ?? stage?.state ?? '').toLowerCase()
  if (status === 'done' || status === 'success') return 'done'
  if (status === 'running' || status === 'processing') return 'running'
  if (status === 'failed' || status === 'error') return 'failed'
  if (status === 'skipped') return 'skipped'
  return 'pending'
}

function resolveStagePercent(stage) {
  const pct = Number(stage?.percent ?? stage?.progress)
  if (Number.isFinite(pct)) return pct
  return normalizeStageState(stage) === 'done' ? 100 : 0
}

function appendLog(raw) {
  if (!raw) return
  // 把字符串或对象形式的日志都包成 CkLogStream 的行契约
  const base = typeof raw === 'string' ? { level: 'info', message: raw } : raw
  logs.value.push({
    id: `ppt-${Date.now()}-${logSequence++}`,
    level: base.level ?? 'info',
    message: base.message ?? '',
    timestamp: base.timestamp ?? Date.now(),
  })
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
        appendLog({ level: 'error', message: final?.error || '解析失败' })
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
    <p v-if="showTimeoutBanner" class="material-parse-progress-tab__banner">
      {{ MATERIAL_PAGE_COPY.detail.parseTimeoutBanner }}
    </p>

    <div class="material-parse-progress-tab__body">
      <section class="material-parse-progress-tab__stages">
        <CkEmptyState
          v-if="!stages.length && !connecting"
          icon="·"
          :title="MATERIAL_PAGE_COPY.progress.emptyTitle"
          :description="MATERIAL_PAGE_COPY.progress.emptyDescription"
        />
        <CkSplitProgress
          v-else
          :stages="stages"
          :active-key="activeStage?.key ?? ''"
          :current-pct="activeStage?.currentPct ?? 0"
          :show-summary="true"
        />
      </section>

      <section class="material-parse-progress-tab__log">
        <p v-if="streamError" class="material-parse-progress-tab__error" role="alert">
          {{ streamError?.message || MATERIAL_PAGE_COPY.progress.connectionLost }}
        </p>
        <CkLogStream
          v-else
          :lines="logs"
          :max-height="'360px'"
          :empty-hint="MATERIAL_PAGE_COPY.progress.logPlaceholder"
        />
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.material-parse-progress-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.material-parse-progress-tab__banner {
  margin: 0;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-warning-soft);
  border-left: 3px solid var(--ckqa-warning);
  font-size: var(--ckqa-text-sm-size);
  border-radius: var(--ckqa-radius-md);
}
.material-parse-progress-tab__body {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--ckqa-space-4);
  min-height: 360px;
}
@media (max-width: 960px) {
  .material-parse-progress-tab__body {
    grid-template-columns: 1fr;
  }
}
.material-parse-progress-tab__stages {
  padding: var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
}
.material-parse-progress-tab__log {
  min-width: 0;
}
.material-parse-progress-tab__error {
  color: var(--ckqa-danger);
  background: var(--ckqa-danger-soft);
  padding: var(--ckqa-space-3);
  border-radius: var(--ckqa-radius-md);
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
}
</style>
