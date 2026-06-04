<!-- 问答模块副导航 · 展开态：胶囊风格 / 折叠态：GPT 风格图标条 -->
<script setup>
import { computed, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listQaSessions } from '@/api/qa'
import { localDateString, normalizeQaSessionList, toQaSideNavSession } from '@/views/qa/qa-session-model'
import { buildQaRouteQuery, withoutQaSessionQuery } from '@/views/qa/qa-route-query-model'
import { onQaSessionsChanged } from '@/views/qa/qa-session-events'
import { buildQaSideNavQueryParams, filterQaSideNavSessions, normalizeQaSideNavSearchKeyword } from '@/components/module-nav/qa-side-nav-model'

const router = useRouter()
const route = useRoute()

const sidebarCollapsed = inject('sidebarCollapsed', ref(false))
const toggleSidebar = inject('toggleSidebar', () => {})

const rawSessions = ref([])
const loading = ref(false)
const errorMessage = ref('')
const searchKeyword = ref('')
const recentPopoverOpen = ref(false)
const recentBtnRef = ref(null)
const recentPopoverPos = ref({ top: 0, left: 0 })
let recentLoadSeq = 0
let stopSessionChangeListener = null
let searchTimer = null

function toggleRecentPopover() {
  if (recentPopoverOpen.value) {
    recentPopoverOpen.value = false
    return
  }
  const rect = recentBtnRef.value?.getBoundingClientRect?.()
  if (rect) {
    recentPopoverPos.value = {
      top: rect.top,
      left: rect.right + 8,
    }
  }
  recentPopoverOpen.value = true
}

function closeRecentPopover() {
  recentPopoverOpen.value = false
}

const activeSessionId = computed(() => String(route.query.sessionId ?? ''))

const sessions = computed(() =>
  rawSessions.value.map((session) => toQaSideNavSession(session, activeSessionId.value)),
)

const normalizedSearchKeyword = computed(() => normalizeQaSideNavSearchKeyword(searchKeyword.value))

// 最近 10 条（折叠态 popover 用）
const recentTen = computed(() => sessions.value.slice(0, 10))

// 按时间分组（展开态用）
const groupedSessions = computed(() => {
  const now = new Date()
  const todayStr = localDateString(now)
  const yesterday = localDateString(new Date(now.getTime() - 86400000))

  const groups = { today: [], yesterday: [], earlier: [] }
  for (const session of sessions.value) {
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

const emptyStateText = computed(() => normalizedSearchKeyword.value ? '没有找到相关历史会话' : '暂无历史会话')

function createNew() {
  router.push({ path: '/qa/ask', query: withoutQaSessionQuery(route.query) })
}

function viewHistory() {
  router.push('/qa/history')
}

function selectSession(session) {
  const queryPatch = { sessionId: session.id }
  if (session.courseId) {
    queryPatch.courseId = session.courseId
  }
  router.push({ path: '/qa/ask', query: buildQaRouteQuery(route.query, queryPatch) })
  recentPopoverOpen.value = false
}

async function loadRecentSessions(keyword = searchKeyword.value) {
  const seq = ++recentLoadSeq
  loading.value = true
  errorMessage.value = ''
  const normalizedKeyword = normalizeQaSideNavSearchKeyword(keyword)
  try {
    const payload = await listQaSessions(buildQaSideNavQueryParams({ keyword: normalizedKeyword, page: 1 }))
    const normalizedSessions = normalizeQaSessionList(payload)
    const nextSessions = filterQaSideNavSessions(normalizedSessions, { keyword: normalizedKeyword })
    if (seq === recentLoadSeq) {
      rawSessions.value = nextSessions
    }
  } catch (error) {
    if (seq === recentLoadSeq) {
      rawSessions.value = []
      errorMessage.value = error?.message || '历史会话加载失败'
    }
  } finally {
    if (seq === recentLoadSeq) {
      loading.value = false
    }
  }
}

function scheduleSearchReload() {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
  searchTimer = window.setTimeout(() => {
    searchTimer = null
    loadRecentSessions(searchKeyword.value)
  }, 240)
}

onMounted(() => {
  loadRecentSessions()
  stopSessionChangeListener = onQaSessionsChanged(() => {
    if (route.path.startsWith('/qa')) {
      loadRecentSessions()
    }
  })
})

onUnmounted(() => {
  stopSessionChangeListener?.()
  stopSessionChangeListener = null
  if (searchTimer) {
    window.clearTimeout(searchTimer)
    searchTimer = null
  }
})

watch(searchKeyword, () => {
  scheduleSearchReload()
})

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
  <!-- ===== 折叠态：GPT 风格图标条 ===== -->
  <nav v-if="sidebarCollapsed" class="qa-sidebar-collapsed">
    <button class="icon-btn" title="打开边栏" @click="toggleSidebar">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="4" />
        <line x1="9" y1="3" x2="9" y2="21" />
      </svg>
    </button>

    <button class="icon-btn" title="新聊天" @click="createNew">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20h9" /><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
      </svg>
    </button>

    <button class="icon-btn" title="搜索聊天" @click="toggleSidebar">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
      </svg>
    </button>

    <div class="icon-btn-wrap">
      <button ref="recentBtnRef" class="icon-btn" title="最近聊天" @click="toggleRecentPopover">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      </button>

      <!-- 最近聊天 popover · Teleport 到 body 避开父容器约束 -->
      <Teleport to="body">
        <Transition name="pop">
          <div
            v-if="recentPopoverOpen"
            class="recent-popover"
            :style="{ top: recentPopoverPos.top + 'px', left: recentPopoverPos.left + 'px' }"
          >
            <div class="recent-pop-head">最近聊天</div>
            <div class="recent-pop-list">
              <div v-if="loading" class="recent-pop-skeleton-list" aria-label="正在加载最近聊天">
                <div
                  v-for="index in 6"
                  :key="`recent-pop-skeleton-${index}`"
                  class="recent-pop-skeleton-item"
                  :style="{ '--delay': `${index * 48}ms` }"
                >
                  <span class="recent-pop-skeleton-icon"></span>
                  <span class="recent-pop-skeleton-title"></span>
                </div>
              </div>
              <div v-else-if="errorMessage" class="recent-pop-state error">{{ errorMessage }}</div>
              <div v-else-if="!recentTen.length" class="recent-pop-state">暂无最近聊天</div>
              <template v-else>
                <div
                  v-for="session in recentTen"
                  :key="session.id"
                  class="recent-pop-item"
                  :class="{ active: session.active }"
                  @click="selectSession(session)"
                >
                  <svg class="recent-pop-ico" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                  </svg>
                  <span class="recent-pop-title">{{ session.title }}</span>
                </div>
              </template>
            </div>
          </div>
        </Transition>
      </Teleport>
    </div>

    <span class="collapsed-spacer"></span>

    <!-- 底部：查看全部历史 -->
    <button class="icon-btn" title="查看全部历史" @click="viewHistory">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
      </svg>
    </button>
  </nav>

  <!-- ===== 展开态：完整侧边栏 ===== -->
  <nav v-else class="qa-sidebar">
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

    <div class="search-wrap">
      <span class="search-icon">⌕</span>
      <input
        v-model="searchKeyword"
        class="search-input"
        type="text"
        placeholder="搜索历史会话…"
      />
    </div>

    <div class="session-scroll">
      <div v-if="loading" class="session-skeleton-list" aria-label="正在加载历史会话">
        <div class="session-skeleton-group"></div>
        <div
          v-for="index in 8"
          :key="`qa-nav-skeleton-${index}`"
          class="session-skeleton-item"
          :style="{ '--delay': `${index * 52}ms` }"
        >
          <div class="session-skeleton-body">
            <span class="session-skeleton-title"></span>
            <span class="session-skeleton-sub"></span>
          </div>
          <span class="session-skeleton-time"></span>
        </div>
      </div>
      <div v-else-if="errorMessage" class="session-state error">{{ errorMessage }}</div>
      <div v-else-if="!hasAnySession" class="session-state">{{ emptyStateText }}</div>
      <template v-else>
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

    <div class="sidebar-foot">
      <button class="btn-history" @click="viewHistory">查看全部历史</button>
    </div>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

/* ===== 折叠态：GPT 风格图标条 ===== */
.qa-sidebar-collapsed {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
  gap: 6px;
  background: linear-gradient(180deg, rgba(250, 245, 255, 0.3) 0%, rgba(255, 255, 255, 0.5) 100%);
  border-right: 1px solid rgba(226, 232, 240, 0.6);
}

.icon-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #475569;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover {
    background: rgba(148, 163, 184, 0.12);
    color: #7e22ce;
  }
}

.icon-btn-wrap {
  position: relative;
}

.collapsed-spacer {
  flex: 1;
}

/* ===== 展开态 ===== */
.qa-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  gap: 0;
  background: linear-gradient(180deg, rgba(250, 245, 255, 0.4) 0%, rgba(255, 255, 255, 0.65) 100%);
  backdrop-filter: blur(20px);
}

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

  &::placeholder { color: #94a3b8; }
  &:focus { border-color: rgba(147, 51, 234, 0.4); box-shadow: 0 0 0 3px rgba(147, 51, 234, 0.08); }
}

.group-label {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 10px 8px 5px;
}

.session-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin: 0 -2px;
  padding: 0 2px;

  &::-webkit-scrollbar { width: 5px; }
  &::-webkit-scrollbar-thumb { background: rgba(148, 163, 184, 0.2); border-radius: 3px; }
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background $duration-fast $ease-out;
  position: relative;

  &:hover { background: rgba(148, 163, 184, 0.08); }

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

    .session-title { color: #7e22ce; font-weight: 700; }
    .session-time { color: #a78bfa; }
  }
}

.session-body { flex: 1; min-width: 0; }

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

.session-state {
  padding: 12px 10px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;

  &.error { color: #b91c1c; }
}

.session-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px 2px 12px;
}

.session-skeleton-group,
.session-skeleton-title,
.session-skeleton-sub,
.session-skeleton-time {
  display: block;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(226, 232, 240, 0.62), rgba(255, 255, 255, 0.92), rgba(226, 232, 240, 0.62));
  background-size: 220% 100%;
  animation: qaNavSkeletonShimmer 1.35s ease-in-out infinite;
  animation-delay: var(--delay, 0ms);
}

.session-skeleton-group {
  width: 54px;
  height: 10px;
  margin: 9px 8px 4px;
  opacity: 0.72;
}

.session-skeleton-item {
  min-height: 56px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.44);
  border: 1px solid rgba(226, 232, 240, 0.34);
}

.session-skeleton-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-skeleton-title {
  width: 82%;
  height: 14px;
}

.session-skeleton-sub {
  width: 54%;
  height: 10px;
  opacity: 0.76;
}

.session-skeleton-time {
  width: 42px;
  height: 10px;
  margin-top: 3px;
  opacity: 0.76;
}

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

/* Transition */
.pop-enter-active, .pop-leave-active { transition: opacity $duration-fast $ease-out, transform $duration-fast $ease-out; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateX(-4px) scale(0.96); }

@media (prefers-reduced-motion: reduce) {
  .session-skeleton-group,
  .session-skeleton-title,
  .session-skeleton-sub,
  .session-skeleton-time {
    animation: none;
    background: rgba(226, 232, 240, 0.72);
  }
}
</style>

<style lang="scss">
/* 非 scoped：Teleport 到 body 的 popover 样式 */
@keyframes qaNavSkeletonShimmer {
  0% { background-position: 120% 0; }
  100% { background-position: -120% 0; }
}

.recent-popover {
  position: fixed;
  width: 280px;
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 14px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.16);
  padding: 6px;
  z-index: 9999;
}

.recent-pop-head {
  padding: 10px 12px 6px;
  font-size: 13px;
  font-weight: 800;
  color: #0f172a;
}

.recent-pop-list {
  max-height: 480px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.recent-pop-skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px;
}

.recent-pop-skeleton-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.78);
}

.recent-pop-skeleton-icon,
.recent-pop-skeleton-title {
  display: block;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(226, 232, 240, 0.72), rgba(255, 255, 255, 0.96), rgba(226, 232, 240, 0.72));
  background-size: 220% 100%;
  animation: qaNavSkeletonShimmer 1.35s ease-in-out infinite;
  animation-delay: var(--delay, 0ms);
}

.recent-pop-skeleton-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.recent-pop-skeleton-title {
  width: 170px;
  height: 13px;
}

.recent-pop-state {
  padding: 12px;
  font-size: 12px;
  color: #94a3b8;
  text-align: center;

  &.error { color: #b91c1c; }
}

.recent-pop-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover { background: rgba(148, 163, 184, 0.08); }
  &.active { background: rgba(147, 51, 234, 0.08); }
  &.active .recent-pop-title { color: #7e22ce; font-weight: 700; }
}

.recent-pop-ico {
  flex-shrink: 0;
  color: #94a3b8;
}

.recent-pop-title {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

@media (prefers-reduced-motion: reduce) {
  .recent-pop-skeleton-icon,
  .recent-pop-skeleton-title {
    animation: none;
    background: rgba(226, 232, 240, 0.78);
  }
}
</style>
