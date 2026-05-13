<script setup>
import { ref, computed } from 'vue'

defineProps({
  extractGraphContent: { type: String, default: '' },
  buildRunId: { type: [String, Number], default: null },
})

const PROMPT_ITEMS = [
  { key: 'extract_graph',           label: '实体抽取',     status: 'edited' },
  { key: 'summarize_descriptions',  label: '描述总结',     status: 'locked' },
  { key: 'community_report_graph',  label: '社区报告·图',  status: 'locked' },
  { key: 'community_report_text',   label: '社区报告·文',  status: 'locked' },
  { key: 'extract_claims',          label: '声明抽取',     status: 'locked' },
]

const activeKey = ref('extract_graph')
const activeItem = computed(() => PROMPT_ITEMS.find((i) => i.key === activeKey.value))
</script>

<template>
  <section class="prompt-builder-step">
    <header class="prompt-builder-step__header">
      <h3>预览 + 保存</h3>
      <p>复核所有提示词内容，确认无误后保存。保存后会回到构建向导第 04 步。</p>
    </header>

    <div class="preview-grid">
      <ul class="preview-list" role="tablist">
        <li
          v-for="item in PROMPT_ITEMS"
          :key="item.key"
          role="tab"
          :aria-selected="activeKey === item.key"
          :data-active="activeKey === item.key ? 'true' : 'false'"
          class="preview-item"
          tabindex="0"
          @click="activeKey = item.key"
          @keydown.enter.prevent="activeKey = item.key"
        >
          <span>{{ item.label }}</span>
          <span v-if="item.status === 'edited'" class="preview-tag preview-tag--edited">已改</span>
          <span v-else class="preview-tag preview-tag--locked">未开放</span>
        </li>
      </ul>

      <div class="preview-content">
        <pre v-if="activeItem?.status === 'edited'">{{ extractGraphContent }}</pre>
        <p v-else class="preview-content__locked">该提示词将沿用所选种子模板的默认内容，本次构建不会被修改。</p>
      </div>
    </div>

    <p class="preview-attribution">
      保存后该草稿将归属本次构建（Build Run ID：{{ buildRunId ?? '—' }}），其他构建不受影响。
    </p>
  </section>
</template>
