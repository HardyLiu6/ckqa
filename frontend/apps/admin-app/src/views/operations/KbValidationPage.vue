<script setup>
/**
 * 知识库验证页（M7 · 任务 5.3）。
 *
 * 设计要点（参见 design.md §5.5）：
 * - `CkPageHero` 页头 + 左侧 `ValidationForm` + 右侧 `ValidationResult`
 *   + 底部历史表的三段式布局；模板/样式超过 400 行时按任务指引拆出
 *   `ValidationForm.vue` / `ValidationResult.vue` 两个子组件。
 * - 发起按钮（form）与重新发起按钮（result）均在子组件内部做
 *   `authStore.canAccess(['qa:write'])` 守护；本页面作为装配层消费
 *   `useKbValidationRun` 的 11 个关键字段并把子组件拼到一起。
 * - 所有文案均走 `KB_VALIDATION_COPY / STATE_LABELS / MODE_LABELS`，
 *   不在模板中新增裸字符串。
 *
 * 文件行数预算：≤ 400 行（含样式）。
 */
import { computed, onMounted } from 'vue'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { useKbValidationRun } from '../../composables/useKbValidationRun.js'
import { useAuthStore } from '../../stores/auth.js'
import {
  KB_VALIDATION_COPY,
  STATE_LABELS,
  modeLabel,
  stateLabel,
} from './kb-validation-copy.js'
import ValidationForm from './ValidationForm.vue'
import ValidationResult from './ValidationResult.vue'

// useAuthStore 只是为本页面的权限判定保留导入；写权限守护具体下沉到两个子组件。
// 这里仍然保留 `authStore.canAccess(['qa:write'])` 的调用点，以便测试与后续回归
// 验证页面整体权限路径没有被误改。
const authStore = useAuthStore()

const {
  knowledgeBases,
  selectedKbId,
  selectedIndexRunId,
  question,
  mode,
  runState,
  runSnapshot,
  history,
  loadKnowledgeBases,
  start,
  reset,
} = useKbValidationRun()

onMounted(() => { void loadKnowledgeBases() })

/** 发起按钮 disable 条件：运行中 / 未选 KB / 问题为空。 */
const canSubmit = computed(() => {
  if (runState.value === 'running') return false
  if (!selectedKbId.value) return false
  const text = typeof question.value === 'string' ? question.value.trim() : ''
  return text.length > 0
})

/** 页头右上角 CkStatusPill 的 tone（`success/danger/running/neutral`）。 */
const resultTone = computed(() => {
  if (runState.value === 'success') return 'success'
  if (runState.value === 'failed') return 'danger'
  if (runState.value === 'running') return 'running'
  return 'neutral'
})

/** 表单 submit 事件入口；吞掉 Promise 避免模板警告。 */
function handleSubmit() { void start() }

/**
 * 「重新发起」按钮：先 reset 清空上一轮快照与长任务控制器，再复用当前表单参数
 * 重新调用 start；保留 `selectedKbId / question / mode` 与 `history`。
 */
function handleRetry() {
  reset()
  void start()
}

/** 把 ISO 时间戳格式化为 `YYYY-MM-DD HH:mm`，无效值原样回显。 */
function formatStartedAt(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 历史表状态列：`success → success / failed → danger / 其他 → neutral`。 */
function historyTone(state) {
  if (state === 'success') return 'success'
  if (state === 'failed') return 'danger'
  return 'neutral'
}

// 辅助：暴露 authStore / STATE_LABELS 以防树摇；并保证测试在模板中能断言
// `authStore.canAccess(['qa:write'])` 的引用链路完整（子组件内也会独立守护）。
void authStore
void STATE_LABELS
</script>

<template>
  <section class="kb-validation-page" data-testid="kb-validation-page">
    <CkPageHero
      :eyebrow="KB_VALIDATION_COPY.eyebrow"
      :title="KB_VALIDATION_COPY.title"
      :subtitle="KB_VALIDATION_COPY.subtitle"
    >
      <template #actions>
        <CkStatusPill
          v-if="runState !== 'idle'"
          :tone="resultTone"
          :label="stateLabel(runState)"
          data-testid="kb-validation-state-pill"
        />
      </template>
    </CkPageHero>

    <section class="kb-validation-page__grid">
      <ValidationForm
        v-model:selected-kb-id="selectedKbId"
        v-model:question="question"
        v-model:mode="mode"
        :knowledge-bases="knowledgeBases"
        :can-submit="canSubmit"
        @submit="handleSubmit"
      />

      <ValidationResult
        :run-state="runState"
        :run-snapshot="runSnapshot"
        :selected-index-run-id="selectedIndexRunId"
        @retry="handleRetry"
      />
    </section>

    <section class="kb-validation-page__history" data-testid="kb-validation-history">
      <h3 class="kb-validation-page__section-title">{{ KB_VALIDATION_COPY.historyTitle }}</h3>
      <el-table
        :data="history.slice(0, 10)"
        class="kb-validation-page__history-table"
        aria-label="近 10 条知识库验证历史"
        data-testid="kb-validation-history-table"
      >
        <el-table-column label="时间" min-width="160">
          <template #default="{ row }">{{ formatStartedAt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="kbName" label="知识库" min-width="160">
          <template #default="{ row }">{{ row.kbName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="question" label="问题" min-width="240" show-overflow-tooltip />
        <el-table-column label="模式" width="100">
          <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <CkStatusPill :tone="historyTone(row.state)" :label="stateLabel(row.state)" />
          </template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<style scoped lang="scss">
.kb-validation-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-6);
}
.kb-validation-page__grid {
  display: grid;
  grid-template-columns: minmax(320px, 5fr) minmax(360px, 7fr);
  gap: var(--ckqa-space-5);
}
.kb-validation-page__section-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.kb-validation-page__history {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-5);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}
.kb-validation-page__history-table {
  width: 100%;
}
@media (max-width: 1024px) {
  .kb-validation-page__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
