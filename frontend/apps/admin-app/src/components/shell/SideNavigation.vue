<script setup>
defineProps({
  sections: { type: Array, required: true },
  sectionLabels: { type: Object, required: true },
  activePath: { type: String, default: '' },
})
</script>

<template>
  <aside class="side-nav side-navigation" aria-label="主导航">
    <section
      v-for="section in sections"
      :key="section.key"
      class="side-nav-section"
    >
      <h3 v-if="sectionLabels[section.key]" class="side-nav-section-title">
        {{ sectionLabels[section.key] }}
      </h3>
      <nav>
        <RouterLink
          v-for="item in section.items"
          :key="item.key"
          :to="item.path"
          class="side-nav-item"
          :class="{ 'is-active': activePath === item.path }"
          :data-test-id="`nav-${item.key}`"
        >
          <span class="side-nav-item-label">{{ item.label }}</span>
          <span v-if="item.count" class="side-nav-item-count">{{ item.count }}</span>
        </RouterLink>
      </nav>
    </section>

    <footer class="side-nav-footer">
      <strong>● API 正常</strong>
      <span>系统服务在线</span>
    </footer>
  </aside>
</template>

<style scoped lang="scss">
.side-nav {
  position: sticky; top: 52px; align-self: start;
  width: 220px; height: calc(100vh - 52px);
  padding: 14px 10px;
  background: var(--ckqa-surface);
  border-right: 1px solid var(--ckqa-border);
  display: flex; flex-direction: column;
  overflow-y: auto;
}
.side-nav-section { margin-bottom: 14px; }
.side-nav-section-title {
  margin: 0 10px 6px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  font-weight: var(--ckqa-fw-medium);
}
.side-nav-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 7px 10px;
  border-radius: 7px;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  text-decoration: none;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.side-nav-item:hover { background: var(--ckqa-bg); }
.side-nav-item.is-active {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
  font-weight: var(--ckqa-fw-medium);
}
.side-nav-item-count {
  background: var(--ckqa-bg);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-xs-size);
  padding: 1px 6px;
  border-radius: var(--ckqa-radius-full);
}
.side-nav-item.is-active .side-nav-item-count {
  background: var(--ckqa-surface);
  color: var(--ckqa-accent-strong);
}
.side-nav-footer {
  margin-top: auto;
  padding: 10px;
  background: var(--ckqa-bg);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  display: flex; flex-direction: column; gap: 2px;
}
.side-nav-footer strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
}
</style>
