<template>
  <div class="qa-history-page">
    <!-- 头部 -->
    <header class="page-header">
      <div class="header-content">
        <div class="header-left">
          <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
          <div class="header-title">
            <h1>问答记录</h1>
            <span class="record-count">共 {{ sessionList.length }} 个对话</span>
          </div>
        </div>
        <div class="header-actions">
          <el-input v-model="searchKeyword" placeholder="搜索对话..." class="search-input" clearable>
            <template #prefix><el-icon>
                <Search />
              </el-icon></template>
          </el-input>
          <el-button type="primary" :icon="Plus" @click="goToAsk">新建对话</el-button>
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
          <div class="stat-info"><span class="stat-value">{{ stats.totalSessions }}</span><span
              class="stat-label">对话总数</span></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon green"><el-icon>
              <Comment />
            </el-icon></div>
          <div class="stat-info"><span class="stat-value">{{ stats.totalMessages }}</span><span
              class="stat-label">消息总数</span></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon orange"><el-icon>
              <Star />
            </el-icon></div>
          <div class="stat-info"><span class="stat-value">{{ stats.favoriteCount }}</span><span
              class="stat-label">收藏对话</span></div>
        </div>
        <div class="stat-card">
          <div class="stat-icon purple"><el-icon>
              <Reading />
            </el-icon></div>
          <div class="stat-info"><span class="stat-value">{{ stats.courseCount }}</span><span
              class="stat-label">涉及课程</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 筛选栏 -->
    <section class="filter-section">
      <div class="filter-container">
        <div class="filter-left">
          <el-radio-group v-model="filterType" @change="handleFilterChange">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="favorite">已收藏</el-radio-button>
            <el-radio-button value="today">今天</el-radio-button>
            <el-radio-button value="week">本周</el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-right">
          <el-select v-model="filterCourse" placeholder="选择课程" clearable class="course-filter">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-select v-model="sortBy" class="sort-select">
            <el-option value="newest" label="最新优先" />
            <el-option value="oldest" label="最早优先" />
            <el-option value="messages" label="消息最多" />
          </el-select>
        </div>
      </div>
    </section>

    <!-- 对话列表 -->
    <main class="history-main">
      <div class="history-container">
        <div v-for="group in groupedSessions" :key="group.date" class="history-group">
          <div class="group-header">
            <span class="group-date">{{ group.dateLabel }}</span>
            <span class="group-count">{{ group.items.length }} 个对话</span>
          </div>

          <div class="session-list">
            <div v-for="session in group.items" :key="session.id" class="session-card" @click="goToDetail(session.id)">
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
                  <el-tooltip content="继续对话"><el-button :icon="ChatLineRound" circle
                      @click.stop="continueChat(session)" /></el-tooltip>
                  <el-tooltip :content="session.isFavorite ? '取消收藏' : '收藏'"><el-button
                      :icon="session.isFavorite ? StarFilled : Star" circle :class="{ favorite: session.isFavorite }"
                      @click.stop="toggleFavorite(session)" /></el-tooltip>
                  <el-tooltip content="删除"><el-button :icon="Delete" circle class="delete-btn"
                      @click.stop="deleteSession(session)" /></el-tooltip>
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
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Search, Plus, More, Download, Delete, ChatDotRound, Comment, Star, StarFilled, Reading, Clock, ChatLineRound, DocumentDelete } from '@element-plus/icons-vue'
import { listCourses } from '@/api/courses'
import { listQaSessions } from '@/api/qa'
import { normalizeCourseList, normalizeQaSession } from './qa-session-model'

const router = useRouter()

const courses = ref([])
const sessionList = ref([])
const loading = ref(false)

// 筛选状态
const searchKeyword = ref('')
const filterType = ref('all')
const filterCourse = ref('')
const sortBy = ref('newest')

const courseList = computed(() => courses.value.map((course) => ({
  id: course.courseId,
  name: course.name,
})))
const courseNameById = computed(() => Object.fromEntries(
  courses.value.map((course) => [course.courseId, course.name]),
))

// 计算统计
const stats = computed(() => ({
  totalSessions: sessionList.value.length,
  totalMessages: sessionList.value.reduce((sum, s) => sum + s.messageCount, 0),
  favoriteCount: 0,
  courseCount: new Set(sessionList.value.map(s => s.courseId)).size
}))

// 过滤后的会话
const filteredSessions = computed(() => {
  let result = [...sessionList.value]

  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(s => s.title.toLowerCase().includes(kw) || s.lastMessage.toLowerCase().includes(kw))
  }

  if (filterType.value === 'favorite') result = result.filter(s => s.isFavorite)
  else if (filterType.value === 'today') {
    const today = new Date().toISOString().split('T')[0]
    result = result.filter(s => s.date === today)
  } else if (filterType.value === 'week') {
    const weekAgo = new Date(); weekAgo.setDate(weekAgo.getDate() - 7)
    result = result.filter(s => new Date(s.date) >= weekAgo)
  }

  if (filterCourse.value) result = result.filter(s => String(s.courseId) === String(filterCourse.value))

  if (sortBy.value === 'oldest') result.sort((a, b) => new Date(a.date) - new Date(b.date))
  else if (sortBy.value === 'messages') result.sort((a, b) => b.messageCount - a.messageCount)
  else result.sort((a, b) => new Date(b.date) - new Date(a.date))

  return result
})

// 按日期分组
const groupedSessions = computed(() => {
  const groups = {}
  const today = new Date().toISOString().split('T')[0]
  const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0]

  filteredSessions.value.forEach(s => {
    if (!groups[s.date]) {
      let label = s.date
      if (s.date === today) label = '今天'
      else if (s.date === yesterday) label = '昨天'
      else label = new Date(s.date).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
      groups[s.date] = { date: s.date, dateLabel: label, items: [] }
    }
    groups[s.date].items.push(s)
  })

  return Object.values(groups).sort((a, b) => new Date(b.date) - new Date(a.date))
})

// 方法
const goBack = () => router.back()
const goToAsk = () => router.push('/qa/ask')
const goToDetail = (id) => router.push({ path: '/qa/ask', query: { sessionId: id } })
const continueChat = (session) => router.push({ path: '/qa/ask', query: { sessionId: session.id } })
const handleFilterChange = () => { }

const getCourseTheme = (courseId) => {
  const themes = ['blue', 'green', 'orange', 'purple', 'cyan']
  const key = String(courseId ?? '').split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return themes[key % themes.length] || 'blue'
}

const truncate = (text, len) => text?.length > len ? text.substring(0, len) + '...' : text

const toggleFavorite = () => {
  ElMessage.info('收藏功能暂未接入后端')
}

const deleteSession = async () => {
  ElMessage.info('删除会话功能暂未接入后端')
}

const exportAll = () => {
  ElMessage.info('导出功能暂未接入后端')
}

const clearAll = async () => {
  ElMessage.info('清空记录功能暂未接入后端')
}

onMounted(async () => {
  await loadHistory()
})

async function loadHistory() {
  loading.value = true
  try {
    const [coursePayload, sessionPayload] = await Promise.all([
      listCourses({ page: 1, size: 100, status: 'active' }),
      listQaSessions({ status: 'active', page: 1, size: 50 }),
    ])
    courses.value = normalizeCourseList(coursePayload)
    const sessions = Array.isArray(sessionPayload) ? sessionPayload : sessionPayload?.items ?? []
    sessionList.value = sessions.map((session) => toSessionCard(normalizeQaSession(session)))
  } catch (error) {
    ElMessage.error(error?.message || '问答记录加载失败')
    sessionList.value = []
  } finally {
    loading.value = false
  }
}

function toSessionCard(session) {
  const referenceTime = session.lastMessageAt || session.createdAt || session.indexLockedAt || ''
  const date = String(referenceTime).slice(0, 10) || new Date().toISOString().slice(0, 10)
  return {
    ...session,
    courseName: courseNameById.value[session.courseId] || session.courseId || '未绑定课程',
    messageCount: 0,
    lastTime: formatSessionTime(referenceTime),
    lastMessage: session.isLegacy ? '旧会话仅可查看历史消息' : '点击继续这个真实问答会话',
    date,
    isFavorite: false,
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
$primary: #4f46e5;
$primary-light: #818cf8;
$primary-dark: #3730a3;
$success: #10b981;
$warning: #f59e0b;
$danger: #ef4444;
$cyan: #06b6d4;
$purple: #8b5cf6;
$bg: #f8fafc;
$bg-card: #fff;
$text: #1e293b;
$text-secondary: #64748b;
$text-muted: #94a3b8;
$border: #e2e8f0;
$radius: 12px;

.qa-history-page {
  min-height: 100vh;
  background: $bg;
}

.page-header {
  background: $bg-card;
  border-bottom: 1px solid $border;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 100;

  .header-content {
    max-width: 1200px;
    margin: 0 auto;
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .back-btn {
      border: none;
      background: $bg;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }

    .header-title {
      display: flex;
      align-items: center;
      gap: 12px;

      h1 {
        font-size: 18px;
        font-weight: 600;
        margin: 0;
      }

      .record-count {
        font-size: 13px;
        color: $text-muted;
      }
    }
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;

    .search-input {
      width: 260px;
    }

    .more-btn {
      border: none;
      background: $bg;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }
    }
  }
}

.stats-section {
  padding: 24px;
  background: linear-gradient(135deg, $primary, $primary-dark);

  .stats-container {
    max-width: 1200px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  .stat-card {
    background: rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(10px);
    border-radius: $radius;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;

    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      color: white;

      &.blue {
        background: rgba($primary, 0.5);
      }

      &.green {
        background: rgba($success, 0.5);
      }

      &.orange {
        background: rgba($warning, 0.5);
      }

      &.purple {
        background: rgba($purple, 0.5);
      }
    }

    .stat-info {
      display: flex;
      flex-direction: column;

      .stat-value {
        font-size: 28px;
        font-weight: 700;
        color: white;
      }

      .stat-label {
        font-size: 13px;
        color: rgba(255, 255, 255, 0.8);
      }
    }
  }
}

.filter-section {
  background: $bg-card;
  border-bottom: 1px solid $border;
  padding: 16px 24px;

  .filter-container {
    max-width: 1200px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }

  .filter-right {
    display: flex;
    gap: 12px;

    .course-filter {
      width: 180px;
    }

    .sort-select {
      width: 120px;
    }
  }
}

.history-main {
  padding: 24px;

  .history-container {
    max-width: 1200px;
    margin: 0 auto;
  }
}

.history-group {
  margin-bottom: 32px;

  .group-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid $border;

    .group-date {
      font-size: 16px;
      font-weight: 600;
      color: $text;
    }

    .group-count {
      font-size: 13px;
      color: $text-muted;
    }
  }
}

.session-list {
  display: grid;
  gap: 16px;
}

.session-card {
  background: $bg-card;
  border: 1px solid $border;
  border-radius: $radius;
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $primary-light;
    box-shadow: 0 4px 20px rgba($primary, 0.1);
    transform: translateY(-2px);
  }

  .card-main {
    display: flex;
    gap: 16px;
    align-items: flex-start;
  }

  .card-icon {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    flex-shrink: 0;

    &.blue {
      background: rgba($primary, 0.1);
      color: $primary;
    }

    &.green {
      background: rgba($success, 0.1);
      color: $success;
    }

    &.orange {
      background: rgba($warning, 0.1);
      color: $warning;
    }

    &.purple {
      background: rgba($purple, 0.1);
      color: $purple;
    }

    &.cyan {
      background: rgba($cyan, 0.1);
      color: $cyan;
    }
  }

  .card-content {
    flex: 1;
    min-width: 0;

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 8px;
      flex-wrap: wrap;
    }

    .session-title {
      font-size: 16px;
      font-weight: 600;
      color: $text;
      margin: 0;
    }

    .card-tags {
      display: flex;
      gap: 8px;
    }

    .session-preview {
      font-size: 14px;
      color: $text-secondary;
      margin: 0 0 12px;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .card-meta {
      display: flex;
      gap: 20px;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: $text-muted;
      }
    }
  }

  .card-actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;

    .el-button {
      border: none;
      background: $bg;
      color: $text-muted;

      &:hover {
        background: rgba($primary, 0.1);
        color: $primary;
      }

      &.favorite {
        color: $warning;
      }

      &.delete-btn:hover {
        background: rgba($danger, 0.1);
        color: $danger;
      }
    }
  }

  .card-preview {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid $border;

    .preview-message {
      padding: 8px 12px;
      border-radius: 8px;
      margin-bottom: 8px;
      font-size: 13px;

      &.user {
        background: rgba($primary, 0.05);
      }

      &.assistant {
        background: $bg;
      }

      .preview-role {
        font-weight: 500;
        color: $text;
        margin-right: 8px;
      }

      .preview-text {
        color: $text-secondary;
      }
    }
  }
}

.empty-state {
  text-align: center;
  padding: 80px 20px;

  .empty-icon {
    width: 100px;
    height: 100px;
    background: $bg;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 24px;
    font-size: 48px;
    color: $text-muted;
  }

  h3 {
    font-size: 18px;
    color: $text;
    margin: 0 0 8px;
  }

  p {
    font-size: 14px;
    color: $text-secondary;
    margin: 0 0 24px;
  }
}

@media (max-width: 768px) {
  .page-header .header-actions .search-input {
    width: 180px;
  }

  .filter-section .filter-container {
    flex-direction: column;
    align-items: stretch;
  }

  .session-card .card-main {
    flex-direction: column;
  }

  .session-card .card-actions {
    align-self: flex-end;
  }
}
</style>
