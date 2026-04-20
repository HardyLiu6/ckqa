<template>
  <div class="home-container">
    <NavHeader />

    <!-- 主内容区域 -->
    <main class="main-content">
      <!-- Hero区域 - 智能问答入口 -->
      <section class="hero-section">
        <div class="hero-background">
          <div class="gradient-orb orb-1"></div>
          <div class="gradient-orb orb-2"></div>
          <div class="gradient-orb orb-3"></div>
          <!-- 粒子效果 -->
          <div class="particles">
            <span v-for="i in 20" :key="i" class="particle" :style="getParticleStyle(i)"></span>
          </div>
        </div>

        <div class="hero-content">
          <div class="hero-badge">
            <el-icon class="badge-icon">
              <Cpu />
            </el-icon>
            <span>AI 驱动的智能学习助手</span>
          </div>

          <h1 class="hero-title">
            <span class="title-line">让学习变得</span>
            <span class="title-highlight-wrapper">
              <span class="title-highlight">{{ currentWord }}</span>
              <span class="typing-cursor">|</span>
            </span>
          </h1>

          <p class="hero-subtitle">
            融合知识图谱与大语言模型，为您提供精准、专业的课程解答
          </p>

          <!-- 核心问答入口卡片 -->
          <div class="qa-entry-card" @click="goToQA" @mouseenter="isHovered = true" @mouseleave="isHovered = false">
            <div class="card-glow" :class="{ active: isHovered }"></div>
            <div class="card-content">
              <div class="input-mockup">
                <el-icon class="search-icon">
                  <Search />
                </el-icon>
                <span class="placeholder-text">{{ placeholderText }}</span>
                <span class="placeholder-cursor" :class="{ blink: !isTyping }">|</span>
              </div>
              <div class="entry-hint">
                <span class="hint-text">点击开始提问</span>
                <el-icon class="hint-arrow">
                  <Right />
                </el-icon>
              </div>
            </div>
            <div class="card-shimmer"></div>
          </div>

          <!-- 快捷提问标签 -->
          <div class="quick-actions">
            <span class="action-label">热门问题</span>
            <div class="action-tags">
              <div v-for="(tag, index) in hotTags" :key="tag" class="action-tag"
                :style="{ animationDelay: `${index * 0.1}s` }" @click="goToQAWithQuestion(tag)">
                <el-icon>
                  <ChatDotRound />
                </el-icon>
                <span>{{ tag }}</span>
              </div>
            </div>
          </div>

          <!-- 特性展示 -->
          <div class="hero-features">
            <div class="feature-item" v-for="(item, index) in heroFeatures" :key="index">
              <div class="feature-icon-wrapper">
                <el-icon>
                  <component :is="item.icon" />
                </el-icon>
              </div>
              <span class="feature-text">{{ item.text }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 功能模块区域 -->
      <section class="features-section">
        <div class="section-header">
          <h2 class="section-title">核心功能</h2>
          <p class="section-desc">多维度智能服务，满足不同学习需求</p>
        </div>

        <div class="features-grid">
          <div v-for="feature in features" :key="feature.id" class="feature-card" :class="feature.theme"
            @click="navigateToFeature(feature.route)">
            <div class="feature-icon">
              <el-icon :size="32">
                <component :is="feature.icon" />
              </el-icon>
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.description }}</p>
            <div class="feature-stats">
              <span class="stat-item">
                <el-icon>
                  <User />
                </el-icon>
                {{ feature.users }}人使用
              </span>
            </div>
            <div class="feature-arrow">
              <el-icon>
                <ArrowRight />
              </el-icon>
            </div>
          </div>
        </div>
      </section>

      <!-- 双栏布局：我的课程 + 最新问答 -->
      <section class="content-section">
        <div class="content-grid">
          <!-- 左栏：我的课程 -->
          <div class="content-card courses-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon>
                  <Collection />
                </el-icon>
                我的课程
              </h3>
              <el-button text type="primary" @click="$router.push('/course/my')">
                查看全部
                <el-icon>
                  <ArrowRight />
                </el-icon>
              </el-button>
            </div>

            <div class="courses-list">
              <div v-for="course in myCourses" :key="course.id" class="course-item" @click="enterCourse(course.id)">
                <div class="course-cover">
                  <img :src="course.cover" :alt="course.name" />
                  <div class="course-progress-overlay">
                    <el-progress type="circle" :percentage="course.progress" :width="48" :stroke-width="4" />
                  </div>
                </div>
                <div class="course-info">
                  <h4 class="course-name">{{ course.name }}</h4>
                  <p class="course-teacher">{{ course.teacher }}</p>
                  <div class="course-meta">
                    <span class="meta-item">
                      <el-icon>
                        <VideoCamera />
                      </el-icon>
                      {{ course.chapters }}章节
                    </span>
                    <span class="meta-item">
                      <el-icon>
                        <QuestionFilled />
                      </el-icon>
                      {{ course.questions }}个问题
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 右栏：最新问答 -->
          <div class="content-card qa-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon>
                  <ChatLineSquare />
                </el-icon>
                最新问答
              </h3>
              <el-radio-group v-model="qaFilter" size="small">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="mine">我的</el-radio-button>
                <el-radio-button value="hot">热门</el-radio-button>
              </el-radio-group>
            </div>

            <div class="qa-list">
              <div v-for="qa in filteredQAList" :key="qa.id" class="qa-item" @click="viewQADetail(qa.id)">
                <div class="qa-header">
                  <el-avatar :size="32" :src="qa.userAvatar">
                    {{ qa.userName?.charAt(0) }}
                  </el-avatar>
                  <div class="qa-user-info">
                    <span class="qa-username">{{ qa.userName }}</span>
                    <span class="qa-time">{{ qa.timeAgo }}</span>
                  </div>
                  <el-tag :type="qa.status === 'answered' ? 'success' : 'warning'" size="small">
                    {{ qa.status === 'answered' ? '已解答' : '待解答' }}
                  </el-tag>
                </div>
                <h4 class="qa-title">{{ qa.title }}</h4>
                <p class="qa-preview">{{ qa.preview }}</p>
                <div class="qa-footer">
                  <span class="qa-course">
                    <el-icon>
                      <Reading />
                    </el-icon>
                    {{ qa.courseName }}
                  </span>
                  <div class="qa-stats">
                    <span class="stat">
                      <el-icon>
                        <View />
                      </el-icon>
                      {{ qa.views }}
                    </span>
                    <span class="stat">
                      <el-icon>
                        <ChatDotSquare />
                      </el-icon>
                      {{ qa.answers }}
                    </span>
                    <span class="stat">
                      <el-icon>
                        <Star />
                      </el-icon>
                      {{ qa.likes }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div class="card-footer">
              <el-button text type="primary" @click="loadMoreQA">
                加载更多
                <el-icon>
                  <ArrowDown />
                </el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 知识图谱预览 -->
      <section class="knowledge-section">
        <div class="section-header">
          <h2 class="section-title">知识图谱</h2>
          <p class="section-desc">可视化展示课程知识结构，快速定位学习重点</p>
        </div>

        <div class="knowledge-preview">
          <div class="kg-visualization" ref="kgContainer">
            <!-- 这里会通过ECharts或D3.js渲染知识图谱 -->
            <div class="kg-placeholder">
              <el-icon :size="64">
                <Share />
              </el-icon>
              <p>点击查看完整知识图谱</p>
            </div>
          </div>

          <div class="kg-sidebar">
            <h4 class="sidebar-title">热门知识点</h4>
            <div class="knowledge-tags">
              <el-tag v-for="(item, index) in hotKnowledge" :key="index"
                :type="['', 'success', 'warning', 'danger', 'info'][index % 5]" class="knowledge-tag" effect="plain">
                {{ item.name }}
                <span class="tag-count">{{ item.count }}</span>
              </el-tag>
            </div>

            <el-button type="primary" class="explore-btn" @click="$router.push('/knowledge')">
              探索知识图谱
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>
      </section>

      <!-- 数据统计 -->
      <section class="stats-section">
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon courses-icon">
              <el-icon>
                <Reading />
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-number">{{ animatedStats.courses }}</span>
              <span class="stat-label">门课程</span>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon questions-icon">
              <el-icon>
                <ChatLineSquare />
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-number">{{ animatedStats.questions }}</span>
              <span class="stat-label">个问题</span>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon answers-icon">
              <el-icon>
                <CircleCheck />
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-number">{{ animatedStats.answers }}</span>
              <span class="stat-label">条解答</span>
            </div>
          </div>

          <div class="stat-card">
            <div class="stat-icon users-icon">
              <el-icon>
                <UserFilled />
              </el-icon>
            </div>
            <div class="stat-content">
              <span class="stat-number">{{ animatedStats.users }}</span>
              <span class="stat-label">位用户</span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <!-- 底部 -->
    <footer class="page-footer">
      <div class="footer-content">
        <div class="footer-info">
          <span class="footer-logo">智课问答</span>
          <span class="footer-copyright">© 2026 面向课程的混合型问答系统</span>
        </div>
        <div class="footer-links">
          <a href="#">关于我们</a>
          <a href="#">使用帮助</a>
          <a href="#">隐私政策</a>
          <a href="#">联系我们</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import {
  Search, ChatDotRound, Reading, Picture, Microphone,
  Promotion, ArrowRight, Collection, VideoCamera, QuestionFilled,
  ChatLineSquare, View, ChatDotSquare, Star, Share, CircleCheck,
  UserFilled, User, ArrowDown, Cpu, Right, Lightning, Clock, Trophy,
  DataAnalysis
} from '@element-plus/icons-vue'

// 路由
const router = useRouter()

// 响应式数据
const qaFilter = ref('all')
const kgContainer = ref(null)

// ==================== Hero 区域相关 ====================
const isHovered = ref(false)
const isTyping = ref(true)
const currentWord = ref('简单')
const placeholderText = ref('')

const words = ['简单', '高效', '智能', '有趣']
const placeholders = [
  '什么是二叉树的遍历？',
  'TCP三次握手的过程是怎样的？',
  '如何优化SQL查询性能？',
  '进程和线程有什么区别？'
]

let wordIndex = 0
let placeholderCharIndex = 0
let placeholderIndex = 0
let wordTimer = null
let placeholderTimer = null

// 标题文字切换动画
const animateWords = () => {
  wordTimer = setInterval(() => {
    wordIndex = (wordIndex + 1) % words.length
    currentWord.value = words[wordIndex]
  }, 2500)
}

// 占位符打字效果
const animatePlaceholder = () => {
  const type = () => {
    const currentPlaceholderText = placeholders[placeholderIndex]
    if (placeholderCharIndex < currentPlaceholderText.length) {
      placeholderText.value = currentPlaceholderText.slice(0, placeholderCharIndex + 1)
      placeholderCharIndex++
      isTyping.value = true
      placeholderTimer = setTimeout(type, 80)
    } else {
      isTyping.value = false
      placeholderTimer = setTimeout(() => {
        placeholderTimer = setTimeout(() => {
          placeholderCharIndex = 0
          placeholderIndex = (placeholderIndex + 1) % placeholders.length
          placeholderText.value = ''
          type()
        }, 500)
      }, 2000)
    }
  }
  type()
}

// 粒子样式生成
const getParticleStyle = (index) => {
  const size = Math.random() * 4 + 2
  return {
    width: `${size}px`,
    height: `${size}px`,
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 100}%`,
    animationDelay: `${Math.random() * 5}s`,
    animationDuration: `${Math.random() * 10 + 10}s`
  }
}

// Hero 特性
const heroFeatures = ref([
  { icon: 'Lightning', text: '秒级响应' },
  { icon: 'Trophy', text: '准确率95%+' },
  { icon: 'Clock', text: '24小时在线' }
])

// 跳转到问答页面
const goToQA = () => {
  router.push('/qa/ask')
}

// 携带问题跳转
const goToQAWithQuestion = (question) => {
  router.push({
    path: '/qa/ask',
    query: { q: question }
  })
}

// ==================== 原有数据 ====================

// 热门问题标签
const hotTags = ref([
  '什么是二叉树的遍历？',
  'TCP和UDP的区别',
  '进程和线程的区别',
  'SQL优化技巧'
])

// 功能模块
const features = ref([
  {
    id: 1,
    title: '智能问答',
    description: '基于知识图谱与大模型的混合问答，精准理解您的问题',
    icon: 'ChatDotRound',
    theme: 'theme-blue',
    users: '12.5k',
    route: '/qa/ask'
  },
  {
    id: 2,
    title: '知识检索',
    description: '全文检索课程资料，快速定位知识点',
    icon: 'Search',
    theme: 'theme-green',
    users: '8.3k',
    route: '/knowledge/search'
  },
  {
    id: 3,
    title: '学习社区',
    description: '与同学交流讨论，分享学习心得',
    icon: 'UserFilled',
    theme: 'theme-orange',
    users: '15.2k',
    route: '/community'
  },
  {
    id: 4,
    title: '错题分析',
    description: 'AI分析错题原因，推荐针对性练习',
    icon: 'DataAnalysis',
    theme: 'theme-purple',
    users: '6.8k',
    route: '/analysis'
  }
])

// 我的课程
const myCourses = ref([
  {
    id: 1,
    name: '数据结构与算法',
    teacher: '张教授',
    cover: 'https://picsum.photos/seed/ds/200/120',
    progress: 75,
    chapters: 12,
    questions: 48
  },
  {
    id: 2,
    name: '计算机网络',
    teacher: '李教授',
    cover: 'https://picsum.photos/seed/network/200/120',
    progress: 45,
    chapters: 8,
    questions: 32
  },
  {
    id: 3,
    name: '操作系统原理',
    teacher: '王教授',
    cover: 'https://picsum.photos/seed/os/200/120',
    progress: 30,
    chapters: 10,
    questions: 56
  }
])

// 问答列表
const qaList = ref([
  {
    id: 1,
    userName: '学习小达人',
    userAvatar: '',
    title: '二叉树的前序、中序、后序遍历有什么区别？',
    preview: '我在学习数据结构时，对于二叉树的三种遍历方式总是容易混淆...',
    courseName: '数据结构与算法',
    status: 'answered',
    views: 234,
    answers: 5,
    likes: 18,
    timeAgo: '2小时前',
    type: 'all'
  },
  {
    id: 2,
    userName: '码农小白',
    userAvatar: '',
    title: 'TCP三次握手的过程是怎样的？',
    preview: '网络课程中提到的TCP三次握手，能详细解释一下每一步的作用吗？',
    courseName: '计算机网络',
    status: 'pending',
    views: 156,
    answers: 3,
    likes: 12,
    timeAgo: '5小时前',
    type: 'hot'
  },
  {
    id: 3,
    userName: '我自己',
    userAvatar: '',
    title: '死锁的四个必要条件是什么？',
    preview: '操作系统课程中关于死锁的内容，想请教一下四个必要条件的具体含义...',
    courseName: '操作系统原理',
    status: 'answered',
    views: 89,
    answers: 2,
    likes: 8,
    timeAgo: '1天前',
    type: 'mine'
  }
])

// 热门知识点
const hotKnowledge = ref([
  { name: '二叉树', count: 128 },
  { name: 'TCP/IP', count: 96 },
  { name: '进程调度', count: 85 },
  { name: '索引优化', count: 72 },
  { name: '设计模式', count: 68 },
  { name: '递归算法', count: 64 },
  { name: 'HTTP协议', count: 58 },
  { name: '内存管理', count: 52 }
])

// 统计数据动画
const animatedStats = reactive({
  courses: 0,
  questions: 0,
  answers: 0,
  users: 0
})

const targetStats = {
  courses: 156,
  questions: 12580,
  answers: 45620,
  users: 8965
}

// 计算属性
const filteredQAList = computed(() => {
  if (qaFilter.value === 'all') return qaList.value
  return qaList.value.filter(qa => qa.type === qaFilter.value)
})

// 方法
const navigateToFeature = (route) => {
  router.push(route)
}

const enterCourse = (courseId) => {
  router.push(`/course/${courseId}`)
}

const viewQADetail = (qaId) => {
  router.push(`/qa/detail/${qaId}`)
}

const loadMoreQA = () => {
  // 加载更多问答
}

// 数字动画
const animateNumber = (key, target) => {
  const duration = 2000
  const step = target / (duration / 16)
  let current = 0

  const timer = setInterval(() => {
    current += step
    if (current >= target) {
      animatedStats[key] = target
      clearInterval(timer)
    } else {
      animatedStats[key] = Math.floor(current)
    }
  }, 16)
}

onMounted(() => {
  // 启动数字动画
  Object.keys(targetStats).forEach(key => {
    animateNumber(key, targetStats[key])
  })

  // 启动 Hero 动画
  animateWords()
  animatePlaceholder()
})

onUnmounted(() => {
  // 清理定时器
  clearInterval(wordTimer)
  clearTimeout(placeholderTimer)
})
</script>

<style lang="scss" scoped>
// 设计变量
$primary-color: #4f46e5;
$primary-light: #818cf8;
$primary-dark: #3730a3;
$secondary-color: #0ea5e9;
$success-color: #10b981;
$warning-color: #f59e0b;
$danger-color: #ef4444;

$bg-primary: #f8fafc;
$bg-card: #ffffff;
$bg-dark: #0f172a;

$text-primary: #1e293b;
$text-secondary: #64748b;
$text-muted: #94a3b8;

$border-color: #e2e8f0;
$shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
$shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
$shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
$shadow-xl: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1);

$radius-sm: 6px;
$radius-md: 12px;
$radius-lg: 16px;
$radius-xl: 24px;

// 全局容器
.home-container {
  min-height: 100vh;
  background: $bg-primary;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

// 主内容
.main-content {
  padding-top: 64px;
}

// ==================== Hero区域 - 灵动设计 ====================
.hero-section {
  position: relative;
  padding: 100px 32px 80px;
  overflow: hidden;
  background: linear-gradient(180deg, #0f0f1a 0%, #1a1a2e 50%, #16213e 100%);
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;

  .hero-background {
    position: absolute;
    inset: 0;
    overflow: hidden;

    .gradient-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(100px);
      opacity: 0.6;

      &.orb-1 {
        width: 600px;
        height: 600px;
        background: radial-gradient(circle, rgba(99, 102, 241, 0.4) 0%, transparent 70%);
        top: -200px;
        right: -100px;
        animation: pulse 8s ease-in-out infinite;
      }

      &.orb-2 {
        width: 500px;
        height: 500px;
        background: radial-gradient(circle, rgba(14, 165, 233, 0.3) 0%, transparent 70%);
        bottom: -150px;
        left: -100px;
        animation: pulse 10s ease-in-out infinite reverse;
      }

      &.orb-3 {
        width: 400px;
        height: 400px;
        background: radial-gradient(circle, rgba(168, 85, 247, 0.35) 0%, transparent 70%);
        top: 40%;
        left: 50%;
        transform: translateX(-50%);
        animation: pulse 12s ease-in-out infinite;
      }
    }

    .particles {
      position: absolute;
      inset: 0;

      .particle {
        position: absolute;
        background: rgba(255, 255, 255, 0.3);
        border-radius: 50%;
        animation: float-particle linear infinite;
      }
    }
  }

  .hero-content {
    position: relative;
    max-width: 900px;
    margin: 0 auto;
    text-align: center;
    z-index: 1;
  }

  .hero-badge {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    background: rgba(99, 102, 241, 0.15);
    border: 1px solid rgba(99, 102, 241, 0.3);
    border-radius: 50px;
    color: #a5b4fc;
    font-size: 14px;
    margin-bottom: 32px;
    animation: fadeInUp 0.6s ease-out;

    .badge-icon {
      animation: spin 4s linear infinite;
    }
  }

  .hero-title {
    font-size: 56px;
    font-weight: 800;
    color: #fff;
    margin-bottom: 20px;
    line-height: 1.2;
    animation: fadeInUp 0.6s ease-out 0.1s backwards;

    .title-line {
      display: block;
      margin-bottom: 8px;
    }

    .title-highlight-wrapper {
      display: inline-flex;
      align-items: center;
    }

    .title-highlight {
      background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #06b6d4 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-size: 200% 200%;
      animation: gradient-flow 3s ease infinite;
    }

    .typing-cursor {
      color: #6366f1;
      font-weight: 300;
      animation: blink 1s step-end infinite;
      margin-left: 4px;
    }
  }

  .hero-subtitle {
    font-size: 18px;
    color: rgba(255, 255, 255, 0.7);
    margin-bottom: 48px;
    animation: fadeInUp 0.6s ease-out 0.2s backwards;
  }

  // 核心问答入口卡片
  .qa-entry-card {
    position: relative;
    max-width: 640px;
    margin: 0 auto 40px;
    padding: 4px;
    border-radius: 20px;
    cursor: pointer;
    animation: fadeInUp 0.6s ease-out 0.3s backwards;
    transition: transform 0.3s ease;

    &:hover {
      transform: translateY(-4px) scale(1.02);

      .card-shimmer {
        opacity: 1;
      }

      .hint-arrow {
        transform: translateX(4px);
      }

      .card-content {
        background: rgba(30, 30, 50, 0.95);
      }
    }

    .card-glow {
      position: absolute;
      inset: 0;
      border-radius: 20px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6, #06b6d4, #6366f1);
      background-size: 300% 300%;
      animation: gradient-rotate 4s linear infinite;
      opacity: 0.8;
      transition: opacity 0.3s;

      &.active {
        opacity: 1;
      }
    }

    .card-content {
      position: relative;
      background: rgba(20, 20, 40, 0.9);
      border-radius: 16px;
      padding: 20px 28px;
      backdrop-filter: blur(20px);
      transition: background 0.3s;
    }

    .input-mockup {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      .search-icon {
        font-size: 22px;
        color: #6366f1;
      }

      .placeholder-text {
        flex: 1;
        font-size: 17px;
        color: rgba(255, 255, 255, 0.8);
        text-align: left;
        min-height: 24px;
      }

      .placeholder-cursor {
        color: #6366f1;
        font-weight: 300;
        font-size: 20px;

        &.blink {
          animation: blink 1s step-end infinite;
        }
      }
    }

    .entry-hint {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding-top: 12px;
      border-top: 1px solid rgba(255, 255, 255, 0.1);

      .hint-text {
        font-size: 14px;
        color: rgba(255, 255, 255, 0.5);
      }

      .hint-arrow {
        font-size: 14px;
        color: #6366f1;
        transition: transform 0.3s;
      }
    }

    .card-shimmer {
      position: absolute;
      inset: 0;
      border-radius: 20px;
      background: linear-gradient(90deg,
          transparent,
          rgba(255, 255, 255, 0.1),
          transparent);
      background-size: 200% 100%;
      animation: shimmer 2s infinite;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.3s;
    }
  }

  // 快捷操作
  .quick-actions {
    margin-bottom: 48px;
    animation: fadeInUp 0.6s ease-out 0.4s backwards;

    .action-label {
      display: block;
      font-size: 14px;
      color: rgba(255, 255, 255, 0.5);
      margin-bottom: 16px;
    }

    .action-tags {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 12px;
    }

    .action-tag {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 10px 18px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 50px;
      color: rgba(255, 255, 255, 0.8);
      font-size: 14px;
      cursor: pointer;
      transition: all 0.3s;
      animation: fadeInUp 0.5s ease-out backwards;

      &:hover {
        background: rgba(99, 102, 241, 0.2);
        border-color: rgba(99, 102, 241, 0.4);
        color: #fff;
        transform: translateY(-2px);
      }

      .el-icon {
        font-size: 14px;
        color: #6366f1;
      }
    }
  }

  // Hero 特性
  .hero-features {
    display: flex;
    justify-content: center;
    gap: 48px;
    animation: fadeInUp 0.6s ease-out 0.5s backwards;

    .feature-item {
      display: flex;
      align-items: center;
      gap: 10px;

      .feature-icon-wrapper {
        width: 36px;
        height: 36px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgba(99, 102, 241, 0.15);
        border-radius: 10px;

        .el-icon {
          font-size: 18px;
          color: #6366f1;
        }
      }

      .feature-text {
        font-size: 14px;
        color: rgba(255, 255, 255, 0.7);
      }
    }
  }
}

// Hero 动画关键帧
@keyframes pulse {

  0%,
  100% {
    transform: scale(1);
    opacity: 0.6;
  }

  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
}

@keyframes float-particle {
  0% {
    transform: translateY(100vh) rotate(0deg);
    opacity: 0;
  }

  10% {
    opacity: 1;
  }

  90% {
    opacity: 1;
  }

  100% {
    transform: translateY(-100vh) rotate(720deg);
    opacity: 0;
  }
}

@keyframes gradient-flow {
  0% {
    background-position: 0% 50%;
  }

  50% {
    background-position: 100% 50%;
  }

  100% {
    background-position: 0% 50%;
  }
}

@keyframes gradient-rotate {
  0% {
    background-position: 0% 50%;
  }

  100% {
    background-position: 300% 50%;
  }
}

@keyframes blink {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0;
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }

  100% {
    background-position: 200% 0;
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}

// ==================== 功能模块 ====================
.features-section {
  padding: 80px 32px;
  max-width: 1400px;
  margin: 0 auto;
  background: $bg-primary;

  .section-header {
    text-align: center;
    margin-bottom: 48px;

    .section-title {
      font-size: 36px;
      font-weight: 700;
      color: $text-primary;
      margin-bottom: 12px;
    }

    .section-desc {
      font-size: 16px;
      color: $text-secondary;
    }
  }

  .features-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;

    @media (max-width: 1200px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 640px) {
      grid-template-columns: 1fr;
    }
  }

  .feature-card {
    position: relative;
    background: $bg-card;
    border-radius: $radius-lg;
    padding: 28px;
    cursor: pointer;
    transition: all 0.3s;
    border: 1px solid $border-color;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 4px;
    }

    &.theme-blue::before {
      background: linear-gradient(90deg, $primary-color, $secondary-color);
    }

    &.theme-green::before {
      background: linear-gradient(90deg, $success-color, #34d399);
    }

    &.theme-orange::before {
      background: linear-gradient(90deg, $warning-color, #fbbf24);
    }

    &.theme-purple::before {
      background: linear-gradient(90deg, #8b5cf6, #a78bfa);
    }

    &:hover {
      transform: translateY(-4px);
      box-shadow: $shadow-lg;

      .feature-arrow {
        opacity: 1;
        transform: translateX(0);
      }
    }

    .feature-icon {
      width: 56px;
      height: 56px;
      border-radius: $radius-md;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
    }

    &.theme-blue .feature-icon {
      background: rgba($primary-color, 0.1);
      color: $primary-color;
    }

    &.theme-green .feature-icon {
      background: rgba($success-color, 0.1);
      color: $success-color;
    }

    &.theme-orange .feature-icon {
      background: rgba($warning-color, 0.1);
      color: $warning-color;
    }

    &.theme-purple .feature-icon {
      background: rgba(#8b5cf6, 0.1);
      color: #8b5cf6;
    }

    .feature-title {
      font-size: 18px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 8px;
    }

    .feature-desc {
      font-size: 14px;
      color: $text-secondary;
      line-height: 1.6;
      margin-bottom: 16px;
    }

    .feature-stats {
      font-size: 13px;
      color: $text-muted;

      .stat-item {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }

    .feature-arrow {
      position: absolute;
      right: 20px;
      bottom: 20px;
      color: $text-muted;
      opacity: 0;
      transform: translateX(-10px);
      transition: all 0.3s;
    }
  }
}

// ==================== 内容双栏 ====================
.content-section {
  padding: 0 32px 80px;
  max-width: 1400px;
  margin: 0 auto;

  .content-grid {
    display: grid;
    grid-template-columns: 1fr 1.2fr;
    gap: 24px;

    @media (max-width: 1024px) {
      grid-template-columns: 1fr;
    }
  }

  .content-card {
    background: $bg-card;
    border-radius: $radius-lg;
    padding: 24px;
    border: 1px solid $border-color;

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;

      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 18px;
        font-weight: 600;
        color: $text-primary;
      }
    }

    .card-footer {
      text-align: center;
      padding-top: 16px;
      border-top: 1px solid $border-color;
      margin-top: 16px;
    }
  }
}

// 课程列表
.courses-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .course-item {
    display: flex;
    gap: 16px;
    padding: 12px;
    border-radius: $radius-md;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: $bg-primary;
    }

    .course-cover {
      position: relative;
      width: 120px;
      height: 72px;
      border-radius: $radius-sm;
      overflow: hidden;
      flex-shrink: 0;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .course-progress-overlay {
        position: absolute;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.2s;
      }

      &:hover .course-progress-overlay {
        opacity: 1;
      }
    }

    .course-info {
      flex: 1;
      min-width: 0;

      .course-name {
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 4px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .course-teacher {
        font-size: 13px;
        color: $text-secondary;
        margin-bottom: 8px;
      }

      .course-meta {
        display: flex;
        gap: 16px;
        font-size: 12px;
        color: $text-muted;

        .meta-item {
          display: flex;
          align-items: center;
          gap: 4px;
        }
      }
    }
  }
}

// 问答列表
.qa-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .qa-item {
    padding: 16px;
    border-radius: $radius-md;
    border: 1px solid $border-color;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $primary-color;
      box-shadow: $shadow-sm;
    }

    .qa-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;

      .qa-user-info {
        flex: 1;

        .qa-username {
          font-size: 14px;
          font-weight: 500;
          color: $text-primary;
        }

        .qa-time {
          font-size: 12px;
          color: $text-muted;
          margin-left: 8px;
        }
      }
    }

    .qa-title {
      font-size: 15px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 8px;
      line-height: 1.4;
    }

    .qa-preview {
      font-size: 14px;
      color: $text-secondary;
      line-height: 1.5;
      margin-bottom: 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .qa-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .qa-course {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 13px;
        color: $primary-color;
        background: rgba($primary-color, 0.08);
        padding: 4px 10px;
        border-radius: $radius-sm;
      }

      .qa-stats {
        display: flex;
        gap: 16px;

        .stat {
          display: flex;
          align-items: center;
          gap: 4px;
          font-size: 13px;
          color: $text-muted;
        }
      }
    }
  }
}

// ==================== 知识图谱 ====================
.knowledge-section {
  padding: 80px 32px;
  max-width: 1400px;
  margin: 0 auto;

  .section-header {
    text-align: center;
    margin-bottom: 48px;

    .section-title {
      font-size: 36px;
      font-weight: 700;
      color: $text-primary;
      margin-bottom: 12px;
    }

    .section-desc {
      font-size: 16px;
      color: $text-secondary;
    }
  }

  .knowledge-preview {
    display: grid;
    grid-template-columns: 1fr 300px;
    gap: 24px;
    background: $bg-card;
    border-radius: $radius-lg;
    padding: 24px;
    border: 1px solid $border-color;

    @media (max-width: 900px) {
      grid-template-columns: 1fr;
    }
  }

  .kg-visualization {
    min-height: 400px;
    background: linear-gradient(135deg, #f0f4ff 0%, #e8f4f8 100%);
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;

    .kg-placeholder {
      text-align: center;
      color: $text-secondary;

      p {
        margin-top: 16px;
        font-size: 14px;
      }
    }
  }

  .kg-sidebar {
    .sidebar-title {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 16px;
    }

    .knowledge-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 24px;

      .knowledge-tag {
        cursor: pointer;
        transition: all 0.2s;

        .tag-count {
          margin-left: 4px;
          font-size: 11px;
          opacity: 0.7;
        }

        &:hover {
          transform: scale(1.05);
        }
      }
    }

    .explore-btn {
      width: 100%;
      border-radius: $radius-md;
    }
  }
}

// ==================== 统计数据 ====================
.stats-section {
  padding: 80px 32px;
  background: linear-gradient(135deg, $bg-dark 0%, #1e293b 100%);

  .stats-grid {
    max-width: 1200px;
    margin: 0 auto;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 32px;

    @media (max-width: 900px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 500px) {
      grid-template-columns: 1fr;
    }
  }

  .stat-card {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 24px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: $radius-lg;
    border: 1px solid rgba(255, 255, 255, 0.1);

    .stat-icon {
      width: 64px;
      height: 64px;
      border-radius: $radius-md;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;

      &.courses-icon {
        background: rgba($primary-color, 0.2);
        color: $primary-light;
      }

      &.questions-icon {
        background: rgba($secondary-color, 0.2);
        color: $secondary-color;
      }

      &.answers-icon {
        background: rgba($success-color, 0.2);
        color: $success-color;
      }

      &.users-icon {
        background: rgba($warning-color, 0.2);
        color: $warning-color;
      }
    }

    .stat-content {
      .stat-number {
        display: block;
        font-size: 36px;
        font-weight: 700;
        color: white;
        line-height: 1;
        margin-bottom: 4px;
      }

      .stat-label {
        font-size: 14px;
        color: rgba(255, 255, 255, 0.6);
      }
    }
  }
}

// ==================== 页脚 ====================
.page-footer {
  background: $bg-dark;
  padding: 32px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);

  .footer-content {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;

    @media (max-width: 640px) {
      flex-direction: column;
      gap: 16px;
      text-align: center;
    }
  }

  .footer-info {
    display: flex;
    align-items: center;
    gap: 16px;

    .footer-logo {
      font-size: 18px;
      font-weight: 700;
      color: white;
    }

    .footer-copyright {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.5);
    }
  }

  .footer-links {
    display: flex;
    gap: 24px;

    a {
      font-size: 14px;
      color: rgba(255, 255, 255, 0.6);
      text-decoration: none;
      transition: color 0.2s;

      &:hover {
        color: white;
      }
    }
  }
}

// ==================== 响应式适配 ====================
@media (max-width: 768px) {
  .hero-section {
    padding: 80px 20px 60px;
    min-height: auto;

    .hero-title {
      font-size: 36px;
    }

    .hero-subtitle {
      font-size: 16px;
    }

    .hero-features {
      flex-direction: column;
      gap: 16px;
    }

    .quick-actions .action-tags {
      flex-direction: column;
      align-items: center;
    }
  }

  .nav-header {
    .nav-menu {
      display: none;
    }

    .nav-actions .global-search {
      display: none;
    }
  }
}
</style>