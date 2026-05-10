<script setup>
import { computed } from 'vue'

import CkInfoTable from '../../../components/common/CkInfoTable.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'

const props = defineProps({
  knowledgeBase: { type: Object, default: null },
  indexRuns: { type: Array, default: () => [] },
})

const entries = computed(() => {
  const kb = props.knowledgeBase
  if (!kb) return []
  const latestRun = Array.isArray(props.indexRuns) ? props.indexRuns[0] : null
  return [
    { label: '知识库名称', value: kb.name ?? kb.kbCode ?? '-' },
    { label: '所属课程', value: kb.courseId ?? '-' },
    { label: '当前状态', value: resolveStatusLabel(kb) },
    { label: '激活索引', value: kb.activeIndexRunId ? `#${kb.activeIndexRunId}` : '未激活' },
    { label: '最近构建', value: resolveLatestRunLabel(latestRun) },
    { label: '创建时间', value: kb.createdAt ?? '-' },
    { label: '更新时间', value: kb.updatedAt ?? '-' },
    { label: '描述', value: kb.description ?? kb.kbDesc ?? '-' },
  ]
})

function resolveStatusLabel(kb = {}) {
  const status = String(kb.status ?? '').toLowerCase()
  if (status === 'archived') return '已归档'
  if (status === 'active') return '已启用'
  if (status === 'draft') return '草稿'
  return status || '-'
}

function resolveLatestRunLabel(run) {
  if (!run) return '暂无'
  const id = run.id ?? run.indexRunId
  const status = run.status ?? run.displayStatus ?? ''
  return `#${id} · ${status || '状态未知'}`
}
</script>

<template>
  <section class="kb-overview-tab" data-testid="kb-overview-tab">
    <CkEmptyState
      v-if="!entries.length"
      icon="◻"
      title="暂无基础信息"
      description="请确认知识库已创建完成。"
    />
    <CkInfoTable v-else :entries="entries" :columns="2" />
  </section>
</template>

<style scoped lang="scss">
.kb-overview-tab {
  padding: var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
}
</style>
