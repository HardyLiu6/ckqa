<script setup>
import { ArrowRight } from 'lucide-vue-next'

defineProps({
  title: { type: String, required: true },
  lines: { type: Array, default: () => [] },
  actions: { type: Array, default: () => [] },
})

function resolveActionIcon(action) {
  return action.icon ?? ArrowRight
}
</script>

<template>
  <section class="diagnostic-log-panel" :aria-label="title">
    <div class="diagnostic-log-panel__heading">
      <h2>{{ title }}</h2>
      <div v-if="actions.length" class="diagnostic-log-panel__actions">
        <el-button
          v-for="action in actions"
          :key="action.label"
          class="ckqa-link-button diagnostic-log-panel__action"
          link
          type="primary"
          tag="router-link"
          :to="action.to"
        >
          <component :is="resolveActionIcon(action)" class="button-icon" :size="15" aria-hidden="true" />
          {{ action.label }}
        </el-button>
      </div>
    </div>
    <ol class="diagnostic-log-panel__lines">
      <li v-for="line in lines" :key="line">
        <code>{{ line }}</code>
      </li>
      <li v-if="!lines.length">
        <code>暂无诊断记录</code>
      </li>
    </ol>
  </section>
</template>
