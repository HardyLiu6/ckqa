<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import CandidateMatrixRow from './CandidateMatrixRow.vue'
import ScoringRankingTable from './ScoringRankingTable.vue'
import ScoringDetailDrawer from './ScoringDetailDrawer.vue'
import { formatTokens, formatDuration } from './scoring-format-model.js'
import {
  startExtractionEval,
  getExtractionEvalStatus,
  getExtractionEvalReport,
  cancelExtractionEval,
} from '../../../api/prompt-tune-pipeline.js'

const props = defineProps({
  dirty: { type: Boolean, default: false },
})

const emit = defineEmits(['enter-save', 'back', 'select-candidate'])

const route = useRoute()

// 业务码常量
const CODE_AUDIT_NOT_COMPLETED = 4104
const CODE_CANDIDATES_NOT_GENERATED = 4105
const CODE_EVAL_NOT_STARTED = 4106
const CODE_INVALID_CANDIDATE_SELECTION = 4108

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

const selectedCandidatesFromQuery = computed(() => {
  const raw = route.query.selectedCandidates
  if (!raw) return []
  return String(raw).split(',').map((s) => s.trim()).filter(Boolean)
})

// 五态：loading / blocked / running / done / failed
const phase = ref('loading')
const errorMessage = ref('')
const blockedReason = ref('')

// running 态数据
const evalRunId = ref(null)
const status = ref(null) // 整个 ExtractionEvalStatusResponse
let pollTimer = null

// done 态数据
const report = ref(null) // ExtractionEvalReportResponse
const detailOpen = ref(false)
const detailCandidate = ref(null)
const highlightedId = ref('')
const selectedId = ref('')

const overall = computed(() => status.value?.overall ?? null)
const matrixCandidates = computed(() => status.value?.candidates ?? [])
const reportCandidates = computed(() => report.value?.candidates ?? [])
/** 风险 1：未进入排行榜的失败候选清单，由后端 report.failedCandidates 透传。 */
const failedCandidates = computed(() => report.value?.failedCandidates ?? [])

onMounted(initialize)

onBeforeUnmount(() => {
  stopPolling()
})

async function initialize() {
  if (!buildRunId.value) {
    phase.value = 'failed'
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }

  phase.value = 'loading'
  // 1. 先看是否已有 active / 历史评分
  try {
    const s = await getExtractionEvalStatus(buildRunId.value)
    handleStatusUpdate(s)
    return // 有现成任务（pending/running/success/failed），不再触发新任务
  } catch (err) {
    if (err?.code !== CODE_EVAL_NOT_STARTED) {
      phase.value = 'failed'
      errorMessage.value = err?.message ?? '加载评分进度失败'
      return
    }
    // 4106 → 没有任务，下一步检查 selectedCandidates 看是否要立即触发
  }

  // 2. 没有现成任务：必须有 selectedCandidates 才触发，否则提示用户回 03 步
  if (selectedCandidatesFromQuery.value.length === 0) {
    phase.value = 'blocked'
    blockedReason.value = '请先在 03 步勾选要评分的候选 Prompt'
    return
  }

  await triggerNewEval(selectedCandidatesFromQuery.value)
}

async function triggerNewEval(selectedIds) {
  phase.value = 'loading'
  try {
    const started = await startExtractionEval(buildRunId.value, {
      selectedCandidates: selectedIds,
    })
    evalRunId.value = started.evalRunId
    // 立即拉一次 status 然后开始轮询
    const s = await getExtractionEvalStatus(buildRunId.value)
    handleStatusUpdate(s)
  } catch (err) {
    if (err?.code === CODE_AUDIT_NOT_COMPLETED) {
      phase.value = 'blocked'
      blockedReason.value = '请先完成 02 步至少 1 条样本审阅'
    } else if (err?.code === CODE_CANDIDATES_NOT_GENERATED) {
      phase.value = 'blocked'
      blockedReason.value = '请先在 03 步生成候选 Prompt'
    } else if (err?.code === CODE_INVALID_CANDIDATE_SELECTION) {
      phase.value = 'blocked'
      blockedReason.value = '所选候选 ID 与当前构建不匹配，请回 03 步重选'
    } else {
      phase.value = 'failed'
      errorMessage.value = err?.message ?? '触发评分任务失败'
    }
  }
}

function handleStatusUpdate(s) {
  status.value = s
  evalRunId.value = s.evalRunId

  switch (s.status) {
    case 'success':
      phase.value = 'done'
      stopPolling()
      loadReport()
      break
    case 'failed':
      phase.value = 'failed'
      errorMessage.value = s.errorMessage ?? '评分任务执行失败'
      stopPolling()
      break
    case 'cancelled':
      phase.value = 'failed'
      errorMessage.value = '评分任务已取消'
      stopPolling()
      break
    case 'pending':
    case 'running':
    case 'cancelling':
      phase.value = 'running'
      ensurePolling(s.recommendedPollingIntervalMillis ?? 1500)
      break
    default:
      phase.value = 'running'
      ensurePolling(1500)
  }
}

function ensurePolling(intervalMs) {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    try {
      const s = await getExtractionEvalStatus(buildRunId.value)
      handleStatusUpdate(s)
    } catch (err) {
      // 轮询失败时降级：不弹 toast 避免刷屏，下一次自动重试
      // eslint-disable-next-line no-console
      console.warn('[scoring] 轮询失败，将在下一周期重试', err)
    }
  }, intervalMs)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function loadReport() {
  try {
    report.value = await getExtractionEvalReport(buildRunId.value)
    // 默认选中排名第一
    const top = reportCandidates.value.find((c) => c.rank === 1)
    if (top && !selectedId.value) selectedId.value = top.candidateId
  } catch (err) {
    phase.value = 'failed'
    errorMessage.value = err?.message ?? '加载评分报告失败'
  }
}

async function handleAbort() {
  try {
    await ElMessageBox.confirm('中止评分会丢失当前进度，确定吗？', '中止评分', { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await cancelExtractionEval(buildRunId.value)
    ElMessage.info('已请求中止，评分将在当前候选完成后停止')
    // 不立即停止轮询，让 worker 自感知后写 cancelled 终态
  } catch (err) {
    ElMessage.error(err?.message ?? '中止失败')
  }
}

function handleViewDetail(candidateId) {
  highlightedId.value = candidateId
  detailCandidate.value = reportCandidates.value.find((c) => c.candidateId === candidateId) ?? null
  detailOpen.value = true
}

function handleSelectCandidate(candidateId) {
  selectedId.value = candidateId
  emit('select-candidate', candidateId)
  const candidate = reportCandidates.value.find((c) => c.candidateId === candidateId)
  ElMessage.success(`已选定：${candidate?.displayNameZh ?? candidateId}`)
}

function handleEnterSave() {
  if (!selectedId.value) {
    ElMessage.warning('请先在排行榜操作列点击"选定"')
    return
  }
  emit('enter-save', selectedId.value)
}

async function handleRetry() {
  // 失败态点重试：检查 selectedCandidates 重新触发
  if (selectedCandidatesFromQuery.value.length === 0) {
    emit('back')
    return
  }
  await triggerNewEval(selectedCandidatesFromQuery.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-scoring">
    <header class="prompt-builder-step__header">
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>抽取评分</h3>
        <p>在校准集上跑候选提示词，按综合分排序选出最佳候选。</p>
      </div>
      <div class="prompt-builder-step__header-right">
        <el-tag v-if="dirty" type="warning" size="small" effect="light">已修改未保存</el-tag>
        <el-tag v-else type="success" size="small" effect="light">已是最新</el-tag>
      </div>
    </header>

    <!-- loading -->
    <div v-if="phase === 'loading'" class="scoring-state-card">
      <span>正在加载评分任务...</span>
    </div>

    <!-- blocked（02/03 门控失败 / 缺少 selectedCandidates） -->
    <div v-else-if="phase === 'blocked'" class="scoring-state-card scoring-state-card--blocked">
      <p>{{ blockedReason }}</p>
      <el-button @click="$emit('back')">返回 03 步</el-button>
    </div>

    <!-- failed（含 cancelled） -->
    <div v-else-if="phase === 'failed'" class="scoring-state-card scoring-state-card--error">
      <p>{{ errorMessage }}</p>
      <div class="scoring-state-card__actions">
        <el-button @click="handleRetry">重试</el-button>
        <el-button @click="$emit('back')">返回 03 步</el-button>
      </div>
    </div>

    <!-- running -->
    <template v-else-if="phase === 'running'">
      <div class="scoring-progress-summary">
        <div>
          <div class="scoring-progress-summary__metric">
            <strong>{{ overall?.finishedCalls ?? 0 }}</strong> / {{ overall?.totalCalls ?? 0 }}
          </div>
          <div class="ann-text-tiny">
            大模型调用 · 已用时 {{ formatDuration(overall?.elapsedSeconds ?? 0) }}
            <template v-if="overall?.estimatedRemainingSeconds != null">
              · 预估剩余 {{ Math.ceil((overall.estimatedRemainingSeconds ?? 0) / 60) }} min
            </template>
          </div>
        </div>
        <div class="scoring-progress-summary__divider"></div>
        <div>
          <div class="scoring-progress-summary__metric">
            ~ <strong>{{ formatTokens(overall?.tokensUsed ?? 0) }}</strong>
          </div>
          <div class="ann-text-tiny">
            已消耗 token · 预估总量 {{ formatTokens(overall?.estimatedTotalTokens ?? 0) }}
          </div>
        </div>
        <div class="scoring-progress-summary__abort">
          <el-button @click="handleAbort">中止评分</el-button>
        </div>
      </div>

      <div class="scoring-matrix">
        <CandidateMatrixRow
          v-for="(c, i) in matrixCandidates"
          :key="c.candidateId"
          :candidate="{ candidateId: c.candidateId, displayNameZh: c.displayNameZh }"
          :progress="{
            candidateId: c.candidateId,
            status: c.status,
            extractDone: c.extract?.finished ?? 0,
            extractEstimated: c.extract?.estimated === true,
            scoringStartedAtMs: null,
          }"
          :index="i"
        />
      </div>
    </template>

    <!-- done -->
    <template v-else-if="phase === 'done'">
      <ScoringRankingTable
        :candidates="reportCandidates"
        :selected-candidate-id="selectedId"
        :highlighted-candidate-id="highlightedId"
        @select-candidate="handleSelectCandidate"
        @view-detail="handleViewDetail"
      />

      <!-- 风险 1：失败候选不进入排行榜，单独成块；report.failedCandidates 非空时才渲染 -->
      <section v-if="failedCandidates.length > 0" class="scoring-failed-candidates">
        <header class="scoring-failed-candidates__header">未进入排名（{{ failedCandidates.length }}）</header>
        <ul class="scoring-failed-candidates__list">
          <li
            v-for="f in failedCandidates"
            :key="f.candidateId"
            class="scoring-failed-candidates__item"
          >
            <strong>{{ f.displayNameZh || f.candidateId }}</strong>
            <span class="ann-text-tiny">{{ f.stage === 'extract' ? '抽取阶段' : '评分阶段' }}</span>
            <span class="ann-text-tiny ann-text-tiny--danger">{{ f.reason }}</span>
          </li>
        </ul>
      </section>

      <ScoringDetailDrawer
        v-model="detailOpen"
        :candidate="detailCandidate"
        :is-selected="detailCandidate?.candidateId === selectedId"
      />

      <footer class="scoring-bottom-bar">
        <div class="scoring-bottom-bar__info">
          <template v-if="selectedId">
            已选定：<strong>{{
              reportCandidates.find((c) => c.candidateId === selectedId)?.displayNameZh
            }}</strong>
          </template>
          <template v-else>尚未选定候选</template>
        </div>
        <div class="scoring-bottom-bar__actions">
          <el-button type="primary" :disabled="!selectedId" @click="handleEnterSave"
            >进入预览 →</el-button
          >
        </div>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.scoring-state-card {
  padding: 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  color: var(--ckqa-text-muted, #78716c);
}
.scoring-state-card--blocked {
  color: var(--ckqa-warning, #d97706);
}
.scoring-state-card--error {
  color: var(--ckqa-danger, #dc2626);
}
.scoring-state-card__actions {
  display: flex;
  gap: 8px;
}

.scoring-failed-candidates {
  margin-top: 24px;
  padding: 16px;
  border: 1px dashed var(--ckqa-border, #d6d3d1);
  border-radius: 8px;
  background: var(--ckqa-bg-soft, #fafaf9);
}
.scoring-failed-candidates__header {
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--ckqa-warning, #d97706);
}
.scoring-failed-candidates__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.scoring-failed-candidates__item {
  display: flex;
  gap: 16px;
  align-items: center;
}
.ann-text-tiny--danger {
  color: var(--ckqa-danger, #dc2626);
}
</style>
