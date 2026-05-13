<script setup>
import { computed, reactive, ref } from 'vue'
import { KeyRound, LogIn, ShieldCheck, UserRound } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { createApiError } from '../../api/client.js'
import { LOGIN_PRESETS, authStore } from '../../stores/auth.js'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitError = ref('')
const activePreset = ref(LOGIN_PRESETS[0].role)
const form = reactive({
  username: LOGIN_PRESETS[0].username,
  password: LOGIN_PRESETS[0].password,
})

const productionSteps = [
  { label: 'PDF 解析', detail: 'MinerU / MinIO / MySQL' },
  { label: '标准化导出', detail: 'section_docs / page_docs' },
  { label: 'GraphRAG 建图', detail: 'index / lancedb / parquet' },
  { label: '问答运维', detail: 'Java /api/v1 编排' },
]

const activePresetDetail = computed(() =>
  LOGIN_PRESETS.find((preset) => preset.role === activePreset.value) ?? LOGIN_PRESETS[0],
)

function applyPreset(preset) {
  activePreset.value = preset.role
  form.username = preset.username
  form.password = preset.password
  submitError.value = ''
}

async function submit() {
  submitError.value = ''
  loading.value = true

  try {
    await authStore.login(form)
    router.replace(resolveRedirect())
  } catch (error) {
    submitError.value = createApiError(error).message || '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}

function resolveRedirect() {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/app') ? redirect : '/app/dashboard'
}
</script>

<template>
  <div class="login-view login-split">
    <section class="login-positioning" aria-labelledby="login-title">
      <div class="login-brand">
        <img class="brand-mark brand-mark--login" src="/logo.png" alt="智课问答" />
        <div>
          <p class="eyebrow">智课问答</p>
          <h1 id="login-title">智课问答管理台</h1>
        </div>
      </div>
      <p class="login-copy">
        管理员与教师共用一套控制台，登录后按角色权限进入课程、资料、知识库和问答运维工作区。
      </p>

      <ol class="login-flow" aria-label="生产链路简图">
        <li v-for="(step, index) in productionSteps" :key="step.label">
          <span class="login-flow__index">{{ index + 1 }}</span>
          <div>
            <strong>{{ step.label }}</strong>
            <small>{{ step.detail }}</small>
          </div>
        </li>
      </ol>
    </section>

    <section class="login-card" aria-labelledby="login-role-title">
      <div class="login-card__header">
        <div>
          <p class="eyebrow">JWT Access</p>
          <h2 id="login-role-title">登录控制台</h2>
        </div>
        <span class="login-status-pill">
          <ShieldCheck :size="15" aria-hidden="true" />
          Spring Security
        </span>
      </div>

      <div class="login-preset-grid" aria-label="测试账号">
        <button
          v-for="preset in LOGIN_PRESETS"
          :key="preset.role"
          class="login-preset"
          :class="{ active: activePreset === preset.role }"
          type="button"
          @click="applyPreset(preset)"
        >
          <strong>{{ preset.label }}</strong>
          <span>{{ preset.username }}</span>
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>账号</span>
          <el-input v-model.trim="form.username" class="login-input" autocomplete="username">
            <template #prefix>
              <UserRound :size="16" aria-hidden="true" />
            </template>
          </el-input>
        </label>

        <label>
          <span>密码</span>
          <el-input
            v-model="form.password"
            class="login-input"
            autocomplete="current-password"
            show-password
            type="password"
          >
            <template #prefix>
              <KeyRound :size="16" aria-hidden="true" />
            </template>
          </el-input>
        </label>

        <dl class="login-role-facts">
          <div>
            <dt>登录身份</dt>
            <dd>{{ activePresetDetail.label }}</dd>
          </div>
          <div>
            <dt>账号范围</dt>
            <dd>{{ activePresetDetail.description }}</dd>
          </div>
        </dl>

        <p v-if="submitError" class="login-form-error" role="alert">{{ submitError }}</p>

        <el-button
          class="ckqa-el-button ckqa-el-button--primary login-submit"
          type="primary"
          native-type="submit"
          :loading="loading"
        >
          <LogIn class="button-icon" :size="16" aria-hidden="true" />
          进入平台
        </el-button>
      </form>
    </section>
  </div>
</template>
