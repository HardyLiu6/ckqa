<script setup>
/**
 * SkeletonList - 列表骨架屏组件
 * 用于课程列表、知识库列表、问答会话列表等列表页加载态
 * Requirements: 5.1, 5.3, 5.5
 */
defineProps({
  /** 骨架行数 */
  rows: { type: Number, default: 5 },
  /** 是否显示头像占位 */
  showAvatar: { type: Boolean, default: false },
  /** 是否显示操作区占位 */
  showActions: { type: Boolean, default: true },
})
</script>

<template>
  <div class="skeleton-list" aria-hidden="true">
    <div v-for="index in rows" :key="index" class="skeleton-list__row">
      <!-- 头像占位 -->
      <div v-if="showAvatar" class="skeleton-list__avatar skeleton-shimmer" />

      <!-- 文本行占位 -->
      <div class="skeleton-list__content">
        <div class="skeleton-list__title skeleton-shimmer" />
        <div class="skeleton-list__subtitle skeleton-shimmer" />
      </div>

      <!-- 操作区占位 -->
      <div v-if="showActions" class="skeleton-list__actions skeleton-shimmer" />
    </div>
  </div>
</template>

<style scoped>
/* shimmer 动画：1.5s 周期，从左到右渐变闪烁 */
@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.skeleton-shimmer {
  background: linear-gradient(
    90deg,
    var(--ckqa-surface-muted) 25%,
    var(--ckqa-border) 50%,
    var(--ckqa-surface-muted) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite linear;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}

.skeleton-list__row {
  display: flex;
  align-items: center;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
}

.skeleton-list__avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--ckqa-radius-full);
}

.skeleton-list__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}

.skeleton-list__title {
  height: 14px;
  width: 60%;
  border-radius: var(--ckqa-radius-sm);
}

.skeleton-list__subtitle {
  height: 12px;
  width: 40%;
  border-radius: var(--ckqa-radius-sm);
}

.skeleton-list__actions {
  flex-shrink: 0;
  width: 64px;
  height: 28px;
  border-radius: var(--ckqa-radius-sm);
}

/* prefers-reduced-motion 降级 */
@media (prefers-reduced-motion: reduce) {
  .skeleton-shimmer {
    animation: none;
  }
}
</style>
