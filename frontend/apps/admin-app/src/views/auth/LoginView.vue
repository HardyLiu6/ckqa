<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ROLE_PROFILES, authStore } from '../../stores/auth.js'

const route = useRoute()
const router = useRouter()
const selectedRole = ref('admin')

const productionSteps = [
  { label: 'PDF 解析', detail: 'MinerU / MinIO / MySQL' },
  { label: '标准化导出', detail: 'section_docs / page_docs' },
  { label: 'GraphRAG 建图', detail: 'index / lancedb / parquet' },
  { label: '问答运维', detail: 'Java /api/v1 编排' },
]

const roleOptions = [
  { value: 'admin', label: '平台管理员' },
  { value: 'teacher', label: '教师' },
]

const selectedProfile = computed(() => ROLE_PROFILES[selectedRole.value])

function submit() {
  authStore.loginAs(selectedRole.value)
  router.replace(route.query.redirect || '/app/dashboard')
}
</script>

<template>
  <div class="login-view login-split">
    <section class="login-positioning" aria-labelledby="login-title">
      <div class="login-brand">
        <span class="brand-mark">CK</span>
        <div>
          <p class="eyebrow">CKQA Admin</p>
          <h1 id="login-title">进入课程知识库运维台</h1>
        </div>
      </div>
      <p class="login-copy">
        面向管理员和教师的共享控制台，聚焦课程资料解析、知识库构建、问答运维和系统审计。
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
      <div>
        <p class="eyebrow">Dev Identity</p>
        <h2 id="login-role-title">选择开发态身份</h2>
        <p class="login-copy">当前为开发态身份切换，正式登录接口待接入</p>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label>
          <span>开发态身份</span>
          <select v-model="selectedRole">
            <option v-for="role in roleOptions" :key="role.value" :value="role.value">
              {{ role.label }}
            </option>
          </select>
        </label>

        <dl class="login-role-facts">
          <div>
            <dt>当前身份</dt>
            <dd>{{ selectedProfile.name }}</dd>
          </div>
          <div>
            <dt>数据范围</dt>
            <dd>{{ selectedProfile.dataScope }}</dd>
          </div>
          <div>
            <dt>权限模型</dt>
            <dd>{{ selectedProfile.permissions.includes('*') ? '全量权限' : '按课程授权' }}</dd>
          </div>
        </dl>

        <button class="primary-button" type="submit">进入平台</button>
      </form>
    </section>
  </div>
</template>
