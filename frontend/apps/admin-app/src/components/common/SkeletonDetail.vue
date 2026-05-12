<script setup>
/**
 * SkeletonDetail - 详情页骨架屏组件
 * 模拟详情页布局：标题区 + 多个内容段落区 + 可选侧边栏
 * Requirements: 5.1, 5.3, 5.5
 */
defineProps({
  sections: { type: Number, default: 3 },
  showHeader: { type: Boolean, default: true },
  showSidebar: { type: Boolean, default: false },
})
</script>

<template>
  <div class="skeleton-detail" aria-hidden="true">
    <!-- 标题区 -->
    <div v-if="showHeader" class="skeleton-detail__header">
      <span class="skeleton-detail__title shimmer" />
      <span class="skeleton-detail__subtitle shimmer" />
    </div>

    <div class="skeleton-detail__body">
      <!-- 主内容区 -->
      <div class="skeleton-detail__main">
        <div
          v-for="index in sections"
          :key="index"
          class="skeleton-detail__section"
        >
          <span class="skeleton-detail__section-title shimmer" />
          <span class="skeleton-detail__line shimmer" />
          <span class="skeleton-detail__line skeleton-detail__line--medium shimmer" />
          <span class="skeleton-detail__line skeleton-detail__line--short shimmer" />
        </div>
      </div>

      <!-- 可选侧边栏 -->
      <aside v-if="showSidebar" class="skeleton-detail__sidebar">
        <span class="skeleton-detail__sidebar-block shimmer" />
        <span class="skeleton-detail__sidebar-block skeleton-detail__sidebar-block--small shimmer" />
        <span class="skeleton-detail__sidebar-block skeleton-detail__sidebar-block--small shimmer" />
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* shimmer 动画 - 1.5s 周期 */
@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.shimmer {
  background: linear-gradient(
    90deg,
    var(--ckqa-surface-muted) 25%,
    var(--ckqa-surface-strong) 50%,
    var(--ckqa-surface-muted) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite var(--ckqa-ease-standard);
  border-radius: var(--ckqa-radius-sm);
}

@media (prefers-reduced-motion: reduce) {
  .shimmer {
    animation: none;
  }
}

.skeleton-detail {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-6);
  padding: var(--ckqa-space-6);
}

/* 标题区 */
.skeleton-detail__header {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}

.skeleton-detail__title {
  display: block;
  height: 28px;
  width: 45%;
}

.skeleton-detail__subtitle {
  display: block;
  height: 16px;
  width: 30%;
}

/* 主体区域 */
.skeleton-detail__body {
  display: flex;
  gap: var(--ckqa-space-6);
}

.skeleton-detail__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-6);
}

/* 内容段落区 */
.skeleton-detail__section {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}

.skeleton-detail__section-title {
  display: block;
  height: 20px;
  width: 35%;
}

.skeleton-detail__line {
  display: block;
  height: 14px;
  width: 100%;
}

.skeleton-detail__line--medium {
  width: 80%;
}

.skeleton-detail__line--short {
  width: 55%;
}

/* 侧边栏 */
.skeleton-detail__sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}

.skeleton-detail__sidebar-block {
  display: block;
  height: 120px;
  border-radius: var(--ckqa-radius-md);
}

.skeleton-detail__sidebar-block--small {
  height: 72px;
}
</style>
