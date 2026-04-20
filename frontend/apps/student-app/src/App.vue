<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import { userLoadingStore } from './stores'

const userLoading = userLoadingStore()
const route = useRoute()
const showNav = computed(() => route.path.startsWith('/home'))
</script>

<template>
  <div class="app-shell">
    <RouterView v-loading.fullscreen.lock="userLoading.loading" />
  </div>
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html {
  font-size: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Roboto, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

a {
  text-decoration: none;
}

ul li {
  list-style: none;
}

.app-shell {
  min-height: 100vh;
}
</style>