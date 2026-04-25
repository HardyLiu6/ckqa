<!-- 个人资料主页 · 可视化编辑表单 + 统计卡 -->
<script setup>
import { ref } from 'vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import userMock from '@/mock/user.json'

const profile = ref({ ...userMock.profile })
const stats = userMock.stats
</script>

<template>
  <div class="profile-page">
    <header class="page-head">
      <h1 class="page-title">个人资料</h1>
      <GlowButton size="sm">保存</GlowButton>
    </header>

    <!-- 基本信息卡 -->
    <GlassCard tier="base" padding="lg" class="info-card">
      <div class="avatar-row">
        <div class="avatar-wrap">
          <div class="avatar"></div>
          <button class="avatar-btn" title="更换头像">📷</button>
        </div>
        <div>
          <div class="name">{{ profile.name }}</div>
          <div class="meta">ID · {{ profile.id }}</div>
        </div>
      </div>

      <div class="form-grid">
        <div class="field">
          <label>昵称</label>
          <input v-model="profile.name" />
        </div>
        <div class="field">
          <label>学号</label>
          <input :value="profile.studentNo" disabled />
        </div>
        <div class="field">
          <label>学院</label>
          <input :value="profile.college" disabled />
        </div>
        <div class="field">
          <label>专业</label>
          <input :value="profile.major" disabled />
        </div>
        <div class="field">
          <label>邮箱</label>
          <input v-model="profile.email" />
        </div>
        <div class="field field-wide">
          <label>个人简介</label>
          <textarea v-model="profile.bio" rows="2"></textarea>
        </div>
      </div>
    </GlassCard>

    <!-- 统计卡 -->
    <div class="stats-grid">
      <GlassCard tier="light" padding="md" class="stat stat-course">
        <div class="label">已学课程</div>
        <div class="num blue">
          {{ stats.coursesLearned }}<span class="sub">/{{ stats.coursesTotal }}</span>
        </div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-qa">
        <div class="label">提问次数</div>
        <div class="num purple">{{ stats.qaCount }}</div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-streak">
        <div class="label">连续学习</div>
        <div class="num pink">
          {{ stats.streakDays }}<span class="sub">天</span>
        </div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-fav">
        <div class="label">收藏</div>
        <div class="num lemon">{{ stats.favorites }}</div>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.profile-page {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .page-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 800;
    color: #0f172a;
  }
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;

  .avatar-wrap {
    position: relative;

    .avatar {
      width: 56px; height: 56px;
      background: linear-gradient(135deg, #64748b, #94a3b8);
      border-radius: $radius-2xl;
    }
    .avatar-btn {
      position: absolute;
      bottom: -4px; right: -4px;
      width: 24px; height: 24px;
      background: #fff;
      border: 2px solid #e5e7eb;
      border-radius: 50%;
      cursor: pointer;
      font-size: 11px;
    }
  }
  .name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
  .meta { font-size: 12px; color: #64748b; margin-top: 2px; }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }

  .field {
    label {
      display: block;
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
      margin-bottom: 4px;
    }
    input, textarea {
      width: 100%;
      padding: 8px 12px;
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: $radius-md;
      font-family: inherit;
      font-size: 13px;
      color: #0f172a;
      outline: none;
      transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

      &:focus {
        border-color: #64748b;
        box-shadow: 0 0 0 3px rgba(100, 116, 139, 0.12);
      }
      &:disabled {
        background: #f8fafc;
        color: #9ca3af;
      }
    }
  }
  .field-wide { grid-column: 1 / -1; }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: repeat(2, 1fr); }

  .stat {
    .label { font-size: 10px; color: #64748b; }
    .num {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 24px;
      font-weight: 700;
      margin-top: 4px;
    }
    .sub {
      font-size: 11px;
      color: #64748b;
      font-weight: 400;
      margin-left: 2px;
    }
    .blue { color: #2563eb; }
    .purple { color: #9333ea; }
    .pink { color: #db2777; }
    .lemon { color: #ca8a04; }
  }
  .stat-course { border-color: rgba(37, 99, 235, 0.15) !important; }
  .stat-qa { border-color: rgba(147, 51, 234, 0.15) !important; }
  .stat-streak { border-color: rgba(219, 39, 119, 0.15) !important; }
  .stat-fav { border-color: rgba(234, 179, 8, 0.2) !important; }
}
</style>
