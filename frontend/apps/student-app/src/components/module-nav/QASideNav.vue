<!-- 问答模块副导航 · V2 胶囊风格 · 新建对话 + 搜索 + 时间分组 + 活跃指示条 -->
<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listQaSessions } from '@/api/qa'
import { normalizeQaSessionList, toQaSideNavSession } from '@/views/qa/qa-session-model'

const router = useRouter()
const route = useRoute()

const sidebarCollapsed = inject('sidebarCollapsed', ref(false))
const toggleSidebar = inject('toggleSidebar', () => {})

const rawSessions = ref([])
const loading = ref(false)
const errorMessage = ref('')
const searchKeyword = ref('')

const activeSessionId = computed(() => String(route.query.sessionId ?? ''))

const sessions = computed(() =>
  rawSessions.value.map((session) => toQaSideNavSession(session, activeSessionId.value)),
)

// 按时间分组
const groupedSessions = computed(() => {
  const filtered = searchKeyword.value
    ? sessions.value.filter((s) => s.title.toLowerCase().includes(searchKeyword.value.toLowerCase()))
    : sessions.value

  const today = new Date()
  const todayStr = today.toISOString().slice(0, 10)
  const yesterday = new Date(today.getTime() - 86400000).toISOString().slice(0, 10)

  const groups = { today: [], yesterday: [], earlier: [] }
  for (const session of filtered) {
    const dateStr = session.dateStr || todayStr
    if (dateStr === todayStr) groups.today.push(session)
    else if (dateStr === yesterday) groups.yesterday.push(session)
    else groups.earlier.push(session)
  }
  return groups
})

const hasAnySession = computed(() =>
  groupedSessions.value.today.length
  || groupedSessions.value.yesterday.length
  || groupedSessions.value.earlier.length,
)

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
    const payload = await listQaSessions({ status: 'active', page: 1, size: 20 })
    rawSessions.value = normalizeQaSessionList(payload)
    // 只保留近一个月的会话，更早的通过「查看全部历史」获取
    const oneMonthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
    rawSessions.value = rawSessions.value.filter((s) => {
      const ref = s.lastMessageAt || s.createdAt || ''
      if (!ref) return true
      return new Date(ref) >= oneMonthAgo
    })
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
  <nav class="qa-sidebar">
    <!-- 顶部：新建对话 + 折叠按钮 -->
    <div class="sidebar-head">
      <button class="btn-new" @click="createNew">
        <span class="btn-new-icon">＋</span>
        <span>新建对话</span>
      </button>
      <button class="btn-collapse" type="button" title="收起侧边栏" @click="toggleSidebar">
        <svg class="collapse-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="4" />
          <line x1="9" y1="3" x2="9" y2="21" />
        </svg>
      </button>
    </div>

    <!-- 搜索 · 胶囊 -->
    <div class="search-wrap">
      <span class="search-icon">⌕</span>
      <input
        v-model="searchKeyword"
        class="search-input"
        type="text"
        placeholder="搜索历史会话…"
      />
    </div>

    <!-- 会话列表 -->
    <div class="session-scroll">
      <div v-if="loading" class="session-state">正在加载…</div>
      <div v-else-if="errorMessage" class="session-state error">{{ errorMessage }}</div>
      <div v-else-if="!hasAnySession" class="session-state">暂无历史会话</div>
      <template v-else>
        <!-- 今天 -->
        <template v-if="groupedSessions.today.length">
          <div class="group-label">今天</div>
          <div
            v-for="session in groupedSessions.today"
            :key="session.id"
            class="session-item"
            :class="{ active: session.active }"
            @click="selectSession(session)"
          >
            <div class="session-body">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-sub">{{ session.courseName || '' }}{{ session.modeName ? ' · ' + session.modeName : '' }}</div>
            </div>
            <span class="session-time">{{ session.relativeTime || '' }}</span>
          </div>
        </template>

        <!-- 昨天 -->
        <template v-if="groupedSessions.yesterday.length">
          <div class="group-label">昨天</div>
          <div
            v-for="session in groupedSessions.yesterday"
            :key="session.id"
            class="session-item"
            :class="{ active: session.active }"
            @click="selectSession(session)"
          >
            <div class="session-body">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-sub">{{ session.courseName || '' }}{{ session.modeName ? ' · ' + session.modeName : '' }}</div>
            </div>
            <span class="session-time">{{ session.relativeTime || '' }}</span>
          </div>
        </template>

        <!-- 更早 -->
        <template v-if="groupedSessions.earlier.length">
          <div class="group-label">更早</div>
          <div
            v-for="session in groupedSessions.earlier"
            :key="session.id"
            class="session-item"
            :class="{ active: session.active }"
            @click="selectSession(session)"
          >
            <div class="session-body">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-sub">{{ session.courseName || '' }}{{ session.modeName ? ' · ' + session.modeName : '' }}</div>
            </div>
            <span class="session-time">{{ session.relativeTime || '' }}</span>
          </div>
        </template>
      </template>
    </div>

    <!-- 底部 · 查看全部历史 · 居中胶囊 -->
    <div class="sidebar-foot">
      <button class="btn-history" @click="viewHistory">查看全部历史</button>
    </div>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.qa-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  gap: 0;
  background: linear-gradient(180deg, rgba(250, 245, 255, 0.4) 0%, rgba(255, 255, 255, 0.65) 100%);
  backdrop-filter: blur(20px);
}

/* 新建对话 · 胶囊 */
.sidebar-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.btn-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  flex: 1;
  height: 42px;
  border-radius: 999px;
  border: 0;
  background: linear-gradient(135deg, #9333ea, #6366f1);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(147, 51, 234, 0.28);
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 12px 28px rgba(147, 51, 234, 0.38);
  }
}

.btn-collapse {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  font-size: 14px;
  flex-shrink: 0;
  transition: background $duration-fast $ease-out, border-color $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover {
    background: #fff;
    border-color: rgba(147, 51, 234, 0.32);
    color: #7e22ce;
  }
}

.collapse-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-new-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 1;
}

/* 搜索 · 胶囊 */
.search-wrap {
  position: relative;
  margin-bottom: 10px;
}

.search-icon {
  position: absolute;
  left: 13px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  font-size: 13px;
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 38px;
  border-radius: 999px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.85);
  padding: 0 14px 0 36px;
  font-size: 12.5px;
  color: #0f172a;
  outline: none;
  font-family: inherit;
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &::placeholder {
    color: #94a3b8;
  }

  &:focus {
    border-color: rgba(147, 51, 234, 0.4);
    box-shadow: 0 0 0 3px rgba(147, 51, 234, 0.08);
  }
}

/* 分组标签 */
.group-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 10px 8px 5px;
}

/* 会话列表滚动区 */
.session-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0 -2px;
  padding: 0 2px;

  &::-webkit-scrollbar {
    width: 5px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.2);
    border-radius: 3px;
  }
}

/* 会话条目 */
.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;
  position: relative;

  &:hover {
    background: rgba(148, 163, 184, 0.08);
  }

  &.active {
    background: rgba(147, 51, 234, 0.08);

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 18px;
      border-radius: 0 3px 3px 0;
      background: linear-gradient(180deg, #9333ea, #6366f1);
    }

    .session-title {
      color: #7e22ce;
      font-weight: 700;
    }

    .session-time {
      color: #a78bfa;
    }
  }
}

.session-body {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 10.5px;
  color: #cbd5e1;
  flex-shrink: 0;
  align-self: flex-start;
  margin-top: 2px;
}

/* 状态提示 */
.session-state {
  padding: 12px 10px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;

  &.error {
    color: #b91c1c;
  }
}

/* 底部 · 查看全部历史 · 居中 */
.sidebar-foot {
  border-top: 1px solid rgba(226, 232, 240, 0.5);
  padding: 12px 2px 4px;
  margin-top: 6px;
  display: flex;
  justify-content: center;
}

.btn-history {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid rgba(226, 232, 240, 0.85);
  background: rgba(255, 255, 255, 0.7);
  font-family: inherit;
  transition: background $duration-fast $ease-out, border-color $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover {
    background: #fff;
    border-color: rgba(147, 51, 234, 0.32);
    color: #7e22ce;
  }
}
</style>
