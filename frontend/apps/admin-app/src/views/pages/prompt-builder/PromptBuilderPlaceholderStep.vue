<script setup>
defineProps({
  stepKey: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  phase: { type: String, default: 'Phase 1b' },
})
</script>

<template>
  <section class="prompt-builder-step prompt-builder-placeholder">
    <header class="prompt-builder-step__header">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </header>
    <div class="prompt-builder-placeholder__body">
      <div class="prompt-builder-placeholder__chip">即将开放</div>
      <p class="prompt-builder-placeholder__hint">
        本步骤将在 {{ phase }} 实施。当前可直接点击"下一步"跳过，到 05 步保存草稿。
      </p>
    </div>
  </section>
</template>
