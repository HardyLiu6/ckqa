<template>
  <div class="landing-page" ref="landingRef">
    <!-- 自定义光标 -->
    <div class="custom-cursor" ref="cursorRef">
      <div class="cursor-dot"></div>
      <div class="cursor-ring"></div>
    </div>

    <!-- 导航栏 -->
    <nav class="navbar" :class="{ 'scrolled': isScrolled }">
      <div class="nav-container">
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none">
              <path d="M20 4L36 12V28L20 36L4 28V12L20 4Z" stroke="currentColor" stroke-width="2" fill="none" />
              <path d="M20 4V36M4 12L36 28M36 12L4 28" stroke="currentColor" stroke-width="1.5" opacity="0.5" />
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
        <button class="mobile-menu-btn" @click="toggleMobileMenu">
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
    </nav>

    <!-- 移动端菜单 -->
    <div class="mobile-menu" :class="{ 'active': isMobileMenuOpen }">
      <a href="#features" @click="closeMobileMenu">功能特性</a>
      <a href="#showcase" @click="closeMobileMenu">产品展示</a>
      <a href="#stats" @click="closeMobileMenu">数据统计</a>
      <a href="#testimonials" @click="closeMobileMenu">用户评价</a>
      <div class="mobile-actions">
        <button class="btn-ghost" @click="goToLogin">登录</button>
        <button class="btn-primary" @click="goToRegister">免费开始</button>
      </div>
    </div>

    <!-- Hero 区域 - 视差层 -->
    <section class="hero" ref="heroRef">
      <div class="hero-bg">
        <div class="parallax-layer layer-1" ref="layer1">
          <div class="floating-shape shape-1"></div>
          <div class="floating-shape shape-2"></div>
          <div class="floating-shape shape-3"></div>
        </div>
        <div class="parallax-layer layer-2" ref="layer2">
          <div class="grid-lines"></div>
        </div>
        <div class="parallax-layer layer-3" ref="layer3">
          <div class="glow-orb orb-1"></div>
          <div class="glow-orb orb-2"></div>
          <div class="glow-orb orb-3"></div>
        </div>
      </div>

      <div class="hero-content">
        <div class="hero-badge" data-aos="fade-down" data-aos-delay="100">
          <span class="badge-icon">✨</span>
          <span>AI驱动的智能学习平台</span>
        </div>
        <h1 class="hero-title" data-aos="fade-up" data-aos-delay="200">
          <span class="title-line">重新定义</span>
          <span class="title-line gradient-text">课程问答体验</span>
        </h1>
        <p class="hero-subtitle" data-aos="fade-up" data-aos-delay="300">
          基于大语言模型的智能问答系统，让学习更高效、更智能。
          <br>支持多模态交互，精准理解您的每一个问题。
        </p>
        <div class="hero-actions" data-aos="fade-up" data-aos-delay="400">
          <button class="btn-hero-primary" @click="goToRegister">
            <span>立即体验</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </button>
          <button class="btn-hero-secondary" @click="playDemo">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path d="M8 5v14l11-7z" />
            </svg>
            <span>观看演示</span>
          </button>
        </div>
        <div class="hero-stats" data-aos="fade-up" data-aos-delay="500">
          <div class="stat-item">
            <span class="stat-number">50K+</span>
            <span class="stat-label">活跃用户</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">1M+</span>
            <span class="stat-label">问答记录</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-number">98%</span>
            <span class="stat-label">满意度</span>
          </div>
        </div>
      </div>

      <div class="hero-visual" data-aos="zoom-in" data-aos-delay="600">
        <div class="visual-container">
          <div class="browser-mockup">
            <div class="browser-header">
              <div class="browser-dots">
                <span></span><span></span><span></span>
              </div>
              <div class="browser-url">智课问答 - 智能学习助手</div>
            </div>
            <div class="browser-content">
              <div class="chat-demo">
                <div class="chat-message user">
                  <div class="message-content">请解释一下量子纠缠的原理</div>
                </div>
                <div class="chat-message ai">
                  <div class="ai-avatar">AI</div>
                  <div class="message-content">
                    <div class="typing-indicator">
                      <span></span><span></span><span></span>
                    </div>
                    量子纠缠是量子力学中的一种现象，当两个或多个粒子以某种方式相互作用后...
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="floating-card card-1">
            <div class="card-icon">🎯</div>
            <div class="card-text">精准回答</div>
          </div>
          <div class="floating-card card-2">
            <div class="card-icon">⚡</div>
            <div class="card-text">实时响应</div>
          </div>
          <div class="floating-card card-3">
            <div class="card-icon">🧠</div>
            <div class="card-text">智能理解</div>
          </div>
        </div>
      </div>

      <div class="scroll-indicator" @click="scrollToFeatures">
        <div class="mouse">
          <div class="wheel"></div>
        </div>
        <span>向下滚动探索更多</span>
      </div>
    </section>

    <!-- 功能特性区域 -->
    <section class="features" id="features" ref="featuresRef">
      <div class="section-container">
        <div class="section-header" data-aos="fade-up">
          <span class="section-tag">功能特性</span>
          <h2 class="section-title">为什么选择智课问答？</h2>
          <p class="section-desc">我们提供全方位的智能学习解决方案，让每一次提问都物超所值</p>
        </div>

        <div class="features-grid">
          <div class="feature-card" v-for="(feature, index) in features" :key="index" data-aos="fade-up"
            :data-aos-delay="index * 100">
            <div class="feature-icon" :style="{ background: feature.gradient }">
              <component :is="feature.icon" />
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
            <div class="feature-hover-effect"></div>
          </div>
        </div>
      </div>

      <!-- 视差背景元素 -->
      <div class="features-bg">
        <div class="bg-circle circle-1" ref="featureCircle1"></div>
        <div class="bg-circle circle-2" ref="featureCircle2"></div>
      </div>
    </section>

    <!-- 产品展示区域 -->
    <section class="showcase" id="showcase" ref="showcaseRef">
      <div class="section-container">
        <div class="section-header" data-aos="fade-up">
          <span class="section-tag">产品展示</span>
          <h2 class="section-title">探索强大功能</h2>
          <p class="section-desc">直观的界面设计，让复杂的学习变得简单有趣</p>
        </div>

        <div class="showcase-tabs" data-aos="fade-up" data-aos-delay="100">
          <button v-for="(tab, index) in showcaseTabs" :key="index" class="tab-btn"
            :class="{ active: activeTab === index }" @click="activeTab = index">
            {{ tab.label }}
          </button>
        </div>

        <div class="showcase-content" data-aos="zoom-in" data-aos-delay="200">
          <div class="showcase-screen" ref="showcaseScreen">
            <div class="screen-glow"></div>
            <div class="screen-frame">
              <img :src="showcaseTabs[activeTab].image" :alt="showcaseTabs[activeTab].label" />
            </div>
          </div>
          <div class="showcase-info">
            <h3>{{ showcaseTabs[activeTab].title }}</h3>
            <p>{{ showcaseTabs[activeTab].description }}</p>
            <ul class="showcase-features">
              <li v-for="(item, i) in showcaseTabs[activeTab].features" :key="i">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 13l4 4L19 7" />
                </svg>
                {{ item }}
              </li>
            </ul>
          </div>
        </div>
      </div>

      <!-- 视差背景 -->
      <div class="showcase-parallax">
        <div class="parallax-element el-1" ref="showcaseEl1"></div>
        <div class="parallax-element el-2" ref="showcaseEl2"></div>
        <div class="parallax-element el-3" ref="showcaseEl3"></div>
      </div>
    </section>

    <!-- 数据统计区域 -->
    <section class="stats" id="stats" ref="statsRef">
      <div class="stats-bg">
        <div class="stats-grid-lines"></div>
      </div>
      <div class="section-container">
        <div class="stats-content">
          <div class="stats-text" data-aos="fade-right">
            <span class="section-tag">数据统计</span>
            <h2 class="section-title">用数据说话</h2>
            <p class="section-desc">
              持续增长的用户数据证明了我们的价值，
              每一个数字背后都是用户的信任与支持。
            </p>
            <button class="btn-stats" @click="goToRegister">加入我们</button>
          </div>
          <div class="stats-numbers" data-aos="fade-left">
            <div class="stat-card" v-for="(stat, index) in statsData" :key="index" :data-aos-delay="index * 100">
              <div class="stat-icon" :style="{ color: stat.color }">
                <component :is="stat.icon" />
              </div>
              <div class="stat-value">
                <span class="stat-num" ref="statNums">{{ stat.value }}</span>
                <span class="stat-suffix">{{ stat.suffix }}</span>
              </div>
              <div class="stat-name">{{ stat.name }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 用户评价区域 -->
    <section class="testimonials" id="testimonials" ref="testimonialsRef">
      <div class="section-container">
        <div class="section-header" data-aos="fade-up">
          <span class="section-tag">用户评价</span>
          <h2 class="section-title">听听用户怎么说</h2>
          <p class="section-desc">来自真实用户的反馈，见证智课问答的价值</p>
        </div>

        <div class="testimonials-slider" ref="testimonialsSlider">
          <div class="testimonial-card" v-for="(item, index) in testimonials" :key="index" data-aos="fade-up"
            :data-aos-delay="index * 100">
            <div class="testimonial-content">
              <div class="quote-icon">"</div>
              <p>{{ item.content }}</p>
            </div>
            <div class="testimonial-author">
              <div class="author-avatar" :style="{ background: item.avatarColor }">
                {{ item.name[0] }}
              </div>
              <div class="author-info">
                <span class="author-name">{{ item.name }}</span>
                <span class="author-role">{{ item.role }}</span>
              </div>
            </div>
            <div class="testimonial-rating">
              <span v-for="i in 5" :key="i" class="star">★</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 背景装饰 -->
      <div class="testimonials-bg">
        <div class="bg-gradient"></div>
      </div>
    </section>

    <!-- CTA 区域 -->
    <section class="cta" ref="ctaRef">
      <div class="cta-bg">
        <div class="cta-particles" ref="ctaParticles"></div>
      </div>
      <div class="section-container">
        <div class="cta-content" data-aos="zoom-in">
          <h2 class="cta-title">准备好开始智能学习之旅了吗？</h2>
          <p class="cta-desc">立即注册，免费体验所有功能，开启高效学习新纪元</p>
          <div class="cta-actions">
            <button class="btn-cta-primary" @click="goToRegister">
              免费注册
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </button>
            <button class="btn-cta-secondary" @click="contactUs">
              联系我们
            </button>
          </div>
          <div class="cta-trust">
            <span>已有 <strong>50,000+</strong> 用户选择信任我们</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-container">
        <div class="footer-main">
          <div class="footer-brand">
            <div class="logo">
              <div class="logo-icon">
                <svg viewBox="0 0 40 40" fill="none">
                  <path d="M20 4L36 12V28L20 36L4 28V12L20 4Z" stroke="currentColor" stroke-width="2" fill="none" />
                  <path d="M20 4V36M4 12L36 28M36 12L4 28" stroke="currentColor" stroke-width="1.5" opacity="0.5" />
                  <circle cx="20" cy="20" r="6" fill="currentColor" />
                </svg>
              </div>
              <span class="logo-text">智课问答</span>
            </div>
            <p class="footer-desc">基于AI的智能课程问答系统，让学习更高效、更智能。</p>
            <div class="social-links">
              <a href="#" class="social-link">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path
                    d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z" />
                </svg>
              </a>
              <a href="#" class="social-link">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path
                    d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0112 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z" />
                </svg>
              </a>
              <a href="#" class="social-link">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <path
                    d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433c-1.144 0-2.063-.926-2.063-2.065 0-1.138.92-2.063 2.063-2.063 1.14 0 2.064.925 2.064 2.063 0 1.139-.925 2.065-2.064 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z" />
                </svg>
              </a>
            </div>
          </div>
          <div class="footer-links">
            <div class="link-group">
              <h4>产品</h4>
              <a href="#">功能介绍</a>
              <a href="#">价格方案</a>
              <a href="#">更新日志</a>
              <a href="#">API文档</a>
            </div>
            <div class="link-group">
              <h4>资源</h4>
              <a href="#">帮助中心</a>
              <a href="#">使用教程</a>
              <a href="#">常见问题</a>
              <a href="#">社区论坛</a>
            </div>
            <div class="link-group">
              <h4>公司</h4>
              <a href="#">关于我们</a>
              <a href="#">加入我们</a>
              <a href="#">联系方式</a>
              <a href="#">新闻动态</a>
            </div>
            <div class="link-group">
              <h4>法律</h4>
              <a href="#">用户协议</a>
              <a href="#">隐私政策</a>
              <a href="#">Cookie政策</a>
            </div>
          </div>
        </div>
        <div class="footer-bottom">
          <p>© 2024 智课问答. 保留所有权利.</p>
          <div class="footer-badges">
            <span class="badge">🔒 数据安全</span>
            <span class="badge">⚡ 高性能</span>
            <span class="badge">🌍 全球可用</span>
          </div>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, h } from 'vue'
import { useRouter } from 'vue-router'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import AOS from 'aos'
import 'aos/dist/aos.css'
import Lenis from 'lenis'

// 注册 GSAP 插件
gsap.registerPlugin(ScrollTrigger)

const router = useRouter()

// Refs
const landingRef = ref(null)
const cursorRef = ref(null)
const heroRef = ref(null)
const layer1 = ref(null)
const layer2 = ref(null)
const layer3 = ref(null)
const featuresRef = ref(null)
const featureCircle1 = ref(null)
const featureCircle2 = ref(null)
const showcaseRef = ref(null)
const showcaseScreen = ref(null)
const showcaseEl1 = ref(null)
const showcaseEl2 = ref(null)
const showcaseEl3 = ref(null)
const statsRef = ref(null)
const statNums = ref([])
const testimonialsRef = ref(null)
const testimonialsSlider = ref(null)
const ctaRef = ref(null)
const ctaParticles = ref(null)

// State
const isScrolled = ref(false)
const isMobileMenuOpen = ref(false)
const activeTab = ref(0)

// Lenis 实例
let lenis = null

// 功能特性数据
const features = [
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z' })
    ]),
    title: '智能对话',
    desc: '基于大语言模型，支持多轮对话，精准理解上下文语境，提供最相关的答案。',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('circle', { cx: '11', cy: '11', r: '8' }),
      h('path', { d: 'M21 21l-4.35-4.35' })
    ]),
    title: '知识检索',
    desc: '海量课程知识库，支持语义搜索，快速定位所需知识点，学习效率提升300%。',
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' })
    ]),
    title: '知识图谱',
    desc: '可视化知识网络，清晰展示概念关联，构建完整知识体系，告别碎片化学习。',
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2' }),
      h('circle', { cx: '9', cy: '7', r: '4' }),
      h('path', { d: 'M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75' })
    ]),
    title: '学习社区',
    desc: '与志同道合的学习者交流，分享学习心得，互相解答疑惑，共同进步成长。',
    gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z' }),
      h('path', { d: 'M14 2v6h6M16 13H8M16 17H8M10 9H8' })
    ]),
    title: '错题分析',
    desc: 'AI智能分析错题原因，推荐针对性练习，弱点突破更高效，成绩提升看得见。',
    gradient: 'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M12 20V10M18 20V4M6 20v-4' })
    ]),
    title: '学习分析',
    desc: '多维度数据分析，追踪学习进度，可视化学习报告，让进步清晰可见。',
    gradient: 'linear-gradient(135deg, #d299c2 0%, #fef9d7 100%)'
  }
]

// 产品展示数据
const showcaseTabs = [
  {
    label: '智能问答',
    title: '对话式智能问答',
    description: '自然语言交互，支持文字、图片、语音多种输入方式，获得精准答案。',
    image: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAwIiBoZWlnaHQ9IjUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZGVmcz48bGluZWFyR3JhZGllbnQgaWQ9ImJnIiB4MT0iMCUiIHkxPSIwJSIgeDI9IjEwMCUiIHkyPSIxMDAlIj48c3RvcCBvZmZzZXQ9IjAlIiBzdHlsZT0ic3RvcC1jb2xvcjojMWExYzJjIi8+PHN0b3Agb2Zmc2V0PSIxMDAlIiBzdHlsZT0ic3RvcC1jb2xvcjojMmQzNDQ4Ii8+PC9saW5lYXJHcmFkaWVudD48L2RlZnM+PHJlY3Qgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgZmlsbD0idXJsKCNiZykiLz48cmVjdCB4PSI1MCIgeT0iNTAiIHdpZHRoPSI3MDAiIGhlaWdodD0iNDAwIiByeD0iMTIiIGZpbGw9IiMyODJjMzQiLz48cmVjdCB4PSI3MCIgeT0iMTAwIiB3aWR0aD0iNDAwIiBoZWlnaHQ9IjUwIiByeD0iOCIgZmlsbD0iIzM2M2I0OCIvPjxyZWN0IHg9IjcwIiB5PSIxNzAiIHdpZHRoPSI1MDAiIGhlaWdodD0iNjAiIHJ4PSI4IiBmaWxsPSIjNjY3ZWVhIiBvcGFjaXR5PSIwLjMiLz48cmVjdCB4PSI3MCIgeT0iMjUwIiB3aWR0aD0iNTUwIiBoZWlnaHQ9IjgwIiByeD0iOCIgZmlsbD0iIzRmYWNmZSIgb3BhY2l0eT0iMC4yIi8+PGNpcmNsZSBjeD0iNjgwIiBjeT0iMTI1IiByPSIyNSIgZmlsbD0iIzY2N2VlYSIvPjx0ZXh0IHg9IjQwMCIgeT0iMzgwIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiIgZm9udC1zaXplPSIyNCIgZmlsbD0iIzg4OCIgdGV4dC1hbmNob3I9Im1pZGRsZSI+5pm66IO96Zeu562U55WM6Z2iPC90ZXh0Pjwvc3ZnPg==',
    features: ['多轮对话记忆', '上下文理解', '多模态输入', '实时流式响应']
  },
  {
    label: '知识图谱',
    title: '可视化知识网络',
    description: '将零散知识点串联成网络，直观展示概念间的关联关系。',
    image: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAwIiBoZWlnaHQ9IjUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZGVmcz48bGluZWFyR3JhZGllbnQgaWQ9ImJnMiIgeDE9IjAlIiB5MT0iMCUiIHgyPSIxMDAlIiB5Mj0iMTAwJSI+PHN0b3Agb2Zmc2V0PSIwJSIgc3R5bGU9InN0b3AtY29sb3I6IzBmMGYyMyIvPjxzdG9wIG9mZnNldD0iMTAwJSIgc3R5bGU9InN0b3AtY29sb3I6IzFhMWEzNSIvPjwvbGluZWFyR3JhZGllbnQ+PC9kZWZzPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9InVybCgjYmcyKSIvPjxsaW5lIHgxPSIyMDAiIHkxPSIxNTAiIHgyPSI0MDAiIHkyPSIyNTAiIHN0cm9rZT0iIzRmYWNmZSIgc3Ryb2tlLXdpZHRoPSIyIiBvcGFjaXR5PSIwLjUiLz48bGluZSB4MT0iNDAwIiB5MT0iMjUwIiB4Mj0iNjAwIiB5Mj0iMTUwIiBzdHJva2U9IiM2NjdlZWEiIHN0cm9rZS13aWR0aD0iMiIgb3BhY2l0eT0iMC41Ii8+PGxpbmUgeDE9IjQwMCIgeTE9IjI1MCIgeDI9IjQwMCIgeTI9IjQwMCIgc3Ryb2tlPSIjZjA5M2ZiIiBzdHJva2Utd2lkdGg9IjIiIG9wYWNpdHk9IjAuNSIvPjxsaW5lIHgxPSIyMDAiIHkxPSIxNTAiIHgyPSIzMDAiIHkyPSIzNTAiIHN0cm9rZT0iI2ZhNzA5YSIgc3Ryb2tlLXdpZHRoPSIyIiBvcGFjaXR5PSIwLjUiLz48Y2lyY2xlIGN4PSIyMDAiIGN5PSIxNTAiIHI9IjQwIiBmaWxsPSIjNGZhY2ZlIi8+PGNpcmNsZSBjeD0iNjAwIiBjeT0iMTUwIiByPSI0MCIgZmlsbD0iIzY2N2VlYSIvPjxjaXJjbGUgY3g9IjQwMCIgY3k9IjI1MCIgcj0iNTAiIGZpbGw9IiNmMDkzZmIiLz48Y2lyY2xlIGN4PSI0MDAiIGN5PSI0MDAiIHI9IjM1IiBmaWxsPSIjZmE3MDlhIi8+PGNpcmNsZSBjeD0iMzAwIiBjeT0iMzUwIiByPSIzMCIgZmlsbD0iI2ZlZTE0MCIvPjx0ZXh0IHg9IjQwMCIgeT0iNDcwIiBmb250LWZhbWlseT0ic2Fucy1zZXJpZiIgZm9udC1zaXplPSIyMCIgZmlsbD0iIzY2NiIgdGV4dC1hbmNob3I9Im1pZGRsZSI+55+l6K+G5Zu+6LCx5Y+v6KeG5YyWPC90ZXh0Pjwvc3ZnPg==',
    features: ['概念关联可视化', '学习路径规划', '知识点标签', '一键跳转学习']
  },
  {
    label: '学习分析',
    title: '数据驱动学习',
    description: '全方位追踪学习数据，生成个性化学习报告，科学规划学习路径。',
    image: 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAwIiBoZWlnaHQ9IjUwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZGVmcz48bGluZWFyR3JhZGllbnQgaWQ9ImJnMyIgeDE9IjAlIiB5MT0iMCUiIHgyPSIxMDAlIiB5Mj0iMTAwJSI+PHN0b3Agb2Zmc2V0PSIwJSIgc3R5bGU9InN0b3AtY29sb3I6IzFhMWMyYyIvPjxzdG9wIG9mZnNldD0iMTAwJSIgc3R5bGU9InN0b3AtY29sb3I6IzJkMzQ0OCIvPjwvbGluZWFyR3JhZGllbnQ+PC9kZWZzPjxyZWN0IHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiIGZpbGw9InVybCgjYmczKSIvPjxyZWN0IHg9IjEwMCIgeT0iMzAwIiB3aWR0aD0iNjAiIGhlaWdodD0iMTIwIiByeD0iNCIgZmlsbD0iIzRmYWNmZSIvPjxyZWN0IHg9IjIwMCIgeT0iMjIwIiB3aWR0aD0iNjAiIGhlaWdodD0iMjAwIiByeD0iNCIgZmlsbD0iIzY2N2VlYSIvPjxyZWN0IHg9IjMwMCIgeT0iMTgwIiB3aWR0aD0iNjAiIGhlaWdodD0iMjQwIiByeD0iNCIgZmlsbD0iI2YwOTNmYiIvPjxyZWN0IHg9IjQwMCIgeT0iMTQwIiB3aWR0aD0iNjAiIGhlaWdodD0iMjgwIiByeD0iNCIgZmlsbD0iI2ZhNzA5YSIvPjxyZWN0IHg9IjUwMCIgeT0iMTAwIiB3aWR0aD0iNjAiIGhlaWdodD0iMzIwIiByeD0iNCIgZmlsbD0iI2ZlZTE0MCIvPjxyZWN0IHg9IjYwMCIgeT0iODAiIHdpZHRoPSI2MCIgaGVpZ2h0PSIzNDAiIHJ4PSI0IiBmaWxsPSIjYThlZGVhIi8+PHRleHQgeD0iNDAwIiB5PSI0NzAiIGZvbnQtZmFtaWx5PSJzYW5zLXNlcmlmIiBmb250LXNpemU9IjIwIiBmaWxsPSIjNjY2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj7lrabkuaDmlbDmja7liIbmnpA8L3RleHQ+PC9zdmc+',
    features: ['学习时长统计', '进度可视化', '弱点分析', '目标追踪']
  }
]

// 统计数据
const statsData = [
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2' }),
      h('circle', { cx: '9', cy: '7', r: '4' }),
      h('path', { d: 'M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75' })
    ]),
    value: '50,000',
    suffix: '+',
    name: '注册用户',
    color: '#667eea'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z' })
    ]),
    value: '1,000,000',
    suffix: '+',
    name: '问答记录',
    color: '#f093fb'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5' })
    ]),
    value: '500',
    suffix: '+',
    name: '课程覆盖',
    color: '#4facfe'
  },
  {
    icon: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M14 9V5a3 3 0 00-3-3l-4 9v11h11.28a2 2 0 002-1.7l1.38-9a2 2 0 00-2-2.3zM7 22H4a2 2 0 01-2-2v-7a2 2 0 012-2h3' })
    ]),
    value: '98',
    suffix: '%',
    name: '用户满意度',
    color: '#fa709a'
  }
]

// 用户评价数据
const testimonials = [
  {
    content: '智课问答彻底改变了我的学习方式，AI助手能够精准理解我的问题，给出的解答既专业又易懂。复习效率提升了至少一倍！',
    name: '张明远',
    role: '计算机科学专业学生',
    avatarColor: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  },
  {
    content: '作为一名教师，我经常推荐学生使用这个平台。它的知识图谱功能帮助学生建立系统的知识框架，而不是碎片化记忆。',
    name: '李晓婷',
    role: '大学讲师',
    avatarColor: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
  },
  {
    content: '错题分析功能太棒了！系统能准确识别我的薄弱环节，并推荐针对性练习。数学成绩从70分提升到了90分！',
    name: '王思涵',
    role: '高中生',
    avatarColor: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)'
  },
  {
    content: '团队版的协作功能非常实用，学习小组可以共享资料、讨论问题，还能追踪每个人的学习进度。',
    name: '陈建国',
    role: '学习小组组长',
    avatarColor: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)'
  }
]

// 导航方法
const goToLogin = () => {
  router.push('/login')
}

const goToRegister = () => {
  router.push('/register')
}

const playDemo = () => {
  // 播放演示视频逻辑
  console.log('Play demo')
}

const contactUs = () => {
  // 联系我们逻辑
  console.log('Contact us')
}

const scrollToFeatures = () => {
  document.getElementById('features').scrollIntoView({ behavior: 'smooth' })
}

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value
}

const closeMobileMenu = () => {
  isMobileMenuOpen.value = false
}

// 初始化 Lenis 平滑滚动
const initLenis = () => {
  lenis = new Lenis({
    duration: 1.2,
    easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
    direction: 'vertical',
    gestureDirection: 'vertical',
    smooth: true,
    smoothTouch: false,
    touchMultiplier: 2
  })

  function raf(time) {
    lenis.raf(time)
    requestAnimationFrame(raf)
  }
  requestAnimationFrame(raf)

  // 同步 Lenis 和 ScrollTrigger
  lenis.on('scroll', ScrollTrigger.update)
}

// 初始化滚动监听
const initScrollListener = () => {
  window.addEventListener('scroll', () => {
    isScrolled.value = window.scrollY > 50
  })
}

// 初始化自定义光标
const initCustomCursor = () => {
  if (window.innerWidth <= 768) return

  const cursor = cursorRef.value
  if (!cursor) return

  document.addEventListener('mousemove', (e) => {
    gsap.to(cursor, {
      x: e.clientX,
      y: e.clientY,
      duration: 0.3,
      ease: 'power2.out'
    })
  })

  // 鼠标悬浮效果
  const interactiveElements = document.querySelectorAll('a, button, .feature-card')
  interactiveElements.forEach(el => {
    el.addEventListener('mouseenter', () => {
      cursor.classList.add('hover')
    })
    el.addEventListener('mouseleave', () => {
      cursor.classList.remove('hover')
    })
  })
}

// 初始化 Hero 视差效果
const initHeroParallax = () => {
  if (!layer1.value || !layer2.value || !layer3.value) return

  gsap.to(layer1.value, {
    y: 200,
    ease: 'none',
    scrollTrigger: {
      trigger: heroRef.value,
      start: 'top top',
      end: 'bottom top',
      scrub: 1
    }
  })

  gsap.to(layer2.value, {
    y: 100,
    ease: 'none',
    scrollTrigger: {
      trigger: heroRef.value,
      start: 'top top',
      end: 'bottom top',
      scrub: 1.5
    }
  })

  gsap.to(layer3.value, {
    y: 50,
    ease: 'none',
    scrollTrigger: {
      trigger: heroRef.value,
      start: 'top top',
      end: 'bottom top',
      scrub: 2
    }
  })
}

// 初始化 Features 视差效果
const initFeaturesParallax = () => {
  if (!featureCircle1.value || !featureCircle2.value) return

  gsap.to(featureCircle1.value, {
    x: 100,
    y: -50,
    ease: 'none',
    scrollTrigger: {
      trigger: featuresRef.value,
      start: 'top bottom',
      end: 'bottom top',
      scrub: 1
    }
  })

  gsap.to(featureCircle2.value, {
    x: -100,
    y: 100,
    ease: 'none',
    scrollTrigger: {
      trigger: featuresRef.value,
      start: 'top bottom',
      end: 'bottom top',
      scrub: 1.5
    }
  })
}

// 初始化 Showcase 视差效果
const initShowcaseParallax = () => {
  if (!showcaseEl1.value || !showcaseEl2.value || !showcaseEl3.value) return

  gsap.to(showcaseEl1.value, {
    rotation: 360,
    ease: 'none',
    scrollTrigger: {
      trigger: showcaseRef.value,
      start: 'top bottom',
      end: 'bottom top',
      scrub: 2
    }
  })

  gsap.to(showcaseEl2.value, {
    y: -150,
    ease: 'none',
    scrollTrigger: {
      trigger: showcaseRef.value,
      start: 'top bottom',
      end: 'bottom top',
      scrub: 1
    }
  })

  gsap.to(showcaseEl3.value, {
    scale: 1.5,
    opacity: 0.5,
    ease: 'none',
    scrollTrigger: {
      trigger: showcaseRef.value,
      start: 'top bottom',
      end: 'bottom top',
      scrub: 1.5
    }
  })
}

// 初始化数字动画
const initStatsAnimation = () => {
  ScrollTrigger.create({
    trigger: statsRef.value,
    start: 'top 80%',
    onEnter: () => {
      // 数字增长动画可以在这里添加
    }
  })
}

// 生命周期
onMounted(() => {
  // 初始化 AOS
  AOS.init({
    duration: 800,
    easing: 'ease-out-cubic',
    once: true,
    offset: 50
  })

  // 初始化各种效果
  initLenis()
  initScrollListener()
  initCustomCursor()
  initHeroParallax()
  initFeaturesParallax()
  initShowcaseParallax()
  initStatsAnimation()
})

onUnmounted(() => {
  // 清理
  if (lenis) {
    lenis.destroy()
  }
  ScrollTrigger.getAll().forEach(trigger => trigger.kill())
})
</script>

<style lang="scss" scoped>
// 变量定义
$primary: #667eea;
$primary-dark: #5a6fd6;
$secondary: #764ba2;
$accent: #f093fb;
$accent-alt: #f5576c;
$cyan: #4facfe;
$cyan-light: #00f2fe;
$coral: #fa709a;
$yellow: #fee140;
$mint: #a8edea;
$pink-light: #fed6e3;

$dark-bg: #0a0b14;
$dark-surface: #12131f;
$dark-card: #1a1b2e;
$dark-border: rgba(255, 255, 255, 0.08);

$text-primary: #ffffff;
$text-secondary: rgba(255, 255, 255, 0.7);
$text-muted: rgba(255, 255, 255, 0.5);

$gradient-primary: linear-gradient(135deg, $primary 0%, $secondary 100%);
$gradient-accent: linear-gradient(135deg, $accent 0%, $accent-alt 100%);
$gradient-cyan: linear-gradient(135deg, $cyan 0%, $cyan-light 100%);

// 基础样式
.landing-page {
  background: $dark-bg;
  color: $text-primary;
  min-height: 100vh;
  overflow-x: hidden;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

// 自定义光标
.custom-cursor {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 9999;
  mix-blend-mode: difference;

  @media (max-width: 768px) {
    display: none;
  }

  .cursor-dot {
    width: 8px;
    height: 8px;
    background: white;
    border-radius: 50%;
    transform: translate(-50%, -50%);
    transition: transform 0.15s ease;
  }

  .cursor-ring {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 40px;
    height: 40px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-radius: 50%;
    transform: translate(-50%, -50%);
    transition: all 0.3s ease;
  }

  &.hover {
    .cursor-dot {
      transform: translate(-50%, -50%) scale(2);
    }

    .cursor-ring {
      width: 60px;
      height: 60px;
      border-color: $primary;
    }
  }
}

// 导航栏
.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  padding: 1rem 2rem;
  transition: all 0.3s ease;

  &.scrolled {
    background: rgba(10, 11, 20, 0.9);
    backdrop-filter: blur(20px);
    border-bottom: 1px solid $dark-border;
    padding: 0.75rem 2rem;
  }

  .nav-container {
    max-width: 1400px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .logo {
    display: flex;
    align-items: center;
    gap: 0.75rem;

    .logo-icon {
      width: 40px;
      height: 40px;
      color: $primary;
    }

    .logo-text {
      font-size: 1.25rem;
      font-weight: 700;
      background: $gradient-primary;
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
  }

  .nav-links {
    display: flex;
    gap: 2rem;

    @media (max-width: 968px) {
      display: none;
    }

    .nav-link {
      color: $text-secondary;
      text-decoration: none;
      font-size: 0.95rem;
      font-weight: 500;
      transition: color 0.3s ease;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        bottom: -4px;
        left: 0;
        width: 0;
        height: 2px;
        background: $gradient-primary;
        transition: width 0.3s ease;
      }

      &:hover {
        color: $text-primary;

        &::after {
          width: 100%;
        }
      }
    }
  }

  .nav-actions {
    display: flex;
    gap: 1rem;

    @media (max-width: 968px) {
      display: none;
    }
  }

  .mobile-menu-btn {
    display: none;
    flex-direction: column;
    gap: 5px;
    background: none;
    border: none;
    cursor: pointer;
    padding: 5px;

    @media (max-width: 968px) {
      display: flex;
    }

    span {
      width: 24px;
      height: 2px;
      background: $text-primary;
      transition: all 0.3s ease;
    }
  }
}

// 移动端菜单
.mobile-menu {
  position: fixed;
  top: 70px;
  left: 0;
  right: 0;
  background: rgba(10, 11, 20, 0.98);
  backdrop-filter: blur(20px);
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  transform: translateY(-100%);
  opacity: 0;
  visibility: hidden;
  transition: all 0.3s ease;
  z-index: 999;

  &.active {
    transform: translateY(0);
    opacity: 1;
    visibility: visible;
  }

  a {
    color: $text-secondary;
    text-decoration: none;
    font-size: 1.1rem;
    font-weight: 500;
    padding: 0.5rem 0;
    border-bottom: 1px solid $dark-border;
    transition: color 0.3s ease;

    &:hover {
      color: $text-primary;
    }
  }

  .mobile-actions {
    display: flex;
    gap: 1rem;
    margin-top: 1rem;
  }
}

// 按钮样式
.btn-ghost {
  background: transparent;
  border: 1px solid $dark-border;
  color: $text-primary;
  padding: 0.6rem 1.5rem;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    border-color: $primary;
    color: $primary;
  }
}

.btn-primary {
  background: $gradient-primary;
  border: none;
  color: white;
  padding: 0.6rem 1.5rem;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
  }
}

// Hero 区域
.hero {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8rem 2rem 4rem;
  position: relative;
  overflow: hidden;

  .hero-bg {
    position: absolute;
    inset: 0;
    overflow: hidden;

    .parallax-layer {
      position: absolute;
      inset: 0;
    }

    .layer-1 {
      .floating-shape {
        position: absolute;
        border-radius: 50%;
        filter: blur(60px);
        opacity: 0.6;

        &.shape-1 {
          width: 400px;
          height: 400px;
          background: $primary;
          top: 10%;
          left: 10%;
          animation: float 8s ease-in-out infinite;
        }

        &.shape-2 {
          width: 300px;
          height: 300px;
          background: $secondary;
          top: 50%;
          right: 15%;
          animation: float 10s ease-in-out infinite reverse;
        }

        &.shape-3 {
          width: 250px;
          height: 250px;
          background: $accent;
          bottom: 20%;
          left: 30%;
          animation: float 12s ease-in-out infinite;
        }
      }
    }

    .layer-2 {
      .grid-lines {
        position: absolute;
        inset: 0;
        background-image:
          linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
          linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
        background-size: 50px 50px;
      }
    }

    .layer-3 {
      .glow-orb {
        position: absolute;
        border-radius: 50%;
        filter: blur(80px);

        &.orb-1 {
          width: 200px;
          height: 200px;
          background: $cyan;
          opacity: 0.3;
          top: 20%;
          right: 10%;
        }

        &.orb-2 {
          width: 150px;
          height: 150px;
          background: $coral;
          opacity: 0.3;
          bottom: 30%;
          left: 5%;
        }

        &.orb-3 {
          width: 180px;
          height: 180px;
          background: $mint;
          opacity: 0.2;
          top: 60%;
          right: 30%;
        }
      }
    }
  }

  .hero-content {
    position: relative;
    z-index: 2;
    text-align: center;
    max-width: 800px;

    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(102, 126, 234, 0.15);
      border: 1px solid rgba(102, 126, 234, 0.3);
      padding: 0.5rem 1rem;
      border-radius: 50px;
      font-size: 0.9rem;
      color: $primary;
      margin-bottom: 1.5rem;

      .badge-icon {
        font-size: 1rem;
      }
    }

    .hero-title {
      font-size: clamp(2.5rem, 6vw, 4.5rem);
      font-weight: 800;
      line-height: 1.1;
      margin-bottom: 1.5rem;

      .title-line {
        display: block;
      }

      .gradient-text {
        background: $gradient-primary;
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }
    }

    .hero-subtitle {
      font-size: 1.2rem;
      color: $text-secondary;
      line-height: 1.8;
      margin-bottom: 2rem;
    }

    .hero-actions {
      display: flex;
      gap: 1rem;
      justify-content: center;
      flex-wrap: wrap;
      margin-bottom: 3rem;
    }

    .hero-stats {
      display: flex;
      justify-content: center;
      gap: 2rem;
      flex-wrap: wrap;

      .stat-item {
        text-align: center;

        .stat-number {
          display: block;
          font-size: 1.75rem;
          font-weight: 700;
          color: $text-primary;
        }

        .stat-label {
          font-size: 0.9rem;
          color: $text-muted;
        }
      }

      .stat-divider {
        width: 1px;
        height: 40px;
        background: $dark-border;
      }
    }
  }

  .hero-visual {
    position: absolute;
    right: 5%;
    top: 50%;
    transform: translateY(-50%);
    z-index: 1;

    @media (max-width: 1200px) {
      display: none;
    }

    .visual-container {
      position: relative;
      width: 500px;
      height: 400px;
    }

    .browser-mockup {
      width: 100%;
      background: $dark-card;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 30px 60px rgba(0, 0, 0, 0.5);

      .browser-header {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.75rem 1rem;
        background: rgba(0, 0, 0, 0.3);
        border-bottom: 1px solid $dark-border;

        .browser-dots {
          display: flex;
          gap: 6px;

          span {
            width: 12px;
            height: 12px;
            border-radius: 50%;

            &:nth-child(1) {
              background: #ff5f57;
            }

            &:nth-child(2) {
              background: #febc2e;
            }

            &:nth-child(3) {
              background: #28c840;
            }
          }
        }

        .browser-url {
          flex: 1;
          text-align: center;
          font-size: 0.85rem;
          color: $text-muted;
        }
      }

      .browser-content {
        padding: 1.5rem;
        min-height: 250px;

        .chat-demo {
          display: flex;
          flex-direction: column;
          gap: 1rem;

          .chat-message {
            display: flex;
            gap: 0.75rem;

            &.user {
              justify-content: flex-end;

              .message-content {
                background: $gradient-primary;
                color: white;
              }
            }

            &.ai {
              .ai-avatar {
                width: 36px;
                height: 36px;
                background: $gradient-cyan;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 0.75rem;
                font-weight: 700;
                flex-shrink: 0;
              }

              .message-content {
                background: rgba(255, 255, 255, 0.1);
              }
            }

            .message-content {
              padding: 0.75rem 1rem;
              border-radius: 12px;
              font-size: 0.9rem;
              max-width: 300px;
            }
          }

          .typing-indicator {
            display: flex;
            gap: 4px;
            margin-bottom: 0.5rem;

            span {
              width: 6px;
              height: 6px;
              background: $text-muted;
              border-radius: 50%;
              animation: typing 1.4s ease-in-out infinite;

              &:nth-child(2) {
                animation-delay: 0.2s;
              }

              &:nth-child(3) {
                animation-delay: 0.4s;
              }
            }
          }
        }
      }
    }

    .floating-card {
      position: absolute;
      background: $dark-card;
      border: 1px solid $dark-border;
      padding: 1rem 1.25rem;
      border-radius: 12px;
      display: flex;
      align-items: center;
      gap: 0.75rem;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
      animation: floatCard 4s ease-in-out infinite;

      .card-icon {
        font-size: 1.5rem;
      }

      .card-text {
        font-size: 0.9rem;
        font-weight: 500;
      }

      &.card-1 {
        top: -20px;
        left: -60px;
        animation-delay: 0s;
      }

      &.card-2 {
        top: 50%;
        right: -80px;
        animation-delay: 1s;
      }

      &.card-3 {
        bottom: 20px;
        left: -40px;
        animation-delay: 2s;
      }
    }
  }

  .scroll-indicator {
    position: absolute;
    bottom: 2rem;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.75rem;
    cursor: pointer;
    opacity: 0.7;
    transition: opacity 0.3s ease;

    &:hover {
      opacity: 1;
    }

    .mouse {
      width: 26px;
      height: 40px;
      border: 2px solid $text-secondary;
      border-radius: 13px;
      position: relative;

      .wheel {
        width: 4px;
        height: 8px;
        background: $text-secondary;
        border-radius: 2px;
        position: absolute;
        top: 6px;
        left: 50%;
        transform: translateX(-50%);
        animation: scroll 2s ease-in-out infinite;
      }
    }

    span {
      font-size: 0.8rem;
      color: $text-muted;
    }
  }
}

// Hero 按钮
.btn-hero-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  background: $gradient-primary;
  border: none;
  color: white;
  padding: 1rem 2rem;
  border-radius: 12px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    width: 20px;
    height: 20px;
    transition: transform 0.3s ease;
  }

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 15px 40px rgba(102, 126, 234, 0.5);

    svg {
      transform: translateX(5px);
    }
  }
}

.btn-hero-secondary {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid $dark-border;
  color: $text-primary;
  padding: 1rem 2rem;
  border-radius: 12px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    width: 20px;
    height: 20px;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    border-color: $primary;
  }
}

// Section 通用样式
.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem;
}

.section-header {
  text-align: center;
  margin-bottom: 4rem;

  .section-tag {
    display: inline-block;
    background: rgba(102, 126, 234, 0.15);
    color: $primary;
    padding: 0.4rem 1rem;
    border-radius: 50px;
    font-size: 0.85rem;
    font-weight: 500;
    margin-bottom: 1rem;
  }

  .section-title {
    font-size: clamp(2rem, 4vw, 3rem);
    font-weight: 700;
    margin-bottom: 1rem;
    color: $text-primary;
  }

  .section-desc {
    font-size: 1.1rem;
    color: $text-secondary;
    max-width: 600px;
    margin: 0 auto;
    line-height: 1.7;
  }
}

// Features 区域
.features {
  padding: 8rem 0;
  position: relative;

  .features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 2rem;
  }

  .feature-card {
    background: $dark-card;
    border: 1px solid $dark-border;
    border-radius: 16px;
    padding: 2rem;
    position: relative;
    overflow: hidden;
    transition: all 0.4s ease;

    &:hover {
      transform: translateY(-10px);
      border-color: rgba(102, 126, 234, 0.3);
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);

      .feature-hover-effect {
        opacity: 1;
      }
    }

    .feature-icon {
      width: 60px;
      height: 60px;
      border-radius: 14px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1.5rem;

      svg {
        width: 28px;
        height: 28px;
        color: white;
      }
    }

    .feature-title {
      font-size: 1.25rem;
      font-weight: 600;
      margin-bottom: 0.75rem;
      color: $text-primary;
    }

    .feature-desc {
      font-size: 0.95rem;
      color: $text-secondary;
      line-height: 1.7;
    }

    .feature-hover-effect {
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, transparent 100%);
      opacity: 0;
      transition: opacity 0.4s ease;
    }
  }

  .features-bg {
    position: absolute;
    inset: 0;
    pointer-events: none;
    overflow: hidden;

    .bg-circle {
      position: absolute;
      border-radius: 50%;
      filter: blur(100px);

      &.circle-1 {
        width: 500px;
        height: 500px;
        background: $primary;
        opacity: 0.1;
        top: 0;
        left: -200px;
      }

      &.circle-2 {
        width: 400px;
        height: 400px;
        background: $accent;
        opacity: 0.1;
        bottom: 0;
        right: -150px;
      }
    }
  }
}

// Showcase 区域
.showcase {
  padding: 8rem 0;
  position: relative;
  background: linear-gradient(180deg, $dark-bg 0%, $dark-surface 100%);

  .showcase-tabs {
    display: flex;
    justify-content: center;
    gap: 1rem;
    margin-bottom: 3rem;
    flex-wrap: wrap;

    .tab-btn {
      background: transparent;
      border: 1px solid $dark-border;
      color: $text-secondary;
      padding: 0.75rem 1.5rem;
      border-radius: 50px;
      font-size: 0.95rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.3s ease;

      &:hover {
        border-color: $primary;
        color: $primary;
      }

      &.active {
        background: $gradient-primary;
        border-color: transparent;
        color: white;
      }
    }
  }

  .showcase-content {
    display: grid;
    grid-template-columns: 1.5fr 1fr;
    gap: 4rem;
    align-items: center;

    @media (max-width: 968px) {
      grid-template-columns: 1fr;
      gap: 2rem;
    }
  }

  .showcase-screen {
    position: relative;

    .screen-glow {
      position: absolute;
      inset: -20px;
      background: $gradient-primary;
      filter: blur(60px);
      opacity: 0.3;
      border-radius: 24px;
    }

    .screen-frame {
      position: relative;
      background: $dark-card;
      border: 1px solid $dark-border;
      border-radius: 16px;
      overflow: hidden;
      box-shadow: 0 30px 60px rgba(0, 0, 0, 0.4);

      img {
        width: 100%;
        display: block;
      }
    }
  }

  .showcase-info {
    h3 {
      font-size: 1.75rem;
      font-weight: 700;
      margin-bottom: 1rem;
      color: $text-primary;
    }

    p {
      font-size: 1.05rem;
      color: $text-secondary;
      line-height: 1.7;
      margin-bottom: 1.5rem;
    }

    .showcase-features {
      list-style: none;
      padding: 0;
      margin: 0;

      li {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem 0;
        color: $text-secondary;
        font-size: 0.95rem;

        svg {
          width: 20px;
          height: 20px;
          color: $cyan;
          flex-shrink: 0;
        }
      }
    }
  }

  .showcase-parallax {
    position: absolute;
    inset: 0;
    pointer-events: none;
    overflow: hidden;

    .parallax-element {
      position: absolute;
      border-radius: 50%;

      &.el-1 {
        width: 100px;
        height: 100px;
        border: 2px solid rgba(102, 126, 234, 0.2);
        top: 20%;
        left: 10%;
      }

      &.el-2 {
        width: 60px;
        height: 60px;
        background: rgba(240, 147, 251, 0.2);
        bottom: 30%;
        right: 10%;
      }

      &.el-3 {
        width: 80px;
        height: 80px;
        background: rgba(79, 172, 254, 0.1);
        top: 60%;
        left: 5%;
      }
    }
  }
}

// Stats 区域
.stats {
  padding: 8rem 0;
  position: relative;

  .stats-bg {
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);

    .stats-grid-lines {
      position: absolute;
      inset: 0;
      background-image:
        linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
        linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
      background-size: 40px 40px;
    }
  }

  .stats-content {
    position: relative;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4rem;
    align-items: center;

    @media (max-width: 968px) {
      grid-template-columns: 1fr;
      text-align: center;
    }
  }

  .stats-text {

    .section-tag,
    .section-title,
    .section-desc {
      text-align: left;

      @media (max-width: 968px) {
        text-align: center;
      }
    }

    .section-desc {
      margin: 0 0 2rem 0;
    }
  }

  .btn-stats {
    display: inline-flex;
    align-items: center;
    gap: 0.75rem;
    background: $gradient-primary;
    border: none;
    color: white;
    padding: 0.875rem 2rem;
    border-radius: 10px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 15px 40px rgba(102, 126, 234, 0.4);
    }
  }

  .stats-numbers {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 1.5rem;
  }

  .stat-card {
    background: $dark-card;
    border: 1px solid $dark-border;
    border-radius: 16px;
    padding: 1.5rem;
    text-align: center;
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-5px);
      border-color: rgba(102, 126, 234, 0.3);
    }

    .stat-icon {
      margin-bottom: 1rem;

      svg {
        width: 32px;
        height: 32px;
      }
    }

    .stat-value {
      display: flex;
      align-items: baseline;
      justify-content: center;
      gap: 0.25rem;
      margin-bottom: 0.5rem;

      .stat-num {
        font-size: 2rem;
        font-weight: 700;
        color: $text-primary;
      }

      .stat-suffix {
        font-size: 1.25rem;
        font-weight: 600;
        color: $primary;
      }
    }

    .stat-name {
      font-size: 0.9rem;
      color: $text-muted;
    }
  }
}

// Testimonials 区域
.testimonials {
  padding: 8rem 0;
  position: relative;
  overflow: hidden;

  .testimonials-slider {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 2rem;
  }

  .testimonial-card {
    background: $dark-card;
    border: 1px solid $dark-border;
    border-radius: 16px;
    padding: 2rem;
    transition: all 0.3s ease;

    &:hover {
      transform: translateY(-5px);
      border-color: rgba(102, 126, 234, 0.3);
    }

    .testimonial-content {
      position: relative;
      margin-bottom: 1.5rem;

      .quote-icon {
        font-size: 4rem;
        font-family: Georgia, serif;
        color: $primary;
        opacity: 0.3;
        position: absolute;
        top: -1rem;
        left: -0.5rem;
        line-height: 1;
      }

      p {
        position: relative;
        font-size: 1rem;
        color: $text-secondary;
        line-height: 1.8;
        padding-left: 1rem;
      }
    }

    .testimonial-author {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1rem;

      .author-avatar {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 1.25rem;
        font-weight: 600;
        color: white;
      }

      .author-info {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;

        .author-name {
          font-size: 1rem;
          font-weight: 600;
          color: $text-primary;
        }

        .author-role {
          font-size: 0.85rem;
          color: $text-muted;
        }
      }
    }

    .testimonial-rating {
      display: flex;
      gap: 0.25rem;

      .star {
        color: $yellow;
        font-size: 1rem;
      }
    }
  }

  .testimonials-bg {
    position: absolute;
    inset: 0;
    pointer-events: none;

    .bg-gradient {
      position: absolute;
      width: 600px;
      height: 600px;
      background: radial-gradient(circle, rgba(102, 126, 234, 0.15) 0%, transparent 70%);
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
    }
  }
}

// CTA 区域
.cta {
  padding: 8rem 0;
  position: relative;
  overflow: hidden;

  .cta-bg {
    position: absolute;
    inset: 0;
    background: $gradient-primary;

    .cta-particles {
      position: absolute;
      inset: 0;
      background-image:
        radial-gradient(circle at 20% 80%, rgba(255, 255, 255, 0.1) 1px, transparent 1px),
        radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.1) 1px, transparent 1px),
        radial-gradient(circle at 40% 40%, rgba(255, 255, 255, 0.05) 2px, transparent 2px);
      background-size: 100px 100px, 150px 150px, 200px 200px;
    }
  }

  .cta-content {
    position: relative;
    text-align: center;
    max-width: 700px;
    margin: 0 auto;

    .cta-title {
      font-size: clamp(2rem, 4vw, 2.75rem);
      font-weight: 700;
      color: white;
      margin-bottom: 1rem;
    }

    .cta-desc {
      font-size: 1.15rem;
      color: rgba(255, 255, 255, 0.85);
      margin-bottom: 2rem;
    }

    .cta-actions {
      display: flex;
      gap: 1rem;
      justify-content: center;
      flex-wrap: wrap;
      margin-bottom: 2rem;
    }

    .cta-trust {
      font-size: 0.95rem;
      color: rgba(255, 255, 255, 0.7);

      strong {
        color: white;
      }
    }
  }
}

.btn-cta-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  background: white;
  border: none;
  color: $primary;
  padding: 1rem 2rem;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    width: 20px;
    height: 20px;
    transition: transform 0.3s ease;
  }

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 15px 40px rgba(0, 0, 0, 0.3);

    svg {
      transform: translateX(5px);
    }
  }
}

.btn-cta-secondary {
  display: inline-flex;
  align-items: center;
  background: transparent;
  border: 2px solid rgba(255, 255, 255, 0.5);
  color: white;
  padding: 1rem 2rem;
  border-radius: 10px;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: white;
  }
}

// Footer
.footer {
  background: $dark-surface;
  border-top: 1px solid $dark-border;
  padding: 4rem 0 2rem;

  .footer-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 2rem;
  }

  .footer-main {
    display: grid;
    grid-template-columns: 1.5fr 2fr;
    gap: 4rem;
    margin-bottom: 3rem;

    @media (max-width: 768px) {
      grid-template-columns: 1fr;
      gap: 2rem;
    }
  }

  .footer-brand {
    .logo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    .footer-desc {
      font-size: 0.95rem;
      color: $text-secondary;
      line-height: 1.7;
      margin-bottom: 1.5rem;
    }

    .social-links {
      display: flex;
      gap: 1rem;

      .social-link {
        width: 40px;
        height: 40px;
        border: 1px solid $dark-border;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: $text-secondary;
        transition: all 0.3s ease;

        svg {
          width: 18px;
          height: 18px;
        }

        &:hover {
          border-color: $primary;
          color: $primary;
          transform: translateY(-3px);
        }
      }
    }
  }

  .footer-links {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 2rem;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }

    .link-group {
      h4 {
        font-size: 1rem;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 1rem;
      }

      a {
        display: block;
        font-size: 0.9rem;
        color: $text-muted;
        text-decoration: none;
        padding: 0.4rem 0;
        transition: color 0.3s ease;

        &:hover {
          color: $primary;
        }
      }
    }
  }

  .footer-bottom {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 2rem;
    border-top: 1px solid $dark-border;

    @media (max-width: 768px) {
      flex-direction: column;
      gap: 1rem;
      text-align: center;
    }

    p {
      font-size: 0.9rem;
      color: $text-muted;
    }

    .footer-badges {
      display: flex;
      gap: 1rem;

      .badge {
        font-size: 0.85rem;
        color: $text-secondary;
      }
    }
  }
}

// 动画关键帧
@keyframes float {

  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-30px);
  }
}

@keyframes floatCard {

  0%,
  100% {
    transform: translateY(0);
  }

  50% {
    transform: translateY(-15px);
  }
}

@keyframes scroll {
  0% {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }

  100% {
    opacity: 0;
    transform: translateX(-50%) translateY(15px);
  }
}

@keyframes typing {

  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>