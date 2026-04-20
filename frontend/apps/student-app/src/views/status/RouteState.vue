<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Clock,
  Compass,
  House,
  Lock,
  Warning,
} from '@element-plus/icons-vue'

import NavHeader from '@/components/NavHeader.vue'

const route = useRoute()
const router = useRouter()

const statePresetMap = {
  'coming-soon': {
    badge: '未开放',
    title: '功能建设中',
    description: '当前学生端原型暂未开放此页面，请先使用已完成的问答与课程功能。',
    icon: Clock,
    accentClass: 'is-coming-soon',
  },
  '403': {
    badge: '403',
    title: '暂无权限访问',
    description: '当前页面需要更完整的权限体系支持，学生端原型暂未开放。',
    icon: Lock,
    accentClass: 'is-forbidden',
  },
  '404': {
    badge: '404',
    title: '页面不存在',
    description: '当前地址没有对应页面，请检查链接是否正确。',
    icon: Compass,
    accentClass: 'is-not-found',
  },
  '500': {
    badge: '500',
    title: '页面暂时不可用',
    description: '页面暂时无法展示，请稍后重试。',
    icon: Warning,
    accentClass: 'is-server-error',
  },
}

const routeState = computed(() => route.meta.routeState || 'coming-soon')
const pageState = computed(() => {
  const preset = statePresetMap[routeState.value] || statePresetMap['coming-soon']
  return {
    badge: route.meta.routeStateLabel || preset.badge,
    title: route.meta.stateTitle || preset.title,
    description: route.meta.stateDescription || preset.description,
    icon: preset.icon,
    accentClass: preset.accentClass,
    primaryActionTarget: route.meta.primaryActionTarget || '/home',
    primaryActionText: route.meta.primaryActionText || '返回首页',
  }
})

const showNav = computed(() => !route.meta.noAuth)

function goPrimary() {
  router.push(pageState.value.primaryActionTarget)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }

  router.push('/')
}
</script>

<template>
  <div class="route-state-page">
    <NavHeader v-if="showNav" />

    <main class="route-state-shell" :class="{ 'with-nav': showNav }">
      <section class="route-state-card" :class="pageState.accentClass">
        <div class="route-state-copy">
          <el-tag effect="dark" round class="route-state-badge">
            {{ pageState.badge }}
          </el-tag>

          <h1 class="route-state-title">{{ pageState.title }}</h1>
          <p class="route-state-description">{{ pageState.description }}</p>

          <div class="route-state-actions">
            <el-button type="primary" size="large" @click="goPrimary">
              <el-icon>
                <House />
              </el-icon>
              {{ pageState.primaryActionText }}
            </el-button>
            <el-button plain size="large" @click="goBack">
              <el-icon>
                <ArrowLeft />
              </el-icon>
              返回上页
            </el-button>
          </div>

          <p class="route-state-path">当前路径：{{ route.fullPath }}</p>
        </div>

        <div class="route-state-visual">
          <div class="route-state-icon-shell">
            <el-icon :size="64">
              <component :is="pageState.icon" />
            </el-icon>
          </div>
          <div class="route-state-halo route-state-halo-a"></div>
          <div class="route-state-halo route-state-halo-b"></div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped lang="scss">
.route-state-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(110, 231, 183, 0.16), transparent 28%),
    radial-gradient(circle at right center, rgba(56, 189, 248, 0.18), transparent 30%),
    linear-gradient(160deg, #051821 0%, #0b2736 52%, #eef6ff 100%);
}

.route-state-shell {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;

  &.with-nav {
    padding-top: 112px;
  }
}

.route-state-card {
  position: relative;
  width: min(1080px, 100%);
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 24px;
  padding: 40px;
  border-radius: 32px;
  background: rgba(6, 24, 34, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 24px 80px rgba(3, 8, 17, 0.35);
  backdrop-filter: blur(18px);
}

.route-state-copy {
  position: relative;
  z-index: 1;
}

.route-state-badge {
  margin-bottom: 18px;
  padding: 0 14px;
  border: none;
  background: rgba(255, 255, 255, 0.14);
}

.route-state-title {
  font-size: clamp(2rem, 4vw, 3.4rem);
  line-height: 1.08;
  color: #f8fbff;
}

.route-state-description {
  max-width: 560px;
  margin-top: 18px;
  font-size: 1.05rem;
  line-height: 1.8;
  color: rgba(232, 242, 255, 0.78);
}

.route-state-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 28px;
}

.route-state-path {
  margin-top: 22px;
  font-size: 0.94rem;
  color: rgba(215, 231, 250, 0.58);
}

.route-state-visual {
  position: relative;
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.route-state-icon-shell {
  position: relative;
  z-index: 1;
  width: 168px;
  height: 168px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 40px;
  color: #f5fbff;
  background: linear-gradient(145deg, rgba(13, 148, 136, 0.72), rgba(2, 132, 199, 0.72));
  box-shadow: 0 18px 48px rgba(14, 116, 144, 0.28);
}

.route-state-halo {
  position: absolute;
  border-radius: 999px;
  filter: blur(4px);
}

.route-state-halo-a {
  width: 220px;
  height: 220px;
  background: rgba(45, 212, 191, 0.18);
  top: 18%;
  left: 20%;
}

.route-state-halo-b {
  width: 140px;
  height: 140px;
  background: rgba(56, 189, 248, 0.22);
  bottom: 16%;
  right: 18%;
}

.is-forbidden .route-state-icon-shell {
  background: linear-gradient(145deg, rgba(245, 158, 11, 0.78), rgba(234, 88, 12, 0.78));
}

.is-not-found .route-state-icon-shell {
  background: linear-gradient(145deg, rgba(59, 130, 246, 0.78), rgba(29, 78, 216, 0.78));
}

.is-server-error .route-state-icon-shell {
  background: linear-gradient(145deg, rgba(244, 63, 94, 0.78), rgba(190, 24, 93, 0.78));
}

@media (max-width: 900px) {
  .route-state-card {
    grid-template-columns: 1fr;
    padding: 32px 24px;
  }

  .route-state-visual {
    order: -1;
    min-height: 220px;
  }

  .route-state-icon-shell {
    width: 132px;
    height: 132px;
    border-radius: 30px;
  }
}

@media (max-width: 600px) {
  .route-state-shell {
    padding: 24px 16px;

    &.with-nav {
      padding-top: 88px;
    }
  }

  .route-state-card {
    border-radius: 24px;
    padding: 24px 18px;
  }

  .route-state-actions {
    flex-direction: column;
  }
}
</style>
