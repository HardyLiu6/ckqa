<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPipelineHero from '../../components/common/CkPipelineHero.vue'
import CkActivityFeed from '../../components/common/CkActivityFeed.vue'
import CkTaskList from '../../components/common/CkTaskList.vue'
import CkQuickActions from '../../components/common/CkQuickActions.vue'

import { useDashboardSummary } from '../../composables/useDashboardSummary.js'
import { useDashboardFeed } from '../../composables/useDashboardFeed.js'
import { useScopeStore } from '../../stores/scope.js'
import { authStore } from '../../stores/auth.js'
import { COPY } from '../../copy/admin.js'

const scopeStore = useScopeStore()
const summary = useDashboardSummary({ scopeStore })
const feed = useDashboardFeed({ scopeStore })

function greetingPrefix() {
  const hour = new Date().getHours()
  if (hour < 12) return COPY.dashboard.greeting.morning
  if (hour < 18) return COPY.dashboard.greeting.afternoon
  return COPY.dashboard.greeting.evening
}

const greeting = computed(() => {
  const name = authStore.state.currentUser?.name || authStore.state.currentUser?.username || '老师'
  return `${greetingPrefix()}，${name}`
})

// 视觉打磨迭代（2026-05-09）：hero subtitle 改为数字化文案
// 数据源：
//   runningTaskCount 首选 summary.taskInProgressCount，降级时从 feed.state.tasks 计数
//   weeklyQaCount    首选 summary.qaWeeklyCount / summary.qaSessionCount 兜底，降级时 0
const runningTaskCount = computed(() => {
  const fromSummary = summary.state.summary?.taskInProgressCount
  if (typeof fromSummary === 'number') return fromSummary
  const tasks = feed.state.tasks
  if (Array.isArray(tasks)) return tasks.filter((t) => t.status === 'running').length
  return 0
})

const weeklyQaCount = computed(() => {
  const weekly = summary.state.summary?.qaWeeklyCount
  if (typeof weekly === 'number') return weekly
  // 降级到累计数或 0（累计数至少保证 subtitle 有信息）
  const totalQa = summary.state.summary?.qaSessionCount
  if (typeof totalQa === 'number') return totalQa
  return 0
})

const subtitle = computed(() => {
  const hasTasks = runningTaskCount.value > 0
  const hasQa = weeklyQaCount.value > 0
  if (hasTasks && hasQa) {
    return COPY.dashboard.heroSubtitle.withTasks(runningTaskCount.value, weeklyQaCount.value)
  }
  if (hasTasks) return COPY.dashboard.heroSubtitle.withOnlyTasks(runningTaskCount.value)
  if (hasQa) return COPY.dashboard.heroSubtitle.withOnlyQa(weeklyQaCount.value)
  return COPY.dashboard.heroSubtitle.empty
})

watch(() => scopeStore.state.activeCourseId, () => {
  summary.refresh()
  feed.refresh()
})

onMounted(() => {
  summary.refresh()
  feed.start()
})
onBeforeUnmount(() => {
  feed.stop()
})
</script>

<template>
  <div class="dashboard-page" data-testid="dashboard-page">
    <CkPageHero :title="greeting" :subtitle="subtitle" />

    <!-- 视觉打磨迭代：fallback 黄色长 banner 改为 hero 下方 inline pill -->
    <div v-if="summary.state.usingFallback" class="ck-fallback-pill" data-test-id="dashboard-fallback">
      <i class="ck-spinner" aria-hidden="true" />
      <span>{{ COPY.dashboard.fallbackHint }}</span>
    </div>

    <section class="dashboard-page-pipeline" :aria-label="COPY.dashboard.sectionLabels.pipeline">
      <CkPipelineHero
        :summary="summary.state.summary"
        :scope-params="scopeStore.requestParams()"
        :loading="summary.state.loading"
      />
    </section>

    <div class="dashboard-page-grid">
      <section class="dashboard-page-activity" :aria-label="COPY.dashboard.sectionLabels.activity">
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.activity }}</h2>
        <CkActivityFeed :events="feed.state.events" :loading="feed.state.loading" />
      </section>
      <section class="dashboard-page-tasks" :aria-label="COPY.dashboard.sectionLabels.tasks">
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.tasks }}</h2>
        <CkTaskList :tasks="feed.state.tasks" :loading="feed.state.loading" />
      </section>
    </div>

    <section class="dashboard-page-quick" :aria-label="COPY.dashboard.sectionLabels.quickActions">
      <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.quickActions }}</h2>
      <CkQuickActions :actions="COPY.dashboard.quickActionCards" />
    </section>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-6);
}

.dashboard-page-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: var(--ckqa-space-6);
}

@media (max-width: 1080px) {
  .dashboard-page-grid {
    grid-template-columns: 1fr;
  }
}

.dashboard-page-section-title {
  margin: 0 0 var(--ckqa-space-3);
  font-size: var(--ckqa-text-md-size);
  line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
</style>
