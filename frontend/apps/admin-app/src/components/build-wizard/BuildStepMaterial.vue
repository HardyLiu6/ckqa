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

watch(materialIds, (next) => {
  selectedIds.value = [...next.map((id) => String(id))]
}, { immediate: true })

function resolveExportStatus(id) {
  return exportRows.value.get(String(id))?.status ?? 'missing'
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
        <el-option label="解析状态" value="全部" />
        <el-option label="done" value="done" />
        <el-option label="pending" value="pending" />
        <el-option label="processing" value="processing" />
        <el-option label="failed" value="failed" />
      </el-select>
      <el-select v-model="exportStatus" aria-label="导出状态">
        <el-option label="导出状态" value="全部" />
        <el-option label="complete" value="complete" />
        <el-option label="missing" value="missing" />
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
          <el-button
            class="ckqa-el-button ckqa-el-button--ghost"
            native-type="button"
            :data-testid="`build-material-select-${row.id}`"
            @click="toggleMaterial(row.id)"
          >
            {{ isSelected(row.id) ? '已选' : '选择' }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="资料名" min-width="220">
        <template #default="{ row }">
          <div :data-testid="`build-material-row-${row.id}`">
            <strong>{{ row.title }}</strong>
            <small>{{ row.detail || '无 MD5 摘要' }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="解析状态" width="130">
        <template #default="{ row }">
          <StatusBadge :status="row.meta" />
        </template>
      </el-table-column>
      <el-table-column label="导出状态" width="130">
        <template #default="{ row }">
          <StatusBadge :status="resolveExportStatus(row.id)" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">
          <span>{{ row.updatedAt || row.detail || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
