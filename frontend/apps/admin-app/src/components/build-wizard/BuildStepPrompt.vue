<script setup>
import { computed } from 'vue'
import StatusBadge from '../common/StatusBadge.vue'
import PromptStrategyCard from './PromptStrategyCard.vue'
import PromptStrategyDetail from './PromptStrategyDetail.vue'
import { STRATEGIES } from './build-step-prompt-strategies.js'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
  step: { type: Object, default: null },
  actionRunning: { type: Boolean, default: false },
  operationFeedback: { type: Object, default: null },
  selectedStrategy: { type: String, default: 'default' },
  promptTuneState: { type: Object, default: null },
  promptTuneTriggering: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:strategy',
  'goto-builder',
  'prompt-tune-trigger',
  'prompt-tune-retry',
  'prompt-tune-regenerate',
])

const promptBlock = computed(() => props.blocks.prompt ?? {})
const disabled = computed(() => promptBlock.value.status === 'blocked' || promptBlock.value.readonly === true)

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
        :tagline="s.tagline"
        :pros="s.pros"
        :cons="s.cons"
        :best-for="s.bestFor"
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
      :prompt-tune-state="promptTuneState"
      :prompt-tune-triggering="promptTuneTriggering"
      :disabled="disabled || actionRunning"
      @goto-builder="$emit('goto-builder')"
      @prompt-tune-trigger="$emit('prompt-tune-trigger')"
      @prompt-tune-retry="$emit('prompt-tune-retry')"
      @prompt-tune-regenerate="$emit('prompt-tune-regenerate')"
    />
  </section>
</template>
