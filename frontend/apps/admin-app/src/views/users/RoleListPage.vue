<script setup>
/**
 * 角色列表页（M7 · 任务 3.4）。
 *
 * 结构对齐 `views/knowledge-bases/KbListPage.vue` 与任务 3.3 的 UserListPage：
 * - `CkPageHero` 页头，actions 位承载「数据来自用户视图聚合」告警芯片（仅 `aggregated` 模式）
 *   与「新建角色」按钮（M7 内 `disabled`，tooltip 指向后续里程碑开放）；
 * - `<el-table>` 渲染六列：角色编码 / 名称 / 状态 / 权限范围 / 更新时间 / 操作；
 * - 权限范围列通过 `summarizePermissionScope` 做「超过 3 项显示前 3 项 + 等 N 项」的裁剪；
 * - 写操作按钮（启用/停用）同样受 `authStore.canAccess(['role:write'])` 守护且在 M7 内 `disabled`；
 * - 加载 / 空 / 错误三态分别走 `CkSkeleton` / `CkEmptyState` / 错误面板。
 *
 * 与既有 composable / API 契约（见 `composables/useRoleListPage.js` 与 `api/roles.js`）一致：
 * - `dataSourceHint` 由 service 的 `source` 驱动，无需页面再做业务判定；
 * - 分页/关键字通过 composable 暴露的 `setPage / setPageSize / setKeyword` 同步到 `route.query`。
 */
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPager from '../../components/common/CkPager.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'

import { useRoleListPage } from '../../composables/useRoleListPage.js'
import { useAuthStore } from '../../stores/auth.js'
import {
  ROLE_LIST_COPY,
  statusLabel,
  summarizePermissionScope,
} from './role-page-copy.js'

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
} = useRoleListPage({ route, router })

onMounted(load)

// 外部路由 query 变化（浏览器前进/后退、其他组件手动 push）也触发重新加载，
// 与 UserListPage 的观察策略保持一致。
watch(
  () => [route.query.page, route.query.size, route.query.keyword],
  () => load(),
)

/** 回车或 `clear` 事件触发搜索：`el-input` 的 `@keyup.enter` / `@clear` 共用此处理器。 */
function handleSearch(event) {
  const target = event?.target ?? null
  // el-input 的 clear 事件不带 target；从 `keyword.value` 本地态读当前值即可。
  const nextRaw = target && 'value' in target ? target.value : keyword.value
  setKeyword(typeof nextRaw === 'string' ? nextRaw : '')
}
</script>

<template>
  <div class="role-list-page" data-testid="role-list-page">
    <CkPageHero
      :eyebrow="ROLE_LIST_COPY.eyebrow"
      :title="ROLE_LIST_COPY.title"
      :subtitle="ROLE_LIST_COPY.subtitle"
    >
      <template #actions>
        <CkStatusPill
          v-if="dataSourceHint === 'aggregated'"
          tone="warning"
          :label="ROLE_LIST_COPY.dataSourceHint.aggregatedLabel"
          data-testid="role-aggregated-hint"
        />
        <el-input
          v-model="keyword"
          class="role-list-page__search"
          placeholder="搜索角色编码或名称"
          clearable
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button
          v-if="authStore.canAccess(['role:write'])"
          type="primary"
          disabled
          :title="ROLE_LIST_COPY.writeActions.disabledHint"
          data-testid="role-create-button"
        >
          {{ ROLE_LIST_COPY.writeActions.createLabel }}
        </el-button>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state === 'loading'" variant="row" :count="6" />

    <CkEmptyState
      v-else-if="state === 'empty'"
      icon="◻"
      :title="ROLE_LIST_COPY.empty.title"
      :description="ROLE_LIST_COPY.empty.description"
    />

    <section
      v-else-if="state === 'error'"
      class="role-list-page__error"
      role="alert"
    >
      <p class="role-list-page__error-title">角色列表加载失败</p>
      <p class="role-list-page__error-message">{{ error?.message || '请求失败' }}</p>
      <el-button size="small" @click="load">重试</el-button>
    </section>

    <template v-else-if="state === 'success'">
      <el-table
        class="role-list-page__table"
        :data="rows"
        aria-label="角色列表"
        data-testid="role-table"
      >
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <CkStatusPill
              :status="row.status"
              :label="statusLabel(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column label="权限范围" min-width="260">
          <template #default="{ row }">
            <span class="role-list-page__scope">
              {{ summarizePermissionScope(row.permissions) || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">
            <span>{{ row.updatedAt || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="authStore.canAccess(['role:write'])"
              size="small"
              disabled
              :title="ROLE_LIST_COPY.writeActions.disabledHint"
              :data-testid="`role-toggle-${row.code}`"
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
.role-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-5);
}
.role-list-page__search {
  width: 220px;
}
.role-list-page__error {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-2);
  padding: var(--ckqa-space-5) var(--ckqa-space-6);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
}
.role-list-page__error-title {
  margin: 0;
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.role-list-page__error-message {
  margin: 0;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.role-list-page__scope {
  color: var(--ckqa-text);
  font-size: var(--ckqa-text-sm-size);
}
</style>
