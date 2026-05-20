<!-- 问答模块副导航 · 紫色系 · 顶部"新建对话"按钮 + 会话列表 -->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, Clock } from '@element-plus/icons-vue'
import { listQaSessions } from '@/api/qa'
import { normalizeQaSessionList, toQaSideNavSession } from '@/views/qa/qa-session-model'

const router = useRouter()
const route = useRoute()

const rawSessions = ref([])
const loading = ref(false)
const errorMessage = ref('')

const activeSessionId = computed(() => String(route.query.sessionId ?? ''))
const sessions = computed(() => rawSessions.value.map((session) => (
  toQaSideNavSession(session, activeSessionId.value)
)))

function createNew() {
  router.push('/qa/ask')
}

function viewHistory() {
  router.push('/qa/history')
}

function selectSession(session) {
  router.push({ path: '/qa/ask', query: { sessionId: session.id } })
}

async function loadRecentSessions() {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = await listQaSessions({ status: 'active', page: 1, size: 5 })
    rawSessions.value = normalizeQaSessionList(payload)
  } catch (error) {
    rawSessions.value = []
    errorMessage.value = error?.message || '历史会话加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadRecentSessions)

watch(
  () => route.query.sessionId,
  () => {
    if (route.path.startsWith('/qa')) {
      loadRecentSessions()
    }
  },
)
</script>

<template>
  <nav class="side-nav qa-side-nav">
    <button class="btn-new" @click="createNew">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新建对话</span>
    </button>

    <div class="session-label">历史会话</div>
    <div class="session-list">
      <div v-if="loading" class="session-state">正在加载...</div>
      <div v-else-if="errorMessage" class="session-state error">{{ errorMessage }}</div>
      <div v-else-if="!sessions.length" class="session-state">暂无历史会话</div>
      <template v-else>
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.active }"
          @click="selectSession(session)"
        >
          <div class="session-title">{{ session.title }}</div>
          <div class="session-meta">{{ session.meta }}</div>
        </div>
      </template>
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

.session-state {
  padding: 8px 10px;
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.5;

  &.error {
    color: #b91c1c;
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
