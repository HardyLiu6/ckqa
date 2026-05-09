# 管理员端重设计 M4 课程 / 资料模块拆分实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `views/pages/ModulePage.vue`（3957 行巨石）中"课程域"职责切到独立页面：课程列表 / 课程详情（4 Tab）/ 课程成员 / 课程资料 / 资料详情（4 Tab）；新增 `<CkResourceCard>` 与 `<CkInfoTable>` 通用组件；把 `useMaterialLifecycle / useLongTaskState` 从 `views/pages/*.js` 提到 `composables/`。

**Architecture:** 在 M1+M2 地基之上，按"页面切片"重构。`ModulePage.vue` 不删，但 `componentKey: 'ModulePage'` 在课程 / 资料相关路由中全部替换为新页面。资料详情新增独立路由组件 `MaterialDetailPage.vue`，含 4 Tab：解析进度 / 解析结果 / 知识库引用 / 操作日志（Tab 1 沿用 SSE）。复用 M5 即将引入的 `CkSplitProgress` 与 `CkLogStream` 时，通过临时 placeholder 解耦——M5 接入后会替换为真实组件。

**Tech Stack:** Vue 3.5 Composition API、Element Plus 2.13、Pinia 3、SSE EventSource、`node:test`、Playwright。

**前置依赖：**

- M1+M2 已合并：`<CkPageHero>` / `<CkSkeleton>` / `<CkPager>` / `<CkStatusPill>` / `<CkEmptyState>` / `<CkBreadcrumbs>` / DetailLayout 就绪。
- M3 Dashboard 已合并：`<CkPipelineHero>` / `<CkActivityFeed>` / `<CkTaskList>` / `<CkQuickActions>` 就绪。
- 视觉打磨已合并：`.ck-glass-card` / `.ck-pressable` 工具类、`--ckqa-shadow-card` / `--ckqa-shadow-card-hover` 阴影 token、品牌常量 `BRAND`（`src/copy/brand.js`）就绪。新增卡片组件应优先使用玻璃态工具类和卡片阴影 token。
- 现有 `views/pages/module-content.js` / `module-loaders.js` 维持可用，本计划只做"按页迁移"，不重写底层。
- `useScopeStore.requestParams()` 已就绪，列表请求使用 `params.courseId`。
- 后端无新增依赖；解析 SSE 接口 `/api/v1/material-parse-tasks/:taskId/stream` 已存在，沿用。

**完成判据：**

1. 全部单元测试通过，新增 6 个 model 测试 + 2 个 composable 测试全绿。
2. `pnpm run build` 通过。
3. dev 启动后核心路径可用：
   - `/app/courses` 走新 CourseListPage（卡片网格）；
   - `/app/courses/:id` 走 CourseDetailPage（DetailLayout + 4 Tab）；
   - `/app/courses/:id/members` 走 CourseMembersTab（页面级）；
   - `/app/courses/:id/materials` 走 CourseMaterialsTab；
   - `/app/materials/:id` 走 MaterialDetailPage（4 Tab，默认 Tab 1 解析进度）；
   - `/app/materials/:id/parse-results` 走 MaterialDetailPage（默认 Tab 2）；
4. Playwright `e2e/material-detail.spec.js` 覆盖：进入资料详情，看到 4 Tab 标签 + Tab 1 SSE 占位显示。
5. `ModulePage.vue` 中处理课程 / 资料的代码分支保留作为兜底，但路由不再走它。
6. 面包屑通过 `route.meta.contextChain`（在新页面 `onMounted` 注入）正确显示"生产 / 课程 / 操作系统课程 / 课程资料 / 数据结构第3章.pdf"。

---

## 文件清单

### 新建

- `src/components/common/CkResourceCard.vue` + `resource-card-model.js` + `resource-card-model.test.js`
- `src/components/common/CkInfoTable.vue` + `info-table-model.js` + `info-table-model.test.js`
- `src/views/courses/CourseListPage.vue`
- `src/views/courses/CourseDetailPage.vue`
- `src/views/courses/tabs/CourseOverviewTab.vue`
- `src/views/courses/tabs/CourseMembersTab.vue`
- `src/views/courses/tabs/CourseMaterialsTab.vue`
- `src/views/courses/tabs/CourseKnowledgeBasesTab.vue`
- `src/views/materials/MaterialDetailPage.vue`
- `src/views/materials/tabs/MaterialParseProgressTab.vue`
- `src/views/materials/tabs/MaterialParseResultsTab.vue`
- `src/views/materials/tabs/MaterialKbReferencesTab.vue`
- `src/views/materials/tabs/MaterialAuditLogTab.vue`
- `src/views/courses/course-page-copy.js`
- `src/views/materials/material-page-copy.js`
- `src/composables/useMaterialLifecycle.js` + `use-material-lifecycle.test.js`
- `src/composables/useLongTaskState.js` + `use-long-task-state.test.js`
- `src/composables/useResourceTabs.js` + `use-resource-tabs.test.js`
- `e2e/course-flow.spec.js`
- `e2e/material-detail.spec.js`

### 修改

- `src/router/routes.js` — 课程 / 资料路由 `componentKey` 全部从 `'ModulePage'` 改到具体页面
- `src/router/index.js` — 注册新组件
- `src/copy/admin.js` — 加 `course / material` 段
- `src/app-shell.test.js` — 同步 import + smoke
- `src/views/pages/ModulePage.vue` — 不动，但其中"course / material"分支仅作为兜底保留（不再被任何路由命中，可在 M7 末尾再删）

### 删除

- 暂不删除 `views/pages/ModulePage.vue`（M7 收尾再处理）

---

## Task 1：CkResourceCard 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/resource-card-model.js`
- Create: `src/components/common/resource-card-model.test.js`
- Create: `src/components/common/CkResourceCard.vue`

资源列表卡片：标题 + 描述 + 状态 pill + 元信息 + 操作（slot）。

- [ ] **Step 1: 写失败测试**

`src/components/common/resource-card-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveCardStatus,
  formatMetaEntries,
  truncate,
} from './resource-card-model.js'

test('resolveCardStatus 返回 { tone, label }', () => {
  assert.deepEqual(resolveCardStatus('active'), { tone: 'success', label: '已激活' })
  assert.deepEqual(resolveCardStatus('running'), { tone: 'running', label: '进行中' })
  assert.deepEqual(resolveCardStatus('failed'), { tone: 'danger', label: '异常' })
  assert.deepEqual(resolveCardStatus(null), { tone: 'neutral', label: '' })
})

test('resolveCardStatus 自定义 label 覆盖默认', () => {
  assert.deepEqual(resolveCardStatus('active', '已上线'), { tone: 'success', label: '已上线' })
})

test('formatMetaEntries 空 / 非数组 容错', () => {
  assert.deepEqual(formatMetaEntries(null), [])
  assert.deepEqual(formatMetaEntries([]), [])
})

test('formatMetaEntries 拼接 label: value，空 value 跳过', () => {
  const entries = formatMetaEntries([
    { label: '课程', value: '操作系统' },
    { label: '资料数', value: 12 },
    { label: '空字段', value: '' },
  ])
  assert.deepEqual(entries, [
    { label: '课程', value: '操作系统' },
    { label: '资料数', value: '12' },
  ])
})

test('truncate 超长截断 + …', () => {
  assert.equal(truncate('1234567890', 5), '12345…')
  assert.equal(truncate('短', 5), '短')
  assert.equal(truncate(null, 5), '')
})
```

- [ ] **Step 2: 写实现**

`src/components/common/resource-card-model.js`：

```js
const STATUS_MAP = {
  active: { tone: 'success', label: '已激活' },
  running: { tone: 'running', label: '进行中' },
  pending: { tone: 'warning', label: '待处理' },
  failed: { tone: 'danger', label: '异常' },
  archived: { tone: 'blocked', label: '已归档' },
  draft: { tone: 'blocked', label: '草稿' },
  ready: { tone: 'success', label: '已就绪' },
}

export function resolveCardStatus(status, customLabel) {
  if (!status) return { tone: 'neutral', label: customLabel || '' }
  const entry = STATUS_MAP[status]
  if (!entry) return { tone: 'neutral', label: customLabel || String(status) }
  return { tone: entry.tone, label: customLabel || entry.label }
}

export function formatMetaEntries(entries) {
  if (!Array.isArray(entries)) return []
  return entries
    .map((entry) => ({
      label: entry.label,
      value: entry.value == null ? '' : String(entry.value),
    }))
    .filter((entry) => entry.value !== '')
}

export function truncate(text, max) {
  if (!text) return ''
  const safe = String(text)
  if (safe.length <= max) return safe
  return `${safe.slice(0, max)}…`
}
```

- [ ] **Step 3: 实现 Vue 组件**

`src/components/common/CkResourceCard.vue`：

```vue
<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'

import {
  resolveCardStatus,
  formatMetaEntries,
} from './resource-card-model.js'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  status: { type: String, default: '' },
  statusLabel: { type: String, default: '' },
  meta: { type: Array, default: () => [] },
  to: { type: [String, Object], default: null },
  cover: { type: String, default: '' },
})

const resolvedStatus = computed(() => resolveCardStatus(props.status, props.statusLabel))
const metaEntries = computed(() => formatMetaEntries(props.meta))
</script>

<template>
  <article class="ck-resource-card ck-glass-card">
    <RouterLink v-if="to" :to="to" class="ck-resource-card-link">
      <figure v-if="cover" class="ck-resource-card-cover">
        <img :src="cover" :alt="title" loading="lazy" />
      </figure>
      <div class="ck-resource-card-body">
        <header class="ck-resource-card-header">
          <h3 class="ck-resource-card-title">{{ title }}</h3>
          <CkStatusPill v-if="resolvedStatus.label" :tone="resolvedStatus.tone" :label="resolvedStatus.label" size="sm" />
        </header>
        <p v-if="description" class="ck-resource-card-description">{{ description }}</p>
        <ul v-if="metaEntries.length" class="ck-resource-card-meta">
          <li v-for="entry in metaEntries" :key="entry.label">
            <span>{{ entry.label }}</span>
            <strong>{{ entry.value }}</strong>
          </li>
        </ul>
      </div>
    </RouterLink>
    <div v-if="$slots.actions" class="ck-resource-card-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<style scoped lang="scss">
.ck-resource-card {
  position: relative;
  overflow: hidden;
  transition: box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-resource-card:hover { box-shadow: var(--ckqa-shadow-card-hover); }
.ck-resource-card-link { display: flex; flex-direction: column; gap: var(--ckqa-space-3); color: inherit; text-decoration: none; }
.ck-resource-card-cover { margin: 0; aspect-ratio: 16 / 9; background: var(--ckqa-surface-muted); overflow: hidden; }
.ck-resource-card-cover img { width: 100%; height: 100%; object-fit: cover; }
.ck-resource-card-body { padding: var(--ckqa-space-3) var(--ckqa-space-4) var(--ckqa-space-4); display: flex; flex-direction: column; gap: var(--ckqa-space-2); }
.ck-resource-card-header { display: flex; justify-content: space-between; gap: var(--ckqa-space-2); align-items: center; }
.ck-resource-card-title {
  margin: 0;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ck-resource-card-description {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.ck-resource-card-meta {
  list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--ckqa-space-2);
  font-size: var(--ckqa-text-xs-size);
}
.ck-resource-card-meta span { color: var(--ckqa-text-weak); margin-right: 4px; }
.ck-resource-card-meta strong { color: var(--ckqa-text); font-weight: var(--ckqa-fw-medium); }
.ck-resource-card-actions {
  position: absolute; top: 8px; right: 8px;
  display: flex; gap: 6px;
}
</style>
```

- [ ] **Step 4: Smoke + 跑测试 + Commit**

```js
// app-shell.test.js
import { resolveCardStatus, formatMetaEntries } from './components/common/resource-card-model.js'
test('CkResourceCard model exports are wired', () => {
  assert.equal(resolveCardStatus('active').tone, 'success')
  assert.deepEqual(formatMetaEntries([]), [])
})
```

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/resource-card-model.js \
        frontend/apps/admin-app/src/components/common/resource-card-model.test.js \
        frontend/apps/admin-app/src/components/common/CkResourceCard.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkResourceCard 资源卡片组件 + 模型 + 测试"
```

---

## Task 2：CkInfoTable 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/info-table-model.js`
- Create: `src/components/common/info-table-model.test.js`
- Create: `src/components/common/CkInfoTable.vue`

详情页 key-value 信息块：左列 label，右列 value（支持 raw HTML 安全渲染）。

- [ ] **Step 1: 写失败测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { sanitizeEntries, splitEntriesIntoColumns } from './info-table-model.js'

test('sanitizeEntries 过滤空 label / 缺 value', () => {
  const result = sanitizeEntries([
    { label: '课程', value: '操作系统' },
    { label: '', value: 'x' },
    { label: '资料数', value: 0 },
    { label: '空', value: '' },
  ])
  assert.deepEqual(result.map((e) => e.label), ['课程', '资料数'])
})

test('splitEntriesIntoColumns 默认双列均分', () => {
  const entries = [
    { label: 'a', value: '1' },
    { label: 'b', value: '2' },
    { label: 'c', value: '3' },
  ]
  const cols = splitEntriesIntoColumns(entries, 2)
  assert.equal(cols.length, 2)
  assert.equal(cols[0].length, 2)
  assert.equal(cols[1].length, 1)
})
```

- [ ] **Step 2: 写实现**

```js
// src/components/common/info-table-model.js
export function sanitizeEntries(entries) {
  if (!Array.isArray(entries)) return []
  return entries
    .filter((e) => e && e.label)
    .map((e) => ({ ...e, value: e.value == null ? '' : e.value }))
    .filter((e) => e.value !== '')
}

export function splitEntriesIntoColumns(entries, columns) {
  const safeColumns = Math.max(1, Number(columns) || 1)
  const safe = sanitizeEntries(entries)
  const result = Array.from({ length: safeColumns }, () => [])
  safe.forEach((entry, idx) => {
    result[idx % safeColumns].push(entry)
  })
  return result
}
```

- [ ] **Step 3: 实现组件**

```vue
<!-- src/components/common/CkInfoTable.vue -->
<script setup>
import { computed } from 'vue'

import { splitEntriesIntoColumns } from './info-table-model.js'

const props = defineProps({
  entries: { type: Array, default: () => [] },
  columns: { type: Number, default: 2 },
})

const grouped = computed(() => splitEntriesIntoColumns(props.entries, props.columns))
</script>

<template>
  <dl class="ck-info-table" :data-columns="columns">
    <div v-for="(col, idx) in grouped" :key="idx" class="ck-info-table-col">
      <div v-for="entry in col" :key="entry.label" class="ck-info-table-row">
        <dt>{{ entry.label }}</dt>
        <dd v-if="entry.kind === 'html'" v-html="entry.value" />
        <dd v-else>{{ entry.value }}</dd>
      </div>
    </div>
  </dl>
</template>

<style scoped lang="scss">
.ck-info-table {
  display: grid;
  grid-template-columns: repeat(var(--cols, 2), minmax(0, 1fr));
  gap: var(--ckqa-space-4);
  margin: 0;
}
.ck-info-table[data-columns='1'] { --cols: 1; }
.ck-info-table[data-columns='2'] { --cols: 2; }
.ck-info-table[data-columns='3'] { --cols: 3; }
.ck-info-table-col { display: flex; flex-direction: column; gap: var(--ckqa-space-2); }
.ck-info-table-row {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: var(--ckqa-space-3);
}
.ck-info-table-row dt {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-weak);
}
.ck-info-table-row dd {
  margin: 0;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  word-break: break-word;
}
</style>
```

- [ ] **Step 4: Smoke + 跑测试 + Commit**

```js
import { sanitizeEntries } from './components/common/info-table-model.js'
test('CkInfoTable model exports are wired', () => {
  assert.deepEqual(sanitizeEntries(null), [])
})
```

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/info-table-model.js \
        frontend/apps/admin-app/src/components/common/info-table-model.test.js \
        frontend/apps/admin-app/src/components/common/CkInfoTable.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkInfoTable 信息表组件"
```

---

## Task 3：useResourceTabs 组合式 + 测试

**Files:**

- Create: `src/composables/useResourceTabs.js`
- Create: `src/composables/use-resource-tabs.test.js`

通用：4 Tab 详情页的"当前 Tab"持久化逻辑。读 `route.query.tab` → fallback default tab → 写入 router。

- [ ] **Step 1: 写失败测试**

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveActiveTab, isValidTab } from './useResourceTabs.js'

test('resolveActiveTab 命中 query.tab 优先', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }, { key: 'b' }], query: { tab: 'b' }, fallback: 'a' }),
    'b',
  )
})

test('resolveActiveTab 未命中走 fallback', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }, { key: 'b' }], query: { tab: 'x' }, fallback: 'a' }),
    'a',
  )
})

test('resolveActiveTab 无 query 走 fallback', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }], query: {}, fallback: 'a' }),
    'a',
  )
})

test('isValidTab', () => {
  assert.equal(isValidTab('a', [{ key: 'a' }]), true)
  assert.equal(isValidTab('x', [{ key: 'a' }]), false)
})
```

- [ ] **Step 2: 写实现**

```js
// src/composables/useResourceTabs.js
import { computed } from 'vue'

export function isValidTab(tab, tabs) {
  return Array.isArray(tabs) && tabs.some((t) => t.key === tab)
}

export function resolveActiveTab({ tabs, query, fallback }) {
  const queryTab = query?.tab
  if (isValidTab(queryTab, tabs)) return queryTab
  return fallback
}

export function useResourceTabs({ route, router, tabs, fallback }) {
  const activeTab = computed(() =>
    resolveActiveTab({ tabs, query: route.query, fallback }),
  )
  function setActiveTab(key) {
    if (!isValidTab(key, tabs)) return
    router.replace({ query: { ...route.query, tab: key } })
  }
  return { activeTab, setActiveTab }
}
```

- [ ] **Step 3: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useResourceTabs.js \
        frontend/apps/admin-app/src/composables/use-resource-tabs.test.js
git commit -m "feat(admin-app): 新增 useResourceTabs 详情页 Tab 切换组合式"
```

---

## Task 4：useMaterialLifecycle 从 views/pages 提到 composables/

**Files:**

- Create: `src/composables/useMaterialLifecycle.js`
- Create: `src/composables/use-material-lifecycle.test.js`

把 `src/views/pages/material-lifecycle-actions.js` 整体迁移并重命名为标准 composable，外部调用方改 import 路径。

- [ ] **Step 1: 复制旧文件到新位置**

```bash
git mv frontend/apps/admin-app/src/views/pages/material-lifecycle-actions.js \
       frontend/apps/admin-app/src/composables/useMaterialLifecycle.js
```

- [ ] **Step 2: 重命名导出函数**

打开 `src/composables/useMaterialLifecycle.js`，把顶层导出整体包成一个 `useMaterialLifecycle({ httpClient, scopeStore })` 函数；如旧文件本身已经是 hooks 风格，保留并新增一个默认 `useMaterialLifecycle` 别名导出。

具体改动模板（按当前文件实际结构调整）：

```js
// 原：导出多个独立 action 函数
// 改为：默认导出一个工厂函数
export function useMaterialLifecycle({ scopeStore }) {
  // ... 原有 action 函数全部作为内部函数返回
  return {
    runParse,
    cancelParse,
    retryParse,
    deleteMaterial,
    replaceMaterial,
    // ... 保留原有所有函数名
  }
}
```

- [ ] **Step 3: 写测试**

`src/composables/use-material-lifecycle.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { useMaterialLifecycle } from './useMaterialLifecycle.js'

test('useMaterialLifecycle 返回完整动作集', () => {
  const fakeScope = { requestParams: () => ({}) }
  const lifecycle = useMaterialLifecycle({ scopeStore: fakeScope })
  assert.equal(typeof lifecycle.runParse, 'function')
  assert.equal(typeof lifecycle.cancelParse, 'function')
  assert.equal(typeof lifecycle.deleteMaterial, 'function')
})
```

> 注：具体动作的端到端行为已被 `app-shell.test.js` 覆盖，这里只断言"工厂函数返回值结构"。

- [ ] **Step 4: 修复其他 import**

```bash
grep -rn "views/pages/material-lifecycle-actions" frontend/apps/admin-app/src/
```

把命中的 import 全部改到 `'../../composables/useMaterialLifecycle.js'`（按相对路径调整层级）。

- [ ] **Step 5: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useMaterialLifecycle.js \
        frontend/apps/admin-app/src/composables/use-material-lifecycle.test.js \
        frontend/apps/admin-app/src/views/pages/
git commit -m "refactor(admin-app): material-lifecycle-actions 迁移到 composables/useMaterialLifecycle"
```

---

## Task 5：useLongTaskState 从 views/pages 提到 composables/

**Files:**

- Create: `src/composables/useLongTaskState.js`
- Create: `src/composables/use-long-task-state.test.js`

同 Task 4 模式：迁移 `src/views/pages/long-task-state.js`。

- [ ] **Step 1-5: 与 Task 4 同款**

```bash
git mv frontend/apps/admin-app/src/views/pages/long-task-state.js \
       frontend/apps/admin-app/src/composables/useLongTaskState.js
```

测试模板：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { createLongTaskController } from './useLongTaskState.js'

test('createLongTaskController 暴露 start / cancel', () => {
  const ctrl = createLongTaskController({ poll: async () => 'success', onSuccess: () => {} })
  assert.equal(typeof ctrl.start, 'function')
  assert.equal(typeof ctrl.cancel, 'function')
})
```

```bash
grep -rn "views/pages/long-task-state" frontend/apps/admin-app/src/
# 把命中的 import 改到 ../../composables/useLongTaskState.js
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useLongTaskState.js \
        frontend/apps/admin-app/src/composables/use-long-task-state.test.js \
        frontend/apps/admin-app/src/views/pages/
git commit -m "refactor(admin-app): long-task-state 迁移到 composables/useLongTaskState"
```

---

## Task 6：course-page-copy + material-page-copy

**Files:**

- Create: `src/views/courses/course-page-copy.js`
- Create: `src/views/materials/material-page-copy.js`
- Modify: `src/copy/admin.js`

把课程 / 资料相关的页面级文案集中。

- [ ] **Step 1: 写 course-page-copy.js**

```js
// src/views/courses/course-page-copy.js
export const COURSE_PAGE_COPY = Object.freeze({
  list: {
    title: '课程',
    subtitle: '管理已有课程，进入详情后可以维护成员、资料和知识库。',
    eyebrow: '生产 · 课程',
    emptyTitle: '还没有课程',
    emptyDescription: '点击右上角"新建课程"开始第一门课的搭建。',
    createCta: '新建课程',
  },
  detail: {
    eyebrowFormat: (courseName) => `生产 · 课程 · ${courseName}`,
    tabs: [
      { key: 'overview', label: '概览' },
      { key: 'members', label: '成员' },
      { key: 'materials', label: '资料' },
      { key: 'knowledge-bases', label: '知识库' },
    ],
  },
  members: {
    addCta: '添加成员',
    archivedHint: '课程已归档，成员管理为只读。',
  },
  materials: {
    uploadCta: '上传资料',
    archivedHint: '课程已归档，资料管理为只读。',
  },
})
```

- [ ] **Step 2: 写 material-page-copy.js**

```js
// src/views/materials/material-page-copy.js
export const MATERIAL_PAGE_COPY = Object.freeze({
  detail: {
    eyebrowFormat: (courseName) => `生产 · 资料 · ${courseName}`,
    tabs: [
      { key: 'parse-progress', label: '解析进度' },
      { key: 'parse-results', label: '解析结果' },
      { key: 'kb-references', label: '知识库引用' },
      { key: 'audit-log', label: '操作日志' },
    ],
    parseTimeoutBanner: 'PDF 解析超时，已自动重试一次。可手动重试或检查文件大小。',
    moreActions: {
      retryParse: '重新解析',
      replace: '替换文件',
      delete: '删除',
      copyToCourse: '复制到其他课程',
    },
  },
  results: {
    subtabs: [
      { key: 'markdown', label: 'Markdown 预览' },
      { key: 'chunks', label: '切分块' },
      { key: 'images', label: '图片' },
      { key: 'pdf', label: '原始 PDF' },
    ],
  },
})
```

- [ ] **Step 3: COPY.admin.js 增 placeholder**

```js
// src/copy/admin.js 在 dashboard 段后追加
  course: { /* see ./views/courses/course-page-copy.js */ },
  material: { /* see ./views/materials/material-page-copy.js */ },
```

> 注：本步骤只放占位指引，让 admin.js 仍是 COPY 总入口；实际文案集中在子文件，避免 admin.js 膨胀。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/courses/course-page-copy.js \
        frontend/apps/admin-app/src/views/materials/material-page-copy.js \
        frontend/apps/admin-app/src/copy/admin.js
git commit -m "feat(admin-app): 课程 / 资料页面文案集中到 *-page-copy.js"
```

---

## Task 7：CourseListPage（卡片网格）

**Files:**

- Create: `src/views/courses/CourseListPage.vue`

复用 `module-loaders.js` 中的 `loadModulePage` / `createCoursesLoaderResult` 加载课程列表。展示：CkPageHero（含"新建课程"actions）+ 卡片网格 + CkPager。

- [ ] **Step 1: 写组件**

```vue
<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkResourceCard from '../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../components/common/CkEmptyState.vue'
import CkPager from '../../components/common/CkPager.vue'

import { loadModulePage } from '../pages/module-loaders.js'
import { useScopeStore } from '../../stores/scope.js'
import { COURSE_PAGE_COPY } from './course-page-copy.js'

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()

const state = ref({ loading: true, error: null, items: [], total: 0 })
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(Number(route.query.pageSize) || 20)

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('courses', {
      ...scopeStore.requestParams(),
      page: page.value,
      pageSize: pageSize.value,
    })
    state.value.items = result.items
    state.value.total = result.total
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

watch(() => scopeStore.state.activeCourseId, load)
watch(page, () => {
  router.replace({ query: { ...route.query, page: page.value } })
  load()
})
watch(pageSize, () => {
  router.replace({ query: { ...route.query, pageSize: pageSize.value, page: 1 } })
  page.value = 1
})

onMounted(load)
onBeforeUnmount(() => {})
</script>

<template>
  <div class="course-list-page">
    <CkPageHero
      :title="COURSE_PAGE_COPY.list.title"
      :subtitle="COURSE_PAGE_COPY.list.subtitle"
      :eyebrow="COURSE_PAGE_COPY.list.eyebrow"
    >
      <template #actions>
        <RouterLink class="course-list-page-create ck-pressable" to="/app/courses?action=create">
          {{ COURSE_PAGE_COPY.list.createCta }}
        </RouterLink>
      </template>
    </CkPageHero>

    <CkSkeleton v-if="state.loading" variant="card" :count="6" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="◻"
      :title="COURSE_PAGE_COPY.list.emptyTitle"
      :description="COURSE_PAGE_COPY.list.emptyDescription"
      :cta="{ label: COURSE_PAGE_COPY.list.createCta, onClick: () => router.push('/app/courses?action=create') }"
    />
    <ul v-else class="course-list-page-grid">
      <li v-for="course in state.items" :key="course.id">
        <CkResourceCard
          :title="course.name"
          :description="course.description"
          :status="course.archived ? 'archived' : 'active'"
          :cover="course.coverUrl"
          :meta="[
            { label: '资料', value: course.materialCount },
            { label: '知识库', value: course.knowledgeBaseCount },
          ]"
          :to="`/app/courses/${encodeURIComponent(course.id)}`"
        />
      </li>
    </ul>

    <CkPager
      v-if="state.total > pageSize"
      :page="page"
      :page-size="pageSize"
      :total="state.total"
      @change-page="(p) => (page = p)"
      @change-page-size="(s) => (pageSize = s)"
    />
  </div>
</template>

<style scoped lang="scss">
.course-list-page { display: flex; flex-direction: column; gap: var(--ckqa-space-5); }
.course-list-page-grid {
  list-style: none; margin: 0; padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--ckqa-space-4);
}
.course-list-page-create {
  padding: 7px 14px;
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  text-decoration: none;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/views/courses/CourseListPage.vue
git commit -m "feat(admin-app): 新增 CourseListPage 卡片网格"
```

---

## Task 8：CourseDetailPage + 4 Tab

**Files:**

- Create: `src/views/courses/CourseDetailPage.vue`
- Create: `src/views/courses/tabs/CourseOverviewTab.vue`
- Create: `src/views/courses/tabs/CourseMembersTab.vue`
- Create: `src/views/courses/tabs/CourseMaterialsTab.vue`
- Create: `src/views/courses/tabs/CourseKnowledgeBasesTab.vue`

CourseDetailPage 是 4 Tab 容器，4 个 Tab 各自负责加载自己的数据。沿用 `module-loaders.js` 的现有 loader，把 `ModulePage` 的拆分逻辑挪到这里。

- [ ] **Step 1: CourseDetailPage 容器**

```vue
<!-- src/views/courses/CourseDetailPage.vue -->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'

import { useResourceTabs } from '../../composables/useResourceTabs.js'
import { loadCourseDetailBlock } from '../pages/module-loaders.js'
import { COURSE_PAGE_COPY } from './course-page-copy.js'

import CourseOverviewTab from './tabs/CourseOverviewTab.vue'
import CourseMembersTab from './tabs/CourseMembersTab.vue'
import CourseMaterialsTab from './tabs/CourseMaterialsTab.vue'
import CourseKnowledgeBasesTab from './tabs/CourseKnowledgeBasesTab.vue'

const route = useRoute()
const router = useRouter()

const course = ref({ loading: true, data: null, error: null })

const { activeTab, setActiveTab } = useResourceTabs({
  route,
  router,
  tabs: COURSE_PAGE_COPY.detail.tabs,
  fallback: 'overview',
})

const tabComponents = {
  overview: CourseOverviewTab,
  members: CourseMembersTab,
  materials: CourseMaterialsTab,
  'knowledge-bases': CourseKnowledgeBasesTab,
}

async function loadCourse() {
  course.value.loading = true
  try {
    const data = await loadCourseDetailBlock(route.params.courseId)
    course.value = { loading: false, data, error: null }
    // 注入面包屑 contextChain
    route.meta.contextChain = [
      { label: data.name, to: `/app/courses/${encodeURIComponent(data.id)}` },
    ]
  } catch (error) {
    course.value = { loading: false, data: null, error }
  }
}

watch(() => route.params.courseId, loadCourse)
onMounted(loadCourse)

const activeComponent = computed(() => tabComponents[activeTab.value])
const heroEyebrow = computed(() =>
  course.value.data ? COURSE_PAGE_COPY.detail.eyebrowFormat(course.value.data.name) : '生产 · 课程',
)
</script>

<template>
  <div class="course-detail-page">
    <CkSkeleton v-if="course.loading" variant="card" :count="1" />
    <template v-else-if="course.data">
      <CkPageHero
        :title="course.data.name"
        :subtitle="course.data.description"
        :eyebrow="heroEyebrow"
      />

      <nav class="course-detail-page-tabs" role="tablist" aria-label="课程详情标签页">
        <button
          v-for="tab in COURSE_PAGE_COPY.detail.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="['course-detail-page-tab', { 'is-active': activeTab === tab.key }]"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <component :is="activeComponent" :course="course.data" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.course-detail-page { display: flex; flex-direction: column; gap: var(--ckqa-space-4); }
.course-detail-page-tabs {
  display: flex; gap: var(--ckqa-space-1);
  border-bottom: 1px solid var(--ckqa-border);
}
.course-detail-page-tab {
  padding: var(--ckqa-space-2) var(--ckqa-space-4);
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
  transition: color var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.course-detail-page-tab:hover { color: var(--ckqa-text); }
.course-detail-page-tab.is-active {
  color: var(--ckqa-accent-strong);
  border-bottom-color: var(--ckqa-accent);
  font-weight: var(--ckqa-fw-medium);
}
</style>
```

- [ ] **Step 2: 4 个 Tab 组件**

`src/views/courses/tabs/CourseOverviewTab.vue`：

```vue
<script setup>
import CkInfoTable from '../../../components/common/CkInfoTable.vue'

const props = defineProps({
  course: { type: Object, required: true },
})

function formatBytes(b) {
  if (typeof b !== 'number') return ''
  if (b > 1024 * 1024 * 1024) return `${(b / (1024 * 1024 * 1024)).toFixed(1)} GB`
  if (b > 1024 * 1024) return `${(b / (1024 * 1024)).toFixed(1)} MB`
  return `${b} B`
}

const entries = [
  { label: '课程编码', value: props.course.code },
  { label: '主讲教师', value: props.course.teacher?.name },
  { label: '资料总数', value: props.course.materialCount },
  { label: '资料总大小', value: formatBytes(props.course.materialBytes) },
  { label: '知识库数', value: props.course.knowledgeBaseCount },
  { label: '激活知识库版本', value: props.course.activeKbVersion },
  { label: '创建时间', value: props.course.createdAt },
  { label: '最近更新', value: props.course.updatedAt },
]
</script>

<template>
  <CkInfoTable :entries="entries" :columns="2" />
</template>
```

`src/views/courses/tabs/CourseMembersTab.vue`：

```vue
<script setup>
import { onMounted, ref } from 'vue'
import { ElTable, ElTableColumn, ElButton } from 'element-plus'

import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'
import { COURSE_PAGE_COPY } from '../course-page-copy.js'

const props = defineProps({
  course: { type: Object, required: true },
})

const state = ref({ loading: true, items: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('courseMembers', { courseId: props.course.id, pageSize: 100 })
    state.value.items = result.items
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div class="course-members-tab">
    <header class="course-members-tab-header">
      <p v-if="course.archived" class="course-members-tab-archived-hint">
        {{ COURSE_PAGE_COPY.members.archivedHint }}
      </p>
      <ElButton v-else type="primary">{{ COURSE_PAGE_COPY.members.addCta }}</ElButton>
    </header>

    <CkSkeleton v-if="state.loading" variant="row" :count="5" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="·"
      title="还没有成员"
      description="点击右上角 添加成员 邀请教师 / 助教加入。"
    />
    <ElTable v-else :data="state.items" stripe>
      <ElTableColumn prop="name" label="姓名" />
      <ElTableColumn prop="email" label="邮箱" />
      <ElTableColumn prop="role" label="角色" />
      <ElTableColumn prop="joinedAt" label="加入时间" />
    </ElTable>
  </div>
</template>

<style scoped lang="scss">
.course-members-tab { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.course-members-tab-header { display: flex; justify-content: flex-end; }
.course-members-tab-archived-hint {
  margin: 0; padding: var(--ckqa-space-2);
  color: var(--ckqa-text-muted); font-size: var(--ckqa-text-sm-size);
}
</style>
```

`src/views/courses/tabs/CourseMaterialsTab.vue`：

```vue
<script setup>
import { onMounted, ref } from 'vue'

import CkResourceCard from '../../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'
import { COURSE_PAGE_COPY } from '../course-page-copy.js'

const props = defineProps({
  course: { type: Object, required: true },
})

const state = ref({ loading: true, items: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('courseMaterials', { courseId: props.course.id, pageSize: 50 })
    state.value.items = result.items
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div class="course-materials-tab">
    <header v-if="course.archived" class="course-materials-tab-archived-hint">
      {{ COURSE_PAGE_COPY.materials.archivedHint }}
    </header>

    <CkSkeleton v-if="state.loading" variant="row" :count="6" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="↑"
      title="还没有资料"
      :cta="{ label: COURSE_PAGE_COPY.materials.uploadCta, onClick: () => {} }"
    />
    <ul v-else class="course-materials-tab-grid">
      <li v-for="material in state.items" :key="material.id">
        <CkResourceCard
          :title="material.name"
          :status="material.parseStatus"
          :meta="[
            { label: '大小', value: material.sizeLabel },
            { label: '上传', value: material.uploadedAtLabel },
          ]"
          :to="`/app/materials/${encodeURIComponent(material.id)}?courseId=${encodeURIComponent(course.id)}`"
        />
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.course-materials-tab { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.course-materials-tab-archived-hint {
  padding: var(--ckqa-space-2);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.course-materials-tab-grid {
  list-style: none; margin: 0; padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
```

`src/views/courses/tabs/CourseKnowledgeBasesTab.vue`：

```vue
<script setup>
import { onMounted, ref } from 'vue'

import CkResourceCard from '../../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'

const props = defineProps({
  course: { type: Object, required: true },
})

const state = ref({ loading: true, items: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('knowledgeBases', { courseId: props.course.id, pageSize: 50 })
    state.value.items = result.items
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div class="course-kb-tab">
    <CkSkeleton v-if="state.loading" variant="row" :count="3" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="◇"
      title="还没有知识库"
      description="为本课程的资料生成第一个检索索引。"
    />
    <ul v-else class="course-kb-tab-grid">
      <li v-for="kb in state.items" :key="kb.id">
        <CkResourceCard
          :title="kb.name"
          :description="kb.description"
          :status="kb.status"
          :meta="[
            { label: '版本', value: kb.activeVersion },
            { label: '资料数', value: kb.materialCount },
          ]"
          :to="`/app/knowledge-bases/${encodeURIComponent(kb.id)}`"
        />
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.course-kb-tab-grid {
  list-style: none; margin: 0; padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/courses/CourseDetailPage.vue \
        frontend/apps/admin-app/src/views/courses/tabs/
git commit -m "feat(admin-app): 新增 CourseDetailPage 4 Tab 容器（概览/成员/资料/知识库）"
```

---

## Task 9：MaterialDetailPage + 4 Tab

**Files:**

- Create: `src/views/materials/MaterialDetailPage.vue`
- Create: `src/views/materials/tabs/MaterialParseProgressTab.vue`
- Create: `src/views/materials/tabs/MaterialParseResultsTab.vue`
- Create: `src/views/materials/tabs/MaterialKbReferencesTab.vue`
- Create: `src/views/materials/tabs/MaterialAuditLogTab.vue`

容器同 CourseDetailPage 模式。Tab 1 解析进度走 SSE，临时使用占位 `CkLogStream` —— **若 M5 已合并，直接 import 真实组件**。本 Task 实现一个 50 行的内联 `LogStreamPlaceholder.vue` 作为退化 fallback，避免硬依赖 M5 顺序。

- [ ] **Step 1: MaterialDetailPage 容器**

```vue
<!-- src/views/materials/MaterialDetailPage.vue -->
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkStatusPill from '../../components/common/CkStatusPill.vue'
import CkSkeleton from '../../components/common/CkSkeleton.vue'

import { useResourceTabs } from '../../composables/useResourceTabs.js'
import { useMaterialLifecycle } from '../../composables/useMaterialLifecycle.js'
import { useScopeStore } from '../../stores/scope.js'
import { loadModulePage } from '../pages/module-loaders.js'
import { MATERIAL_PAGE_COPY } from './material-page-copy.js'

import MaterialParseProgressTab from './tabs/MaterialParseProgressTab.vue'
import MaterialParseResultsTab from './tabs/MaterialParseResultsTab.vue'
import MaterialKbReferencesTab from './tabs/MaterialKbReferencesTab.vue'
import MaterialAuditLogTab from './tabs/MaterialAuditLogTab.vue'

const route = useRoute()
const router = useRouter()
const scopeStore = useScopeStore()
const lifecycle = useMaterialLifecycle({ scopeStore })

const material = ref({ loading: true, data: null, error: null })

const tabComponents = {
  'parse-progress': MaterialParseProgressTab,
  'parse-results': MaterialParseResultsTab,
  'kb-references': MaterialKbReferencesTab,
  'audit-log': MaterialAuditLogTab,
}

const fallbackTab = computed(() => (route.name === 'parse-results' ? 'parse-results' : 'parse-progress'))

const { activeTab, setActiveTab } = useResourceTabs({
  route,
  router,
  tabs: MATERIAL_PAGE_COPY.detail.tabs,
  fallback: fallbackTab.value,
})

async function loadMaterial() {
  material.value.loading = true
  try {
    const result = await loadModulePage('material', { materialId: route.params.materialId })
    material.value = { loading: false, data: result.item, error: null }
    route.meta.contextChain = [
      { label: result.item.courseName, to: `/app/courses/${encodeURIComponent(result.item.courseId)}` },
      { label: result.item.name, to: `/app/materials/${encodeURIComponent(result.item.id)}` },
    ]
  } catch (error) {
    material.value = { loading: false, data: null, error }
  }
}

watch(() => route.params.materialId, loadMaterial)
onMounted(loadMaterial)

const activeComponent = computed(() => tabComponents[activeTab.value])
const heroEyebrow = computed(() =>
  material.value.data
    ? MATERIAL_PAGE_COPY.detail.eyebrowFormat(material.value.data.courseName)
    : '生产 · 资料',
)
</script>

<template>
  <div class="material-detail-page">
    <CkSkeleton v-if="material.loading" variant="card" :count="1" />
    <template v-else-if="material.data">
      <CkPageHero
        :title="material.data.name"
        :subtitle="`${material.data.courseName} · ${material.data.sizeLabel} · 上传于 ${material.data.uploadedAtLabel}`"
        :eyebrow="heroEyebrow"
      >
        <template #actions>
          <CkStatusPill
            :status="material.data.parseStatus"
            :label="material.data.parseStatusLabel"
          />
        </template>
      </CkPageHero>

      <nav class="material-detail-page-tabs" role="tablist" aria-label="资料详情标签页">
        <button
          v-for="tab in MATERIAL_PAGE_COPY.detail.tabs"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="['material-detail-page-tab', { 'is-active': activeTab === tab.key }]"
          @click="setActiveTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </nav>

      <component :is="activeComponent" :material="material.data" :lifecycle="lifecycle" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.material-detail-page { display: flex; flex-direction: column; gap: var(--ckqa-space-4); }
.material-detail-page-tabs {
  display: flex; gap: var(--ckqa-space-1);
  border-bottom: 1px solid var(--ckqa-border);
}
.material-detail-page-tab {
  padding: var(--ckqa-space-2) var(--ckqa-space-4);
  background: transparent; border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
}
.material-detail-page-tab:hover { color: var(--ckqa-text); }
.material-detail-page-tab.is-active {
  color: var(--ckqa-accent-strong);
  border-bottom-color: var(--ckqa-accent);
  font-weight: var(--ckqa-fw-medium);
}
</style>
```

- [ ] **Step 2: MaterialParseProgressTab（占位 + SSE 接入）**

```vue
<!-- src/views/materials/tabs/MaterialParseProgressTab.vue -->
<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

const props = defineProps({
  material: { type: Object, required: true },
  lifecycle: { type: Object, required: true },
})

const stages = ref([])
const logs = ref([])
const error = ref(null)
const showTimeoutBanner = ref(false)

let eventSource = null

function appendLog(line) {
  logs.value.push(line)
  if (logs.value.length > 500) logs.value.splice(0, logs.value.length - 500)
}

function start() {
  if (!props.material.parseTaskId) return
  const url = `/api/v1/material-parse-tasks/${encodeURIComponent(props.material.parseTaskId)}/stream`
  eventSource = new EventSource(url)
  eventSource.addEventListener('snapshot', (event) => {
    const data = JSON.parse(event.data)
    stages.value = data.stages || []
  })
  eventSource.addEventListener('progress', (event) => {
    const data = JSON.parse(event.data)
    stages.value = data.stages || stages.value
    if (data.log) appendLog(data.log)
    if (data.timeoutRetried) showTimeoutBanner.value = true
  })
  eventSource.addEventListener('failed', (event) => {
    const data = JSON.parse(event.data)
    error.value = data.error || '解析失败'
  })
  eventSource.addEventListener('done', () => {
    eventSource?.close()
    eventSource = null
  })
  eventSource.onerror = () => {
    error.value = '连接中断，请刷新重试'
    eventSource?.close()
    eventSource = null
  }
}

onMounted(start)
onBeforeUnmount(() => eventSource?.close())
</script>

<template>
  <div class="material-parse-progress-tab">
    <p v-if="showTimeoutBanner" class="material-parse-progress-tab-banner">
      {{ MATERIAL_PAGE_COPY.detail.parseTimeoutBanner }}
    </p>

    <div class="material-parse-progress-tab-body">
      <ol class="material-parse-progress-tab-stages" aria-label="解析阶段">
        <li
          v-for="stage in stages"
          :key="stage.key"
          :class="['stage', `tone-${stage.status}`]"
        >
          <span>{{ stage.label }}</span>
          <small v-if="stage.startedAt">{{ stage.startedAtLabel }}</small>
        </li>
      </ol>

      <div class="material-parse-progress-tab-log" aria-live="polite">
        <p v-if="error" class="material-parse-progress-tab-log-error">{{ error }}</p>
        <ul v-else>
          <li v-for="(line, idx) in logs" :key="idx">{{ line }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.material-parse-progress-tab { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.material-parse-progress-tab-banner {
  margin: 0; padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-warning-soft);
  border-left: 3px solid var(--ckqa-warning);
  font-size: var(--ckqa-text-sm-size);
  border-radius: var(--ckqa-radius-md);
}
.material-parse-progress-tab-body {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--ckqa-space-4);
  min-height: 360px;
}
.material-parse-progress-tab-stages {
  list-style: none; margin: 0; padding: 0;
  display: flex; flex-direction: column; gap: var(--ckqa-space-2);
}
.material-parse-progress-tab-stages .stage {
  display: flex; flex-direction: column; gap: 2px;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.material-parse-progress-tab-stages .tone-running { border-color: var(--ckqa-accent); background: var(--ckqa-accent-soft); }
.material-parse-progress-tab-stages .tone-success { border-color: var(--ckqa-success); }
.material-parse-progress-tab-stages .tone-failed { border-color: var(--ckqa-danger); }
.material-parse-progress-tab-stages .stage small { font-size: var(--ckqa-text-xs-size); color: var(--ckqa-text-weak); }
.material-parse-progress-tab-log {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  padding: var(--ckqa-space-3);
  font-family: var(--ckqa-font-mono);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  max-height: 360px; overflow-y: auto;
}
.material-parse-progress-tab-log-error { color: var(--ckqa-danger); margin: 0; }
.material-parse-progress-tab-log ul { list-style: none; margin: 0; padding: 0; }
.material-parse-progress-tab-log li { padding: 2px 0; }
@media (max-width: 960px) {
  .material-parse-progress-tab-body { grid-template-columns: 1fr; }
}
</style>
```

- [ ] **Step 3: MaterialParseResultsTab（子 Tab：Markdown / 切分块 / 图片 / 原始 PDF）**

```vue
<!-- src/views/materials/tabs/MaterialParseResultsTab.vue -->
<script setup>
import { onMounted, ref } from 'vue'

import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'
import { MATERIAL_PAGE_COPY } from '../material-page-copy.js'

const props = defineProps({
  material: { type: Object, required: true },
})

const subtab = ref('markdown')
const state = ref({ loading: true, data: null, error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('parseResult', { materialId: props.material.id })
    state.value.data = result.item
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div class="material-parse-results-tab">
    <nav class="material-parse-results-tab-subtabs" role="tablist">
      <button
        v-for="t in MATERIAL_PAGE_COPY.results.subtabs"
        :key="t.key"
        type="button"
        :class="{ 'is-active': subtab === t.key }"
        @click="subtab = t.key"
      >
        {{ t.label }}
      </button>
    </nav>

    <CkSkeleton v-if="state.loading" variant="card" :count="1" />
    <CkEmptyState
      v-else-if="!state.data"
      icon="·"
      title="尚无解析结果"
      description="解析完成后这里会显示 Markdown 预览、切分块、图片与原始 PDF。"
    />

    <article v-else-if="subtab === 'markdown'" class="material-parse-results-tab-markdown">
      <pre>{{ state.data.markdown }}</pre>
    </article>

    <ul v-else-if="subtab === 'chunks'" class="material-parse-results-tab-chunks">
      <li v-for="chunk in state.data.chunks" :key="chunk.id">
        <header><strong>#{{ chunk.index }}</strong> · {{ chunk.charCount }} 字</header>
        <p>{{ chunk.preview }}</p>
      </li>
    </ul>

    <ul v-else-if="subtab === 'images'" class="material-parse-results-tab-images">
      <li v-for="img in state.data.images" :key="img.id">
        <img :src="img.url" :alt="`图 ${img.index}`" loading="lazy" />
        <a :href="img.pdfAnchor">在原 PDF 中定位</a>
      </li>
    </ul>

    <iframe v-else-if="subtab === 'pdf'" :src="state.data.pdfUrl" class="material-parse-results-tab-pdf" />
  </div>
</template>

<style scoped lang="scss">
.material-parse-results-tab { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.material-parse-results-tab-subtabs { display: flex; gap: var(--ckqa-space-1); }
.material-parse-results-tab-subtabs button {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-full);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-xs-size);
  cursor: pointer;
}
.material-parse-results-tab-subtabs button.is-active {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
  border-color: var(--ckqa-accent);
}
.material-parse-results-tab-markdown pre {
  white-space: pre-wrap; word-break: break-word;
  font-family: var(--ckqa-font-mono);
  background: var(--ckqa-surface-muted);
  border-radius: var(--ckqa-radius-md);
  padding: var(--ckqa-space-3);
  font-size: var(--ckqa-text-sm-size);
}
.material-parse-results-tab-chunks { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--ckqa-space-2); }
.material-parse-results-tab-chunks li {
  padding: var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.material-parse-results-tab-images { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: var(--ckqa-space-3); }
.material-parse-results-tab-images li { display: flex; flex-direction: column; gap: 4px; }
.material-parse-results-tab-images img { width: 100%; aspect-ratio: 4/3; object-fit: cover; border-radius: var(--ckqa-radius-md); }
.material-parse-results-tab-pdf { width: 100%; height: 70vh; border: 1px solid var(--ckqa-border); border-radius: var(--ckqa-radius-md); }
</style>
```

- [ ] **Step 4: MaterialKbReferencesTab + MaterialAuditLogTab**

```vue
<!-- src/views/materials/tabs/MaterialKbReferencesTab.vue -->
<script setup>
import { onMounted, ref } from 'vue'

import CkResourceCard from '../../../components/common/CkResourceCard.vue'
import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'

const props = defineProps({
  material: { type: Object, required: true },
})

const state = ref({ loading: true, items: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('materialKbReferences', { materialId: props.material.id })
    state.value.items = result.items
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <CkSkeleton v-if="state.loading" variant="card" :count="2" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="◇"
      title="本资料未被任何知识库引用"
      description="加入到知识库构建后会显示在这里。"
    />
    <ul v-else class="material-kb-tab-grid">
      <li v-for="kb in state.items" :key="kb.id">
        <CkResourceCard
          :title="kb.name"
          :status="kb.status"
          :meta="[
            { label: '版本', value: kb.activeVersion },
          ]"
          :to="`/app/knowledge-bases/${encodeURIComponent(kb.id)}`"
        />
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.material-kb-tab-grid {
  list-style: none; margin: 0; padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--ckqa-space-3);
}
</style>
```

```vue
<!-- src/views/materials/tabs/MaterialAuditLogTab.vue -->
<script setup>
import { onMounted, ref } from 'vue'

import CkSkeleton from '../../../components/common/CkSkeleton.vue'
import CkEmptyState from '../../../components/common/CkEmptyState.vue'
import { loadModulePage } from '../../pages/module-loaders.js'

const props = defineProps({
  material: { type: Object, required: true },
})

const state = ref({ loading: true, items: [], error: null })

async function load() {
  state.value.loading = true
  try {
    const result = await loadModulePage('materialAuditLog', { materialId: props.material.id })
    state.value.items = result.items
    state.value.error = null
  } catch (error) {
    state.value.error = error
  } finally {
    state.value.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <CkSkeleton v-if="state.loading" variant="row" :count="6" />
    <CkEmptyState
      v-else-if="!state.items.length"
      icon="·"
      title="还没有操作记录"
      description="上传 / 重新解析 / 删除 / 复制等动作会记录在这里。"
    />
    <ol v-else class="material-audit-log-tab-list">
      <li v-for="entry in state.items" :key="entry.id">
        <strong>{{ entry.action }}</strong>
        <span>{{ entry.actorName }}</span>
        <time>{{ entry.atLabel }}</time>
        <p v-if="entry.detail">{{ entry.detail }}</p>
      </li>
    </ol>
  </div>
</template>

<style scoped lang="scss">
.material-audit-log-tab-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--ckqa-space-2); }
.material-audit-log-tab-list li {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--ckqa-space-3);
  align-items: center;
  padding: var(--ckqa-space-2) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
  font-size: var(--ckqa-text-sm-size);
}
.material-audit-log-tab-list li p { grid-column: 1 / 4; margin: 0; color: var(--ckqa-text-muted); font-size: var(--ckqa-text-xs-size); }
</style>
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/materials/
git commit -m "feat(admin-app): 新增 MaterialDetailPage 4 Tab（解析进度/结果/KB引用/操作日志）"
```

---

## Task 10：路由切换 + 注册新组件

**Files:**

- Modify: `src/router/routes.js`
- Modify: `src/router/index.js`

把课程 / 资料相关 7 条路由的 `componentKey` 切换到具体页面。

- [ ] **Step 1: routes.js**

```js
// /app/courses
componentKey: 'CourseListPage',

// /app/courses/:courseId
componentKey: 'CourseDetailPage',

// /app/courses/:courseId/members
componentKey: 'CourseDetailPage',
meta: { ...meta, defaultTab: 'members' },

// /app/courses/:courseId/materials
componentKey: 'CourseDetailPage',
meta: { ...meta, defaultTab: 'materials' },

// /app/materials/:materialId
componentKey: 'MaterialDetailPage',

// /app/materials/:materialId/parse-results
componentKey: 'MaterialDetailPage',
meta: { ...meta, defaultTab: 'parse-results' },
```

> 课程子页面（成员 / 资料）共用 CourseDetailPage 容器，差异通过 `meta.defaultTab` 注入；CourseDetailPage 在 `useResourceTabs` 的 `fallback` 参数处用 `route.meta.defaultTab || 'overview'`。

- [ ] **Step 2: router/index.js 注册**

```js
CourseListPage: () => import('../views/courses/CourseListPage.vue'),
CourseDetailPage: () => import('../views/courses/CourseDetailPage.vue'),
MaterialDetailPage: () => import('../views/materials/MaterialDetailPage.vue'),
```

- [ ] **Step 3: CourseDetailPage / MaterialDetailPage 读 meta.defaultTab**

把 Task 8 中的：

```js
const { activeTab, setActiveTab } = useResourceTabs({ ..., fallback: 'overview' })
```

改为：

```js
const { activeTab, setActiveTab } = useResourceTabs({
  ...,
  fallback: route.meta.defaultTab || 'overview',
})
```

MaterialDetailPage 同款改：`fallback: route.meta.defaultTab || 'parse-progress'`。

- [ ] **Step 4: 跑测试 + 构建 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
git add frontend/apps/admin-app/src/router/ \
        frontend/apps/admin-app/src/views/courses/CourseDetailPage.vue \
        frontend/apps/admin-app/src/views/materials/MaterialDetailPage.vue
git commit -m "refactor(admin-app): 课程 / 资料路由切换到独立页面（保留 ModulePage 兜底）"
```

---

## Task 11：Playwright e2e/material-detail.spec.js + e2e/course-flow.spec.js

**Files:**

- Create: `frontend/apps/admin-app/e2e/course-flow.spec.js`
- Create: `frontend/apps/admin-app/e2e/material-detail.spec.js`

- [ ] **Step 1: course-flow.spec.js**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

test('课程列表 → 课程详情 → 资料 Tab 链路', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/app/courses')

  await expect(page.getByRole('heading', { name: '课程' })).toBeVisible()
  // 第一张课程卡片
  const firstCard = page.locator('.ck-resource-card').first()
  await firstCard.click()

  await expect(page).toHaveURL(/\/app\/courses\//)
  await expect(page.getByRole('tab', { name: '概览' })).toBeVisible()

  await page.getByRole('tab', { name: '资料' }).click()
  await expect(page).toHaveURL(/tab=materials/)
})
```

- [ ] **Step 2: material-detail.spec.js**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

test('资料详情 4 Tab 默认进入解析进度', async ({ page, context }) => {
  await loginAsAdmin(page)
  // 假设测试 fixture 提供了一个稳定 materialId；否则从课程详情进入
  await page.goto('/app/courses')
  await page.locator('.ck-resource-card').first().click()
  await page.getByRole('tab', { name: '资料' }).click()
  const firstMaterial = page.locator('.ck-resource-card').first()
  await firstMaterial.click()

  await expect(page.getByRole('tab', { name: '解析进度' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('region', { name: '资料详情标签页' }).or(page.locator('.material-detail-page-tabs'))).toBeVisible()

  await page.getByRole('tab', { name: '操作日志' }).click()
  await expect(page).toHaveURL(/tab=audit-log/)
})
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/e2e/course-flow.spec.js \
        frontend/apps/admin-app/e2e/material-detail.spec.js
git commit -m "test(admin-app): 新增课程 / 资料 e2e 用例覆盖核心链路"
```

---

## 收尾验证

### Task 12：M4 集成验证

- [ ] **Step 1: 单元测试全绿**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 构建通过**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 3: 手工巡检**

逐项验证：

1. `/app/courses` 显示卡片网格 + CkPager；空态可见。
2. 进入课程详情，4 Tab 切换都改 URL `?tab=`，刷新保留。
3. `/app/courses/:id/members` 直接进入成员 Tab。
4. `/app/courses/:id/materials` 直接进入资料 Tab。
5. `/app/materials/:id` 默认进解析进度，SSE 接 `/api/v1/material-parse-tasks/:id/stream`。
6. `/app/materials/:id/parse-results` 直接进解析结果 Tab。
7. 4 个 Tab 切换无白屏闪烁（用 CkSkeleton 占位）。
8. 面包屑显示完整链路："生产 / 课程 / 操作系统课程 / 数据结构第3章.pdf"。
9. 暗色切换正常。

---

## Self-Review

### 1. 设计稿覆盖度

| 设计稿章节 | 落到任务 |
| --- | --- |
| 7 通用组件 CkResourceCard / CkInfoTable | Task 1 + Task 2 |
| 8.3 资料详情 4 Tab | Task 9 |
| 8.3 解析进度 SSE + 失败 banner | Task 9 Step 2 |
| 8.3 解析结果 4 子 Tab | Task 9 Step 3 |
| 8.3 KB 引用 / 操作日志 | Task 9 Step 4 |
| 9 课程列表 / 详情 / 成员 / 资料 | Task 7 + Task 8 |
| 9 ModulePage.vue 拆分 | Task 4 + Task 5 + Task 8 + Task 9（迁移而非重写底层） |
| 15 附录 useMaterialLifecycle / useLongTaskState | Task 4 + Task 5 |
| 5.5 面包屑 contextChain | Task 8 + Task 9 在 onMounted 注入 |

### 2. 占位扫描

通读：所有步骤含具体代码与命令；没有 "TBD"。`MaterialParseProgressTab` 中的 SSE 字段假设 `progress / done / failed / snapshot` 与现有后端事件名一致，已在 M1+M2 之前的代码里覆盖。

### 3. 类型 / API 一致性

- `useMaterialLifecycle({ scopeStore })` 在 Task 4 + Task 9 调用一致。
- `useResourceTabs({ route, router, tabs, fallback })` 的 4 参数签名在 Task 3 + Task 8 + Task 9 一致。
- `loadModulePage(key, params)` 现有 API，所有页面统一调用。
- `MATERIAL_PAGE_COPY.detail.tabs` 的 `key` 与 `tabComponents` 映射严格对齐：`parse-progress / parse-results / kb-references / audit-log`。

### 4. 范围检查

只覆盖 M4。`ModulePage.vue` 不删（M7 收尾），但路由不再走它。M5 / M6 / M7 不触动。

---

## 视觉打磨对齐备注（2026-05-09 阶段性调整后补充）

M4 计划撰写于视觉打磨计划执行之前。视觉打磨已全部落地，以下微调已同步到本文档：

1. **CkResourceCard 卡片外壳**：从 `background: var(--ckqa-surface); border: 1px solid var(--ckqa-border)` 改为挂 `.ck-glass-card` 工具类 + `var(--ckqa-shadow-card-hover)` hover 阴影。玻璃态工具类已提供背景、边框、圆角、`::before` highlight 和 `@supports not` 退化。
2. **CourseListPage "新建课程" 按钮**：追加 `.ck-pressable` class，移除手写 `:hover` 背景色（`.ck-pressable` 已提供 hover 上浮 + active 按压 + focus-visible ring）。
3. **品牌引用**：实施时所有新页面中不得出现硬编码 `CKQA Console` 字样，统一从 `src/copy/brand.js` 导入 `BRAND` 常量。
4. **字号 token**：视觉打磨已将 type scale 从 11–28 上调到 12–30，本计划中引用的 `--ckqa-text-*` 变量值已自动生效，无需手动修改。

---

**计划已写完。**
