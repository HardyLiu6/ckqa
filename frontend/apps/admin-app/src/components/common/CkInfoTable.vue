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
  <!--
    A11y：axe `dlitem` 规则要求 `<dt>/<dd>` 是 `<dl>` 的直接子元素。
    之前的结构把 `<dt>/<dd>` 嵌在 `.ck-info-table-col > .ck-info-table-row` 两层
    `<div>` 里（用于 CSS Grid 排版），导致 axe 报 serious 违规。
    现在把"列容器"本身升级为 `<dl>`：外层 `<section>` 只做 CSS Grid 排版（列分布），
    每列一个 `<dl>`；每个 `<dl>` 内部按 `<dt>/<dd>` 配对依次排布，dt-dd 间的
    `96px 1fr` 栅格用 CSS `display: grid` + `grid-column` 实现，结构语义不变。
  -->
  <section
    class="ck-info-table"
    :data-columns="columns"
    data-testid="info-table"
    aria-label="信息明细"
  >
    <dl v-for="(col, idx) in grouped" :key="idx" class="ck-info-table-col">
      <template v-for="entry in col" :key="entry.label">
        <dt>{{ entry.label }}</dt>
        <dd v-if="entry.kind === 'html'" v-html="entry.value" />
        <dd v-else>{{ entry.value }}</dd>
      </template>
    </dl>
  </section>
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
  display: grid;
  // 每列内部按 `标签 | 值` 两列栅格；`<dt>` 落第 1 列、`<dd>` 落第 2 列。
  grid-template-columns: 96px 1fr;
  row-gap: var(--ckqa-space-2);
  column-gap: var(--ckqa-space-3);
  margin: 0;
}
.ck-info-table-col dt {
  grid-column: 1;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-weak);
}
.ck-info-table-col dd {
  grid-column: 2;
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  word-break: break-word;
}
</style>
