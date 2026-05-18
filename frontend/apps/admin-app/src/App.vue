<script setup>
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <ElConfigProvider :locale="zhCn">
    <component :is="$route.meta.layoutComponent || 'main'" class="app-layout">
      <RouterView />
    </component>
  </ElConfigProvider>
</template>
