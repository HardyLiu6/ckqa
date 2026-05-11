<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'
import CkEmptyState from './CkEmptyState.vue'
import CkSkeleton from './CkSkeleton.vue'

import {
  groupEventsByPeriod,
  formatEventWhen,
  resolveEventTone,
} from './activity-feed-model.js'

const props = defineProps({
  events: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  now: { type: Number, default: () => Date.now() },
})

const groups = computed(() => groupEventsByPeriod(props.events, props.now))
</script>

<template>
  <section class="ck-activity-feed ck-glass-card">
    <CkSkeleton v-if="loading" variant="row" :count="4" />
    <CkEmptyState
      v-else-if="!groups.length"
      icon="·"
      title="近期没有新动态"
      description="新的解析、构建、激活、验证记录会出现在这里。"
    />
    <ol v-else class="ck-activity-feed-list">
      <template v-for="group in groups" :key="group.key">
        <li class="ck-activity-feed-section-title">{{ group.label }}</li>
        <li
          v-for="event in group.items"
          :key="event.id"
          class="ck-activity-feed-item"
        >
          <CkStatusPill :tone="resolveEventTone(event.type)" :label="event.statusLabel || ''" size="sm" />
          <div class="ck-activity-feed-item-body">
            <RouterLink v-if="event.to" :to="event.to" class="ck-activity-feed-item-title">{{ event.title }}</RouterLink>
            <span v-else class="ck-activity-feed-item-title">{{ event.title }}</span>
            <span v-if="event.sub" class="ck-activity-feed-item-sub">{{ event.sub }}</span>
          </div>
          <time class="ck-activity-feed-item-when" :datetime="new Date(event.when).toISOString()">
            {{ formatEventWhen(event.when, now) }}
          </time>
        </li>
      </template>
    </ol>
  </section>
</template>

<style scoped lang="scss">
.ck-activity-feed {
  width: 100%;
  padding: 18px;
  min-height: 200px;
}

.ck-activity-feed-list {
  position: relative;
  z-index: 1;
  list-style: none;
  margin: 0;
  padding: 0;
}

.ck-activity-feed-section-title {
  margin-top: var(--ckqa-space-3);
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  text-transform: uppercase;
  letter-spacing: var(--ckqa-tracking-wide);
  font-weight: var(--ckqa-fw-semibold);
}

.ck-activity-feed-section-title:first-child {
  margin-top: 0;
}

.ck-activity-feed-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--ckqa-space-3);
  align-items: center;
  padding: var(--ckqa-space-2) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}

.ck-activity-feed-item:last-child {
  border-bottom: none;
}

.ck-activity-feed-item-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.ck-activity-feed-item-title {
  font-size: var(--ckqa-text-base-size);
  line-height: var(--ckqa-text-base-line);
  color: var(--ckqa-text);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ck-activity-feed-item-title:hover {
  color: var(--ckqa-accent-strong);
}

.ck-activity-feed-item-sub {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
}

.ck-activity-feed-item-when {
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  flex-shrink: 0;
}
</style>
