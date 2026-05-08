<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPipelineHero from '../../components/common/CkPipelineHero.vue'
import CkActivityFeed from '../../components/common/CkActivityFeed.vue'
import CkTaskList from '../../components/common/CkTaskList.vue'

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

const subtitle = computed(() => {
  if (!summary.state.summary) return COPY.dashboard.summarySentence(null, null, null)
  return COPY.dashboard.summarySentence(
    summary.state.summary.courseCount,
    summary.state.summary.materialCount,
    summary.state.summary.knowledgeBaseCount,
  )
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
  <div class="dashboard-page">
    <CkPageHero :title="greeting" :subtitle="subtitle" eyebrow="工作台" />

    <p v-if="summary.state.usingFallback" class="dashboard-page-fallback-hint">
      {{ COPY.dashboard.fallbackHint }}
    </p>

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
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.quickActions }}</h2>
        <ul class="dashboard-page-quick-actions">
          <li v-for="action in COPY.dashboard.quickActions" :key="action.key">
            <RouterLink :to="action.to">{{ action.label }}</RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page { display: flex; flex-direction: column; gap: var(--ckqa-space-6); }
.dashboard-page-fallback-hint {
  margin: 0;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-warning-soft);
  border-left: 3px solid var(--ckqa-warning);
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
}
.dashboard-page-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: var(--ckqa-space-6);
}
@media (max-width: 1080px) {
  .dashboard-page-grid { grid-template-columns: 1fr; }
}
.dashboard-page-section-title {
  margin: 0 0 var(--ckqa-space-3);
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.dashboard-page-section-title + .dashboard-page-section-title { margin-top: var(--ckqa-space-5); }
.dashboard-page-quick-actions {
  list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--ckqa-space-2);
}
.dashboard-page-quick-actions li a {
  display: block; padding: var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text);
  text-decoration: none;
  font-size: var(--ckqa-text-sm-size);
}
.dashboard-page-quick-actions li a:hover {
  background: var(--ckqa-accent-soft);
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent-strong);
}
</style>
