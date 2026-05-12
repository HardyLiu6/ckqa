<script setup>
/**
 * SkeletonTable — 表格骨架屏组件
 * 用于 DataTableShell 内表格加载态
 * Requirements: 5.1, 5.3, 5.5
 */
defineProps({
  rows: { type: Number, default: 8 },
  columns: { type: Number, default: 5 },
  showHeader: { type: Boolean, default: true },
})
</script>

<template>
  <div class="skeleton-table" aria-hidden="true">
    <!-- 表头行 -->
    <div v-if="showHeader" class="skeleton-table__header">
      <span
        v-for="col in columns"
        :key="`header-${col}`"
        class="skeleton-table__cell skeleton-table__cell--header"
      />
    </div>
    <!-- 数据行 -->
    <div
      v-for="row in rows"
      :key="`row-${row}`"
      class="skeleton-table__row"
    >
      <span
        v-for="col in columns"
        :key="`cell-${row}-${col}`"
        class="skeleton-table__cell"
      />
    </div>
  </div>
</template>

<style scoped>
.skeleton-table {
  display: grid;
  gap: var(--ckqa-space-1);
  width: 100%;
}

.skeleton-table__header,
.skeleton-table__row {
  display: grid;
  grid-template-columns: repeat(v-bind(columns), minmax(0, 1fr));
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  border-radius: var(--ckqa-radius-sm);
}

.skeleton-table__header {
  background: var(--ckqa-surface-muted);
}

.skeleton-table__row {
  border-bottom: 1px solid var(--ckqa-border-subtle);
}

.skeleton-table__row:last-child {
  border-bottom: none;
}

.skeleton-table__cell {
  height: 14px;
  border-radius: var(--ckqa-radius-full);
  background:
    linear-gradient(
      90deg,
      var(--ckqa-surface-muted),
      color-mix(in srgb, var(--ckqa-accent) 16%, var(--ckqa-surface-muted)),
      var(--ckqa-surface-muted)
    );
  background-size: 200% 100%;
  animation: skeleton-table-shimmer 1.5s ease-in-out infinite;
}

.skeleton-table__cell--header {
  height: 12px;
  width: 70%;
}

/* 数据行中的列宽变化，模拟真实表格内容 */
.skeleton-table__row .skeleton-table__cell:nth-child(1) {
  width: 60%;
}

.skeleton-table__row .skeleton-table__cell:nth-child(2) {
  width: 85%;
}

.skeleton-table__row .skeleton-table__cell:nth-child(3) {
  width: 72%;
}

.skeleton-table__row .skeleton-table__cell:nth-child(4) {
  width: 55%;
}

.skeleton-table__row .skeleton-table__cell:nth-child(5) {
  width: 40%;
}

.skeleton-table__row .skeleton-table__cell:nth-child(n+6) {
  width: 65%;
}

@keyframes skeleton-table-shimmer {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-table__cell {
    animation: none;
  }
}
</style>
