<script setup>
import { computed, reactive } from 'vue'
import {
  ArrowRight,
  BookOpen,
  Eye,
  Pencil,
  Play,
  Search,
  Trash2,
  Users,
  Archive,
} from 'lucide-vue-next'

import StatusBadge from './StatusBadge.vue'
import {
  resolvePaginationState,
  resolveTableError,
  resolveTableRecordCount,
} from './data-table-shell-model.js'
import {
  filterRowsBySearchAndFilters,
  getCellText,
  getRowCells,
} from '../../views/pages/module-content.js'

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
  filterValues: { type: Object, default: () => ({}) },
  search: { type: [Object, String, Boolean], default: false },
  searchText: { type: String, default: '' },
  emptyText: { type: String, default: '暂无记录' },
  pagination: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: [Object, String], default: null },
})

const emit = defineEmits(['pageChange', 'filterChange', 'searchChange', 'rowAction'])

const filterValues = reactive({})
const localSearchText = reactive({ value: '' })

const paginationState = computed(() => resolvePaginationState(props.pagination))
const tableError = computed(() => resolveTableError(props.error))
const effectiveFilterValues = computed(() => ({ ...filterValues, ...props.filterValues }))
const effectiveSearchText = computed(() => props.searchText || localSearchText.value)
const searchConfig = computed(() => resolveSearchConfig(props.search))
const filteredRows = computed(() => filterRowsBySearchAndFilters(
  props.rows,
  props.filters,
  effectiveFilterValues.value,
  effectiveSearchText.value,
))
const recordCount = computed(() => resolveTableRecordCount(filteredRows.value, paginationState.value))
const hasRowActions = computed(() => filteredRows.value.some((row) => getRowActions(row).length > 0))

function isStatusCell(column, cell) {
  return cell?.kind === 'status' || column.includes('状态') || STATUS_VALUES.has(String(cell))
}

function isProgressCell(cell) {
  return cell?.kind === 'progress'
}

function isEmptyCell(cell) {
  return cell?.kind === 'empty'
}

function handlePageChange(page) {
  emit('pageChange', page)
}

function handleSearchInput(value) {
  localSearchText.value = value
  emit('searchChange', value)
}

function handleFilterChange(key, value) {
  filterValues[key] = value
  emit('filterChange', { key, value })
}

function resolveRowKey(row, index) {
  return row?.id ?? getRowCells(row).join('-') ?? index
}

function getCell(row, index) {
  return getRowCells(row)[index] ?? ''
}

function getCellLabel(row, index) {
  return getCellText(getCell(row, index))
}

function getColumnMinWidth(index) {
  const column = props.columns[index] ?? ''
  if (index === 0) return 260
  if (column.includes('状态')) return 112
  if (column.includes('进度') || column.includes('知识库')) return 190
  if (column.includes('更新时间')) return 168
  return 148
}

function getRowActions(row) {
  return Array.isArray(row?.actions)
    ? row.actions.filter((action) => action?.label && (action?.to || action?.key))
    : []
}

function getFilterValue(filter) {
  return effectiveFilterValues.value[filter.key] ?? ''
}

function getOptionLabel(option) {
  return typeof option === 'object' ? option.label : option
}

function getOptionValue(option) {
  return typeof option === 'object' ? option.value : option
}

function getFilterTagType(index) {
  return ['success', 'warning', 'info', 'danger'][index % 4]
}

function resolveSearchConfig(search) {
  if (!search) return null
  if (typeof search === 'string') {
    return { placeholder: search, ariaLabel: search }
  }
  if (typeof search === 'object') {
    return {
      placeholder: search.placeholder ?? '搜索',
      ariaLabel: search.ariaLabel ?? search.placeholder ?? '搜索列表',
    }
  }
  return { placeholder: '搜索', ariaLabel: '搜索列表' }
}

function resolveActionClass(action) {
  return [
    action.variant === 'primary'
      ? 'ckqa-el-button--primary'
      : action.variant === 'danger'
        ? 'ckqa-el-button--danger'
        : action.variant === 'warning'
          ? 'ckqa-el-button--warning'
          : 'ckqa-el-button--secondary',
    'ckqa-el-button',
    'table-action-button',
  ]
}

function resolveActionType(action) {
  if (action.variant === 'danger') return 'danger'
  if (action.variant === 'warning') return 'warning'
  return action.variant === 'primary' ? 'primary' : 'default'
}

function resolveActionIcon(action) {
  if (action.icon === 'edit') return Pencil
  if (action.icon === 'delete') return Trash2
  if (action.icon === 'archive') return Archive
  if (action.icon === 'users') return Users
  if (action.icon === 'knowledge') return BookOpen
  if (action.icon === 'parse') return Play
  return action.variant === 'primary' ? ArrowRight : Eye
}

function handleRowAction(row, action, index) {
  if (action.to || action.disabled) {
    return
  }
  emit('rowAction', { row, action, index })
}

function resolveProgressStatus(status) {
  if (status === 'failed') return 'exception'
  if (status === 'success') return 'success'
  if (status === 'pending' || status === 'running') return 'warning'
  return undefined
}
</script>

<template>
  <section class="panel data-table-shell" :aria-labelledby="`${title}-table-title`">
    <div class="panel-heading">
      <h2 :id="`${title}-table-title`">{{ title }}</h2>
      <span class="record-count">{{ recordCount }} 条</span>
    </div>

    <p v-if="tableError" class="inline-error">{{ tableError }}</p>

    <div v-if="searchConfig || filters.length" class="table-toolbar" aria-label="列表检索与筛选">
      <label v-if="searchConfig" class="table-toolbar-field table-toolbar-field--search">
        <el-tag class="table-toolbar-tag" type="primary" effect="light">检索</el-tag>
        <el-input
          class="table-search-input"
          :model-value="effectiveSearchText"
          :placeholder="searchConfig.placeholder"
          :aria-label="searchConfig.ariaLabel"
          clearable
          @update:model-value="handleSearchInput"
        >
          <template #prefix>
            <Search :size="16" aria-hidden="true" />
          </template>
        </el-input>
      </label>

      <label
        v-for="(filter, index) in filters"
        :key="filter.key"
        class="table-toolbar-field table-toolbar-field--filter"
      >
        <el-tag class="table-toolbar-tag" :type="getFilterTagType(index)" effect="light">
          {{ filter.label }}
        </el-tag>
        <el-select
          :model-value="getFilterValue(filter)"
          class="table-filter-select"
          :aria-label="filter.label"
          @update:model-value="handleFilterChange(filter.key, $event)"
        >
          <el-option
            v-for="option in filter.options"
            :key="getOptionValue(option)"
            :label="getOptionLabel(option)"
            :value="getOptionValue(option)"
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
        :fit="false"
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
              <div class="ckqa-table-identity">
                <img
                  v-if="row.thumbnailUrl"
                  class="ckqa-table-thumbnail"
                  :src="row.thumbnailUrl"
                  :alt="`${getCellLabel(row, index)}封面`"
                />
                <div class="ckqa-table-identity__copy">
                  <RouterLink v-if="row.to" :to="row.to">
                    <strong class="ckqa-table-primary">{{ getCellLabel(row, index) }}</strong>
                  </RouterLink>
                  <strong v-else class="ckqa-table-primary">{{ getCellLabel(row, index) }}</strong>
                  <small v-if="row.subtitle" class="ckqa-table-secondary">{{ row.subtitle }}</small>
                </div>
              </div>
            </template>
            <div v-else-if="isProgressCell(getCell(row, index))" class="table-progress-cell">
              <el-progress
                class="table-progress-cell__ring"
                type="circle"
                :width="42"
                :stroke-width="5"
                :percentage="Number(getCell(row, index).percent ?? 0)"
                :status="resolveProgressStatus(getCell(row, index).status)"
                :aria-label="`${column}：${getCell(row, index).summary}`"
              />
              <div class="table-progress-cell__copy">
                <strong>{{ getCell(row, index).summary }}</strong>
                <small>{{ getCell(row, index).detail }}</small>
              </div>
            </div>
            <StatusBadge
              v-else-if="isStatusCell(column, getCell(row, index))"
              :status="String(getCell(row, index).status ?? getCell(row, index))"
              :label="getCell(row, index).label"
            />
            <span v-else-if="isEmptyCell(getCell(row, index))" class="table-empty-cell">
              {{ getCell(row, index).label }}
            </span>
            <span v-else>{{ getCellLabel(row, index) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasRowActions"
          label="操作"
          fixed="right"
          width="390"
          class-name="ckqa-el-table__action-column"
          header-class-name="ckqa-el-table__action-column"
        >
          <template #default="{ row, $index }">
            <div class="data-table__actions">
              <template
                v-for="action in getRowActions(row)"
                :key="`${resolveRowKey(row, $index)}-${action.label}`"
              >
                <el-button
                  v-if="action.to"
                  :class="resolveActionClass(action)"
                  :type="resolveActionType(action)"
                  :disabled="Boolean(action.disabled)"
                  tag="router-link"
                  :to="action.to"
                  :title="action.title"
                >
                  <component :is="resolveActionIcon(action)" class="button-icon" :size="15" aria-hidden="true" />
                  {{ action.label }}
                </el-button>
                <el-button
                  v-else
                  :class="resolveActionClass(action)"
                  :type="resolveActionType(action)"
                  :disabled="Boolean(action.disabled)"
                  native-type="button"
                  :title="action.title"
                  @click="handleRowAction(row, action, $index)"
                >
                  <component :is="resolveActionIcon(action)" class="button-icon" :size="15" aria-hidden="true" />
                  {{ action.label }}
                </el-button>
              </template>
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
