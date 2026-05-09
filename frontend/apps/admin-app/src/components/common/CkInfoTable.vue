<script setup>
import { computed } from 'vue'

import { splitEntriesIntoColumns } from './info-table-model.js'

const props = defineProps({
  entries: { type: Array, default: () => [] },
  columns: { type: Number, default: 2 },
})

const grouped = computed(() => splitEntriesIntoColumns(props.entries, props.columns))
</script>

<template>
  <dl class="ck-info-table" :data-columns="columns" data-testid="info-table">
    <div v-for="(col, idx) in grouped" :key="idx" class="ck-info-table-col">
      <div v-for="entry in col" :key="entry.label" class="ck-info-table-row">
        <dt>{{ entry.label }}</dt>
        <dd v-if="entry.kind === 'html'" v-html="entry.value" />
        <dd v-else>{{ entry.value }}</dd>
      </div>
    </div>
  </dl>
</template>

<style scoped lang="scss">
.ck-info-table {
  display: grid;
  grid-template-columns: repeat(var(--cols, 2), minmax(0, 1fr));
  gap: var(--ckqa-space-4);
  margin: 0;
}
.ck-info-table[data-columns='1'] {
  --cols: 1;
}
.ck-info-table[data-columns='2'] {
  --cols: 2;
}
.ck-info-table[data-columns='3'] {
  --cols: 3;
}
.ck-info-table-col {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
}
.ck-info-table-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: var(--ckqa-space-3);
}
.ck-info-table-row dt {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-weak);
}
.ck-info-table-row dd {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  word-break: break-word;
}
</style>
