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
$course-primary: #2563eb;
$course-light: #3b82f6;
$course-50: #eff6ff;
$text: #0f172a;
$text-secondary: #475569;
$text-muted: #94a3b8;
$border: rgba(226, 232, 240, 0.9);
$bg: #f8fafc;
$bg-card: #fff;
$radius: 16px;

.course-list-page {
  min-height: 100vh;
  background: $bg;
}

// 页面头部 · 轻量白底
.page-header {
  position: relative;
  padding: 48px 40px 80px;
  background:
    radial-gradient(900px 400px at 50% 0%, rgba(37, 99, 235, 0.12), transparent 70%),
    linear-gradient(180deg, #eff6ff 0%, $bg 100%);
  text-align: center;

  .header-content {
    margin-bottom: 32px;
  }

  .page-title {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    font-size: 28px;
    font-weight: 800;
    color: $text;
    margin: 0 0 10px;

    .el-icon { font-size: 32px; color: $course-primary; }
  }

  .page-subtitle {
    font-size: 15px;
    color: $text-muted;
    margin: 0;
  }

  .header-stats {
    display: flex;
    justify-content: center;
    gap: 48px;

    .stat-item {
      text-align: center;

      .stat-num {
        display: block;
        font-size: 28px;
        font-weight: 800;
        color: $course-primary;
        margin-bottom: 4px;
      }

      .stat-label {
        font-size: 12.5px;
        color: $text-muted;
      }
    }
  }
}

// 筛选区域
.filter-section {
  margin-top: -40px;
  padding: 0 40px;
  position: relative;
  z-index: 10;

  .filter-container {
    max-width: 1100px;
    margin: 0 auto;
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius;
    padding: 24px 28px;
    box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  }

  .search-box {
    margin-bottom: 20px;

    :deep(.el-input) {
      max-width: 100%;

      .el-input__wrapper {
        border-radius: 999px;
        padding: 4px 16px;
        box-shadow: 0 0 0 1px $border inset;

        &:hover { box-shadow: 0 0 0 1px rgba($course-primary, 0.35) inset; }
        &.is-focus { box-shadow: 0 0 0 1px $course-primary inset, 0 0 0 3px rgba($course-primary, 0.08); }
      }

      .el-input-group__append .el-button {
        border-radius: 0 999px 999px 0;
        padding: 0 20px;
        background: $course-primary;
        border-color: $course-primary;
        color: #fff;
        border-left: 0;

        &:hover { background: darken($course-primary, 8%); border-color: darken($course-primary, 8%); }
      }

      .el-input-group__append {
        background: $course-primary;
        border-color: $course-primary;
        box-shadow: none;
      }
    }
  }

  .category-filter,
  .sort-filter {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;

    &:last-child { margin-bottom: 0; }
  }

  .filter-label {
    font-size: 13px;
    font-weight: 700;
    color: $text-secondary;
    white-space: nowrap;
  }

  .category-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .category-tag {
    cursor: pointer;
    border-radius: 999px;
    padding: 6px 16px;
    font-size: 12.5px;
    transition: all 0.2s ease;

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba($course-primary, 0.15);
    }
  }

  :deep(.el-radio-group) {
    --el-radio-button-checked-bg-color: #{$course-primary};
    --el-radio-button-checked-border-color: #{$course-primary};
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
    background: $course-primary;
    border-color: $course-primary;
    box-shadow: -1px 0 0 0 $course-primary;
  }
}

// 课程列表
.course-section {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

// 课程卡片
.course-card {
  background: $bg-card;
  border: 1px solid $border;
  border-radius: $radius;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba($course-primary, 0.3);
    box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);

    .card-cover img { transform: scale(1.04); }
    .cover-overlay { opacity: 1; }
  }

  .card-cover {
    position: relative;
    height: 170px;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.4s ease;
    }

    .cover-overlay {
      position: absolute;
      inset: 0;
      background: rgba(37, 99, 235, 0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.3s ease;
    }

    .card-tags {
      position: absolute;
      top: 12px;
      left: 12px;

      .el-tag {
        font-weight: 600;
        padding: 4px 12px;
        border-radius: 999px;
      }
    }

    .card-category {
      position: absolute;
      top: 12px;
      right: 12px;
      background: rgba(0, 0, 0, 0.5);
      color: #fff;
      padding: 4px 12px;
      border-radius: 999px;
      font-size: 11.5px;
      font-weight: 600;
    }
  }

  .card-content {
    padding: 20px;
  }

  .course-title {
    font-size: 16px;
    font-weight: 700;
    color: $text;
    margin: 0 0 8px;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    min-height: 48px;
  }

  .course-desc {
    font-size: 13px;
    color: $text-muted;
    margin: 0 0 14px;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .teacher-info {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 14px;
    padding-bottom: 14px;
    border-bottom: 1px solid rgba(226, 232, 240, 0.7);

    .teacher-text { display: flex; flex-direction: column; gap: 1px; }
    .teacher-name { font-size: 13px; color: $text; font-weight: 600; }
    .teacher-title { font-size: 11.5px; color: $text-muted; }
  }

  .course-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
    margin-bottom: 12px;

    .meta-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: $text-muted;
    }
  }

  .course-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;

    .el-tag {
      border-radius: 999px;
      font-size: 11px;
    }
  }
}

// 响应式
@media (max-width: 768px) {
  .page-header {
    padding: 32px 20px 60px;
    .page-title { font-size: 22px; }
    .header-stats { gap: 24px; .stat-num { font-size: 22px; } }
  }

  .filter-section {
    padding: 0 16px;
    margin-top: -30px;
    .filter-container { padding: 18px 16px; }
    .category-filter, .sort-filter { flex-direction: column; align-items: flex-start; }
  }

  .course-section { padding: 24px 16px; }
  .course-grid { grid-template-columns: 1fr; }
}
</style>