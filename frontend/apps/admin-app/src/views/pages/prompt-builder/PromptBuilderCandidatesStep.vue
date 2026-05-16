<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import CandidateCard from './CandidateCard.vue'
import PromptDisplay from './PromptDisplay.vue'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  formatTokens,
} from './candidates-selection-model.js'
import {
  generateCandidates,
  listCandidates,
  getCandidatePromptText,
  listAuditSamples,
} from '../../../api/prompt-tune-pipeline.js'
//      ^^^^^^^^^^ 三个 ../，从 src/views/pages/prompt-builder/ 上溯到 src/api/

const props = defineProps({
  dirty: { type: Boolean, default: false },
  /**
   * Phase 4.5：当前 build run metadata 中的 seed。
   * 用于与 candidates[0].seed 比较，若不一致则候选已 stale，提示用户重新生成。
   * 缺失（null）时不触发 stale 判定（兼容老 build run）。
   */
  currentBuildRunSeed: { type: String, default: null },
})

const emit = defineEmits(['start-scoring', 'back'])

const route = useRoute()

const BUSINESS_CODE_CANDIDATES_NOT_GENERATED = 4105

const buildRunId = computed(() => {
  const raw = route.query.buildRunId
  if (!raw) return null
  const num = Number(raw)
  return Number.isFinite(num) && num > 0 ? num : null
})

// 五态：loading / error / blocked-by-gate / empty / ready
const phase = ref('loading')
const errorMessage = ref('')
const candidates = ref([])
const selectedIds = ref([])

// ready 态下重新生成候选时的局部 loading 标志（不切 phase，保持候选网格可见）
const regenerating = ref(false)

// 抽屉懒加载状态
const drawerOpen = ref(false)
const drawerCandidate = ref(null)
const drawerPromptText = ref('')
const drawerLoading = ref(false)

const summary = computed(() => computeSummary(selectedIds.value, candidates.value))

// Phase 4.5：候选 seed 与当前 build run seed 不一致 → 标记 stale
// 缺 candidate.seed（Phase 4 老 build run 候选目录无 seed-info.json）→ 不触发 stale
const isCandidatesStaleBySeed = computed(() => {
  if (candidates.value.length === 0) return false
  const candidateSeed = candidates.value[0].seed ?? null
  if (candidateSeed === null) return false
  return candidateSeed !== props.currentBuildRunSeed
})

function seedShortLabel(seed) {
  if (seed === 'system_default') return '系统默认'
  if (seed === 'graphrag_tuned') return '自动调优'
  return seed ?? '未知'
}

onMounted(loadCandidates)

async function loadCandidates() {
  if (!buildRunId.value) {
    phase.value = 'error'
    errorMessage.value = '缺少 buildRunId，请从构建向导进入此页面'
    return
  }
  phase.value = 'loading'
  errorMessage.value = ''
  try {
    // 1. 进入门控：02 步至少 1 条 completed
    const samples = await listAuditSamples(buildRunId.value)
    const completedCount = (samples ?? []).filter(
      (s) => s.reviewerDecision === 'completed'
    ).length
    if (completedCount === 0) {
      phase.value = 'blocked-by-gate'
      return
    }

    // 2. 拉候选；4105 表示未生成 → 进入 empty 态
    try {
      const list = await listCandidates(buildRunId.value)
      candidates.value = list
      selectedIds.value = selectAll(list)
      phase.value = 'ready'
    } catch (err) {
      if (err?.code === BUSINESS_CODE_CANDIDATES_NOT_GENERATED) {
        phase.value = 'empty'
      } else {
        throw err
      }
    }
  } catch (err) {
    phase.value = 'error'
    errorMessage.value = err?.message ?? '加载候选失败'
  }
}

async function handleGenerate() {
  if (!buildRunId.value) return
  phase.value = 'loading'
  try {
    const list = await generateCandidates(buildRunId.value)
    candidates.value = list
    selectedIds.value = selectAll(list)
    phase.value = 'ready'
    ElMessage.success(`已生成 ${list.length} 个候选 Prompt`)
  } catch (err) {
    if (err?.code === 4104) {
      phase.value = 'blocked-by-gate'
    } else {
      phase.value = 'error'
      errorMessage.value = err?.message ?? '候选生成失败'
    }
  }
}

/**
 * ready 态下"重新生成候选"按钮：覆盖式重跑，不切换页面 phase（保持原候选可见
 * 直到重跑完成），失败时只 toast 不破坏页面。
 *
 * 设计意图：用户在 02 步补 gold 后，希望快速触发候选刷新，不必刷新整个页面。
 * 与 handleGenerate（empty 态触发）区分开：handleGenerate 会切 phase 到 loading；
 * handleRegenerate 用独立的 regenerating 标志，按钮 disable + 文案变化即可。
 */
async function handleRegenerate() {
  if (!buildRunId.value) return
  if (regenerating.value) return
  regenerating.value = true
  try {
    const list = await generateCandidates(buildRunId.value)
    candidates.value = list
    selectedIds.value = selectAll(list)
    ElMessage.success(`已重新生成 ${list.length} 个候选`)
  } catch (err) {
    if (err?.code === 4104) {
      // 边界：用户在 02 步把所有 completed 改回 in_progress 再点重新生成
      phase.value = 'blocked-by-gate'
    } else {
      ElMessage.error(err?.message ?? '重新生成失败')
    }
  } finally {
    regenerating.value = false
  }
}

function handleToggle(id) {
  selectedIds.value = toggleCandidate(selectedIds.value, id)
}

function handleSelectAll()  { selectedIds.value = selectAll(candidates.value) }
function handleSelectNone() { selectedIds.value = selectNone() }
function handleSelectBaseline() {
  selectedIds.value = selectBaselineOnly(candidates.value)
  ElMessage.info(`已仅选基线（${selectedIds.value.length} 个）`)
}

async function handleViewPrompt(id) {
  drawerCandidate.value = candidates.value.find((c) => c.candidateId === id) ?? null
  drawerOpen.value = true
  drawerLoading.value = true
  drawerPromptText.value = ''
  try {
    drawerPromptText.value = await getCandidatePromptText(buildRunId.value, id)
  } catch (err) {
    ElMessage.error(err?.message ?? '读取 Prompt 文本失败')
  } finally {
    drawerLoading.value = false
  }
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

    <!-- loading -->
    <div v-if="phase === 'loading'" class="candidate-state-card">
      <span>正在加载候选 Prompt...</span>
    </div>

    <!-- error -->
    <div v-else-if="phase === 'error'" class="candidate-state-card candidate-state-card--error">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="loadCandidates">重试</el-button>
    </div>

    <!-- blocked-by-gate（02 步 0 条 completed） -->
    <div v-else-if="phase === 'blocked-by-gate'" class="candidate-state-card candidate-state-card--blocked">
      <p>请先在 02 步完成至少 1 条样本的审阅，才能进入候选 Prompt 生成。</p>
      <el-button @click="$emit('back')">返回 02 步标注</el-button>
    </div>

    <!-- empty（02 步已完成但候选未生成） -->
    <div v-else-if="phase === 'empty'" class="candidate-state-card candidate-state-card--empty">
      <p>本次构建尚未生成候选 Prompt。点击下方按钮立即生成（约 1 秒）。</p>
      <el-button type="primary" @click="handleGenerate">立即生成候选</el-button>
    </div>

    <!-- ready -->
    <template v-else>
      <!-- Phase 4.5：候选 seed 与当前 seed 不一致时显示 stale 横幅 -->
      <div v-if="isCandidatesStaleBySeed" class="candidate-stale-banner">
        <span>
          ⚠ 当前候选基于旧种子（{{ seedShortLabel(candidates[0]?.seed) }}）生成，
          与本次构建当前的种子（{{ seedShortLabel(currentBuildRunSeed) }}）不一致，
          建议点&quot;重新生成候选&quot;覆盖更新。
        </span>
        <el-button size="small" @click="handleRegenerate" :disabled="regenerating">
          {{ regenerating ? '重新生成中...' : '立即重新生成' }}
        </el-button>
      </div>

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
            <button @click="handleRegenerate" :disabled="regenerating" :title="regenerating ? '正在重新生成...' : '基于最新 02 步 gold 重新生成候选'">
              {{ regenerating ? '重新生成中...' : '重新生成候选' }}
            </button>
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
          <div v-if="drawerLoading" class="ann-text-muted">加载 Prompt 文本中...</div>
          <PromptDisplay
            v-else-if="drawerPromptText"
            :text="drawerPromptText"
            default-mode="rich"
          />
          <div v-else class="ann-text-muted">未找到该候选的提示词文本</div>
        </div>
      </el-drawer>
    </template>
  </section>
</template>

<style scoped>
.candidate-state-card {
  padding: 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  color: var(--ckqa-text-muted, #78716c);
}
.candidate-state-card--error { color: var(--ckqa-danger, #dc2626); }
.candidate-state-card--blocked { color: var(--ckqa-warning, #d97706); }
.candidate-state-card--empty { color: var(--ckqa-text); }

/* Phase 4.5：stale 横幅 */
.candidate-stale-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background-color: rgba(217, 119, 6, 0.08);
  border: 1px solid rgba(217, 119, 6, 0.32);
  border-radius: 8px;
  color: var(--ckqa-warning, #d97706);
  font-size: 13px;
}
.candidate-stale-banner > span {
  flex: 1;
}
</style>
