<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { useResourceTabs } from '../../composables/useResourceTabs.js'
import { useMaterialLifecycle } from '../../composables/useMaterialLifecycle.js'
import { useScopeStore } from '../../stores/scope.js'
import { loadModulePage } from '../pages/module-loaders.js'
import { MATERIAL_PAGE_COPY } from './material-page-copy.js'

import MaterialParseProgressTab from './tabs/MaterialParseProgressTab.vue'
import MaterialParseResultsTab from './tabs/MaterialParseResultsTab.vue'
import MaterialKbReferencesTab from './tabs/MaterialKbReferencesTab.vue'
import MaterialAuditLogTab from './tabs/MaterialAuditLogTab.vue'

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()
const lifecycle = useMaterialLifecycle({ scopeStore })

const detail = ref({ loading: true, data: null, error: null })

const tabComponents = {
  'parse-progress': MaterialParseProgressTab,
  'parse-results': MaterialParseResultsTab,
  'kb-references': MaterialKbReferencesTab,
  'audit-log': MaterialAuditLogTab,
}

// 路由命名为 material-detail 或 parse-results 都走同一个页面
// 若走 parse-results 路径 + route.meta.defaultTab 没设置时，兜底到 'parse-results'
const fallbackTab = computed(() => {
  if (route.meta?.defaultTab) return route.meta.defaultTab
  if (route.name === 'parse-results') return 'parse-results'
  return 'parse-progress'
})

const { activeTab, setActiveTab } = useResourceTabs({
  route,
  router,
  tabs: MATERIAL_PAGE_COPY.detail.tabs,
  fallback: fallbackTab.value,
})

async function loadDetail() {
  detail.value.loading = true
  try {
    const materialId = route.params.materialId
    const result = await loadModulePage(
      { name: 'material-detail', params: { materialId }, query: route.query },
      {},
    )
    if (result?.requestState === 'error') {
      detail.value = { loading: false, data: null, error: result.error }
      return
    }
    detail.value = { loading: false, data: result, error: null }

    // 面包屑上下文链：课程 + 资料两级
    const material = result?.blocks?.material?.item
    const course = result?.blocks?.course?.item || result?.raw?.course
    const chain = []
    if (course?.courseId) {
      chain.push({
        label: course.courseName || course.courseId,
        to: `/app/courses/${encodeURIComponent(course.courseId)}`,
      })
    }
    if (material?.id) {
      chain.push({
        label: material.displayName || material.fileName || `资料 ${material.id}`,
        to: `/app/materials/${encodeURIComponent(material.id)}`,
      })
    }
    route.meta.contextChain = chain
  } catch (error) {
    detail.value = { loading: false, data: null, error }
  }
}

watch(() => route.params.materialId, loadDetail)
onMounted(loadDetail)

const material = computed(() => detail.value.data?.blocks?.material?.item || null)
const courseRaw = computed(
  () => detail.value.data?.blocks?.course?.item || detail.value.data?.raw?.course || null,
)
const activeComponent = computed(() => tabComponents[activeTab.value])

const heroEyebrow = computed(() =>
  courseRaw.value?.courseName
    ? MATERIAL_PAGE_COPY.detail.eyebrowFormat(courseRaw.value.courseName)
    : '生产 · 资料',
)

const heroSubtitle = computed(() => {
  if (!material.value) return ''
  const parts = []
  if (courseRaw.value?.courseName) parts.push(courseRaw.value.courseName)
  if (material.value.fileSize) parts.push(formatFileSize(material.value.fileSize))
  if (material.value.uploadTime || material.value.createdAt) {
    parts.push(`上传于 ${material.value.uploadTime || material.value.createdAt}`)
  }
  return parts.join(' · ')
})

const parseStatusPill = computed(() => {
  if (!material.value) return null
  return {
    status: String(material.value.parseStatus ?? 'pending').toLowerCase(),
    label: material.value.parseStatusLabel || '',
  }
})

function formatFileSize(value) {
  const size = Number(value)
  if (!Number.isFinite(size) || size <= 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="material-detail-page" data-test-id="material-detail-page">
    <CkSkeleton v-if="detail.loading" variant="card" :count="1" />

    <CkEmptyState
      v-else-if="detail.error || !material"
      icon="!"
      :title="MATERIAL_PAGE_COPY.detail.loadError"
      :description="detail.error?.message || ''"
    />

    <template v-else>
      <CkPageHero
        :title="material.displayName || material.fileName || '课程资料'"
        :subtitle="heroSubtitle"
        :eyebrow="heroEyebrow"
      >
        <template v-if="parseStatusPill" #actions>
          <CkStatusPill
            :status="parseStatusPill.status"
            :label="parseStatusPill.label"
          />
        </template>
      </CkPageHero>

      <nav class="material-detail-page-tabs" role="tablist" aria-label="资料详情标签页">
        <button
          v-for="tab in MATERIAL_PAGE_COPY.detail.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="['material-detail-page-tab', { 'is-active': activeTab === tab.key }]"
          :data-test-id="`material-tab-${tab.key}`"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <component
        :is="activeComponent"
        :material="material"
        :detail-data="detail.data"
        :lifecycle="lifecycle"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.material-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.material-detail-page-tabs {
  display: flex;
  gap: var(--ckqa-space-1);
  border-bottom: 1px solid var(--ckqa-border);
}
.material-detail-page-tab {
  padding: var(--ckqa-space-2) var(--ckqa-space-4);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
  transition: color var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.material-detail-page-tab:hover {
  color: var(--ckqa-text);
}
.material-detail-page-tab.is-active {
  color: var(--ckqa-accent-strong);
  border-bottom-color: var(--ckqa-accent);
  font-weight: var(--ckqa-fw-medium);
}
.material-detail-page-tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ckqa-focus);
  border-radius: var(--ckqa-radius-sm);
}
</style>
