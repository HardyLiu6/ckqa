<script setup>
import { computed } from 'vue'

import {
  PAGE_SIZE_OPTIONS,
  resolveTotalPages,
  resolvePageWindow,
  resolveLoadMoreState,
} from './pager-model.js'

const props = defineProps({
  variant: { type: String, default: 'page' },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
  loaded: { type: Number, default: 0 },
})

const emit = defineEmits(['change-page', 'change-page-size', 'load-more'])

const totalPages = computed(() => resolveTotalPages({ total: props.total, pageSize: props.pageSize }))
const windowPages = computed(() => resolvePageWindow({ page: props.page, totalPages: totalPages.value }))
const loadMore = computed(() => resolveLoadMoreState({ loaded: props.loaded, total: props.total }))

function go(page) {
  if (page < 1 || page > totalPages.value || page === props.page) return
  emit('change-page', page)
}
</script>

<template>
  <nav v-if="variant === 'page'" class="ck-pager" aria-label="分页">
    <button class="ck-pager-btn" :disabled="page <= 1" @click="go(page - 1)">←</button>
    <button
      v-for="p in windowPages"
      :key="p"
      class="ck-pager-btn"
      :class="{ 'ck-pager-btn--active': p === page }"
      @click="go(p)"
    >
      {{ p }}
    </button>
    <button class="ck-pager-btn" :disabled="page >= totalPages" @click="go(page + 1)">→</button>
    <select
      class="ck-pager-size"
      :value="pageSize"
      aria-label="每页条数"
      @change="emit('change-page-size', Number($event.target.value))"
    >
      <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }} / 页</option>
    </select>
  </nav>

  <div v-else class="ck-pager-load-more">
    <button
      class="ck-pager-btn ck-pager-btn--block"
      :disabled="!loadMore.canLoadMore"
      @click="emit('load-more')"
    >
      {{ loadMore.label }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.ck-pager { display: flex; align-items: center; gap: 6px; }
.ck-pager-btn {
  min-width: 32px; height: 32px; padding: 0 10px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-pager-btn:hover:not(:disabled) { background: var(--ckqa-surface-muted); }
.ck-pager-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ck-pager-btn--active { background: var(--ckqa-accent-soft); border-color: var(--ckqa-accent); color: var(--ckqa-accent-strong); }
.ck-pager-btn--block { width: 100%; height: 36px; }
.ck-pager-size {
  height: 32px; padding: 0 8px; margin-left: 12px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.ck-pager-load-more { width: 100%; }
</style>
