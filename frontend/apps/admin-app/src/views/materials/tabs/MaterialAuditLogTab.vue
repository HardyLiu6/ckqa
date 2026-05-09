<script setup>
import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

// 当前 Java /api/v1 暂未对资料粒度的操作日志提供专用端点
// 先以只读占位呈现，留出接入点；未来可改为复用 /api/v1/audit-logs?materialId=... 查询
defineProps({
  material: { type: Object, required: true },
})
</script>

<template>
  <div class="material-audit-log-tab" data-test-id="material-audit-log-tab">
    <CkEmptyState
      icon="·"
      :title="MATERIAL_PAGE_COPY.auditLog.emptyTitle"
      :description="MATERIAL_PAGE_COPY.auditLog.emptyDescription"
    />
  </div>
</template>

<style scoped lang="scss">
.material-audit-log-tab {
  padding: var(--ckqa-space-3) 0;
}
</style>
