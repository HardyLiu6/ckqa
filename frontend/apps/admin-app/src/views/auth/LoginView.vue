<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { authStore } from '../../stores/auth.js'

const route = useRoute()
const router = useRouter()
const selectedRole = ref('admin')

function submit() {
  authStore.loginAs(selectedRole.value)
  router.replace(route.query.redirect || '/app/dashboard')
}
</script>

<template>
  <div class="login-view">
    <p class="eyebrow">CKQA Admin</p>
    <h1>进入课程知识库运维台</h1>

    <form class="login-form" @submit.prevent="submit">
      <label>
        <span>开发态身份</span>
        <select v-model="selectedRole">
          <option value="admin">平台管理员</option>
          <option value="teacher">教师</option>
        </select>
      </label>

      <button class="primary-button" type="submit">进入平台</button>
    </form>
  </div>
</template>
