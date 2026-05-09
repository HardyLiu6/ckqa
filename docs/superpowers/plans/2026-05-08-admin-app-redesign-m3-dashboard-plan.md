# 管理员端重设计 M3 Dashboard 重做实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `views/dashboard/DashboardView.vue` 重做为"生产架势"看板：流水线 5 段 hero、活动时间线、进行中任务面板、快捷入口；新增 `<CkPipelineHero>` / `<CkActivityFeed>` / `<CkTaskList>` 三个通用组件，并接入聚合数据源。

**Architecture:** 在 M1+M2 地基之上，新增 `views/dashboard/DashboardPage.vue` 替换旧 `DashboardView.vue`（并修改 routes 指向新文件）。三个新组件落到 `components/common/`，纯渲染 + 同名 `*-model.js` 承载逻辑。数据层新增 `composables/useDashboardSummary.js`（首选 `/api/v1/dashboard/summary`，失败时降级到分资源并发请求）和 `composables/useDashboardFeed.js`（聚合活动 + 进行中任务）。

**Tech Stack:** Vue 3.5 Composition API、Pinia 3、SSE/HTTP（沿用 axios）、`node:test`、Playwright。

**前置依赖：**

- M1+M2 必须已合并到默认分支：`primaryNavigation` / `NAV_SECTIONS` / `CkPageHero` / `CkSkeleton` / `CkStatusPill` / `useScopeStore` / 暖色 token / 新 ConsoleLayout 全部就绪。
- 后端 `/api/v1/dashboard/summary?courseId={?}` 接口若就绪，按统一接口走；若未就绪，本计划 Task 9 的降级方案自动接管，不阻塞 M3 上线。
- `useScopeStore.requestParams()` 的 `courseId` 自动注入为列表请求参数（已在 M1 完成）。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run test` 全绿（含本计划新增 4 个 model 测试）。
2. `pnpm --dir frontend/apps/admin-app run build` 通过。
3. 启动 dev，登录后落到 `/app/dashboard`，依次可见：
   - 顶部 `<CkPageHero>` 显示问候语 + 摘要句；
   - 流水线 5 段 hero（课程 / 资料 / 知识库 / 激活 / 问答）按角色范围渲染；
   - 1.55fr 近期动态 + 1fr 进行中任务（含 4 个快捷入口）；
   - 范围芯片切换课程后，看板数据按 `courseId` 过滤；
   - 暗色切换正常，无脏白卡片。
4. Playwright `dashboard.spec.js`（新建）覆盖：登录后看到流水线 + 任务列表，点流水线"知识库"段跳到 `/app/knowledge-bases?status=running`。
5. `views/dashboard/DashboardView.vue` 被删除；路由 `componentKey: 'DashboardView'` 改为 `'DashboardPage'`。

---

## 文件清单

### 新建

- `src/components/common/CkPipelineHero.vue` + `pipeline-hero-model.js` + `pipeline-hero-model.test.js`
- `src/components/common/CkActivityFeed.vue` + `activity-feed-model.js` + `activity-feed-model.test.js`
- `src/components/common/CkTaskList.vue` + `task-list-model.js` + `task-list-model.test.js`
- `src/composables/useDashboardSummary.js` + `use-dashboard-summary.test.js`
- `src/composables/useDashboardFeed.js` + `use-dashboard-feed.test.js`
- `src/views/dashboard/DashboardPage.vue` + 同目录 `dashboard-page-copy.js`（页面文案抽离）
- `src/api/dashboard.js`（封装 summary 接口）
- `e2e/dashboard.spec.js`（Playwright）

### 修改

- `src/router/routes.js` — `dashboard` 路由 `componentKey: 'DashboardView'` 改为 `'DashboardPage'`
- `src/router/index.js`（如使用 `componentKey -> 组件` 映射） — 注册 `DashboardPage`
- `src/copy/admin.js` — 加 `dashboard` 段
- `src/app-shell.test.js` — 同步导入与 smoke 断言

### 删除

- `src/views/dashboard/DashboardView.vue`（最后一个 Task 删）

---

## Task 1：CkPipelineHero 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/pipeline-hero-model.js`
- Create: `src/components/common/pipeline-hero-model.test.js`
- Create: `src/components/common/CkPipelineHero.vue`

**接口契约：**

```
PIPELINE_STAGES：5 段定义，每段 { key, title, hint, route, runningKey }。
resolveStageMetric(stage, summary)：根据 summary 字段返回 { primary, secondary, runningCount } 三组数字。
isStageActive(stage, summary)：当 stage 有 runningCount > 0 或 stage.key 命中 summary.activeKey 时返回 true。
buildPipelineNavTarget(stage, scopeParams)：把"点这段卡片"的目标路径拼好，附 status=running 等查询参数。
```

- [ ] **Step 1: 写失败测试**

`src/components/common/pipeline-hero-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PIPELINE_STAGES,
  resolveStageMetric,
  isStageActive,
  buildPipelineNavTarget,
} from './pipeline-hero-model.js'

const SUMMARY = {
  courseCount: 12,
  materialCount: 428,
  materialReadyCount: 412,
  materialPendingCount: 16,
  knowledgeBaseCount: 9,
  knowledgeBaseRunningCount: 3,
  knowledgeBaseRunningPercents: [65, 32],
  activeKbCount: 1,
  activeKbVersion: 'v3',
  qaSessionCount: 1234,
  qaResponseTimeMs: 312,
  activeKey: 'knowledgeBases',
}

test('PIPELINE_STAGES 暴露 5 段且顺序固定', () => {
  assert.deepEqual(
    PIPELINE_STAGES.map((s) => s.key),
    ['courses', 'materials', 'knowledgeBases', 'activation', 'qa'],
  )
})

test('resolveStageMetric 课程段返回总数和待解析数', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '12',
    secondary: '',
    runningCount: 0,
  })
})

test('resolveStageMetric 资料段返回 ready/total + pending', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'materials')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '428/412',
    secondary: '16 待解析',
    runningCount: 0,
  })
})

test('resolveStageMetric 知识库段附运行中和进度', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '3/9 构建中',
    secondary: '65% / 32%',
    runningCount: 3,
  })
})

test('resolveStageMetric 激活段返回数量与最新版本', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'activation')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '9',
    secondary: '最新 v3',
    runningCount: 0,
  })
})

test('resolveStageMetric 问答段返回累计数与响应时间', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'qa')
  assert.deepEqual(resolveStageMetric(stage, SUMMARY), {
    primary: '1.2k',
    secondary: '响应 312ms（高负载下）',
    runningCount: 0,
  })
})

test('resolveStageMetric 容忍空 summary', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.deepEqual(resolveStageMetric(stage, null), {
    primary: '—',
    secondary: '',
    runningCount: 0,
  })
})

test('isStageActive 当段命中 activeKey 时为 true', () => {
  const kb = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.equal(isStageActive(kb, SUMMARY), true)
  const courses = PIPELINE_STAGES.find((s) => s.key === 'courses')
  assert.equal(isStageActive(courses, SUMMARY), false)
})

test('isStageActive 当 runningCount > 0 也激活', () => {
  const kb = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  assert.equal(isStageActive(kb, { knowledgeBaseRunningCount: 1 }), true)
})

test('buildPipelineNavTarget 拼接 status=running + courseId', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'knowledgeBases')
  const target = buildPipelineNavTarget(stage, { courseId: 'os-2026' })
  assert.equal(target.path, '/app/knowledge-bases')
  assert.deepEqual(target.query, { status: 'running', courseId: 'os-2026' })
})

test('buildPipelineNavTarget 在范围全平台时省略 courseId', () => {
  const stage = PIPELINE_STAGES.find((s) => s.key === 'qa')
  const target = buildPipelineNavTarget(stage, {})
  assert.deepEqual(target, { path: '/app/qa-sessions', query: {} })
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：`Cannot find module './pipeline-hero-model.js'`。

- [ ] **Step 3: 写实现**

`src/components/common/pipeline-hero-model.js`：

```js
export const PIPELINE_STAGES = Object.freeze([
  { key: 'courses', title: '01 课程', hint: '已开课程', route: '/app/courses' },
  { key: 'materials', title: '02 资料', hint: '资料就绪与待解析', route: '/app/materials' },
  { key: 'knowledgeBases', title: '03 知识库', hint: '构建中 / 总数', route: '/app/knowledge-bases', runningKey: 'knowledgeBaseRunningCount' },
  { key: 'activation', title: '04 激活', hint: '已激活的知识库', route: '/app/knowledge-bases?activated=1' },
  { key: 'qa', title: '05 问答', hint: '本周问答量 + 响应', route: '/app/qa-sessions' },
])

const NUMBER_FORMATTER = new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 })
function compact(n) {
  if (typeof n !== 'number' || Number.isNaN(n)) return '—'
  if (n < 1000) return String(n)
  return NUMBER_FORMATTER.format(n).toLowerCase()
}

const RESOLVERS = {
  courses(summary) {
    return {
      primary: compact(summary.courseCount),
      secondary: '',
      runningCount: 0,
    }
  },
  materials(summary) {
    const total = summary.materialCount
    const ready = summary.materialReadyCount
    const pending = summary.materialPendingCount
    const primary = total != null && ready != null ? `${total}/${ready}` : compact(total)
    const secondary = pending != null ? `${pending} 待解析` : ''
    return { primary, secondary, runningCount: 0 }
  },
  knowledgeBases(summary) {
    const running = summary.knowledgeBaseRunningCount || 0
    const total = summary.knowledgeBaseCount || 0
    const primary = running > 0 ? `${running}/${total} 构建中` : compact(total)
    const secondary = (summary.knowledgeBaseRunningPercents || [])
      .map((p) => `${p}%`)
      .join(' / ')
    return { primary, secondary, runningCount: running }
  },
  activation(summary) {
    const primary = compact(summary.activeKbCount)
    const secondary = summary.activeKbVersion ? `最新 ${summary.activeKbVersion}` : ''
    return { primary, secondary, runningCount: 0 }
  },
  qa(summary) {
    const primary = compact(summary.qaSessionCount)
    const secondary = summary.qaResponseTimeMs != null
      ? `响应 ${summary.qaResponseTimeMs}ms（高负载下）`
      : ''
    return { primary, secondary, runningCount: 0 }
  },
}

export function resolveStageMetric(stage, summary) {
  if (!summary) return { primary: '—', secondary: '', runningCount: 0 }
  const fn = RESOLVERS[stage.key]
  return fn ? fn(summary) : { primary: '—', secondary: '', runningCount: 0 }
}

export function isStageActive(stage, summary) {
  if (!summary) return false
  if (summary.activeKey === stage.key) return true
  if (stage.runningKey && summary[stage.runningKey] > 0) return true
  return false
}

export function buildPipelineNavTarget(stage, scopeParams) {
  const [path, query = ''] = stage.route.split('?')
  const queryObject = { ...Object.fromEntries(new URLSearchParams(query)) }
  if (stage.key === 'knowledgeBases') queryObject.status = 'running'
  if (scopeParams?.courseId) queryObject.courseId = scopeParams.courseId
  return { path, query: queryObject }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: 实现 Vue 组件**

`src/components/common/CkPipelineHero.vue`：

```vue
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import {
  PIPELINE_STAGES,
  resolveStageMetric,
  isStageActive,
  buildPipelineNavTarget,
} from './pipeline-hero-model.js'

const props = defineProps({
  summary: { type: Object, default: null },
  scopeParams: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
})

const router = useRouter()

const cards = computed(() =>
  PIPELINE_STAGES.map((stage) => ({
    stage,
    metric: resolveStageMetric(stage, props.summary),
    active: isStageActive(stage, props.summary),
  })),
)

function jump(stage) {
  if (props.loading) return
  const target = buildPipelineNavTarget(stage, props.scopeParams)
  router.push(target)
}
</script>

<template>
  <section class="ck-pipeline-hero" aria-label="生产流水线概览">
    <div
      v-for="card in cards"
      :key="card.stage.key"
      class="ck-pipeline-hero-card"
      :class="{ 'is-active': card.active, 'is-loading': loading }"
      role="button"
      tabindex="0"
      @click="jump(card.stage)"
      @keyup.enter="jump(card.stage)"
    >
      <header class="ck-pipeline-hero-card-head">
        <span class="ck-pipeline-hero-card-title">{{ card.stage.title }}</span>
        <span v-if="card.active" class="ck-pipeline-hero-card-pulse" aria-hidden="true" />
      </header>
      <strong class="ck-pipeline-hero-card-primary">{{ card.metric.primary }}</strong>
      <span v-if="card.metric.secondary" class="ck-pipeline-hero-card-secondary">
        {{ card.metric.secondary }}
      </span>
      <small class="ck-pipeline-hero-card-hint">{{ card.stage.hint }}</small>
    </div>
  </section>
</template>

<style scoped lang="scss">
.ck-pipeline-hero {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
}
.ck-pipeline-hero-card {
  position: relative;
  padding: var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-lg);
  cursor: pointer;
  transition: border-color var(--ckqa-duration-fast) var(--ckqa-ease-standard),
              box-shadow var(--ckqa-duration-fast) var(--ckqa-ease-standard);
  display: flex; flex-direction: column; gap: var(--ckqa-space-1);
}
.ck-pipeline-hero-card:hover { border-color: var(--ckqa-border-strong); box-shadow: var(--ckqa-shadow-sm); }
.ck-pipeline-hero-card:focus-visible { outline: none; box-shadow: var(--ckqa-focus-ring); }
.ck-pipeline-hero-card.is-active {
  border-color: var(--ckqa-accent);
  box-shadow: 0 0 0 1px var(--ckqa-accent-soft) inset;
}
.ck-pipeline-hero-card.is-loading { pointer-events: none; opacity: 0.55; }
.ck-pipeline-hero-card-head {
  display: flex; justify-content: space-between; align-items: center;
}
.ck-pipeline-hero-card-title {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
}
.ck-pipeline-hero-card-pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--ckqa-accent);
  animation: pulse var(--ckqa-duration-slow) ease-in-out infinite alternate;
}
.ck-pipeline-hero-card-primary {
  font-size: var(--ckqa-text-xl-size);
  font-weight: var(--ckqa-fw-semibold);
  color: var(--ckqa-text);
}
.ck-pipeline-hero-card-secondary {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text-muted);
}
.ck-pipeline-hero-card-hint {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
@media (max-width: 1080px) {
  .ck-pipeline-hero { grid-template-columns: repeat(2, 1fr); }
}
@keyframes pulse {
  from { transform: scale(1); opacity: 1; }
  to   { transform: scale(1.4); opacity: 0.5; }
}
</style>
```

- [ ] **Step 6: Smoke import 注入 `app-shell.test.js`**

在已有 `CkPager model exports are wired` 之后追加：

```js
import {
  PIPELINE_STAGES,
  resolveStageMetric,
} from './components/common/pipeline-hero-model.js'

test('CkPipelineHero model exports are wired', () => {
  assert.equal(PIPELINE_STAGES.length, 5)
  assert.equal(typeof resolveStageMetric, 'function')
})
```

- [ ] **Step 7: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/pipeline-hero-model.js \
        frontend/apps/admin-app/src/components/common/pipeline-hero-model.test.js \
        frontend/apps/admin-app/src/components/common/CkPipelineHero.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkPipelineHero 流水线概览组件"
```

---

## Task 2：CkActivityFeed 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/activity-feed-model.js`
- Create: `src/components/common/activity-feed-model.test.js`
- Create: `src/components/common/CkActivityFeed.vue`

`<CkActivityFeed>` 接收 `events[]`（type / title / sub / when / to），按时间倒序渲染；分组：今天 / 本周 / 更早；空态用 `<CkEmptyState>`。

- [ ] **Step 1: 写失败测试**

`src/components/common/activity-feed-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  groupEventsByPeriod,
  formatEventWhen,
  resolveEventTone,
} from './activity-feed-model.js'

const NOW = new Date('2026-05-08T10:00:00+08:00').getTime()

test('groupEventsByPeriod 按今天/本周/更早三段切', () => {
  const events = [
    { id: 'e1', when: NOW - 60_000 },
    { id: 'e2', when: NOW - 3 * 24 * 3600_000 },
    { id: 'e3', when: NOW - 30 * 24 * 3600_000 },
  ]
  const groups = groupEventsByPeriod(events, NOW)
  assert.deepEqual(groups.map((g) => g.key), ['today', 'week', 'older'])
  assert.equal(groups[0].items[0].id, 'e1')
  assert.equal(groups[1].items[0].id, 'e2')
  assert.equal(groups[2].items[0].id, 'e3')
})

test('groupEventsByPeriod 空 group 不返回', () => {
  const groups = groupEventsByPeriod([{ id: 'e1', when: NOW - 60_000 }], NOW)
  assert.deepEqual(groups.map((g) => g.key), ['today'])
})

test('groupEventsByPeriod 容错非数组', () => {
  assert.deepEqual(groupEventsByPeriod(null, NOW), [])
})

test('formatEventWhen 不到 1 分钟显示 "刚刚"', () => {
  assert.equal(formatEventWhen(NOW - 30_000, NOW), '刚刚')
})

test('formatEventWhen 1 小时内用分钟', () => {
  assert.equal(formatEventWhen(NOW - 5 * 60_000, NOW), '5 分钟前')
})

test('formatEventWhen 跨日用日期', () => {
  const ts = NOW - 3 * 24 * 3600_000
  assert.match(formatEventWhen(ts, NOW), /05-05/)
})

test('resolveEventTone 把事件 type 映射到 status-pill tone', () => {
  assert.equal(resolveEventTone('build.failed'), 'danger')
  assert.equal(resolveEventTone('build.success'), 'success')
  assert.equal(resolveEventTone('parse.running'), 'running')
  assert.equal(resolveEventTone('verification.pending'), 'warning')
  assert.equal(resolveEventTone('unknown'), 'neutral')
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 3: 写实现**

`src/components/common/activity-feed-model.js`：

```js
const ONE_DAY = 24 * 3600_000

export function groupEventsByPeriod(events, now = Date.now()) {
  if (!Array.isArray(events)) return []
  const today = []
  const week = []
  const older = []
  for (const event of events) {
    if (!event || typeof event.when !== 'number') continue
    const diff = now - event.when
    if (diff < ONE_DAY) today.push(event)
    else if (diff < 7 * ONE_DAY) week.push(event)
    else older.push(event)
  }
  const sortDesc = (a, b) => b.when - a.when
  return [
    { key: 'today', label: '今天', items: today.sort(sortDesc) },
    { key: 'week', label: '本周', items: week.sort(sortDesc) },
    { key: 'older', label: '更早', items: older.sort(sortDesc) },
  ].filter((g) => g.items.length > 0)
}

export function formatEventWhen(ts, now = Date.now()) {
  if (typeof ts !== 'number') return ''
  const diff = Math.max(0, now - ts)
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < ONE_DAY) return `${Math.floor(diff / 3600_000)} 小时前`
  const d = new Date(ts)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

const TONE_MAP = {
  'build.failed': 'danger',
  'build.success': 'success',
  'build.running': 'running',
  'parse.failed': 'danger',
  'parse.success': 'success',
  'parse.running': 'running',
  'verification.pending': 'warning',
  'verification.failed': 'danger',
  'verification.success': 'success',
  'kb.activated': 'success',
}

export function resolveEventTone(type) {
  return TONE_MAP[type] || 'neutral'
}
```

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: 实现 Vue 组件**

`src/components/common/CkActivityFeed.vue`：

```vue
<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'
import CkEmptyState from './CkEmptyState.vue'
import CkSkeleton from './CkSkeleton.vue'

import {
  groupEventsByPeriod,
  formatEventWhen,
  resolveEventTone,
} from './activity-feed-model.js'

const props = defineProps({
  events: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  now: { type: Number, default: () => Date.now() },
})

const groups = computed(() => groupEventsByPeriod(props.events, props.now))
</script>

<template>
  <section class="ck-activity-feed" aria-label="近期动态">
    <CkSkeleton v-if="loading" variant="row" :count="4" />
    <CkEmptyState
      v-else-if="!groups.length"
      icon="·"
      title="近期没有新动态"
      description="新的解析、构建、激活、验证记录会出现在这里。"
    />
    <ol v-else class="ck-activity-feed-list">
      <template v-for="group in groups" :key="group.key">
        <li class="ck-activity-feed-section-title">{{ group.label }}</li>
        <li
          v-for="event in group.items"
          :key="event.id"
          class="ck-activity-feed-item"
        >
          <CkStatusPill :tone="resolveEventTone(event.type)" :label="event.statusLabel || ''" size="sm" />
          <div class="ck-activity-feed-item-body">
            <RouterLink v-if="event.to" :to="event.to" class="ck-activity-feed-item-title">{{ event.title }}</RouterLink>
            <span v-else class="ck-activity-feed-item-title">{{ event.title }}</span>
            <span v-if="event.sub" class="ck-activity-feed-item-sub">{{ event.sub }}</span>
          </div>
          <time class="ck-activity-feed-item-when" :datetime="new Date(event.when).toISOString()">
            {{ formatEventWhen(event.when, now) }}
          </time>
        </li>
      </template>
    </ol>
  </section>
</template>

<style scoped lang="scss">
.ck-activity-feed { width: 100%; }
.ck-activity-feed-list { list-style: none; margin: 0; padding: 0; }
.ck-activity-feed-section-title {
  margin-top: var(--ckqa-space-3);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
}
.ck-activity-feed-section-title:first-child { margin-top: 0; }
.ck-activity-feed-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: var(--ckqa-space-3);
  align-items: center;
  padding: var(--ckqa-space-2) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.ck-activity-feed-item:last-child { border-bottom: none; }
.ck-activity-feed-item-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.ck-activity-feed-item-title {
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  text-decoration: none;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ck-activity-feed-item-title:hover { color: var(--ckqa-accent-strong); }
.ck-activity-feed-item-sub {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.ck-activity-feed-item-when {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 6: Smoke import**

`app-shell.test.js` 加：

```js
import {
  groupEventsByPeriod,
  resolveEventTone,
} from './components/common/activity-feed-model.js'

test('CkActivityFeed model exports are wired', () => {
  assert.equal(typeof groupEventsByPeriod, 'function')
  assert.equal(resolveEventTone('build.failed'), 'danger')
})
```

- [ ] **Step 7: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/activity-feed-model.js \
        frontend/apps/admin-app/src/components/common/activity-feed-model.test.js \
        frontend/apps/admin-app/src/components/common/CkActivityFeed.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkActivityFeed 活动时间线组件"
```

---

## Task 3：CkTaskList 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/task-list-model.js`
- Create: `src/components/common/task-list-model.test.js`
- Create: `src/components/common/CkTaskList.vue`

任务卡片列表，每条带 `<progress>`；颜色随 status 变（运行=暖橙、等待=灰、失败=暗红）。

- [ ] **Step 1: 写失败测试**

`src/components/common/task-list-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveTaskAccent,
  formatTaskProgress,
  sortTasks,
} from './task-list-model.js'

test('resolveTaskAccent 运行 / 失败 / 等待 / 完成', () => {
  assert.equal(resolveTaskAccent('running'), 'running')
  assert.equal(resolveTaskAccent('failed'), 'danger')
  assert.equal(resolveTaskAccent('pending'), 'warning')
  assert.equal(resolveTaskAccent('success'), 'success')
  assert.equal(resolveTaskAccent('unknown'), 'neutral')
})

test('formatTaskProgress 显示百分比并夹到 0~100', () => {
  assert.equal(formatTaskProgress(0.65), '65%')
  assert.equal(formatTaskProgress(1), '100%')
  assert.equal(formatTaskProgress(-0.2), '0%')
  assert.equal(formatTaskProgress(null), '—')
  assert.equal(formatTaskProgress('abc'), '—')
})

test('sortTasks 把 running 顶到最前，failed 次之，其余按 startedAt 倒序', () => {
  const list = [
    { id: 'a', status: 'success', startedAt: 1 },
    { id: 'b', status: 'running', startedAt: 10 },
    { id: 'c', status: 'failed', startedAt: 5 },
    { id: 'd', status: 'pending', startedAt: 20 },
  ]
  const sorted = sortTasks(list)
  assert.deepEqual(sorted.map((t) => t.id), ['b', 'c', 'd', 'a'])
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 3: 写实现**

`src/components/common/task-list-model.js`：

```js
const ACCENT_MAP = {
  running: 'running',
  pending: 'warning',
  failed: 'danger',
  success: 'success',
}

export function resolveTaskAccent(status) {
  return ACCENT_MAP[status] || 'neutral'
}

export function formatTaskProgress(pct) {
  const n = Number(pct)
  if (!Number.isFinite(n)) return '—'
  const clamped = Math.max(0, Math.min(1, n))
  return `${Math.round(clamped * 100)}%`
}

const PRIORITY = { running: 0, failed: 1, pending: 2, success: 3 }

export function sortTasks(tasks) {
  if (!Array.isArray(tasks)) return []
  return [...tasks].sort((a, b) => {
    const pa = PRIORITY[a.status] ?? 99
    const pb = PRIORITY[b.status] ?? 99
    if (pa !== pb) return pa - pb
    return (b.startedAt || 0) - (a.startedAt || 0)
  })
}
```

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: 实现 Vue 组件**

`src/components/common/CkTaskList.vue`：

```vue
<script setup>
import { computed } from 'vue'

import CkEmptyState from './CkEmptyState.vue'
import CkSkeleton from './CkSkeleton.vue'

import {
  resolveTaskAccent,
  formatTaskProgress,
  sortTasks,
} from './task-list-model.js'

const props = defineProps({
  tasks: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})

const sorted = computed(() => sortTasks(props.tasks))
</script>

<template>
  <section class="ck-task-list" aria-label="进行中任务">
    <CkSkeleton v-if="loading" variant="row" :count="3" />
    <CkEmptyState
      v-else-if="!sorted.length"
      icon="◐"
      title="暂无进行中任务"
      description="解析、索引、验证任务进入运行状态后会显示在这里。"
    />
    <ul v-else class="ck-task-list-items">
      <li
        v-for="task in sorted"
        :key="task.id"
        class="ck-task-list-item"
        :data-tone="resolveTaskAccent(task.status)"
      >
        <RouterLink v-if="task.to" :to="task.to" class="ck-task-list-item-title">
          {{ task.title }}
        </RouterLink>
        <span v-else class="ck-task-list-item-title">{{ task.title }}</span>
        <progress
          class="ck-task-list-item-progress"
          :value="task.progress || 0"
          max="1"
          :aria-label="`${task.title} 进度`"
        />
        <span class="ck-task-list-item-pct">{{ formatTaskProgress(task.progress) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped lang="scss">
.ck-task-list-items { list-style: none; margin: 0; padding: 0; }
.ck-task-list-item {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px var(--ckqa-space-3);
  padding: var(--ckqa-space-2) 0;
  border-bottom: 1px solid var(--ckqa-border-soft);
}
.ck-task-list-item:last-child { border-bottom: none; }
.ck-task-list-item-title {
  grid-column: 1 / 2; grid-row: 1;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  text-decoration: none;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.ck-task-list-item-title:hover { color: var(--ckqa-accent-strong); }
.ck-task-list-item-pct {
  grid-column: 2 / 3; grid-row: 1;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
}
.ck-task-list-item-progress {
  grid-column: 1 / 3; grid-row: 2;
  width: 100%; height: 6px;
  appearance: none;
  border: none;
  background: var(--ckqa-surface-muted);
  border-radius: 6px;
}
.ck-task-list-item-progress::-webkit-progress-bar {
  background: var(--ckqa-surface-muted);
  border-radius: 6px;
}
.ck-task-list-item-progress::-webkit-progress-value {
  background: var(--ckqa-running);
  border-radius: 6px;
}
.ck-task-list-item[data-tone='running'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-accent); }
.ck-task-list-item[data-tone='danger'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-danger); }
.ck-task-list-item[data-tone='warning'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-warning); }
.ck-task-list-item[data-tone='success'] .ck-task-list-item-progress::-webkit-progress-value { background: var(--ckqa-success); }
</style>
```

- [ ] **Step 6: Smoke import + 跑测试 + Commit**

```js
// app-shell.test.js
import { sortTasks, resolveTaskAccent } from './components/common/task-list-model.js'
test('CkTaskList model exports are wired', () => {
  assert.equal(typeof sortTasks, 'function')
  assert.equal(resolveTaskAccent('failed'), 'danger')
})
```

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/task-list-model.js \
        frontend/apps/admin-app/src/components/common/task-list-model.test.js \
        frontend/apps/admin-app/src/components/common/CkTaskList.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkTaskList 进行中任务列表组件"
```

---

## Task 4：dashboard API 封装

**Files:**

- Create: `src/api/dashboard.js`

封装首选 summary 接口与降级方案的列表请求；返回统一 `summary` shape。

- [ ] **Step 1: 写实现**

`src/api/dashboard.js`：

```js
import { httpClient } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function fetchDashboardSummary({ courseId } = {}) {
  const params = courseId ? { courseId } : {}
  const data = await unwrapApiResponse(await httpClient.get('/dashboard/summary', { params }))
  return data
}

export async function fetchFallbackSummary({ courseId } = {}) {
  const params = courseId ? { courseId } : {}
  const [
    courses,
    materials,
    parsing,
    knowledgeBases,
    knowledgeBaseRunning,
    qaSessions,
  ] = await Promise.all([
    httpClient.get('/courses', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    httpClient.get('/materials', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    httpClient.get('/material-parse-tasks', { params: { ...params, status: 'pending', pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    httpClient.get('/knowledge-bases', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
    httpClient.get('/index-runs', { params: { ...params, status: 'running', pageSize: 100 } }).then(unwrapApiResponse).catch(() => ({})),
    httpClient.get('/qa-sessions', { params: { ...params, summary: 1, pageSize: 1 } }).then(unwrapApiResponse).catch(() => ({})),
  ])
  return {
    courseCount: courses.total ?? courses.summary?.total ?? null,
    materialCount: materials.total ?? null,
    materialReadyCount: materials.summary?.ready ?? null,
    materialPendingCount: parsing.total ?? null,
    knowledgeBaseCount: knowledgeBases.total ?? null,
    knowledgeBaseRunningCount: knowledgeBaseRunning.total ?? (knowledgeBaseRunning.items?.length ?? 0),
    knowledgeBaseRunningPercents: (knowledgeBaseRunning.items || []).slice(0, 2).map((it) => Math.round((it.progress || 0) * 100)),
    activeKbCount: knowledgeBases.summary?.active ?? null,
    activeKbVersion: knowledgeBases.summary?.latestVersion ?? null,
    qaSessionCount: qaSessions.total ?? null,
    qaResponseTimeMs: qaSessions.summary?.responseTimeP95Ms ?? null,
    activeKey: knowledgeBaseRunning.total > 0 ? 'knowledgeBases' : null,
  }
}
```

> 注：`unwrapApiResponse` 与 `httpClient` 已在 M1+M2 之前的代码里存在。

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/api/dashboard.js
git commit -m "feat(admin-app): 新增 dashboard API 封装（summary 首选 + 分资源降级）"
```

---

## Task 5：useDashboardSummary 组合式函数 + 测试

**Files:**

- Create: `src/composables/useDashboardSummary.js`
- Create: `src/composables/use-dashboard-summary.test.js`

`useDashboardSummary({ scopeStore })` 返回 `{ state, refresh }`。`state.summary / state.loading / state.error / state.usingFallback`。

- [ ] **Step 1: 写失败测试**

`src/composables/use-dashboard-summary.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveSummaryStrategy } from './useDashboardSummary.js'

test('resolveSummaryStrategy 优先 primary, 失败时降级', async () => {
  const calls = []
  const result = await resolveSummaryStrategy({
    primary: async () => { calls.push('p'); return { courseCount: 9 } },
    fallback: async () => { calls.push('f'); return { courseCount: 9, fromFallback: true } },
  })
  assert.deepEqual(calls, ['p'])
  assert.equal(result.summary.courseCount, 9)
  assert.equal(result.usingFallback, false)
})

test('resolveSummaryStrategy primary 抛错时走降级', async () => {
  const calls = []
  const result = await resolveSummaryStrategy({
    primary: async () => { calls.push('p'); throw new Error('404') },
    fallback: async () => { calls.push('f'); return { courseCount: 9 } },
  })
  assert.deepEqual(calls, ['p', 'f'])
  assert.equal(result.summary.courseCount, 9)
  assert.equal(result.usingFallback, true)
})

test('resolveSummaryStrategy fallback 也失败时返回 error', async () => {
  const result = await resolveSummaryStrategy({
    primary: async () => { throw new Error('boom-p') },
    fallback: async () => { throw new Error('boom-f') },
  })
  assert.equal(result.summary, null)
  assert.equal(result.error.message, 'boom-f')
})
```

- [ ] **Step 2: 写实现**

`src/composables/useDashboardSummary.js`：

```js
import { reactive, readonly } from 'vue'

import { fetchDashboardSummary, fetchFallbackSummary } from '../api/dashboard.js'

export async function resolveSummaryStrategy({ primary, fallback }) {
  try {
    const summary = await primary()
    return { summary, error: null, usingFallback: false }
  } catch (primaryError) {
    try {
      const summary = await fallback()
      return { summary, error: null, usingFallback: true, primaryError }
    } catch (error) {
      return { summary: null, error, usingFallback: true }
    }
  }
}

export function useDashboardSummary({ scopeStore }) {
  const state = reactive({
    summary: null,
    loading: false,
    error: null,
    usingFallback: false,
    lastRefreshedAt: 0,
  })

  async function refresh() {
    state.loading = true
    state.error = null
    const params = scopeStore.requestParams()
    const result = await resolveSummaryStrategy({
      primary: () => fetchDashboardSummary(params),
      fallback: () => fetchFallbackSummary(params),
    })
    state.summary = result.summary
    state.error = result.error
    state.usingFallback = result.usingFallback
    state.lastRefreshedAt = Date.now()
    state.loading = false
  }

  return { state: readonly(state), refresh }
}
```

- [ ] **Step 3: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useDashboardSummary.js \
        frontend/apps/admin-app/src/composables/use-dashboard-summary.test.js
git commit -m "feat(admin-app): 新增 useDashboardSummary 组合式（首选 + 降级 + 范围芯片联动）"
```

---

## Task 6：useDashboardFeed 组合式函数 + 测试

**Files:**

- Create: `src/composables/useDashboardFeed.js`
- Create: `src/composables/use-dashboard-feed.test.js`

聚合活动 + 进行中任务，60s 轮询。返回 `{ state, refresh, stop }`。

- [ ] **Step 1: 写失败测试**

`src/composables/use-dashboard-feed.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { mergeDashboardFeed } from './useDashboardFeed.js'

test('mergeDashboardFeed 合并 indexRuns + parseTasks 为活动事件流', () => {
  const indexRuns = [
    { id: 'r1', kbName: 'KB-A v1', status: 'success', updatedAt: 1, progress: 1 },
    { id: 'r2', kbName: 'KB-B v2', status: 'running', updatedAt: 2, progress: 0.6 },
  ]
  const parseTasks = [
    { id: 'p1', materialName: 'os.pdf', status: 'failed', updatedAt: 3 },
  ]
  const result = mergeDashboardFeed({ indexRuns, parseTasks })
  assert.equal(result.events.length, 3)
  assert.equal(result.tasks.length, 1)
  // running 任务进入 tasks，其余进入事件
  assert.equal(result.tasks[0].id, 'r2')
})

test('mergeDashboardFeed 容错全空', () => {
  const result = mergeDashboardFeed({})
  assert.deepEqual(result.events, [])
  assert.deepEqual(result.tasks, [])
})
```

- [ ] **Step 2: 写实现**

`src/composables/useDashboardFeed.js`：

```js
import { reactive, readonly } from 'vue'

import { httpClient } from '../axios/index.js'
import { unwrapApiResponse } from '../api/client.js'

const POLL_INTERVAL = 60_000

export function mergeDashboardFeed({ indexRuns, parseTasks } = {}) {
  const safeIndexRuns = Array.isArray(indexRuns) ? indexRuns : []
  const safeParseTasks = Array.isArray(parseTasks) ? parseTasks : []
  const events = []
  const tasks = []
  for (const run of safeIndexRuns) {
    if (run.status === 'running') {
      tasks.push({
        id: run.id,
        title: `${run.kbName} 索引`,
        status: 'running',
        progress: run.progress || 0,
        startedAt: run.startedAt || run.updatedAt,
        to: `/app/index-runs/${encodeURIComponent(run.id)}`,
      })
    }
    events.push({
      id: `index-${run.id}`,
      type: `build.${run.status}`,
      title: `${run.kbName} 构建${formatStatusLabel(run.status)}`,
      sub: '',
      when: run.updatedAt,
      to: `/app/index-runs/${encodeURIComponent(run.id)}`,
    })
  }
  for (const task of safeParseTasks) {
    if (task.status === 'running') {
      tasks.push({
        id: `parse-${task.id}`,
        title: `${task.materialName} 解析`,
        status: 'running',
        progress: task.progress || 0,
        startedAt: task.startedAt || task.updatedAt,
        to: `/app/materials/${encodeURIComponent(task.materialId)}`,
      })
    } else {
      events.push({
        id: `parse-${task.id}`,
        type: `parse.${task.status}`,
        title: `${task.materialName} 解析${formatStatusLabel(task.status)}`,
        sub: '',
        when: task.updatedAt,
        to: `/app/materials/${encodeURIComponent(task.materialId)}`,
      })
    }
  }
  return { events, tasks }
}

function formatStatusLabel(status) {
  const map = { success: '成功', failed: '失败', running: '中', cancelled: '已取消', pending: '待开始' }
  return map[status] || status
}

export function useDashboardFeed({ scopeStore }) {
  const state = reactive({
    events: [],
    tasks: [],
    loading: false,
    error: null,
  })
  let timer = null

  async function refresh() {
    state.loading = true
    state.error = null
    try {
      const params = scopeStore.requestParams()
      const [indexRunsRes, parseTasksRes] = await Promise.all([
        httpClient.get('/index-runs', { params: { ...params, since: '24h', pageSize: 50 } }).then(unwrapApiResponse).catch(() => ({})),
        httpClient.get('/material-parse-tasks', { params: { ...params, since: '24h', pageSize: 50 } }).then(unwrapApiResponse).catch(() => ({})),
      ])
      const merged = mergeDashboardFeed({
        indexRuns: indexRunsRes.items || [],
        parseTasks: parseTasksRes.items || [],
      })
      state.events = merged.events
      state.tasks = merged.tasks
    } catch (error) {
      state.error = error
    } finally {
      state.loading = false
    }
  }

  function start() {
    refresh()
    timer = setInterval(refresh, POLL_INTERVAL)
  }
  function stop() {
    if (timer) clearInterval(timer)
    timer = null
  }

  return { state: readonly(state), refresh, start, stop }
}
```

- [ ] **Step 3: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useDashboardFeed.js \
        frontend/apps/admin-app/src/composables/use-dashboard-feed.test.js
git commit -m "feat(admin-app): 新增 useDashboardFeed 组合式（活动 + 进行中任务聚合）"
```

---

## Task 7：copy/admin.js 增 dashboard 段

**Files:**

- Modify: `src/copy/admin.js`

- [ ] **Step 1: 在 COPY 末尾追加 dashboard 段**

把 `topbar` 段之后追加：

```js
  dashboard: {
    greeting: {
      morning: '早上好',
      afternoon: '下午好',
      evening: '晚上好',
    },
    summarySentence(courseCount, materialCount, kbCount) {
      const parts = []
      if (courseCount != null) parts.push(`${courseCount} 门课程`)
      if (materialCount != null) parts.push(`${materialCount} 份资料`)
      if (kbCount != null) parts.push(`${kbCount} 个知识库`)
      if (parts.length === 0) return '欢迎回来，开始今天的工作吧。'
      return `当前看板覆盖：${parts.join(' · ')}。`
    },
    quickActions: [
      { key: 'new-kb', label: '+ 新建知识库', to: '/app/knowledge-bases?action=create' },
      { key: 'upload', label: '↑ 上传资料', to: '/app/courses' },
      { key: 'kb-validation', label: '▷ 知识库验证', to: '/app/qa-smoke' },
      { key: 'retrieval-logs', label: '≡ 检索日志', to: '/app/retrieval-logs' },
    ],
    sectionLabels: {
      pipeline: '生产流水线',
      activity: '近期动态',
      tasks: '进行中任务',
      quickActions: '快捷入口',
    },
    fallbackHint: '正在以分资源接口聚合数据，加载略慢。',
  },
```

> 注：`COPY` 整体仍是 `Object.freeze`，增加 dashboard 段后 `dashboard.summarySentence` 是函数，`Object.freeze` 不阻止函数自身被引用，但浅冻结不会冻结函数 captures，所以不影响。

- [ ] **Step 2: Smoke import**

`app-shell.test.js`：

```js
test('COPY.dashboard 字段齐全', () => {
  assert.equal(typeof COPY.dashboard.summarySentence, 'function')
  assert.deepEqual(
    COPY.dashboard.quickActions.map((q) => q.key),
    ['new-kb', 'upload', 'kb-validation', 'retrieval-logs'],
  )
  assert.equal(COPY.dashboard.summarySentence(12, 428, 9), '当前看板覆盖：12 门课程 · 428 份资料 · 9 个知识库。')
})
```

- [ ] **Step 3: Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/copy/admin.js \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): COPY.dashboard 段补齐问候语 / 摘要 / 快捷入口"
```

---

## Task 8：DashboardPage.vue 整合

**Files:**

- Create: `src/views/dashboard/DashboardPage.vue`

整合：CkPageHero（问候+摘要）+ CkPipelineHero + CkActivityFeed + CkTaskList + 快捷入口（4 个卡片）。

- [ ] **Step 1: 写组件**

`src/views/dashboard/DashboardPage.vue`：

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'

import CkPageHero from '../../components/common/CkPageHero.vue'
import CkPipelineHero from '../../components/common/CkPipelineHero.vue'
import CkActivityFeed from '../../components/common/CkActivityFeed.vue'
import CkTaskList from '../../components/common/CkTaskList.vue'

import { useDashboardSummary } from '../../composables/useDashboardSummary.js'
import { useDashboardFeed } from '../../composables/useDashboardFeed.js'
import { useScopeStore } from '../../stores/scope.js'
import { authStore } from '../../stores/auth.js'
import { COPY } from '../../copy/admin.js'

const scopeStore = useScopeStore()
const summary = useDashboardSummary({ scopeStore })
const feed = useDashboardFeed({ scopeStore })

function greetingPrefix() {
  const hour = new Date().getHours()
  if (hour < 12) return COPY.dashboard.greeting.morning
  if (hour < 18) return COPY.dashboard.greeting.afternoon
  return COPY.dashboard.greeting.evening
}

const greeting = computed(() => {
  const name = authStore.state.currentUser?.name || authStore.state.currentUser?.username || '老师'
  return `${greetingPrefix()}，${name}`
})

const subtitle = computed(() => {
  if (!summary.state.summary) return COPY.dashboard.summarySentence(null, null, null)
  return COPY.dashboard.summarySentence(
    summary.state.summary.courseCount,
    summary.state.summary.materialCount,
    summary.state.summary.knowledgeBaseCount,
  )
})

watch(() => scopeStore.state.activeCourseId, () => {
  summary.refresh()
  feed.refresh()
})

onMounted(() => {
  summary.refresh()
  feed.start()
})
onBeforeUnmount(() => {
  feed.stop()
})
</script>

<template>
  <div class="dashboard-page">
    <CkPageHero :title="greeting" :subtitle="subtitle" eyebrow="工作台" />

    <p v-if="summary.state.usingFallback" class="dashboard-page-fallback-hint">
      {{ COPY.dashboard.fallbackHint }}
    </p>

    <section class="dashboard-page-pipeline" :aria-label="COPY.dashboard.sectionLabels.pipeline">
      <CkPipelineHero
        :summary="summary.state.summary"
        :scope-params="scopeStore.requestParams()"
        :loading="summary.state.loading"
      />
    </section>

    <div class="dashboard-page-grid">
      <section class="dashboard-page-activity" :aria-label="COPY.dashboard.sectionLabels.activity">
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.activity }}</h2>
        <CkActivityFeed :events="feed.state.events" :loading="feed.state.loading" />
      </section>
      <section class="dashboard-page-tasks" :aria-label="COPY.dashboard.sectionLabels.tasks">
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.tasks }}</h2>
        <CkTaskList :tasks="feed.state.tasks" :loading="feed.state.loading" />
        <h2 class="dashboard-page-section-title">{{ COPY.dashboard.sectionLabels.quickActions }}</h2>
        <ul class="dashboard-page-quick-actions">
          <li v-for="action in COPY.dashboard.quickActions" :key="action.key">
            <RouterLink :to="action.to">{{ action.label }}</RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dashboard-page { display: flex; flex-direction: column; gap: var(--ckqa-space-6); }
.dashboard-page-fallback-hint {
  margin: 0;
  padding: var(--ckqa-space-2) var(--ckqa-space-3);
  background: var(--ckqa-warning-soft);
  border-left: 3px solid var(--ckqa-warning);
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
}
.dashboard-page-grid {
  display: grid;
  grid-template-columns: 1.55fr 1fr;
  gap: var(--ckqa-space-6);
}
@media (max-width: 1080px) {
  .dashboard-page-grid { grid-template-columns: 1fr; }
}
.dashboard-page-section-title {
  margin: 0 0 var(--ckqa-space-3);
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.dashboard-page-section-title + .dashboard-page-section-title { margin-top: var(--ckqa-space-5); }
.dashboard-page-quick-actions {
  list-style: none; margin: 0; padding: 0;
  display: grid; grid-template-columns: 1fr 1fr; gap: var(--ckqa-space-2);
}
.dashboard-page-quick-actions li a {
  display: block; padding: var(--ckqa-space-3);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text);
  text-decoration: none;
  font-size: var(--ckqa-text-sm-size);
}
.dashboard-page-quick-actions li a:hover {
  background: var(--ckqa-accent-soft);
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent-strong);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/views/dashboard/DashboardPage.vue
git commit -m "feat(admin-app): 新增 DashboardPage 整合 hero + 流水线 + 活动 + 任务 + 快捷入口"
```

---

## Task 9：路由切换 + 删除旧 DashboardView

**Files:**

- Modify: `src/router/routes.js`
- Modify: `src/router/index.js`（如使用 componentKey 映射表）
- Delete: `src/views/dashboard/DashboardView.vue`

- [ ] **Step 1: 切 routes.js 的 componentKey**

```js
// src/router/routes.js
{
  path: '/app/dashboard',
  name: 'dashboard',
  componentKey: 'DashboardPage',   // 原 'DashboardView'
  meta: { /* 不变 */ },
},
```

- [ ] **Step 2: 注册新组件**

打开 `src/router/index.js`，找到 componentKey → 组件映射表（搜 `DashboardView`），改为：

```js
DashboardPage: () => import('../views/dashboard/DashboardPage.vue'),
```

- [ ] **Step 3: 删除旧文件**

```bash
git rm frontend/apps/admin-app/src/views/dashboard/DashboardView.vue
```

如有别处直接 `import { ... } from '../views/dashboard/DashboardView.vue'`：

```bash
grep -rn "DashboardView" frontend/apps/admin-app/src/
```

把命中的引用改到 `DashboardPage`。如果只是 router 配置或 test 文件，逐项修复。

- [ ] **Step 4: Smoke import 校验**

`app-shell.test.js`：

```js
test('routes 指向 DashboardPage', () => {
  const dashboardRoute = routeRecords.find((r) => r.name === 'dashboard')
  assert.equal(dashboardRoute.componentKey, 'DashboardPage')
})
```

并删除任何旧 `DashboardView` 相关断言。

- [ ] **Step 5: 跑测试 + 构建 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
git add frontend/apps/admin-app/src/router/ \
        frontend/apps/admin-app/src/views/dashboard/ \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "refactor(admin-app): 路由切换到 DashboardPage 并删除旧 DashboardView"
```

---

## Task 10：Playwright 用例 dashboard.spec.js

**Files:**

- Create: `frontend/apps/admin-app/e2e/dashboard.spec.js`

- [ ] **Step 1: 写用例**

```js
import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

test('dashboard 看板渲染完整结构', async ({ page }) => {
  await loginAsAdmin(page)
  await page.goto('/app/dashboard')

  await expect(page.getByRole('region', { name: '生产流水线概览' })).toBeVisible()
  await expect(page.getByRole('region', { name: '近期动态' })).toBeVisible()
  await expect(page.getByRole('region', { name: '进行中任务' })).toBeVisible()
  await expect(page.getByRole('link', { name: /新建知识库/ })).toBeVisible()

  // 点知识库段跳转到列表
  const kbCard = page.locator('.ck-pipeline-hero-card').nth(2)
  await kbCard.click()
  await expect(page).toHaveURL(/\/app\/knowledge-bases/)
})
```

> 注：若 `loginAsAdmin` 不存在，参考 `e2e/data-table-layout.spec.js` 的同款身份注入逻辑创建一个 `e2e/fixtures/auth.js`。

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/e2e/dashboard.spec.js \
        frontend/apps/admin-app/e2e/fixtures/auth.js
git commit -m "test(admin-app): 新增 e2e/dashboard.spec.js 覆盖看板核心结构"
```

---

## 收尾验证

### Task 11：M3 集成验证

不修改代码：

- [ ] **Step 1: 跑全部单元测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 构建**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 3: 启动 dev + 手工巡检**

逐项验证：

1. `/app/dashboard` 渲染 hero（"早/午/晚 + 用户名" + 摘要句）。
2. 流水线 5 段显示正常（有数据时）；后端无 summary 时显示降级提示。
3. 范围芯片切换教师课程，看板数据自动按 courseId 过滤。
4. 点击流水线"03 知识库"卡片，跳到 `/app/knowledge-bases?status=running`。
5. 进行中任务列表的进度条颜色：运行=暖橙、失败=暗红、待开始=琥珀、完成=绿色。
6. 快捷入口 4 个 CTA 链接到正确路径（`/app/qa-smoke` 等）。
7. 暗色模式切换无脏白区。

---

## Self-Review

### 1. 设计稿覆盖度

| 设计稿章节 | 落到任务 |
| --- | --- |
| 8.1 Dashboard 流水线 hero | Task 1 + Task 8 |
| 8.1 近期动态 | Task 2 + Task 8 |
| 8.1 进行中任务 + 快捷入口 | Task 3 + Task 7 + Task 8 |
| 8.1 数据来源（summary 首选 + 降级） | Task 4 + Task 5 |
| 8.1 角色差异（按 courseId 过滤） | Task 5（scopeStore.requestParams 注入） |
| 8.1 文案（不"冒烟"/"P95"/"embedding"） | Task 7（COPY.dashboard） |
| 8.1 进行中任务进度条配色随状态 | Task 3（`data-tone` CSS） |
| 8.1 流水线点击跳转 | Task 1 + 路由 query |

### 2. 占位扫描

通读：每步给了具体代码、命令、预期。无 `TBD / 略 / 类似 Task N`。

### 3. 类型 / API 一致性

- `useDashboardSummary` 返回 `{ state, refresh }` → DashboardPage 使用一致。
- `useDashboardFeed` 返回 `{ state, refresh, start, stop }` → DashboardPage onMounted/onBeforeUnmount 一致。
- `PIPELINE_STAGES[i].key` 与 `summary` 字段名（courseCount / materialCount / knowledgeBaseRunningCount / activeKey）在 fallback / 测试 / 组件 三方一致。
- `COPY.dashboard.summarySentence` 签名（courseCount, materialCount, kbCount）与 DashboardPage 调用一致。

### 4. 范围检查

只覆盖 M3。未触动其他模块（M4 资料 / M5 KB / M6 问答 / M7 其他页 / M8 巡检）。

---

**计划已写完。**
