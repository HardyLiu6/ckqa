<!-- 知识检索 · 骨架级视觉壳 -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
</script>

<template>
  <div class="kg-search-page">
    <header class="page-head">
      <h1 class="page-title">知识检索</h1>
      <p class="page-sub">搜索学科中的任意知识点</p>
    </header>

    <GlassCard tier="base" padding="md" class="search-card">
      <div class="search-input">
        <span class="icon">🔍</span>
        <input placeholder="输入知识点名称、关键词或概念" />
      </div>
      <div class="skeleton-wrap">
        <div v-for="i in 4" :key="i" class="skel-row">
          <SkeletonBlock width="48px" height="48px" rounded="md" />
          <div class="skel-lines">
            <SkeletonBlock width="60%" height="14px" />
            <SkeletonBlock width="85%" height="10px" />
          </div>
        </div>
      </div>
      <div class="coming-hint">功能完善中 · 本次仅提供视觉稿</div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.kg-search-page {
  --module-color-500: #0d9488;
  padding: 24px;
}

.page-head {
  margin-bottom: 16px;
  .page-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 26px;
    font-weight: 800;
    color: #0f172a;
  }
  .page-sub { font-size: 14px; color: #64748b; margin-top: 4px; }
}

.search-card {
  border-color: rgba(13, 148, 136, 0.2) !important;
}

.search-input {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: $radius-lg;
  color: #9ca3af;
  margin-bottom: 16px;

  input {
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    font-family: inherit;
    font-size: 14px;
  }
}

.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .skel-row {
    display: flex;
    gap: 12px;
    align-items: center;
  }
  .skel-lines {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
}

.coming-hint {
  margin-top: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
</style>
