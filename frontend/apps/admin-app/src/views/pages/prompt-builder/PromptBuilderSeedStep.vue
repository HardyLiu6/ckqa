<script setup>
import { computed } from 'vue'

const props = defineProps({
  seed: { type: String, default: null },
  graphragTunedSummary: { type: Object, default: null },
  historyDrafts: { type: Array, default: () => [] },
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

const isOptionDisabled = (option) => option.key === 'history_draft'
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
        @click="!isOptionDisabled(option) && emit('select-seed', option.key)"
      >
        <strong>{{ option.title }}</strong>
        <p>{{ option.desc }}</p>
        <small v-if="metaFor(option)">{{ metaFor(option) }}</small>
      </button>
    </div>
  </section>
</template>
