<script setup>
/**
 * 用户列表页（M7 · 任务 3.3）。
 *
 * 结构与 `RoleListPage.vue` / `PermissionListPage.vue` 对齐（同一套骨架）：
 * - `CkPageHero` 页头，actions 位承载「数据来自用户视图聚合」告警芯片（仅
 *   `dataSourceHint === 'aggregated'` 时出现）+ 关键字搜索框 + 「新建用户」按钮
 *   （M7 内 `disabled`，tooltip 指向后续里程碑开放）；
 * - `<el-table>` 渲染六列：用户编码 / 用户名 / 展示名称 / 状态 / 角色 / 最近登录时间；
 * - 状态列走 `CkStatusPill`；角色列将 `user.roles[]` 压成"、"拼接字符串展示；
 * - 写操作按钮（新建 / 启用停用）均受 `authStore.canAccess(['user:write'])` 守护，
 *   M7 内 `disabled` 并带 tooltip "后续里程碑开放"；
 * - 加载 / 空 / 错误三态分别走 `CkSkeleton` / `CkEmptyState` / 错误面板；
 * - `useUserListPage` 当前数据源固定是 `'api'`，故 `dataSourceHint === 'aggregated'`
 *   分支在运行时永不触发；保留模板结构是为了与 Role / Permission 两页一致，
 *   未来 API 变更后直接复用。
 *
 * 文件行数预算：≤ 280 行（含样式）。
 */
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPager from '../../components/common/CkPager.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { useUserListPage } from '../../composables/useUserListPage.js'
import { useAuthStore } from '../../stores/auth.js'
import { USER_LIST_COPY, statusLabel, summarizeRoles } from './user-page-copy.js'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const {
  state,
  rows,
  pagination,
  error,
  keyword,
  dataSourceHint,
  load,
  setPage,
  setPageSize,
  setKeyword,
} = useUserListPage({ route, router })

onMounted(load)

// 外部路由 query 变化（浏览器前进/后退、其他组件手动 push）也触发重新加载，
// 与 RoleListPage / PermissionListPage 的观察策略保持一致。
watch(
  () => [route.query.page, route.query.size, route.query.keyword],
  () => load(),
)

/** 回车或 `clear` 事件触发搜索；`el-input` 的 `@keyup.enter` / `@clear` 复用此处理器。 */
function handleSearch(event) {
  const target = event?.target ?? null
  // el-input 的 clear 事件不带 target；从 `keyword.value` 本地态读当前值即可。
  const nextRaw = target && 'value' in target ? target.value : keyword.value
  setKeyword(typeof nextRaw === 'string' ? nextRaw : '')
}
</script>

<template>
  <div class="user-list-page" data-testid="user-list-page">
    <CkPageHero
      :eyebrow="USER_LIST_COPY.eyebrow"
      :title="USER_LIST_COPY.title"
      :subtitle="USER_LIST_COPY.subtitle"
    >
      <template #actions>
        <CkStatusPill
          v-if="dataSourceHint === 'aggregated'"
          tone="warning"
          :label="USER_LIST_COPY.dataSourceHint.aggregatedLabel"
          data-testid="user-aggregated-hint"
        />
        <el-input
          v-model="keyword"
          class="user-list-page__search"
          placeholder="搜索用户编码或用户名"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button
          v-if="authStore.canAccess(['user:write'])"
          type="primary"
          disabled
          :title="USER_LIST_COPY.writeActions.disabledHint"
          data-testid="user-create-button"
        >
          {{ USER_LIST_COPY.writeActions.createLabel }}
        </el-button>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state === 'loading'" variant="row" :count="6" />

    <CkEmptyState
      v-else-if="state === 'empty'"
      icon="◻"
      :title="USER_LIST_COPY.empty.title"
      :description="USER_LIST_COPY.empty.description"
    />

    <section
      v-else-if="state === 'error'"
      class="user-list-page__error"
      role="alert"
    >
      <p class="user-list-page__error-title">用户列表加载失败</p>
      <p class="user-list-page__error-message">{{ error?.message || '请求失败' }}</p>
      <el-button size="small" @click="load">重试</el-button>
    </section>

    <template v-else-if="state === 'success'">
      <el-table
        class="user-list-page__table"
        :data="rows"
        aria-label="用户列表"
        data-testid="user-table"
      >
        <el-table-column prop="code" label="用户编码" min-width="140" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="展示名称" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <CkStatusPill
              :status="row.status"
              :label="statusLabel(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <span class="user-list-page__roles">
              {{ summarizeRoles(row.roles) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录时间" width="180">
          <template #default="{ row }">
            <span>{{ row.lastLoginAt || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="authStore.canAccess(['user:write'])"
              size="small"
              disabled
              :title="USER_LIST_COPY.writeActions.disabledHint"
              :data-testid="`user-toggle-${row.code}`"
            >
              {{ row.status === 'active' ? '停用' : '启用' }}
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
  </div>
</template>

<style scoped lang="scss">
.user-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.user-list-page__search {
  width: 220px;
}
.user-list-page__error {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-5) var(--ckqa-space-6);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}
.user-list-page__error-title {
  margin: 0;
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.user-list-page__error-message {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.user-list-page__roles {
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-sm-size);
}
</style>
