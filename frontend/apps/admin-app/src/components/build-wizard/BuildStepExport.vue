<script setup>
import StatusBadge from '../common/StatusBadge.vue'

defineProps({
  blocks: { type: Object, default: () => ({}) },
  operationFeedback: { type: Object, default: null },
})

const fileLabels = {
  'graphrag_normalized_docs.json': '标准化',
  'graphrag_section_docs.json': '章节输入',
  'graphrag_page_docs.json': '分页输入',
}
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
    <div class="artifact-matrix">
      <article v-for="row in blocks.exportArtifacts?.items" :key="row.id" class="artifact-row">
        <header>
          <strong>{{ row.title }}</strong>
          <StatusBadge :status="row.status" />
        </header>
        <div class="artifact-cells">
          <span v-for="file in row.requiredFiles" :key="`${row.id}-${file.fileName}`" class="artifact-cell" :data-status="file.status">
            <strong>{{ fileLabels[file.fileName] ?? file.fileName }}</strong>
            <small>{{ file.status === 'complete' ? '已就绪' : '缺失' }}</small>
          </span>
        </div>
      </article>
    </div>
    <p v-if="blocks.exportArtifacts?.state === 'empty'">暂无可检查的图谱输入产物。</p>
  </section>
</template>
