<script setup>
import { computed } from 'vue'

import CkResourceCard from '../../../components/common/CkResourceCard.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import { COURSE_PAGE_COPY } from '../course-page-copy.js'

const props = defineProps({
  course: { type: Object, required: true },
  detailData: { type: Object, default: null },
})

// 资料列表直接从 CourseDetailPage 的 loader 结果复用（blocks.materials.items），
// 避免重复请求；如果后续需要分页，再独立走 course-materials 分支。
const items = computed(() => props.detailData?.blocks?.materials?.items ?? [])
const blockState = computed(() => props.detailData?.blocks?.materials?.state)

const isArchived = computed(() => String(props.course?.status ?? '').toLowerCase() === 'archived')
</script>

<template>
  <div class="course-materials-tab" data-test-id="course-materials-tab">
    <header class="course-materials-tab-header">
      <p v-if="isArchived" class="course-materials-tab-archived-hint">
        {{ COURSE_PAGE_COPY.materials.archivedHint }}
      </p>
      <button v-else type="button" class="course-materials-tab-upload ck-pressable">
        {{ COURSE_PAGE_COPY.materials.uploadCta }}
      </button>
    </header>

    <CkEmptyState
      v-if="blockState === 'error'"
      icon="!"
      title="资料列表加载失败"
    />

    <CkEmptyState
      v-else-if="!items.length"
      icon="↑"
      :title="COURSE_PAGE_COPY.materials.emptyTitle"
      :description="COURSE_PAGE_COPY.materials.emptyDescription"
    />

    <ul v-else class="course-materials-tab-grid">
      <li v-for="material in items" :key="material.id">
        <CkResourceCard
          :title="material.title"
          :description="material.detail"
          :status="material.meta"
          :meta="[
            { label: '更新', value: material.updatedAt },
          ]"
          :to="material.to"
        />
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.course-materials-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.course-materials-tab-header {
  display: flex;
  justify-content: flex-end;
}
.course-materials-tab-archived-hint {
  margin: 0;
  padding: var(--ckqa-space-2);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.course-materials-tab-upload {
  padding: 6px 14px;
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
  border: none;
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  font-weight: var(--ckqa-fw-medium);
  cursor: pointer;
}
.course-materials-tab-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
