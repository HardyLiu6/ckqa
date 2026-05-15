<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CandidateMatrixRow from './CandidateMatrixRow.vue'
import ScoringRankingTable from './ScoringRankingTable.vue'
import ScoringDetailDrawer from './ScoringDetailDrawer.vue'
import {
  buildInitialProgress,
  advanceProgress,
  isAllDone,
  TOTAL_SAMPLES_PER_CANDIDATE,
} from './scoring-progress-model.js'
import { formatTokens, formatDuration } from './scoring-format-model.js'
import { MOCK_CANDIDATES, MOCK_SCORING_REPORT } from './mocks/index.js'

const props = defineProps({
  dirty: { type: Boolean, default: false },
})

const emit = defineEmits(['enter-save', 'back', 'select-candidate'])

const candidates = MOCK_CANDIDATES
const candidateIds = candidates.map((c) => c.candidateId)

const view = ref('running')
const progress = ref(buildInitialProgress(candidateIds))
const startedAt = ref(Date.now())
const elapsedMs = ref(0)
const totalCalls = computed(() => candidateIds.length * TOTAL_SAMPLES_PER_CANDIDATE)
const finishedCalls = computed(() =>
  progress.value.reduce((sum, p) => sum + p.extractDone, 0)
)

const detailOpen = ref(false)
const detailCandidate = ref(null)
const highlightedId = ref('')
const selectedId = ref('')

let timer = null

onMounted(() => {
  startedAt.value = Date.now()
  timer = setInterval(() => {
    elapsedMs.value = Date.now() - startedAt.value
    progress.value = advanceProgress(buildInitialProgress(candidateIds), elapsedMs.value, { tickRate: 4 })
    if (isAllDone(progress.value)) {
      clearInterval(timer)
      timer = null
      view.value = 'done'
      const top = MOCK_SCORING_REPORT.candidates.find((c) => c.rank === 1)
      if (top) selectedId.value = top.candidateId
    }
  }, 250)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

const elapsedSec = computed(() => Math.floor(elapsedMs.value / 1000))
const tokensUsedEstimate = computed(() => finishedCalls.value * 5000)
const totalTokensEstimate = computed(() => totalCalls.value * 5000)
// 流水线模式：总时长 ≈ N × extractDuration + scoringDuration
// 用已完成的抽取样本数推算整体进度比例
const remainingMin = computed(() => {
  if (finishedCalls.value === 0) return '?'
  const progressRatio = finishedCalls.value / totalCalls.value
  const elapsedSec = elapsedMs.value / 1000
  const estimatedTotalSec = elapsedSec / Math.max(progressRatio, 0.01)
  const remainingSec = Math.max(0, estimatedTotalSec - elapsedSec)
  if (remainingSec < 60) return '< 1'
  return Math.ceil(remainingSec / 60)
})

const reportCandidates = MOCK_SCORING_REPORT.candidates

function handleAbort() {
  ElMessageBox.confirm('中止评分会丢失当前进度，确定吗？', '中止评分', { type: 'warning' })
    .then(() => {
      if (timer) clearInterval(timer)
      ElMessage.info('已中止')
      emit('back')
    })
    .catch(() => {})
}

function handleViewDetail(candidateId) {
  highlightedId.value = candidateId
  detailCandidate.value = reportCandidates.find((c) => c.candidateId === candidateId) ?? null
  detailOpen.value = true
}

function handleSelectCandidate(candidateId) {
  selectedId.value = candidateId
  emit('select-candidate', candidateId)
  ElMessage.success(`已选定：${reportCandidates.find((c) => c.candidateId === candidateId)?.displayNameZh ?? candidateId}`)
}

function handleEnterSave() {
  if (!selectedId.value) {
    ElMessage.warning('请先在排行榜操作列点击"选定"')
    return
  }
  emit('enter-save', selectedId.value)
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

    <template v-if="view === 'running'">
      <div class="scoring-progress-summary">
        <div>
          <div class="scoring-progress-summary__metric">
            <strong>{{ finishedCalls }}</strong> / {{ totalCalls }}
          </div>
          <div class="ann-text-tiny">大模型调用 · 已用时 {{ formatDuration(elapsedSec) }} · 预估剩余 {{ remainingMin }} min</div>
        </div>
        <div class="scoring-progress-summary__divider"></div>
        <div>
          <div class="scoring-progress-summary__metric">
            ~ <strong>{{ formatTokens(tokensUsedEstimate) }}</strong>
          </div>
          <div class="ann-text-tiny">已消耗 token · 预估总量 {{ formatTokens(totalTokensEstimate) }}</div>
        </div>
        <div class="scoring-progress-summary__abort">
          <el-button @click="handleAbort">中止评分</el-button>
        </div>
      </div>

      <div class="scoring-matrix">
        <CandidateMatrixRow
          v-for="(p, i) in progress"
          :key="p.candidateId"
          :candidate="candidates.find((c) => c.candidateId === p.candidateId)"
          :progress="p"
          :index="i"
        />
      </div>
    </template>

    <template v-else>
      <ScoringRankingTable
        :candidates="reportCandidates"
        :selected-candidate-id="selectedId"
        :highlighted-candidate-id="highlightedId"
        @select-candidate="handleSelectCandidate"
        @view-detail="handleViewDetail"
      />

      <ScoringDetailDrawer
        v-model="detailOpen"
        :candidate="detailCandidate"
        :is-selected="detailCandidate?.candidateId === selectedId"
      />

      <footer class="scoring-bottom-bar">
        <div class="scoring-bottom-bar__info">
          <template v-if="selectedId">
            已选定：<strong>{{ reportCandidates.find((c) => c.candidateId === selectedId)?.displayNameZh }}</strong>
            （rank {{ reportCandidates.find((c) => c.candidateId === selectedId)?.rank }}，综合分 {{ reportCandidates.find((c) => c.candidateId === selectedId)?.compositeScore.toFixed(2) }}）
          </template>
          <template v-else>尚未选定候选</template>
        </div>
        <div class="scoring-bottom-bar__actions">
          <el-button type="primary" :disabled="!selectedId" @click="handleEnterSave">进入预览 →</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
