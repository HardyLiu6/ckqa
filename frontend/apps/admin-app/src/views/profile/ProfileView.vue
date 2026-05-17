<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Camera,
  KeyRound,
  Mail,
  Save,
  ShieldCheck,
  Star,
  UserCog,
} from 'lucide-vue-next'

import {
  changeCurrentPassword,
  updateCurrentProfile,
  uploadCurrentAvatar,
} from '../../api/auth.js'
import { authStore } from '../../stores/auth.js'

// 当前用户快照
const currentUser = computed(() => authStore.state.currentUser)

// 显示名草稿
const displayNameDraft = ref(currentUser.value?.displayName ?? '')
const savingProfile = ref(false)
const avatarUploading = ref(false)
const fileInput = ref(null)
const avatarLoadFailed = ref(false)

// 修改密码 form
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const changingPassword = ref(false)

const displayNameDirty = computed(
  () => (displayNameDraft.value ?? '').trim() !== (currentUser.value?.displayName ?? '').trim(),
)

const avatarUrl = computed(() => currentUser.value?.avatarUrl ?? '')
const avatarInitial = computed(() => {
  const name = currentUser.value?.displayName ?? currentUser.value?.username ?? 'U'
  return name.trim().charAt(0) || 'U'
})

const roleLabels = computed(() => {
  const map = { admin: '平台管理员', teacher: '教师', student: '学生' }
  return (currentUser.value?.roles ?? []).map((r) => map[r] ?? r)
})

const lastLoginLabel = computed(() => {
  const v = currentUser.value?.lastLoginAt
  if (!v) return '暂无记录'
  return v
})

const permissionList = computed(() => {
  const ps = currentUser.value?.permissions ?? []
  if (ps.includes('*')) return ['*（管理员超级权限）']
  return ps
})

async function handleSaveDisplayName() {
  const trimmed = displayNameDraft.value.trim()
  if (!trimmed) {
    ElMessage.warning('显示名不能为空')
    return
  }
  if (!displayNameDirty.value) {
    ElMessage.info('未做更改')
    return
  }
  savingProfile.value = true
  try {
    const updated = await updateCurrentProfile({ displayName: trimmed })
    authStore.applyProfile(updated)
    displayNameDraft.value = updated.displayName ?? trimmed
    ElMessage.success('显示名已更新')
  } catch (error) {
    const msg = error?.message || error?.response?.data?.message || '保存失败'
    ElMessage.error(msg)
  } finally {
    savingProfile.value = false
  }
}

function handleAvatarClick() {
  fileInput.value?.click()
}

async function handleAvatarSelected(event) {
  const file = event.target?.files?.[0]
  if (!file) return
  if (!/^image\//i.test(file.type)) {
    ElMessage.warning('仅支持图片格式（PNG / JPG / WebP）')
    event.target.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 5MB')
    event.target.value = ''
    return
  }
  avatarUploading.value = true
  try {
    const updated = await uploadCurrentAvatar(file)
    authStore.applyProfile(updated)
    avatarLoadFailed.value = false
    ElMessage.success('头像已更新')
  } catch (error) {
    const msg = error?.message || error?.response?.data?.message || '头像上传失败'
    ElMessage.error(msg)
  } finally {
    avatarUploading.value = false
    if (event.target) event.target.value = ''
  }
}

function validatePasswordForm() {
  const { oldPassword, newPassword, confirmPassword } = passwordForm.value
  if (!oldPassword) return '请输入原密码'
  if (!newPassword) return '请输入新密码'
  if (newPassword.length < 8 || newPassword.length > 64) return '新密码长度需在 8-64 字符之间'
  if (!/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) return '新密码需同时包含字母与数字'
  if (newPassword === oldPassword) return '新密码不能与原密码相同'
  if (newPassword !== confirmPassword) return '两次输入的新密码不一致'
  return null
}

async function handleChangePassword() {
  const error = validatePasswordForm()
  if (error) {
    ElMessage.warning(error)
    return
  }
  try {
    await ElMessageBox.confirm(
      '修改密码后，下次登录请使用新密码。是否继续？',
      '修改密码',
      { confirmButtonText: '确认修改', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  changingPassword.value = true
  try {
    await changeCurrentPassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    })
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码已更新')
  } catch (error) {
    const msg = error?.message || error?.response?.data?.message || '修改密码失败'
    ElMessage.error(msg)
  } finally {
    changingPassword.value = false
  }
}
</script>

<template>
  <div class="profile-view">
    <header class="profile-view__hero">
      <h2 class="profile-view__hero-title">
        <UserCog :size="24" aria-hidden="true" />
        个人中心
      </h2>
      <p class="profile-view__lead">
        管理你的账号资料、登录密码与权限明细。头像和显示名会同步在顶部菜单展示。
      </p>
    </header>

    <!-- 卡 1：基本信息 -->
    <section class="profile-card">
      <header class="profile-card__header">
        <h3>基本信息</h3>
        <small>头像与显示名会同步在顶部导航栏展示。</small>
      </header>
      <div class="profile-card__body profile-basic">
        <!-- 头像编辑 -->
        <div class="profile-avatar-block">
          <button
            type="button"
            class="profile-avatar"
            :title="'点击更换头像'"
            :aria-label="'更换头像'"
            :disabled="avatarUploading"
            @click="handleAvatarClick"
          >
            <img
              v-if="avatarUrl && !avatarLoadFailed"
              :src="avatarUrl"
              :alt="`${currentUser?.displayName ?? '用户'} 头像`"
              @error="avatarLoadFailed = true"
            />
            <span v-else class="profile-avatar__fallback">{{ avatarInitial }}</span>
            <span class="profile-avatar__overlay" aria-hidden="true">
              <Camera :size="20" />
              <small>{{ avatarUploading ? '上传中…' : '更换头像' }}</small>
            </span>
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            class="profile-avatar__input"
            @change="handleAvatarSelected"
          />
          <small class="profile-avatar__hint">支持 PNG / JPG / WebP，最大 5MB</small>
        </div>

        <!-- 字段网格 -->
        <dl class="profile-meta-grid">
          <div>
            <dt>用户代码</dt>
            <dd>
              <code>{{ currentUser?.userCode || '—' }}</code>
            </dd>
          </div>
          <div>
            <dt>登录用户名</dt>
            <dd>{{ currentUser?.username || '—' }}</dd>
          </div>
          <div>
            <dt>角色</dt>
            <dd class="profile-tag-row">
              <ShieldCheck :size="14" aria-hidden="true" />
              <span v-for="role in roleLabels" :key="role" class="profile-tag">{{ role }}</span>
              <span v-if="!roleLabels.length" class="profile-tag profile-tag--muted">未授予角色</span>
            </dd>
          </div>
          <div>
            <dt>数据范围</dt>
            <dd>{{ currentUser?.dataScope || '—' }}</dd>
          </div>
          <div class="profile-meta-grid__full">
            <dt>最近登录</dt>
            <dd>{{ lastLoginLabel }}</dd>
          </div>
          <div class="profile-meta-grid__full">
            <dt>显示名</dt>
            <dd>
              <div class="profile-display-name-row">
                <el-input
                  v-model="displayNameDraft"
                  maxlength="128"
                  show-word-limit
                  clearable
                  placeholder="请输入显示名"
                  class="profile-display-name-input"
                />
                <el-button
                  type="primary"
                  class="ckqa-el-button ckqa-el-button--primary"
                  :loading="savingProfile"
                  :disabled="!displayNameDirty || savingProfile"
                  @click="handleSaveDisplayName"
                >
                  <Save :size="14" aria-hidden="true" />
                  保存
                </el-button>
              </div>
            </dd>
          </div>
        </dl>
      </div>
    </section>

    <!-- 卡 2：修改密码 -->
    <section class="profile-card">
      <header class="profile-card__header">
        <h3>
          <KeyRound :size="16" aria-hidden="true" />
          修改密码
        </h3>
        <small>新密码需要 8-64 字符，且同时包含字母与数字。</small>
      </header>
      <div class="profile-card__body">
        <div class="profile-password-form">
          <label>
            <span>原密码</span>
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="输入当前登录密码"
            />
          </label>
          <label>
            <span>新密码</span>
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="字母+数字组合，8-64 字符"
            />
          </label>
          <label>
            <span>确认新密码</span>
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="再次输入新密码"
            />
          </label>
          <div class="profile-password-form__actions">
            <el-button
              type="primary"
              class="ckqa-el-button ckqa-el-button--primary"
              :loading="changingPassword"
              @click="handleChangePassword"
            >
              <KeyRound :size="14" aria-hidden="true" />
              更新密码
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- 卡 3：权限明细（只读） -->
    <section class="profile-card">
      <header class="profile-card__header">
        <h3>
          <Star :size="16" aria-hidden="true" />
          权限明细
        </h3>
        <small>由系统自动同步，不可在此页面修改。</small>
      </header>
      <div class="profile-card__body">
        <ul class="profile-permission-list">
          <li v-for="p in permissionList" :key="p">
            <code>{{ p }}</code>
          </li>
          <li v-if="!permissionList.length" class="profile-permission-list__empty">
            <Mail :size="14" aria-hidden="true" />
            暂未分配业务权限
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped>
.profile-view {
  display: grid;
  gap: var(--ckqa-space-5, 20px);
  padding: 0 0 var(--ckqa-space-5, 20px);
  max-width: 980px;
  margin: 0 auto;
  width: 100%;
}

.profile-view__hero {
  display: grid;
  gap: 4px;
}
.profile-view__hero-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  font-weight: 700;
  margin: 0;
  color: var(--ckqa-text);
}
.profile-view__lead {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.profile-card {
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-lg, 14px);
  background: var(--ckqa-surface);
  overflow: hidden;
}

.profile-card__header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--ckqa-border);
  background: color-mix(in srgb, var(--ckqa-surface) 92%, transparent);
}

.profile-card__header h3 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 2px;
}

.profile-card__header small {
  display: block;
  color: var(--ckqa-text-muted);
  font-size: 12px;
}

.profile-card__body {
  padding: 20px;
}

.profile-basic {
  display: grid;
  gap: 24px;
  grid-template-columns: 144px 1fr;
  align-items: start;
}

.profile-avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.profile-avatar {
  position: relative;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--ckqa-border);
  background: color-mix(in srgb, var(--ckqa-accent) 12%, var(--ckqa-surface));
  cursor: pointer;
  padding: 0;
  display: grid;
  place-items: center;
  transition: border-color 0.18s ease, transform 0.18s ease;
}
.profile-avatar:hover { border-color: var(--ckqa-accent); transform: translateY(-1px); }
.profile-avatar:disabled { cursor: progress; opacity: 0.7; }

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar__fallback {
  font-size: 36px;
  font-weight: 800;
  color: var(--ckqa-accent-strong);
}

.profile-avatar__overlay {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  gap: 4px;
  color: white;
  background: linear-gradient(180deg, transparent 40%, rgba(0, 0, 0, 0.55));
  opacity: 0;
  transition: opacity 0.2s ease;
  font-size: 11px;
  pointer-events: none;
}
.profile-avatar:hover .profile-avatar__overlay,
.profile-avatar:disabled .profile-avatar__overlay {
  opacity: 1;
}

.profile-avatar__input { display: none; }

.profile-avatar__hint {
  color: var(--ckqa-text-muted);
  font-size: 11px;
}

.profile-meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 24px;
  margin: 0;
}
.profile-meta-grid__full { grid-column: 1 / -1; }

.profile-meta-grid dt {
  font-size: 12px;
  color: var(--ckqa-text-muted);
  margin-bottom: 4px;
}

.profile-meta-grid dd {
  margin: 0;
  font-size: 14px;
  color: var(--ckqa-text);
  font-weight: 500;
}

.profile-meta-grid code {
  font-family: var(--ckqa-font-mono);
  background: var(--ckqa-surface-muted);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.profile-tag-row {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--ckqa-text);
}
.profile-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--ckqa-radius-full, 999px);
  background: color-mix(in srgb, var(--ckqa-accent) 14%, var(--ckqa-surface));
  color: var(--ckqa-accent-strong);
  font-size: 12px;
  font-weight: 600;
}
.profile-tag--muted {
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-muted);
}

.profile-display-name-row {
  display: flex;
  gap: 8px;
  align-items: stretch;
}
.profile-display-name-input { flex: 1; }

.profile-password-form {
  display: grid;
  gap: 12px;
  max-width: 480px;
}
.profile-password-form label {
  display: grid;
  gap: 4px;
}
.profile-password-form label > span {
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
.profile-password-form__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.profile-permission-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.profile-permission-list li {
  padding: 8px 10px;
  background: var(--ckqa-surface-muted);
  border: 1px solid var(--ckqa-border);
  border-radius: 8px;
  font-size: 12px;
}
.profile-permission-list code {
  font-family: var(--ckqa-font-mono);
  font-weight: 600;
}
.profile-permission-list__empty {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ckqa-text-muted);
}

@media (max-width: 720px) {
  .profile-basic {
    grid-template-columns: 1fr;
    justify-items: center;
  }
  .profile-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
