<script setup>
import { computed } from 'vue'

import CkStatusPill from '../../../components/common/CkStatusPill.vue'
import {
  SESSION_TYPE_LABELS,
  resolveSessionAnomaly,
  resolveSessionStatusTone,
} from '../qa-session-list-model.js'
import { resolveSessionTitle } from '../qa-session-detail-model.js'

const props = defineProps({
  session: { type: Object, default: null },
})

const title = computed(() => resolveSessionTitle(props.session))

const typeLabel = computed(() => {
  const type = String(props.session?.sessionType ?? '').toLowerCase()
  return SESSION_TYPE_LABELS[type] ?? '会话'
})

const userLabel = computed(() => {
  if (!props.session) return ''
  return (
    props.session.userDisplayName
    ?? props.session.userName
    ?? (props.session.userId != null ? `用户 ${props.session.userId}` : '')
  )
})

const courseLabel = computed(() => {
  if (!props.session?.courseId) return ''
  return String(props.session.courseId)
})

const createdAtLabel = computed(() => props.session?.createdAt ?? '')

const statusTone = computed(() => resolveSessionStatusTone(props.session ?? {}))
const anomaly = computed(() => resolveSessionAnomaly(props.session ?? {}))
</script>

<template>
  <header class="qa-session-header" data-testid="qa-session-header">
    <div class="qa-session-header__main">
      <span class="qa-session-header__eyebrow">运维 · 问答会话</span>
      <h1 class="qa-session-header__title">{{ title }}</h1>
      <ul class="qa-session-header__meta">
        <li v-if="typeLabel" :data-type="session?.sessionType">{{ typeLabel }}</li>
        <li v-if="userLabel">学员 {{ userLabel }}</li>
        <li v-if="courseLabel">课程 {{ courseLabel }}</li>
        <li v-if="createdAtLabel">创建于 {{ createdAtLabel }}</li>
      </ul>
    </div>
    <div class="qa-session-header__actions">
      <CkStatusPill :tone="statusTone.tone" :label="statusTone.label" />
      <CkStatusPill
        v-if="anomaly"
        tone="warning"
        label="异常"
        size="sm"
        data-testid="qa-session-header-anomaly"
      />
    </div>
  </header>
</template>

<style scoped lang="scss">
.qa-session-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--ckqa-space-4);
  flex-wrap: wrap;
}
.qa-session-header__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.qa-session-header__eyebrow {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
}
.qa-session-header__title {
  margin: 0;
  font-size: var(--ckqa-text-2xl-size);
  line-height: var(--ckqa-text-2xl-line);
  font-weight: var(--ckqa-fw-semibold);
  letter-spacing: var(--ckqa-tracking-tight);
  color: var(--ckqa-text);
}
.qa-session-header__meta {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--ckqa-space-3);
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
}
.qa-session-header__meta li[data-type='smoke'] {
  color: var(--ckqa-accent-strong);
  font-weight: var(--ckqa-fw-medium);
}
.qa-session-header__actions {
  display: flex;
  align-items: center;
  gap: var(--ckqa-space-2);
  flex-shrink: 0;
}
</style>
