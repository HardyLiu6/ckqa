<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'card' },
  count: { type: Number, default: 1 },
  animated: { type: Boolean, default: true },
})

const items = computed(() => Array.from({ length: Math.max(1, props.count) }))
</script>

<template>
  <div
    class="ck-skeleton-group"
    :class="{ 'ck-skeleton-group--animated': animated }"
    role="status"
    aria-busy="true"
    aria-live="polite"
  >
    <div
      v-for="(_, idx) in items"
      :key="idx"
      class="ck-skeleton"
      :class="`ck-skeleton--${variant}`"
    />
    <span class="ck-skeleton-sr">加载中…</span>
  </div>
</template>

<style scoped lang="scss">
.ck-skeleton-group { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.ck-skeleton {
  background: linear-gradient(
    90deg,
    var(--ckqa-surface-muted) 0%,
    var(--ckqa-surface-strong) 50%,
    var(--ckqa-surface-muted) 100%
  );
  background-size: 200% 100%;
  border-radius: var(--ckqa-radius-md);
}
.ck-skeleton-group--animated .ck-skeleton {
  animation: ck-skeleton-shimmer 1600ms linear infinite;
}
.ck-skeleton--card { height: 84px; }
.ck-skeleton--row { height: 36px; }
.ck-skeleton--text { height: var(--ckqa-text-md-line); width: 70%; border-radius: var(--ckqa-radius-sm); }
.ck-skeleton--avatar { width: 36px; height: 36px; border-radius: var(--ckqa-radius-full); }
.ck-skeleton-sr {
  position: absolute; width: 1px; height: 1px;
  padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0);
  white-space: nowrap; border: 0;
}
@keyframes ck-skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
