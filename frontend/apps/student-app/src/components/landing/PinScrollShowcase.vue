<!-- 特性横向钉滚 · 详见设计稿 §7.1 动效 ② -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const wrapperRef = ref(null)
const trackRef = ref(null)
let trigger = null

const features = [
  { key: 'qa', title: '智能问答', subtitle: 'AI + 知识图谱，精准解答每一个课程问题', color: '#9333ea' },
  { key: 'kg', title: '知识图谱', subtitle: '可视化学科脉络，让知识连成网', color: '#0d9488' },
  { key: 'learn', title: '沉浸学习', subtitle: '边看边问，笔记与提问一体', color: '#2563eb' },
]

onMounted(() => {
  const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (prefersReduced) return

  const wrapper = wrapperRef.value
  const track = trackRef.value
  if (!wrapper || !track) return

  // 横向滚动距离 = 轨道总宽 - 视窗宽
  const distance = () => track.scrollWidth - window.innerWidth

  trigger = gsap.to(track, {
    x: () => -distance(),
    ease: 'none',
    scrollTrigger: {
      trigger: wrapper,
      start: 'top top',
      end: () => `+=${distance()}`,
      scrub: 0.5,
      pin: true,
      anticipatePin: 1,
      invalidateOnRefresh: true,
    },
  })
})

onBeforeUnmount(() => {
  trigger?.scrollTrigger?.kill()
  trigger?.kill()
})
</script>

<template>
  <section id="showcase" ref="wrapperRef" class="showcase-wrapper">
    <div ref="trackRef" class="showcase-track">
      <div
        v-for="(f, i) in features"
        :key="f.key"
        class="showcase-panel"
        :style="{ '--accent': f.color }"
      >
        <div class="panel-inner">
          <div class="panel-index">0{{ i + 1 }}</div>
          <h2 class="panel-title">{{ f.title }}</h2>
          <p class="panel-subtitle">{{ f.subtitle }}</p>
          <div class="panel-glow" :style="{ background: `radial-gradient(circle, ${f.color} 0%, transparent 70%)` }"></div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.showcase-wrapper {
  height: 100vh;
  overflow: hidden;
  background: #0f0f1a;
  position: relative;
}

.showcase-track {
  display: flex;
  height: 100%;
  will-change: transform;
}

.showcase-panel {
  flex: 0 0 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  .panel-inner {
    position: relative;
    text-align: center;
    padding: 0 32px;
    max-width: 800px;
  }

  .panel-glow {
    position: absolute;
    inset: -100px;
    opacity: 0.25;
    filter: blur(60px);
    z-index: 0;
    pointer-events: none;
  }

  .panel-index {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 96px;
    font-weight: 700;
    color: var(--accent);
    opacity: 0.15;
    line-height: 1;
    margin-bottom: 16px;
    position: relative;
  }

  .panel-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 64px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 16px;
    letter-spacing: -0.02em;
    position: relative;
  }

  .panel-subtitle {
    font-size: 18px;
    color: rgba(255, 255, 255, 0.65);
    position: relative;
  }
}
</style>
