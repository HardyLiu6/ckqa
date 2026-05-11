<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { useResourceTabs } from '../../composables/useResourceTabs.js'
import {
  activateIndexRun,
  listIndexRuns as listIndexRunsApi,
} from '../../api/knowledge-bases.js'
import { listCourseMaterials } from '../../api/courses.js'
import { loadModulePage } from '../pages/module-loaders.js'

import { KB_PAGE_COPY } from './kb-page-copy.js'
import KbOverviewTab from './tabs/KbOverviewTab.vue'
import KbSourceMaterialsTab from './tabs/KbSourceMaterialsTab.vue'
import KbIndexRunsTab from './tabs/KbIndexRunsTab.vue'
import KbValidationTab from './tabs/KbValidationTab.vue'

const route = useRoute()
const router = useRouter()

const detail = ref({ loading: true, data: null, error: null })
const sourceMaterials = ref([])
const indexRuns = ref([])
const feedback = ref({ message: '', tone: '' })

const { activeTab, setActiveTab } = useResourceTabs({
  route,
  router,
  tabs: KB_PAGE_COPY.detail.tabs,
  fallback: 'overview',
})

const tabComponents = {
  overview: KbOverviewTab,
  'source-materials': KbSourceMaterialsTab,
  'index-runs': KbIndexRunsTab,
  validation: KbValidationTab,
}

const kbId = computed(() => route.params.kbId)
const knowledgeBase = computed(() => detail.value.data?.blocks?.knowledgeBase?.item ?? null)

const heroEyebrow = computed(() =>
  knowledgeBase.value?.name
    ? KB_PAGE_COPY.detail.eyebrowFormat(knowledgeBase.value.name)
    : '生产 · 知识库',
)

const statusTone = computed(() => {
  const status = String(knowledgeBase.value?.status ?? '').toLowerCase()
  if (status === 'archived') return { tone: 'blocked', label: '已归档' }
  if (knowledgeBase.value?.latestIndexRunStatus === 'running') {
    return { tone: 'running', label: '构建中' }
  }
  if (knowledgeBase.value?.activeIndexRunId) return { tone: 'success', label: '已激活' }
  return { tone: 'neutral', label: '待激活' }
})

async function loadDetail() {
  detail.value.loading = true
  try {
    const result = await loadModulePage(
      { name: 'knowledge-base-detail', params: { kbId: kbId.value } },
      {},
    )
    if (result?.requestState === 'error') {
      detail.value = { loading: false, data: null, error: result.error }
      return
    }
    detail.value = { loading: false, data: result, error: null }

    // 面包屑上下文链
    const name = result?.blocks?.knowledgeBase?.item?.name ?? '知识库详情'
    route.meta.contextChain = [
      { label: name, to: `/app/knowledge-bases/${encodeURIComponent(kbId.value)}` },
    ]

    // 同步加载来源资料 + 索引版本列表（source-materials / index-runs Tab 各自消费）
    indexRuns.value = result?.blocks?.indexRuns?.raw ?? result?.blocks?.indexRuns?.items ?? []
    loadSourceMaterials(result?.blocks?.knowledgeBase?.item?.courseId ?? '')
    if (!indexRuns.value.length) loadIndexRuns()
  } catch (error) {
    detail.value = { loading: false, data: null, error }
  }
}

async function loadSourceMaterials(courseId) {
  if (!courseId) {
    sourceMaterials.value = []
    return
  }
  try {
    const response = await listCourseMaterials(courseId)
    sourceMaterials.value = Array.isArray(response) ? response : response?.items ?? []
  } catch {
    sourceMaterials.value = []
  }
}

async function loadIndexRuns() {
  try {
    const runs = await listIndexRunsApi(kbId.value)
    indexRuns.value = Array.isArray(runs) ? runs : runs?.items ?? []
  } catch {
    indexRuns.value = []
  }
}

async function onActivate(indexRunId) {
  if (!indexRunId || !kbId.value) return
  try {
    await activateIndexRun(kbId.value, indexRunId)
    feedback.value = { message: `已激活索引 #${indexRunId}`, tone: 'success' }
    await loadDetail()
  } catch (error) {
    feedback.value = {
      message: error?.message ?? '激活失败，请稍后重试',
      tone: 'danger',
    }
  }
}

async function onActivateLatest() {
  const latestReady = indexRuns.value.find((run) => String(run.status ?? '').toLowerCase() === 'success')
  if (latestReady) {
    await onActivate(latestReady.id ?? latestReady.indexRunId)
  } else {
    feedback.value = { message: '暂无可激活的索引版本', tone: 'warning' }
  }
}

watch(kbId, loadDetail)
onMounted(loadDetail)

const activeComponent = computed(() => tabComponents[activeTab.value] ?? KbOverviewTab)
const tabProps = computed(() => {
  if (activeTab.value === 'overview') {
    return { knowledgeBase: knowledgeBase.value, indexRuns: indexRuns.value }
  }
  if (activeTab.value === 'source-materials') {
    return { materials: sourceMaterials.value, courseId: knowledgeBase.value?.courseId ?? '' }
  }
  if (activeTab.value === 'index-runs') {
    return {
      indexRuns: indexRuns.value,
      activeIndexRunId: knowledgeBase.value?.activeIndexRunId ?? null,
    }
  }
  return {}
})
</script>

<template>
  <div class="kb-detail-page" data-testid="kb-detail-page">
    <CkSkeleton v-if="detail.loading" variant="card" :count="1" />

    <CkEmptyState
      v-else-if="detail.error || !knowledgeBase"
      icon="!"
      :title="KB_PAGE_COPY.detail.loadError"
      :description="detail.error?.message || ''"
    />

    <template v-else>
      <CkPageHero
        :title="knowledgeBase.name || `知识库 ${kbId}`"
        :subtitle="knowledgeBase.description || knowledgeBase.kbDesc || ''"
        :eyebrow="heroEyebrow"
      >
        <template #actions>
          <CkStatusPill :tone="statusTone.tone" :label="statusTone.label" />
          <RouterLink
            :to="`/app/knowledge-bases/${encodeURIComponent(kbId)}/build`"
            class="kb-detail-page__build ck-pressable"
            data-testid="kb-detail-build"
          >
            {{ KB_PAGE_COPY.detail.buildCta }}
          </RouterLink>
          <button
            type="button"
            class="kb-detail-page__activate ck-pressable"
            data-testid="kb-detail-activate-latest"
            @click="onActivateLatest"
          >
            {{ KB_PAGE_COPY.detail.activateCta }}
          </button>
        </template>
      </CkPageHero>

      <div
        v-if="feedback.message"
        class="kb-detail-page__feedback"
        :data-tone="feedback.tone"
        role="status"
        aria-live="polite"
      >
        {{ feedback.message }}
      </div>

      <nav class="kb-detail-page__tabs" role="tablist" aria-label="知识库详情标签页">
        <button
          v-for="tab in KB_PAGE_COPY.detail.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="['kb-detail-page__tab', { 'is-active': activeTab === tab.key }]"
          :data-testid="`kb-tab-${tab.key}`"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <component
        :is="activeComponent"
        v-bind="tabProps"
        @activate="onActivate"
      />
    </template>
  </div>
</template>

<style scoped lang="scss">
.kb-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.kb-detail-page__build,
.kb-detail-page__activate {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  text-decoration: none;
  cursor: pointer;
  border: 1px solid transparent;
}
.kb-detail-page__build {
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  border-color: var(--ckqa-accent-strong);
}
.kb-detail-page__build:hover {
  background: var(--ckqa-accent-strong);
}
.kb-detail-page__activate {
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-color: var(--ckqa-border-strong);
}
.kb-detail-page__activate:hover {
  background: var(--ckqa-surface-muted);
}
.kb-detail-page__feedback {
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.kb-detail-page__feedback[data-tone='success'] {
  background: var(--ckqa-success-soft);
  color: var(--ckqa-success);
}
.kb-detail-page__feedback[data-tone='danger'] {
  background: var(--ckqa-danger-soft);
  color: var(--ckqa-danger);
}
.kb-detail-page__feedback[data-tone='warning'] {
  background: var(--ckqa-warning-soft);
  color: var(--ckqa-warning);
}
.kb-detail-page__tabs {
  display: flex;
  gap: var(--ckqa-space-1);
  border-bottom: 1px solid var(--ckqa-border);
}
.kb-detail-page__tab {
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
.kb-detail-page__tab:hover {
  color: var(--ckqa-text);
}
.kb-detail-page__tab.is-active {
  color: var(--ckqa-accent-strong);
  border-bottom-color: var(--ckqa-accent);
  font-weight: var(--ckqa-fw-medium);
}
.kb-detail-page__tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ckqa-focus);
  border-radius: var(--ckqa-radius-sm);
}
</style>
