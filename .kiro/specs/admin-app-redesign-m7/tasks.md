# 实施任务：管理员端 M7 —— 其他页面拆分与适配

- 上位设计：`docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md`
- 本 spec 的 `design.md` 与 `requirements.md` 是任务拆解的 SoT；任务引用的编号与两份文档保持一致
- 目标 worktree：`/home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2/`
- 所有 `pnpm` 命令从 `frontend/apps/admin-app/` 目录执行
- 文档语言：中文

## Overview

本任务表把 M7 拆成 7 个粗颗粒阶段（与 `design.md` 第 14 节一一对应），按"骨架与 composable 迁移 → 文案清洗 → 列表三件套 → HealthPage → KbValidationPage → RouteState 视觉刷新 → 巡检与验收"的顺序串行推进。每个页面对应一个可独立合入的子阶段，任务颗粒度按"能在 0.5~4 小时内独立交付并跑通 `pnpm test`"控制，超过即拆子任务；测试任务紧贴实现任务、不集中到最后。

> PR 映射（与 `design.md` 第 3.7 节灰度策略对齐）：阶段 1 一个 PR、阶段 2 一个 PR、阶段 3 可拆 3 个 PR（UserList / RoleList / PermissionList）、阶段 4 一个 PR、阶段 5 一个 PR、阶段 6 一个 PR、阶段 7 一个 PR（巡检收尾）。

## 快速对照：Definition of Done（12 条）

来源：`requirements.md` 第 7 节。M7 合并前必须逐条勾选。

1. `/app/users` / `/app/roles` / `/app/permissions` / `/app/health` / `/app/qa-smoke` 五条路由的 `componentKey` 不再是 `ModulePage / HealthView / RouteState`。
2. `ModulePage.vue` 不被任何 `APP_ROUTES` 条目直接引用（P1 属性测试跑绿）。
3. `UserListPage / RoleListPage / PermissionListPage / HealthPage / KbValidationPage` 五个新页面可单独加载、分页/搜索可用、错误可见。
4. `KbValidationPage` 可以完整跑通一次验证流程（发起 → 轮询 → 展示答复 / 失败原因）。
5. `RouteState.vue` 视觉吃 Token，亮/暗主题下无色值泄漏。
6. `src/copy/admin.js` 包含 M7 键空间 + `cleanTerms / TERM_REPLACEMENT_MAP`；P2 / P3 属性测试跑绿。
7. `composables/useQaPolling.js` 落地；`views/pages/qa-polling.js / long-task-state.js / material-lifecycle-actions.js` 若仍为独立文件则已删除；相关 import 路径收口。
8. Playwright 新增 3 条 E2E 用例（`m7-users / m7-health / m7-validation`）跑绿。
9. Playwright 视觉快照 10 张基准图（5 页 × 亮/暗主题）首次建立并跑绿。
10. `pnpm test` + `pnpm test:e2e` + `pnpm build` 全部跑绿。
11. `stylelint` 无 `color-no-hex` 违规（`styles/element-plus.scss / styles/tokens/**` 等例外路径除外）。
12. M7 PR 描述中附有灰度/回退说明，明确列出每个页面可回退到的原 `componentKey`。

---

## Tasks

- [x] 1. 阶段 1：骨架与 composable 迁移
  - [x] 1.1 迁移 `qa-polling` 到 `composables/useQaPolling.js`
    - 用 smartRelocate 工具把 `frontend/apps/admin-app/src/views/pages/qa-polling.js` 重命名为 `frontend/apps/admin-app/src/composables/useQaPolling.js`；若工具无法跨目录移动则 `git mv` 后手工修正 import 引用者。保留导出 `resolveQaPollingInterval / resolveQaStaleTimeout` 的签名与语义不变。
    - 同步迁移同名单元测试到 `frontend/apps/admin-app/src/composables/useQaPolling.test.js`；删除旧测试文件。
    - 落点文件：`src/composables/useQaPolling.js`、`src/composables/useQaPolling.test.js`
    - 验证方式：`pnpm test --run src/composables/useQaPolling.test.js`
    - 对应需求：[CC-2, design §6.1, design §15]

  - [x] 1.2 为迁移期加一条 P4 等价性属性测试
    - 在 `src/composables/useQaPolling.test.js` 内临时新增一段 `fast-check` 属性测试：生成随机合法 `{ status, elapsedMs, mode, kind }`，断言新 `resolveQaPollingInterval / resolveQaStaleTimeout` 与仍保留的旧文件导出（如 1.4 尚未删除）返回完全一致。阶段 1 合入并跑绿后，**在同一 PR 内**删除这段对比测试与残留旧文件。
    - 落点文件：`src/composables/useQaPolling.test.js`
    - 验证方式：`pnpm test --run src/composables/useQaPolling.test.js`
    - 对应需求：[P4, CC-2, design §13.3]

  - [x] 1.3 收口 `long-task-state / material-lifecycle-actions / qa-polling` 的 import 路径
    - 修改 `frontend/apps/admin-app/src/views/pages/ModulePage.vue`：把 `'./long-task-state.js'` 改为 `'../../composables/useLongTaskState.js'`；`'./material-lifecycle-actions.js'` 改为 `'../../composables/useMaterialLifecycle.js'`；`'./qa-polling.js'` 改为 `'../../composables/useQaPolling.js'`。
    - 修改 `frontend/apps/admin-app/src/app-shell.test.js`：同步 import 路径。
    - 落点文件：`src/views/pages/ModulePage.vue`、`src/app-shell.test.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js` + `pnpm build`
    - 对应需求：[CC-2, CC-6, design §2.2, design §6.2]

  - [x] 1.4 删除 `views/pages/` 下的旧独立文件
    - 确认 `src/views/pages/qa-polling.js / long-task-state.js / material-lifecycle-actions.js` 在 1.1 / 1.3 完成后已无任何 import 引用后，物理删除这三个文件。保留 `module-loaders.js / module-content.js / module-page-model.js`（它们仍被 M3~M6 拆出的页面消费）。
    - 落点文件：以上三个旧文件（删除）
    - 验证方式：`pnpm test --run src/app-shell.test.js` + `pnpm build`；用 grep 确认 `qa-polling` / `long-task-state` / `material-lifecycle-actions` 在 `src/` 内零引用。
    - 对应需求：[CC-2, design §6.1, design §6.2, design §15]

  - [x] 1.5 建立 P1（路由表一致性）与 P5（path 不变快照）的属性测试骨架
    - 在 `src/app-shell.test.js` 新增 P1 测试：`fast-check` 从 `APP_ROUTES` 任取一条，断言 `route.componentKey !== 'ModulePage'` 且 `components[route.componentKey]` 能被解析到 Vue 组件构造器（本阶段允许 `users / roles / permissions / health / qa-smoke` 五条暂时处于"旧 componentKey"状态，故此处 P1 断言先放宽为 `白名单 + 其他不得为 ModulePage`，待阶段 3~5 完成后收紧为完全禁止）。
    - 在 `src/app-shell.test.js` 新增 P5 测试：把当前 `APP_ROUTES.map(r => r.path).sort()` 作为 inline snapshot（或 `__snapshots__/`），阶段 3~6 改动后必须保持该快照不变。
    - 落点文件：`src/app-shell.test.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js`
    - 对应需求：[P1, P5, CC-3, CC-6, design §13.1, design §13.4]

  - [x] 1.6 阶段 1 验收
    - 跑 `pnpm test` + `pnpm build`；确认：`useQaPolling` 迁移完成、对比测试已删除、三份旧文件已删除、`ModulePage.vue` 与 `app-shell.test.js` 的 import 路径已收口、P1/P5 骨架测试跑绿。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[CC-2, CC-6, P1, P4, P5]

- [x] 2. 阶段 2：文案清洗
  - [x] 2.1 在 `copy/admin.js` 新增 M7 键空间
    - 按 `design.md` 第 8.1 节，向 `COPY` 下增补 `system / users / roles / permissions / validation / routeState` 六个子键；保持 `COPY.feedback.kbValidationLabel / COPY.status.*` 原值不变。
    - 落点文件：`src/copy/admin.js`
    - 验证方式：`pnpm test --run src/copy/` + `pnpm build`
    - 对应需求：[CC-1, design §8.1]

  - [x] 2.2 在 `copy/admin.js` 导出 `cleanTerms` 与 `TERM_REPLACEMENT_MAP`
    - 实现 `design.md` 第 8.3 节的纯函数 `cleanTerms(text, map)`（大小写不敏感、幂等、对非字符串输入返回空串）与常量 `TERM_REPLACEMENT_MAP`（含 `冒烟验证 / 冒烟 / smoke / embedding(s)? / 嵌入 / 实体抽取 / P95 延迟 / MinerU` 等条目）。
    - 落点文件：`src/copy/admin.js`
    - 验证方式：`pnpm test --run src/copy/admin.test.js`（测试见 2.3）
    - 对应需求：[CC-1, design §8.2, design §8.3]

  - [x] 2.3 新建 `copy/admin.test.js`，覆盖 P2 与 P3 两条属性测试
    - P2：用 `fast-check` 遍历 `COPY` 的所有字符串叶子，断言不命中 `/冒烟|embedding|实体抽取|P95|MinerU/i`（显式白名单：`COPY.system.health.service.graphrag.name = 'GraphRAG 问答服务'` 等项以数组形式允许放行）。
    - P3：`fc.property(string, constantFrom(...termKeys))` 对任意 `noise + term + noise` 输入断言 `cleanTerms(cleanTerms(x)) === cleanTerms(x)`，且输出不再命中该禁用词。
    - 同时增补若干具体用例：`cleanTerms('冒烟验证已提交', MAP) === '知识库验证已提交'`、`cleanTerms('MinerU 超时', MAP) === 'PDF 解析服务 超时'`。
    - 落点文件：`src/copy/admin.test.js`
    - 验证方式：`pnpm test --run src/copy/admin.test.js`
    - 对应需求：[P2, P3, CC-1, CC-5, design §13.2]

  - [x] 2.4 清洗既有文件中的残留禁用术语
    - 修改 `src/views/pages/module-page-model.js` 中 `OPERATION_FEEDBACK['qa-smoke']` 的 `titles.*` 与 `resolveOperationMessage` 相关串，统一指向 `COPY.validation.*`。
    - 修改 `src/views/pages/module-content.js` 中 `configs['qa-sessions']` 的 `filters.options`（`'冒烟验证' → '知识库验证'`）与 `secondaryAction.label`。
    - 若 `src/views/knowledge-bases/kb-build-copy.js` 存在"冒烟"字样，同步替换为"知识库验证"。
    - 落点文件：`src/views/pages/module-page-model.js`、`src/views/pages/module-content.js`、按需 `src/views/knowledge-bases/kb-build-copy.js`
    - 验证方式：`pnpm test --run src/copy/admin.test.js` + `pnpm test`（保证既有 M3~M6 单元测试不退化）
    - 对应需求：[CC-1, design §8.2]

  - [x] 2.5 阶段 2 验收
    - 跑 `pnpm test` + `pnpm build`；确认：`COPY` 六个 M7 键齐备、`cleanTerms` 幂等、P2/P3 属性测试跑绿、`module-page-model.js / module-content.js` 残留术语清零。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[CC-1, P2, P3, DoD-6]

- [x] 3. 阶段 3：UserList / RoleList / PermissionList
  - [x] 3.1 新建 `views/users/user-page-copy.js`
    - 定义该页专用文案常量（引用 `COPY.users.*`）+ `statusLabel(status)` 函数（用 `COPY.users.status[active|inactive|locked]` 映射，未知态兜底为原值）+ 表格列的显示名常量。不含业务逻辑。
    - 落点文件：`src/views/users/user-page-copy.js`
    - 验证方式：`pnpm test --run src/views/users/`（本任务自身无测试，靠 3.2/3.3 覆盖）
    - 对应需求：[CC-1, FR-1.2, design §7 "users/roles/permissions create/edit/delete"]

  - [x] 3.2 新建 `composables/useUserListPage.js` + 单元测试
    - 按 `design.md` 第 6.4 节签名实现：`load / refresh / setPage / setPageSize / setKeyword`；内部通过 `createStaleRequestGuard`（来自 `views/pages/module-page-model.js`）处理并发；从 `route.query` 读 `page / size / keyword` 并通过 `router.replace` 回写。对 `listUsers` 注入式可替换（测试 mock）。
    - 单元测试 `useUserListPage.test.js`：覆盖 ① 首次加载以 `page=1 / size=20` 调用 service；② `setPage / setPageSize / setKeyword` 触发重新请求并同步 `route.query`；③ 空结果进入 `state = 'empty'`；④ 服务抛错进入 `state = 'error'` 并保留 `error.message`；⑤ 并发请求时只保留最新一次的结果。
    - 落点文件：`src/composables/useUserListPage.js`、`src/composables/useUserListPage.test.js`
    - 验证方式：`pnpm test --run src/composables/useUserListPage.test.js`
    - 对应需求：[FR-1, CC-5, design §6.3, design §6.4]

  - [x] 3.3 新建 `views/users/UserListPage.vue` + 组件测试
    - 按 `design.md` 第 5.1 节骨架实现：`CkPageHero` 页头 + `<el-table>`（`data-testid="user-table"`, `aria-label="用户列表"`）+ `CkStatusPill` 状态列 + `CkPager` 分页 + `CkSkeleton` 加载态 + `CkEmptyState` 空态 + 错误面板。文件控制在 280 行内；超出时抽 `user-list-columns.js`。写操作按钮（新建/切换启用）`v-if="authStore.canAccess(['user:write'])"` 守护；第一版 `disabled` 并带 tooltip："后续里程碑开放"。
    - 组件测试 `UserListPage.test.js`：mount 后注入成功/空态/错误三种 service snapshot，断言 `<el-table>` 行数、`CkStatusPill` 出现、`CkPager` 派发事件能触发 composable。
    - 落点文件：`src/views/users/UserListPage.vue`、`src/views/users/UserListPage.test.js`
    - 验证方式：`pnpm test --run src/views/users/UserListPage.test.js`
    - 对应需求：[FR-1, CC-4, CC-5, NFR-1, NFR-8, design §5.1]

  - [x] 3.4 同构做 `RoleListPage`（copy + composable + 组件 + 测试）
    - `src/views/users/role-page-copy.js` + `src/composables/useRoleListPage.js`（签名同 UserList，service 走 `listRoles`）+ `src/composables/useRoleListPage.test.js` + `src/views/users/RoleListPage.vue`（≤ 240 行，列：角色编码 / 名称 / 状态 / 权限范围（超 3 项"等 N 项"）/ 更新时间 / 操作）+ `src/views/users/RoleListPage.test.js`。写操作按钮 `v-if="authStore.canAccess(['role:write'])"` 守护且 M7 内 `disabled`。
    - 落点文件：`src/views/users/role-page-copy.js`、`src/composables/useRoleListPage.js`、`src/composables/useRoleListPage.test.js`、`src/views/users/RoleListPage.vue`、`src/views/users/RoleListPage.test.js`
    - 验证方式：`pnpm test --run src/composables/useRoleListPage.test.js` + `pnpm test --run src/views/users/RoleListPage.test.js`
    - 对应需求：[FR-2, CC-5, NFR-1, design §5.2]

  - [x] 3.5 同构做 `PermissionListPage`（copy + composable + 组件 + 测试，含资源筛选）
    - `src/views/users/permission-page-copy.js`（含 `resource` 维度的可选项：`course / material / kb / qa / user / role / permission / system`）+ `src/composables/usePermissionListPage.js`（签名扩展 `resource` 参数写回 `route.query.resource`）+ `src/composables/usePermissionListPage.test.js` + `src/views/users/PermissionListPage.vue`（≤ 220 行，页头放 `el-select` 资源筛选）+ `src/views/users/PermissionListPage.test.js`（覆盖"切换 resource 触发重新请求且同步 query"）。
    - 落点文件：`src/views/users/permission-page-copy.js`、`src/composables/usePermissionListPage.js`、`src/composables/usePermissionListPage.test.js`、`src/views/users/PermissionListPage.vue`、`src/views/users/PermissionListPage.test.js`
    - 验证方式：`pnpm test --run src/composables/usePermissionListPage.test.js` + `pnpm test --run src/views/users/PermissionListPage.test.js`
    - 对应需求：[FR-3, CC-5, NFR-1, design §5.3]

  - [x] 3.6 在 `router/routes.js` 与 `router/index.js` 完成 `users / roles / permissions` 的 `componentKey` 替换
    - `router/routes.js`：把 `users / roles / permissions` 三条路由的 `componentKey` 从 `'ModulePage'` 改为 `'UserListPage' / 'RoleListPage' / 'PermissionListPage'`；保留 `path / name / meta.permissions / meta.navGroup / meta.section` 不变。
    - `router/index.js`：在组件字典内 `import` 三个新页面并加入 `components` 对象；`ModulePage` import 保留作兜底。
    - 落点文件：`src/router/routes.js`、`src/router/index.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js`（P5 快照不变，P1 断言 `users/roles/permissions` 不再是 `ModulePage`）
    - 对应需求：[CC-3, P1, P5, design §9]

  - [x] 3.7 后端接口未就绪时的兜底策略（用户视图聚合）
    - 在 `src/api/users.js` 或新建 `src/api/roles.js / src/api/permissions.js` 中：`listRoles / listPermissions` 优先调用 `/api/v1/roles` / `/api/v1/permissions`；若 HTTP 404 / 501 / `code = 40401` 则回退到"从用户列表中聚合 `user.roles[] / role.permissions[]` 唯一化 + 前端分页"。
    - 在 `useRoleListPage / usePermissionListPage` 上暴露 `dataSourceHint`（`'api' | 'aggregated'`）；对应页面在 `CkPageHero` 的 `actions` 位渲染一枚 `CkStatusPill tone="warning" label="数据来自用户视图聚合"`（仅 `aggregated` 模式下显示）。
    - 在 `useRoleListPage.test.js / usePermissionListPage.test.js` 中新增"主接口 404 时自动降级"的用例。
    - 落点文件：`src/api/roles.js`（或 `src/api/users.js`）、`src/api/permissions.js`（或 `src/api/users.js`）、`src/composables/useRoleListPage.js`、`src/composables/usePermissionListPage.js`、对应测试、`src/views/users/RoleListPage.vue`、`src/views/users/PermissionListPage.vue`
    - 验证方式：`pnpm test --run src/composables/useRoleListPage.test.js src/composables/usePermissionListPage.test.js`
    - 对应需求：[FR-2, FR-3, OP-2, requirements §1.4, design §16]

  - [x] 3.8 阶段 3 验收
    - 跑 `pnpm test`；用浏览器 `pnpm dev` 目测 `/app/users` / `/app/roles` / `/app/permissions` 在亮/暗主题下可加载、分页/搜索可用、空态/错误态正常。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[FR-1, FR-2, FR-3, CC-3, DoD-1, DoD-3]

- [x] 4. 阶段 4：HealthPage
  - [x] 4.1 用 smartRelocate 工具把 `HealthView.vue` 重命名为 `HealthPage.vue`
    - 用 smartRelocate 工具重命名文件，保留 git 历史并自动修正 import 引用者（含 `router/index.js` 与潜在测试文件）。保留 `normalizeHealthResponse` 等模块导出接口不变。
    - 落点文件：`src/views/system/HealthView.vue` → `src/views/system/HealthPage.vue`
    - 验证方式：`pnpm build`（确保无未修正的引用）
    - 对应需求：[FR-4, CC-3, CC-6, design §5.4, design §15]

  - [x] 4.2 新建 `composables/useHealthStatus.js` + 单元测试（含 P6 属性测试）
    - 按 `design.md` 第 6.5 节签名实现：`state / overallTone / overallLabel / services / diagnostics / refreshedAt / error / loadHealth`。内部复用既有 `api/system.js:getSystemHealth`；`diagnostics[]` 每行调 `cleanTerms(service.message, TERM_REPLACEMENT_MAP)` 清洗。
    - `useHealthStatus.test.js` 覆盖：① 加载中/成功/失败三态；② `diagnostics` 行拼装格式 `"{displayName}：{reachLabel} / {readyLabel}[ {清洗 message}]"`；③ 服务 message 中的 `MinerU / embedding` 等术语被清洗。
    - P6 属性测试：`fc.array(fc.record({ reachable: fc.boolean(), ready: fc.boolean() }))` 断言
      - 存在 `!reachable` → `overallTone = 'danger'`
      - 全 `reachable` 且存在 `!ready` → `'warning'`
      - 全 `reachable && ready` → `'success'`
      - 空数组 → `'blocked'`
    - 落点文件：`src/composables/useHealthStatus.js`、`src/composables/useHealthStatus.test.js`
    - 验证方式：`pnpm test --run src/composables/useHealthStatus.test.js`
    - 对应需求：[FR-4, CC-5, P6, design §13.5, design §6.5]

  - [x] 4.3 新建 `views/system/health-page-copy.js`
    - 从 `COPY.system.health.*` 派生该页用到的 eyebrow / title / subtitle / diagnosticsTitle / overall 标签 / service 名称映射；不定义新的裸字符串。
    - 落点文件：`src/views/system/health-page-copy.js`
    - 验证方式：`pnpm build`
    - 对应需求：[CC-1, design §8.1]

  - [x] 4.4 重写 `HealthPage.vue` 的模板与样式
    - 按 `design.md` 第 5.4 节实现：`CkPageHero`（`actions` 位放聚合状态 `CkStatusPill` + "刷新"按钮，仅 `auditor / admin` 下可见）+ 服务卡片网格（每卡 `CkStatusPill` + `CkInfoTable`）+ `CkLogStream` 诊断日志。全部颜色走 M1 Token，移除 `"Java 编排入口健康检查"` / `"MySQL、PDF 解析、GraphRAG 输出和问答服务状态"` / `"GRAPHRAG 输出 / MinerU"` 等裸串与裸色值。文件 ≤ 380 行。
    - 加 `UI 组件测试`：`HealthPage.test.js` 覆盖"加载 → 成功 → 刷新按钮派发 loadHealth"主流程。
    - 落点文件：`src/views/system/HealthPage.vue`、`src/views/system/HealthPage.test.js`
    - 验证方式：`pnpm test --run src/views/system/HealthPage.test.js`
    - 对应需求：[FR-4, CC-1, CC-4, NFR-1, NFR-8, design §5.4]

  - [x] 4.5 路由替换 + `app-shell.test.js` 同步
    - `router/routes.js`：`health` 路由 `componentKey` 从 `'HealthView'` 改为 `'HealthPage'`。
    - `router/index.js`：组件字典把 `HealthView` 替换为 `HealthPage`（保留 import 名一致性）。
    - `app-shell.test.js`：同步任何 `HealthView` 引用；P1 断言收紧（`health` 不再是 `HealthView`）。
    - 落点文件：`src/router/routes.js`、`src/router/index.js`、`src/app-shell.test.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js` + `pnpm build`
    - 对应需求：[CC-3, CC-6, P1, P5, design §9]

  - [x] 4.6 阶段 4 验收
    - 跑 `pnpm test` + `pnpm build`；`pnpm dev` 手动进入 `/app/health`，亮/暗主题下均无色值泄漏，诊断日志不含禁用术语，刷新按钮可用；P6 属性测试跑绿。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[FR-4, CC-1, CC-4, P6, DoD-1, DoD-3]

- [x] 5. 阶段 5：KbValidationPage
  - [x] 5.1 新建目录 `views/operations/` 与 `kb-validation-copy.js`
    - 创建 `src/views/operations/` 目录；新建 `kb-validation-copy.js` 从 `COPY.validation.*` 派生页面用到的文案（eyebrow / title / subtitle / form.* / result.* / history.title / mode 映射 / stateLabel 函数）；定义阶段常量 `STAGES = [{ key, label }, ...]` 引用 `COPY.validation.page.stages`。
    - 落点文件：`src/views/operations/kb-validation-copy.js`
    - 验证方式：`pnpm build`
    - 对应需求：[CC-1, design §8.1, design §5.5]

  - [x] 5.2 新建 `composables/useKbValidationRun.js` + 单元测试
    - 按 `design.md` 第 6.6 节签名实现：`knowledgeBases / selectedKbId / selectedIndexRunId / question / mode / runState / runSnapshot / history / start / reset`。内部复用 `createLongTaskController`（from `useLongTaskState`）+ `resolveQaPollingInterval / resolveQaStaleTimeout`（from `useQaPolling`），`trigger` 调 `runBuildRunQaSmoke(buildRunId, ...)`，`poll` 调 `getQaSession(sessionId)`。历史写入 `localStorage.ckqa.validation.history`（上限 20 条，显示最近 10 条）。
    - `useKbValidationRun.test.js`：覆盖 ① 发起后立即进入 `running`；② 轮询到 `succeeded` 后 `runSnapshot` 含 `answer / sources / timings`；③ 失败态含 `errorMessage` 并可通过 `reset + start` 重发；④ 历史持久化到 `localStorage` 且上限 20 条、倒序显示；⑤ `question/kb` 为空时 `start()` 拒绝提交。
    - 落点文件：`src/composables/useKbValidationRun.js`、`src/composables/useKbValidationRun.test.js`
    - 验证方式：`pnpm test --run src/composables/useKbValidationRun.test.js`
    - 对应需求：[FR-5, CC-2, CC-5, design §6.6, design §4.2]

  - [x] 5.3 新建 `views/operations/KbValidationPage.vue` + 组件测试
    - 按 `design.md` 第 5.5 节实现：`CkPageHero` + 左侧表单区（KB 选择 / 问题输入 / 模式 radio / 发起按钮，写权限守护 `v-if="authStore.canAccess(['qa:write'])"`）+ 右侧结果区（`CkEmptyState` / `CkSplitProgress` / 答复文本 + 耗时 + `CkLogStream` / 错误面板 + 重新发起按钮）+ 底部历史表（近 10 条）。文件 ≤ 400 行；超出时抽 `ValidationForm.vue / ValidationResult.vue` 两个子组件。
    - `KbValidationPage.test.js`：覆盖 ① 首次加载渲染 `CkEmptyState`；② 发起验证进入 `running` 并展示阶段进度；③ 成功态展示答复、耗时与日志；④ 失败态展示平实错误文案且含"重新发起"按钮；⑤ 历史表 mock 数据下正确渲染 10 行。
    - 落点文件：`src/views/operations/KbValidationPage.vue`（必要时 `src/views/operations/ValidationForm.vue` / `ValidationResult.vue`）、`src/views/operations/KbValidationPage.test.js`
    - 验证方式：`pnpm test --run src/views/operations/KbValidationPage.test.js`
    - 对应需求：[FR-5, CC-4, CC-5, NFR-1, NFR-8, design §5.5]

  - [x] 5.4 路由替换（`qa-smoke` → `KbValidationPage`）
    - `router/routes.js`：`qa-smoke` 路由 `componentKey` 从 `'RouteState'` 改为 `'KbValidationPage'`；`meta.status` 从 `'upcoming'` 改为 `'mvp'`；移除 `meta.routeState: 'coming-soon'` 与 `props`；新增 `meta.keepAlive: true`；保留 `path = '/app/qa-smoke'` 和 `meta.title = '知识库验证'`。
    - `router/index.js`：组件字典新增 `KbValidationPage` import 与 key。
    - `app-shell.test.js`：P1 断言收紧（`qa-smoke` 不再是 `RouteState` 也不是 `ModulePage`）。
    - 落点文件：`src/router/routes.js`、`src/router/index.js`、`src/app-shell.test.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js` + `pnpm build`
    - 对应需求：[CC-3, P1, P5, design §9]

  - [x] 5.5 OP-1 决策记录与 composable 层透明切换
    - 在本 spec 目录下新建 `.kiro/specs/admin-app-redesign-m7/decisions/OP-1-validation-entrypoint.md`（短文 ≤ 1 页）记录后端给出的最终答案：① 继续复用 `runBuildRunQaSmoke(buildRunId)`；或 ② 后端提供"按 KB 直接触发验证"的新接口。
    - 若最终走 ①：`useKbValidationRun.start` 内部先读所选 KB 的 `activeIndexRunId / buildRunId`，再调 `runBuildRunQaSmoke`；若走 ②：直接调新接口。两种实现要通过同一 composable 对外签名保持 UI 层零感知。
    - 落点文件：`.kiro/specs/admin-app-redesign-m7/decisions/OP-1-validation-entrypoint.md`、`src/composables/useKbValidationRun.js`
    - 验证方式：`pnpm test --run src/composables/useKbValidationRun.test.js`
    - 对应需求：[OP-1, FR-5, requirements §1.4, design §16]

  - [x] 5.6 阶段 5 验收
    - 跑 `pnpm test` + `pnpm build`；`pnpm dev` 手动在 `/app/qa-smoke` 跑通一次完整验证流程（发起 → 轮询 → 答复/失败文案），亮/暗主题无异常。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[FR-5, DoD-1, DoD-3, DoD-4]

- [x] 6. 阶段 6：RouteState 视觉刷新
  - [x] 6.1 重写 `RouteState.vue` 的 `<template>` 与 `<style scoped>`
    - 在模板顶部新增 `<figure class="ck-route-state-illustration" aria-hidden="true" />`，背景用 `--ckqa-accent-soft / --ckqa-bg-elevated` 径向渐变，与 AuthLayout 一致。
    - `<style scoped>` 内所有 `#xxxxxx` / `rgb(...)` 裸色值替换为 M1 Token；移除 `.eyebrow` 等段的自定义色值。
    - 按钮由 `<button class="ckqa-el-button ckqa-el-button--primary">` 改写为 `<el-button type="primary">`；删除 `ckqa-el-button / ckqa-el-button--primary` 的自定义样式段（由 M2 Element Plus 主题映射接管）。
    - 保留 `<script setup>` 的 `navGroupLabels / statusLabels / copy` 导出与既有单元测试契约不变。
    - 落点文件：`src/views/status/RouteState.vue`
    - 验证方式：`pnpm test --run src/views/status/` + `pnpm build`
    - 对应需求：[FR-6, CC-4, NFR-4, design §5.6]

  - [x] 6.2 调整 `/app/retrieval-logs` 列表占位的 `moduleLabel`
    - 在 `RouteState.vue` 的 `computed moduleLabel` 内为 `name === 'retrieval-logs'` 的分支返回 `'运维 · 检索日志'`，覆盖默认 `navGroup` 兜底逻辑。
    - 落点文件：`src/views/status/RouteState.vue`
    - 验证方式：`pnpm test --run src/views/status/`
    - 对应需求：[FR-6, FR-7.1]

  - [x] 6.3 保证既有 RouteState 单元测试跑绿
    - 回归 `src/views/status/*.test.js`，确认 `navGroupLabels / statusLabels / copy` 的导出断言与文案均未退化。如因 `moduleLabel` 调整需要更新一条断言，记在 PR 描述。
    - 落点文件：`src/views/status/` 既有测试文件（只更新必需的断言）
    - 验证方式：`pnpm test --run src/views/status/`
    - 对应需求：[FR-6, CC-5]

  - [x] 6.4 阶段 6 验收
    - 跑 `pnpm test` + `pnpm build`；`pnpm dev` 手动遍历 `/app/retrieval-logs`、`/app/retrieval-logs/:logId`、`/app/authorization-audit-logs`、`/app/users/:userId`、`/app/knowledge-bases/:kbId/index-runs` 五条 coming-soon 路由，亮/暗主题下视觉一致无裸色值。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[FR-6, FR-7, DoD-5]

- [x] 7. 阶段 7：巡检与验收
  - [x] 7.1 新增 3 条 Playwright E2E 用例（强制 `data-testid`）
    - 新增 `frontend/apps/admin-app/e2e/m7-users.spec.js`：登录 admin → 进入 `/app/users` → 通过 `data-testid="user-table"` 断言至少 1 行 → 切换分页不报错。
    - 新增 `frontend/apps/admin-app/e2e/m7-health.spec.js`：登录 admin → 进入 `/app/health` → 点击"刷新"按钮 → 断言 `CkStatusPill` 出现且页面文本不含 `/冒烟|embedding|实体抽取|P95|MinerU/i`。
    - 新增 `frontend/apps/admin-app/e2e/m7-validation.spec.js`：登录 admin → 进入 `/app/qa-smoke` → 选择一个有激活索引的 KB（e2e fixture 中 mock）→ 输入问题 → 点击发起 → 等待完成 → 断言答复非空；对 mock 500 响应断言错误文案平实。
    - 落点文件：`e2e/m7-users.spec.js`、`e2e/m7-health.spec.js`、`e2e/m7-validation.spec.js`（按需增补 `e2e/fixtures/`）
    - 验证方式：`pnpm test:e2e -- e2e/m7-users.spec.js e2e/m7-health.spec.js e2e/m7-validation.spec.js`
    - 对应需求：[CC-5, DoD-8, design §10.2]

  - [x] 7.2 新增 Playwright 暗色视觉快照（5 页 × 亮/暗 = 10 张基准图）
    - 在 `e2e/` 下新增 `m7-visual.spec.js`（或按现有 fixtures 约定挂到 `m7-*.spec.js` 的 `test.describe('visual')` 内）。对 UserList / RoleList / PermissionList / HealthPage / KbValidationPage 五个页面在 `data-theme='light'` 与 `'dark'` 下 `expect(page).toHaveScreenshot('{name}-{theme}.png', { maxDiffPixelRatio: 0.003 })`。首次运行建立基准图并 commit 到 `e2e/` 或同目录 `__screenshots__/`（按现有 Playwright 配置）。
    - 落点文件：`e2e/m7-visual.spec.js`、对应 `__screenshots__/` 基准图
    - 验证方式：`pnpm test:e2e -- e2e/m7-visual.spec.js`
    - 对应需求：[CC-4, NFR-3, DoD-9, design §10.3]

  - [x] 7.3 跑 `axe-core` 自动化 A11y 扫描
    - 在 `m7-users.spec.js / m7-health.spec.js / m7-validation.spec.js` 内分别集成 `@axe-core/playwright`（若 `package.json` 未依赖则新增 `devDependency`）；对关键视图断言 `0 serious/critical` + `0 color-contrast` 违规。
    - 落点文件：`e2e/m7-users.spec.js` 等（新增 axe 扫描段）、按需 `package.json`
    - 验证方式：`pnpm test:e2e -- e2e/m7-users.spec.js e2e/m7-health.spec.js e2e/m7-validation.spec.js`
    - 对应需求：[CC-4, NFR-3, DoD-9, design §3.6]

  - [x] 7.4 验证 `stylelint` 无裸 hex / rgb 直写违规
    - 跑 `pnpm lint:style`（若项目未配置 script 则用 `npx stylelint "src/**/*.{vue,scss,css}"`）。例外路径：`styles/element-plus.scss`、`styles/tokens/**`、`node_modules/**`。
    - 若本轮引入了新的裸色值（阶段 3~6 组件），回到对应阶段修复到 Token 引用。
    - 落点文件：`src/` 下新/改样式文件
    - 验证方式：`pnpm lint:style`
    - 对应需求：[NFR-4, DoD-11]

  - [x] 7.5 收紧 P1 断言 + `app-shell.test.js` ModulePage 冒烟测试
    - 在 `src/app-shell.test.js` 把 1.5 引入的 P1 断言从"白名单放宽"收紧为：`∀ route ∈ APP_ROUTES: route.componentKey !== 'ModulePage'`；同时 P5 path 快照保持不变。
    - 保留一条"ModulePage 可编译"的 import-level 冒烟测试（仅 `import ModulePage from '../views/pages/ModulePage.vue'` + `expect(ModulePage).toBeTruthy()`），防止文件被误删但不再走功能路径。
    - 落点文件：`src/app-shell.test.js`
    - 验证方式：`pnpm test --run src/app-shell.test.js`
    - 对应需求：[CC-6, P1, P5, DoD-2, design §3.7]

  - [x] 7.6 全量回归
    - 在 `frontend/apps/admin-app/` 顺序执行：`pnpm test`、`pnpm test:e2e`、`pnpm build`；确保三者全部绿。任何回归问题回到对应阶段修复，最终所有 DoD 条目勾选完毕再合入。
    - 验证方式：`pnpm test` + `pnpm test:e2e` + `pnpm build`
    - 对应需求：[CC-5, DoD-10, DoD-12]

  - [x] 7.7 阶段 7 验收
    - 对照本文件顶部 DoD 12 条逐条勾选；在 PR 描述里附灰度/回退说明（每个页面可回退到的原 `componentKey`）。
    - Ensure all tests pass, ask the user if questions arise.
    - 对应需求：[DoD-1..DoD-12]

## Notes

- 所有命令统一从 `/home/sunlight/Projects/ckqa/.worktrees/admin-redesign-m1-m2/frontend/apps/admin-app/` 执行。
- `smartRelocate` 是 Kiro 工具；文字描述里一律写"用 smartRelocate 工具重命名文件"。
- 每个阶段末尾的"阶段验收"任务是该阶段 DoD 的局部子集；全阶段完成后由阶段 7 收口核对全局 DoD。
- 测试任务紧贴实现任务（每个新组件 / composable 的任务后立即跟随同 PR 内的单元测试任务），不允许延后或集中到阶段 7 才写。
- P1~P6 属性测试分布：P1 / P5 在阶段 1 起骨架、阶段 4/5 收紧、阶段 7 最终收紧；P2 / P3 在阶段 2；P4 在阶段 1（迁移完即删）；P6 在阶段 4。
- 路由 `path / name / meta.permissions / meta.navGroup / meta.section` 在整个 M7 内保持不变（P5 快照约束）；`componentKey / meta.status / meta.routeState / meta.keepAlive` 允许按阶段 3~5 的迁移逐条改动。
- 若某页面合入后发现线上问题：把 `routes.js` 对应 `componentKey` 改回 `'ModulePage'`（用户/角色/权限）、`'HealthView'`（健康，若尚保留文件）或 `'RouteState'`（知识库验证）即可即时回滚，无需数据迁移。

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4", "1.5"] },
    { "id": 3, "tasks": ["2.1"] },
    { "id": 4, "tasks": ["2.2", "2.4"] },
    { "id": 5, "tasks": ["2.3", "3.1", "3.7", "4.3", "5.1"] },
    { "id": 6, "tasks": ["3.2", "4.2", "5.2"] },
    { "id": 7, "tasks": ["3.3", "3.4", "3.5", "4.1", "6.1"] },
    { "id": 8, "tasks": ["4.4", "5.3", "6.2", "6.3"] },
    { "id": 9, "tasks": ["3.6", "4.5", "5.4", "5.5"] },
    { "id": 10, "tasks": ["7.1", "7.2", "7.3", "7.4", "7.5"] },
    { "id": 11, "tasks": ["7.6"] }
  ]
}
```
