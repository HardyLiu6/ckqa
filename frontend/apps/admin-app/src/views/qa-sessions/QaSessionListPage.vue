<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkResourceCard from '../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkPager from '../../components/common/CkPager.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { listQaSessions } from '../../api/qa-sessions.js'
import { useScopeStore } from '../../stores/scope.js'
import { QA_SESSION_PAGE_COPY } from './qa-session-copy.js'
import { buildListParams, mapSessionToCard } from './qa-session-list-model.js'

// M6a：问答会话列表页
// - 采用 M4 / M5 建立的"卡片网格 + CkPager + 范围芯片联动"资源页模板
// - 筛选由 route.query 单点驱动；范围芯片写入 courseId，URL 写入其它筛选项
// - 后端列表接口未就绪（404 / 4040）时显示"暂未开放"兜底，不阻塞详情直链

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()

const state = ref({
  loading: true,
  error: null,
  unavailable: false,
  items: [],
  pagination: null,
})
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 20)

async function load() {
  state.value.loading = true
  state.value.error = null
  state.value.unavailable = false

  const params = {
    ...buildListParams(route.query),
    ...scopeStore.requestParams(),
    page: page.value,
    size: pageSize.value,
  }

  try {
    const result = await listQaSessions(params)
    state.value.items = Array.isArray(result?.items) ? result.items : []
    state.value.pagination = result?.pagination ?? null
  } catch (error) {
    // 后端列表接口未就绪（404 / 业务码 4040）时进入兜底；其它错误正常展示
    const status = Number(error?.status)
    const code = Number(error?.code)
    if (status === 404 || code === 4040) {
      state.value.unavailable = true
      state.value.items = []
      state.value.pagination = null
    } else {
      state.value.error = error
      state.value.items = []
      state.value.pagination = null
    }
  } finally {
    state.value.loading = false
  }
}

// 卡片数据：filter 掉 id 缺失的脏数据
const cards = computed(() =>
  state.value.items
    .map((session) => mapSessionToCard(session))
    .filter((card) => card && card.id != null),
)

const total = computed(() => state.value.pagination?.total ?? cards.value.length)

// 范围芯片 / URL 筛选 / 分页变化时刷新
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
  () => [route.query.keyword, route.query.sessionType, route.query.hasAnomaly],
  () => {
    page.value = 1
    load()
  },
)

onMounted(load)

function onSessionTypeChange(event) {
  const next = event.target.value
  const query = { ...route.query, page: '1' }
  if (!next) {
    delete query.sessionType
  } else {
    query.sessionType = next
  }
  router.replace({ query })
}

function onAnomalyChange(event) {
  const query = { ...route.query, page: '1' }
  if (event.target.checked) {
    query.hasAnomaly = '1'
  } else {
    delete query.hasAnomaly
  }
  router.replace({ query })
}

const sessionTypeValue = computed(() => {
  const value = Array.isArray(route.query.sessionType)
    ? route.query.sessionType[0]
    : route.query.sessionType
  return String(value ?? '')
})

const anomalyValue = computed(() => {
  const value = Array.isArray(route.query.hasAnomaly)
    ? route.query.hasAnomaly[0]
    : route.query.hasAnomaly
  const normalized = String(value ?? '').toLowerCase()
  return normalized === '1' || normalized === 'true'
})
</script>

<template>
  <div class="qa-session-list-page" data-testid="qa-session-list-page">
    <CkPageHero
      :eyebrow="QA_SESSION_PAGE_COPY.list.eyebrow"
      :title="QA_SESSION_PAGE_COPY.list.title"
      :subtitle="QA_SESSION_PAGE_COPY.list.subtitle"
    />

    <section class="qa-session-list-page__filters" data-testid="qa-session-list-filters">
      <label class="qa-session-list-page__filter">
        <span>{{ QA_SESSION_PAGE_COPY.list.filterSessionType }}</span>
        <select :value="sessionTypeValue" @change="onSessionTypeChange" data-testid="qa-session-filter-type">
          <option value="">{{ QA_SESSION_PAGE_COPY.list.filterSessionTypeAny }}</option>
          <option value="formal">正式问答</option>
          <option value="smoke">知识库验证</option>
        </select>
      </label>

      <label class="qa-session-list-page__filter qa-session-list-page__filter--inline">
        <input
          type="checkbox"
          :checked="anomalyValue"
          data-testid="qa-session-filter-anomaly"
          @change="onAnomalyChange"
        />
        <span>{{ QA_SESSION_PAGE_COPY.list.filterAnomalyOnly }}</span>
      </label>
    </section>

    <CkSkeleton v-if="state.loading" variant="card" :count="6" />

    <CkEmptyState
      v-else-if="state.unavailable"
      icon="◐"
      :title="QA_SESSION_PAGE_COPY.list.comingSoonTitle"
      :description="QA_SESSION_PAGE_COPY.list.comingSoonDescription"
    />

    <CkEmptyState
      v-else-if="state.error"
      icon="!"
      :title="QA_SESSION_PAGE_COPY.list.loadError"
      :description="state.error?.message || ''"
    />

    <CkEmptyState
      v-else-if="!cards.length"
      icon="◻"
      :title="QA_SESSION_PAGE_COPY.list.emptyTitle"
      :description="QA_SESSION_PAGE_COPY.list.emptyDescription"
    />

    <ul v-else class="qa-session-list-page__grid">
      <li v-for="card in cards" :key="card.id">
        <CkResourceCard
          :title="card.title"
          :description="card.description"
          :status="card.status"
          :status-label="card.statusLabel"
          :meta="card.meta"
          :to="card.to"
          :data-session-type="card.sessionType"
          :data-anomaly="card.anomaly ? 'true' : null"
        >
          <template #actions>
            <CkStatusPill
              v-if="card.anomaly"
              tone="warning"
              label="异常"
              size="sm"
              :title="card.anomalyReason"
              data-testid="qa-session-anomaly-pill"
            />
          </template>
        </CkResourceCard>
      </li>
    </ul>

    <CkPager
      v-if="!state.loading && !state.unavailable && total > pageSize"
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
.qa-session-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.qa-session-list-page__filters {
  display: flex;
  gap: var(--ckqa-space-4);
  flex-wrap: wrap;
  align-items: center;
  padding: var(--ckqa-space-2) 0;
}
.qa-session-list-page__filter {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
}
.qa-session-list-page__filter select {
  min-width: 180px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-sm-size);
}
.qa-session-list-page__filter--inline {
  flex-direction: row;
  align-items: center;
  gap: 8px;
  padding-top: 18px; // 与 select 对齐
  color: var(--ckqa-text);
}
.qa-session-list-page__filter--inline input {
  accent-color: var(--ckqa-accent);
  width: 16px;
  height: 16px;
  cursor: pointer;
}
.qa-session-list-page__grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--ckqa-space-4);
}
</style>
