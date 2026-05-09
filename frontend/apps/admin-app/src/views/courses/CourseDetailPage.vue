<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'

import { useResourceTabs } from '../../composables/useResourceTabs.js'
import { loadModulePage } from '../pages/module-loaders.js'
import { COURSE_PAGE_COPY } from './course-page-copy.js'

import CourseOverviewTab from './tabs/CourseOverviewTab.vue'
import CourseMembersTab from './tabs/CourseMembersTab.vue'
import CourseMaterialsTab from './tabs/CourseMaterialsTab.vue'
import CourseKnowledgeBasesTab from './tabs/CourseKnowledgeBasesTab.vue'

const route = useRoute()
const router = useRouter()

// loader 返回的详情 block 完整结构：blocks.course / blocks.materials / blocks.knowledgeBases
const detail = ref({ loading: true, data: null, error: null })

const { activeTab, setActiveTab } = useResourceTabs({
  route,
  router,
  tabs: COURSE_PAGE_COPY.detail.tabs,
  // route.meta.defaultTab 由 routes.js 为 /members /materials 子路径注入
  fallback: route.meta?.defaultTab || 'overview',
})

const tabComponents = {
  overview: CourseOverviewTab,
  members: CourseMembersTab,
  materials: CourseMaterialsTab,
  'knowledge-bases': CourseKnowledgeBasesTab,
}

async function loadDetail() {
  detail.value.loading = true
  try {
    const courseId = route.params.courseId
    const result = await loadModulePage(
      { name: 'course-detail', params: { courseId } },
      {},
    )
    if (result?.requestState === 'error') {
      detail.value = { loading: false, data: null, error: result.error }
      return
    }
    detail.value = { loading: false, data: result, error: null }

    // 面包屑上下文链：生产 / 课程 / {课程名}
    const courseName = result?.blocks?.course?.item?.courseName || result?.summary || '课程详情'
    route.meta.contextChain = [
      { label: courseName, to: `/app/courses/${encodeURIComponent(courseId)}` },
    ]
  } catch (error) {
    detail.value = { loading: false, data: null, error }
  }
}

watch(() => route.params.courseId, loadDetail)
onMounted(loadDetail)

const course = computed(() => detail.value.data?.blocks?.course?.item || null)
const activeComponent = computed(() => tabComponents[activeTab.value])

const heroEyebrow = computed(() =>
  course.value?.courseName
    ? COURSE_PAGE_COPY.detail.eyebrowFormat(course.value.courseName)
    : '生产 · 课程',
)
</script>

<template>
  <div class="course-detail-page" data-testid="course-detail-page">
    <CkSkeleton v-if="detail.loading" variant="card" :count="1" />

    <CkEmptyState
      v-else-if="detail.error || !course"
      icon="!"
      :title="COURSE_PAGE_COPY.detail.loadError"
      :description="detail.error?.message || ''"
    />

    <template v-else>
      <CkPageHero
        :title="course.courseName"
        :subtitle="course.description || course.courseDesc || ''"
        :eyebrow="heroEyebrow"
      />

      <nav class="course-detail-page-tabs" role="tablist" aria-label="课程详情标签页">
        <button
          v-for="tab in COURSE_PAGE_COPY.detail.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="['course-detail-page-tab', { 'is-active': activeTab === tab.key }]"
          :data-testid="`course-tab-${tab.key}`"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <component :is="activeComponent" :course="course" :detail-data="detail.data" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.course-detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.course-detail-page-tabs {
  display: flex;
  gap: var(--ckqa-space-1);
  border-bottom: 1px solid var(--ckqa-border);
}
.course-detail-page-tab {
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
.course-detail-page-tab:hover {
  color: var(--ckqa-text);
}
.course-detail-page-tab.is-active {
  color: var(--ckqa-accent-strong);
  border-bottom-color: var(--ckqa-accent);
  font-weight: var(--ckqa-fw-medium);
}
.course-detail-page-tab:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--ckqa-focus);
  border-radius: var(--ckqa-radius-sm);
}
</style>
