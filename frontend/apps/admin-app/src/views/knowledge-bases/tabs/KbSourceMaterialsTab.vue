<script setup>
import { computed } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import CkResourceCard from '../../../components/common/CkResourceCard.vue'

import { KB_PAGE_COPY } from '../kb-page-copy.js'

const props = defineProps({
  // 来源资料（来自 loadKnowledgeBaseBuild 的 blocks.materials 或 blocks.selection）
  materials: { type: Array, default: () => [] },
  courseId: { type: String, default: '' },
})

// 资料卡片数据：调用 API 层直接返回的 material 对象
const cards = computed(() =>
  (Array.isArray(props.materials) ? props.materials : []).map((material) => {
    const id = material.id ?? material.materialId ?? material.pdfFileId
    const parseStatus = String(material.parseStatus ?? '').toLowerCase() || 'pending'
    return {
      id,
      title: material.displayName ?? material.fileName ?? `资料 ${id}`,
      description: material.description ?? '',
      status: mapMaterialParseStatus(parseStatus),
      statusLabel: mapMaterialParseStatusLabel(parseStatus),
      to: id ? buildMaterialDetailPath(id, material.courseId ?? props.courseId) : null,
      meta: [
        { label: '类型', value: material.materialType ?? '-' },
        { label: '大小', value: formatFileSize(material.fileSize) },
      ],
    }
  }),
)

function mapMaterialParseStatus(status) {
  if (status === 'done' || status === 'success') return 'ready'
  if (status === 'processing' || status === 'running') return 'running'
  if (status === 'failed') return 'failed'
  return 'pending'
}

function mapMaterialParseStatusLabel(status) {
  if (status === 'done' || status === 'success') return '解析完成'
  if (status === 'processing' || status === 'running') return '解析中'
  if (status === 'failed') return '解析失败'
  return '待解析'
}

function buildMaterialDetailPath(id, courseId) {
  const encodedId = encodeURIComponent(id)
  const query = courseId ? `?courseId=${encodeURIComponent(courseId)}` : ''
  return `/app/materials/${encodedId}${query}`
}

function formatFileSize(bytes) {
  const num = Number(bytes)
  if (!Number.isFinite(num) || num <= 0) return '-'
  if (num < 1024) return `${num} B`
  if (num < 1024 * 1024) return `${(num / 1024).toFixed(1)} KB`
  return `${(num / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <section class="kb-source-materials-tab" data-testid="kb-source-materials-tab">
    <CkEmptyState
      v-if="!cards.length"
      icon="◻"
      :title="KB_PAGE_COPY.detail.sourceMaterialsEmpty"
    />
    <ul v-else class="kb-source-materials-tab__grid">
      <li v-for="card in cards" :key="card.id">
        <CkResourceCard
          :title="card.title"
          :description="card.description"
          :status="card.status"
          :status-label="card.statusLabel"
          :meta="card.meta"
          :to="card.to"
        />
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.kb-source-materials-tab {
  display: block;
}
.kb-source-materials-tab__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
