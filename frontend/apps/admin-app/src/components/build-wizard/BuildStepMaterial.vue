<script setup>
import { computed, ref, watch } from 'vue'

import StatusBadge from '../common/StatusBadge.vue'

const props = defineProps({
  blocks: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select-materials'])

const keyword = ref('')
const parseStatus = ref('全部')
const exportStatus = ref('全部')
const selectedIds = ref([])

const PARSE_STATUS_LABELS = {
  done: '解析完成',
  pending: '待解析',
  processing: '解析中',
  running: '解析中',
  failed: '解析失败',
}
const EXPORT_STATUS_LABELS = {
  complete: '已导出',
  missing: '缺失产物',
}
const PARSE_STATUS_OPTIONS = [
  { label: '全部解析状态', value: '全部' },
  { label: PARSE_STATUS_LABELS.done, value: 'done' },
  { label: PARSE_STATUS_LABELS.pending, value: 'pending' },
  { label: PARSE_STATUS_LABELS.processing, value: 'processing' },
  { label: PARSE_STATUS_LABELS.failed, value: 'failed' },
]
const EXPORT_STATUS_OPTIONS = [
  { label: '全部导出状态', value: '全部' },
  { label: EXPORT_STATUS_LABELS.complete, value: 'complete' },
  { label: EXPORT_STATUS_LABELS.missing, value: 'missing' },
]

const materialIds = computed(() => props.blocks.selection?.materialIds ?? [])
const materials = computed(() => props.blocks.materials?.items ?? [])
const exportRows = computed(() => new Map(
  (props.blocks.exportArtifacts?.items ?? []).map((row) => [String(row.id), row]),
))
const filteredMaterials = computed(() => materials.value.filter((item) => {
  const title = String(item.title ?? '')
  const id = String(item.id ?? '')
  const parseMatched = parseStatus.value === '全部' || item.meta === parseStatus.value
  const exportMatched = exportStatus.value === '全部' || resolveExportStatus(id) === exportStatus.value
  return title.toLowerCase().includes(keyword.value.trim().toLowerCase()) && parseMatched && exportMatched
}))

// 把 props 的 materialIds 同步到本地 selectedIds，但仅在两边集合不同时同步，
// 避免在用户刚切换 checkbox 后 loadPage 完成时，watch 把"刚 emit 出去又被回灌"的同值序列再触发一轮回写，
// 进而打断后续连续点击。
function sameSelection(left, right) {
  if (left.length !== right.length) return false
  const set = new Set(left)
  return right.every((id) => set.has(id))
}

watch(materialIds, (next) => {
  const normalized = next.map((id) => String(id))
  if (sameSelection(selectedIds.value, normalized)) return
  selectedIds.value = normalized
}, { immediate: true })

function resolveExportStatus(id) {
  return exportRows.value.get(String(id))?.status ?? 'missing'
}

function resolveParseStatusLabel(status) {
  return PARSE_STATUS_LABELS[String(status ?? '').toLowerCase()] ?? '状态未知'
}

function resolveExportStatusLabel(id) {
  return EXPORT_STATUS_LABELS[resolveExportStatus(id)] ?? '状态未知'
}

function isSelected(id) {
  return selectedIds.value.includes(String(id))
}

function toggleMaterial(id) {
  const normalizedId = String(id)
  const next = isSelected(normalizedId)
    ? selectedIds.value.filter((item) => item !== normalizedId)
    : [...selectedIds.value, normalizedId]

  emitSelection(next)
}

function selectFiltered() {
  emitSelection([...new Set([...selectedIds.value, ...filteredMaterials.value.map((item) => String(item.id))])])
}

function clearSelection() {
  emitSelection([])
}

function emitSelection(ids) {
  selectedIds.value = ids
  emit('select-materials', ids)
}
</script>

<template>
  <section class="build-step-panel">
    <div class="build-step-toolbar">
      <el-input v-model="keyword" placeholder="搜索资料名" clearable />
      <el-select v-model="parseStatus" aria-label="解析状态">
        <el-option
          v-for="option in PARSE_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select v-model="exportStatus" aria-label="导出状态">
        <el-option
          v-for="option in EXPORT_STATUS_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-button class="ckqa-el-button ckqa-el-button--secondary" native-type="button" @click="selectFiltered">
        全选当前筛选结果
      </el-button>
      <el-button class="ckqa-el-button ckqa-el-button--ghost" native-type="button" @click="clearSelection">
        清空选择
      </el-button>
    </div>

    <el-table class="ckqa-el-table build-material-table" :data="filteredMaterials" row-key="id">
      <el-table-column label="选择" width="92">
        <template #default="{ row }">
          <el-checkbox
            class="build-material-checkbox"
            :model-value="isSelected(row.id)"
            :aria-label="`${isSelected(row.id) ? '取消勾选' : '勾选'} ${row.title}`"
            :data-testid="`build-material-checkbox-${row.id}`"
            @change="toggleMaterial(row.id)"
          />
        </template>
      </el-table-column>
      <el-table-column label="资料名" min-width="220">
        <template #default="{ row }">
          <div class="build-material-name" :data-status="row.meta" :data-testid="`build-material-row-${row.id}`">
            <span class="build-material-name__marker" aria-hidden="true"></span>
            <div>
              <strong>{{ row.title }}</strong>
              <small v-if="row.detail">{{ row.detail }}</small>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="解析状态" width="130">
        <template #default="{ row }">
          <StatusBadge :status="row.meta" :label="resolveParseStatusLabel(row.meta)" />
        </template>
      </el-table-column>
      <el-table-column label="导出状态" width="130">
        <template #default="{ row }">
          <StatusBadge :status="resolveExportStatus(row.id)" :label="resolveExportStatusLabel(row.id)" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          <span>{{ row.updatedAt || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
