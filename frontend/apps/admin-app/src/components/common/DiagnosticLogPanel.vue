<script setup>
defineProps({
  title: { type: String, required: true },
  lines: { type: Array, default: () => [] },
  actions: { type: Array, default: () => [] },
})
</script>

<template>
  <section class="diagnostic-log-panel" :aria-label="title">
    <div class="diagnostic-log-panel__heading">
      <h2>{{ title }}</h2>
      <div v-if="actions.length" class="diagnostic-log-panel__actions">
        <RouterLink
          v-for="action in actions"
          :key="action.label"
          class="diagnostic-log-panel__action"
          :to="action.to"
        >
          {{ action.label }}
        </RouterLink>
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
