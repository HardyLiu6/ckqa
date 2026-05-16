<script setup>
import { computed } from 'vue'

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
})

const emit = defineEmits(['select-seed'])

const SEED_OPTIONS = [
  { key: 'system_default',  title: '🧱 系统默认',     desc: '使用 GraphRAG 内置默认提示词作为起点', meta: '来源：prompts/extract_graph.txt' },
  { key: 'graphrag_tuned',  title: '✨ 沿用自动调优版', desc: '克隆当前激活的自动调优结果，在此基础上微调', meta: null },
  { key: 'history_draft',   title: '📦 我的历史草稿',  desc: '从你之前在该知识库保存过的草稿继续', meta: null },
]

function metaFor(option) {
  if (option.meta) return option.meta
  if (option.key === 'graphrag_tuned' && props.graphragTunedSummary) {
    const time = props.graphragTunedSummary.activatedAt
      ? new Date(props.graphragTunedSummary.activatedAt).toLocaleDateString('zh-CN')
      : ''
    return `候选：${props.graphragTunedSummary.name ?? 'auto_tuned'} · 激活于 ${time}`
  }
  if (option.key === 'graphrag_tuned') return '本课程当前激活的自动调优结果'
  if (option.key === 'history_draft') {
    if (props.historyDrafts.length === 0) return 'Phase 1e 开放（暂无可选草稿）'
    return `Phase 1e 开放（共 ${props.historyDrafts.length} 条草稿）`
  }
  return ''
}

function findAvailabilityOption(key) {
  const opts = props.seedAvailability?.options ?? []
  return opts.find((o) => o.key === key)
}

function isOptionDisabled(option) {
  // Phase 4.5：先检查 availability；缺失时只禁 history_draft
  const match = findAvailabilityOption(option.key)
  if (match) return !match.available
  return option.key === 'history_draft'
}

function disabledReasonFor(option) {
  const match = findAvailabilityOption(option.key)
  if (!match || match.available) return ''
  switch (match.reason) {
    case 'auto_tuned_not_started':
      return '请先在知识库构建向导触发自动调优'
    case 'auto_tuned_running':
    case 'auto_tuned_pending':
      return '自动调优正在执行，请稍候'
    case 'auto_tuned_failed':
      return '上次自动调优失败，请重新触发'
    case 'phase_6_not_implemented':
      return '历史草稿入口将在后续版本开放'
    case 'evaluation_failed':
      return '无法评估自动调优产物状态'
    default:
      return match.summary ?? '当前不可选'
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
        :title="isOptionDisabled(option) ? disabledReasonFor(option) : ''"
        @click="!isOptionDisabled(option) && emit('select-seed', option.key)"
      >
        <strong>{{ option.title }}</strong>
        <p>{{ option.desc }}</p>
        <small v-if="metaFor(option)">{{ metaFor(option) }}</small>
      </button>
    </div>
  </section>
</template>
