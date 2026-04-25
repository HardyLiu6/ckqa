<!-- 账号设置 · 骨架级视觉壳 -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
</script>

<template>
  <div class="user-skeleton-page">
    <h1 class="page-title">账号设置</h1>

    <GlassCard tier="base" padding="lg">
      <div v-for="i in 4" :key="i" class="setting-row">
        <div class="row-left">
          <SkeletonBlock width="160px" height="14px" />
          <SkeletonBlock width="240px" height="10px" />
        </div>
        <SkeletonBlock width="48px" height="24px" rounded="full" />
      </div>
      <div class="coming-hint">账号设置的具体表单设计在后续迭代补齐</div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.user-skeleton-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f1f5f9;

  &:last-of-type { border-bottom: 0; }

  .row-left {
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
