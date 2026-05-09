<script setup>
import { computed } from 'vue'

import CkResourceCard from '../../../components/common/CkResourceCard.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import { COURSE_PAGE_COPY } from '../course-page-copy.js'

const props = defineProps({
  course: { type: Object, required: true },
  detailData: { type: Object, default: null },
})

// 知识库列表直接复用 detailData 中 blocks.knowledgeBases.items
const items = computed(() => props.detailData?.blocks?.knowledgeBases?.items ?? [])
const blockState = computed(() => props.detailData?.blocks?.knowledgeBases?.state)
</script>

<template>
  <div class="course-kb-tab" data-test-id="course-kb-tab">
    <CkEmptyState
      v-if="blockState === 'error'"
      icon="!"
      title="知识库列表加载失败"
    />

    <CkEmptyState
      v-else-if="!items.length"
      icon="◇"
      :title="COURSE_PAGE_COPY.knowledgeBases.emptyTitle"
      :description="COURSE_PAGE_COPY.knowledgeBases.emptyDescription"
    />

    <ul v-else class="course-kb-tab-grid">
      <li v-for="kb in items" :key="kb.id">
        <CkResourceCard
          :title="kb.title"
          :description="kb.detail"
          :status="kb.meta"
          :to="kb.to"
        />
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.course-kb-tab {
  padding: var(--ckqa-space-3) 0;
}
.course-kb-tab-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
