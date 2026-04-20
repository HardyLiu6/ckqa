<template>
  <div class="course-detail-page">
    <!-- 课程头部信息 -->
    <div class="course-header">
      <div class="header-bg">
        <img :src="course.cover" alt="" />
        <div class="bg-overlay"></div>
      </div>

      <div class="header-content">
        <div class="content-left">
          <div class="course-cover">
            <img :src="course.cover" :alt="course.title" />
            <div class="cover-badge" v-if="course.price === 0">
              <el-icon>
                <Trophy />
              </el-icon>
              免费课程
            </div>
          </div>
        </div>

        <div class="content-right">
          <div class="course-tags">
            <el-tag type="primary">{{ course.category }}</el-tag>
            <el-tag v-for="tag in course.tags.slice(0, 3)" :key="tag" type="info">
              {{ tag }}
            </el-tag>
          </div>

          <h1 class="course-title">{{ course.title }}</h1>
          <p class="course-desc">{{ course.description }}</p>

          <div class="course-stats">
            <div class="stat-item">
              <div class="stat-value">{{ course.lessonCount }}</div>
              <div class="stat-label">课时数</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value">{{ formatDuration(course.duration) }}</div>
              <div class="stat-label">总时长</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value">{{ formatNumber(course.studentCount) }}</div>
              <div class="stat-label">学习人数</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <div class="stat-value rating">
                <el-icon>
                  <StarFilled />
                </el-icon>
                {{ course.rating.toFixed(1) }}
              </div>
              <div class="stat-label">课程评分</div>
            </div>
          </div>

          <div class="teacher-card">
            <el-avatar :src="course.teacher.avatar" :size="56" />
            <div class="teacher-info">
              <div class="teacher-name">{{ course.teacher.name }}</div>
              <div class="teacher-title">{{ course.teacher.title }}</div>
            </div>
          </div>

          <div class="action-buttons">
            <el-button type="primary" size="large" @click="startLearning">
              <el-icon>
                <VideoPlay />
              </el-icon>
              {{ isEnrolled ? '继续学习' : '立即学习' }}
            </el-button>
            <el-button size="large" @click="handleCollect">
              <el-icon>
                <Star />
              </el-icon>
              收藏课程
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 课程内容区域 -->
    <div class="course-body">
      <div class="body-container">
        <!-- 左侧主内容 -->
        <div class="main-content">
          <el-tabs v-model="activeTab" class="content-tabs">
            <!-- 课程目录 -->
            <el-tab-pane label="课程目录" name="chapters">
              <div class="chapters-list">
                <el-collapse v-model="expandedChapters">
                  <el-collapse-item v-for="chapter in chapters" :key="chapter.id" :name="chapter.id">
                    <template #title>
                      <div class="chapter-header">
                        <span class="chapter-title">第{{ chapter.order }}章 · {{ chapter.title }}</span>
                        <span class="chapter-meta">{{ chapter.lessons.length }}课时</span>
                      </div>
                    </template>

                    <div class="lessons-list">
                      <div v-for="lesson in chapter.lessons" :key="lesson.id" class="lesson-item"
                        @click="handleLessonClick(lesson)">
                        <div class="lesson-icon">
                          <el-icon v-if="lesson.type === 'video'">
                            <VideoPlay />
                          </el-icon>
                          <el-icon v-else-if="lesson.type === 'document'">
                            <Document />
                          </el-icon>
                          <el-icon v-else>
                            <EditPen />
                          </el-icon>
                        </div>
                        <div class="lesson-info">
                          <span class="lesson-title">{{ lesson.title }}</span>
                          <span class="lesson-duration">{{ lesson.duration }}分钟</span>
                        </div>
                        <div class="lesson-status">
                          <el-tag v-if="lesson.isFree" size="small" type="success">试学</el-tag>
                          <el-icon v-if="lesson.isCompleted" class="completed-icon">
                            <CircleCheck />
                          </el-icon>
                          <el-icon v-else-if="!lesson.isFree" class="lock-icon">
                            <Lock />
                          </el-icon>
                        </div>
                      </div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </el-tab-pane>

            <!-- 课程介绍 -->
            <el-tab-pane label="课程介绍" name="intro">
              <div class="course-intro">
                <div class="intro-section">
                  <h3>课程简介</h3>
                  <p>{{ course.fullDescription }}</p>
                </div>

                <div class="intro-section">
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

                <div class="intro-section">
                  <h3>适合人群</h3>
                  <div class="audience-tags">
                    <el-tag v-for="(item, index) in course.audience" :key="index" size="large">
                      {{ item }}
                    </el-tag>
                  </div>
                </div>
              </div>
            </el-tab-pane>

            <!-- 学员评价 -->
            <el-tab-pane label="学员评价" name="reviews">
              <div class="reviews-section">
                <div class="rating-summary">
                  <div class="rating-score">
                    <span class="score">{{ course.rating.toFixed(1) }}</span>
                    <el-rate :model-value="course.rating" disabled />
                    <span class="review-count">{{ formatNumber(course.studentCount) }}条评价</span>
                  </div>
                </div>

                <div class="review-list">
                  <div class="review-item" v-for="review in reviews" :key="review.id">
                    <div class="review-header">
                      <el-avatar :src="review.avatar" :size="40" />
                      <div class="reviewer-info">
                        <span class="reviewer-name">{{ review.name }}</span>
                        <el-rate :model-value="review.rating" disabled size="small" />
                      </div>
                      <span class="review-date">{{ review.date }}</span>
                    </div>
                    <p class="review-content">{{ review.content }}</p>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <!-- 右侧边栏 -->
        <div class="sidebar">
          <div class="sidebar-card teacher-sidebar">
            <h4>关于讲师</h4>
            <div class="teacher-detail">
              <el-avatar :src="course.teacher.avatar" :size="80" />
              <div class="teacher-name">{{ course.teacher.name }}</div>
              <div class="teacher-title">{{ course.teacher.title }}</div>
              <p class="teacher-desc">{{ course.teacher.description }}</p>
            </div>
          </div>

          <div class="sidebar-card">
            <h4>相关推荐</h4>
            <div class="recommend-list">
              <div class="recommend-item" v-for="item in recommendCourses" :key="item.id" @click="goToDetail(item.id)">
                <div class="recommend-cover">
                  <img :src="item.cover" alt="" />
                </div>
                <div class="recommend-info">
                  <span class="recommend-title">{{ item.title }}</span>
                  <span class="recommend-price">{{ item.price === 0 ? '免费' : '¥' + item.price }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Trophy, StarFilled, VideoPlay, Star, Document, EditPen, CircleCheck, Lock
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// ========== 模拟课程数据 ==========
const courseId = computed(() => Number(route.params.id))

const course = ref({
  id: 1,
  title: '深度学习入门：从原理到实践',
  cover: 'https://picsum.photos/seed/dl1/800/450',
  description: '本课程从零开始讲解深度学习的核心概念，适合AI入门学习者。',
  fullDescription: '本课程从零开始讲解深度学习的核心概念，包括神经网络基础、反向传播算法、常见网络架构（CNN、RNN、Transformer）等。通过大量实战项目帮助学员掌握深度学习的实际应用，包括图像分类、目标检测、自然语言处理等领域。课程采用Python和PyTorch框架，循序渐进，由浅入深。',
  teacher: {
    id: 1,
    name: '张教授',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1',
    title: '人工智能专家',
    description: '清华大学博士，10年AI研究经验，发表顶会论文50余篇，曾任职于知名科技公司AI实验室，专注于深度学习和计算机视觉研究。'
  },
  category: '人工智能',
  tags: ['深度学习', '神经网络', 'Python', 'PyTorch', 'CNN'],
  duration: 1200,
  lessonCount: 48,
  studentCount: 12580,
  rating: 4.9,
  price: 0,
  objectives: [
    '理解深度学习的基本原理和数学基础',
    '掌握PyTorch框架的使用方法',
    '能够独立构建和训练神经网络模型',
    '完成图像分类、目标检测等实战项目'
  ],
  audience: [
    '对AI感兴趣的初学者',
    '想转行AI领域的开发者',
    '需要提升技能的数据分析师',
    '计算机相关专业学生'
  ]
})

// ========== 模拟章节数据 ==========
const chapters = ref([
  {
    id: 1,
    order: 1,
    title: '深度学习概述',
    lessons: [
      { id: 1, title: '什么是深度学习', type: 'video', duration: 15, isFree: true, isCompleted: false },
      { id: 2, title: '深度学习的发展历程', type: 'video', duration: 20, isFree: true, isCompleted: false },
      { id: 3, title: '深度学习的应用场景', type: 'video', duration: 18, isFree: false, isCompleted: false },
      { id: 4, title: '章节测验', type: 'quiz', duration: 10, isFree: false, isCompleted: false }
    ]
  },
  {
    id: 2,
    order: 2,
    title: '神经网络基础',
    lessons: [
      { id: 5, title: '感知机模型', type: 'video', duration: 25, isFree: false, isCompleted: false },
      { id: 6, title: '多层感知机', type: 'video', duration: 30, isFree: false, isCompleted: false },
      { id: 7, title: '激活函数详解', type: 'video', duration: 22, isFree: false, isCompleted: false },
      { id: 8, title: '实战：手写数字识别', type: 'video', duration: 35, isFree: false, isCompleted: false }
    ]
  },
  {
    id: 3,
    order: 3,
    title: '反向传播算法',
    lessons: [
      { id: 9, title: '梯度下降法', type: 'video', duration: 28, isFree: false, isCompleted: false },
      { id: 10, title: '反向传播原理', type: 'video', duration: 35, isFree: false, isCompleted: false },
      { id: 11, title: '优化器详解', type: 'video', duration: 30, isFree: false, isCompleted: false },
      { id: 12, title: '补充材料', type: 'document', duration: 15, isFree: false, isCompleted: false }
    ]
  },
  {
    id: 4,
    order: 4,
    title: '卷积神经网络CNN',
    lessons: [
      { id: 13, title: '卷积操作原理', type: 'video', duration: 32, isFree: false, isCompleted: false },
      { id: 14, title: '池化层详解', type: 'video', duration: 20, isFree: false, isCompleted: false },
      { id: 15, title: '经典CNN架构', type: 'video', duration: 40, isFree: false, isCompleted: false },
      { id: 16, title: '实战：图像分类', type: 'video', duration: 45, isFree: false, isCompleted: false }
    ]
  }
])

// ========== 模拟评价数据 ==========
const reviews = ref([
  {
    id: 1,
    name: '学员小明',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user1',
    rating: 5,
    date: '2024-03-15',
    content: '课程内容非常详细，张教授讲解清晰易懂，从基础到进阶循序渐进，对入门深度学习帮助很大！'
  },
  {
    id: 2,
    name: '学员小红',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user2',
    rating: 5,
    date: '2024-03-10',
    content: '实战项目设计得很好，跟着做完后对神经网络有了深入理解，强烈推荐！'
  },
  {
    id: 3,
    name: '学员小李',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=user3',
    rating: 4,
    date: '2024-03-05',
    content: '整体不错，就是有些数学公式推导希望能讲得更详细一些。'
  }
])

// ========== 推荐课程 ==========
const recommendCourses = ref([
  { id: 3, title: '自然语言处理完全指南', cover: 'https://picsum.photos/seed/nlp1/200/120', price: 299 },
  { id: 6, title: '机器学习算法详解', cover: 'https://picsum.photos/seed/ml1/200/120', price: 199 },
  { id: 8, title: '智能问答系统设计', cover: 'https://picsum.photos/seed/qa1/200/120', price: 399 }
])

// ========== 状态 ==========
const activeTab = ref('chapters')
const expandedChapters = ref([1])
const isEnrolled = ref(false)

// 格式化时长
const formatDuration = (minutes) => {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return hours > 0 ? `${hours}小时${mins}分` : `${mins}分钟`
}

// 格式化数字
const formatNumber = (num) => {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  return num.toString()
}

// 收藏课程
const handleCollect = () => {
  ElMessage.success('收藏成功！')
}

// 开始学习
const startLearning = () => {
  router.push(`/course/learn/${courseId.value}`)
}

// 点击课时
const handleLessonClick = (lesson) => {
  if (!lesson.isFree) {
    ElMessage.info('请先加入课程')
    return
  }
  router.push(`/course/learn/${courseId.value}?lesson=${lesson.id}`)
}

// 跳转详情
const goToDetail = (id) => {
  router.push(`/course/detail/${id}`)
}
</script>

<style lang="scss" scoped>
.course-detail-page {
  min-height: 100vh;
  background: #f5f7fa;
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
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.95) 0%, rgba(118, 75, 162, 0.95) 100%);
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
      width: 420px;
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
        background: linear-gradient(135deg, #f7ba2a 0%, #f56c6c 100%);
        color: #fff;
        padding: 10px 20px;
        border-radius: 24px;
        font-size: 14px;
        font-weight: 600;
        display: flex;
        align-items: center;
        gap: 6px;
      }
    }
  }

  .content-right {
    flex: 1;
    color: #fff;

    .course-tags {
      display: flex;
      gap: 10px;
      margin-bottom: 20px;

      .el-tag {
        background: rgba(255, 255, 255, 0.2);
        border: none;
        color: #fff;
        padding: 8px 16px;
        border-radius: 20px;
      }
    }

    .course-title {
      font-size: 34px;
      font-weight: 700;
      margin: 0 0 16px;
      line-height: 1.4;
    }

    .course-desc {
      font-size: 16px;
      line-height: 1.8;
      opacity: 0.9;
      margin: 0 0 28px;
    }
  }

  .course-stats {
    display: flex;
    align-items: center;
    gap: 36px;
    margin-bottom: 28px;
    padding: 24px 28px;
    background: rgba(255, 255, 255, 0.12);
    border-radius: 16px;
    backdrop-filter: blur(10px);

    .stat-item {
      text-align: center;

      .stat-value {
        font-size: 26px;
        font-weight: 700;
        margin-bottom: 6px;

        &.rating {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 6px;
          color: #f7ba2a;
        }
      }

      .stat-label {
        font-size: 13px;
        opacity: 0.8;
      }
    }

    .stat-divider {
      width: 1px;
      height: 44px;
      background: rgba(255, 255, 255, 0.25);
    }
  }

  .teacher-card {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 28px;

    .teacher-info {
      .teacher-name {
        font-size: 18px;
        font-weight: 600;
        margin-bottom: 4px;
      }

      .teacher-title {
        font-size: 14px;
        opacity: 0.8;
      }
    }
  }

  .action-buttons {
    display: flex;
    gap: 16px;

    .el-button--large {
      padding: 18px 36px;
      font-size: 16px;
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
    gap: 32px;
  }

  .main-content {
    flex: 1;
    min-width: 0;
  }

  .content-tabs {
    background: #fff;
    border-radius: 16px;
    padding: 28px;
    box-shadow: 0 4px 24px rgba(0, 0, 0, 0.05);

    :deep(.el-tabs__header) {
      margin-bottom: 28px;
    }

    :deep(.el-tabs__item) {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .sidebar {
    width: 320px;
    flex-shrink: 0;
  }
}

// 章节列表
.chapters-list {
  :deep(.el-collapse) {
    border: none;
  }

  :deep(.el-collapse-item__header) {
    height: auto;
    padding: 18px 0;
    font-size: 16px;
    border: none;
  }

  :deep(.el-collapse-item__wrap) {
    border: none;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 0;
  }

  .chapter-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding-right: 16px;

    .chapter-title {
      font-weight: 600;
      color: #303133;
    }

    .chapter-meta {
      font-size: 13px;
      color: #909399;
    }
  }
}

.lessons-list {
  .lesson-item {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px 18px;
    margin-bottom: 10px;
    background: #f8f9fc;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background: #e8f4ff;
      transform: translateX(4px);
    }

    .lesson-icon {
      width: 40px;
      height: 40px;
      background: #fff;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #667eea;
      font-size: 18px;
    }

    .lesson-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;

      .lesson-title {
        font-size: 14px;
        color: #303133;
      }

      .lesson-duration {
        font-size: 12px;
        color: #909399;
      }
    }

    .lesson-status {
      display: flex;
      align-items: center;
      gap: 8px;

      .completed-icon {
        color: #67c23a;
        font-size: 20px;
      }

      .lock-icon {
        color: #c0c4cc;
        font-size: 18px;
      }
    }
  }
}

// 课程介绍
.course-intro {
  .intro-section {
    margin-bottom: 36px;

    h3 {
      font-size: 18px;
      font-weight: 600;
      color: #303133;
      margin: 0 0 18px;
      padding-left: 14px;
      border-left: 4px solid #667eea;
    }

    p {
      font-size: 15px;
      line-height: 1.9;
      color: #606266;
    }
  }

  .learn-list {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;

    .learn-item {
      display: flex;
      align-items: center;
      gap: 10px;
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
    gap: 12px;
  }
}

// 评价区域
.reviews-section {
  .rating-summary {
    text-align: center;
    padding: 28px;
    background: #f8f9fc;
    border-radius: 16px;
    margin-bottom: 28px;

    .score {
      font-size: 52px;
      font-weight: 700;
      color: #f7ba2a;
      margin-right: 12px;
    }

    .review-count {
      display: block;
      font-size: 14px;
      color: #909399;
      margin-top: 10px;
    }
  }

  .review-item {
    padding: 24px 0;
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .review-header {
      display: flex;
      align-items: center;
      gap: 14px;
      margin-bottom: 14px;

      .reviewer-info {
        flex: 1;

        .reviewer-name {
          display: block;
          font-weight: 500;
          margin-bottom: 4px;
        }
      }

      .review-date {
        font-size: 13px;
        color: #909399;
      }
    }

    .review-content {
      font-size: 14px;
      line-height: 1.8;
      color: #606266;
      margin: 0;
    }
  }
}

// 侧边栏
.sidebar-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);

  h4 {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 20px;
    padding-bottom: 14px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.teacher-sidebar {
  .teacher-detail {
    text-align: center;

    .el-avatar {
      margin-bottom: 14px;
    }

    .teacher-name {
      font-size: 18px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 6px;
    }

    .teacher-title {
      font-size: 14px;
      color: #909399;
      margin-bottom: 14px;
    }

    .teacher-desc {
      font-size: 14px;
      line-height: 1.7;
      color: #606266;
      margin: 0;
      text-align: left;
    }
  }
}

.recommend-list {
  .recommend-item {
    display: flex;
    gap: 14px;
    padding: 14px 0;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      transform: translateX(4px);
    }

    &:not(:last-child) {
      border-bottom: 1px solid #f0f0f0;
    }

    .recommend-cover {
      width: 90px;
      height: 55px;
      border-radius: 8px;
      overflow: hidden;
      flex-shrink: 0;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }

    .recommend-info {
      display: flex;
      flex-direction: column;
      justify-content: center;
      gap: 6px;

      .recommend-title {
        font-size: 14px;
        color: #303133;
        line-height: 1.4;
      }

      .recommend-price {
        font-size: 13px;
        color: #67c23a;
        font-weight: 500;
      }
    }
  }
}

// 响应式
@media (max-width: 1024px) {
  .course-header .header-content {
    flex-direction: column;
    padding: 0 20px;
  }

  .course-header .content-left .course-cover {
    width: 100%;
    max-width: 420px;
    margin: 0 auto;
  }

  .course-header .course-stats {
    flex-wrap: wrap;
    justify-content: center;
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