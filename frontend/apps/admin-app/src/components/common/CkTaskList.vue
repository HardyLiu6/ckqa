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
  <section class="ck-task-list" aria-label="进行中任务">
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
        <progress
          class="ck-task-list-item-progress"
          :value="task.progress || 0"
          max="1"
          :aria-label="`${task.title} 进度`"
        />
        <span class="ck-task-list-item-pct">{{ formatTaskProgress(task.progress) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.ck-task-list-items { list-style: none; margin: 0; padding: 0; }
.ck-task-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px var(--ckqa-space-3);
  padding: var(--ckqa-space-2) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.ck-task-list-item:last-child { border-bottom: none; }
.ck-task-list-item-title {
  grid-column: 1 / 2; grid-row: 1;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  text-decoration: none;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ck-task-list-item-title:hover { color: var(--ckqa-accent-strong); }
.ck-task-list-item-pct {
  grid-column: 2 / 3; grid-row: 1;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.ck-task-list-item-progress {
  grid-column: 1 / 3; grid-row: 2;
  width: 100%; height: 6px;
  appearance: none;
  border: none;
  background: var(--ckqa-surface-muted);
  border-radius: 6px;
}
.ck-task-list-item-progress::-webkit-progress-bar {
  background: var(--ckqa-surface-muted);
  border-radius: 6px;
}
.ck-task-list-item-progress::-webkit-progress-value {
  background: var(--ckqa-running);
  border-radius: 6px;
}
.ck-task-list-item[data-tone='running'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-accent); }
.ck-task-list-item[data-tone='danger'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-danger); }
.ck-task-list-item[data-tone='warning'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-warning); }
.ck-task-list-item[data-tone='success'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-success); }
</style>
