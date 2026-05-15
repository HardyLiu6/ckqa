<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CandidateCard from './CandidateCard.vue'
import CandidateSummaryBar from './CandidateSummaryBar.vue'
import { MOCK_CANDIDATES } from './mocks/index.js'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  formatTokens,
} from './candidates-selection-model.js'

const emit = defineEmits(['start-scoring', 'back'])

const candidates = MOCK_CANDIDATES
const selectedIds = ref(selectAll(candidates))
const drawerOpen = ref(false)
const drawerCandidate = ref(null)

const summary = computed(() => computeSummary(selectedIds.value, candidates))

function handleToggle(id) {
  selectedIds.value = toggleCandidate(selectedIds.value, id)
}

function handleSelectAll()  { selectedIds.value = selectAll(candidates) }
function handleSelectNone() { selectedIds.value = selectNone() }
function handleSelectBaseline() {
  selectedIds.value = selectBaselineOnly(candidates)
  ElMessage.info(`已仅选基线（${selectedIds.value.length} 个）`)
}

function handleViewPrompt(id) {
  drawerCandidate.value = candidates.find((c) => c.candidateId === id) ?? null
  drawerOpen.value = true
}

function handleStart() {
  if (summary.value.candidateCount === 0) {
    ElMessage.warning('请至少选择 1 个候选')
    return
  }
  emit('start-scoring', selectedIds.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-candidates">
    <header class="prompt-builder-step__header">
      <h3>生成候选提示词</h3>
      <p>勾选要进入 04 步评分的候选 · 默认全选 · 长 prompt 候选 token 消耗显著高于基线</p>
    </header>

    <CandidateSummaryBar
      :total-candidates="candidates.length"
      :candidate-count="summary.candidateCount"
      :total-calls="summary.totalCalls"
      :estimated-tokens="summary.estimatedTokens"
      :estimated-minutes="summary.estimatedMinutes"
    />

    <footer class="candidate-bottom-bar">
      <div class="candidate-bottom-bar__info">
        已选 <strong>{{ summary.candidateCount }}</strong> 个候选 ·
        预估 <strong>{{ formatTokens(summary.estimatedTokens) }}</strong> tokens ·
        约 <strong>{{ summary.estimatedMinutes }}</strong> 分钟
      </div>
      <div class="candidate-bottom-bar__actions">
        <el-button @click="$emit('back')">← 返回 02</el-button>
        <el-button type="primary" :disabled="summary.candidateCount === 0" @click="handleStart">
          开始抽取评分 →
        </el-button>
      </div>
    </footer>

    <div class="candidate-quick-actions">
      <button @click="handleSelectAll">全选</button>
      <button @click="handleSelectNone">清空</button>
      <button @click="handleSelectBaseline">仅选基线</button>
      <span class="ann-text-muted candidate-quick-actions__hint">点击候选卡片切换勾选状态</span>
    </div>

    <div class="candidate-grid">
      <CandidateCard
        v-for="candidate in candidates"
        :key="candidate.candidateId"
        :candidate="candidate"
        :selected="selectedIds.includes(candidate.candidateId)"
        @toggle="handleToggle"
        @view-prompt="handleViewPrompt"
      />
    </div>

    <el-drawer
      v-model="drawerOpen"
      :title="drawerCandidate ? `${drawerCandidate.displayNameZh}（${drawerCandidate.candidateId}）` : ''"
      direction="rtl"
      size="520px"
    >
      <div class="candidate-prompt-drawer">
        <div class="ann-text-muted candidate-prompt-drawer__hint">
          Phase 1e 会换成富文本三视图。当前展示为暗色 IDE 简版。
        </div>
        <pre class="candidate-prompt-drawer__pre">{{ drawerCandidate?.basePromptSource ? `（mock）此处展示候选 ${drawerCandidate.candidateId} 的完整 prompt.txt 文本，Phase 1e 会接入 PromptDisplay 组件展示真实内容。` : '' }}</pre>
      </div>
    </el-drawer>
  </section>
</template>
