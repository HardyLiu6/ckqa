<script setup>
import { computed } from 'vue'

import CkInfoTable from '../../../components/common/CkInfoTable.vue'

const props = defineProps({
  course: { type: Object, required: true },
  detailData: { type: Object, default: null },
})

// 字节数格式化：支持 byte / KB / MB / GB
function formatBytes(b) {
  if (typeof b !== 'number' || !Number.isFinite(b) || b <= 0) return ''
  const KB = 1024
  const MB = KB * 1024
  const GB = MB * 1024
  if (b >= GB) return `${(b / GB).toFixed(1)} GB`
  if (b >= MB) return `${(b / MB).toFixed(1)} MB`
  if (b >= KB) return `${(b / KB).toFixed(1)} KB`
  return `${b} B`
}

const entries = computed(() => {
  const course = props.course || {}
  const materials = props.detailData?.blocks?.materials
  const knowledgeBases = props.detailData?.blocks?.knowledgeBases
  const materialCount = materials?.items?.length ?? course.materialCount
  const kbCount = knowledgeBases?.items?.length ?? course.knowledgeBaseCount
  return [
    { label: '课程编码', value: course.courseId },
    { label: '课程状态', value: resolveStatusLabel(course.status) },
    { label: '资料总数', value: materialCount },
    { label: '资料总大小', value: formatBytes(course.materialBytes) },
    { label: '知识库数', value: kbCount },
    { label: '激活版本', value: course.activeKbVersion },
    { label: '创建时间', value: course.createdAt },
    { label: '最近更新', value: course.updatedAt },
  ]
})

function resolveStatusLabel(status) {
  const normalized = String(status ?? '').toLowerCase()
  if (normalized === 'archived') return '已归档'
  if (normalized === 'active') return '进行中'
  if (!normalized) return ''
  return normalized
}
</script>

<template>
  <div class="course-overview-tab" data-test-id="course-overview-tab">
    <CkInfoTable :entries="entries" :columns="2" />
  </div>
</template>

<style scoped lang="scss">
.course-overview-tab {
  padding: var(--ckqa-space-3) 0;
}
</style>
