<script setup>
/**
 * EmptyState - 空状态组件
 * 用于列表页无数据时的占位展示，包含插图区域、主标题、描述文案和可选的操作按钮
 * 自动适配 Light/Dark 配色（使用 Design Token）
 * Requirements: 6.1, 6.3, 6.4
 */
import { Icon } from '@iconify/vue'

const props = defineProps({
  /** lucide-vue-next 图标组件或 @iconify/vue 图标名（字符串） */
  icon: { type: [String, Object], default: null },
  /** 主标题 */
  title: { type: String, required: true },
  /** 描述文案 */
  description: { type: String, default: '' },
  /** 操作按钮文案 */
  actionLabel: { type: String, default: '' },
  /** 路由链接目标（String 或 Object） */
  actionTo: { type: [String, Object], default: '' },
})

const emit = defineEmits(['action'])
</script>

<template>
  <div class="empty-state">
    <!-- 插图/图标区域 -->
    <div v-if="props.icon" class="empty-state__icon">
      <!-- 字符串类型：使用 @iconify/vue 渲染 -->
      <Icon
        v-if="typeof props.icon === 'string'"
        :icon="props.icon"
        class="empty-state__icon-svg"
        aria-hidden="true"
      />
      <!-- 组件类型：使用 component :is 动态渲染 lucide-vue-next 图标 -->
      <component
        :is="props.icon"
        v-else
        class="empty-state__icon-svg"
        :size="48"
        :stroke-width="1.5"
        aria-hidden="true"
      />
    </div>

    <!-- 主标题 -->
    <h2 class="empty-state__title">{{ props.title }}</h2>

    <!-- 描述文案 -->
    <p v-if="props.description" class="empty-state__description">{{ props.description }}</p>

    <!-- 操作按钮 -->
    <el-button
      v-if="props.actionLabel && props.actionTo"
      class="empty-state__action ckqa-el-button ckqa-el-button--primary"
      type="primary"
      tag="router-link"
      :to="props.actionTo"
    >
      {{ props.actionLabel }}
    </el-button>
    <el-button
      v-else-if="props.actionLabel"
      class="empty-state__action ckqa-el-button ckqa-el-button--primary"
      type="primary"
      @click="emit('action')"
    >
      {{ props.actionLabel }}
    </el-button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--ckqa-space-8) var(--ckqa-space-4);
  min-height: 240px;
}

.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  margin-bottom: var(--ckqa-space-5);
  border-radius: var(--ckqa-radius-full);
  background: var(--ckqa-bg-elevated);
  color: var(--ckqa-accent);
}

.empty-state__icon-svg {
  width: 48px;
  height: 48px;
}

.empty-state__title {
  margin: 0;
  font-size: var(--ckqa-text-h3-size);
  font-weight: var(--ckqa-text-h3-weight);
  line-height: var(--ckqa-text-h3-line-height);
  color: var(--ckqa-text);
}

.empty-state__description {
  margin: var(--ckqa-space-2) 0 0;
  font-size: var(--ckqa-text-body-size);
  line-height: var(--ckqa-text-body-line-height);
  color: var(--ckqa-text-muted);
  max-width: 360px;
}

.empty-state__action {
  margin-top: var(--ckqa-space-5);
}
</style>
