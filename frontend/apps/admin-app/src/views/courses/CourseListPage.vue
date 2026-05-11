<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkResourceCard from '../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkPager from '../../components/common/CkPager.vue'

import { loadModulePage, DEFAULT_COURSE_COVER_URL } from '../pages/module-loaders.js'
import { useScopeStore } from '../../stores/scope.js'
import { COURSE_PAGE_COPY } from './course-page-copy.js'

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()

// 课程列表使用卡片网格；复用 loadModulePage 的课程分支（route.name === 'courses'）
const state = ref({ loading: true, error: null, rows: [], pagination: null })
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 20)

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage(
      { name: 'courses', params: {} },
      {
        ...scopeStore.requestParams(),
        page: page.value,
        size: pageSize.value,
      },
    )
    // loader 已经做了 requestState 标识，这里只关心成功/空态/错误分支
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

// 当范围芯片变化时，列表重新加载（范围对 course 列表本身其实不生效，
// 但教师 / 助教切课时其他列表会跟着更新，统一监听保持一致）
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

onMounted(load)

// 卡片展示用的派生数据：loader 的 rows 已经包含 raw + to，直接映射即可
const cards = computed(() =>
  state.value.rows.map((row) => {
    const course = row.raw ?? {}
    const status = String(course.status ?? '').toLowerCase()
    return {
      id: row.id,
      to: row.to,
      title: course.courseName || course.courseId || '未命名课程',
      description: course.description || course.courseDesc || '',
      status: status === 'archived' ? 'archived' : 'active',
      cover: row.thumbnailUrl || DEFAULT_COURSE_COVER_URL,
      meta: [
        { label: '资料', value: course.materialCount },
        { label: '知识库', value: course.knowledgeBaseCount },
      ],
    }
  }),
)

const total = computed(() => state.value.pagination?.total ?? state.value.rows.length)
</script>

<template>
  <div class="course-list-page" data-testid="course-list-page">
    <CkPageHero
      :title="COURSE_PAGE_COPY.list.title"
      :subtitle="COURSE_PAGE_COPY.list.subtitle"
    >
      <template #actions>
        <RouterLink
          class="course-list-page-create ck-pressable"
          to="/app/courses?action=create"
          data-testid="course-list-create"
        >
          {{ COURSE_PAGE_COPY.list.createCta }}
        </RouterLink>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state.loading" variant="card" :count="6" />

    <CkEmptyState
      v-else-if="state.error"
      icon="!"
      :title="COURSE_PAGE_COPY.list.loadError"
      :description="state.error?.message || ''"
    />

    <CkEmptyState
      v-else-if="!cards.length"
      icon="◻"
      :title="COURSE_PAGE_COPY.list.emptyTitle"
      :description="COURSE_PAGE_COPY.list.emptyDescription"
    />

    <ul v-else class="course-list-page-grid">
      <li v-for="card in cards" :key="card.id">
        <CkResourceCard
          :title="card.title"
          :description="card.description"
          :status="card.status"
          :cover="card.cover"
          :meta="card.meta"
          :to="card.to"
        />
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
.course-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.course-list-page-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--ckqa-space-4);
}
.course-list-page-create {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  text-decoration: none;
  font-weight: var(--ckqa-fw-medium);
}
</style>
