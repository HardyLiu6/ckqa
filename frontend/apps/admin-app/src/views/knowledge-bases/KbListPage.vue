<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkResourceCard from '../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkPager from '../../components/common/CkPager.vue'

import { loadModulePage } from '../pages/module-loaders.js'
import { useScopeStore } from '../../stores/scope.js'
import { KB_PAGE_COPY } from './kb-page-copy.js'

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()

// 知识库列表复用 loadModulePage('knowledge-bases', params) 分支；
// 下面的 mapping 与 module-loaders.js 的 mapKnowledgeBaseRow 输出保持契约。
const state = ref({ loading: true, error: null, rows: [], pagination: null })
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 20)

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage(
      { name: 'knowledge-bases', params: {} },
      {
        ...scopeStore.requestParams(),
        page: page.value,
        size: pageSize.value,
        keyword: route.query.keyword ?? '',
        status: route.query.status ?? '',
      },
    )
    if (result?.requestState === 'error') {
      state.value.error = result.error
      state.value.rows = []
      state.value.pagination = null
    } else {
      state.value.rows = result?.rows ?? []
      state.value.pagination = result?.pagination ?? null
      state.value.error = null
    }
  } catch (error) {
    state.value.error = error
    state.value.rows = []
  } finally {
    state.value.loading = false
  }
}

// 范围芯片 / 分页 / 筛选变化时触发重载
watch(
  () => scopeStore.state.activeCourseId,
  () => {
    page.value = 1
    load()
  },
)

watch(page, () => {
  router.replace({ query: { ...route.query, page: String(page.value) } })
  load()
})

watch(pageSize, (next) => {
  router.replace({ query: { ...route.query, pageSize: String(next), page: '1' } })
  page.value = 1
})

watch(
  () => [route.query.keyword, route.query.status],
  () => {
    page.value = 1
    load()
  },
)

onMounted(load)

// loader rows 的 cells 结构：[知识库名, 课程ID, statusCell, 激活索引文案, latestIndexCell, 更新时间]
// raw 是原始 KB 对象，含 status / activeIndexRunId / courseId / name / updatedAt 等字段
const cards = computed(() =>
  state.value.rows.map((row) => {
    const raw = row.raw ?? {}
    return {
      id: row.id,
      to: row.to,
      buildTo: row.buildTo,
      title: raw.name ?? raw.kbCode ?? `知识库 ${row.id}`,
      description: raw.description ?? raw.kbDesc ?? '',
      status: resolveKbCardStatus(raw),
      statusLabel: resolveKbStatusLabel(raw),
      meta: [
        { label: '所属课程', value: raw.courseId ?? '-' },
        { label: '激活版本', value: raw.activeIndexRunId ? `#${raw.activeIndexRunId}` : '未激活' },
      ],
    }
  }),
)

function resolveKbCardStatus(kb = {}) {
  const status = String(kb.status ?? '').toLowerCase()
  if (status === 'archived') return 'archived'
  if (kb.latestIndexRunStatus === 'running') return 'running'
  if (kb.activeIndexRunId) return 'active'
  return 'draft'
}

function resolveKbStatusLabel(kb = {}) {
  const status = String(kb.status ?? '').toLowerCase()
  if (status === 'archived') return '已归档'
  if (kb.latestIndexRunStatus === 'running') return '构建中'
  if (kb.activeIndexRunId) return '已激活'
  if (kb.latestIndexRunId) return '待激活'
  return '草稿'
}

const total = computed(() => state.value.pagination?.total ?? state.value.rows.length)
</script>

<template>
  <div class="kb-list-page" data-testid="kb-list-page">
    <CkPageHero
      :title="KB_PAGE_COPY.list.title"
      :subtitle="KB_PAGE_COPY.list.subtitle"
      :eyebrow="KB_PAGE_COPY.list.eyebrow"
    >
      <template #actions>
        <RouterLink
          class="kb-list-page__create ck-pressable"
          to="/app/knowledge-bases?action=create"
          data-testid="kb-list-create"
        >
          {{ KB_PAGE_COPY.list.createCta }}
        </RouterLink>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state.loading" variant="card" :count="6" />

    <CkEmptyState
      v-else-if="state.error"
      icon="!"
      :title="KB_PAGE_COPY.list.loadError"
      :description="state.error?.message || ''"
    />

    <CkEmptyState
      v-else-if="!cards.length"
      icon="◻"
      :title="KB_PAGE_COPY.list.emptyTitle"
      :description="KB_PAGE_COPY.list.emptyDescription"
    />

    <ul v-else class="kb-list-page__grid">
      <li v-for="card in cards" :key="card.id">
        <CkResourceCard
          :title="card.title"
          :description="card.description"
          :status="card.status"
          :status-label="card.statusLabel"
          :meta="card.meta"
          :to="card.to"
        >
          <template #actions>
            <RouterLink
              v-if="card.buildTo"
              :to="card.buildTo"
              class="kb-list-page__build-link ck-pressable"
              :data-testid="`kb-list-build-${card.id}`"
            >
              继续构建 →
            </RouterLink>
          </template>
        </CkResourceCard>
      </li>
    </ul>

    <CkPager
      v-if="!state.loading && total > pageSize"
      variant="page"
      :page="page"
      :page-size="pageSize"
      :total="total"
      @change-page="(p) => (page = p)"
      @change-page-size="(s) => (pageSize = s)"
    />
  </div>
</template>

<style scoped lang="scss">
.kb-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.kb-list-page__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--ckqa-space-4);
}
.kb-list-page__create {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  text-decoration: none;
  font-weight: var(--ckqa-fw-medium);
}
.kb-list-page__build-link {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-accent);
  background: var(--ckqa-accent-soft);
  padding: 4px 8px;
  border-radius: var(--ckqa-radius-full);
  text-decoration: none;
}
</style>
