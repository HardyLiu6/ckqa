<!-- 知识图谱模块副导航 · 青色系 -->
<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Connection, Search } from '@element-plus/icons-vue'

const route = useRoute()

const items = [
  { path: '/knowledge/graph', label: '图谱浏览', icon: Connection },
  { path: '/knowledge/search', label: '知识检索', icon: Search },
]

const activePath = computed(() => route.path)

const subjects = ref([
  { id: 'os', label: 'OS', selected: true },
  { id: 'algo', label: '算法', selected: false },
  { id: 'ds', label: '数据结构', selected: false },
])

const legends = [
  { color: '#0d9488', label: '概念' },
  { color: '#2dd4bf', label: '实例' },
  { color: '#f59e0b', label: '错题' },
]

function toggleSubject(s) {
  s.selected = !s.selected
}
</script>

<template>
  <nav class="side-nav knowledge-side-nav">
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="nav-link"
      :class="{ active: activePath === item.path }"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>

    <div class="section-label">学科</div>
    <div class="subjects">
      <button
        v-for="s in subjects"
        :key="s.id"
        class="subject-chip"
        :class="{ selected: s.selected }"
        @click="toggleSubject(s)"
      >{{ s.label }}</button>
    </div>

    <div class="section-label">关系</div>
    <div class="legends">
      <div v-for="l in legends" :key="l.label" class="legend-item">
        <span class="legend-dot" :style="{ background: l.color }"></span>
        <span>{{ l.label }}</span>
      </div>
    </div>
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

  &:hover { background: rgba(13, 148, 136, 0.05); color: #334155; }

  &.active {
    background: rgba(13, 148, 136, 0.1);
    color: #0d9488;
    font-weight: 600;
  }
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 12px 10px 4px;
}

.subjects {
  display: flex;
  gap: 6px;
  padding: 0 4px;
  flex-wrap: wrap;

  .subject-chip {
    padding: 3px 10px;
    background: rgba(13, 148, 136, 0.06);
    border: 1px solid rgba(13, 148, 136, 0.15);
    color: #0d9488;
    font-size: 11px;
    border-radius: $radius-full;
    cursor: pointer;
    transition: background $duration-fast $ease-out, border-color $duration-fast $ease-out;

    &.selected {
      background: rgba(13, 148, 136, 0.15);
      border-color: rgba(13, 148, 136, 0.35);
      font-weight: 600;
    }
  }
}

.legends {
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #475569;
  }
  .legend-dot {
    width: 8px; height: 8px; border-radius: 50%;
  }
}
</style>
