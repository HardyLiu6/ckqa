<template>
  <div class="qa-history-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
          <div class="header-title">
            <h1>问答记录</h1>
            <span class="record-count">共 {{ stats.totalSessions }} 个对话</span>
          </div>
        </div>
        <div class="header-actions">
          <el-input v-model="searchKeyword" placeholder="搜索对话..." class="search-input" clearable>
            <template #prefix><el-icon>
                <Search />
              </el-icon></template>
          </el-input>
          <el-dropdown trigger="click">
            <el-button :icon="More" circle class="more-btn" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="exportAll"><el-icon>
                    <Download />
                  </el-icon>导出全部</el-dropdown-item>
                <el-dropdown-item @click="clearAll" divided><el-icon>
                    <Delete />
                  </el-icon>清空记录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 统计卡片 -->
    <section class="stats-section">
      <div class="stats-container">
        <div class="stat-card">
          <div class="stat-icon blue"><el-icon>
              <ChatDotRound />
            </el-icon></div>
          <div class="stat-info"><span v-if="loading" class="stat-value stat-value-skeleton"></span><span v-else
              class="stat-value">{{ stats.totalSessions }}</span><span class="stat-label">对话总数</span></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon green"><el-icon>
              <Comment />
            </el-icon></div>
          <div class="stat-info"><span v-if="loading" class="stat-value stat-value-skeleton"></span><span v-else
              class="stat-value">{{ stats.totalMessages }}</span><span class="stat-label">消息总数</span></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon purple"><el-icon>
              <Reading />
            </el-icon></div>
          <div class="stat-info"><span v-if="loading" class="stat-value stat-value-skeleton"></span><span v-else
              class="stat-value">{{ stats.courseCount }}</span><span class="stat-label">涉及课程</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon orange"><el-icon>
              <StarFilled />
            </el-icon></div>
          <div class="stat-info"><span v-if="loading" class="stat-value stat-value-skeleton"></span><span v-else
              class="stat-value">{{ stats.favoriteCount }}</span><span class="stat-label">已收藏</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 筛选栏 -->
    <section class="filter-section">
      <div class="filter-container">
        <div class="filter-left">
          <el-radio-group v-model="filterType" @change="handleFilterChange">
            <el-radio-button value="active">进行中</el-radio-button>
            <el-radio-button value="archived">已归档</el-radio-button>
            <el-radio-button value="favorite">已收藏</el-radio-button>
            <el-radio-button value="today">今天</el-radio-button>
            <el-radio-button value="week">本周</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-right">
          <el-select v-model="filterCourse" placeholder="选择课程" clearable class="course-filter filter-select"
            popper-class="qa-history-filter-dropdown">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="sortBy" class="sort-select filter-select" popper-class="qa-history-filter-dropdown">
            <el-option value="newest" label="最新优先" />
            <el-option value="oldest" label="最早优先" />
            <el-option value="messages" label="消息最多" />
          </el-select>
        </div>
      </div>
    </section>

    <!-- 对话列表 -->
    <main class="history-main">
      <div class="history-container" :aria-busy="loading ? 'true' : 'false'">
        <div v-if="loading" class="history-skeleton" aria-label="问答记录加载中">
          <div v-for="groupIndex in 2" :key="`skeleton-group-${groupIndex}`" class="skeleton-group">
            <div class="skeleton-group-header">
              <span class="skeleton-line skeleton-line-date"></span>
              <span class="skeleton-line skeleton-line-count"></span>
            </div>
            <div class="session-list">
              <div v-for="cardIndex in 3" :key="`skeleton-card-${groupIndex}-${cardIndex}`"
                class="session-card skeleton-card" :style="{ animationDelay: cardEnterDelay(cardIndex) }">
                <div class="card-main">
                  <div class="skeleton-avatar"></div>
                  <div class="card-content">
                    <div class="card-header">
                      <span class="skeleton-line skeleton-line-title"></span>
                      <span class="skeleton-line skeleton-line-tag"></span>
                    </div>
                    <span class="skeleton-line skeleton-line-preview"></span>
                    <span class="skeleton-line skeleton-line-meta"></span>
                  </div>
                  <div class="skeleton-actions">
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <template v-else>
        <div v-for="group in groupedSessions" :key="group.date" class="history-group">
          <div class="group-header">
            <span class="group-date">{{ group.dateLabel }}</span>
            <span class="group-count">{{ group.items.length }} 个对话</span>
          </div>

          <div class="session-list">
            <div v-for="(session, sessionIndex) in group.items" :key="session.id" class="session-card"
              :style="{ animationDelay: cardEnterDelay(sessionIndex) }" @click="goToDetail(session.id)">
              <div class="card-main">
                <div class="card-icon" :class="getCourseTheme(session.courseId)">
                  <el-icon>
                    <ChatDotRound />
                  </el-icon>
                </div>
                <div class="card-content">
                  <div class="card-header">
                    <h3 class="session-title">{{ session.title }}</h3>
                    <div class="card-tags">
                      <el-tag v-if="session.courseName" size="small" type="primary" effect="plain">{{ session.courseName
                        }}</el-tag>
                      <el-tag v-if="session.isFavorite" size="small" type="warning" effect="plain"><el-icon>
                          <StarFilled />
                        </el-icon></el-tag>
                    </div>
                  </div>
                  <p class="session-preview">{{ session.lastMessage }}</p>
                  <div class="card-meta">
                    <span class="meta-item"><el-icon>
                        <Comment />
                      </el-icon>{{ session.messageCount }} 条消息</span>
                    <span class="meta-item"><el-icon>
                        <Clock />
                      </el-icon>{{ session.lastTime }}</span>
                  </div>
                </div>
                <div class="card-actions">
                  <el-tooltip :content="session.isFavorite ? '取消收藏' : '收藏'"><el-button
                      :icon="session.isFavorite ? StarFilled : Star" circle class="favorite-btn"
                      :class="{ active: session.isFavorite }" @click.stop="toggleFavorite(session)" /></el-tooltip>
                  <el-tooltip content="继续对话"><el-button :icon="ChatLineRound" circle
                      @click.stop="continueChat(session)" /></el-tooltip>
                  <el-tooltip content="改名"><el-button :icon="EditPen" circle
                      @click.stop="renameSession(session)" /></el-tooltip>
                  <el-tooltip :content="session.status === 'archived' ? '恢复' : '归档'"><el-button
                      :icon="session.status === 'archived' ? RefreshLeft : Delete" circle class="delete-btn"
                      @click.stop="toggleArchive(session)" /></el-tooltip>
                </div>
              </div>

              <!-- 最近消息预览 -->
              <div class="card-preview">
                <div v-for="msg in session.recentMessages?.slice(0, 2)" :key="msg.id" class="preview-message"
                  :class="msg.role">
                  <span class="preview-role">{{ msg.role === 'user' ? '我' : 'AI' }}:</span>
                  <span class="preview-text">{{ truncate(msg.content, 60) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="filteredSessions.length === 0" class="empty-state">
          <div class="empty-icon"><el-icon>
              <DocumentDelete />
            </el-icon></div>
          <h3>暂无对话记录</h3>
          <p>{{ searchKeyword || filterCourse ? '没有找到符合条件的对话' : '开始你的第一次提问吧' }}</p>
          <el-button type="primary" @click="goToAsk"><el-icon>
              <ChatDotRound />
            </el-icon>开始提问</el-button>
        </div>
        </template>

        <!-- 无限滚动哨兵 + 加载状态 -->
        <div ref="loadMoreSentinel" class="load-more-sentinel"></div>
        <div v-if="loadingMore" class="load-more-skeleton" aria-label="正在加载更多对话">
          <div v-for="cardIndex in 2" :key="`load-more-skeleton-${cardIndex}`" class="session-card skeleton-card"
            :style="{ animationDelay: cardEnterDelay(cardIndex) }">
            <div class="card-main">
              <div class="skeleton-avatar"></div>
              <div class="card-content">
                <span class="skeleton-line skeleton-line-title"></span>
                <span class="skeleton-line skeleton-line-preview"></span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="sessionList.length > 0" class="list-footer">
          <span v-if="loadingMore">正在加载更多…</span>
          <span v-else-if="!hasMore">已加载全部 {{ total }} 个对话</span>
        </div>
      </div>
    </main>

    <el-backtop :right="28" :bottom="28" :visibility-height="360" class="history-backtop">
      <el-icon>
        <Top />
      </el-icon>
    </el-backtop>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, More, Download, Delete, ChatDotRound, Comment, Star, StarFilled, Reading, Clock, ChatLineRound, DocumentDelete, EditPen, RefreshLeft, Top } from '@element-plus/icons-vue'
import { listCourses } from '@/api/courses'
import { listQaSessions, getQaSessionStats, updateQaSession } from '@/api/qa'
import { normalizeCourseList, normalizeQaSession, normalizeQaSessionPage, normalizeQaSessionStats, localDateString } from './qa-session-model'
import { buildQaHistoryQueryParams, groupQaHistorySessions } from './qa-history-model'
import { notifyQaSessionsChanged } from './qa-session-events'

const router = useRouter()

const courses = ref([])
const sessionList = ref([])
const loading = ref(false)

// 分页 / 无限滚动状态
const PAGE_SIZE = 20
const page = ref(1)
const total = ref(0)
const loadingMore = ref(false)
const loadMoreSentinel = ref(null)
const hasMore = computed(() => sessionList.value.length < total.value)

// 筛选状态
const searchKeyword = ref('')
const filterType = ref('active')
const filterCourse = ref('')
const sortBy = ref('newest')

const courseList = computed(() => courses.value.map((course) => ({
  id: course.courseId,
  name: course.name,
})))
const courseNameById = computed(() => Object.fromEntries(
  courses.value.map((course) => [course.courseId, course.name]),
))

// 统计卡片：由后端按全部历史聚合得出，不受分页/无限滚动影响
const EMPTY_STATS = { totalSessions: 0, totalMessages: 0, courseCount: 0, favoriteCount: 0 }
const stats = ref({ ...EMPTY_STATS })

// 过滤后的会话
const filteredSessions = computed(() => {
  let result = [...sessionList.value]

  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(s => s.title.toLowerCase().includes(kw) || s.lastMessage.toLowerCase().includes(kw))
  }

  if (filterType.value === 'today') {
    const today = localDateString()
    result = result.filter(s => s.date === today)
  } else if (filterType.value === 'week') {
    const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 7)
    result = result.filter(s => new Date(s.date) >= weekAgo)
  }

  return result
})

// 按日期分组
const groupedSessions = computed(() => {
  return groupQaHistorySessions(filteredSessions.value, { sortBy: sortBy.value })
})

// 方法
const goBack = () => router.back()
const goToAsk = () => router.push('/qa/ask')
const goToDetail = (id) => router.push({ path: '/qa/ask', query: { sessionId: id } })
const continueChat = (session) => router.push({ path: '/qa/ask', query: { sessionId: session.id } })
const handleFilterChange = () => {
  loadHistory()
}

const getCourseTheme = (courseId) => {
  const themes = ['blue', 'green', 'orange', 'purple', 'cyan']
  const key = String(courseId ?? '').split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return themes[key % themes.length] || 'blue'
}

const truncate = (text, len) => text?.length > len ? text.substring(0, len) + '...' : text

const cardEnterDelay = (index = 0) => `${Math.min(Number(index) || 0, 8) * 26}ms`

function notifyHistorySessionChanged(type, session, patch = {}) {
  const current = session ?? {}
  notifyQaSessionsChanged({
    source: 'qa-history',
    type,
    sessionId: current.id ?? patch.id ?? null,
    title: patch.title ?? current.title ?? '',
    status: patch.status ?? current.status ?? '',
    isFavorite: patch.isFavorite ?? current.isFavorite ?? false,
  })
}

const toggleFavorite = async (session) => {
  const nextFavorite = !session.isFavorite
  try {
    await updateQaSession(session.id, { isFavorite: nextFavorite })
    ElMessage.success(nextFavorite ? '会话已收藏' : '已取消收藏')
    if (filterType.value === 'favorite' && !nextFavorite) {
      await loadHistory()
      notifyHistorySessionChanged('favorite', session, { isFavorite: nextFavorite })
      return
    }
    sessionList.value = sessionList.value.map((item) => (
      item.id === session.id ? { ...item, isFavorite: nextFavorite } : item
    ))
    notifyHistorySessionChanged('favorite', session, { isFavorite: nextFavorite })
  } catch (error) {
    ElMessage.error(error?.message || '收藏状态更新失败')
  }
}

const renameSession = async (session) => {
  const title = window.prompt('请输入新的会话标题', session.title)
  if (!title || title.trim() === session.title) {
    return
  }
  try {
    const updated = normalizeQaSession(await updateQaSession(session.id, { title: title.trim() }))
    sessionList.value = sessionList.value.map((item) => (
      item.id === session.id ? toSessionCard(updated) : item
    ))
    notifyHistorySessionChanged('rename', updated)
    ElMessage.success('会话标题已更新')
  } catch (error) {
    ElMessage.error(error?.message || '会话改名失败')
  }
}

const toggleArchive = async (session) => {
  const nextStatus = session.status === 'archived' ? 'active' : 'archived'
  try {
    await updateQaSession(session.id, { status: nextStatus })
    ElMessage.success(nextStatus === 'archived' ? '会话已归档' : '会话已恢复')
    await loadHistory()
    notifyHistorySessionChanged('archive', { ...session, status: nextStatus })
  } catch (error) {
    ElMessage.error(error?.message || '会话状态更新失败')
  }
}

const exportAll = () => {
  ElMessage.info('导出功能暂未接入后端')
}

const clearAll = async () => {
  ElMessage.info('清空记录功能暂未接入后端')
}

let scrollObserver = null

function setupInfiniteScroll() {
  if (typeof IntersectionObserver === 'undefined' || !loadMoreSentinel.value) {
    return
  }
  scrollObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      loadMore()
    }
  }, { rootMargin: '240px' })
  scrollObserver.observe(loadMoreSentinel.value)
}

onMounted(async () => {
  await loadHistory()
  setupInfiniteScroll()
})

watch([filterCourse, sortBy], () => {
  loadHistory()
})

onUnmounted(() => {
  if (scrollObserver) {
    scrollObserver.disconnect()
    scrollObserver = null
  }
})

async function loadHistory() {
  loading.value = true
  page.value = 1
  try {
    const queryParams = buildQaHistoryQueryParams({
      filterType: filterType.value,
      filterCourse: filterCourse.value,
      sortBy: sortBy.value,
      page: 1,
      size: PAGE_SIZE,
    })
    const [coursePayload, statsPayload, sessionPayload] = await Promise.all([
      listCourses({ page: 1, size: 100, status: 'active' }),
      getQaSessionStats(queryParams),
      listQaSessions(queryParams),
    ])
    courses.value = normalizeCourseList(coursePayload)
    stats.value = normalizeQaSessionStats(statsPayload)
    const pageData = normalizeQaSessionPage(sessionPayload)
    total.value = pageData.total
    sessionList.value = pageData.items.map((session) => toSessionCard(session))
  } catch (error) {
    ElMessage.error(error?.message || '问答记录加载失败')
    sessionList.value = []
    total.value = 0
    stats.value = { ...EMPTY_STATS }
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || loading.value || !hasMore.value) {
    return
  }
  loadingMore.value = true
  try {
    const nextPage = page.value + 1
    const queryParams = buildQaHistoryQueryParams({
      filterType: filterType.value,
      filterCourse: filterCourse.value,
      sortBy: sortBy.value,
      page: nextPage,
      size: PAGE_SIZE,
    })
    const pageData = normalizeQaSessionPage(
      await listQaSessions(queryParams),
    )
    total.value = pageData.total
    const seen = new Set(sessionList.value.map((item) => item.id))
    const appended = pageData.items
      .filter((item) => !seen.has(item.id))
      .map((session) => toSessionCard(session))
    sessionList.value = [...sessionList.value, ...appended]
    page.value = nextPage
  } catch (error) {
    ElMessage.error(error?.message || '加载更多对话失败')
  } finally {
    loadingMore.value = false
  }
}

function toSessionCard(session) {
  const referenceTime = session.lastMessageAt || session.createdAt || session.indexLockedAt || ''
  const date = String(referenceTime).slice(0, 10) || new Date().toISOString().slice(0, 10)
  return {
    ...session,
    courseName: courseNameById.value[session.courseId] || session.courseId || '未绑定课程',
    messageCount: session.messageCount,
    lastTime: formatSessionTime(referenceTime),
    lastMessage: session.status === 'archived'
      ? '该会话已归档，可恢复后继续提问'
      : (session.isLegacy ? '旧会话仅可查看历史消息' : '点击继续这个真实问答会话'),
    date,
    isFavorite: Boolean(session.isFavorite),
    recentMessages: [],
  }
}

function formatSessionTime(value) {
  if (!value) {
    return '暂无消息'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ').slice(0, 16)
  }
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style lang="scss" scoped>
$primary: #9333ea;
$primary-light: #a78bfa;
$primary-dark: #7e22ce;
$success: #10b981;
$warning: #f59e0b;
$danger: #ef4444;
$cyan: #06b6d4;
$purple: #8b5cf6;
$bg: #f8fafc;
$bg-card: #fff;
$text: #0f172a;
$text-secondary: #475569;
$text-muted: #94a3b8;
$border: rgba(226, 232, 240, 0.9);
$radius: 14px;

.qa-history-page {
  min-height: 100vh;
  background: $bg;

  /* Element Plus 组件主题覆盖 */
  :deep(.el-button--primary) {
    background: linear-gradient(135deg, $primary, #6366f1);
    border-color: transparent;
    border-radius: 999px;
    font-weight: 700;
    box-shadow: 0 4px 14px rgba(147, 51, 234, 0.25);

    &:hover, &:focus { background: linear-gradient(135deg, $primary-dark, #4f46e5); border-color: transparent; }
  }

  :deep(.el-input) {
    --el-input-border-radius: 999px;
    --el-input-border-color: #{$border};
    --el-input-hover-border-color: rgba(147, 51, 234, 0.35);
    --el-input-focus-border-color: #{$primary};
  }

  :deep(.el-input__wrapper) {
    border-radius: 999px;
    box-shadow: 0 0 0 1px $border inset;
    padding: 4px 14px;

    &:hover { box-shadow: 0 0 0 1px rgba(147, 51, 234, 0.35) inset; }
    &.is-focus { box-shadow: 0 0 0 1px $primary inset, 0 0 0 3px rgba(147, 51, 234, 0.08); }
  }

  :deep(.el-select) {
    --el-select-border-color-hover: rgba(147, 51, 234, 0.35);
  }

  :deep(.filter-select .el-select__wrapper) {
    min-height: 36px;
    border-radius: 999px;
    background: rgba(147, 51, 234, 0.08);
    border: 1px solid rgba(147, 51, 234, 0.24);
    box-shadow: none;
    padding: 0 12px 0 16px;

    &:hover {
      background: rgba(147, 51, 234, 0.12);
      border-color: rgba(147, 51, 234, 0.42);
      box-shadow: 0 4px 14px rgba(147, 51, 234, 0.08);
    }

    &.is-focus {
      background: #fff;
      border-color: rgba(147, 51, 234, 0.62);
      box-shadow: 0 0 0 3px rgba(147, 51, 234, 0.12);
    }
  }

  :deep(.filter-select .el-select__placeholder),
  :deep(.filter-select .el-select__selected-item) {
    font-size: 13px;
    font-weight: 700;
    color: $primary-dark;
  }

  :deep(.filter-select .el-select__placeholder.is-transparent) {
    color: rgba(126, 34, 206, 0.68);
    font-weight: 700;
  }

  :deep(.filter-select .el-select__caret) {
    color: $primary;
  }

  :deep(.filter-select .el-icon) {
    color: $primary;
  }

  :deep(.filter-select .el-select__suffix) {
    color: $primary;
  }

  :deep(.el-radio-group) {
    --el-radio-button-checked-bg-color: #{$primary};
    --el-radio-button-checked-border-color: #{$primary};
    --el-radio-button-checked-text-color: #fff;
  }

  :deep(.el-radio-button__inner) {
    border-radius: 999px !important;
    border-color: $border;
    font-weight: 600;
    font-size: 12.5px;
    padding: 7px 14px;
  }

  :deep(.el-radio-button:first-child .el-radio-button__inner) { border-radius: 999px !important; }
  :deep(.el-radio-button:last-child .el-radio-button__inner) { border-radius: 999px !important; }

  :deep(.el-radio-button.is-active .el-radio-button__inner) {
    background: $primary;
    border-color: $primary;
    box-shadow: -1px 0 0 0 $primary;
  }
}

.page-header {
  background: $bg-card;
  border-bottom: 1px solid $border;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 100;

  .header-content {
    max-width: 1000px;
    margin: 0 auto;
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;

    .back-btn {
      border: 1px solid $border;
      background: $bg-card;
      border-radius: 10px;
      &:hover { border-color: rgba($primary, 0.3); color: $primary; background: rgba($primary, 0.04); }
    }

    .header-title {
      display: flex;
      align-items: baseline;
      gap: 10px;

      h1 { font-size: 17px; font-weight: 700; margin: 0; color: $text; }
      .record-count { font-size: 12.5px; color: $text-muted; }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 10px;

    .search-input { width: 240px; }
    .more-btn { border: 1px solid $border; background: $bg-card; border-radius: 10px; &:hover { border-color: rgba($primary, 0.3); color: $primary; background: rgba($primary, 0.04); } }
  }
}

/* 统计卡片 · 轻量白底 */
.stats-section {
  padding: 20px 24px;
  background: $bg;

  .stats-container {
    max-width: 1000px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 14px;

    @media (max-width: 1024px) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    @media (max-width: 640px) { grid-template-columns: repeat(1, minmax(0, 1fr)); }
  }

  .stat-card {
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius;
    padding: 16px 18px;
    display: flex;
    align-items: center;
    gap: 14px;
    transition: border-color 0.2s, box-shadow 0.2s;

    &:hover { border-color: rgba($primary, 0.25); box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05); }

    .stat-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;

      &.blue { background: rgba(99, 102, 241, 0.1); color: #6366f1; }
      &.green { background: rgba($success, 0.1); color: $success; }
      &.orange { background: rgba($warning, 0.1); color: #d97706; }
      &.purple { background: rgba($primary, 0.1); color: $primary; }
    }

    .stat-info {
      display: flex;
      flex-direction: column;

      .stat-value { font-size: 22px; font-weight: 800; color: $text; }
      .stat-value-skeleton {
        width: 52px;
        height: 28px;
        border-radius: 8px;
        display: block;
      }
      .stat-label { font-size: 12px; color: $text-muted; margin-top: 2px; }
    }
  }
}

/* 筛选栏 */
.filter-section {
  background: $bg;
  padding: 10px 24px 14px;

  .filter-container {
    max-width: 1000px;
    margin: 0 auto;
    background: $bg-card;
    border: 1px solid $border;
    border-radius: 16px;
    padding: 12px 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
  }

  .filter-right {
    display: flex;
    gap: 10px;
    .course-filter { width: 154px; }
    .sort-select { width: 126px; }
  }
}

.history-backtop {
  width: 42px;
  height: 42px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(255, 255, 255, 0.92);
  color: $primary;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(12px);

  &:hover {
    background: #fff;
    color: $primary-dark;
    border-color: rgba(147, 51, 234, 0.3);
    transform: translateY(-1px);
  }
}

:global(.qa-history-filter-dropdown .el-select-dropdown__item) {
  border-radius: 8px;
  margin: 2px 6px;
}

:global(.qa-history-filter-dropdown .el-select-dropdown__item.is-selected) {
  background: rgba(147, 51, 234, 0.1);
  color: #7e22ce;
  font-weight: 700;
}

:global(.qa-history-filter-dropdown .el-select-dropdown__item.is-hovering) {
  background: rgba(147, 51, 234, 0.08);
}

/* 对话列表 */
.history-main {
  padding: 20px 24px;

  .history-container { max-width: 1000px; margin: 0 auto; }
}

.load-more-sentinel {
  height: 1px;
}

.history-skeleton,
.load-more-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton-group {
  margin-bottom: 28px;
}

.skeleton-group-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid $border;
}

.skeleton-line,
.skeleton-avatar,
.skeleton-actions span {
  position: relative;
  overflow: hidden;
  background: linear-gradient(90deg, #eef2f7 0%, #f8fafc 42%, #e8edf5 74%);
  background-size: 220% 100%;
  animation: skeleton-shimmer 1.25s ease-in-out infinite;
}

.skeleton-line {
  display: block;
  height: 12px;
  border-radius: 999px;
}

.skeleton-line-date { width: 52px; height: 14px; }
.skeleton-line-count { width: 64px; }
.skeleton-line-title { width: min(280px, 54vw); height: 16px; }
.skeleton-line-tag { width: 96px; height: 20px; border-radius: 6px; }
.skeleton-line-preview { width: min(520px, 70vw); margin-top: 8px; }
.skeleton-line-meta { width: 170px; margin-top: 10px; }

.skeleton-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

.skeleton-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;

  span {
    width: 32px;
    height: 32px;
    border-radius: 50%;
  }
}

.list-footer {
  text-align: center;
  padding: 18px 0 8px;
  font-size: 13px;
  color: $text-muted;
}

.history-group {
  margin-bottom: 28px;

  .group-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;
    padding-bottom: 10px;
    border-bottom: 1px solid $border;

    .group-date { font-size: 14px; font-weight: 700; color: $text; }
    .group-count { font-size: 12px; color: $text-muted; }
  }
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.session-card {
  background: $bg-card;
  border: 1px solid $border;
  border-radius: $radius;
  padding: 16px 18px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  animation: history-card-enter 0.28s ease both;

  &.skeleton-card {
    cursor: default;
    pointer-events: none;
  }

  &:hover {
    border-color: rgba($primary, 0.3);
    box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
    transform: translateY(-1px);
  }

  .card-main {
    display: flex;
    gap: 14px;
    align-items: flex-start;
  }

  .card-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    flex-shrink: 0;

    &.blue { background: rgba(99, 102, 241, 0.08); color: #6366f1; }
    &.green { background: rgba($success, 0.08); color: $success; }
    &.orange { background: rgba($warning, 0.08); color: #d97706; }
    &.purple { background: rgba($primary, 0.08); color: $primary; }
    &.cyan { background: rgba($cyan, 0.08); color: $cyan; }
  }

  .card-content {
    flex: 1;
    min-width: 0;

    .card-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
      flex-wrap: wrap;
    }

    .session-title { font-size: 14px; font-weight: 600; color: $text; margin: 0; }
    .card-tags { display: flex; gap: 6px; }

    .session-preview {
      font-size: 13px;
      color: $text-secondary;
      margin: 0 0 8px;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-meta {
      display: flex;
      gap: 16px;
      .meta-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: $text-muted; }
    }
  }

  .card-actions {
    display: flex;
    gap: 6px;
    flex-shrink: 0;

    .el-button {
      border: 1px solid $border;
      background: $bg-card;
      color: $text-muted;
      &:hover { background: rgba($primary, 0.06); color: $primary; border-color: rgba($primary, 0.25); }
      &.favorite-btn.active,
      &.favorite-btn:hover { background: rgba($warning, 0.1); color: #d97706; border-color: rgba($warning, 0.34); }
      &.delete-btn:hover { background: rgba($danger, 0.06); color: $danger; border-color: rgba($danger, 0.25); }
    }
  }

  .card-preview {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid $border;

    .preview-message {
      padding: 7px 10px;
      border-radius: 8px;
      margin-bottom: 6px;
      font-size: 12.5px;

      &.user { background: rgba($primary, 0.04); }
      &.assistant { background: $bg; }
      .preview-role { font-weight: 600; color: $text; margin-right: 6px; }
      .preview-text { color: $text-secondary; }
    }
  }
}

.empty-state {
  text-align: center;
  padding: 60px 20px;

  .empty-icon {
    width: 80px;
    height: 80px;
    background: rgba($primary, 0.06);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 20px;
    font-size: 36px;
    color: $text-muted;
  }

  h3 { font-size: 16px; color: $text; margin: 0 0 6px; }
  p { font-size: 13px; color: $text-secondary; margin: 0 0 20px; }
}

@keyframes skeleton-shimmer {
  0% { background-position: 120% 0; }
  100% { background-position: -120% 0; }
}

@keyframes history-card-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .session-card,
  .history-backtop,
  .skeleton-line,
  .skeleton-avatar,
  .skeleton-actions span {
    animation: none;
  }
}

@media (max-width: 768px) {
  .page-header {
    .header-content { gap: 12px; }
    .header-actions .search-input { width: min(48vw, 200px); }
  }
  .filter-section .filter-container { flex-direction: column; align-items: stretch; }
  .filter-section .filter-right {
    width: 100%;
    .course-filter, .sort-select { flex: 1; width: auto; }
  }
  .session-card .card-main { flex-direction: column; }
  .session-card .card-actions { align-self: flex-end; }
  .skeleton-actions { align-self: flex-end; }
}
</style>
