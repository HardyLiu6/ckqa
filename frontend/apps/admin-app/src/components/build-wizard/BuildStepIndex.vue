<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
})
</script>

<template>
  <section class="build-step-panel">
    <div v-if="blocks.indexAvailability?.warning" class="sync-warning">
      {{ blocks.indexAvailability.warning }}
    </div>
    <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
      <div class="operation-feedback__heading">
        <strong>{{ operationFeedback.title }}</strong>
        <StatusBadge :status="operationFeedback.status" />
      </div>
      <p>{{ operationFeedback.message }}</p>
      <small>{{ operationFeedback.detail }}</small>
    </div>
    <ol class="build-task-list">
      <li v-for="item in blocks.indexRuns?.items" :key="item.id" class="build-task-row">
        <div>
          <strong>{{ item.title }}</strong>
          <small>{{ item.detail }}</small>
        </div>
        <StatusBadge :status="item.meta" />
      </li>
    </ol>
    <p v-if="blocks.indexRuns?.state === 'empty'">暂无索引运行。</p>
  </section>
</template>
