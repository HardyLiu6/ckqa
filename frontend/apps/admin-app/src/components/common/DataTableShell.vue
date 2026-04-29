<script setup>
import { computed, reactive } from 'vue'

import StatusBadge from './StatusBadge.vue'
import {
  resolvePageChangeTarget,
  resolvePaginationState,
  resolveTableError,
  resolveTableRecordCount,
} from './data-table-shell-model.js'
import { filterRowsByFilters, getRowCells } from '../../views/pages/module-content.js'

const STATUS_VALUES = new Set([
  'active',
  'archived',
  'draft',
  'indexing',
  'success',
  'running',
  'failed',
  'pending',
  'done',
  'ready',
  'blocked',
])

const props = defineProps({
  title: { type: String, required: true },
  columns: { type: Array, required: true },
  rows: { type: Array, required: true },
  filters: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无记录' },
  pagination: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: [Object, String], default: null },
})

const emit = defineEmits(['pageChange'])

const filterValues = reactive({})

const paginationState = computed(() => resolvePaginationState(props.pagination))
const recordCount = computed(() => resolveTableRecordCount(filteredRows.value, paginationState.value))
const tableError = computed(() => resolveTableError(props.error))
const filteredRows = computed(() => {
  return filterRowsByFilters(props.rows, props.filters, filterValues)
})

function isStatusCell(column, cell) {
  return column.includes('状态') || STATUS_VALUES.has(String(cell))
}

function emitPageChange(direction) {
  emit('pageChange', resolvePageChangeTarget(paginationState.value, direction))
}

function resolveRowKey(row, index) {
  return row?.id ?? getRowCells(row).join('-') ?? index
}
</script>

<template>
  <section class="panel data-table-shell" :aria-labelledby="`${title}-table-title`">
    <div class="panel-heading">
      <h2 :id="`${title}-table-title`">{{ title }}</h2>
      <span class="record-count">{{ recordCount }} 条</span>
    </div>

    <p v-if="tableError" class="inline-error">{{ tableError }}</p>

    <div v-if="filters.length" class="filter-bar" aria-label="列表筛选">
      <label v-for="filter in filters" :key="filter.key">
        <span>{{ filter.label }}</span>
        <select v-model="filterValues[filter.key]">
          <option v-for="option in filter.options" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>

    <div v-if="loading" class="empty-state">正在加载列表。</div>

    <div v-else class="table-scroll">
      <table class="data-table" :aria-label="title">
        <thead>
          <tr>
            <th v-for="column in columns" :key="column" scope="col">{{ column }}</th>
          </tr>
        </thead>
        <tbody v-if="filteredRows.length">
          <tr v-for="(row, rowIndex) in filteredRows" :key="resolveRowKey(row, rowIndex)">
            <td
              v-for="(cell, index) in getRowCells(row)"
              :key="`${resolveRowKey(row, rowIndex)}-${index}`"
            >
              <template v-if="index === 0">
                <RouterLink v-if="row.to" :to="row.to"><strong>{{ cell }}</strong></RouterLink>
                <strong v-else>{{ cell }}</strong>
                <small v-if="row.subtitle">{{ row.subtitle }}</small>
              </template>
              <StatusBadge v-else-if="isStatusCell(columns[index] ?? '', cell)" :status="String(cell)" />
              <span v-else>{{ cell }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-if="!loading && !filteredRows.length" class="empty-state">{{ emptyText }}</p>

    <div v-if="paginationState" class="pagination-bar" aria-label="分页">
      <button
        class="secondary-button compact"
        type="button"
        :disabled="paginationState.page <= 1 || loading"
        @click="emitPageChange('prev')"
      >
        上一页
      </button>
      <span>第 {{ paginationState.page }} / {{ Math.max(paginationState.pages, 1) }} 页</span>
      <button
        class="secondary-button compact"
        type="button"
        :disabled="paginationState.page >= Math.max(paginationState.pages, 1) || loading"
        @click="emitPageChange('next')"
      >
        下一页
      </button>
    </div>
  </section>
</template>
