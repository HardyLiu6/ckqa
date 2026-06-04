<!-- 我的收藏 · 当前接入真实问答收藏，课程收藏后续补齐 -->
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Clock, Star } from '@element-plus/icons-vue'

import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { listCourses } from '@/api/courses'
import { listQaSessions } from '@/api/qa'
import { normalizeCourseList, normalizeQaSessionPage, formatRelativeSessionTime } from '@/views/qa/qa-session-model'

const router = useRouter()
const loading = ref(false)
const favoriteSessions = ref([])
const courses = ref([])

const courseNameById = computed(() => Object.fromEntries(
  courses.value.map((course) => [course.courseId, course.name]),
))

onMounted(() => {
  loadFavorites()
})

async function loadFavorites() {
  loading.value = true
  try {
    const [coursePayload, sessionPayload] = await Promise.all([
      listCourses({ page: 1, size: 100, status: 'active' }),
      listQaSessions({ status: 'active', favorite: true, sort: 'newest', page: 1, size: 50 }),
    ])
    courses.value = normalizeCourseList(coursePayload)
    favoriteSessions.value = normalizeQaSessionPage(sessionPayload).items
  } catch (error) {
    ElMessage.error(error?.message || '收藏列表加载失败')
    favoriteSessions.value = []
  } finally {
    loading.value = false
  }
}

function goToSession(session) {
  router.push({ path: '/qa/ask', query: { sessionId: session.id } })
}

function sessionCourseName(session) {
  return courseNameById.value[session.courseId] || session.courseId || '未绑定课程'
}
</script>

<template>
  <div class="fav-page">
    <div class="page-heading">
      <div>
        <h1 class="page-title">我的收藏</h1>
        <p>已收藏的问答对话会在这里集中展示</p>
      </div>
      <div class="heading-icon"><el-icon><Star /></el-icon></div>
    </div>

    <section class="fav-section">
      <div class="section-title">
        <span>问答收藏</span>
        <small>{{ favoriteSessions.length }} 个对话</small>
      </div>

      <div v-if="loading" class="fav-grid" aria-label="收藏问答加载中">
        <GlassCard v-for="index in 4" :key="`favorite-skeleton-${index}`" tier="light" padding="md" class="fav-card skeleton-card">
          <span class="skeleton-line skeleton-tag"></span>
          <span class="skeleton-line skeleton-title"></span>
          <span class="skeleton-line skeleton-meta"></span>
        </GlassCard>
      </div>

      <div v-else-if="favoriteSessions.length" class="fav-grid">
        <GlassCard
          v-for="session in favoriteSessions"
          :key="session.id"
          tier="light"
          padding="md"
          :hover="true"
          class="fav-card"
          @click="goToSession(session)"
        >
          <div class="card-top">
            <ModuleTag module="qa" size="sm">问答</ModuleTag>
            <span class="course-name">{{ sessionCourseName(session) }}</span>
          </div>
          <div class="title">{{ session.title }}</div>
          <div class="meta">
            <span><el-icon><ChatDotRound /></el-icon>{{ session.messageCount }} 条消息</span>
            <span><el-icon><Clock /></el-icon>{{ formatRelativeSessionTime(session.lastMessageAt || session.createdAt) }}</span>
          </div>
        </GlassCard>
      </div>

      <div v-else class="empty-state">
        <el-icon><Star /></el-icon>
        <span>还没有收藏的问答对话</span>
      </div>
    </section>

    <section class="fav-section muted-section">
      <div class="section-title">
        <span>课程收藏</span>
        <small>后续接入</small>
      </div>
      <p class="coming-hint">课程收藏的真实数据接口尚未接入，本页当前只展示真实问答收藏。</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.fav-page {
  padding: 24px;
  max-width: 920px;
  margin: 0 auto;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  p {
    margin: 4px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin: 0;
}

.heading-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  color: #d97706;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.22);
}

.fav-section {
  background: #fff;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: $radius-lg;
  padding: 16px;
  margin-bottom: 14px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;

  span {
    font-size: 15px;
    font-weight: 800;
    color: #0f172a;
  }

  small {
    color: #94a3b8;
    font-size: 12px;
  }
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.fav-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;

  .card-top,
  .meta {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .card-top {
    justify-content: space-between;
  }

  .course-name {
    max-width: 180px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: #8b5cf6;
  }

  .title {
    font-size: 14px;
    font-weight: 700;
    color: #0f172a;
    line-height: 1.45;
  }

  .meta {
    flex-wrap: wrap;
    font-size: 12px;
    color: #94a3b8;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }
}

.empty-state {
  min-height: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;

  .el-icon {
    font-size: 24px;
    color: #d97706;
  }
}

.muted-section {
  background: rgba(248, 250, 252, 0.72);
}

.coming-hint {
  color: #94a3b8;
  font-size: 12px;
  margin: 0;
}

.skeleton-card {
  cursor: default;
  pointer-events: none;
}

.skeleton-line {
  display: block;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, #eef2f7 0%, #f8fafc 42%, #e8edf5 74%);
  background-size: 220% 100%;
  animation: skeleton-shimmer 1.25s ease-in-out infinite;
}

.skeleton-tag { width: 54px; height: 20px; }
.skeleton-title { width: 72%; height: 15px; }
.skeleton-meta { width: 48%; }

@keyframes skeleton-shimmer {
  0% { background-position: 120% 0; }
  100% { background-position: -120% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-line {
    animation: none;
  }
}
</style>
