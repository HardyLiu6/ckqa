# 管理员端重设计 M6 问答会话 + 检索诊断实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `views/pages/ModulePage.vue` 承担的"问答会话域"职责切到独立页面——问答会话列表（`QaSessionListPage`）/ 问答会话详情（`QaSessionDetailPage`，双栏"消息流 + 检索诊断"）；新增通用组件 `<CkRetrievalPanel>`（设计稿 §7 预留）承载检索诊断的 4 段信息（子问题 / 检索片段 / 调用链 / 出处）。本计划按设计稿 §8.4 的"**M6a 列表 + 消息流**"与"**M6b 检索诊断面板**"双阶段推进，确保即使后端 `retrieval_trace` 字段未就绪，M6a 也能独立上线。

**Architecture:** 在 M1+M2 地基 + M4 课程/资料拆分 + M5 知识库/构建向导拆分之上，按"资源页 + 双栏交互"两条线推进。

- **M6a 列表页（QaSessionListPage）**：遵循 M4/M5 已建立的"`<CkPageHero>` + `<CkResourceCard>` 卡片网格 + `<CkPager>`"资源页模板；底层通过新增 `api/qa-sessions.js` 中的 `listQaSessions({ page, size, keyword, courseId, knowledgeBaseId, sessionType, hasAnomaly })` 读取后端（**依赖后端补 `GET /api/v1/qa-sessions` 列表接口**，见下方前置依赖）；loader 逻辑放进 `views/qa-sessions/qa-session-loader.js`，不再挂到 `module-loaders.js` 上。
- **M6a 详情页骨架（QaSessionDetailPage）**：外层 `<DetailLayout>` + 页内 CSS Grid `1fr 1fr` 双栏；左栏 `<QaMessageStream>` 渲染消息流（基于 `getQaSession(id)` + `listQaMessages(id)`），右栏占位 `<QaRetrievalPanelPlaceholder>`，在 M6b 落地前显示"本会话的检索诊断信息暂未启用"提示。
- **M6b 检索诊断面板**：新增 `<CkRetrievalPanel>`（`components/common/`）承载 4 段固定信息（子问题 / TOP 5 检索片段 / 调用链耗时分布 / 出处）；右栏 `<QaRetrievalPanelPlaceholder>` 替换为真实 `<CkRetrievalPanel>`，通过 `useQaRetrievalTrace({ message })` 读取每条 AI 消息的 `retrievalTrace` 字段（后端新字段）。
- **ModulePage.vue 不删**（与 M4/M5 策略一致），但 `/app/qa-sessions` 与 `/app/qa-sessions/:sessionId` 两条路由 `componentKey` 分别替换为 `QaSessionListPage` / `QaSessionDetailPage`。M7 收尾再清理。

**Tech Stack:** Vue 3.5 Composition API、Element Plus 2.13、Pinia 3、Axios、`node:test`、Playwright。

**前置依赖：**

- M1+M2 已合并：`<CkPageHero>` / `<CkSkeleton>` / `<CkPager>` / `<CkStatusPill>` / `<CkEmptyState>` / `<CkBreadcrumbs>` / `<CkResourceCard>`（M4 产出）/ `<CkInfoTable>`（M4 产出）/ `useResourceTabs`（M4 产出）/ ConsoleLayout / DetailLayout / `useScopeStore` 全部就绪。
- M4 已合并：课程 / 资料拆分样板可复用（范围芯片联动、`data-testid` 命名规则、`route.meta.contextChain` 面包屑注入）。
- M5 已合并：不直接复用，但"左 7fr / 右 5fr 分屏 + 右栏状态订阅"的页面模型可作为参考（本计划用 `1fr 1fr` 平分双栏）。
- 视觉打磨已合并：`.ck-glass-card` / `.ck-pressable` 工具类、品牌常量 `BRAND` 就绪。
- **后端联动（列为硬依赖 + 软依赖两档）**：
  - **硬依赖（M6a 上线必需）**：`GET /api/v1/qa-sessions?page=&size=&keyword=&courseId=&knowledgeBaseId=&sessionType=&hasAnomaly=` 列表接口。当前 `QaSessionsController` 只有 POST 创建 + GET 单个详情 + messages，**列表接口未实现**。本计划在 Task 0 明确要求先接通此接口；如后端工期不允许，**M6a 降级为"会话详情直通"**（从课程详情 / 构建向导跳进详情页，列表页暂显示"敬请期待"占位 + 列表接口联调说明）。
  - **软依赖（仅 M6b 需要）**：`QaMessageResponse` 需新增 `retrievalTrace` 字段，形如 `{ subQueries: string[], retrievedChunks: [{ id, title, section, preview, sourceId, pageRange }], callTrace: { retrievalMs, generationMs, postMs }, sources: [{ materialId, displayName, pageRange, chunkCount }] }`。未就绪时前端走 `<QaRetrievalPanelPlaceholder>`，**不阻塞 M6a**。
- 默认沿用现有 `/api/v1/qa-sessions/:id` / `/qa-sessions/:id/messages` / `/qa-sessions/:sessionId/tasks/:taskId` 接口。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run test` 全绿（含本计划新增 4 个 model 测试 + 3 个 composable 测试）。
2. `pnpm --dir frontend/apps/admin-app run build` 通过。
3. 启动 dev，登录后核心路径可用：
   - `/app/qa-sessions` 走 `QaSessionListPage`（卡片网格 + `<CkPager>` + 筛选：课程 / 知识库 / 会话类型 / 时间范围 / 含异常）；
   - `/app/qa-sessions/:sessionId` 走 `QaSessionDetailPage`（DetailLayout + 左右双栏：消息流 + 检索诊断面板）；
   - 会话列表的"异常"角标可 hover 显示原因（tone: warning）；"冒烟验证"类型用专属 pill 区分；
   - 详情页默认右栏锁定到最新一条 AI 回答的检索诊断；点击任意 AI 消息下"查看检索过程"按钮，右栏切换到对应消息；
   - 未开启 retrievalTrace 后端字段时，右栏顶部显示"本会话的检索诊断信息暂未启用"一行提示（不影响左侧消息流可用）；
   - 检索片段卡片可点击跳到资料详情（`/app/materials/:id?page=12`），实现资料页锚点跳转。
4. Playwright `e2e/qa-session-list.spec.js` + `e2e/qa-session-detail.spec.js`（新建）覆盖：
   - 列表加载卡片 + 筛选"会话类型 = 冒烟验证"后只剩冒烟会话；
   - 详情页双栏结构可见，点击第 2 条 AI 回答的"查看检索过程"后右栏子问题列表更新；
   - retrievalTrace 缺失时显示占位文案，并不报错。
5. 文案清洗：新页面内不出现 "冒烟 / embedding / 实体抽取 / P95 / MinerU / smoke" 等术语，统一走 `copy/admin.js` 的 `qa` 段 + `src/views/qa-sessions/qa-session-copy.js`（冒烟验证 → "知识库验证"保持 M5 的统一口径）。
6. `route.meta.contextChain` 正确显示："运维 / 问答会话 / {会话标题}"。
7. 暗色切换（`data-theme='dark'`）无脏白卡片。

---

## 文件清单

### 新建

**通用组件（M6b 交付）：**

- `src/components/common/CkRetrievalPanel.vue` + `retrieval-panel-model.js` + `retrieval-panel-model.test.js`

**业务组件：**

- `src/views/qa-sessions/components/QaMessageStream.vue`（左栏消息流）
- `src/views/qa-sessions/components/QaMessageBubble.vue`（单条消息气泡）
- `src/views/qa-sessions/components/QaRetrievalPanelPlaceholder.vue`（M6a 占位，M6b 合并后不删，用于后端字段缺失的降级显示）
- `src/views/qa-sessions/components/QaSessionHeader.vue`（顶部资源标题 + 元信息 chip）

**页面：**

- `src/views/qa-sessions/QaSessionListPage.vue`
- `src/views/qa-sessions/QaSessionDetailPage.vue`

**组合式函数：**

- `src/composables/useQaSessionMessages.js` + `use-qa-session-messages.test.js`（封装 `getQaSession + listQaMessages` 合并加载 + 消息流轮询）
- `src/composables/useQaRetrievalTrace.js` + `use-qa-retrieval-trace.test.js`（封装 message → retrievalTrace 映射 + 字段缺失降级）

**API 层：**

- `src/api/qa-sessions.js`（新增 `listQaSessions` / `listQaMessages` 包装，其他复用 `api/qa.js`）

**文案 / 模型：**

- `src/views/qa-sessions/qa-session-copy.js`
- `src/views/qa-sessions/qa-session-list-model.js` + `qa-session-list-model.test.js`
- `src/views/qa-sessions/qa-session-detail-model.js` + `qa-session-detail-model.test.js`

**Playwright 用例：**

- `e2e/qa-session-list.spec.js`
- `e2e/qa-session-detail.spec.js`

### 修改

- `src/router/routes.js` — `qa-sessions` / `qa-session-detail` 路由 `componentKey` 从 `'ModulePage'` 改到新页面
- `src/router/index.js` — 注册 `QaSessionListPage` / `QaSessionDetailPage`
- `src/copy/admin.js` — 新增 `qa` 段（列表、详情、检索面板、异常标签文案）
- `src/app-shell.test.js` — 同步导入与 smoke 断言（M6 路由切换 + 术语清洗）
- `src/views/pages/ModulePage.vue` — **不动**，其 `qa-session*` 分支仍保留为兜底（M7 收尾再删）

### 删除

- 暂不删除（M7 收尾再处理 `ModulePage.vue` 的 qa 分支）

---

## 阶段划分

本计划明确分为两阶段：

| 阶段 | Tasks | 后端前置 | 产出 |
| --- | --- | --- | --- |
| **M6a** | Task 1~10 | `GET /api/v1/qa-sessions` 列表接口（硬依赖）；`retrievalTrace` 字段（软依赖）| 列表页 + 详情页双栏骨架（右栏占位）+ 真实消息流 + e2e 覆盖 |
| **M6b** | Task 11~13 | `retrievalTrace` 字段（硬依赖）| `<CkRetrievalPanel>` 真实实现 + 替换右栏占位 + e2e 升级 |

M6a 合并后即可上线；M6b 的落地时机由后端 `retrievalTrace` 的 ready 日期决定，不阻塞其它里程碑。

---

## Task 0：后端前置依赖检查（M6a 启动门禁）

**非代码 Task。** 本 Task 的目的是在 M6a 动工前和后端确认列表接口的 ready 状态，不产生 git commit。

- [ ] **Step 1: 列表接口契约对齐**

与后端确认 `GET /api/v1/qa-sessions` 是否已落地。契约：

```
Query:
  page: number = 1
  size: number = 20
  keyword: string    模糊匹配 title / sessionCode
  courseId: string
  knowledgeBaseId: number
  sessionType: 'formal' | 'smoke'   // smoke = 知识库验证（UI 显示为"验证会话"）
  hasAnomaly: boolean               // 是否只看异常会话
  startAt: ISO8601
  endAt: ISO8601

Response: IPage<QaSessionResponse>（分页形状沿用现有 normalizePageData）
```

- [ ] **Step 2: 判定分支**

- ✅ 已就绪 → 按本计划 Task 1 起推进。
- ⏳ 未就绪 → 前端先走 **M6a 降级方案**：`QaSessionListPage` 改为读取最近会话（通过 `listKnowledgeBaseBuildRuns` + `listIndexRuns` 聚合 smoke 会话 ID 反查），或直接显示 `<RouteState state="coming-soon" />` 占位；Task 3~6 顺延到后端就绪后再启动。

---

## Task 1：API 层 listQaSessions / listQaMessages 封装

**Files:**

- Create: `src/api/qa-sessions.js`

**接口契约：**

```js
// 列表；hasAnomaly 是否只显示异常；sessionType 是后端枚举（formal / smoke）
export async function listQaSessions(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/qa-sessions', { params }))
}

// 会话下的消息流（顺序由 sequenceNo 决定）
export async function listQaMessages(sessionId, client = http) {
  return unwrapApiResponse(
    await client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`),
  )
}

// 单个会话详情（沿用现有 getQaSession）
export { getQaSession } from './qa.js'  // qa.js 里现有 getQaTask / sendQaMessage / createQaSession 保留
```

> 注：`api/qa.js` 已存在，但没有 `getQaSession` 命名导出（只有 `getQaTask`）。本任务先在 `qa.js` 中补 `getQaSession(sessionId)` 的同名包装，然后 `qa-sessions.js` re-export，避免前端分散多处导入。

- [ ] **Step 1: 在 `api/qa.js` 里补 `getQaSession` 包装（不破坏现有导出）**

```js
export async function getQaSession(sessionId, client = http) {
  return unwrapApiResponse(await client.get(`/qa-sessions/${encodeURIComponent(sessionId)}`))
}
```

- [ ] **Step 2: 创建 `src/api/qa-sessions.js`**

```js
import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'
import { getQaSession } from './qa.js'

export { getQaSession }

export async function listQaSessions(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/qa-sessions', { params })))
}

export async function listQaMessages(sessionId, client = http) {
  return unwrapApiResponse(
    await client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`),
  )
}
```

- [ ] **Step 3: smoke 测试（可选）** 在 `app-shell.test.js` 已有 API smoke 集合里加两行 `typeof listQaSessions === 'function'` 断言。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/api/qa-sessions.js \
        frontend/apps/admin-app/src/api/qa.js
git commit -m "feat(admin-app): 新增 api/qa-sessions 封装 list / message 查询"
```

---

## Task 2：qa-session-list-model 纯函数 + 单测

**Files:**

- Create: `src/views/qa-sessions/qa-session-list-model.js`
- Create: `src/views/qa-sessions/qa-session-list-model.test.js`

**目标：** 把"后端 `QaSessionResponse` 数组 → `<CkResourceCard>` cards"的转换逻辑沉淀为纯函数。

**接口契约：**

```
SESSION_TYPE_LABELS = { formal: '正式问答', smoke: '知识库验证' }
resolveSessionStatusTone(session)：按 status + anomaly 返回 { tone, label }
  - anomaly === true → { tone: 'warning', label: '异常' }
  - status === 'running' → { tone: 'running', label: '进行中' }
  - status === 'failed' → { tone: 'danger', label: '失败' }
  - status === 'completed' / 'done' → { tone: 'success', label: '完成' }
  - 其它 → { tone: 'neutral', label: status ?? '-' }

mapSessionToCard(session)：返回 { id, title, description, to, status, statusLabel, meta[], anomaly }
  - to = `/app/qa-sessions/${session.id}`
  - description = `学员 ${userDisplayName} · ${courseId ?? '未绑定课程'}`
  - meta = [ { label: '类型', value: SESSION_TYPE_LABELS[sessionType] }, { label: '消息数', value: messageCount ?? '-' } ]
  - anomaly = !!session.hasAnomaly（兼容后端字段 `hasAnomaly` / `anomaly`）

buildListParams(query)：标准化 route.query → 后端入参；空字段不发
```

- [ ] **Step 1: 写失败测试**（断言 10 条以上：tone 映射 / card 映射 / 空字段 / 冒烟类型映射为"知识库验证" / 参数标准化）。

- [ ] **Step 2: 写实现**（~80 行纯函数）。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/qa-session-list-model.js \
        frontend/apps/admin-app/src/views/qa-sessions/qa-session-list-model.test.js
git commit -m "feat(admin-app): qa-session-list-model 纯函数 + 单测"
```

---

## Task 3：useQaSessionMessages 组合式函数

**Files:**

- Create: `src/composables/useQaSessionMessages.js`
- Create: `src/composables/use-qa-session-messages.test.js`

**职责：** 合并加载 `getQaSession(id)` + `listQaMessages(id)`，对外暴露 `{ state, refresh, startPolling, stopPolling }`。

- 首次挂载时并行触发两次请求，聚合为 `state = { session, messages, loading, error }`。
- 当 session.status 为 `running` 时启动 5s 轮询（复用 M5 的模式），接收到 terminal 状态（`completed / failed / cancelled`）后自动停止。
- 暴露 `pollingMode: 'idle' | 'polling'`，页面可展示"正在接收新消息"角标。

**接口契约：**

```
useQaSessionMessages({ sessionId, pollIntervalMs = 5000, api })
  - state: reactive({ session, messages, loading, error, pollingMode, updatedAt })
  - refresh(): 拉一次
  - startPolling() / stopPolling()
  - onBeforeUnmount 自动 stopPolling
```

- [ ] **Step 1: 写失败测试**（纯函数部分：`normalizeSessionMessages(session, messages)` / `isSessionTerminal(status)` / `mergeMessageSequence(existing, next)`）。

- [ ] **Step 2: 写实现**（参考 M5 `useBuildRunStream` 的轮询结构，但不需要 SSE，因为消息流是拉取型）。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/composables/useQaSessionMessages.js \
        frontend/apps/admin-app/src/composables/use-qa-session-messages.test.js
git commit -m "feat(admin-app): useQaSessionMessages 组合式函数（合并加载 + 轮询）"
```

---

## Task 4：QaSessionListPage

**Files:**

- Create: `src/views/qa-sessions/QaSessionListPage.vue`
- Create: `src/views/qa-sessions/qa-session-copy.js`

**结构：**

- `<CkPageHero>` 顶部 "问答会话" + 摘要（"按课程查看学员问答记录，标记异常"） + 右侧"新建会话"按钮（仅 smoke/admin 可见）。
- 筛选区：`课程 / 知识库 / 会话类型 / 时间范围 / 仅看异常`；读写通过 `route.query` 同步。
- 主区 `<CkResourceCard>` 网格；每卡片展示：标题 / 学员 / 所属课程 / 消息数 / 会话类型 pill / "异常"角标（`tone: warning` + hover tooltip）。
- 分页：`<CkPager variant="page">`（默认 `pageSize=20`）。
- 空态：`<CkEmptyState>` "还没有会话"。
- 范围芯片（`useScopeStore`）联动 `courseId` 过滤。
- 路由 `contextChain`：`[{ label: '运维' }, { label: '问答会话' }]`。

**数据源：** `listQaSessions(qaListParams)` → `mapSessionToCard`（Task 2 模型）。

- [ ] **Step 1: 写 `qa-session-copy.js`**（hero / 筛选 / 空态 / 异常提示 / "知识库验证"文案）。
- [ ] **Step 2: 写 `QaSessionListPage.vue`**（~180 行）。
- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/QaSessionListPage.vue \
        frontend/apps/admin-app/src/views/qa-sessions/qa-session-copy.js
git commit -m "feat(admin-app): QaSessionListPage 卡片化问答会话列表"
```

---

## Task 5：QaMessageBubble + QaMessageStream 组件

**Files:**

- Create: `src/views/qa-sessions/components/QaMessageBubble.vue`
- Create: `src/views/qa-sessions/components/QaMessageStream.vue`

**QaMessageBubble 结构（单条消息气泡）：**

- Props：`message: { id, role: 'user' | 'assistant', content, createdAt, taskStatus?, hasRetrievalTrace? }`、`isActive: Boolean`（是否为右栏当前选中）。
- role=user 时气泡右对齐、中性底；role=assistant 时左对齐、`ck-glass-card` 底 + 轻微暖色渐晕。
- assistant 气泡底部：`查看检索过程` 按钮（emit `select-for-diagnosis`）。`hasRetrievalTrace === false` 时按钮禁用并附 tooltip "本回答未触发检索"。
- 支持 Markdown 渲染（沿用现有 Markdown 渲染方式，若无则用纯文本 + 换行保留）。

**QaMessageStream 结构（左栏）：**

- Props：`messages: Array`、`activeMessageId: Number | null`、`loading: Boolean`、`pollingMode: String`。
- Emit：`select-for-diagnosis(messageId)`。
- 顶部可选"正在接收新消息"角标（当 `pollingMode === 'polling'`）。
- 最底部无限加载提示？**不做**——按设计稿 §7 "活动时间线 / 通知 / 日志类 用 load-more，资源列表用传统翻页"，本场景是消息流不超过 200 条，直接渲染即可。
- 空态：`<CkEmptyState>` "尚未发起问答"。

- [ ] **Step 1: 写两个组件**（含 scoped style，走设计 token，角色差异用 `data-role` 属性 + CSS 属性选择器）。
- [ ] **Step 2: smoke 测试**（可选，vitest 不引入，只做视觉手检）。
- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/components/QaMessageStream.vue \
        frontend/apps/admin-app/src/views/qa-sessions/components/QaMessageBubble.vue
git commit -m "feat(admin-app): QaMessageStream + QaMessageBubble 消息流组件"
```

---

## Task 6：QaRetrievalPanelPlaceholder 占位组件

**Files:**

- Create: `src/views/qa-sessions/components/QaRetrievalPanelPlaceholder.vue`

设计稿 §8.4 明确允许字段未就绪时走"N/A 占位 + 顶部提示"。这个占位在 M6a 就绪、M6b 后作为降级路径保留。

**结构：**

- 挂 `.ck-glass-card` 作外壳。
- 顶部固定提示："本会话的检索诊断信息暂未启用"（`aria-live="polite"`）。
- 中间 4 段骨架占位（子问题 / TOP 5 / 调用链 / 出处）的灰色虚描边框，复用 `<CkSkeleton variant="text" />`。
- 底部"了解详情"链接 → 占位跳到 `/app/retrieval-logs`（未开放页）。

- [ ] **Step 1: 写组件 + Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/components/QaRetrievalPanelPlaceholder.vue
git commit -m "feat(admin-app): QaRetrievalPanelPlaceholder 检索诊断占位"
```

---

## Task 7：qa-session-detail-model 纯函数 + 单测

**Files:**

- Create: `src/views/qa-sessions/qa-session-detail-model.js`
- Create: `src/views/qa-sessions/qa-session-detail-model.test.js`

**目标：** 把"消息数组 → 默认选中 messageId"与"context chain 构造"等纯函数抽出来，保持 `QaSessionDetailPage.vue` < 300 行。

**接口契约：**

```
resolveDefaultActiveMessageId(messages)：
  - 从后往前找第一个 role === 'assistant' 且有 content 的消息，返回其 id
  - 若无 → 返回 null

resolveSessionTitle(session)：优先 session.title，其次 `会话 #${session.sessionCode ?? session.id}`

buildQaSessionContextChain(session)：
  [{ label: '运维', to: '/app/qa-sessions' },
   { label: '问答会话', to: '/app/qa-sessions' },
   { label: resolveSessionTitle(session) }]

resolveMessageRetrievalAvailable(message)：
  - 有 retrievalTrace 字段 → true
  - role !== 'assistant' → false
  - 其它 → false
```

- [ ] **Step 1: 写失败测试**（10 条以上）。
- [ ] **Step 2: 写实现 + Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/qa-session-detail-model.js \
        frontend/apps/admin-app/src/views/qa-sessions/qa-session-detail-model.test.js
git commit -m "feat(admin-app): qa-session-detail-model 纯函数 + 单测"
```

---

## Task 8：QaSessionDetailPage（M6a 骨架）

**Files:**

- Create: `src/views/qa-sessions/QaSessionDetailPage.vue`
- Create: `src/views/qa-sessions/components/QaSessionHeader.vue`

**结构：**

```vue
<template>
  <div class="qa-session-detail-page">
    <QaSessionHeader :session="session" />

    <div class="qa-session-detail-page__columns">
      <section class="qa-session-detail-page__messages">
        <QaMessageStream
          :messages="messages"
          :active-message-id="activeMessageId"
          :loading="state.loading"
          :polling-mode="state.pollingMode"
          @select-for-diagnosis="setActiveMessageId"
        />
      </section>
      <aside class="qa-session-detail-page__panel">
        <!-- M6a 默认：占位 -->
        <QaRetrievalPanelPlaceholder />
        <!-- M6b 合并后，这里会换成：
             <CkRetrievalPanel :trace="activeMessage?.retrievalTrace" :fallback-hint="..." />
        -->
      </aside>
    </div>
  </div>
</template>
```

- 使用 `useQaSessionMessages({ sessionId })` 提供 `state` 和消息流。
- `activeMessageId` 默认由 `resolveDefaultActiveMessageId(messages)` 计算，用户点击消息后更新。
- 响应式布局：`1280px` 以下 `grid-template-columns: 1fr`，右栏折叠为 `<details>` 抽屉。

- [ ] **Step 1: 写 `QaSessionHeader.vue`**（标题 + 会话类型 pill + 学员 + 创建时间 + 异常标识）。
- [ ] **Step 2: 写 `QaSessionDetailPage.vue`**（~220 行）。
- [ ] **Step 3: `route.meta.contextChain` 注入**。
- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/qa-sessions/QaSessionDetailPage.vue \
        frontend/apps/admin-app/src/views/qa-sessions/components/QaSessionHeader.vue
git commit -m "feat(admin-app): QaSessionDetailPage 双栏骨架（M6a）"
```

---

## Task 9：路由切换 + copy/admin.js + smoke 同步

**Files:**

- Modify: `src/router/routes.js`
- Modify: `src/router/index.js`
- Modify: `src/copy/admin.js`
- Modify: `src/app-shell.test.js`

**步骤：**

- `routes.js`：把 `qa-sessions` / `qa-session-detail` 两条路由的 `componentKey` 改到 `QaSessionListPage` / `QaSessionDetailPage`。
- `index.js`：在 `componentMap` 注册 2 个新组件。
- `copy/admin.js`：新增 `qa: { _docRef: './views/qa-sessions/qa-session-copy.js' }`。
- `app-shell.test.js`：新增 M6 smoke：
  - `findByName('qa-sessions').componentKey === 'QaSessionListPage'`
  - `findByName('qa-session-detail').componentKey === 'QaSessionDetailPage'`
  - 术语清洗：`qa-session-copy.js` 不含 `冒烟 / embedding / 实体抽取 / MinerU / P95 / smoke`。

- [ ] **Step 1-4: 修改 + 测试 + 构建 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
git add frontend/apps/admin-app/src/router/ \
        frontend/apps/admin-app/src/copy/admin.js \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "refactor(admin-app): 问答会话路由切换到 M6 新页面"
```

---

## Task 10：Playwright e2e（M6a 收尾）

**Files:**

- Create: `frontend/apps/admin-app/e2e/qa-session-list.spec.js`
- Create: `frontend/apps/admin-app/e2e/qa-session-detail.spec.js`

**qa-session-list.spec.js 覆盖：**

1. 登录后访问 `/app/qa-sessions`，看到卡片网格 + 分页；
2. 切换 "会话类型 = 知识库验证"（URL 写入 `?sessionType=smoke`），列表只剩 smoke 会话；
3. 异常角标 hover 展示 tooltip；点击卡片跳到 `/app/qa-sessions/:id`。

**qa-session-detail.spec.js 覆盖：**

1. 进入详情页，左栏消息流可见（至少 2 条消息 mock）；
2. 右栏默认显示 `<QaRetrievalPanelPlaceholder>`（未接入字段的降级路径）；
3. 点击第 2 条 AI 回答的"查看检索过程"按钮，`activeMessageId` 更新（可通过气泡 `data-active="true"` 断言）；
4. 暗色切换后双栏均无脏白。

mocks：在 `e2e/fixtures/` 下新增 `qa-session-mock.js`，提供 `makeQaSessionListHandler()` + `makeQaSessionDetailHandler()` + `makeQaMessagesHandler()`。

- [ ] **Step 1: 写 fixture**。
- [ ] **Step 2: 写两个 spec + Commit**

```bash
git add frontend/apps/admin-app/e2e/qa-session-list.spec.js \
        frontend/apps/admin-app/e2e/qa-session-detail.spec.js \
        frontend/apps/admin-app/e2e/fixtures/qa-session-mock.js
git commit -m "test(admin-app): e2e/qa-session-*.spec.js 覆盖 M6a 流程"
```

**M6a 在此节点可合并基线（与 M5 同等地位），供后端继续推进 retrievalTrace 字段。**

---

## Task 11：CkRetrievalPanel 模型 + 组件 + 测试（M6b）

**Files:**

- Create: `src/components/common/retrieval-panel-model.js`
- Create: `src/components/common/retrieval-panel-model.test.js`
- Create: `src/components/common/CkRetrievalPanel.vue`

**接口契约（retrieval-panel-model.js）：**

```
normalizeRetrievalTrace(rawTrace)：
  - 空输入 → { subQueries: [], chunks: [], callTrace: null, sources: [] }
  - 截断 chunks 到 TOP 5
  - 兜底 sources 每项 { materialId, displayName, pageRange, chunkCount }

resolveCallTraceBreakdown(callTrace)：
  - 返回 [ { label: '检索', ms, percent }, { label: '模型生成', ms, percent }, { label: '后处理', ms, percent } ]
  - percent = ms / totalMs * 100（totalMs 由三段之和计算）
  - totalMs <= 0 时 percent 统一为 0

resolveChunkNavigation(chunk)：
  - 返回 { to: `/app/materials/${chunk.materialId}?page=${chunk.pageRange?.start ?? ''}`, disabled: !chunk.materialId }
```

**CkRetrievalPanel.vue 结构（渲染 4 段）：**

- 顶部状态条：若 `trace == null` → 显示 `<slot name="fallback" />` 或默认提示 "本回答未触发检索"。
- 段 1 子问题拆分：`<ul>` 简洁列表。
- 段 2 检索片段（TOP 5）：卡片列表；点击跳 `resolveChunkNavigation(chunk).to`。
- 段 3 调用链：3 段水平堆叠条 + 绝对耗时数字。
- 段 4 出处：纯列表；每行点击跳资料详情。
- Props：`trace: Object | null`、`fallbackHint: String = '本回答未触发检索'`。

- [ ] **Step 1: 写失败测试（10+ 条）**。
- [ ] **Step 2: 写模型 + 组件**。
- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/retrieval-panel-model.js \
        frontend/apps/admin-app/src/components/common/retrieval-panel-model.test.js \
        frontend/apps/admin-app/src/components/common/CkRetrievalPanel.vue
git commit -m "feat(admin-app): 新增 CkRetrievalPanel 检索诊断面板（M6b）"
```

---

## Task 12：useQaRetrievalTrace + 替换右栏占位

**Files:**

- Create: `src/composables/useQaRetrievalTrace.js`
- Create: `src/composables/use-qa-retrieval-trace.test.js`
- Modify: `src/views/qa-sessions/QaSessionDetailPage.vue`

**职责：** 把"活跃消息 → retrievalTrace"映射 + 字段缺失判定封装为组合式。

```
useQaRetrievalTrace({ messages, activeMessageId })
  - trace: computed → 活跃消息的 retrievalTrace（为空返回 null）
  - isAvailable: computed → !!trace
  - fallbackHint: computed → 基于 message.taskStatus 决定占位文案
```

QaSessionDetailPage 的右栏改为：

```vue
<aside class="qa-session-detail-page__panel">
  <CkRetrievalPanel
    v-if="retrieval.isAvailable.value"
    :trace="retrieval.trace.value"
  />
  <QaRetrievalPanelPlaceholder v-else :hint="retrieval.fallbackHint.value" />
</aside>
```

- [ ] **Step 1: 写失败测试**（useQaRetrievalTrace 纯函数部分）。
- [ ] **Step 2: 写实现 + 页面接入**。
- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/composables/useQaRetrievalTrace.js \
        frontend/apps/admin-app/src/composables/use-qa-retrieval-trace.test.js \
        frontend/apps/admin-app/src/views/qa-sessions/QaSessionDetailPage.vue
git commit -m "feat(admin-app): useQaRetrievalTrace + 替换右栏占位（M6b）"
```

---

## Task 13：Playwright 升级 + M6b 收尾

**Files:**

- Modify: `frontend/apps/admin-app/e2e/qa-session-detail.spec.js`

**新增用例：**

1. mock 一条含 `retrievalTrace` 字段的 AI 消息，详情页右栏显示 4 段（子问题 / TOP 5 / 调用链 / 出处）；
2. 点击检索片段卡片，跳到 `/app/materials/:id?page=12`；
3. mock 一条 `taskStatus: 'failed'` 且无 `retrievalTrace` 的消息 → 占位提示文案为"检索失败"。

- [ ] **Step 1: 扩充用例 + Commit**

```bash
git add frontend/apps/admin-app/e2e/qa-session-detail.spec.js
git commit -m "test(admin-app): e2e/qa-session-detail.spec.js 覆盖 M6b 检索诊断面板"
```

---

## 收尾验证

### Task 14：M6 集成验证

不修改代码：

- [ ] **Step 1: 跑全部单元测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 构建通过**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 3: Playwright 业务相关用例全通过**

```bash
pnpm --dir frontend/apps/admin-app exec playwright test e2e/qa-session-list.spec.js e2e/qa-session-detail.spec.js
```

- [ ] **Step 4: 启动 dev + 手工巡检**

逐项验证：

1. `/app/qa-sessions` 列表卡片网格可见；筛选"知识库验证"过滤正常；`<CkPager>` 翻页正常。
2. 范围芯片切换课程后，会话列表按 `courseId` 过滤。
3. 异常会话有 `warning` 角标，hover 显示原因。
4. 点击任意会话进入详情；双栏布局正常；默认右栏锁定到最新 AI 回答。
5. 点击不同 AI 消息的"查看检索过程"，右栏切换；点击检索片段可跳资料详情。
6. 后端 `retrievalTrace` 未返回时，右栏显示占位提示，不影响左栏消息流使用。
7. 暗色切换：列表 / 详情两个页面均无脏白。
8. 面包屑链路准确："运维 / 问答会话 / {会话标题}"。

---

## Self-Review

### 1. 设计稿覆盖度

| 设计稿章节 | 落到任务 |
| --- | --- |
| 7 `<CkRetrievalPanel>` | Task 11 |
| 8.4 问答会话 / 学员视角标题区 | Task 8 + Task 4 |
| 8.4 双栏：消息流 + 检索诊断 | Task 8（骨架）+ Task 12（真实面板） |
| 8.4 查看检索过程按钮 / 右栏跟随活跃消息 | Task 5（emit）+ Task 8（state 绑定）+ Task 12（trace 映射） |
| 8.4 未触发检索 / 检索失败文案 | Task 6（占位）+ Task 12（fallbackHint） |
| 8.4 数据来源 retrievalTrace 分阶段交付 | 本计划 Task 0 门禁 + 阶段划分 |
| 8.4 列表筛选（课程 / 知识库 / 时间 / 异常）+ 异常角标 | Task 2（model）+ Task 4（页面） |
| 9 `QaSessionList / QaSessionDetail` 拆出独立页面 | Task 4 + Task 8 |
| 10.2 文案术语清洗（冒烟 → 知识库验证） | Task 4（copy）+ Task 9（smoke 测试） |
| 11.2 可访问性（`aria-live="polite"` / 键盘 Tab） | Task 5（气泡）+ Task 8（Tab 顺序） |

### 2. 占位扫描

通读：所有 Task 步骤给出具体路径、契约、命令、预期；无 `TBD / 略 / 类似 Task N`。Task 11 / 12 的 `retrievalTrace` 字段结构在 Task 0 契约段已明确列出。

### 3. 类型 / API 一致性

- `listQaSessions({ page, size, keyword, courseId, knowledgeBaseId, sessionType, hasAnomaly })` 在 Task 1 定义，Task 4 / Task 2 统一消费。
- `useQaSessionMessages({ sessionId })` 返回 `{ session, messages, loading, error, pollingMode, updatedAt }`，Task 8 / Task 12 的绑定严格一致。
- `retrievalTrace` 字段结构（`subQueries / chunks / callTrace / sources`）在 Task 0 / Task 11 / Task 12 三处统一，不存在别名。
- 所有新组件 slot / props 命名遵循 M1-M2 建立的 `<Ck*>` 前缀 + `tone / status / state` 三字段惯例。

### 4. 范围检查

只覆盖 M6。`ModulePage.vue` 不删（M7 收尾再处理），但 `/app/qa-sessions*` 两条路由不再走它。M3 Dashboard / M4 课程 + 资料 / M5 知识库 + 构建向导 / M7 其他页 / M8 巡检不触动。

### 5. 与 M4 / M5 衔接

- 完全复用 M4 的 `<CkResourceCard>` / `<CkInfoTable>` / `useResourceTabs` 和 "page-copy.js" 文案集中模式。
- 不复用 M5 的 `useBuildRunStream`（QA 场景走拉取型轮询，不需要 SSE），但轮询状态机模式（`startPolling` / `stopPolling` / onBeforeUnmount 自动清理）与 M5 保持一致。
- 文案清洗规则与 M5 `sanitizeLogMessage` 互补——`qa-session-copy.js` 是人工保证常量不含禁用词，M5 日志清洗是运行时对后端下发文本的消毒。

### 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 后端 `GET /api/v1/qa-sessions` 列表接口未就绪 | 高（阻塞 M6a 整个列表页） | Task 0 明确要求列表接口作为 M6a 硬依赖；未就绪时降级为 `<RouteState state="coming-soon" />`；Task 8 详情页可通过 `/app/qa-sessions/:id` 直链访问，不受列表页阻塞 |
| `retrievalTrace` 字段后端延期 | 中 | 设计稿允许拆分；本计划 M6a/M6b 分界明确；M6a 完成即可合并基线，不绑定 M6b |
| `useQaSessionMessages` 轮询对后端压力 | 低 | 默认 5s 间隔，仅在 session.status === 'running' 时启用；terminal 后立即停止；UI 可加"暂停刷新"按钮预留扩展 |
| 消息 Markdown 渲染安全（XSS） | 中 | 沿用现有 Markdown 渲染；若项目未接，先用纯文本 + `white-space: pre-wrap`，M8 巡检时统一接入 DOMPurify |
| 文案遗漏清洗"冒烟"出现在 pill 文本 | 低 | Task 2 / Task 4 的 copy 常量 + Task 9 smoke 测试强断言 |
| 消息流长度过大（200+）渲染卡顿 | 低 | 首版不做虚拟滚动；M8 巡检时若出现卡顿，引入 `v-memo` 或 `virtualList` 组件 |

---

**计划已写完。**
