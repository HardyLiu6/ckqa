<template>
  <header class="nav-header">
    <div class="nav-content">
      <div class="logo-section">
        <div class="logo-icon">
          <el-icon :size="28">
            <ChatDotRound />
          </el-icon>
        </div>
        <span class="logo-text">智课问答</span>
      </div>

      <nav class="nav-menu">
        <router-link to="/home" class="nav-item" :class="{ active: isActive('/home') }">首页</router-link>
        <router-link to="/course" class="nav-item" :class="{ active: isActive('/course') }">课程中心</router-link>
        <router-link to="/community" class="nav-item" :class="{ active: isActive('/community') }">学习社区</router-link>
        <router-link to="/knowledge" class="nav-item" :class="{ active: isActive('/knowledge') }">知识库</router-link>
      </nav>

      <div class="nav-actions">
        <el-input v-model="globalSearch" placeholder="搜索课程、问题..." class="global-search" :prefix-icon="Search"
          clearable />
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
          <el-button :icon="Bell" circle class="icon-btn" />
        </el-badge>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="user-avatar">
            <el-avatar :size="36" :src="userStore.avatar">
              {{ userStore.nickname?.charAt(0) || 'U' }}
            </el-avatar>
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

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Bell, ChatDotRound, Search } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const globalSearch = ref('')
const unreadCount = ref(3)

const currentPath = computed(() => router.currentRoute.value.path)
const isActive = (path) => currentPath.value.startsWith(path)

const handleUserCommand = (command) => {
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
</script>

<style scoped lang="scss">
$primary-color: #4f46e5;
$secondary-color: #0ea5e9;
$radius-sm: 6px;
$radius-md: 12px;
$radius-lg: 16px;

.nav-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: rgba(15, 15, 26, 0.9);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .nav-content {
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 32px;
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .logo-section {
    display: flex;
    align-items: center;
    gap: 10px;

    .logo-icon {
      width: 40px;
      height: 40px;
      background: linear-gradient(135deg, $primary-color, $secondary-color);
      border-radius: $radius-md;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
    }

    .logo-text {
      font-size: 20px;
      font-weight: 700;
      background: linear-gradient(135deg, #6366f1, #06b6d4);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
  }

  .nav-menu {
    display: flex;
    gap: 8px;

    .nav-item {
      padding: 8px 20px;
      color: rgba(255, 255, 255, 0.7);
      text-decoration: none;
      font-size: 15px;
      border-radius: $radius-sm;
      transition: all 0.2s;

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.1);
      }

      &.active {
        color: #fff;
        font-weight: 600;
      }
    }
  }

  .nav-actions {
    display: flex;
    align-items: center;
    gap: 16px;

    .global-search {
      width: 240px;

      :deep(.el-input__wrapper) {
        border-radius: $radius-lg;
        background: rgba(255, 255, 255, 0.1);
        box-shadow: none;
        border: 1px solid rgba(255, 255, 255, 0.1);

        .el-input__inner {
          color: #fff;

          &::placeholder {
            color: rgba(255, 255, 255, 0.5);
          }
        }

        &:hover,
        &:focus {
          box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.5);
        }
      }
    }

    .icon-btn {
      border: none;
      background: rgba(255, 255, 255, 0.1);
      color: rgba(255, 255, 255, 0.7);

      &:hover {
        background: rgba(99, 102, 241, 0.3);
        color: #fff;
      }
    }

    .user-avatar {
      cursor: pointer;
      transition: transform 0.2s;

      &:hover {
        transform: scale(1.05);
      }
    }
  }
}
</style>
