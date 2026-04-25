<!-- 我的收藏 · 收藏列表视觉壳 -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import userMock from '@/mock/user.json'

const favorites = userMock.favorites
</script>

<template>
  <div class="fav-page">
    <h1 class="page-title">我的收藏</h1>

    <div class="fav-grid">
      <GlassCard
        v-for="f in favorites"
        :key="f.id"
        tier="light"
        padding="md"
        :hover="true"
        class="fav-card"
      >
        <ModuleTag :module="f.type === 'course' ? 'course' : 'qa'" size="sm">
          {{ f.type === 'course' ? '课程' : '问答' }}
        </ModuleTag>
        <div class="title">{{ f.title }}</div>
      </GlassCard>
    </div>

    <p class="coming-hint">收藏完整筛选和管理在后续迭代补齐</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.fav-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 14px;
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }

  .fav-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    cursor: pointer;

    .title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }
  }
}

.coming-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  margin-top: 16px;
}
</style>
