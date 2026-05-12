<script setup>
/**
 * RetryPanel - 局部错误态重试面板
 * 用于 API 请求失败时的局部错误展示，包含错误摘要和重试按钮。
 * 连续 3 次失败后展示升级提示（"请检查网络连接或联系管理员"）。
 * 始终保留手动重试入口。
 * Requirements: 8.3, 8.4, 8.5
 */
import { computed } from 'vue'
import { AlertTriangle, Loader2, RefreshCw } from 'lucide-vue-next'

const props = defineProps({
  /** 错误信息，可以是字符串或包含 message 属性的对象 */
  error: { type: [String, Object], default: '' },
  /** 当前重试次数 */
  retryCount: { type: Number, default: 0 },
  /** 最大重试次数阈值，超过后展示升级提示 */
  maxRetries: { type: Number, default: 3 },
  /** 是否正在重试中 */
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['retry'])

/** 提取错误摘要文本 */
const errorMessage = computed(() => {
  if (!props.error) return '请求失败'
  if (typeof props.error === 'string') return props.error
  if (typeof props.error === 'object' && props.error.message) return props.error.message
  return '请求失败'
})

/** 是否已达到升级阈值 */
const isEscalated = computed(() => props.retryCount >= props.maxRetries)

function handleRetry() {
  emit('retry')
}
</script>

<template>
  <div
    class="retry-panel"
    :class="{ 'retry-panel--escalated': isEscalated }"
    role="alert"
    aria-live="polite"
  >
    <!-- 图标区域 -->
    <div class="retry-panel__icon">
      <AlertTriangle :size="24" aria-hidden="true" />
    </div>

    <!-- 内容区域 -->
    <div class="retry-panel__body">
      <!-- 错误摘要 -->
      <p class="retry-panel__message">{{ errorMessage }}</p>

      <!-- 升级提示：连续失败 >= maxRetries 次 -->
      <p v-if="isEscalated" class="retry-panel__escalation">
        请检查网络连接或联系管理员
      </p>

      <!-- 重试次数提示 -->
      <p v-if="retryCount > 0" class="retry-panel__count">
        已重试 {{ retryCount }} 次
      </p>
    </div>

    <!-- 重试按钮：始终保留手动重试入口 -->
    <button
      class="retry-panel__button"
      :disabled="loading"
      @click="handleRetry"
    >
      <Loader2 v-if="loading" class="retry-panel__spinner" :size="16" aria-hidden="true" />
      <RefreshCw v-else :size="16" aria-hidden="true" />
      <span>{{ loading ? '重试中…' : '重试' }}</span>
    </button>
  </div>
</template>

<style scoped>
.retry-panel {
  display: flex;
  align-items: flex-start;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-4);
  border-radius: var(--ckqa-radius-md);
  border: 1px solid color-mix(in srgb, var(--ckqa-warning) 36%, var(--ckqa-border));
  background: color-mix(in srgb, var(--ckqa-warning) 6%, var(--ckqa-surface));
  color: var(--ckqa-text);
}

/* 升级提示使用更强的 danger 配色 */
.retry-panel--escalated {
  border-color: color-mix(in srgb, var(--ckqa-danger) 56%, var(--ckqa-border));
  background: color-mix(in srgb, var(--ckqa-danger) 10%, var(--ckqa-surface));
}

.retry-panel__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ckqa-warning);
}

.retry-panel--escalated .retry-panel__icon {
  color: var(--ckqa-danger);
}

.retry-panel__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-1);
}

.retry-panel__message {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--ckqa-text);
}

.retry-panel__escalation {
  margin: 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--ckqa-danger);
  font-weight: 600;
}

.retry-panel__count {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--ckqa-text-muted);
}

.retry-panel__button {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: var(--ckqa-space-1);
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  border: 1px solid var(--ckqa-border-strong);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    background var(--ckqa-duration-fast) var(--ckqa-ease-standard),
    box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}

.retry-panel__button:hover:not(:disabled) {
  border-color: var(--ckqa-accent);
  background: var(--ckqa-surface-hover);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--ckqa-accent) 16%, transparent);
}

.retry-panel__button:active:not(:disabled) {
  transform: scale(0.97);
}

.retry-panel__button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 加载旋转动画 */
@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.retry-panel__spinner {
  animation: spin 1s linear infinite;
}

/* prefers-reduced-motion 降级 */
@media (prefers-reduced-motion: reduce) {
  .retry-panel__button {
    transition: none;
  }

  .retry-panel__button:active:not(:disabled) {
    transform: none;
  }

  .retry-panel__spinner {
    animation: none;
  }
}
</style>
