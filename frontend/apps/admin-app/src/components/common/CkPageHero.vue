<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
})
</script>

<template>
  <header class="ck-page-hero">
    <div class="ck-page-hero-text">
      <span v-if="eyebrow" class="ck-page-hero-eyebrow">{{ eyebrow }}</span>
      <h1 class="ck-page-hero-title">{{ title }}</h1>
      <p v-if="subtitle" class="ck-page-hero-subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="ck-page-hero-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.ck-page-hero {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: var(--ckqa-space-6);
  margin-bottom: var(--ckqa-space-6);
}
.ck-page-hero-text { flex: 1; min-width: 0; }
.ck-page-hero-eyebrow {
  display: inline-block;
  font-size: var(--ckqa-text-xs-size); line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  margin-bottom: var(--ckqa-space-2);
}
.ck-page-hero-title {
  margin: 0;
  font-size: var(--ckqa-text-3xl-size); line-height: var(--ckqa-text-3xl-line);
  font-weight: var(--ckqa-fw-semibold); letter-spacing: var(--ckqa-tracking-tight);
  color: var(--ckqa-text);
}
.ck-page-hero-subtitle {
  margin: var(--ckqa-space-2) 0 0;
  font-size: var(--ckqa-text-md-size); line-height: var(--ckqa-text-md-line);
  color: var(--ckqa-text-muted);
}
.ck-page-hero-actions {
  display: flex; align-items: center; gap: var(--ckqa-space-2);
  flex-shrink: 0;
}
</style>
