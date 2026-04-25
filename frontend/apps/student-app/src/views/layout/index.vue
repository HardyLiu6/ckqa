<!-- frontend/apps/student-app/src/views/layout/index.vue -->
<!-- 落地页 · 深色 + 节点云 + Pin-scroll + 磁吸 · 详见设计稿 §6.1 -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import KnowledgeNodeCloud from '@/components/landing/KnowledgeNodeCloud.vue'
import PinScrollShowcase from '@/components/landing/PinScrollShowcase.vue'
import MagneticButton from '@/components/landing/MagneticButton.vue'
import Tilt3DCard from '@/components/landing/Tilt3DCard.vue'

const router = useRouter()
const isScrolled = ref(false)

function handleScroll() {
  isScrolled.value = window.scrollY > 60
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
})

function goToRegister() { router.push('/register') }
function goToLogin() { router.push('/login') }

const stats = [
  { num: '50K+', label: '活跃用户' },
  { num: '120+', label: '精品课程' },
  { num: '98%', label: '好评率' },
  { num: '24/7', label: 'AI 随问随答' },
]

const features = [
  { icon: '💬', title: '多轮 AI 问答', desc: '上下文记忆，像和讲师对话' },
  { icon: '🕸', title: '知识图谱', desc: '知识点自动连接，脉络一眼清' },
  { icon: '📚', title: '课程学习', desc: '沉浸式视频 + 笔记 + 问问' },
  { icon: '📊', title: '学习分析', desc: '错题 / 报告 / 推荐一体' },
]

const testimonials = [
  { name: '陈同学', role: '计算机 · 大三', quote: '第一次感觉课本里的知识点真的"串"起来了。' },
  { name: '王同学', role: '软件工程 · 大二', quote: 'AI 回答不止抄课本，还能结合图谱给出上下文。' },
  { name: '李同学', role: '数据科学 · 研一', quote: '期末复习阶段，错题推荐帮我定位到薄弱环节。' },
]
</script>

<template>
  <div class="landing-page">
    <!-- 顶栏 -->
    <nav class="navbar" :class="{ scrolled: isScrolled }">
      <div class="nav-container">
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none">
              <path d="M20 4L36 12V28L20 36L4 28V12L20 4Z" stroke="currentColor" stroke-width="2" fill="none" />
              <circle cx="20" cy="20" r="6" fill="currentColor" />
            </svg>
          </div>
          <span class="logo-text">智课问答</span>
        </div>
        <div class="nav-links">
          <a href="#features" class="nav-link">功能特性</a>
          <a href="#showcase" class="nav-link">产品展示</a>
          <a href="#stats" class="nav-link">数据统计</a>
          <a href="#testimonials" class="nav-link">用户评价</a>
        </div>
        <div class="nav-actions">
          <button class="btn-ghost" @click="goToLogin">登录</button>
          <button class="btn-primary" @click="goToRegister">免费开始</button>
        </div>
      </div>
    </nav>

    <!-- Hero 区 -->
    <section class="hero">
      <KnowledgeNodeCloud class="hero-bg" />
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          <span>AI 驱动的智能学习平台</span>
        </div>
        <h1 class="hero-title">
          <span class="title-line">重新定义</span>
          <span class="title-line gradient-text">课程问答体验</span>
        </h1>
        <p class="hero-subtitle">
          基于知识图谱和大语言模型，让每一次学习都有迹可循。<br />
          多轮对话、上下文记忆、跨章节推理，支持你和课本之间的每一次灵感追问。
        </p>
        <div class="hero-actions">
          <MagneticButton variant="primary" @click="goToRegister">
            <span>立即体验</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </MagneticButton>
          <MagneticButton variant="secondary">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M8 5v14l11-7z" />
            </svg>
            <span>观看演示</span>
          </MagneticButton>
        </div>
      </div>
    </section>

    <!-- 特性卡 · 3D 倾斜 -->
    <section id="features" class="features-section">
      <div class="section-header">
        <h2 class="section-title">学习，被重新设计</h2>
        <p class="section-desc">四大核心能力，环环相扣</p>
      </div>
      <div class="features-grid">
        <Tilt3DCard v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-icon">{{ f.icon }}</div>
          <h3 class="feature-title">{{ f.title }}</h3>
          <p class="feature-desc">{{ f.desc }}</p>
        </Tilt3DCard>
      </div>
    </section>

    <!-- Pin-scroll 特性流 -->
    <PinScrollShowcase />

    <!-- 数据统计 -->
    <section id="stats" class="stats-section">
      <div class="stats-grid">
        <div v-for="s in stats" :key="s.label" class="stat-item">
          <div class="stat-num">{{ s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 用户评价 -->
    <section id="testimonials" class="testimonials-section">
      <div class="section-header">
        <h2 class="section-title">学员们怎么说</h2>
      </div>
      <div class="testimonials-grid">
        <div v-for="t in testimonials" :key="t.name" class="testimonial-card">
          <p class="quote">"{{ t.quote }}"</p>
          <div class="author">
            <div class="author-avatar"></div>
            <div>
              <div class="author-name">{{ t.name }}</div>
              <div class="author-role">{{ t.role }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="cta-section">
      <div class="cta-inner">
        <h2 class="cta-title">准备好重新学习了吗？</h2>
        <p class="cta-subtitle">完全免费注册，立即体验 AI + 知识图谱的学习方式</p>
        <MagneticButton variant="primary" @click="goToRegister">
          <span>免费开始</span>
        </MagneticButton>
      </div>
    </section>

    <footer class="footer">
      <div class="footer-inner">
        <div>© 2026 智课问答 · CKQA</div>
        <div>基于 Vue 3 + GraphRAG</div>
      </div>
    </footer>
  </div>
</template>
<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.landing-page {
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
  color: #fff;
  background: #0f0f1a;
  overflow-x: hidden;
}

// ========== Navbar ==========
.navbar {
  position: fixed;
  top: 0; left: 0; right: 0;
  z-index: 100;
  padding: 16px 0;
  transition: background $duration-fast $ease-out, padding $duration-fast $ease-out,
    backdrop-filter $duration-fast $ease-out;

  &.scrolled {
    background: rgba(15, 15, 26, 0.7);
    backdrop-filter: blur(20px);
    padding: 10px 0;
  }
}

.nav-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;

  .logo-icon {
    width: 32px; height: 32px;
    color: #818cf8;
  }
  .logo-text {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-weight: 700;
    font-size: 18px;
  }
}

.nav-links {
  display: flex;
  gap: 28px;

  .nav-link {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.7);
    transition: color $duration-fast $ease-out;
    &:hover { color: #fff; }
  }

  @media (max-width: $bp-tablet) { display: none; }
}
.nav-actions {
  display: flex;
  gap: 12px;

  .btn-ghost, .btn-primary {
    padding: 8px 18px;
    border-radius: $radius-lg;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    font-family: inherit;
    transition: transform $duration-fast $ease-out;
  }
  .btn-ghost {
    background: transparent;
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.2);
    &:hover { background: rgba(255, 255, 255, 0.05); }
  }
  .btn-primary {
    background: linear-gradient(135deg, #6366f1, #818cf8);
    color: #fff;
    border: 0;
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);
    &:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(99, 102, 241, 0.5); }
  }
}

// ========== Hero ==========
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  .hero-bg { z-index: 0; }

  .hero-content {
    position: relative;
    z-index: 1;
    text-align: center;
    padding: 0 32px;
    max-width: 860px;
  }
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: $radius-full;
  color: #c4b5fd;
  font-size: 13px;
  margin-bottom: 24px;

  .badge-dot {
    width: 6px; height: 6px;
    background: #a5b4fc;
    border-radius: 50%;
    box-shadow: 0 0 8px #a5b4fc;
  }
}
.hero-title {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 72px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
  margin-bottom: 24px;

  .title-line { display: block; }
  .gradient-text {
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  @media (max-width: $bp-tablet) { font-size: 44px; }
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.65);
  margin-bottom: 40px;
  @media (max-width: $bp-tablet) { font-size: 15px; }
}

.hero-actions {
  display: inline-flex;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: center;
}

// ========== Features ==========
.features-section {
  padding: 120px 32px;
  max-width: 1280px;
  margin: 0 auto;

  .section-header { text-align: center; margin-bottom: 64px; }
  .section-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 12px;
    letter-spacing: -0.02em;
  }
  .section-desc {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.6);
  }
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  @media (max-width: $bp-laptop) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}
.feature-card {
  padding: 28px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;
  transition: border-color $duration-base $ease-out;
  &:hover { border-color: rgba(99, 102, 241, 0.4); }

  .feature-icon { font-size: 32px; margin-bottom: 16px; }
  .feature-title { font-size: 18px; font-weight: 700; margin-bottom: 8px; }
  .feature-desc { font-size: 13px; color: rgba(255, 255, 255, 0.6); line-height: 1.6; }
}

// ========== Stats ==========
.stats-section {
  padding: 80px 32px;
  background: linear-gradient(180deg, transparent, rgba(99, 102, 241, 0.05));
}

.stats-grid {
  max-width: 1280px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
  text-align: center;
  @media (max-width: $bp-tablet) { grid-template-columns: repeat(2, 1fr); }

  .stat-num {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 48px;
    font-weight: 700;
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  .stat-label {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.6);
    margin-top: 4px;
  }
}

// ========== Testimonials ==========
.testimonials-section {
  padding: 120px 32px;
  max-width: 1280px;
  margin: 0 auto;

  .section-header { text-align: center; margin-bottom: 64px; }
  .section-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 40px;
    font-weight: 700;
  }
}
.testimonials-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.testimonial-card {
  padding: 28px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;

  .quote {
    font-size: 15px;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.85);
    margin-bottom: 20px;
  }
  .author { display: flex; align-items: center; gap: 12px; }
  .author-avatar {
    width: 40px; height: 40px;
    background: linear-gradient(135deg, #6366f1, #818cf8);
    border-radius: 50%;
  }
  .author-name { font-weight: 600; }
  .author-role { font-size: 12px; color: rgba(255, 255, 255, 0.5); }
}

// ========== CTA ==========
.cta-section {
  padding: 120px 32px;
  text-align: center;

  .cta-inner { max-width: 680px; margin: 0 auto; }
  .cta-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 16px;
    letter-spacing: -0.02em;
  }
  .cta-subtitle {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.65);
    margin-bottom: 32px;
  }
}

// ========== Footer ==========
.footer {
  padding: 40px 32px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);

  .footer-inner {
    max-width: 1280px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    color: rgba(255, 255, 255, 0.4);
    font-size: 13px;

    @media (max-width: $bp-tablet) {
      flex-direction: column;
      gap: 8px;
    }
  }
}
</style>
