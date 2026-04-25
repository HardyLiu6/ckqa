<!-- 课程模块副导航 · 蓝色系 -->
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Files, Collection, Star, Document } from '@element-plus/icons-vue'

const route = useRoute()

const items = [
  { path: '/course/list', label: '全部课程', icon: Files },
  { path: '/course/my', label: '我的课程', icon: Collection },
  { path: '/course/favorite', label: '收藏课程', icon: Star, comingSoon: true },
  { path: '/course/report', label: '学习报告', icon: Document, comingSoon: true },
]

const activePath = computed(() => route.path)
</script>

<template>
  <nav class="side-nav course-side-nav">
    <div class="nav-label">课程</div>
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.comingSoon ? '/course/list' : item.path"
      class="nav-link"
      :class="{ active: activePath === item.path, disabled: item.comingSoon }"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <span v-if="item.comingSoon" class="tag-coming">未开放</span>
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
  gap: 2px;
}

.nav-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 6px 10px 10px;
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

  &:hover:not(.disabled) {
    background: rgba(37, 99, 235, 0.05);
    color: #334155;
  }

  &.active {
    background: var(--module-color-50, #eff6ff);
    color: var(--module-color-500, #2563eb);
    font-weight: 600;
  }

  &.disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .tag-coming {
    margin-left: auto;
    font-size: 10px;
    padding: 1px 6px;
    background: #f1f5f9;
    border-radius: $radius-sm;
    color: #94a3b8;
  }
}
</style>
