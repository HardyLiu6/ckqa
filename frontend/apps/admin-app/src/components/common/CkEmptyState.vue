<script setup>
defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  cta: { type: Object, default: null },
})
</script>

<template>
  <div class="ck-empty-state" role="region" aria-label="无数据">
    <div v-if="icon || $slots.icon" class="ck-empty-state-icon">
      <slot name="icon">
        <span aria-hidden="true">{{ icon }}</span>
      </slot>
    </div>
    <h3 class="ck-empty-state-title">{{ title }}</h3>
    <p v-if="description" class="ck-empty-state-desc">{{ description }}</p>
    <button
      v-if="cta?.label"
      class="ck-empty-state-cta"
      type="button"
      @click="cta.onClick && cta.onClick($event)"
    >
      {{ cta.label }}
    </button>
    <slot name="extra" />
  </div>
</template>

<style scoped lang="scss">
.ck-empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 200px;
  padding: var(--ckqa-space-8) var(--ckqa-space-6);
  text-align: center;
  color: var(--ckqa-text-muted);
}
.ck-empty-state-icon {
  width: 48px; height: 48px;
  display: flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface-muted);
  border-radius: var(--ckqa-radius-full);
  font-size: 22px;
  margin-bottom: var(--ckqa-space-3);
}
.ck-empty-state-title {
  margin: 0 0 var(--ckqa-space-2);
  font-size: var(--ckqa-text-md-size); line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.ck-empty-state-desc {
  margin: 0 0 var(--ckqa-space-4);
  max-width: 360px;
  font-size: var(--ckqa-text-sm-size); line-height: var(--ckqa-text-sm-line);
}
.ck-empty-state-cta {
  padding: 7px 16px;
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  border: none;
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
}
.ck-empty-state-cta:hover { background: var(--ckqa-accent-strong); }
</style>
