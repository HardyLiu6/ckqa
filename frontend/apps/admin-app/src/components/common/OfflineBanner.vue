<script setup>
/**
 * OfflineBanner - 全局断网提示组件
 * 断网时展示持久性提示条（不自动消失），位于页面顶部
 * 网络恢复时短暂展示"网络已恢复"成功 Toast（ElMessage.success）
 * Requirements: 8.1, 8.2
 */
import { watch } from 'vue'
import { WifiOff } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { useNetworkStatus } from '../../composables/useNetworkStatus.js'

const { isOnline, wasOffline } = useNetworkStatus()

// 网络恢复时通过 ElMessage.success 展示短暂 Toast 提示
watch(wasOffline, (newVal) => {
  if (newVal) {
    ElMessage.success('网络已恢复')
  }
})
</script>

<template>
  <!-- 断网提示条：持久性展示，不自动消失 -->
  <Transition name="slide-down">
    <div v-if="!isOnline" class="offline-banner" role="alert">
      <WifiOff class="offline-banner__icon" :size="18" />
      <span class="offline-banner__text">网络连接已断开，部分功能可能不可用</span>
    </div>
  </Transition>
</template>

<style scoped>
.offline-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-2) var(--ckqa-space-4);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  z-index: 2000;
  width: 100%;
  background-color: var(--ckqa-danger);
  color: #ffffff;
}

.offline-banner__icon {
  flex-shrink: 0;
}

.offline-banner__text {
  white-space: nowrap;
}
</style>
