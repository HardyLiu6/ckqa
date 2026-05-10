<script setup>
/**
 * 权限列表页（M7 任务 3.5）。
 *
 * 设计要点（参见 design.md §5.3）：
 * - `CkPageHero.actions` 位放 `el-select` 资源筛选器（`RESOURCE_OPTIONS`）；
 *   当 composable 暴露 `dataSourceHint === 'aggregated'`（主接口 404 走用户视图聚合）
 *   时，额外渲染一枚 `CkStatusPill tone="warning"`，提示"数据来自用户视图聚合"。
 * - 表格列：权限编码 / 权限名称 / 资源 / 操作 / 状态（来自 `PERMISSION_LIST_COLUMNS`）；
 *   资源列展示 `RESOURCE_OPTIONS` 对应的中文 label，避免把后端 key（如 `qa`）直出。
 * - 写操作按钮 `v-if="authStore.canAccess(['permission:write'])"` 守护；
 *   M7 内 `disabled` 并带 tooltip "后续里程碑开放"。
 * - 所有文案来自 `permission-page-copy.js`，不允许新的裸字符串。
 *
 * 行数预算：≤ 220 行（含样式）。
 */
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus } from 'lucide-vue-next'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPager from '../../components/common/CkPager.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'
import { usePermissionListPage } from '../../composables/usePermissionListPage.js'
import { authStore } from '../../stores/auth.js'
import {
  PERMISSION_LIST_COPY,
  PERMISSION_LIST_COLUMNS,
  RESOURCE_OPTIONS,
  statusLabel,
} from './permission-page-copy.js'

const route = useRoute()
const router = useRouter()

const {
  state,
  rows,
  pagination,
  error,
  resource,
  dataSourceHint,
  load,
  setPage,
  setPageSize,
  setResource,
} = usePermissionListPage({ route, router })

onMounted(load)
watch(() => route.query, () => load(), { deep: true })

/** `<el-select>` v-model：写入时经 composable 的 setResource，保证与 query 同步。 */
const resourceModel = computed({
  get: () => resource.value ?? '',
  set: (next) => setResource(next),
})

/** 把后端 resource key 翻译为中文 label（`course` → `课程`），未知值原样返回。 */
function resourceLabel(value) {
  const normalized = typeof value === 'string' ? value.toLowerCase() : ''
  const matched = RESOURCE_OPTIONS.find((option) => option.value === normalized)
  return matched && matched.value ? matched.label : (value ?? '')
}
</script>

<template>
  <section class="ck-permission-list-page">
    <CkPageHero
      :eyebrow="PERMISSION_LIST_COPY.eyebrow"
      :title="PERMISSION_LIST_COPY.title"
      :subtitle="PERMISSION_LIST_COPY.subtitle"
    >
      <template #actions>
        <CkStatusPill
          v-if="dataSourceHint === 'aggregated'"
          tone="warning"
          :label="PERMISSION_LIST_COPY.aggregatedHint"
        />
        <el-select
          v-model="resourceModel"
          size="default"
          class="ck-permission-list-page__filter"
          :placeholder="PERMISSION_LIST_COPY.filter.resourcePlaceholder"
          :aria-label="PERMISSION_LIST_COPY.filter.resourceLabel"
        >
          <el-option
            v-for="option in RESOURCE_OPTIONS"
            :key="option.value || '__all__'"
            :value="option.value"
            :label="option.label"
          />
        </el-select>
        <el-button
          v-if="authStore.canAccess(['permission:write'])"
          type="primary"
          disabled
          :title="PERMISSION_LIST_COPY.writeLockedTooltip"
        >
          <Plus :size="16" />新建权限
        </el-button>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state === 'loading'" variant="row" :count="6" />

    <CkEmptyState
      v-else-if="state === 'empty'"
      :title="PERMISSION_LIST_COPY.empty.title"
      :description="PERMISSION_LIST_COPY.empty.description"
    />

    <template v-else-if="state === 'success'">
      <el-table
        :data="rows"
        aria-label="权限列表"
        data-testid="permission-table"
      >
        <el-table-column
          v-for="column in PERMISSION_LIST_COLUMNS.filter((c) => c.key !== 'resource' && c.key !== 'status')"
          :key="column.key"
          :prop="column.prop"
          :label="column.label"
          :min-width="column.minWidth"
          :width="column.width"
        />
        <el-table-column label="资源" width="120">
          <template #default="{ row }">
            <CkStatusPill tone="neutral" size="sm" :label="resourceLabel(row.resource)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <CkStatusPill :status="row.status" :label="statusLabel(row.status)" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="authStore.canAccess(['permission:write'])"
          label="操作"
          width="140"
          fixed="right"
        >
          <template #default>
            <el-button
              size="small"
              disabled
              :title="PERMISSION_LIST_COPY.writeLockedTooltip"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <CkPager
        variant="page"
        :page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        @change-page="setPage"
        @change-page-size="setPageSize"
      />
    </template>

    <section v-else class="ck-error-panel" role="alert">
      {{ error?.message }}
    </section>
  </section>
</template>

<style scoped lang="scss">
.ck-permission-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-4);
}
.ck-permission-list-page__filter {
  width: 160px;
}
.ck-error-panel {
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-muted);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
</style>
