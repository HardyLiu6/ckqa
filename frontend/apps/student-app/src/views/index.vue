<!-- frontend/apps/student-app/src/views/index.vue -->
<!-- 登录后首页 · Indigo · Product Layout -->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCourseStore, useUserStore } from '@/stores'
import { listQaSessions, getQaSessionStats } from '@/api/qa'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { ArrowRight, Reading, ChatDotRound, Share, DataAnalysis, Search, Collection } from '@element-plus/icons-vue'
import { normalizeQaSessionStats } from './qa/qa-session-model'
import {
  DEFAULT_HOME_COURSE_COVER,
  buildHomeCourseItems,
  buildHomeRecentQaItems,
  resolveHomeGreetingName,
} from './home/home-page-model'

const router = useRouter()
const courseStore = useCourseStore()
const userStore = useUserStore()

const qaStats = ref({ totalSessions: 0, totalMessages: 0, courseCount: 0, favoriteCount: 0 })
const recentSessionPayload = ref({ items: [], total: 0 })
const homeLoading = ref(false)
const qaErrorMessage = ref('')

const greetingName = computed(() => resolveHomeGreetingName(userStore.userInfo))
const timeOfDay = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const defaultCourse = {
  id: '',
  title: '选择一门课程',
  cover: DEFAULT_HOME_COURSE_COVER,
  progress: 0,
  lastLearnAt: '从课程中心开始学习',
  meta: '课程资料待完善',
}

const courseNameById = computed(() => Object.fromEntries(
  courseStore.allCourses.flatMap((course) => [
    [String(course.id ?? ''), course.title],
    [String(course.courseId ?? ''), course.title],
  ]).filter(([id]) => id),
))

const recentCourse = computed(() => {
  return myCourses.value[0] || defaultCourse
})

const hotQuestions = [
  '什么是进程同步？',
  '红黑树的插入规则',
  'Vue3 的响应式原理',
  '动态规划怎么入手？',
]

const modules = computed(() => [
  {
    key: 'course',
    label: '课程中心',
    desc: courseStore.allCourses.length ? `${courseStore.allCourses.length} 门可见课程` : '课程中心',
    route: '/course/list',
    icon: Reading,
  },
  {
    key: 'qa',
    label: '智能问答',
    desc: qaStats.value.totalSessions ? `${qaStats.value.totalSessions} 个对话 / ${qaStats.value.totalMessages} 条消息` : 'AI 专业解答',
    route: '/qa/ask',
    icon: ChatDotRound,
  },
  {
    key: 'knowledge',
    label: '知识图谱',
    desc: courseStore.allCourses.length ? `${courseStore.allCourses.reduce((sum, course) => sum + Number(course.activeKnowledgeBaseCount ?? 0), 0)} 个活跃知识库` : '可视化学科脉络',
    route: '/knowledge/graph',
    icon: Share,
  },
  { key: 'analysis', label: '学习分析', desc: '错题 / 报告 / 推荐', route: '/analysis/wrong', icon: DataAnalysis },
])

const myCourses = computed(() => buildHomeCourseItems(courseStore.myCoursesWithDetail || []).slice(0, 3))

const recentQAs = computed(() => buildHomeRecentQaItems(recentSessionPayload.value, courseNameById.value))

function goQA(prefill) {
  router.push({ path: '/qa/ask', query: prefill ? { topic: prefill } : {} })
}
function goCourse(id) {
  if (!id) {
    router.push('/course/list')
    return
  }
  router.push(`/course/detail/${encodeURIComponent(id)}`)
}
function goQADetail(q) {
  router.push(q.route)
}

async function loadHomeData() {
  homeLoading.value = true
  qaErrorMessage.value = ''
  await Promise.allSettled([
    courseStore.loadCourses({ force: true }),
    loadQaOverview(),
  ])
  homeLoading.value = false
}

async function loadQaOverview() {
  try {
    const [statsPayload, sessionPayload] = await Promise.all([
      getQaSessionStats({ status: 'active' }),
      listQaSessions({ status: 'active', sort: 'newest', page: 1, size: 3 }),
    ])
    qaStats.value = normalizeQaSessionStats(statsPayload)
    recentSessionPayload.value = sessionPayload
  } catch (error) {
    qaErrorMessage.value = error?.message || '最近问答加载失败'
    recentSessionPayload.value = { items: [], total: 0 }
    qaStats.value = { totalSessions: 0, totalMessages: 0, courseCount: 0, favoriteCount: 0 }
  }
}

onMounted(() => {
  loadHomeData()
})
</script>

<template>
  <div class="home-page">
    <div class="page-glow"></div>

    <div class="home-inner">
      <!-- 欢迎 / 继续学习 -->
      <GlassCard tier="base" padding="lg" class="welcome-card" :class="{ 'is-loading': homeLoading }">
        <div class="welcome-row">
          <div class="welcome-left">
            <ModuleTag module="home" size="sm">{{ timeOfDay }}，{{ greetingName }}</ModuleTag>
            <h1 class="welcome-title">
              继续学习 <span class="module-accent">{{ recentCourse.title }}</span>
            </h1>
            <p class="welcome-sub">{{ recentCourse.lastLearnAt || '最近一次学习' }} · 已完成 {{ recentCourse.progress }}%</p>
          </div>
          <div class="welcome-right">
            <GlowButton size="md" @click="goCourse(recentCourse.id)">
              {{ recentCourse.id ? '继续学习' : '浏览课程' }}
              <template #suffix>
                <el-icon><ArrowRight /></el-icon>
              </template>
            </GlowButton>
          </div>
        </div>

        <!-- 快捷问答 -->
        <div class="quick-ask">
          <div class="quick-input" @click="goQA()">
            <el-icon><Search /></el-icon>
            <span>有什么课程问题想问？直接提问或选个热门话题</span>
          </div>
          <div class="quick-tags">
            <button v-for="q in hotQuestions" :key="q" class="hot-chip" @click="goQA(q)">
              {{ q }}
            </button>
          </div>
        </div>
      </GlassCard>
      <!-- 四大模块入口 -->
      <div class="modules-grid">
        <GlassCard
          v-for="m in modules"
          :key="m.key"
          tier="light"
          padding="md"
          :hover="true"
          :class="['module-card', `module-${m.key}`]"
          @click="router.push(m.route)"
        >
          <div class="module-halo"></div>
          <div class="module-icon-wrap">
            <el-icon :size="20"><component :is="m.icon" /></el-icon>
          </div>
          <div class="module-label">{{ m.label }}</div>
          <div class="module-desc">{{ m.desc }}</div>
        </GlassCard>
      </div>

      <!-- 双栏 -->
      <div class="dual-col">
        <GlassCard tier="light" padding="md" class="col-card">
          <div class="col-header">
            <div class="col-title">
              <el-icon><Collection /></el-icon>
              我的课程
            </div>
            <button class="link-btn" @click="router.push('/course/my')">查看全部 →</button>
          </div>
          <div class="course-list">
            <div
              v-for="c in myCourses"
              :key="c.id"
              class="course-row"
              @click="goCourse(c.id)"
            >
              <img :src="c.cover || DEFAULT_HOME_COURSE_COVER" :alt="c.title" class="course-cover" />
              <div class="course-info">
                <div class="course-name">{{ c.title }}</div>
                <div class="course-meta-line">{{ c.meta }}</div>
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: c.progress + '%' }"></div>
                </div>
              </div>
              <div class="progress-text">{{ c.progress }}%</div>
            </div>
            <div v-if="!myCourses.length" class="empty-hint">还没有课程，先去 <button class="link-btn" @click="router.push('/course/list')">课程中心</button> 看看</div>
          </div>
        </GlassCard>

        <GlassCard tier="light" padding="md" class="col-card qa-col">
          <div class="col-header">
            <div class="col-title">
              <el-icon><ChatDotRound /></el-icon>
              最近问答
            </div>
            <button class="link-btn qa-link" @click="router.push('/qa/history')">查看全部 →</button>
          </div>
          <div class="qa-list">
            <div
              v-for="q in recentQAs"
              :key="q.id"
              class="qa-row"
              :class="{ active: q.active }"
              @click="goQADetail(q)"
            >
              <div class="qa-title">{{ q.title }}</div>
              <div class="qa-meta">{{ q.time }} · {{ q.subject }}</div>
            </div>
            <div v-if="qaErrorMessage" class="empty-hint">{{ qaErrorMessage }}</div>
            <div v-else-if="!recentQAs.length" class="empty-hint">暂无近期问答，先去 <button class="link-btn qa-link" @click="goQA()">智能问答</button> 试试</div>
          </div>
        </GlassCard>
      </div>
    </div>
  </div>
</template>
<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.home-page {
  position: relative;
  min-height: calc(100vh - 64px);
  box-sizing: border-box;
  width: 100%;
  overflow-x: hidden;
  padding: clamp(16px, 2.2vw, 32px);
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);
  @media (max-width: $bp-tablet) { padding: 16px; }
}

.page-glow {
  position: absolute;
  width: 480px; height: 480px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15), transparent 60%);
  border-radius: 50%;
  top: -120px; right: 0;
  transform: translateX(28%);
  filter: blur(40px);
  pointer-events: none;
}

.home-inner {
  position: relative;
  width: 100%;
  max-width: min(1280px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.45vw, 20px);
  min-width: 0;
}

// ========== Welcome Card ==========
.welcome-card {
  --module-color-500: #6366f1;
  --module-color-700: #4338ca;
}

.welcome-row {
  display: flex;
  align-items: center;
  gap: 20px;
  @media (max-width: $bp-tablet) { flex-direction: column; align-items: stretch; }
}

.welcome-left { flex: 1; }
.welcome-right { flex-shrink: 0; }

.welcome-title {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 8px 0 4px;
  overflow-wrap: anywhere;

  .module-accent {
    background: linear-gradient(135deg, #6366f1, #818cf8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}
.welcome-sub {
  font-size: 14px;
  color: #64748b;
}

.quick-ask {
  margin-top: 20px;

  .quick-input {
    display: flex;
    align-items: center;
    gap: 10px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: $radius-lg;
    padding: 12px 16px;
    color: #9ca3af;
    font-size: 14px;
    cursor: pointer;
    transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

    &:hover {
      border-color: #9333ea;
      box-shadow: 0 0 0 4px rgba(147, 51, 234, 0.08);
    }
  }

  .quick-tags {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .hot-chip {
    padding: 4px 12px;
    background: rgba(147, 51, 234, 0.08);
    border: 1px solid rgba(147, 51, 234, 0.2);
    color: #9333ea;
    font-family: inherit;
    font-size: 12px;
    border-radius: $radius-full;
    cursor: pointer;
    transition: background $duration-fast $ease-out;
    &:hover { background: rgba(147, 51, 234, 0.15); }
  }
}

// ========== Modules Grid ==========
.modules-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
  @media (max-width: $bp-laptop) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}
.module-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  min-width: 0;

  .module-halo {
    position: absolute;
    width: 120px; height: 120px;
    border-radius: 50%;
    top: -30px; right: -30px;
    filter: blur(20px);
    pointer-events: none;
    opacity: 0.5;
  }
  .module-icon-wrap {
    position: relative;
    width: 40px; height: 40px;
    border-radius: $radius-lg;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    margin-bottom: 12px;
  }
  .module-label { font-size: 15px; font-weight: 700; color: #0f172a; overflow-wrap: anywhere; }
  .module-desc { font-size: 12px; color: #64748b; margin-top: 4px; overflow-wrap: anywhere; }
}
.module-course .module-halo { background: radial-gradient(circle, rgba(37, 99, 235, 0.3), transparent 60%); }
.module-course .module-icon-wrap { background: linear-gradient(135deg, #2563eb, #60a5fa); box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3); }
.module-qa .module-halo { background: radial-gradient(circle, rgba(147, 51, 234, 0.3), transparent 60%); }
.module-qa .module-icon-wrap { background: linear-gradient(135deg, #9333ea, #c084fc); box-shadow: 0 4px 16px rgba(147, 51, 234, 0.3); }
.module-knowledge .module-halo { background: radial-gradient(circle, rgba(13, 148, 136, 0.3), transparent 60%); }
.module-knowledge .module-icon-wrap { background: linear-gradient(135deg, #0d9488, #2dd4bf); box-shadow: 0 4px 16px rgba(13, 148, 136, 0.3); }
.module-analysis .module-halo { background: radial-gradient(circle, rgba(219, 39, 119, 0.3), transparent 60%); }
.module-analysis .module-icon-wrap { background: linear-gradient(135deg, #db2777, #f472b6); box-shadow: 0 4px 16px rgba(219, 39, 119, 0.3); }

// ========== Dual Col ==========
.dual-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  min-width: 0;
  @media (max-width: $bp-laptop) { grid-template-columns: 1fr; }
}

.col-card {
  min-width: 0;
}

.col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;

  .col-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
  .link-btn {
    background: transparent;
    border: 0;
    color: #2563eb;
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;
    &.qa-link { color: #9333ea; }
    &:hover { opacity: 0.8; }
  }
}

.course-list, .qa-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.course-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;
  min-width: 0;
  &:hover { background: rgba(37, 99, 235, 0.04); }

  .course-cover {
    width: 60px; height: 40px;
    border-radius: $radius-md;
    object-fit: cover;
  }
  .course-info { flex: 1; min-width: 0; }
  .course-name {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .course-meta-line {
    margin-top: 2px;
    font-size: 11px;
    color: #94a3b8;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .progress-bar {
    height: 4px;
    background: #e5e7eb;
    border-radius: 2px;
    margin-top: 4px;
    overflow: hidden;

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #2563eb, #60a5fa);
      border-radius: 2px;
      box-shadow: 0 0 6px #60a5fa;
      transition: width $duration-base $ease-out;
    }
  }
  .progress-text { font-size: 11px; color: #2563eb; font-weight: 600; }
}

.qa-row {
  padding: 8px 10px;
  border-left: 2px solid #e5e7eb;
  border-radius: 0 $radius-md $radius-md 0;
  cursor: pointer;
  transition: background $duration-fast $ease-out;
  min-width: 0;
  &:hover { background: rgba(147, 51, 234, 0.04); }

  &.active {
    border-left-color: #9333ea;
    background: rgba(147, 51, 234, 0.05);
    .qa-title { color: #7e22ce; }
  }

  .qa-title {
    font-size: 13px;
    font-weight: 500;
    color: #0f172a;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .qa-meta {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 2px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.empty-hint {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px;
  text-align: center;
}
</style>
