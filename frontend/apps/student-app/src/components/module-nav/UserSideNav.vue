<!-- 个人中心模块副导航 · 中性灰系 -->
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, Setting, Bell, Star } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()

const items = [
  { path: '/user/profile', label: '个人资料', icon: User, accent: 'neutral' },
  { path: '/user/settings', label: '账号设置', icon: Setting, accent: 'neutral' },
  { path: '/user/notification', label: '消息通知', icon: Bell, accent: 'amber', badge: 12 },
  { path: '/user/favorite', label: '我的收藏', icon: Star, accent: 'lemon' },
]

const activePath = computed(() => route.path)
const avatarLoadFailed = ref(false)

const profile = computed(() => ({
  name: userStore.user?.name || '未登录用户',
  meta: userStore.user?.userCode || userStore.user?.username || '个人中心',
  avatarUrl: userStore.user?.avatarUrl || '',
  initial: (userStore.user?.name || 'U').trim().charAt(0) || 'U',
}))

watch(() => profile.value.avatarUrl, () => {
  avatarLoadFailed.value = false
})
</script>

<template>
  <nav class="side-nav user-side-nav">
    <div class="profile-card">
      <div class="avatar">
        <img
          v-if="profile.avatarUrl && !avatarLoadFailed"
          :src="profile.avatarUrl"
          :alt="`${profile.name}头像`"
          @error="avatarLoadFailed = true"
        />
        <span v-else>{{ profile.initial }}</span>
      </div>
      <div>
        <div class="profile-name">{{ profile.name }}</div>
        <div class="profile-meta">{{ profile.meta }}</div>
      </div>
    </div>

    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="nav-link"
      :class="[{ active: activePath === item.path }, `accent-${item.accent}`]"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <span v-if="item.badge" class="badge">{{ item.badge }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #e5e7eb;
  border-radius: $radius-xl;
  margin-bottom: 8px;

  .avatar {
    position: relative;
    display: grid;
    width: 36px;
    height: 36px;
    place-items: center;
    overflow: hidden;
    background: linear-gradient(135deg, #64748b, #94a3b8);
    border-radius: $radius-lg;
    color: #fff;
    font-size: 13px;
    font-weight: 800;
    flex-shrink: 0;

    img {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
  .profile-name {
    font-size: 13px;
    font-weight: 700;
    color: #0f172a;
  }
  .profile-meta {
    font-size: 11px;
    color: #64748b;
  }
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $radius-md;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover { background: rgba(100, 116, 139, 0.05); color: #0f172a; }

  &.active {
    background: rgba(100, 116, 139, 0.1);
    color: #334155;
    font-weight: 600;
  }

  &.accent-amber .el-icon { color: #f59e0b; }
  &.accent-lemon .el-icon { color: #eab308; }

  .badge {
    margin-left: auto;
    font-size: 10px;
    padding: 1px 6px;
    background: #f59e0b;
    color: #fff;
    border-radius: $radius-full;
    font-weight: 700;
  }
}
</style>
