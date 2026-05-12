# 需求文档：管理员端 M7 —— 其他页面拆分与适配

- 上位设计：`docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md`
- 配套技术设计：`./design.md`
- 本里程碑定位：M1（设计系统）/ M2（布局壳）已落地前提下，承接"重刷不重做"的收尾页面，让 `ModulePage.vue` 退役为兜底
- 目标读者：负责 `frontend/apps/admin-app/` 的开发同学、QA、以及审阅 PR 的前端架构 owner
- 文档语言：中文

> EARS（Easy Approach to Requirements Syntax）语法速查：
> - Ubiquitous：The system shall …
> - Event-driven：WHEN <event>, THE SYSTEM SHALL …
> - State-driven：WHILE <state>, THE SYSTEM SHALL …
> - Optional feature：WHERE <feature present>, THE SYSTEM SHALL …
> - Unwanted behavior：IF <error condition>, THEN THE SYSTEM SHALL …

---

## 1. 介绍

### 1.1 业务背景

管理员端视觉与交互重设计的第 7 个里程碑（M7）承担"把剩余页面纳入新设计系统"的任务。M3~M6 已经把 Dashboard、课程/资料、知识库/构建向导、问答会话四条主线拆出独立页面，`views/pages/ModulePage.vue` 这个 3957 行巨石目前仍承担 users / roles / permissions 三条路由。M7 完成后：

1. 用户、角色、权限页面从 ModulePage 中拆出，成为独立组件；
2. 系统健康页 `HealthView` 改名升级为 `HealthPage`，吃上 M1 设计系统；
3. 原 `/app/qa-smoke` 占位升级为真页面 `KbValidationPage`，让教师/管理员可脱离构建向导单独发起"抽样问答"；
4. 一批占位路由（`/app/retrieval-logs`、用户详情、索引版本列表、授权审计）仍用 `RouteState` 渲染，但视觉对齐 AuthLayout；
5. `ModulePage.vue` 不再被任何路由直接引用，保留文件作为回滚兜底；
6. 清理残留的"冒烟 / embedding / 实体抽取 / P95 / MinerU"等工程术语，集中到 `copy/admin.js`；
7. 抽取 `composables/useQaPolling.js`，收口 `useLongTaskState / useMaterialLifecycle` 的 import 路径。

### 1.2 价值主张

- 对管理员：系统健康、用户/角色/权限、知识库验证 4 类设置/运维场景获得一致的视觉语言，不再与 Dashboard、课程、知识库形成视觉断层。
- 对教师：`/app/qa-smoke` 从"敬请期待"变为可用的"知识库验证"，不需要再进入构建向导才能跑一次抽样问答。
- 对开发：`ModulePage.vue` 退役、`views/pages/*.js` 工具层明确哪部分长期保留、哪部分迁入 `composables/`，后续维护成本下降。
- 对全站一致性：文案层面进一步清洗工程术语，教师视角不再碰到"冒烟 / embedding / P95 / MinerU"等困惑词。

### 1.3 范围与非范围

**In Scope**：

1. 用户列表、角色列表、权限列表三条独立页面（只做"列表 + 搜索 + 筛选 + 分页"，写操作按钮保留但 M7 内禁用）。
2. 系统健康页文件改名 + 视觉重做 + 诊断日志术语清洗。
3. 知识库验证页从 coming-soon 升级为真页面（单次验证 + 本地历史 + 结果摘要）。
4. `RouteState` 视觉刷新（吃 Token，顶部加暖橙光晕）。
5. 文案集中到 `copy/admin.js` + 新增 `cleanTerms / TERM_REPLACEMENT_MAP` 工具。
6. `composables/useQaPolling.js` 的新增与旧文件删除；`useLongTaskState / useMaterialLifecycle` 的 import 路径收口。
7. 对应的单元测试、Playwright E2E、暗色视觉快照。

**Out of Scope**：

1. 用户 / 角色 / 权限的 CRUD 写操作（新建、编辑、删除、授权）。
2. 用户详情 `/app/users/:userId`、检索日志详情 `/app/retrieval-logs/:logId`、授权审计日志、索引版本列表的真页面实现。
3. `DataTableShell.vue` 在 M3~M6 已完成页面中的替换。
4. `api/*.js` 的签名改动；Pinia store 的新增；axios 层的调整。
5. 后端接口契约变更（含 `/api/v1/roles`、`/api/v1/permissions`、"按 KB 直接验证" 等）。
6. `ModulePage.vue` 文件的物理删除（M7 只让其不被引用）。
7. 新引入图表库（ECharts / D3）或新通道（WebSocket / SSE）。

### 1.4 依赖与假设

1. M1 已交付 `CkPageHero / CkStatusPill / CkResourceCard / CkPager / CkSkeleton / CkEmptyState / CkInfoTable / CkLogStream / CkSplitProgress` 等基础组件。
2. M2 已交付 `ConsoleLayout / DetailLayout / WorkflowLayout / AuthLayout / AppTopbar / SideNavigation` 与 `useAuthStore / useScopeStore`。
3. Java `/api/v1` 相关端点（`/users`、`/system/health`、`/kb-build-runs/:id/qa-smoke`、`/qa-sessions/:id`、`/knowledge-bases`）可用。
4. 后端若提供 `/api/v1/roles` 与 `/api/v1/permissions`，M7 按该接口消费；若暂未提供，M7 允许"短期用用户视图聚合"作为兜底（见开放问题 OP-2）。
5. `/api/v1/kb-build-runs/:buildRunId/qa-smoke` 是 KbValidationPage 的真实触发通道，复用 build-run 机制，不要求后端新增端点（见开放问题 OP-1）。

---

## 2. 角色与权限矩阵

M7 页面涉及的角色与其可见/可操作能力：

| 能力 | 平台管理员（admin） | 教师（teacher） | 助教（assistant） | 只读运维（auditor） |
| --- | --- | --- | --- | --- |
| 查看用户列表 | ✅ | ❌（侧栏隐藏） | ❌（侧栏隐藏） | ✅（只读） |
| 切换用户启用状态 | ✅ | ❌ | ❌ | ❌ |
| 查看角色列表 | ✅ | ❌ | ❌ | ✅ |
| 查看权限列表 | ✅ | ❌ | ❌ | ✅ |
| 查看系统健康 | ✅（含诊断日志与刷新） | ✅（总览与服务卡，无刷新对话框） | ✅（同教师） | ✅（含刷新） |
| 发起知识库验证 | ✅（全平台 KB） | ✅（本人课程 KB） | ❌（只读） | ❌（只读） |
| 查看知识库验证历史 | ✅（全部） | ✅（本人课程） | ✅（参与课程） | ✅（全部） |
| 打开 RouteState 占位 | ✅ | ✅ | ✅ | ✅ |

> 权限判定走 M2 已搭好的 `useAuthStore.canAccess(permissionList)`；M7 页面只在写操作按钮处 `v-if` 守护，不重写权限模型。

---

## 3. 功能需求（FR）

### 3.1 FR-1 用户列表页（UserListPage）

**用户故事**：作为平台管理员，我想在 `/app/users` 看到一份清晰的用户列表，能够快速搜索、翻页、看到每个用户的角色徽章，并一眼判断账号是否启用，这样我能在发生登录异常或权限问题时快速定位到具体用户。

**验收标准**：

1. WHEN 用户以 `admin` 或 `auditor` 身份进入 `/app/users`, THE SYSTEM SHALL 渲染独立页面 `UserListPage.vue`，使用 `ConsoleLayout` 外壳、`CkPageHero` 页头、Element Plus `el-table` 展示用户数据。
2. THE SYSTEM SHALL 在表格中展示以下列：用户名、展示名称、状态（使用 `CkStatusPill` 而非裸 `el-tag`）、角色徽章（至少可见 3 个，超出以"+N"角标展示）、最近登录时间、操作列。
3. WHEN 首次加载, THE SYSTEM SHALL 以 `page=1, size=20` 调用 `listUsers({ page, size, keyword })`, 并通过 `normalizePageData` 统一归一化分页 payload。
4. WHILE 数据处于加载中, THE SYSTEM SHALL 渲染 `<CkSkeleton variant="row" :count="6" />` 占位骨架，不展示空白表格。
5. WHEN 用户点击 `CkPager` 的页码或改变每页条数, THE SYSTEM SHALL 同步更新 `route.query`（`page / size`）并重新调用 `listUsers`。
6. WHEN 用户在搜索框输入关键字并回车, THE SYSTEM SHALL 以 `keyword` 参数重发请求，同步写入 `route.query.keyword`；清空时同步清除。
7. IF 请求返回空结果，THEN THE SYSTEM SHALL 渲染 `<CkEmptyState>`，文案来自 `COPY.users.list.empty`。
8. IF 请求失败，THEN THE SYSTEM SHALL 渲染错误面板并显示后端 `message`，同时保留"重试"按钮。
9. WHERE 用户具备 `user:write` 权限, THE SYSTEM SHALL 在 `CkPageHero actions` 位渲染"新建用户"按钮，但 M7 内该按钮 `disabled` 并带 tooltip："后续里程碑开放"。
10. WHERE 用户仅具备 `user:read`, THE SYSTEM SHALL 隐藏"切换启用状态"等写操作按钮。
11. THE SYSTEM SHALL 在表格上添加 `data-testid="user-table"` 以支撑 E2E 稳定选择。
12. THE SYSTEM SHALL 让文件行数 ≤ 280 行，超过时把列定义抽到 `user-list-columns.js`。

### 3.2 FR-2 角色列表页（RoleListPage）

**用户故事**：作为平台管理员，我希望在 `/app/roles` 快速浏览角色及其授权范围，了解"这个角色能做什么"，并为后续授权决策做准备。

**验收标准**：

1. WHEN 用户以 `admin` 或 `auditor` 身份进入 `/app/roles`, THE SYSTEM SHALL 渲染独立页面 `RoleListPage.vue`。
2. THE SYSTEM SHALL 展示以下列：角色编码、角色名称、状态、权限范围（取 `role.permissions[].name` 拼接，超过 3 个显示"前三名 + 等 N 项"）、更新时间、操作列。
3. THE SYSTEM SHALL 调用 `listRoles({ page, size, keyword })`。IF 后端未提供 `/api/v1/roles`, THEN THE SYSTEM SHALL 回退至按"用户视图聚合"模式，并在页头展示提示芯片"数据来自用户视图聚合"。
4. WHERE 用户具备 `role:write`, THE SYSTEM SHALL 保留"新建角色"按钮，但 M7 内 `disabled` + tooltip。
5. THE SYSTEM SHALL 让文件行数 ≤ 240 行。
6. 其他加载/分页/空态/错误要求同 FR-1。

### 3.3 FR-3 权限列表页（PermissionListPage）

**用户故事**：作为平台管理员，我希望在 `/app/permissions` 查到最小权限点清单，并能按资源维度筛选，方便在排查授权问题时对照。

**验收标准**：

1. WHEN 用户以 `admin` 或 `auditor` 身份进入 `/app/permissions`, THE SYSTEM SHALL 渲染独立页面 `PermissionListPage.vue`。
2. THE SYSTEM SHALL 展示以下列：权限编码、权限名称、资源（`course / material / kb / qa / user / role / permission / system`）、操作（`read / write / *`）、状态。
3. THE SYSTEM SHALL 在页头提供资源维度的 `el-select` 筛选；选中后以 `resource` 参数调用 `listPermissions`。
4. IF 后端未提供 `/api/v1/permissions`, THEN 处理策略同 FR-2 的兜底。
5. THE SYSTEM SHALL 让文件行数 ≤ 220 行。
6. 其他加载/分页/空态/错误要求同 FR-1。

### 3.4 FR-4 系统健康页（HealthPage）

**用户故事**：作为平台管理员或运维，我希望打开 `/app/health` 立刻看到整体健康状态，快速定位异常服务，并能点一次刷新重新探测；作为教师，我希望只看到总览信息，不被工程细节淹没。

**验收标准**：

1. THE SYSTEM SHALL 把 `views/system/HealthView.vue` 改名为 `HealthPage.vue`，保留所有现有模块导出接口（如 `normalizeHealthResponse`）。
2. WHEN 用户进入 `/app/health`, THE SYSTEM SHALL 调用 `getSystemHealth()` 一次，并在 `CkPageHero actions` 位展示聚合状态 `CkStatusPill`（tone 取 `success / warning / danger / blocked` 之一）。
3. THE SYSTEM SHALL 为每个依赖服务渲染一张卡片，卡片内使用 `CkStatusPill` 表示单服务状态 + `CkInfoTable` 展示 key-value 细节。
4. THE SYSTEM SHALL 在诊断区渲染 `CkLogStream`，每行格式为 `"{service.displayName}：{reachLabel} / {readyLabel} {清洗后的 message}"`。
5. WHEN 用户点击"刷新"按钮, THE SYSTEM SHALL 再次调用 `getSystemHealth()`，按钮 loading 期间禁用，并更新"最近刷新于"时间戳。
6. IF 请求失败，THEN THE SYSTEM SHALL 在页头显示 tone=`danger` 的 CkStatusPill，并保留诊断日志区的"暂无数据"提示。
7. THE SYSTEM SHALL 清洗文案，不出现"Java 编排入口健康检查"、"MySQL、PDF 解析、GraphRAG 输出和问答服务状态"、"GRAPHRAG 输出 / MinerU" 等原句；使用 `COPY.system.health.*` 作为唯一文案源。
8. THE SYSTEM SHALL 仅在 `auditor / admin` 下显示"刷新"按钮；教师/助教默认隐藏或置灰。
9. THE SYSTEM SHALL 让文件行数 ≤ 380 行。

### 3.5 FR-5 知识库验证页（KbValidationPage）

**用户故事**：作为教师，我希望在课程上线前不必进入构建向导也能对知识库做一次抽样问答，验证"这份知识库能否用教师的语气准确回答一个典型问题"。

**验收标准**：

1. WHEN 用户进入 `/app/qa-smoke`, THE SYSTEM SHALL 渲染独立页面 `KbValidationPage.vue`（路由 `path` 保持 `/app/qa-smoke`）；侧栏显示名改为"知识库验证"。
2. THE SYSTEM SHALL 在左侧表单区提供：知识库选择（`el-select filterable`）、问题输入（`el-input type="textarea"`, `maxlength=500` 且显示字数）、模式选择（`快速 / 课程内 / 跨课程`，对应 `basic / local / global`）、"发起验证"按钮。
3. WHILE `selectedKbId` 为空或 `question` 为空, THE SYSTEM SHALL 禁用"发起验证"按钮。
4. WHEN 用户点击"发起验证", THE SYSTEM SHALL:
   - 从所选 KB 读取 `activeIndexRunId` 和对应的 `buildRunId`;
   - 调用 `runBuildRunQaSmoke(buildRunId, { question, mode })`;
   - 进入 `runState = 'running'`, 右侧结果区渲染 `CkSplitProgress`（阶段来自 `COPY.validation.page.stages`）。
5. WHILE `runState = 'running'`, THE SYSTEM SHALL 使用 `createLongTaskController` 轮询 `getQaSession(sessionId)`，轮询间隔由 `useQaPolling.resolveQaPollingInterval` 决定，最长不超过 `resolveQaStaleTimeout` 返回的过期值。
6. WHEN 会话完成, THE SYSTEM SHALL 在结果区展示答复文本、耗时摘要（`检索 XX ms / 模型生成 XX ms`）、以及来自会话 trace 的日志流（使用 `CkLogStream`）。
7. IF 会话失败, THEN THE SYSTEM SHALL 显示失败原因的平实文案（经 `cleanTerms` 清洗），并提供"重新发起"按钮复用上一次表单参数。
8. THE SYSTEM SHALL 在页面底部展示"最近 10 次验证"（来源：`localStorage.ckqa.validation.history`，最多保存 20 条），每行展示知识库名、问题首行、状态、总耗时、发起时间。
9. WHERE 当前用户不具备 `qa:write` 权限, THE SYSTEM SHALL 隐藏"发起验证"按钮，仅保留"最近 10 次验证"只读视图。
10. THE SYSTEM SHALL 让 UI 不出现"冒烟 / smoke / embedding / 实体抽取 / P95 / MinerU" 等禁用术语，所有文案走 `COPY.validation.*`。
11. THE SYSTEM SHALL 让文件行数 ≤ 400 行。超过时抽出 `ValidationForm.vue / ValidationResult.vue` 两个子组件。

### 3.6 FR-6 RouteState 占位视觉刷新

**用户故事**：作为用户，我点开尚未开放的功能时，不希望看到一个裸的浅灰页面，而是看到与登录页同款的暖橙氛围 + 清晰的"后续开放"说明。

**验收标准**：

1. THE SYSTEM SHALL 为 `views/status/RouteState.vue` 顶部新增品牌图形占位（`<figure class="ck-route-state-illustration" />`），背景使用 `--ckqa-accent-soft / --ckqa-bg-elevated` 径向渐变，与 AuthLayout 登录页一致。
2. THE SYSTEM SHALL 把 `RouteState.vue` 的所有裸色值替换为 Token 引用，消除 `<style>` 中的 `#xxxxxx` 与 `rgb(...)` 直写。
3. THE SYSTEM SHALL 移除 `ckqa-el-button / ckqa-el-button--primary` 之类冗余类名，直接使用 `<el-button type="primary">`，让 M2 的主题映射接管样式。
4. THE SYSTEM SHALL 保留现有 `navGroupLabels / statusLabels / copy` 逻辑与其单元测试契约；不改任何 `<script setup>` 导出的常量。
5. WHERE 路由为 `/app/retrieval-logs` 列表占位, THE SYSTEM SHALL 把 `moduleLabel` 显示为"运维 · 检索日志"。

### 3.7 FR-7 占位路由视觉回归巡检

**用户故事**：作为 QA，我希望 M7 结束时，所有仍使用 `RouteState` 的路由在新视觉下都可用、无降级。

**验收标准**：

1. THE SYSTEM SHALL 保证下列路由仍以 `componentKey: 'RouteState'` 渲染，但视觉吃 FR-6 升级：
   - `/app/retrieval-logs`（列表占位，state=`coming-soon`）
   - `/app/retrieval-logs/:logId`
   - `/app/authorization-audit-logs`
   - `/app/users/:userId`
   - `/app/knowledge-bases/:kbId/index-runs`
2. THE SYSTEM SHALL 通过 Playwright 截图验证上述 5 条路由在亮/暗主题下无色值泄漏、无布局错位。

---

## 4. 跨页共性需求（CC）

### 4.1 CC-1 文案清洗与集中化

**验收标准**：

1. THE SYSTEM SHALL 在 `src/copy/admin.js` 中新增以下键空间：`COPY.system`、`COPY.users`、`COPY.roles`、`COPY.permissions`、`COPY.validation`、`COPY.routeState`，具体字段见设计稿第 8.1 节。
2. THE SYSTEM SHALL 在 `copy/admin.js` 中导出工具函数 `cleanTerms(text, map)` 与常量 `TERM_REPLACEMENT_MAP`，实现设计稿第 8.3 节规定的替换逻辑。
3. IF UI 可见的任意字符串叶子命中正则 `/冒烟|embedding|实体抽取|P95|MinerU/i`（除 `COPY.system.health.service.graphrag.name` 等明确保留项外），THEN THE SYSTEM SHALL 通过单元测试让 CI 红灯。
4. THE SYSTEM SHALL 清洗以下现有文件中的残留禁用术语：
   - `views/pages/module-page-model.js`（`OPERATION_FEEDBACK['qa-smoke']`）
   - `views/pages/module-content.js`（`configs['qa-sessions']` 的 `filters` 与 `secondaryAction.label`）
   - `views/system/HealthView.vue`（改名为 `HealthPage.vue` 时同步清洗）
   - `views/knowledge-bases/kb-build-copy.js`（若存在"冒烟"字样）
5. THE SYSTEM SHALL 保留以下专业表达：
   - `COPY.system.health.service.graphrag.name = 'GraphRAG 问答服务'`
   - `copy/brand.js` 中的品牌名
   - `api/client.js` 内部错误码描述（不在 UI 渲染）
   - E2E / 单元测试的 `data-testid`

### 4.2 CC-2 组合式函数收口

**验收标准**：

1. THE SYSTEM SHALL 新增 `src/composables/useQaPolling.js`，从 `src/views/pages/qa-polling.js` 迁移，保留导出函数签名不变：
   - `resolveQaPollingInterval({ status, elapsedMs, config? }): number`
   - `resolveQaStaleTimeout({ mode, kind }): number`
2. THE SYSTEM SHALL 把 `src/views/pages/ModulePage.vue` 中的 `import` 路径从 `'./long-task-state.js'` / `'./material-lifecycle-actions.js'` / `'./qa-polling.js'` 收口到 `'../../composables/useLongTaskState.js'` / `'../../composables/useMaterialLifecycle.js'` / `'../../composables/useQaPolling.js'`。
3. THE SYSTEM SHALL 同步更新 `src/app-shell.test.js` 中的 import 路径。
4. WHEN 全部 import 收口完成, THE SYSTEM SHALL 删除 `views/pages/qa-polling.js / long-task-state.js / material-lifecycle-actions.js`（如仍存在实体文件）。
5. THE SYSTEM SHALL 保留 `views/pages/module-loaders.js / module-content.js / module-page-model.js`，因其仍被 M3~M6 的独立页面消费。

### 4.3 CC-3 路由表改动

**验收标准**：

1. THE SYSTEM SHALL 在 `src/router/routes.js` 中把下列 `componentKey` 从 `ModulePage` / `HealthView` / `RouteState` 换成新组件：
   - `users → UserListPage`
   - `roles → RoleListPage`
   - `permissions → PermissionListPage`
   - `health → HealthPage`
   - `qa-smoke → KbValidationPage`（`meta.status: 'upcoming' → 'mvp'`, 移除 `routeState: 'coming-soon'`）
2. THE SYSTEM SHALL 在 `src/router/index.js` 的组件字典中新增上述 5 个 import，同时保留 `ModulePage` import 以便兜底。
3. THE SYSTEM SHALL 保持所有路由 `path / name / meta.permissions / meta.navGroup / meta.section` 不变。
4. THE SYSTEM SHALL 通过"path 快照"单元测试保证 `APP_ROUTES.map(r => r.path)` 与 M7 起点分支对应快照完全一致。

### 4.4 CC-4 暗色主题与可访问性

**验收标准**：

1. WHILE 根 `<html data-theme='dark'>`, THE SYSTEM SHALL 让 M7 新/改组件在 `axe-core` 扫描下 0 `serious/critical` / 0 `color-contrast` 违规。
2. THE SYSTEM SHALL 在 Playwright 视觉快照（`@pw-visual`）中新增 UserList / RoleList / PermissionList / HealthPage / KbValidationPage × `light/dark` = 10 张基准图，允许误差 0.3%。
3. THE SYSTEM SHALL 让所有 `<el-table>` 具备 `aria-label`；所有可聚焦元素显示 `--ckqa-shadow-focus`；表单错误与字段通过 `aria-describedby` 关联。
4. THE SYSTEM SHALL 让状态列使用 `CkStatusPill` 而非裸 `el-tag`，确保主题与 Token 对齐。

### 4.5 CC-5 测试与 CI

**验收标准**：

1. THE SYSTEM SHALL 为每个新/改组件提供 `*.test.js`（详见设计稿第 10.1 节）。
2. THE SYSTEM SHALL 新增 `src/copy/admin.test.js`，至少覆盖设计稿第 13.2 / 13.3 节的属性测试。
3. THE SYSTEM SHALL 新增 3 条 Playwright 用例：`m7-users / m7-health / m7-validation`，全部使用 `data-testid` 选择器。
4. WHEN `pnpm test` 或 `pnpm test:e2e` 执行, THE SYSTEM SHALL 让 M7 改动不导致既有 M1~M6 用例退化。

### 4.6 CC-6 灰度与回退

**验收标准**：

1. THE SYSTEM SHALL 让 M7 按页面粒度可独立发 PR（UserList / RoleList / PermissionList 可独立，HealthPage 独立，KbValidationPage 独立，RouteState 独立）。
2. IF 某页面合并后发现线上问题, THEN THE SYSTEM SHALL 支持"改回 `componentKey: 'ModulePage'` 或 `'RouteState'`"作为即时回滚手段，无需数据迁移。
3. THE SYSTEM SHALL 在 `app-shell.test.js` 保留一条"ModulePage 可编译"的冒烟测试，防止其被误删。

---

## 5. 非功能需求（NFR）

| 编号 | 维度 | 要求 |
| --- | --- | --- |
| NFR-1 | 代码规模 | 每个页面组件 ≤ 设计稿给定上限（UserList 280 / Role 240 / Permission 220 / Health 380 / Validation 400）；子组件 ≤ 300 行；composable ≤ 200 行 |
| NFR-2 | 响应性能 | 列表页首屏在 `admin-app` mock 数据下 ≤ 150ms 完成 `listXxx` 请求处理 + 骨架 → 首屏渲染；E2E 运行环境允许放宽至 ≤ 500ms |
| NFR-3 | 可达性 | WCAG 2.1 AA：文本对比度 ≥ 4.5:1，交互态 ≥ 3:1；键盘可遍历所有主流程 |
| NFR-4 | 可维护性 | 所有颜色走 Token；`stylelint` 禁止 hex / rgb 直写（例外：`styles/element-plus.scss / styles/tokens/**`） |
| NFR-5 | 兼容性 | 桌面端 ≥ 1280px 基线；1024px ≤ 宽度 < 1280px 退化保底 |
| NFR-6 | 可观测 | 验证页历史仅保存在 `localStorage`，不引入新埋点；关键错误走 `api/client.js` 现有日志通道 |
| NFR-7 | 安全 | M7 不新增对外暴露的接口调用，不写入任何后端状态（除 `runBuildRunQaSmoke` 正常 QA 通道） |
| NFR-8 | 无障碍 | 所有状态徽章使用 `CkStatusPill`，颜色 + 文字双通道传达状态，避免仅靠颜色 |

---

## 6. 正确性属性（用于 PBT）

M7 是 UI 结构迁移，业务逻辑变更极少，属性测试聚焦"迁移一致性"：

| 编号 | 属性 | 说明 | 测试文件 |
| --- | --- | --- | --- |
| P1 | 路由表一致性 | M7 结束后 `APP_ROUTES` 中不存在 `componentKey === 'ModulePage'` 的条目；所有 `componentKey` 都能在组件字典解析出真实组件 | `app-shell.test.js` |
| P2 | 文案禁用术语 | `COPY` 对象树任意字符串叶子不包含禁用术语（除明确白名单） | `copy/admin.test.js` |
| P3 | cleanTerms 幂等 | 对任意输入串，`cleanTerms(cleanTerms(x)) === cleanTerms(x)`；且输出不再包含任一禁用词 | `copy/admin.test.js` |
| P4 | useQaPolling 行为等价 | 新 `useQaPolling.js` 的 `resolveQaPollingInterval / resolveQaStaleTimeout` 对随机合法输入与旧 `views/pages/qa-polling.js` 返回值完全相等（迁移 PR 合并后删除该对比测试） | `composables/useQaPolling.test.js` |
| P5 | 路由 path 不变 | `APP_ROUTES.map(r => r.path).sort()` 与 M7 起点快照完全相等 | `app-shell.test.js` |
| P6 | HealthPage 状态聚合 | `overallTone(services)` 满足：存在不可达 ⇒ `danger`；均可达但存在未就绪 ⇒ `warning`；全就绪 ⇒ `success`；空数组 ⇒ `blocked` | `composables/useHealthStatus.test.js` |

---

## 7. 验收清单（Definition of Done）

M7 里程碑完成当且仅当以下条件全部满足：

1. ✅ `/app/users`、`/app/roles`、`/app/permissions`、`/app/health`、`/app/qa-smoke` 五条路由 `componentKey` 不再是 `ModulePage / HealthView / RouteState`（`qa-smoke` 除外的意图性替换）。
2. ✅ `ModulePage.vue` 不被任何 `APP_ROUTES` 条目直接引用（通过 P1 属性测试）。
3. ✅ 新增页面 `UserListPage / RoleListPage / PermissionListPage / HealthPage / KbValidationPage` 均可单独加载、分页/搜索可用、错误可见。
4. ✅ `KbValidationPage` 可以完整跑通一次验证流程（发起 → 轮询 → 展示答复 / 失败原因）。
5. ✅ `RouteState.vue` 视觉吃 Token，亮/暗主题下无色值泄漏。
6. ✅ `src/copy/admin.js` 包含 M7 键空间 + `cleanTerms / TERM_REPLACEMENT_MAP`；P2/P3 属性测试跑绿。
7. ✅ `composables/useQaPolling.js` 落地，`views/pages/qa-polling.js / long-task-state.js / material-lifecycle-actions.js` 若为独立文件则已删除；相关 import 路径收口。
8. ✅ Playwright 新增 3 条 E2E 用例（`m7-users / m7-health / m7-validation`）跑绿。
9. ✅ Playwright 视觉快照 10 张基准图首次建立并跑绿。
10. ✅ `pnpm test` + `pnpm test:e2e` + `pnpm build` 全部跑绿。
11. ✅ `stylelint` 无 `color-no-hex` 违规（例外路径除外）。
12. ✅ M7 PR 描述中附有灰度/回退说明，明确列出每个页面可回退到的原 `componentKey`。

---

## 8. 开放问题（Open Points）

| 编号 | 问题 | 影响 | 负责人 | 预期决策时间 |
| --- | --- | --- | --- | --- |
| OP-1 | `/api/v1/kb-build-runs/:id/qa-smoke` 是否允许前端脱离构建向导独立调用（而非构建完成立即跟随触发） | KbValidationPage 是否可直接复用该端点，还是需要后端新增"按 KB 触发验证"接口 | 后端 owner | M7 启动前 1 天 |
| OP-2 | `/api/v1/roles` 与 `/api/v1/permissions` 是否已就绪 | RoleListPage / PermissionListPage 是否需要短期兜底策略 | 后端 owner | M7 启动前 1 天 |
| OP-3 | `auditor` 角色在当前 `useAuthStore` 里的权限粒度 | 影响 HealthPage 刷新按钮、UserList 可见性等守卫判断 | 前端 / 权限 owner | M7 启动前 2 天 |
| OP-4 | `ModulePage.vue` 的物理删除时机 | 决定 M7 之后的清理 PR 发起窗口 | 前端 owner | M7 合并后 + 1 轮线上稳定期 |

---

## 9. 变更记录

| 日期 | 作者 | 变更 |
| --- | --- | --- |
| 2026-05-10 | Kiro（design-first workflow） | 初版：基于 `./design.md` 反向整理出 7 条功能需求 + 6 条共性需求 + 8 条 NFR + 6 条 PBT 属性 |
