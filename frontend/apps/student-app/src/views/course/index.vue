<template>
  <div class="course-list-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <el-icon>
            <Reading />
          </el-icon>
          课程中心
        </h1>
        <p class="page-subtitle">探索知识的海洋，开启智能学习之旅</p>
      </div>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-num">{{ courses.length }}+</span>
          <span class="stat-label">精品课程</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">50k+</span>
          <span class="stat-label">学习人次</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">98%</span>
          <span class="stat-label">好评率</span>
        </div>
      </div>
    </div>

    <!-- 搜索和筛选区域 -->
    <div class="filter-section">
      <div class="filter-container">
        <!-- 搜索框 -->
        <div class="search-box">
          <el-input v-model="searchKeyword" placeholder="搜索课程名称、讲师、关键词..." size="large" clearable>
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
            <template #append>
              <el-button type="primary">搜索</el-button>
            </template>
          </el-input>
        </div>

        <!-- 分类筛选 -->
        <div class="category-filter">
          <span class="filter-label">课程分类：</span>
          <div class="category-tags">
            <el-tag v-for="cat in allCategories" :key="cat" :type="selectedCategory === cat ? '' : 'info'"
              :effect="selectedCategory === cat ? 'dark' : 'plain'" class="category-tag"
              @click="selectedCategory = cat">
              {{ cat }}
            </el-tag>
          </div>
        </div>

        <!-- 排序 -->
        <div class="sort-filter">
          <span class="filter-label">排序方式：</span>
          <el-radio-group v-model="sortBy">
            <el-radio-button value="newest">最新发布</el-radio-button>
            <el-radio-button value="popular">最受欢迎</el-radio-button>
            <el-radio-button value="rating">评分最高</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <!-- 课程列表 -->
    <div class="course-section">
      <div class="course-grid">
        <div v-for="course in filteredCourses" :key="course.id" class="course-card" @click="goToDetail(course.id)">
          <!-- 课程封面 -->
          <div class="card-cover">
            <img :src="course.cover" :alt="course.title" />
            <div class="cover-overlay">
              <el-button type="primary" circle size="large">
                <el-icon size="24">
                  <VideoPlay />
                </el-icon>
              </el-button>
            </div>
            <div class="card-tags">
              <el-tag v-if="course.price === 0" type="success" size="small">免费</el-tag>
              <el-tag v-else type="warning" size="small">¥{{ course.price }}</el-tag>
            </div>
            <div class="card-category">{{ course.category }}</div>
          </div>

          <!-- 课程信息 -->
          <div class="card-content">
            <h3 class="course-title">{{ course.title }}</h3>
            <p class="course-desc">{{ course.description }}</p>

            <!-- 讲师信息 -->
            <div class="teacher-info">
              <el-avatar :src="course.teacher.avatar" :size="32" />
              <div class="teacher-text">
                <span class="teacher-name">{{ course.teacher.name }}</span>
                <span class="teacher-title">{{ course.teacher.title }}</span>
              </div>
            </div>

            <!-- 课程数据 -->
            <div class="course-meta">
              <div class="meta-item">
                <el-icon>
                  <User />
                </el-icon>
                <span>{{ formatNumber(course.studentCount) }}人学习</span>
              </div>
              <div class="meta-item">
                <el-icon>
                  <Clock />
                </el-icon>
                <span>{{ course.lessonCount }}课时</span>
              </div>
              <div class="meta-item rating">
                <el-icon>
                  <Star />
                </el-icon>
                <span>{{ course.rating.toFixed(1) }}</span>
              </div>
            </div>

            <!-- 标签 -->
            <div class="course-tags">
              <el-tag v-for="tag in course.tags.slice(0, 3)" :key="tag" size="small" type="info" effect="plain">
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="filteredCourses.length === 0" description="暂无相关课程" :image-size="200">
        <el-button type="primary" @click="resetFilters">重置筛选</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Reading, Search, VideoPlay, User, Clock, Star
} from '@element-plus/icons-vue'

const router = useRouter()

// ========== 模拟数据 ==========
const courses = ref([
  {
    id: 1,
    title: '深度学习入门：从原理到实践',
    cover: 'https://picsum.photos/seed/dl1/400/225',
    description: '本课程从零开始讲解深度学习的核心概念，包括神经网络基础、反向传播算法、常见网络架构等。',
    teacher: { id: 1, name: '张教授', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1', title: '人工智能专家' },
    category: '人工智能',
    tags: ['深度学习', '神经网络', 'Python', 'TensorFlow'],
    lessonCount: 48,
    studentCount: 12580,
    rating: 4.9,
    price: 0
  },
  {
    id: 2,
    title: 'Vue3.0 + Pinia 企业级实战开发',
    cover: 'https://picsum.photos/seed/vue3/400/225',
    description: '从Vue3新特性到Pinia状态管理，手把手带你构建企业级中后台管理系统。',
    teacher: { id: 2, name: '李老师', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher2', title: '前端架构师' },
    category: '前端开发',
    tags: ['Vue3', 'Pinia', 'Vite', 'Element Plus'],
    lessonCount: 36,
    studentCount: 8920,
    rating: 4.8,
    price: 199
  },
  {
    id: 3,
    title: '自然语言处理(NLP)完全指南',
    cover: 'https://picsum.photos/seed/nlp1/400/225',
    description: '系统学习NLP核心技术，从词向量、序列模型到Transformer和大语言模型。',
    teacher: { id: 3, name: '王博士', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3', title: '机器学习专家' },
    category: '人工智能',
    tags: ['NLP', 'Transformer', 'BERT', 'GPT'],
    lessonCount: 56,
    studentCount: 6750,
    rating: 4.9,
    price: 299
  },
  {
    id: 4,
    title: 'Python数据分析与可视化实战',
    cover: 'https://picsum.photos/seed/python1/400/225',
    description: '使用Python进行数据处理、分析和可视化，掌握Pandas、NumPy、Matplotlib等核心库。',
    teacher: { id: 1, name: '张教授', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher1', title: '人工智能专家' },
    category: '数据科学',
    tags: ['Python', 'Pandas', '数据分析', 'Matplotlib'],
    lessonCount: 28,
    studentCount: 15680,
    rating: 4.7,
    price: 0
  },
  {
    id: 5,
    title: 'React18 + Next.js 全栈开发',
    cover: 'https://picsum.photos/seed/react1/400/225',
    description: '掌握React18新特性和Next.js全栈开发框架，构建高性能的服务端渲染应用。',
    teacher: { id: 2, name: '李老师', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher2', title: '前端架构师' },
    category: '前端开发',
    tags: ['React', 'Next.js', 'SSR', 'TypeScript'],
    lessonCount: 32,
    studentCount: 5420,
    rating: 4.8,
    price: 249
  },
  {
    id: 6,
    title: '机器学习算法详解与实战',
    cover: 'https://picsum.photos/seed/ml1/400/225',
    description: '从基础到进阶，系统讲解机器学习核心算法，配合Sklearn实战项目。',
    teacher: { id: 3, name: '王博士', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3', title: '机器学习专家' },
    category: '人工智能',
    tags: ['机器学习', 'Sklearn', '算法', '实战'],
    lessonCount: 42,
    studentCount: 9870,
    rating: 4.9,
    price: 199
  },
  {
    id: 7,
    title: 'Node.js 后端开发实战',
    cover: 'https://picsum.photos/seed/node1/400/225',
    description: '从零构建企业级Node.js应用，掌握Express、Koa、数据库设计等核心技能。',
    teacher: { id: 2, name: '李老师', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher2', title: '前端架构师' },
    category: '后端开发',
    tags: ['Node.js', 'Express', 'MongoDB', 'RESTful'],
    lessonCount: 35,
    studentCount: 7230,
    rating: 4.6,
    price: 179
  },
  {
    id: 8,
    title: '智能问答系统设计与实现',
    cover: 'https://picsum.photos/seed/qa1/400/225',
    description: '结合NLP技术和知识图谱，构建企业级智能问答系统，支持多轮对话。',
    teacher: { id: 3, name: '王博士', avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=teacher3', title: '机器学习专家' },
    category: '人工智能',
    tags: ['问答系统', '知识图谱', 'NLP', 'ChatBot'],
    lessonCount: 52,
    studentCount: 4560,
    rating: 4.8,
    price: 399
  }
])

// ========== 筛选状态 ==========
const searchKeyword = ref('')
const selectedCategory = ref('全部')
const sortBy = ref('newest')

// 分类列表
const allCategories = computed(() => {
  const cats = [...new Set(courses.value.map(c => c.category))]
  return ['全部', ...cats]
})

// 过滤后的课程
const filteredCourses = computed(() => {
  let result = [...courses.value]

  // 关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(c =>
      c.title.toLowerCase().includes(keyword) ||
      c.teacher.name.toLowerCase().includes(keyword) ||
      c.tags.some(t => t.toLowerCase().includes(keyword))
    )
  }

  // 分类筛选
  if (selectedCategory.value !== '全部') {
    result = result.filter(c => c.category === selectedCategory.value)
  }

  // 排序
  if (sortBy.value === 'popular') {
    result.sort((a, b) => b.studentCount - a.studentCount)
  } else if (sortBy.value === 'rating') {
    result.sort((a, b) => b.rating - a.rating)
  }

  return result
})

// 格式化数字
const formatNumber = (num) => {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  return num.toString()
}

// 重置筛选
const resetFilters = () => {
  searchKeyword.value = ''
  selectedCategory.value = '全部'
  sortBy.value = 'newest'
}

// 跳转详情
const goToDetail = (id) => {
  router.push(`/course/detail/${id}`)
}
</script>

<style lang="scss" scoped>
.course-list-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f7ff 0%, #ffffff 100%);
}

// 页面头部
.page-header {
  position: relative;
  padding: 60px 40px 100px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: -50%;
    right: -10%;
    width: 600px;
    height: 600px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
    border-radius: 50%;
  }

  .header-content {
    position: relative;
    z-index: 2;
    text-align: center;
    margin-bottom: 40px;
  }

  .page-title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    font-size: 42px;
    font-weight: 700;
    color: #fff;
    margin: 0 0 16px;

    .el-icon {
      font-size: 48px;
    }
  }

  .page-subtitle {
    font-size: 18px;
    color: rgba(255, 255, 255, 0.9);
    margin: 0;
    letter-spacing: 2px;
  }

  .header-stats {
    position: relative;
    z-index: 2;
    display: flex;
    justify-content: center;
    gap: 60px;

    .stat-item {
      text-align: center;

      .stat-num {
        display: block;
        font-size: 36px;
        font-weight: 700;
        color: #fff;
        margin-bottom: 8px;
      }

      .stat-label {
        font-size: 14px;
        color: rgba(255, 255, 255, 0.8);
      }
    }
  }
}

// 筛选区域
.filter-section {
  margin-top: -50px;
  padding: 0 40px;
  position: relative;
  z-index: 10;

  .filter-container {
    max-width: 1200px;
    margin: 0 auto;
    background: #fff;
    border-radius: 20px;
    padding: 32px;
    box-shadow: 0 10px 60px rgba(102, 126, 234, 0.15);
  }

  .search-box {
    margin-bottom: 28px;

    :deep(.el-input) {
      max-width: 700px;

      .el-input__wrapper {
        border-radius: 12px;
        padding: 4px 16px;
      }

      .el-input-group__append .el-button {
        border-radius: 0 12px 12px 0;
        padding: 0 24px;
      }
    }
  }

  .category-filter,
  .sort-filter {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .filter-label {
    font-size: 14px;
    font-weight: 500;
    color: #606266;
    white-space: nowrap;
  }

  .category-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .category-tag {
    cursor: pointer;
    transition: all 0.3s ease;
    border-radius: 20px;
    padding: 8px 20px;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
    }
  }
}

// 课程列表
.course-section {
  max-width: 1400px;
  margin: 0 auto;
  padding: 50px 40px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 28px;
}

// 课程卡片
.course-card {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.06);

  &:hover {
    transform: translateY(-10px);
    box-shadow: 0 24px 50px rgba(102, 126, 234, 0.18);

    .card-cover img {
      transform: scale(1.08);
    }

    .cover-overlay {
      opacity: 1;
    }
  }

  .card-cover {
    position: relative;
    height: 190px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.6s ease;
    }

    .cover-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.85), rgba(118, 75, 162, 0.85));
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.4s ease;
    }

    .card-tags {
      position: absolute;
      top: 16px;
      left: 16px;

      .el-tag {
        font-weight: 600;
        padding: 6px 14px;
        border-radius: 20px;
      }
    }

    .card-category {
      position: absolute;
      top: 16px;
      right: 16px;
      background: rgba(0, 0, 0, 0.6);
      color: #fff;
      padding: 6px 14px;
      border-radius: 20px;
      font-size: 12px;
    }
  }

  .card-content {
    padding: 24px;
  }

  .course-title {
    font-size: 18px;
    font-weight: 600;
    color: #1a1a2e;
    margin: 0 0 10px;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    min-height: 54px;
  }

  .course-desc {
    font-size: 14px;
    color: #8c8c8c;
    margin: 0 0 18px;
    line-height: 1.7;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .teacher-info {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 18px;
    padding-bottom: 18px;
    border-bottom: 1px solid #f5f5f5;

    .teacher-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .teacher-name {
      font-size: 14px;
      color: #303133;
      font-weight: 500;
    }

    .teacher-title {
      font-size: 12px;
      color: #909399;
    }
  }

  .course-meta {
    display: flex;
    gap: 20px;
    margin-bottom: 16px;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 13px;
      color: #909399;

      &.rating {
        color: #f7ba2a;
        font-weight: 600;
      }
    }
  }

  .course-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .el-tag {
      border-radius: 6px;
      font-size: 12px;
    }
  }
}

// 响应式
@media (max-width: 768px) {
  .page-header {
    padding: 40px 20px 80px;

    .page-title {
      font-size: 28px;
    }

    .header-stats {
      gap: 30px;

      .stat-num {
        font-size: 28px;
      }
    }
  }

  .filter-section {
    padding: 0 16px;
    margin-top: -40px;

    .filter-container {
      padding: 24px 20px;
    }

    .category-filter,
    .sort-filter {
      flex-direction: column;
      align-items: flex-start;
    }
  }

  .course-section {
    padding: 30px 16px;
  }

  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>