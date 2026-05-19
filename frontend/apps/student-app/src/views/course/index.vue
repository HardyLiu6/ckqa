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
          <span class="stat-num">{{ courseStore.allCourses.length }}</span>
          <span class="stat-label">可见课程</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ courseStore.myCourses.length }}</span>
          <span class="stat-label">我已加入</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ courseStore.allCategories.length }}</span>
          <span class="stat-label">课程分类</span>
        </div>
      </div>
    </div>

    <!-- 搜索和筛选区域 -->
    <div class="filter-section">
      <div class="filter-container">
        <!-- 搜索框 -->
        <div class="search-box">
          <el-input v-model="searchKeyword" placeholder="搜索课程名称、关键词..." size="large" clearable
            @keyup.enter="onSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
            <template #append>
              <el-button type="primary" @click="onSearch">搜索</el-button>
            </template>
          </el-input>
        </div>

        <!-- 分类筛选 -->
        <div class="category-filter" v-if="allCategories.length > 1">
          <span class="filter-label">课程分类：</span>
          <div class="category-tags">
            <el-tag v-for="cat in allCategories" :key="cat" :type="selectedCategory === cat ? '' : 'info'"
              :effect="selectedCategory === cat ? 'dark' : 'plain'" class="category-tag"
              @click="selectedCategory = cat">
              {{ cat }}
            </el-tag>
          </div>
        </div>

        <!-- 我加入的 / 全部可见 -->
        <div class="sort-filter">
          <span class="filter-label">范围：</span>
          <el-radio-group v-model="scope">
            <el-radio-button value="all">全部可见</el-radio-button>
            <el-radio-button value="member">我加入的</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </div>

    <!-- 课程列表 -->
    <div class="course-section">
      <div v-if="courseStore.isLoading" class="course-loading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="courseStore.errorMessage" class="course-error">
        <el-empty :description="courseStore.errorMessage" :image-size="160">
          <el-button type="primary" @click="reload">重试</el-button>
        </el-empty>
      </div>
      <div v-else class="course-grid">
        <div v-for="course in filteredCourses" :key="course.id" class="course-card" @click="goToDetail(course.id)">
          <!-- 课程封面 -->
          <div class="card-cover">
            <img :src="course.cover || defaultCover" :alt="course.title" />
            <div class="cover-overlay">
              <el-button type="primary" circle size="large">
                <el-icon size="24">
                  <VideoPlay />
                </el-icon>
              </el-button>
            </div>
            <div class="card-tags">
              <el-tag v-if="course.memberStatus === 'member'" type="success" size="small">已加入</el-tag>
              <el-tag v-else-if="course.accessPolicy === 'public'" type="info" size="small">公开</el-tag>
            </div>
            <div v-if="course.category" class="card-category">{{ course.category }}</div>
          </div>

          <!-- 课程信息 -->
          <div class="card-content">
            <h3 class="course-title">{{ course.title || course.id }}</h3>
            <p class="course-desc">{{ course.description || '暂无简介' }}</p>

            <!-- 讲师信息 -->
            <div v-if="course.teacher && course.teacher.name" class="teacher-info">
              <el-avatar :size="32">
                {{ course.teacher.name.slice(0, 1) }}
              </el-avatar>
              <div class="teacher-text">
                <span class="teacher-name">{{ course.teacher.name }}</span>
                <span class="teacher-title">
                  {{ course.teachers.length > 1 ? `等 ${course.teachers.length} 位教师` : '授课教师' }}
                </span>
              </div>
            </div>

            <!-- 课程数据 -->
            <div class="course-meta">
              <div class="meta-item">
                <el-icon>
                  <Document />
                </el-icon>
                <span>{{ course.materialCount }} 份资料</span>
              </div>
              <div class="meta-item">
                <el-icon>
                  <Connection />
                </el-icon>
                <span>{{ course.activeKnowledgeBaseCount }} 个知识库</span>
              </div>
              <div v-if="course.estimatedHours" class="meta-item">
                <el-icon>
                  <Clock />
                </el-icon>
                <span>约 {{ course.estimatedHours }} 学时</span>
              </div>
              <div v-if="course.difficulty" class="meta-item">
                <el-icon>
                  <Star />
                </el-icon>
                <span>{{ formatDifficulty(course.difficulty) }}</span>
              </div>
            </div>

            <!-- 标签 -->
            <div v-if="course.tags.length" class="course-tags">
              <el-tag v-for="tag in course.tags.slice(0, 4)" :key="tag" size="small" type="info" effect="plain">
                {{ tag }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="!courseStore.isLoading && !courseStore.errorMessage && filteredCourses.length === 0"
        description="暂无相关课程" :image-size="200">
        <el-button type="primary" @click="resetFilters">重置筛选</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Reading, Search, VideoPlay, Document, Connection, Clock, Star,
} from '@element-plus/icons-vue'

import { useCourseStore } from '@/stores/course'

const router = useRouter()
const courseStore = useCourseStore()

const defaultCover = '/api/v1/course-covers/default-course-cover.svg'

const searchKeyword = ref('')
const selectedCategory = ref('全部')
const scope = ref('all')

const allCategories = computed(() => ['全部', ...courseStore.allCategories])

const filteredCourses = computed(() => {
  let result = [...courseStore.allCourses]
  if (scope.value === 'member') {
    result = result.filter((c) => c.memberStatus === 'member')
  }
  if (selectedCategory.value && selectedCategory.value !== '全部') {
    result = result.filter((c) => c.category === selectedCategory.value)
  }
  return result.sort((a, b) => {
    const aTime = a.updatedAt ? new Date(a.updatedAt).getTime() : 0
    const bTime = b.updatedAt ? new Date(b.updatedAt).getTime() : 0
    return bTime - aTime
  })
})

function formatDifficulty(value) {
  if (value === 'beginner') return '入门'
  if (value === 'intermediate') return '进阶'
  if (value === 'advanced') return '高级'
  return value
}

async function reload() {
  await courseStore.loadCourses({
    keyword: searchKeyword.value || undefined,
    force: true,
  })
}

function onSearch() {
  reload()
}

function resetFilters() {
  searchKeyword.value = ''
  selectedCategory.value = '全部'
  scope.value = 'all'
  reload()
}

function goToDetail(id) {
  router.push(`/course/detail/${encodeURIComponent(id)}`)
}

onMounted(() => {
  if (courseStore.allCourses.length === 0) {
    courseStore.loadCourses()
  }
})
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