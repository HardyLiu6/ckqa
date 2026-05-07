<script setup>
defineProps({
  items: { type: Array, required: true },
})
</script>

<template>
  <nav class="ck-breadcrumbs" aria-label="面包屑导航">
    <ol class="ck-breadcrumbs-list">
      <li
        v-for="(item, idx) in items"
        :key="`${item.kind}-${idx}-${item.label}`"
        class="ck-breadcrumbs-item"
        :data-kind="item.kind"
      >
        <details v-if="item.kind === 'collapsed'" class="ck-breadcrumbs-collapsed">
          <summary>{{ item.label }}</summary>
          <ul>
            <li
              v-for="(c, ci) in item.collapsed"
              :key="`${c.label}-${ci}`"
            >
              <RouterLink v-if="c.to" :to="c.to">{{ c.label }}</RouterLink>
              <span v-else>{{ c.label }}</span>
            </li>
          </ul>
        </details>
        <RouterLink v-else-if="item.to" :to="item.to">{{ item.label }}</RouterLink>
        <span v-else>{{ item.label }}</span>
        <span v-if="idx < items.length - 1" class="ck-breadcrumbs-sep" aria-hidden="true">/</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped lang="scss">
.ck-breadcrumbs-list {
  display: flex; flex-wrap: wrap; align-items: center;
  gap: 4px; padding: 0; margin: 0; list-style: none;
}
.ck-breadcrumbs-item {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--ckqa-text-xs-size); line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
}
.ck-breadcrumbs-item a { color: var(--ckqa-text-muted); text-decoration: none; }
.ck-breadcrumbs-item a:hover { color: var(--ckqa-accent-strong); text-decoration: underline; }
.ck-breadcrumbs-item[data-kind='current'] { color: var(--ckqa-text); font-weight: var(--ckqa-fw-medium); }
.ck-breadcrumbs-sep { color: var(--ckqa-text-weak); }
.ck-breadcrumbs-collapsed { position: relative; }
.ck-breadcrumbs-collapsed summary {
  cursor: pointer; list-style: none; padding: 0 4px;
  border-radius: var(--ckqa-radius-sm);
}
.ck-breadcrumbs-collapsed summary:hover { background: var(--ckqa-surface-muted); }
.ck-breadcrumbs-collapsed[open] ul {
  position: absolute; top: 100%; left: 0; z-index: 10;
  margin: 4px 0 0; padding: 6px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
  list-style: none; min-width: 160px;
}
.ck-breadcrumbs-collapsed[open] li { padding: 4px 8px; }
.ck-breadcrumbs-collapsed[open] li:hover { background: var(--ckqa-surface-muted); border-radius: var(--ckqa-radius-sm); }
</style>
