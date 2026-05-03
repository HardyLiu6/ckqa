<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  actionRunning: { type: Boolean, default: false },
  operationFeedback: { type: Object, default: null },
  smokeQuestion: { type: String, default: '' },
  smokeResult: { type: Object, default: null },
})

defineEmits(['update-smoke-question'])
</script>

<template>
  <section class="build-step-panel">
    <label class="field-label" for="smoke-question">验证问题</label>
    <el-input
      id="smoke-question"
      class="smoke-question-input"
      :model-value="smokeQuestion"
      :disabled="actionRunning"
      @input="$emit('update-smoke-question', $event)"
    />
    <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
      <div class="operation-feedback__heading">
        <strong>{{ operationFeedback.title }}</strong>
        <StatusBadge :status="operationFeedback.status" />
      </div>
      <p>{{ operationFeedback.message }}</p>
      <small>{{ operationFeedback.detail }}</small>
      <small v-if="operationFeedback.meta">{{ operationFeedback.meta }}</small>
    </div>
    <div v-if="smokeResult?.state === 'success'" class="result-box">
      <strong>助手摘要</strong>
      <p>{{ smokeResult.content }}</p>
    </div>
    <p v-else-if="smokeResult?.state === 'failed' && !operationFeedback" class="inline-error">
      {{ smokeResult.message }}
    </p>
  </section>
</template>
