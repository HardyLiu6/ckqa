<script setup>
import { computed, reactive } from 'vue'

import StatusBadge from './StatusBadge.vue'
import { filterRowsByFilters } from '../../views/pages/module-content.js'

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
})

const filterValues = reactive({})

const filteredRows = computed(() => {
  return filterRowsByFilters(props.rows, props.filters, filterValues)
})

function isStatusCell(column, cell) {
  return column.includes('状态') || STATUS_VALUES.has(String(cell))
}
</script>

<template>
  <section class="panel data-table-shell" :aria-labelledby="`${title}-table-title`">
    <div class="panel-heading">
      <h2 :id="`${title}-table-title`">{{ title }}</h2>
      <span class="record-count">{{ filteredRows.length }} 条</span>
    </div>

    <div v-if="filters.length" class="filter-bar" aria-label="列表筛选">
      <label v-for="filter in filters" :key="filter.key">
        <span>{{ filter.label }}</span>
        <select v-model="filterValues[filter.key]">
          <option v-for="option in filter.options" :key="option" :value="option">{{ option }}</option>
        </select>
      </label>
    </div>

    <div class="table-scroll">
      <table class="data-table" :aria-label="title">
        <thead>
          <tr>
            <th v-for="column in columns" :key="column" scope="col">{{ column }}</th>
          </tr>
        </thead>
        <tbody v-if="filteredRows.length">
          <tr v-for="row in filteredRows" :key="row.join('-')">
            <td v-for="(cell, index) in row" :key="`${row.join('-')}-${index}`">
              <template v-if="index === 0">
                <strong>{{ cell }}</strong>
                <small v-if="row[1]">#{{ row[1] }}</small>
              </template>
              <StatusBadge v-else-if="isStatusCell(columns[index] ?? '', cell)" :status="String(cell)" />
              <span v-else>{{ cell }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <p v-if="!filteredRows.length" class="empty-state">{{ emptyText }}</p>
  </section>
</template>
