<!-- 问答模块副导航 · 紫色系 · 顶部"新建对话"按钮 + 会话列表 -->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Clock } from '@element-plus/icons-vue'

const router = useRouter()

// mock 会话列表
const sessions = ref([
  { id: 1, title: 'OS · 进程调度', messageCount: 5, lastTime: '10 分钟前', active: true },
  { id: 2, title: '死锁检测', messageCount: 3, lastTime: '昨天', active: false },
  { id: 3, title: '虚拟内存', messageCount: 8, lastTime: '3 天前', active: false },
])

function createNew() {
  router.push('/qa/ask')
}

function viewHistory() {
  router.push('/qa/history')
}

function selectSession(session) {
  sessions.value.forEach((s) => (s.active = s.id === session.id))
  router.push('/qa/ask')
}
</script>

<template>
  <nav class="side-nav qa-side-nav">
    <button class="btn-new" @click="createNew">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新建对话</span>
    </button>

    <div class="session-label">历史会话</div>
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.active }"
        @click="selectSession(session)"
      >
        <div class="session-title">{{ session.title }}</div>
        <div class="session-meta">{{ session.messageCount }} 条 · {{ session.lastTime }}</div>
      </div>
    </div>

    <button class="btn-history" @click="viewHistory">
      <el-icon :size="14"><Clock /></el-icon>
      查看全部历史
    </button>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: linear-gradient(135deg, #9333ea, #a855f7);
  color: #fff;
  border: 0;
  border-radius: $radius-lg;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(147, 51, 234, 0.35);
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(147, 51, 234, 0.5);
  }
}

.session-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 8px 6px 4px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  padding: 8px 10px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.05); }

  &.active {
    background: rgba(147, 51, 234, 0.1);
    border: 1px solid rgba(147, 51, 234, 0.2);
    box-shadow: 0 0 12px rgba(147, 51, 234, 0.1);

    .session-title { color: #9333ea; font-weight: 600; }
  }

  .session-title {
    font-size: 12px;
    color: #334155;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .session-meta {
    font-size: 10px;
    color: #94a3b8;
    margin-top: 2px;
  }
}

.btn-history {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: transparent;
  color: #64748b;
  border: 1px solid transparent;
  border-radius: $radius-md;
  font-size: 12px;
  cursor: pointer;

  &:hover { background: rgba(147, 51, 234, 0.05); color: #9333ea; }
}
</style>
