<template>
  <div class="course-detail-page">
    <div v-if="isLoading" class="loading-state">
      <el-skeleton :rows="6" animated />
    </div>
    <el-empty v-else-if="!course" description="课程不存在或暂无访问权限" :image-size="180">
      <el-button type="primary" @click="$router.push('/course/list')">返回课程列表</el-button>
    </el-empty>
    <template v-else>
      <!-- 课程头部信息 -->
      <div class="course-header">
        <div class="header-bg">
          <img :src="course.cover || defaultCover" alt="" />
          <div class="bg-overlay"></div>
        </div>

        <div class="header-content">
          <div class="content-left">
            <div class="course-cover">
              <img :src="course.cover || defaultCover" :alt="course.title" />
              <div v-if="course.memberStatus === 'member'" class="cover-badge member">
                <el-icon>
                  <Trophy />
                </el-icon>
                已加入
              </div>
              <div v-else-if="course.accessPolicy === 'public'" class="cover-badge public">
                公开课程
              </div>
            </div>
          </div>

          <div class="content-right">
            <div class="course-tags">
              <el-tag v-if="course.category" type="primary">{{ course.category }}</el-tag>
              <el-tag v-if="course.difficulty" type="warning">
                {{ formatDifficulty(course.difficulty) }}
              </el-tag>
              <el-tag v-for="tag in course.tags.slice(0, 4)" :key="tag" type="info">
                {{ tag }}
              </el-tag>
            </div>

            <h1 class="course-title">{{ course.title }}</h1>
            <p class="course-desc">{{ course.description || '暂无简介' }}</p>

            <div class="course-stats">
              <div class="stat-item">
                <div class="stat-value">{{ course.materialCount }}</div>
                <div class="stat-label">课程资料</div>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <div class="stat-value">{{ course.activeKnowledgeBaseCount }}</div>
                <div class="stat-label">激活知识库</div>
              </div>
              <div v-if="course.estimatedHours" class="stat-divider"></div>
              <div v-if="course.estimatedHours" class="stat-item">
                <div class="stat-value">{{ course.estimatedHours }}</div>
                <div class="stat-label">预计学时</div>
              </div>
              <div v-if="course.teachers.length" class="stat-divider"></div>
              <div v-if="course.teachers.length" class="stat-item">
                <div class="stat-value">{{ course.teachers.length }}</div>
                <div class="stat-label">授课教师</div>
              </div>
            </div>

            <div v-if="course.teacher && course.teacher.name" class="teacher-card">
              <el-avatar :size="56">{{ course.teacher.name.slice(0, 1) }}</el-avatar>
              <div class="teacher-info">
                <div class="teacher-name">{{ course.teacher.name }}</div>
                <div class="teacher-title">
                  {{ course.teachers.length > 1 ? `等 ${course.teachers.length} 位授课教师` : '授课教师' }}
                </div>
              </div>
            </div>

            <div class="action-buttons">
              <el-button type="primary" size="large" @click="goToGraph">
                <el-icon>
                  <Connection />
                </el-icon>
                浏览知识图谱
              </el-button>
              <el-button size="large" @click="goToQA">
                <el-icon>
                  <ChatDotRound />
                </el-icon>
                进入问答
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 课程内容区域 -->
      <div class="course-body">
        <div class="body-container">
          <div class="main-content">
            <el-tabs v-model="activeTab" class="content-tabs">
              <!-- 课程目录（资料） -->
              <el-tab-pane label="课程资料" name="materials">
                <div v-if="materialsLoading" class="materials-loading">
                  <el-skeleton :rows="3" animated />
                </div>
                <div v-else-if="!materials.length" class="materials-empty">
                  <el-empty description="该课程暂未上传资料" :image-size="120" />
                </div>
                <div v-else class="materials-list">
                  <div v-for="material in materials" :key="material.id" class="material-item">
                    <div class="material-icon">
                      <el-icon>
                        <Document />
                      </el-icon>
                    </div>
                    <div class="material-info">
                      <span class="material-title">{{ material.fileName || `资料 #${material.id}` }}</span>
                      <span class="material-status">
                        解析状态：
                        <el-tag :type="parseStatusTagType(material.parseStatus)" size="small">
                          {{ formatParseStatus(material.parseStatus) }}
                        </el-tag>
                      </span>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <!-- 课程介绍 -->
              <el-tab-pane label="课程介绍" name="intro">
                <div class="course-intro">
                  <div class="intro-section">
                    <h3>课程简介</h3>
                    <p>{{ course.description || '暂无更多介绍。' }}</p>
                  </div>

                  <div v-if="course.objectives.length" class="intro-section">
                    <h3>你将学到</h3>
                    <div class="learn-list">
                      <div class="learn-item" v-for="(item, index) in course.objectives" :key="index">
                        <el-icon>
                          <CircleCheck />
                        </el-icon>
                        <span>{{ item }}</span>
                      </div>
                    </div>
                  </div>

                  <div v-if="course.audience.length" class="intro-section">
                    <h3>适合人群</h3>
                    <div class="audience-tags">
                      <el-tag v-for="(item, index) in course.audience" :key="index" size="large">
                        {{ item }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <!-- 章节目录（占位） -->
              <el-tab-pane label="课程章节" name="chapters">
                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  title="功能预览"
                  description="课程章节 / 视频 / 学习进度功能正在准备中，正式数据待接入。"
                />
                <div class="chapter-placeholder">
                  <el-empty description="本期暂未开放章节内容" :image-size="120" />
                </div>
              </el-tab-pane>

              <!-- 学习进度（占位） -->
              <el-tab-pane label="学习进度" name="progress">
                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  :title="progressEnrolled ? '功能预览' : '尚未加入课程'"
                  :description="progressMessage"
                />
                <div class="progress-placeholder">
                  <el-empty description="本期暂未开放学习进度" :image-size="120" />
                </div>
              </el-tab-pane>
            </el-tabs>
          </div>

          <!-- 右侧边栏 -->
          <div class="sidebar">
            <div v-if="course.teachers.length" class="sidebar-card">
              <h4>授课教师</h4>
              <div v-for="teacher in course.teachers" :key="teacher.userId" class="teacher-item">
                <el-avatar :size="48">
                  {{ (teacher.displayName || teacher.username || '?').slice(0, 1) }}
                </el-avatar>
                <div class="teacher-meta">
                  <div class="teacher-meta-name">
                    {{ teacher.displayName || teacher.username || `教师 #${teacher.userId}` }}
                  </div>
                  <div class="teacher-meta-sub">用户编码：{{ teacher.userCode || '-' }}</div>
                </div>
              </div>
            </div>

            <div class="sidebar-card">
              <h4>课程信息</h4>
              <div class="info-row">
                <span class="info-label">课程编码</span>
                <span class="info-value">{{ course.courseId }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">访问策略</span>
                <span class="info-value">{{ course.accessPolicy === 'public' ? '公开' : '受限' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">最近更新</span>
                <span class="info-value">{{ formatTime(course.updatedAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Trophy, Connection, ChatDotRound, Document, CircleCheck,
} from '@element-plus/icons-vue'

import { useCourseStore } from '@/stores/course'
import { listCourseMaterials, getMyCourseProgress } from '@/api/courses'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()

const defaultCover = '/api/v1/course-covers/default-course-cover.svg'

const courseId = computed(() => decodeURIComponent(String(route.params.id ?? '')))

const course = ref(null)
const isLoading = ref(false)

const materials = ref([])
const materialsLoading = ref(false)

const progressEnrolled = ref(false)
const progressMessage = ref('学习进度功能即将开放，敬请期待')

const activeTab = ref('materials')

async function loadCourse() {
  if (!courseId.value) return
  isLoading.value = true
  try {
    course.value = await courseStore.loadCourseDetail(courseId.value)
  } finally {
    isLoading.value = false
  }
  if (course.value) {
    loadMaterials()
    loadProgress()
  }
}

async function loadMaterials() {
  if (!courseId.value) return
  materialsLoading.value = true
  try {
    const data = await listCourseMaterials(courseId.value)
    materials.value = Array.isArray(data) ? data : []
  } catch (err) {
    materials.value = []
    ElMessage.warning(err?.message || '加载课程资料失败')
  } finally {
    materialsLoading.value = false
  }
}

async function loadProgress() {
  if (!courseId.value) return
  try {
    const data = await getMyCourseProgress(courseId.value)
    progressEnrolled.value = !!data?.enrolled
    if (data?.message) {
      progressMessage.value = data.message
    }
  } catch (err) {
    // 占位接口失败不影响主流程
    progressEnrolled.value = false
  }
}

function formatDifficulty(value) {
  if (value === 'beginner') return '入门'
  if (value === 'intermediate') return '进阶'
  if (value === 'advanced') return '高级'
  return value
}

function formatParseStatus(value) {
  if (value === 'done') return '已解析'
  if (value === 'processing') return '解析中'
  if (value === 'failed') return '解析失败'
  return '待解析'
}

function parseStatusTagType(value) {
  if (value === 'done') return 'success'
  if (value === 'failed') return 'danger'
  if (value === 'processing') return 'warning'
  return 'info'
}

function formatTime(iso) {
  if (!iso) return '-'
  try {
    return new Date(iso).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

function goToGraph() {
  router.push({ path: '/knowledge/graph', query: { courseId: courseId.value } })
}

function goToQA() {
  router.push({ path: '/qa/ask', query: { courseId: courseId.value } })
}

watch(courseId, loadCourse)

onMounted(loadCourse)
</script>

<style lang="scss" scoped>
.course-detail-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.loading-state {
  max-width: 1200px;
  margin: 0 auto;
  padding: 60px 40px;
}

// 课程头部
.course-header {
  position: relative;
  padding: 60px 0;

  .header-bg {
    position: absolute;
    inset: 0;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      filter: blur(30px);
      transform: scale(1.1);
    }

    .bg-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.92) 0%, rgba(118, 75, 162, 0.92) 100%);
    }
  }

  .header-content {
    position: relative;
    z-index: 2;
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 40px;
    display: flex;
    gap: 48px;
  }

  .content-left {
    flex-shrink: 0;

    .course-cover {
      position: relative;
      width: 360px;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);

      img {
        width: 100%;
        display: block;
      }

      .cover-badge {
        position: absolute;
        top: 16px;
        left: 16px;
        color: #fff;
        padding: 8px 16px;
        border-radius: 20px;
        font-size: 13px;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 6px;

        &.member {
          background: linear-gradient(135deg, #f7ba2a 0%, #f56c6c 100%);
        }

        &.public {
          background: rgba(99, 102, 241, 0.85);
        }
      }
    }
  }

  .content-right {
    flex: 1;
    color: #fff;

    .course-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 16px;

      .el-tag {
        background: rgba(255, 255, 255, 0.2);
        border: none;
        color: #fff;
        padding: 6px 14px;
        border-radius: 20px;
      }
    }

    .course-title {
      font-size: 30px;
      font-weight: 700;
      margin: 0 0 12px;
      line-height: 1.4;
    }

    .course-desc {
      font-size: 15px;
      line-height: 1.8;
      opacity: 0.9;
      margin: 0 0 24px;
    }
  }

  .course-stats {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 24px;
    margin-bottom: 24px;
    padding: 20px 24px;
    background: rgba(255, 255, 255, 0.12);
    border-radius: 16px;
    backdrop-filter: blur(10px);

    .stat-item {
      text-align: center;

      .stat-value {
        font-size: 22px;
        font-weight: 700;
        margin-bottom: 4px;
      }

      .stat-label {
        font-size: 12px;
        opacity: 0.85;
      }
    }

    .stat-divider {
      width: 1px;
      height: 36px;
      background: rgba(255, 255, 255, 0.25);
    }
  }

  .teacher-card {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 24px;

    .teacher-info {
      .teacher-name {
        font-size: 17px;
        font-weight: 600;
        margin-bottom: 4px;
      }

      .teacher-title {
        font-size: 13px;
        opacity: 0.85;
      }
    }
  }

  .action-buttons {
    display: flex;
    gap: 14px;

    .el-button--large {
      padding: 16px 32px;
      font-size: 15px;
      border-radius: 12px;
    }
  }
}

// 课程主体
.course-body {
  .body-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 40px;
    display: flex;
    gap: 28px;
  }

  .main-content {
    flex: 1;
    min-width: 0;
  }

  .content-tabs {
    background: #fff;
    border-radius: 16px;
    padding: 24px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.05);

    :deep(.el-tabs__header) {
      margin-bottom: 24px;
    }

    :deep(.el-tabs__item) {
      font-size: 15px;
      font-weight: 500;
    }
  }

  .sidebar {
    width: 300px;
    flex-shrink: 0;
  }
}

.materials-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.material-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: #f8f9fc;
  border-radius: 12px;

  .material-icon {
    width: 38px;
    height: 38px;
    background: #fff;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #667eea;
    font-size: 18px;
  }

  .material-info {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .material-title {
      font-size: 14px;
      color: #303133;
    }

    .material-status {
      font-size: 12px;
      color: #909399;
    }
  }
}

.course-intro {
  .intro-section {
    margin-bottom: 28px;

    h3 {
      font-size: 17px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 14px;
      padding-left: 12px;
      border-left: 4px solid #667eea;
    }

    p {
      font-size: 14px;
      line-height: 1.85;
      color: #606266;
      margin: 0;
    }
  }

  .learn-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;

    .learn-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: #606266;

      .el-icon {
        color: #67c23a;
        font-size: 18px;
      }
    }
  }

  .audience-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }
}

.chapter-placeholder,
.progress-placeholder {
  margin-top: 16px;
}

.sidebar-card {
  background: #fff;
  border-radius: 16px;
  padding: 22px;
  margin-bottom: 22px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);

  h4 {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.teacher-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;

  &:last-child {
    margin-bottom: 0;
  }

  .teacher-meta-name {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  .teacher-meta-sub {
    font-size: 12px;
    color: #909399;
  }
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 13px;

  .info-label {
    color: #909399;
  }

  .info-value {
    color: #303133;
    font-weight: 500;
  }
}

@media (max-width: 1024px) {
  .course-header .header-content {
    flex-direction: column;
    padding: 0 20px;
  }

  .course-header .content-left .course-cover {
    width: 100%;
    max-width: 360px;
    margin: 0 auto;
  }

  .course-body .body-container {
    flex-direction: column;
    padding: 20px;
  }

  .course-body .sidebar {
    width: 100%;
  }
}
</style>
