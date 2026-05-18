<script setup>
import { computed } from 'vue'
import { formatTokens } from './candidates-selection-model.js'

const props = defineProps({
  totalCandidates:    { type: Number, required: true },
  candidateCount:     { type: Number, required: true },
  totalCalls:         { type: Number, required: true },
  estimatedTokens:    { type: Number, required: true },
  estimatedMinutes:   { type: Number, required: true },
})

const formattedTokens = computed(() => formatTokens(props.estimatedTokens))
</script>

<template>
  <header class="candidate-summary-bar">
    <div>
      <div class="candidate-summary-bar__label">已生成</div>
      <div class="candidate-summary-bar__value">{{ totalCandidates }} 个候选</div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">本次将评分</div>
      <div class="candidate-summary-bar__value">
        <strong>{{ candidateCount }}</strong> 个候选 · {{ totalCalls }} 次大模型调用
      </div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">预估 token 消耗</div>
      <div class="candidate-summary-bar__value">{{ formattedTokens }}</div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">预估时长</div>
      <div class="candidate-summary-bar__value">~ {{ estimatedMinutes }} min</div>
    </div>
  </header>
</template>
