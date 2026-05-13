<script setup>
import { computed } from 'vue'
import StatusBadge from '../common/StatusBadge.vue'
import PromptStrategyCard from './PromptStrategyCard.vue'
import PromptStrategyDetail from './PromptStrategyDetail.vue'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  step: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  operationFeedback: { type: Object, default: null },
  selectedStrategy: { type: String, default: 'default' },
})

const emit = defineEmits(['update:strategy', 'goto-builder', 'reset-confirm'])

const promptBlock = computed(() => props.blocks.prompt ?? {})
const disabled = computed(() => promptBlock.value.status === 'blocked' || promptBlock.value.readonly === true)

const STRATEGIES = [
  { key: 'default',         title: '默认提示词',          icon: '⚙',
    description: '使用系统默认的 GraphRAG 提示词，开箱即用。' },
  { key: 'graphrag_tuned',  title: 'GraphRAG 自动调优提示词', icon: '✨',
    description: '使用 GraphRAG 基于本课程样本自动调优生成的提示词。' },
  { key: 'custom_pipeline', title: '手动调优提示词',      icon: '🛠',
    description: '进入独立页面，按 3 步流程亲手调优本次构建使用的提示词。' },
]

function handleSelect(key) {
  if (disabled.value) return
  emit('update:strategy', key)
}
</script>

<template>
  <section class="build-step-panel prompt-confirm-panel">
    <Transition name="slide-down">
      <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
        <div class="operation-feedback__heading">
          <strong>{{ operationFeedback.title }}</strong>
          <StatusBadge :status="operationFeedback.status" />
        </div>
        <p>{{ operationFeedback.message }}</p>
        <small v-if="operationFeedback.detail">{{ operationFeedback.detail }}</small>
      </div>
    </Transition>

    <div role="radiogroup" aria-label="提示词策略" class="prompt-strategy-grid">
      <PromptStrategyCard
        v-for="s in STRATEGIES"
        :key="s.key"
        :strategy-key="s.key"
        :title="s.title"
        :description="s.description"
        :icon="s.icon"
        :selected="selectedStrategy === s.key"
        :disabled="disabled"
        @select="handleSelect(s.key)"
      />
    </div>

    <PromptStrategyDetail
      :strategy="selectedStrategy"
      :custom-draft-ready="promptBlock.customDraftReady"
      :custom-draft="promptBlock.customDraft"
      :graphrag-tuned-summary="promptBlock.graphragTunedSummary"
      :disabled="disabled || actionRunning"
      @goto-builder="$emit('goto-builder')"
    />

    <div v-if="promptBlock.confirmed" class="prompt-reset-actions">
      <el-button
        class="ckqa-el-button ckqa-el-button--ghost"
        :disabled="actionRunning"
        @click="$emit('reset-confirm')"
      >
        重新选择策略
      </el-button>
    </div>
  </section>
</template>
