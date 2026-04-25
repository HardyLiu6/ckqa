<!-- 消息通知 · 通知列表视觉壳 -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import userMock from '@/mock/user.json'

const notifications = userMock.notifications
</script>

<template>
  <div class="notif-page">
    <h1 class="page-title">消息通知</h1>

    <GlassCard tier="light" padding="none">
      <div class="notif-list">
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notif-item"
          :class="{ unread: !n.read }"
        >
          <div class="type-dot" :class="`type-${n.type}`"></div>
          <div class="body">
            <div class="title">{{ n.title }}</div>
            <div class="time">{{ n.time }}</div>
          </div>
          <div v-if="!n.read" class="unread-dot"></div>
        </div>
      </div>
    </GlassCard>
    <p class="coming-hint">通知中心的完整视觉在后续迭代补齐</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.notif-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 14px;
}

.notif-list {
  display: flex;
  flex-direction: column;
}

.notif-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.2s ease;

  &:last-of-type { border-bottom: 0; }
  &:hover { background: rgba(100, 116, 139, 0.04); }
  &.unread .title { color: #0f172a; font-weight: 600; }

  .type-dot {
    width: 8px; height: 8px;
    border-radius: 50%;

    &.type-system { background: #f59e0b; box-shadow: 0 0 6px #f59e0b; }
    &.type-course { background: #2563eb; box-shadow: 0 0 6px #60a5fa; }
  }
  .body { flex: 1; }
  .title { font-size: 13px; color: #475569; }
  .time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
  .unread-dot {
    width: 6px; height: 6px;
    background: #ef4444;
    border-radius: 50%;
  }
}

.coming-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  margin-top: 16px;
}
</style>
