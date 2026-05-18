<script setup>
import { computed } from 'vue'
import {
  CheckCircle2,
  Loader2,
  AlertTriangle,
  XCircle,
  CircleSlash2,
  Clock,
  Archive,
  Circle,
} from 'lucide-vue-next'

import { getStatusTone, getStatusLabel } from './status-model.js'

const props = defineProps({
  status: { type: String, required: true },
  label: { type: String, default: '' },
  // 控制是否渲染前缀图标，默认开启；个别场景（如 stepper）可关闭
  showIcon: { type: Boolean, default: true },
})

const tone = computed(() => getStatusTone(props.status))

// 显示文本：优先使用调用方显式传的 label（业务定制），否则按 status 兜底中文。
// 当 label 仍是字典里的英文 key（例如 "success"）时也转成中文，避免裸英文。
const text = computed(() => {
  const explicit = props.label && props.label.trim()
  return explicit ? getStatusLabel(props.label) : getStatusLabel(props.status)
})

// tone → 图标映射；done/success 共用对勾；running 用旋转加载；warning 用三角
const iconComponent = computed(() => {
  if (!props.showIcon) return null
  switch (tone.value) {
    case 'success':
      return CheckCircle2
    case 'running':
      return Loader2
    case 'warning':
      return AlertTriangle
    case 'danger':
      return XCircle
    case 'blocked':
      // archived / draft / 未满足 都用半遮蔽圆，跟主色系区分
      return props.status === 'archived' ? Archive
        : props.status === 'draft' ? CircleSlash2
          : Circle
    default:
      return Clock
  }
})

const isAnimated = computed(() => tone.value === 'running')
</script>

<template>
  <span class="status-badge" :data-tone="tone" :aria-label="`状态：${text}`">
    <component
      v-if="iconComponent"
      :is="iconComponent"
      :size="12"
      class="status-badge__icon"
      :class="{ 'is-spin': isAnimated }"
      aria-hidden="true"
    />
    {{ text }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.status-badge__icon {
  flex-shrink: 0;
  display: inline-block;
}
.status-badge__icon.is-spin {
  animation: status-badge-spin 1.2s linear infinite;
}
@keyframes status-badge-spin {
  to { transform: rotate(360deg); }
}
</style>
