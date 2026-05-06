<!-- 个人资料主页 · 可视化编辑表单 + 统计卡 -->
<script setup>
import { computed, ref, watch } from 'vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import { useUserStore } from '@/stores/user'
import userMock from '@/mock/user.json'

const userStore = useUserStore()
const profile = ref(createProfile(userStore.user))
const stats = userMock.stats
const avatarInput = ref(null)
const avatarLoadFailed = ref(false)
const avatarUploading = ref(false)
const avatarProgress = ref(0)
const avatarError = ref('')

const avatarUrl = computed(() => userStore.user?.avatarUrl || '')
const avatarInitial = computed(() => profile.value.name?.trim()?.charAt(0) || 'U')

watch(() => userStore.user, (value) => {
  profile.value = createProfile(value)
}, { deep: true, immediate: true })

watch(avatarUrl, () => {
  avatarLoadFailed.value = false
})

function createProfile(user = {}) {
  return {
    id: user.userCode || user.id || userMock.profile.id,
    name: user.displayName || user.name || user.username || userMock.profile.name,
    studentNo: user.userCode || userMock.profile.studentNo,
    college: user.department || userMock.profile.college,
    major: user.major || userMock.profile.major,
    email: user.email || userMock.profile.email,
    bio: user.bio || userMock.profile.bio,
  }
}

function triggerAvatarSelect() {
  avatarInput.value?.click()
}

async function handleAvatarSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  const message = validateAvatarFile(file)
  if (message) {
    avatarError.value = message
    return
  }

  avatarUploading.value = true
  avatarProgress.value = 0
  avatarError.value = ''
  try {
    await userStore.uploadAvatar(file, (progressEvent) => {
      if (progressEvent.total) {
        avatarProgress.value = Math.min(100, Math.round((progressEvent.loaded / progressEvent.total) * 100))
      }
    })
    avatarProgress.value = 100
  } catch (error) {
    avatarError.value = error?.message || '头像上传失败'
  } finally {
    avatarUploading.value = false
  }
}

function validateAvatarFile(file) {
  const supportedTypes = new Set(['image/png', 'image/jpeg', 'image/webp'])
  if (!supportedTypes.has(file.type)) {
    return '头像仅支持 PNG、JPG 或 WEBP 格式'
  }
  if (file.size > 2 * 1024 * 1024) {
    return '头像文件不能超过 2MB'
  }
  return ''
}

function saveProfile() {
  userStore.setUser({
    displayName: profile.value.name,
    email: profile.value.email,
    bio: profile.value.bio,
  })
}
</script>

<template>
  <div class="profile-page">
    <header class="page-head">
      <h1 class="page-title">个人资料</h1>
      <GlowButton size="sm" @click="saveProfile">保存</GlowButton>
    </header>

    <!-- 基本信息卡 -->
    <GlassCard tier="base" padding="lg" class="info-card">
      <div class="avatar-row">
        <div class="avatar-wrap">
          <div class="avatar">
            <img
              v-if="avatarUrl && !avatarLoadFailed"
              :src="avatarUrl"
              :alt="`${profile.name}头像`"
              @error="avatarLoadFailed = true"
            />
            <span v-else>{{ avatarInitial }}</span>
          </div>
          <button
            class="avatar-btn"
            type="button"
            :disabled="avatarUploading"
            title="更换头像"
            @click="triggerAvatarSelect"
          >
            更换
          </button>
          <input
            ref="avatarInput"
            class="avatar-input"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="handleAvatarSelected"
          />
        </div>
        <div>
          <div class="name">{{ profile.name }}</div>
          <div class="meta">ID · {{ profile.id }}</div>
          <div v-if="avatarUploading || avatarProgress > 0" class="avatar-progress">
            <span>{{ avatarUploading ? '头像上传中' : '头像已更新' }}</span>
            <el-progress :percentage="avatarProgress" :stroke-width="5" />
          </div>
          <p v-if="avatarError" class="avatar-error">{{ avatarError }}</p>
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
    flex-shrink: 0;

    .avatar {
      position: relative;
      display: grid;
      width: 64px;
      height: 64px;
      place-items: center;
      overflow: hidden;
      background: linear-gradient(135deg, #64748b, #94a3b8);
      border-radius: $radius-2xl;
      color: #fff;
      font-size: 20px;
      font-weight: 800;
      box-shadow: 0 10px 24px rgba(100, 116, 139, 0.18);

      img {
        position: absolute;
        inset: 0;
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }
    .avatar-btn {
      position: absolute;
      right: -10px;
      bottom: -6px;
      min-width: 42px;
      height: 26px;
      padding: 0 8px;
      background: #fff;
      border: 2px solid #e5e7eb;
      border-radius: $radius-full;
      cursor: pointer;
      color: #334155;
      font-size: 11px;
      font-weight: 700;
      box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);

      &:disabled {
        cursor: wait;
        opacity: 0.68;
      }
    }
  }
  .name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
  .meta { font-size: 12px; color: #64748b; margin-top: 2px; }
}

.avatar-input {
  display: none;
}

.avatar-progress {
  display: grid;
  gap: 4px;
  width: min(260px, 64vw);
  margin-top: 8px;

  span {
    color: #64748b;
    font-size: 11px;
    font-weight: 600;
  }
}

.avatar-error {
  margin: 6px 0 0;
  color: #dc2626;
  font-size: 12px;
  font-weight: 600;
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
