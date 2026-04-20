<template>
  <div class="my-course-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="page-title">
            <el-icon>
              <Collection />
            </el-icon>
            我的课程
          </h1>
          <p class="page-subtitle">继续你的学习之旅</p>
        </div>

        <div class="stats-cards">
          <div class="stat-card">
            <div class="stat-icon"><el-icon>
                <Reading />
              </el-icon></div>
            <div class="stat-info">
              <span class="stat-value">{{ learningCount }}</span>
              <span class="stat-label">学习中</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon completed"><el-icon>
                <Trophy />
              </el-icon></div>
            <div class="stat-info">
              <span class="stat-value">{{ completedCount }}</span>
              <span class="stat-label">已完成</span>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon time"><el-icon>
                <Clock />
              </el-icon></div>
            <div class="stat-info">
              <span class="stat-value">{{ totalHours }}h</span>
              <span class="stat-label">学习时长</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-radio-group v-model="filterStatus">
          <el-radio-button value="all">全部课程</el-radio-button>
          <el-radio-button value="learning">学习中</el-radio-button>
          <el-radio-button value="completed">已完成</el-radio-button>
        </el-radio-group>
      </div>
      <div class="filter-right">
        <el-input v-model="searchKeyword" placeholder="搜索课程" clearable style="width: 240px">
          <template #prefix><el-icon>
              <Search />
            </el-icon></template>
        </el-input>
      </div>
    </div>

    <!-- 继续学习 -->
    <div class="course-list-section">
      <div class="continue-learning" v-if="lastLearnedCourse && filterStatus === 'all'">
        <div class="section-header">
          <h3>继续学习</h3>
        </div>
        <div class="continue-card" @click="continueLearning(lastLearnedCourse)">
          <div class="continue-cover">
            <img :src="lastLearnedCourse.cover" :alt="lastLearnedCourse.title" />
            <div class="play-overlay">
              <el-button type="primary" circle size="large">
                <el-icon size="28">
                  <VideoPlay />
                </el-icon>
              </el-button>
            </div>
          </div>
          <div class="continue-info">
            <h4>{{ lastLearnedCourse.title }}</h4>
            <p class="teacher">{{ lastLearnedCourse.teacher }}</p>
            <div class="progress-section">
              <el-progress :percentage="lastLearnedCourse.progress" :stroke-width="8" color="#667eea" />
              <span class="progress-text">已学习 {{ lastLearnedCourse.progress }}%</span>
            </div>
            <div class="last-learn">
              <el-icon>
                <Clock />
              </el-icon>
              <span>上次学习：{{ lastLearnedCourse.lastLearnAt }}</span>
            </div>
          </div>
          <div class="continue-action">
            <el-button type="primary" size="large">
              继续学习
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 课程网格 -->
      <div class="section-header" v-if="filteredCourses.length > 0">
        <h3>{{ filterStatus === 'all' ? '全部课程' : filterStatus === 'learning' ? '学习中' : '已完成' }}</h3>
        <span class="course-count">共 {{ filteredCourses.length }} 门课程</span>
      </div>

      <div class="course-grid">
        <div v-for="course in filteredCourses" :key="course.id" class="course-card" @click="goToCourse(course)">
          <div class="card-cover">
            <img :src="course.cover" :alt="course.title" />
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: course.progress + '%' }"></div>
            </div>
            <div class="cover-badge" v-if="course.progress === 100">
              <el-icon><Select /></el-icon>
              已完成
            </div>
          </div>

          <div class="card-body">
            <h4 class="course-title">{{ course.title }}</h4>

            <div class="course-teacher">
              <el-avatar :src="course.teacherAvatar" :size="24" />
              <span>{{ course.teacher }}</span>
            </div>

            <div class="course-progress">
              <div class="progress-info">
                <span>学习进度</span>
                <span class="progress-value">{{ course.progress }}%</span>
              </div>
              <el-progress :percentage="course.progress" :stroke-width="6" :show-text="false"
                :color="course.progress === 100 ? '#67c23a' : '#667eea'" />
            </div>

            <div class="course-meta">
              <div class="meta-item">
                <el-icon>
                  <VideoCamera />
                </el-icon>
                <span>{{ course.lessonCount }}课时</span>
              </div>
              <div class="meta-item">
                <el-icon>
                  <Clock />
                </el-icon>
                <span>{{ course.lastLearnAt }}</span>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <el-button :type="course.progress === 100 ? 'default' : 'primary'" text
              @click.stop="continueLearning(course)">
              {{ course.progress === 100 ? '复习课程' : '继续学习' }}
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="filteredCourses.length === 0" description="暂无课程" :image-size="180">
        <el-button type="primary" @click="goExplore">去探索课程</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Collection, Reading, Trophy, Clock, Search, VideoPlay, ArrowRight, Select, VideoCamera
} from '@element-plus/icons-vue'

const router = useRouter()

// ========== 模拟我的课程数据 ==========
const myCourses = ref([
  {
    id: 1,
    title: '深度学习入门：从原理到实践',
    cover: 'https://picsum.photos/seed/dl1/400/225',
    teacher: '张教授',
    teacherAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1',
    lessonCount: 48,
    progress: 75,
    lastLearnAt: '今天 14:30'
  },
  {
    id: 2,
    title: 'Vue3.0 + Pinia 企业级实战开发',
    cover: 'https://picsum.photos/seed/vue3/400/225',
    teacher: '李老师',
    teacherAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher2',
    lessonCount: 36,
    progress: 100,
    lastLearnAt: '昨天'
  },
  {
    id: 3,
    title: '自然语言处理(NLP)完全指南',
    cover: 'https://picsum.photos/seed/nlp1/400/225',
    teacher: '王博士',
    teacherAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3',
    lessonCount: 56,
    progress: 30,
    lastLearnAt: '3天前'
  },
  {
    id: 4,
    title: 'Python数据分析与可视化实战',
    cover: 'https://picsum.photos/seed/python1/400/225',
    teacher: '张教授',
    teacherAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1',
    lessonCount: 28,
    progress: 45,
    lastLearnAt: '1周前'
  },
  {
    id: 6,
    title: '机器学习算法详解与实战',
    cover: 'https://picsum.photos/seed/ml1/400/225',
    teacher: '王博士',
    teacherAvatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3',
    lessonCount: 42,
    progress: 100,
    lastLearnAt: '2周前'
  }
])

// ========== 筛选状态 ==========
const filterStatus = ref('all')
const searchKeyword = ref('')

// ========== 计算属性 ==========
const learningCount = computed(() => myCourses.value.filter(c => c.progress < 100).length)
const completedCount = computed(() => myCourses.value.filter(c => c.progress === 100).length)
const totalHours = computed(() => Math.round(myCourses.value.reduce((sum, c) => sum + c.lessonCount * 0.5 * c.progress / 100, 0)))

const lastLearnedCourse = computed(() => {
  return myCourses.value.filter(c => c.progress < 100)[0] || null
})

const filteredCourses = computed(() => {
  let result = [...myCourses.value]

  if (filterStatus.value === 'learning') {
    result = result.filter(c => c.progress < 100)
  } else if (filterStatus.value === 'completed') {
    result = result.filter(c => c.progress === 100)
  }

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(c =>
      c.title.toLowerCase().includes(keyword) ||
      c.teacher.toLowerCase().includes(keyword)
    )
  }

  return result
})

// ========== 方法 ==========
const continueLearning = (course) => {
  router.push(`/course/learn/${course.id}`)
}

const goToCourse = (course) => {
  router.push(`/course/detail/${course.id}`)
}

const goExplore = () => {
  router.push('/course/list')
}
</script>

<style lang="scss" scoped>
.my-course-page {
  min-height: 100vh;
  background: #f5f7fa;
}

// 页面头部
.page-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 48px 40px;

  .header-content {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
  }

  .header-left {
    .page-title {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 32px;
      font-weight: 700;
      color: #fff;
      margin: 0 0 8px;

      .el-icon {
        font-size: 36px;
      }
    }

    .page-subtitle {
      font-size: 16px;
      color: rgba(255, 255, 255, 0.8);
      margin: 0;
    }
  }

  .stats-cards {
    display: flex;
    gap: 20px;
  }

  .stat-card {
    background: rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(10px);
    border-radius: 16px;
    padding: 20px 28px;
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 160px;

    .stat-icon {
      width: 48px;
      height: 48px;
      background: rgba(255, 255, 255, 0.2);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;

      .el-icon {
        font-size: 24px;
        color: #fff;
      }

      &.completed {
        background: rgba(103, 194, 58, 0.3);
      }

      &.time {
        background: rgba(247, 186, 42, 0.3);
      }
    }

    .stat-info {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .stat-value {
        font-size: 28px;
        font-weight: 700;
        color: #fff;
      }

      .stat-label {
        font-size: 14px;
        color: rgba(255, 255, 255, 0.7);
      }
    }
  }
}

// 筛选栏
.filter-bar {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .filter-left :deep(.el-radio-button__inner) {
    border-radius: 20px;
    padding: 10px 20px;
  }
}

// 课程列表
.course-list-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 40px 40px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;

  h3 {
    font-size: 20px;
    font-weight: 600;
    color: #303133;
    margin: 0;
  }

  .course-count {
    font-size: 14px;
    color: #909399;
  }
}

// 继续学习卡片
.continue-learning {
  margin-bottom: 40px;
}

.continue-card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  display: flex;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);

  &:hover {
    box-shadow: 0 12px 40px rgba(102, 126, 234, 0.15);
    transform: translateY(-4px);

    .play-overlay {
      opacity: 1;
    }
  }

  .continue-cover {
    position: relative;
    width: 360px;
    flex-shrink: 0;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .play-overlay {
      position: absolute;
      inset: 0;
      background: rgba(0, 0, 0, 0.4);
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.3s ease;
    }
  }

  .continue-info {
    flex: 1;
    padding: 32px;
    display: flex;
    flex-direction: column;
    justify-content: center;

    h4 {
      font-size: 22px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 8px;
    }

    .teacher {
      font-size: 14px;
      color: #909399;
      margin: 0 0 20px;
    }

    .progress-section {
      margin-bottom: 16px;

      :deep(.el-progress) {
        margin-bottom: 8px;
      }

      .progress-text {
        font-size: 13px;
        color: #667eea;
        font-weight: 500;
      }
    }

    .last-learn {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: #909399;
    }
  }

  .continue-action {
    display: flex;
    align-items: center;
    padding: 32px;

    .el-button {
      padding: 14px 28px;
      font-size: 15px;
    }
  }
}

// 课程网格
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
}

.course-card {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);

  &:hover {
    transform: translateY(-6px);
    box-shadow: 0 16px 40px rgba(102, 126, 234, 0.12);
  }

  .card-cover {
    position: relative;
    height: 160px;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .progress-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 4px;
      background: rgba(255, 255, 255, 0.3);

      .progress-fill {
        height: 100%;
        background: #667eea;
        transition: width 0.3s ease;
      }
    }

    .cover-badge {
      position: absolute;
      top: 12px;
      right: 12px;
      background: #67c23a;
      color: #fff;
      padding: 6px 12px;
      border-radius: 20px;
      font-size: 12px;
      display: flex;
      align-items: center;
      gap: 4px;
    }
  }

  .card-body {
    padding: 20px;

    .course-title {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 12px;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .course-teacher {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
      font-size: 13px;
      color: #606266;
    }

    .course-progress {
      margin-bottom: 16px;

      .progress-info {
        display: flex;
        justify-content: space-between;
        margin-bottom: 8px;
        font-size: 13px;
        color: #909399;

        .progress-value {
          color: #667eea;
          font-weight: 500;
        }
      }
    }

    .course-meta {
      display: flex;
      gap: 20px;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: #909399;
      }
    }
  }

  .card-footer {
    padding: 0 20px 20px;
    border-top: 1px solid #f0f0f0;
    padding-top: 16px;
  }
}

// 响应式
@media (max-width: 1024px) {
  .page-header .header-content {
    flex-direction: column;
    gap: 24px;
  }

  .page-header .stats-cards {
    width: 100%;
  }

  .continue-card {
    flex-direction: column;

    .continue-cover {
      width: 100%;
      height: 200px;
    }

    .continue-action {
      padding-top: 0;
    }
  }
}

@media (max-width: 768px) {
  .page-header {
    padding: 32px 20px;

    .page-title {
      font-size: 24px;
    }

    .stats-cards {
      flex-wrap: wrap;

      .stat-card {
        flex: 1;
        min-width: 140px;
        padding: 16px;

        .stat-value {
          font-size: 22px;
        }
      }
    }
  }

  .filter-bar {
    flex-direction: column;
    gap: 16px;
    padding: 16px 20px;

    .filter-right {
      width: 100%;

      .el-input {
        width: 100% !important;
      }
    }
  }

  .course-list-section {
    padding: 0 20px 20px;
  }

  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>