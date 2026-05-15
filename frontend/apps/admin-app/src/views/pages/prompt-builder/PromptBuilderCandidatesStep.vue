<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CandidateCard from './CandidateCard.vue'
import PromptDisplay from './PromptDisplay.vue'
import { MOCK_CANDIDATES, resolveCandidatePromptText } from './mocks/index.js'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  formatTokens,
} from './candidates-selection-model.js'

const props = defineProps({
  dirty: { type: Boolean, default: false },
})

const emit = defineEmits(['start-scoring', 'back'])

const candidates = MOCK_CANDIDATES
const selectedIds = ref(selectAll(candidates))
const drawerOpen = ref(false)
const drawerCandidate = ref(null)

const drawerPromptText = computed(() => drawerCandidate.value ? resolveCandidatePromptText(drawerCandidate.value.candidateId) : '')

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
      <button class="step-back-btn" title="返回上一步" @click="$emit('back')">←</button>
      <div>
        <h3>生成候选提示词</h3>
        <p>勾选要进入 04 步评分的候选 · 默认全选 · 长 prompt 候选 token 消耗显著高于基线</p>
      </div>
    </header>

    <!-- 合并摘要 + 操作为一栏 -->
    <div class="candidate-action-bar">
      <div class="candidate-action-bar__left">
        <el-tag v-if="dirty" type="warning" size="small" effect="light">已修改未保存</el-tag>
        <el-tag v-else type="success" size="small" effect="light">已是最新</el-tag>
        <span class="candidate-action-bar__stats">
          已选 <strong>{{ summary.candidateCount }}</strong> / {{ candidates.length }} 个候选 ·
          <strong>{{ summary.totalCalls }}</strong> 次调用 ·
          <strong>{{ formatTokens(summary.estimatedTokens) }}</strong> tokens ·
          约 <strong>{{ summary.estimatedMinutes }}</strong> 分钟
        </span>
      </div>
      <div class="candidate-action-bar__right">
        <div class="candidate-quick-actions">
          <button @click="handleSelectAll">全选</button>
          <button @click="handleSelectNone">清空</button>
          <button @click="handleSelectBaseline">仅选基线</button>
        </div>
        <el-button type="primary" :disabled="summary.candidateCount === 0" @click="handleStart">
          开始抽取评分 →
        </el-button>
      </div>
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
        <PromptDisplay v-if="drawerPromptText" :text="drawerPromptText" default-mode="rich" />
        <div v-else class="ann-text-muted">未找到该候选的提示词文本</div>
      </div>
    </el-drawer>
  </section>
</template>
