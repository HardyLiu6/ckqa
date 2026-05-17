<script setup>
import { computed } from 'vue'
import { Sparkles, RefreshCw } from 'lucide-vue-next'

const props = defineProps({
  seed: { type: String, default: null },
  graphragTunedSummary: { type: Object, default: null },
  historyDrafts: { type: Array, default: () => [] },
  /**
   * Phase 4.5：来自 GET /seed-availability 的响应。
   * 形状 { currentSeed, options: [{ key, available, reason, summary }, ...] }。
   * 缺失（接口失败）时所有 seed 视为可用，由后端兜底校验。
   */
  seedAvailability: { type: Object, default: null },
  /**
   * Phase 5.3：自动调优 prompt-tune 任务的状态，用于把 graphrag_tuned 卡片
   * 从"被禁用"改造成"可触发 + 进度反馈 + 失败重试"完整状态机。
   * 形状 { status: 'not_started'|'pending'|'running'|'success'|'failed', cacheHit, progressStage, errorMessage, ... }。
   * 由父组件 PromptBuilderPage 持有 + 1.5s 轮询 GET /prompt-tune 维护。
   */
  promptTuneState: { type: Object, default: null },
  /** 触发 / 重试期间的 loading 标志，避免重复点击。 */
  promptTuneTriggering: { type: Boolean, default: false },
})

const emit = defineEmits(['select-seed', 'prompt-tune-trigger', 'prompt-tune-retry'])

const SEED_OPTIONS = [
  { key: 'system_default',  title: '🧱 系统默认',     desc: '使用 GraphRAG 内置默认提示词作为起点', meta: '来源：prompts/extract_graph.txt' },
  { key: 'graphrag_tuned',  title: '✨ 沿用自动调优版', desc: '克隆当前激活的自动调优结果，在此基础上微调', meta: null },
  { key: 'history_draft',   title: '📦 我的历史草稿',  desc: '从你之前在该知识库保存过的草稿继续', meta: null },
]

/** 自动调优的 4 态，决定 graphrag_tuned 卡片渲染分支 */
const tuneStatus = computed(() => props.promptTuneState?.status ?? 'not_started')

/** running / pending 都视作"调优中"显示 spinner */
const isTuneRunning = computed(() =>
  tuneStatus.value === 'running' || tuneStatus.value === 'pending',
)

const tuneProgressLabel = computed(() => {
  const stage = props.promptTuneState?.progressStage ?? ''
  switch (stage) {
    case 'preparing': return '准备样本…'
    case 'tuning': return '自动调优中…'
    case 'finalizing': return '收尾汇总…'
    default: return '自动调优中…'
  }
})

function metaFor(option) {
  if (option.meta) return option.meta
  if (option.key === 'graphrag_tuned') {
    if (props.graphragTunedSummary) {
      const time = props.graphragTunedSummary.activatedAt
        ? new Date(props.graphragTunedSummary.activatedAt).toLocaleDateString('zh-CN')
        : ''
      return `候选：${props.graphragTunedSummary.name ?? 'auto_tuned'} · 激活于 ${time}`
    }
    return '本课程当前激活的自动调优结果'
  }
  if (option.key === 'history_draft') {
    if (props.historyDrafts.length === 0) {
      return '本知识库暂无历史草稿，05 步保存并入库后会出现在这里'
    }
    return `共 ${props.historyDrafts.length} 条历史草稿可选`
  }
  return ''
}

function findAvailabilityOption(key) {
  const opts = props.seedAvailability?.options ?? []
  return opts.find((o) => o.key === key)
}

function isOptionDisabled(option) {
  // Phase 4.5：先检查 availability；缺失时按 historyDrafts 列表回退判断
  const match = findAvailabilityOption(option.key)
  if (option.key === 'graphrag_tuned') {
    // Phase 5.3：graphrag_tuned 卡片不再 disabled——
    // not_started / failed 显示触发按钮；running / pending 显示 spinner（卡片整体阻塞点击避免误选）；
    // success 才允许选择。disabled 状态只剩"调优中"这一种短暂态。
    return isTuneRunning.value
  }
  if (match) return !match.available
  // history_draft：列表非空就放开（与 SeedAvailability 后端 count > 0 一致）
  if (option.key === 'history_draft') {
    return props.historyDrafts.length === 0
  }
  return false
}

function disabledReasonFor(option) {
  if (option.key === 'graphrag_tuned' && isTuneRunning.value) {
    return '自动调优正在执行，请稍候'
  }
  const match = findAvailabilityOption(option.key)
  if (!match || match.available) return ''
  switch (match.reason) {
    case 'auto_tuned_not_started':
      return '本课程暂无自动调优产物，可在卡片内点击「立即触发」'
    case 'auto_tuned_running':
    case 'auto_tuned_pending':
      return '自动调优正在执行，请稍候'
    case 'auto_tuned_failed':
      return '上次自动调优失败，可在卡片内点击「重试」'
    case 'no_history_draft':
      return '本知识库暂无历史草稿，05 步保存并入库后会出现在这里'
    case 'evaluation_failed':
      return '无法评估自动调优产物状态'
    default:
      return match.summary ?? '当前不可选'
  }
}

/** graphrag_tuned 卡片是否处于"可触发"状态——seedAvailability 没让它可用、且任务非 running */
const graphragTunedNeedsTrigger = computed(() => {
  if (isTuneRunning.value) return false
  const match = findAvailabilityOption('graphrag_tuned')
  if (!match) return false
  // 三种 reason 都允许卡片内触发：未启动 / 失败 / 评估失败兜底
  if (match.available) return false
  return ['auto_tuned_not_started', 'auto_tuned_failed', 'evaluation_failed'].includes(match.reason)
})

/** 触发按钮文案：未启动用「立即触发」，失败用「重试调优」 */
const triggerButtonLabel = computed(() => {
  const match = findAvailabilityOption('graphrag_tuned')
  if (match?.reason === 'auto_tuned_failed' || tuneStatus.value === 'failed') return '重试调优'
  return '立即触发'
})

function handleCardClick(option) {
  if (isOptionDisabled(option)) return
  emit('select-seed', option.key)
}

function handleTriggerClick(e) {
  // 阻止冒泡触发卡片选中（用户可能还没决定是否选 graphrag_tuned 作为最终 seed）
  e.stopPropagation()
  if (props.promptTuneTriggering || isTuneRunning.value) return
  if (tuneStatus.value === 'failed') {
    emit('prompt-tune-retry')
  } else {
    emit('prompt-tune-trigger')
  }
}
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>选模板</h3>
      <p>选一个起始模板，后续在此基础上修改。</p>
    </header>

    <div class="seed-grid" role="radiogroup" aria-label="种子模板">
      <button
        v-for="option in SEED_OPTIONS"
        :key="option.key"
        type="button"
        role="radio"
        :aria-checked="seed === option.key"
        :aria-disabled="isOptionDisabled(option)"
        :tabindex="isOptionDisabled(option) ? -1 : 0"
        class="seed-card"
        :data-selected="seed === option.key ? 'true' : 'false'"
        :data-disabled="isOptionDisabled(option) ? 'true' : 'false'"
        :data-needs-trigger="option.key === 'graphrag_tuned' && graphragTunedNeedsTrigger ? 'true' : 'false'"
        :title="isOptionDisabled(option) ? disabledReasonFor(option) : ''"
        @click="handleCardClick(option)"
      >
        <strong>{{ option.title }}</strong>
        <p>{{ option.desc }}</p>

        <!-- graphrag_tuned 卡片专属：可触发 / 调优中 / 失败 三态 -->
        <template v-if="option.key === 'graphrag_tuned' && graphragTunedNeedsTrigger">
          <small class="seed-card__hint">{{ disabledReasonFor(option) || metaFor(option) }}</small>
          <el-button
            type="primary"
            size="small"
            class="seed-card__trigger-btn"
            :loading="promptTuneTriggering"
            :disabled="promptTuneTriggering"
            @click="handleTriggerClick"
          >
            <Sparkles v-if="tuneStatus !== 'failed'" :size="14" aria-hidden="true" />
            <RefreshCw v-else :size="14" aria-hidden="true" />
            {{ triggerButtonLabel }}
          </el-button>
        </template>
        <template v-else-if="option.key === 'graphrag_tuned' && isTuneRunning">
          <div class="seed-card__progress" role="status">
            <span class="seed-card__spinner" aria-hidden="true"></span>
            <span>{{ tuneProgressLabel }}</span>
          </div>
          <small>大模型调优需要 5-10 分钟，可继续浏览其它页面</small>
        </template>
        <small v-else-if="metaFor(option)">{{ metaFor(option) }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.seed-card__hint {
  color: var(--ckqa-warning, #d97706);
}
.seed-card__trigger-btn {
  margin-top: 4px;
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.seed-card__progress {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ckqa-info, #0284c7);
  font-size: 12px;
  margin-top: 2px;
}
.seed-card__spinner {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-top-color: transparent;
  animation: seed-card-spin 0.85s linear infinite;
}
@keyframes seed-card-spin {
  to { transform: rotate(360deg); }
}
</style>
