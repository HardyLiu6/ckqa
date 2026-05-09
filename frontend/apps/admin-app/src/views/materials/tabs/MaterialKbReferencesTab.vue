<script setup>
import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

// 当前 Java /api/v1 暂未暴露"资料被哪些知识库引用"的端点
// 先以只读占位的方式呈现，等后端契约就绪再接入
defineProps({
  material: { type: Object, required: true },
})
</script>

<template>
  <div class="material-kb-tab" data-test-id="material-kb-references-tab">
    <CkEmptyState
      icon="◇"
      :title="MATERIAL_PAGE_COPY.kbReferences.emptyTitle"
      :description="MATERIAL_PAGE_COPY.kbReferences.emptyDescription"
    />
  </div>
</template>

<style scoped lang="scss">
.material-kb-tab {
  padding: var(--ckqa-space-3) 0;
}
</style>
