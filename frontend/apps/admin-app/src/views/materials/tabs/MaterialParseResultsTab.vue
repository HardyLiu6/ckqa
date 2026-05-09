<script setup>
import { computed } from 'vue'

import CkEmptyState from '../../../components/common/CkEmptyState.vue'

import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

const props = defineProps({
  material: { type: Object, required: true },
  detailData: { type: Object, default: null },
})

// loadMaterialDetail 返回的 parseResults block 已经包含按类型分组的 groups 数组
// groups: [{ key, label, count, summary, items }]
const groups = computed(() => props.detailData?.blocks?.parseResults?.groups ?? [])
const blockState = computed(() => props.detailData?.blocks?.parseResults?.state)
const summary = computed(() => props.detailData?.blocks?.parseResults?.summary ?? null)
</script>

<template>
  <div class="material-parse-results-tab" data-testid="material-parse-results-tab">
    <p v-if="summary && summary.total > 0" class="material-parse-results-tab-summary">
      共 {{ summary.total }} 份解析产物 · {{ summary.groupCount }} 种类型
      <span v-if="summary.imageCount"> · {{ summary.imageCount }} 张图片</span>
    </p>

    <CkEmptyState
      v-if="blockState === 'error'"
      icon="!"
      title="解析结果加载失败"
    />

    <CkEmptyState
      v-else-if="!groups.length"
      icon="·"
      :title="MATERIAL_PAGE_COPY.results.emptyTitle"
      :description="MATERIAL_PAGE_COPY.results.emptyDescription"
    />

    <div v-else class="material-parse-results-tab-groups">
      <section
        v-for="group in groups"
        :key="group.key"
        class="material-parse-results-tab-group"
        :data-group="group.key"
      >
        <header class="material-parse-results-tab-group-head">
          <h4>{{ group.label }}</h4>
          <span>{{ group.summary }}</span>
        </header>
        <ul class="material-parse-results-tab-list">
          <li v-for="item in group.items" :key="item.id">
            <a
              v-if="item.previewUrl"
              :href="item.previewUrl"
              target="_blank"
              rel="noopener"
              class="material-parse-results-tab-item-title"
            >
              {{ item.title }}
            </a>
            <span v-else class="material-parse-results-tab-item-title">{{ item.title }}</span>
            <small v-if="item.detail">{{ item.detail }}</small>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.material-parse-results-tab {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.material-parse-results-tab-summary {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.material-parse-results-tab-groups {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}
.material-parse-results-tab-group {
  padding: var(--ckqa-space-3) var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
}
.material-parse-results-tab-group-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: var(--ckqa-space-2);
}
.material-parse-results-tab-group-head h4 {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.material-parse-results-tab-group-head span {
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-xs-size);
}
.material-parse-results-tab-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--ckqa-text-sm-size);
}
.material-parse-results-tab-list li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.material-parse-results-tab-list li:last-child {
  border-bottom: none;
}
.material-parse-results-tab-item-title {
  color: var(--ckqa-text);
  text-decoration: none;
  word-break: break-all;
}
.material-parse-results-tab-item-title[href]:hover {
  color: var(--ckqa-accent-strong);
  text-decoration: underline;
}
.material-parse-results-tab-list small {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
</style>
