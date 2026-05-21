<!-- 全站顶栏 · 居中胶囊导航 + 激活态荧光 · 详见设计稿 §5.2 -->
<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCurrentModule, MODULE_COLORS } from '@/composables/useCurrentModule'
import { Bell, ChatDotRound, Search } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const { moduleKey } = useCurrentModule()

const globalSearch = ref('')
const unreadCount = ref(3)
const isScrolled = ref(false)
const avatarLoadFailed = ref(false)

// 顶栏主导航项
const modules = [
  { key: 'home', path: '/home', label: '首页' },
  { key: 'course', path: '/course', label: '课程' },
  { key: 'qa', path: '/qa', label: '问答' },
  { key: 'knowledge', path: '/knowledge', label: '图谱' },
  { key: 'community', path: '/community', label: '社区' },
  { key: 'analysis', path: '/analysis', label: '分析' },
]

const activeModule = computed(() => moduleKey.value)
const avatarUrl = computed(() => userStore.user?.avatarUrl || '')
const userInitial = computed(() => userStore.user?.name?.trim()?.charAt(0) || 'U')

function isActive(key) {
  return activeModule.value === key
}

function itemStyle(key) {
  if (!isActive(key)) return {}
  const c = MODULE_COLORS[key] || MODULE_COLORS.home
  return {
    background: '#fff',
    color: c[500],
    boxShadow: `0 1px 3px rgba(${hexToRgb(c[500])}, 0.1), 0 0 0 1px rgba(${hexToRgb(c[500])}, 0.15), 0 0 16px rgba(${hexToRgb(c[500])}, 0.15)`,
  }
}

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  return `${parseInt(h.slice(0, 2), 16)}, ${parseInt(h.slice(2, 4), 16)}, ${parseInt(h.slice(4, 6), 16)}`
}

function handleUserCommand(command) {
  switch (command) {
    case 'profile':
      router.push('/user/profile')
      break
    case 'settings':
      router.push('/user/settings')
      break
    case 'logout':
      userStore.logout()
      router.push('/login')
      break
  }
}

// 滚动监听：scrollY > 80 时背景收浓
function handleScroll() {
  isScrolled.value = window.scrollY > 80
}

watch(avatarUrl, () => {
  avatarLoadFailed.value = false
})

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <header class="nav-header" :class="{ scrolled: isScrolled }">
    <div class="nav-grid">
      <!-- 左：Logo -->
      <div class="nav-left">
        <RouterLink to="/home" class="logo-section">
          <img class="logo-img" src="/logo.png" alt="智课问答" />
          <span class="logo-text">智课问答</span>
        </RouterLink>
      </div>

      <!-- 中：模块胶囊导航 -->
      <nav class="nav-center">
        <RouterLink
          v-for="m in modules"
          :key="m.key"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.key) }"
          :style="itemStyle(m.key)"
        >
          {{ m.label }}
        </RouterLink>
      </nav>

      <!-- 右：搜索 + 通知 + 头像 -->
      <div class="nav-right">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input v-model="globalSearch" placeholder="搜索课程、问题或知识点" />
          <kbd class="shortcut">⌘K</kbd>
        </div>

        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notify-badge">
          <button class="icon-btn" aria-label="通知">
            <el-icon :size="16"><Bell /></el-icon>
          </button>
        </el-badge>

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="avatar">
            <img
              v-if="avatarUrl && !avatarLoadFailed"
              :src="avatarUrl"
              :alt="`${userStore.user?.name || '用户'}头像`"
              @error="avatarLoadFailed = true"
            />
            <span v-else>{{ userInitial }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="settings">设置</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/shadow' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.nav-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 64px;
  @include glass.glass-base;
  background: rgba(255, 255, 255, 0.8);
  border-left: 0;
  border-right: 0;
  border-top: 0;
  border-bottom-color: rgba(229, 231, 235, 0.6);
  transition: background $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &.scrolled {
    background: rgba(255, 255, 255, 0.95);
    box-shadow: $shadow-sm;
  }
}

.nav-grid {
  height: 100%;
  padding: 0 32px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  max-width: 1600px;
  margin: 0 auto;

  @media (max-width: $bp-laptop) {
    padding: 0 20px;
  }
}

.nav-left { justify-self: start; }
.nav-center { justify-self: center; }
.nav-right { justify-self: end; display: flex; align-items: center; gap: 10px; }

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;

  .logo-img {
    width: 36px;
    height: 36px;
    border-radius: $radius-lg;
    object-fit: contain;
  }

  .logo-text {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
}

.nav-center {
  display: flex;
  gap: 4px;
  background: rgba(248, 250, 252, 0.6);
  border: 1px solid rgba(229, 231, 235, 0.4);
  border-radius: $radius-full;
  padding: 4px;

  .nav-item {
    padding: 6px 14px;
    color: #64748b;
    font-size: 13px;
    font-weight: 500;
    border-radius: $radius-full;
    transition: color $duration-fast $ease-out, background $duration-fast $ease-out,
      box-shadow $duration-fast $ease-out;

    &:hover:not(.active) {
      color: #334155;
      background: rgba(255, 255, 255, 0.5);
    }

    &.active {
      font-weight: 600;
    }
  }

  @media (max-width: $bp-laptop) {
    .nav-item { padding: 6px 10px; font-size: 12px; }
  }
}

.nav-right {
  .search-box {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    background: rgba(255, 255, 255, 0.85);
    border: 1px solid rgba(226, 232, 240, 0.9);
    border-radius: 999px;
    min-width: 220px;
    transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

    .search-icon { color: #94a3b8; font-size: 14px; }

    input {
      flex: 1;
      border: 0;
      outline: 0;
      background: transparent;
      font-size: 13px;
      color: #0f172a;
      font-family: inherit;

      &::placeholder { color: #94a3b8; }
    }

    .shortcut {
      font-size: 10px;
      padding: 2px 6px;
      background: #f1f5f9;
      border-radius: 4px;
      color: #64748b;
      font-family: 'JetBrains Mono', monospace;
    }

    &:focus-within {
      border-color: rgba(147, 51, 234, 0.4);
      box-shadow: 0 0 0 3px rgba(147, 51, 234, 0.08);
    }

    @media (max-width: $bp-laptop) {
      min-width: 160px;
    }
  }

  .icon-btn {
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.85);
    border: 1px solid rgba(226, 232, 240, 0.9);
    border-radius: 50%;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #64748b;
    transition: background $duration-fast $ease-out, color $duration-fast $ease-out, border-color $duration-fast $ease-out;

    &:hover {
      background: #fff;
      color: #0f172a;
      border-color: rgba(147, 51, 234, 0.3);
    }
  }

  .avatar {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    border-radius: 50%;
    color: #fff;
    font-weight: 700;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
    transition: transform $duration-fast $ease-out;
    position: relative;
    overflow: hidden;

    &:hover { transform: scale(1.05); }

    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    span {
      position: relative;
      z-index: 1;
    }
  }
}

// 小屏：隐藏搜索框，只保留搜索图标
@media (max-width: $bp-tablet) {
  .search-box { display: none; }
}
</style>
