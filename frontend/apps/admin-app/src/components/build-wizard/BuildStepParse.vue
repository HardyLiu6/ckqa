<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
})
</script>

<template>
  <section class="build-step-panel">
    <div v-if="operationFeedback" class="operation-feedback" :data-status="operationFeedback.status">
      <div class="operation-feedback__heading">
        <strong>{{ operationFeedback.title }}</strong>
        <StatusBadge :status="operationFeedback.status" />
      </div>
      <p>{{ operationFeedback.message }}</p>
      <small>{{ operationFeedback.detail }}</small>
    </div>
    <ol class="build-task-list">
      <li v-for="row in blocks.parseTasks?.items" :key="row.id" class="build-task-row">
        <div>
          <strong>{{ row.title }}</strong>
          <small>{{ row.detail }}</small>
        </div>
        <el-progress :percentage="row.percent" :status="row.status === 'failed' ? 'exception' : undefined" />
        <StatusBadge :status="row.status" />
      </li>
    </ol>
    <p v-if="blocks.parseTasks?.state === 'empty'">请先选择需要构建的课程资料。</p>
  </section>
</template>
