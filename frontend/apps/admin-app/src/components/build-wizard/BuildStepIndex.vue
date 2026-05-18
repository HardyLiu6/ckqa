<script setup>
import { computed } from 'vue'
import { Database, Hammer, RefreshCw, AlertTriangle, CheckCircle2, Zap } from 'lucide-vue-next'
import { ElButton, ElMessageBox, ElProgress } from 'element-plus'
import StatusBadge from '../common/StatusBadge.vue'
import { resolveIndexStageLabel } from '../../views/pages/module-content.js'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  step: { type: Object, default: null },
})

const emit = defineEmits(['start-index', 'rebuild-index', 'activate-index'])

// 本次 build_run 的索引运行列表（已按 startedAt desc 排序）
const indexRuns = computed(() => props.blocks.buildRunIndexRuns?.items ?? [])
const latestRun = computed(() => indexRuns.value[0] ?? null)
const hasRuns = computed(() => indexRuns.value.length > 0)

const latestStatus = computed(() => latestRun.value?.status ?? null)
const isRunning = computed(() =>
  props.actionRunning
  || latestStatus.value === 'running'
  || latestStatus.value === 'pending',
)
const isFailed = computed(() => !props.actionRunning && latestStatus.value === 'failed')
const isDone = computed(() =>
  !props.actionRunning
  && (latestStatus.value === 'done' || latestStatus.value === 'success'),
)
const isIdle = computed(() => !hasRuns.value && !props.actionRunning)

// 真实进度，来自后端 BuildRunDetailResponse.indexProgress（透传到 operationFeedback.indexProgress）
const progress = computed(() => props.operationFeedback?.indexProgress ?? null)

// 主进度条：按工作流等权计算，每个阶段贡献相同，避免「extract_graph 95% 但全局只有 25%」的撕裂感。
// 后端 progress.percentage 是按真实耗时加权的（如 create_community_reports 占 50%），更适合
// 用作"还剩多久"的预估，但用户期望主条线性反映"已完成多少阶段"，所以这里改用等权。
const overallPercentage = computed(() => {
  const list = progress.value?.pipelineWorkflows ?? []
  if (list.length === 0) return 0
  const idx = Math.max(0, progress.value?.currentWorkflowIndex ?? 0)
  const sub = progress.value?.subProgress
  const subRatio = sub && sub.total > 0 ? sub.current / sub.total : 0
  // 当前已完成 idx 个阶段 + 当前阶段子进度比例
  const ratio = (idx + subRatio) / list.length
  // 跑动期间 cap 到 99，避免子进度凑齐时 UI 显示 100% 但 status 还没切到 success
  return Math.min(99, Math.max(0, Math.round(ratio * 100)))
})

const currentStageLabel = computed(() => resolveIndexStageLabel(progress.value?.currentWorkflowKey))
const subProgressText = computed(() => {
  const sub = progress.value?.subProgress
  if (!sub || !sub.total) return ''
  return `${sub.current} / ${sub.total}`
})

// 当前阶段子进度百分比，仅在 subProgress 存在时显示独立的小进度条
const subPercentage = computed(() => {
  const sub = progress.value?.subProgress
  if (!sub || !sub.total) return 0
  return Math.min(100, Math.max(0, Math.round((sub.current / sub.total) * 100)))
})

const hasSubProgress = computed(() => Boolean(progress.value?.subProgress?.total))

const stages = computed(() => {
  const list = progress.value?.pipelineWorkflows ?? []
  const completed = new Set(progress.value?.completedWorkflowKeys ?? [])
  const currentKey = progress.value?.currentWorkflowKey
  return list.map((key) => ({
    key,
    label: resolveIndexStageLabel(key),
    state: completed.has(key) ? 'done' : key === currentKey ? 'active' : 'pending',
  }))
})

const elapsedSeconds = computed(() => props.operationFeedback?.elapsedSeconds ?? 0)

function formatElapsed(seconds) {
  if (!seconds || seconds <= 0) return '0 秒'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h} 时 ${m} 分 ${s} 秒`
  if (m > 0) return `${m} 分 ${s} 秒`
  return `${s} 秒`
}

// === done 视图：成功概览 ===
const PROMPT_STRATEGY_LABELS = {
  default: '默认 GraphRAG 提示词',
  graphrag_tuned: '自动调优版本',
  custom_pipeline: '自定义提示词',
}

// 最新 success 索引运行（done 视图主体；多次失败重试取最后一次成功）
const latestSuccessRun = computed(() =>
  indexRuns.value.find((run) => run.status === 'success' || run.status === 'done') ?? null,
)

const successOverview = computed(() => {
  const run = latestSuccessRun.value
  if (!run) return null
  return {
    id: run.id,
    indexVersion: run.indexVersion ?? null,
    elapsedLabel: run.elapsedSeconds ? formatElapsed(run.elapsedSeconds) : null,
    finishedAt: run.finishedAt ?? null,
    promptStrategyLabel: run.promptStrategy
      ? (PROMPT_STRATEGY_LABELS[run.promptStrategy] ?? run.promptStrategy)
      : null,
    notActivated: run.errorSummary === 'skipped_newer_build_exists',
  }
})

const graphSummary = computed(() => latestSuccessRun.value?.graphSummary ?? null)

// 阶段耗时：按耗时降序，仅展示前 5 个，其余归为「其他阶段」
const workflowDurationRows = computed(() => {
  const durations = graphSummary.value?.workflowDurations
  if (!durations || typeof durations !== 'object') return []
  const total = graphSummary.value?.totalRuntimeSeconds ?? 0
  const entries = Object.entries(durations)
    .map(([key, seconds]) => ({
      key,
      label: resolveIndexStageLabel(key),
      seconds: Number(seconds) || 0,
      percentage: total > 0 ? Math.round((Number(seconds) / total) * 100) : 0,
    }))
    .filter((row) => row.seconds > 0)
    .sort((a, b) => b.seconds - a.seconds)
  return entries
})

function formatSeconds(seconds) {
  if (!seconds && seconds !== 0) return '-'
  const s = Number(seconds)
  if (!Number.isFinite(s)) return '-'
  if (s < 1) return `${Math.round(s * 1000)} ms`
  if (s < 60) return `${s.toFixed(1)} 秒`
  const m = Math.floor(s / 60)
  const rest = Math.round(s - m * 60)
  return `${m} 分 ${rest} 秒`
}

function formatFinishedAt(value) {
  if (!value) return '-'
  // 后端返回 LocalDateTime 字符串，简单格式化为月/日 时:分
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function handleStart() {
  emit('start-index')
}

async function handleRebuild() {
  try {
    await ElMessageBox.confirm(
      '重新构建索引将消耗 GraphRAG 计算资源（通常需要 1–2 小时），确定要继续吗？',
      '确认重新构建索引',
      { confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning' },
    )
    emit('rebuild-index')
  } catch {
    // 用户取消，不做任何动作
  }
}
</script>

<template>
  <section class="build-step-index">
    <!-- 非 running 态时的操作反馈横幅 -->
    <Transition name="slide-down">
      <div v-if="operationFeedback && !isRunning" class="operation-feedback" :data-status="operationFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ operationFeedback.title }}</strong>
          <StatusBadge :status="operationFeedback.status" />
        </div>
        <p>{{ operationFeedback.message }}</p>
        <small v-if="operationFeedback.detail">{{ operationFeedback.detail }}</small>
      </div>
    </Transition>

    <!-- 空态：居中大按钮 -->
    <div v-if="isIdle" class="build-step-index__idle">
      <div class="build-step-index__idle-icon">
        <Database :size="48" />
      </div>
      <h3 class="build-step-index__idle-title">本次构建尚未触发索引运行</h3>
      <p class="build-step-index__idle-desc">
        GraphRAG 将基于已确认的图谱输入和提示词策略构建知识图谱索引，<br>
        过程包含实体抽取、社区发现、报告生成等多个阶段，通常需要 1–2 小时。
      </p>
      <el-button
        class="ckqa-el-button ckqa-el-button--primary build-step-index__start-btn"
        type="primary"
        size="large"
        :disabled="step?.status !== 'ready' || actionRunning"
        @click="handleStart"
      >
        <Hammer class="button-icon" :size="18" />
        开始构建索引
      </el-button>
    </div>

    <!-- 构建中：进度条 -->
    <div v-else-if="isRunning" class="build-step-index__running">
      <div class="build-step-index__running-header">
        <Hammer :size="20" class="build-step-index__running-icon" />
        <span class="build-step-index__running-title">索引构建中</span>
        <span v-if="elapsedSeconds > 0" class="build-step-index__running-elapsed">
          已用时 {{ formatElapsed(elapsedSeconds) }}
        </span>
      </div>

      <el-progress
        :percentage="overallPercentage"
        :stroke-width="12"
        :show-text="true"
        :format="() => `${overallPercentage}%`"
        class="build-step-index__progress"
      />

      <div class="build-step-index__stage-info">
        <span class="build-step-index__stage-label">当前阶段：{{ currentStageLabel }}</span>
        <span v-if="subProgressText" class="build-step-index__stage-sub">{{ subProgressText }}</span>
      </div>

      <!-- 当前阶段子进度条：让用户看到 113/118 这种"接近完成本阶段"的视觉反馈 -->
      <el-progress
        v-if="hasSubProgress"
        :percentage="subPercentage"
        :stroke-width="6"
        :show-text="false"
        class="build-step-index__sub-progress"
        status="warning"
      />

      <ol v-if="stages.length > 0" class="build-step-index__stages">
        <li
          v-for="stage in stages"
          :key="stage.key"
          class="build-step-index__stage-item"
          :class="`is-${stage.state}`"
        >
          <span class="build-step-index__stage-dot"></span>
          <span class="build-step-index__stage-name">{{ stage.label }}</span>
        </li>
      </ol>
      <p v-else class="build-step-index__stage-hint">
        正在初始化 GraphRAG，稍候将显示阶段进度…
      </p>
    </div>

    <!-- 已完成或失败：成功概览 + 图谱体量 + 阶段耗时 + 重建/重试 -->
    <div v-else class="build-step-index__done">
      <!-- 成功概览卡：仅当存在 success 索引时展示 -->
      <article v-if="successOverview" class="build-step-index__success-card">
        <header class="build-step-index__success-head">
          <div class="build-step-index__success-title">
            <CheckCircle2 :size="22" class="build-step-index__success-icon" />
            <div>
              <strong>索引构建完成</strong>
              <small>
                #{{ successOverview.id }}
                <span v-if="successOverview.indexVersion"> · {{ successOverview.indexVersion }}</span>
              </small>
            </div>
          </div>
          <StatusBadge status="success" />
        </header>

        <dl class="build-step-index__success-meta">
          <div v-if="successOverview.elapsedLabel">
            <dt>实际耗时</dt>
            <dd>{{ successOverview.elapsedLabel }}</dd>
          </div>
          <div v-if="successOverview.finishedAt">
            <dt>完成时间</dt>
            <dd>{{ formatFinishedAt(successOverview.finishedAt) }}</dd>
          </div>
          <div v-if="successOverview.promptStrategyLabel">
            <dt>提示词策略</dt>
            <dd>{{ successOverview.promptStrategyLabel }}</dd>
          </div>
        </dl>

        <!-- 索引未自动激活时的提示 + 一键激活按钮 -->
        <div v-if="successOverview.notActivated" class="build-step-index__activate-hint">
          <div class="build-step-index__activate-hint-text">
            <AlertTriangle :size="14" />
            <span>本次索引未自动激活（KB 上存在更新的构建），当前问答仍指向旧索引。</span>
          </div>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary build-step-index__activate-btn"
            type="primary"
            size="small"
            :disabled="actionRunning"
            :loading="actionRunning"
            @click="emit('activate-index', latestSuccessRun)"
          >
            <Zap class="button-icon" :size="13" />
            激活本次索引
          </el-button>
        </div>
      </article>

      <!-- 图谱体量 -->
      <article v-if="graphSummary" class="build-step-index__summary-card">
        <h4 class="build-step-index__summary-title">图谱体量</h4>
        <ul class="build-step-index__summary-grid">
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.entityCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">实体</span>
          </li>
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.relationshipCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">关系</span>
          </li>
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.communityCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">社区</span>
          </li>
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.communityReportCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">社区报告</span>
          </li>
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.documentCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">文档</span>
          </li>
          <li>
            <span class="build-step-index__summary-value">{{ graphSummary.textUnitCount ?? '-' }}</span>
            <span class="build-step-index__summary-label">文本单元</span>
          </li>
        </ul>
      </article>

      <!-- 阶段耗时分布（折叠）：让用户能看到「哪一步最贵」 -->
      <details v-if="workflowDurationRows.length > 0" class="build-step-index__durations">
        <summary>阶段耗时分布</summary>
        <ol class="build-step-index__duration-list">
          <li v-for="row in workflowDurationRows" :key="row.key">
            <span class="build-step-index__duration-label">{{ row.label }}</span>
            <span class="build-step-index__duration-bar">
              <span :style="{ width: `${Math.max(2, row.percentage)}%` }"></span>
            </span>
            <span class="build-step-index__duration-value">{{ formatSeconds(row.seconds) }}</span>
          </li>
        </ol>
      </details>

      <!-- 索引运行历史：失败时主要看这里，成功时折叠到详情供追溯 -->
      <details class="build-step-index__history" :open="isFailed">
        <summary>索引运行历史（{{ indexRuns.length }} 次）</summary>
        <ol class="build-task-list">
          <li v-for="item in indexRuns" :key="item.id" class="build-task-row">
            <div>
              <strong>{{ item.title }}</strong>
              <small>{{ item.detail }}</small>
            </div>
            <StatusBadge :status="item.meta" />
          </li>
        </ol>
      </details>

      <div v-if="isFailed" class="build-step-index__failed-hint">
        <AlertTriangle :size="16" />
        <span>上次索引构建失败，可点击下方按钮重试。</span>
      </div>

      <div class="build-step-index__rebuild-area">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          :disabled="!['done', 'failed'].includes(step?.status) || actionRunning"
          :loading="actionRunning"
          @click="handleRebuild"
        >
          <RefreshCw class="button-icon" :size="14" />
          {{ isFailed ? '重试索引构建' : '重新构建索引' }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.build-step-index {
  display: grid;
  gap: 20px;
}

/* 空态：居中大按钮 */
.build-step-index__idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
  gap: 18px;
  border-radius: 16px;
  background: radial-gradient(ellipse at top, rgba(99, 102, 241, 0.05), transparent 70%);
}
.build-step-index__idle-icon {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(56, 189, 248, 0.12));
  border: 1px solid rgba(99, 102, 241, 0.2);
  color: var(--ckqa-accent, #6366f1);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.12);
}
.build-step-index__idle-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__idle-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ckqa-text-muted, #64748b);
  max-width: 440px;
}
.build-step-index__start-btn {
  margin-top: 8px;
  min-width: 200px;
  height: 48px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.25);
}

/* 构建中 */
.build-step-index__running {
  padding: 28px 24px;
  border-radius: 14px;
  background: rgba(99, 102, 241, 0.03);
  border: 1px solid rgba(99, 102, 241, 0.12);
}
.build-step-index__running-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}
.build-step-index__running-icon {
  color: var(--ckqa-accent, #6366f1);
  animation: pulse-icon 2s ease-in-out infinite;
}
@keyframes pulse-icon {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.build-step-index__running-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__running-elapsed {
  margin-left: auto;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
  font-variant-numeric: tabular-nums;
}
.build-step-index__progress {
  margin-bottom: 14px;
}
.build-step-index__progress :deep(.el-progress-bar__outer) {
  border-radius: 6px;
}
.build-step-index__progress :deep(.el-progress-bar__inner) {
  border-radius: 6px;
  background: linear-gradient(90deg, var(--ckqa-accent, #6366f1), #38bdf8);
  transition: width 0.4s ease;
}
.build-step-index__stage-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
}
.build-step-index__stage-label {
  font-weight: 600;
  color: var(--ckqa-accent, #6366f1);
}
.build-step-index__stage-sub {
  font-variant-numeric: tabular-nums;
}
/* 当前阶段的子进度条：比主条窄、暖色调，视觉从属关系明确 */
.build-step-index__sub-progress {
  margin-bottom: 16px;
}
.build-step-index__sub-progress :deep(.el-progress-bar__outer) {
  border-radius: 4px;
  background: rgba(245, 158, 11, 0.08);
}
.build-step-index__sub-progress :deep(.el-progress-bar__inner) {
  border-radius: 4px;
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
  transition: width 0.3s ease;
}
.build-step-index__stage-hint {
  margin: 0;
  font-size: 12px;
  color: var(--ckqa-text-muted, #94a3b8);
  font-style: italic;
}

/* 阶段列表 */
.build-step-index__stages {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.build-step-index__stage-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
}
.build-step-index__stage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e2e8f0;
  flex-shrink: 0;
  transition: background 0.3s ease, box-shadow 0.3s ease;
}
.build-step-index__stage-item.is-done .build-step-index__stage-dot {
  background: #10b981;
}
.build-step-index__stage-item.is-active .build-step-index__stage-dot {
  background: var(--ckqa-accent, #6366f1);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
  animation: pulse-dot 1.5s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2); }
  50%      { box-shadow: 0 0 0 5px rgba(99, 102, 241, 0.1); }
}
.build-step-index__stage-item.is-done { color: #10b981; }
.build-step-index__stage-item.is-active {
  color: var(--ckqa-accent, #6366f1);
  font-weight: 600;
}

/* 完成 / 失败 */
.build-step-index__done {
  display: grid;
  gap: 16px;
}

/* 成功概览卡 */
.build-step-index__success-card {
  padding: 20px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.06), rgba(56, 189, 248, 0.04));
  border: 1px solid rgba(16, 185, 129, 0.18);
}
.build-step-index__success-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.build-step-index__success-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.build-step-index__success-title strong {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__success-title small {
  font-size: 12px;
  color: var(--ckqa-text-muted, #64748b);
  font-variant-numeric: tabular-nums;
}
.build-step-index__success-icon {
  color: #10b981;
  flex-shrink: 0;
}
.build-step-index__success-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 10px 24px;
  margin: 0;
  padding: 0;
  border-top: 1px dashed rgba(16, 185, 129, 0.18);
  padding-top: 14px;
}
.build-step-index__success-meta > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.build-step-index__success-meta dt {
  font-size: 11px;
  color: var(--ckqa-text-muted, #94a3b8);
}
.build-step-index__success-meta dd {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ckqa-text, #1e293b);
  font-variant-numeric: tabular-nums;
}
.build-step-index__activate-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.25);
  color: #b45309;
  font-size: 12px;
  line-height: 1.5;
}
.build-step-index__activate-hint-text {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.build-step-index__activate-btn {
  flex-shrink: 0;
}

/* 图谱体量 */
.build-step-index__summary-card {
  padding: 18px 20px;
  border-radius: 12px;
  background: var(--ckqa-surface-muted, #f8fafc);
  border: 1px solid var(--ckqa-border-muted, rgba(148, 163, 184, 0.2));
}
.build-step-index__summary-title {
  margin: 0 0 14px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__summary-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}
@media (max-width: 720px) {
  .build-step-index__summary-grid { grid-template-columns: repeat(3, 1fr); }
}
.build-step-index__summary-grid li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.build-step-index__summary-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--ckqa-accent, #6366f1);
  font-variant-numeric: tabular-nums;
}
.build-step-index__summary-label {
  font-size: 11px;
  color: var(--ckqa-text-muted, #64748b);
}

/* 阶段耗时分布 */
.build-step-index__durations,
.build-step-index__history {
  border-radius: 10px;
  border: 1px solid var(--ckqa-border-muted, rgba(148, 163, 184, 0.2));
  background: #fff;
  padding: 4px 14px;
}
.build-step-index__durations summary,
.build-step-index__history summary {
  cursor: pointer;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ckqa-text, #1e293b);
  user-select: none;
}
.build-step-index__durations summary:hover,
.build-step-index__history summary:hover {
  color: var(--ckqa-accent, #6366f1);
}
.build-step-index__duration-list {
  margin: 8px 0 14px;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.build-step-index__duration-list li {
  display: grid;
  grid-template-columns: 140px 1fr 80px;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}
.build-step-index__duration-label {
  color: var(--ckqa-text-muted, #64748b);
}
.build-step-index__duration-bar {
  height: 6px;
  border-radius: 3px;
  background: rgba(148, 163, 184, 0.15);
  overflow: hidden;
  display: block;
}
.build-step-index__duration-bar > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--ckqa-accent, #6366f1), #38bdf8);
  border-radius: 3px;
}
.build-step-index__duration-value {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--ckqa-text, #1e293b);
}
.build-step-index__failed-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #dc2626;
  font-size: 12px;
}
.build-step-index__rebuild-area {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}
</style>
