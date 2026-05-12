<script setup>
/**
 * SkeletonCardGrid — 卡片网格骨架屏
 * 用途：工作台 MetricTile 网格、卡片式列表加载态
 * Requirements: 5.1, 5.3, 5.5
 */
defineProps({
  cards: { type: Number, default: 6 },
  columns: { type: Number, default: 3 },
})
</script>

<template>
  <div
    class="skeleton-card-grid"
    :style="{ '--skeleton-columns': columns }"
    aria-hidden="true"
  >
    <div v-for="i in cards" :key="i" class="skeleton-card">
      <span class="skeleton-card__title" />
      <span class="skeleton-card__value" />
    </div>
  </div>
</template>

<style scoped>
.skeleton-card-grid {
  display: grid;
  grid-template-columns: repeat(var(--skeleton-columns, 3), minmax(0, 1fr));
  gap: var(--ckqa-space-4);
}

.skeleton-card {
  display: grid;
  gap: var(--ckqa-space-3);
  min-height: 132px;
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}

.skeleton-card__title,
.skeleton-card__value {
  border-radius: var(--ckqa-radius-full);
  background:
    linear-gradient(
      90deg,
      var(--ckqa-surface-muted),
      color-mix(in srgb, var(--ckqa-accent) 16%, var(--ckqa-surface-muted)),
      var(--ckqa-surface-muted)
    );
  background-size: 200% 100%;
  animation: skeleton-card-shimmer 1.5s ease-in-out infinite;
}

.skeleton-card__title {
  width: 60%;
  height: 14px;
}

.skeleton-card__value {
  width: 40%;
  height: 28px;
}

@keyframes skeleton-card-shimmer {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-card__title,
  .skeleton-card__value {
    animation: none;
  }
}
</style>
