<script setup>
import { computed } from 'vue'
import { Database, Hammer, RefreshCw, AlertTriangle } from 'lucide-vue-next'
import { ElButton, ElMessageBox, ElProgress } from 'element-plus'
import StatusBadge from '../common/StatusBadge.vue'
import { resolveIndexStageLabel } from '../../views/pages/module-content.js'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  step: { type: Object, default: null },
})

const emit = defineEmits(['start-index', 'rebuild-index'])

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

const percentage = computed(() => progress.value?.percentage ?? 0)
const currentStageLabel = computed(() => resolveIndexStageLabel(progress.value?.currentWorkflowKey))
const subProgressText = computed(() => {
  const sub = progress.value?.subProgress
  if (!sub || !sub.total) return ''
  return `${sub.current} / ${sub.total}`
})

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
        :percentage="percentage"
        :stroke-width="12"
        :show-text="true"
        :format="() => `${percentage}%`"
        class="build-step-index__progress"
      />

      <div class="build-step-index__stage-info">
        <span class="build-step-index__stage-label">当前阶段：{{ currentStageLabel }}</span>
        <span v-if="subProgressText" class="build-step-index__stage-sub">{{ subProgressText }}</span>
      </div>

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

    <!-- 已完成或失败：列表 + 重建/重试 -->
    <div v-else class="build-step-index__done">
      <ol class="build-task-list">
        <li v-for="item in indexRuns" :key="item.id" class="build-task-row">
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.detail }}</small>
          </div>
          <StatusBadge :status="item.meta" />
        </li>
      </ol>

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
  margin-bottom: 14px;
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
