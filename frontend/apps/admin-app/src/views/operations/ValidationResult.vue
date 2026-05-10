<script setup>
/**
 * 知识库验证页 · 右侧结果子组件（M7 · 任务 5.3 拆分产物）。
 *
 * 职责：
 * - 按 `runState` 四态切换：
 *   - `idle`：`CkEmptyState`；
 *   - `running`：`CkSplitProgress`（stages 基于 `STAGES`）；
 *   - `success`：答复文本 + `timings` + `CkLogStream`（sources 作为日志行）；
 *   - `failed`：错误面板 + 「重新发起」按钮（`authStore.canAccess(['qa:write'])` 守护）。
 * - 所有文案走 `KB_VALIDATION_COPY / RESULT_COPY / STATE_LABELS`。
 *
 * 与父组件通过 props 下发 `runState / runSnapshot / selectedIndexRunId`，
 * 通过 `@retry` 事件冒泡「重新发起」操作。
 */
import { computed } from 'vue'

import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkLogStream from '../../components/common/CkLogStream.vue'
import CkSplitProgress from '../../components/common/CkSplitProgress.vue'
import { useAuthStore } from '../../stores/auth.js'
import {
  KB_VALIDATION_COPY,
  RESULT_COPY,
  STAGES,
  stateLabel,
} from './kb-validation-copy.js'

const props = defineProps({
  runState: { type: String, default: 'idle' },
  runSnapshot: { type: Object, default: null },
  selectedIndexRunId: { type: [Number, String, null], default: null },
})

const emit = defineEmits(['retry'])

const authStore = useAuthStore()

const stageItems = computed(() => STAGES.map((stage, index) => ({
  key: stage.key,
  title: stage.label,
  state: resolveStageState(props.runState, index),
})))

const activeStageKey = computed(() => (
  props.runState === 'running' ? (STAGES[0]?.key ?? '') : ''
))

const sourceLines = computed(() => {
  const sources = props.runSnapshot?.sources
  if (!Array.isArray(sources)) return []
  return sources.map((item, index) => ({
    id: `kb-validation-source-${index}`,
    level: 'info',
    message: formatSourceLine(item),
  }))
})

function handleRetry() { emit('retry') }

function resolveStageState(current, index) {
  if (current === 'running') return index === 0 ? 'running' : 'pending'
  if (current === 'success') return 'done'
  if (current === 'failed') return index === STAGES.length - 1 ? 'failed' : 'done'
  return 'pending'
}

function formatSourceLine(item) {
  if (typeof item === 'string') return item
  if (item && typeof item === 'object') {
    const title = item.title ?? ''
    const snippet = item.snippet ?? ''
    if (title && snippet) return `${title} · ${snippet}`
    if (title) return String(title)
    if (snippet) return String(snippet)
    try { return JSON.stringify(item) } catch { return '' }
  }
  return ''
}

// 保持 stateLabel 对外可见（历史表 / 页头由父组件复用）；此处仅做导入保留。
void stateLabel
</script>

<template>
  <article
    class="kb-validation-result"
    data-testid="kb-validation-result"
    :data-index-run-id="selectedIndexRunId ?? undefined"
    :data-run-state="runState"
  >
    <h3 class="kb-validation-result__section-title">{{ RESULT_COPY.title }}</h3>

    <CkEmptyState
      v-if="runState === 'idle'"
      icon="◻"
      :title="KB_VALIDATION_COPY.empty.title"
      :description="KB_VALIDATION_COPY.empty.description"
      data-testid="kb-validation-empty"
    />

    <CkSplitProgress
      v-else-if="runState === 'running'"
      :stages="stageItems"
      :active-key="activeStageKey"
      :current-pct="0"
      :show-summary="false"
      data-testid="kb-validation-progress"
    />

    <div
      v-else-if="runState === 'success'"
      class="kb-validation-result__answer"
      data-testid="kb-validation-answer"
    >
      <h4 class="kb-validation-result__sub-title">{{ RESULT_COPY.answerTitle }}</h4>
      <p class="kb-validation-result__answer-text">{{ runSnapshot?.answer || '-' }}</p>
      <dl v-if="runSnapshot?.timings" class="kb-validation-result__timings">
        <div>
          <dt>{{ RESULT_COPY.timingsTitle }} · 检索</dt>
          <dd>{{ runSnapshot.timings.retrievalMs ?? '-' }} ms</dd>
        </div>
        <div>
          <dt>{{ RESULT_COPY.timingsTitle }} · 生成</dt>
          <dd>{{ runSnapshot.timings.generationMs ?? '-' }} ms</dd>
        </div>
      </dl>
      <section class="kb-validation-result__sources">
        <h5 class="kb-validation-result__sub-title">{{ RESULT_COPY.sourcesTitle }}</h5>
        <CkLogStream
          :lines="sourceLines"
          :empty-hint="RESULT_COPY.sourcesTitle"
          density-compact
          data-testid="kb-validation-logs"
        />
      </section>
    </div>

    <div
      v-else
      class="kb-validation-result__error"
      role="alert"
      data-testid="kb-validation-error"
    >
      <h4 class="kb-validation-result__sub-title">{{ RESULT_COPY.errorTitle }}</h4>
      <p class="kb-validation-result__error-message">{{ runSnapshot?.errorMessage || '-' }}</p>
      <el-button
        v-if="authStore.canAccess(['qa:write'])"
        type="primary"
        data-testid="kb-validation-retry"
        @click="handleRetry"
      >
        {{ RESULT_COPY.retryAction }}
      </el-button>
    </div>
  </article>
</template>

<style scoped lang="scss">
.kb-validation-result {
  padding: var(--ckqa-space-5);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.kb-validation-result__section-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.kb-validation-result__sub-title {
  margin: 0 0 var(--ckqa-space-2);
  font-size: var(--ckqa-text-sm-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text-muted);
}
.kb-validation-result__answer {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.kb-validation-result__answer-text {
  margin: 0;
  padding: var(--ckqa-space-3);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-sm-size);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.kb-validation-result__timings {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
  margin: 0;
}
.kb-validation-result__timings dt {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  margin-bottom: 2px;
}
.kb-validation-result__timings dd {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
}
.kb-validation-result__sources {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.kb-validation-result__error {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-danger);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-danger-soft);
  color: var(--ckqa-danger);
}
.kb-validation-result__error-message {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  line-height: 1.6;
  color: var(--ckqa-text);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
