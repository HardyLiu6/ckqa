<script setup>
import { computed, reactive } from 'vue'
import { ArrowRight, Eye } from 'lucide-vue-next'

import StatusBadge from './StatusBadge.vue'
import {
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
const tableError = computed(() => resolveTableError(props.error))
const filteredRows = computed(() => {
  return filterRowsByFilters(props.rows, props.filters, filterValues)
})
const recordCount = computed(() => resolveTableRecordCount(filteredRows.value, paginationState.value))
const hasRowActions = computed(() => filteredRows.value.some((row) => getRowActions(row).length > 0))

function isStatusCell(column, cell) {
  return column.includes('状态') || STATUS_VALUES.has(String(cell))
}

function handlePageChange(page) {
  emit('pageChange', page)
}

function resolveRowKey(row, index) {
  return row?.id ?? getRowCells(row).join('-') ?? index
}

function getCell(row, index) {
  return getRowCells(row)[index] ?? ''
}

function getColumnMinWidth(index) {
  if (index === 0) return 180
  return 138
}

function getRowActions(row) {
  return Array.isArray(row?.actions) ? row.actions.filter((action) => action?.label && action?.to) : []
}

function resolveActionClass(action) {
  return [
    action.variant === 'primary' ? 'ckqa-el-button--primary' : 'ckqa-el-button--secondary',
    'ckqa-el-button',
    'table-action-button',
  ]
}

function resolveActionType(action) {
  return action.variant === 'primary' ? 'primary' : 'default'
}

function resolveActionIcon(action) {
  return action.variant === 'primary' ? ArrowRight : Eye
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
        <el-select
          v-model="filterValues[filter.key]"
          class="table-filter-select"
          :aria-label="filter.label"
        >
          <el-option
            v-for="option in filter.options"
            :key="option"
            :label="option"
            :value="option"
          />
        </el-select>
      </label>
    </div>

    <div v-if="loading" class="empty-state">正在加载列表。</div>

    <div v-else class="table-scroll">
      <el-table
        class="ckqa-el-table"
        :data="filteredRows"
        :empty-text="emptyText"
        :row-key="resolveRowKey"
        :aria-label="title"
      >
        <el-table-column
          v-for="(column, index) in columns"
          :key="column"
          :label="column"
          :min-width="getColumnMinWidth(index)"
          show-overflow-tooltip
        >
          <template #default="{ row, $index }">
            <template v-if="index === 0">
              <RouterLink v-if="row.to" :to="row.to">
                <strong class="ckqa-table-primary">{{ getCell(row, index) }}</strong>
              </RouterLink>
              <strong v-else class="ckqa-table-primary">{{ getCell(row, index) }}</strong>
              <small v-if="row.subtitle" class="ckqa-table-secondary">{{ row.subtitle }}</small>
            </template>
            <StatusBadge
              v-else-if="isStatusCell(column, getCell(row, index))"
              :status="String(getCell(row, index))"
            />
            <span v-else>{{ getCell(row, index) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasRowActions"
          label="操作"
          fixed="right"
          min-width="220"
          class-name="ckqa-el-table__action-column"
        >
          <template #default="{ row, $index }">
            <div class="data-table__actions">
              <el-button
                v-for="action in getRowActions(row)"
                :key="`${resolveRowKey(row, $index)}-${action.label}`"
                :class="resolveActionClass(action)"
                :type="resolveActionType(action)"
                tag="router-link"
                :to="action.to"
              >
                <component :is="resolveActionIcon(action)" class="button-icon" :size="15" aria-hidden="true" />
                {{ action.label }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="paginationState" class="pagination-bar" aria-label="分页">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="paginationState.page"
        :page-size="paginationState.size"
        :total="paginationState.total"
        :pager-count="5"
        :disabled="loading"
        @current-change="handlePageChange"
      />
    </div>
  </section>
</template>
