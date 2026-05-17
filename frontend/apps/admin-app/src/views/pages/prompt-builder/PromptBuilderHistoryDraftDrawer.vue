<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  drafts: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'select-draft'])

const sorted = computed(() =>
  [...props.drafts].sort((a, b) => {
    const ta = new Date(a.createdAt ?? 0).getTime()
    const tb = new Date(b.createdAt ?? 0).getTime()
    return tb - ta
  }),
)

function handleSelect(draft) {
  emit('select-draft', draft)
  emit('update:modelValue', false)
}

function fmtScore(score) {
  if (score == null) return '—'
  return (Number(score) * 100).toFixed(0) + '%'
}

function fmtDate(iso) {
  if (!iso) return ''
  return String(iso).replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="选择历史草稿"
    direction="rtl"
    size="420px"
    :before-close="() => emit('update:modelValue', false)"
  >
    <div v-if="sorted.length === 0" class="ann-text-muted">暂无可选草稿</div>
    <ul v-else class="history-draft-list">
      <li
        v-for="d in sorted"
        :key="d.id"
        class="history-draft-list__item"
        @click="handleSelect(d)"
      >
        <div class="history-draft-list__head">
          <strong>{{ d.name }}</strong>
          <span class="ann-pill ann-pill--gold">综合 {{ fmtScore(d.compositeScore) }}</span>
        </div>
        <div class="ann-text-tiny">候选：{{ d.candidateId }} · 来源构建 #{{ d.sourceBuildRunId }}</div>
        <div v-if="d.description" class="history-draft-list__desc">{{ d.description }}</div>
        <div class="ann-text-tiny ann-text-muted">{{ fmtDate(d.createdAt) }}</div>
      </li>
    </ul>
  </el-drawer>
</template>

<style scoped>
.history-draft-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.history-draft-list__item {
  cursor: pointer;
  padding: 12px;
  border: 1px solid var(--ckqa-border, #d6d3d1);
  border-radius: 8px;
  transition: background 0.2s;
}
.history-draft-list__item:hover {
  background: var(--ckqa-bg-soft, #fafaf9);
}
.history-draft-list__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.history-draft-list__desc {
  margin: 6px 0;
  color: var(--ckqa-text-muted, #78716c);
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
