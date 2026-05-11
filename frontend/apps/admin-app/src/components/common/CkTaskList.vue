<script setup>
import { computed } from 'vue'

import CkEmptyState from './CkEmptyState.vue'
import CkSkeleton from './CkSkeleton.vue'

import {
  resolveTaskAccent,
  formatTaskProgress,
  sortTasks,
} from './task-list-model.js'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const sorted = computed(() => sortTasks(props.tasks))
</script>

<template>
  <section class="ck-task-list ck-glass-card">
    <CkSkeleton v-if="loading" variant="row" :count="3" />
    <CkEmptyState
      v-else-if="!sorted.length"
      icon="◐"
      title="暂无进行中任务"
      description="解析、索引、验证任务进入运行状态后会显示在这里。"
    />
    <ul v-else class="ck-task-list-items">
      <li
        v-for="task in sorted"
        :key="task.id"
        class="ck-task-list-item"
        :data-tone="resolveTaskAccent(task.status)"
      >
        <RouterLink v-if="task.to" :to="task.to" class="ck-task-list-item-title">
          {{ task.title }}
        </RouterLink>
        <span v-else class="ck-task-list-item-title">{{ task.title }}</span>
        <div
          class="ck-task-list-item-progress"
          role="progressbar"
          :aria-label="`${task.title} 进度`"
          :aria-valuenow="Math.round((Number(task.progress) || 0) * 100)"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span
            class="ck-task-list-item-progress-fill"
            :style="{ width: formatTaskProgress(task.progress) }"
          />
        </div>
        <span class="ck-task-list-item-pct">{{ formatTaskProgress(task.progress) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.ck-task-list {
  padding: 18px;
  min-height: 200px;
}

.ck-task-list-items {
  position: relative;
  z-index: 1;
  list-style: none;
  margin: 0;
  padding: 0;
}

.ck-task-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 6px var(--ckqa-space-3);
  padding: var(--ckqa-space-3) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}

.ck-task-list-item:last-child {
  border-bottom: none;
}

.ck-task-list-item-title {
  grid-column: 1 / 2;
  grid-row: 1;
  font-size: var(--ckqa-text-base-size);
  line-height: var(--ckqa-text-base-line);
  color: var(--ckqa-text);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ck-task-list-item-title:hover {
  color: var(--ckqa-accent-strong);
}

.ck-task-list-item-pct {
  grid-column: 2 / 3;
  grid-row: 1;
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 发光渐变进度条：由 div + span 实现，避免 progress 元素跨浏览器样式冲突 */
.ck-task-list-item-progress {
  grid-column: 1 / 3;
  grid-row: 2;
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: var(--ckqa-progress-track);
  overflow: hidden;
}

.ck-task-list-item-progress-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  box-shadow: var(--ckqa-shadow-progress-glow-accent);
  border-radius: 2px;
  transition: width var(--ckqa-duration-glass) var(--ckqa-ease-glass);
}

.ck-task-list-item[data-tone='danger'] .ck-task-list-item-progress-fill {
  background: linear-gradient(90deg, var(--ckqa-danger), var(--ckqa-danger-strong));
  box-shadow: var(--ckqa-shadow-progress-glow-danger);
}

.ck-task-list-item[data-tone='warning'] .ck-task-list-item-progress-fill {
  background: linear-gradient(90deg, var(--ckqa-warning), var(--ckqa-warning-strong));
  box-shadow: var(--ckqa-shadow-progress-glow-warning);
}

.ck-task-list-item[data-tone='success'] .ck-task-list-item-progress-fill {
  background: linear-gradient(90deg, var(--ckqa-success), var(--ckqa-success-strong));
  box-shadow: var(--ckqa-shadow-progress-glow-success);
}
</style>
