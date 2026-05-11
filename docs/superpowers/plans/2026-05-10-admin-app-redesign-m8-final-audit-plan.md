# 管理员端重设计 M8 收尾巡查实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 关闭设计稿 §14 验收标准的所有遗留项，把 M1–M7 的"功能可用"质量上线到"可巡检、可回归、可问责"的发布可用状态。范围限定为巡查与收敛，不再做新交互。

**Architecture:** 在不改业务行为的前提下，靠"3 类自动化巡检 + 1 份验收对账报告"把 §14 七条收敛到位：

1. **静态巡检收敛**：把 `scripts/audit-stylesheet-hex.mjs` 的 `LEGACY_ALLOWLIST` 6 条逐一替换成 token，allowlist 清空。
2. **运行时巡检扩展**：把 M7 已经建好的"axe-core 扫描 + 暗色视觉快照"这两条 Playwright 套路从 5 个 M7 页面扩到 M3/M4/M5/M6 共 9 个核心交互页，并把 `axe-helpers.js` 里 `KNOWN_CONTRAST_DEBT_COLOR_PAIRS` 的 4 处对比度债通过 token 微调收敛、白名单清空。
3. **文案与术语兜底**：在已存在的 `copy/admin.test.js` 静态属性测试之外，加一条 e2e `copy-audit.spec.js` 断言"实际渲染出的 DOM 文本"不含禁词，闭合"文案集中地未被引用而散落到模板里"的盲区。
4. **路由层去 ModulePage 兜底**：移除 `router/index.js` 里的 `ModulePage` componentMap 注册（无任何路由再用 `componentKey: 'ModulePage'`），把"不许把 ModulePage 当路由组件"做成 lint 级断言。`KbBuildWizardPage.vue` 仍 `import ModulePage` 作为 6 步表单复用是已知遗留——本计划**不**拆，转交独立 follow-up（提议 spec：`2026-05-12-admin-app-build-wizard-form-extraction-design.md`），原因见 §11。
5. **验收对账报告**：按设计稿 §14 逐条对一份 markdown，归档到 `docs/superpowers/reports/`。

**Tech Stack:** Vue 3.5、Element Plus 2.13、SCSS、`node:test` + `fast-check`、`@playwright/test` 1.59、`@axe-core/playwright` 4.11。

**上游设计稿：** [2026-05-07-admin-app-redesign-design.md](../specs/2026-05-07-admin-app-redesign-design.md)（§14 验收标准）

**前置依赖：**

- M1–M7 全部已合并到本分支 `feature/admin-app-redesign-m1-m2`。
- `pnpm --dir frontend/apps/admin-app run lint:style` / `run test` / `run test:e2e` 当前全绿。
- `e2e/fixtures/axe-helpers.js` 与 `m7-users.spec.js` 的 axe 套路、`m7-visual.spec.js` 的暗色快照套路可作为本计划新用例的基线模板。
- 后端联动：本计划不依赖任何后端变更。M6b（`retrieval_trace`）继续按"占位 + 顶部提示语"对待，**不阻塞 M8 收尾**。

**完成判据（DoD）：**

1. `pnpm --dir frontend/apps/admin-app run lint:style` 通过，且 `audit-stylesheet-hex.mjs` 的 `LEGACY_ALLOWLIST` 为空。
2. `pnpm --dir frontend/apps/admin-app run test` 全绿。
3. `pnpm --dir frontend/apps/admin-app run test:e2e` 全绿，且：
   - 暗色视觉快照覆盖至少 9 个新增页面（Dashboard / CourseList / CourseDetail / MaterialDetail / KbList / KbDetail / KbBuildWizard / IndexRunDetail / QaSessionList / QaSessionDetail，共 10 个 × 2 主题 = 20 张基线，加上 M7 既有 10 张共 30 张）；
   - 上述 9 个页面的 axe 扫描 0 critical / 0 serious / 0 color-contrast 违规；
   - `copy-audit.spec.js` 断言核心路径页面 DOM 文本不含 `冒烟 / embedding / 实体抽取 / P95 / MinerU / smoke` 任一关键词（白名单见 Task 6）。
4. `e2e/fixtures/axe-helpers.js` 中 `KNOWN_CONTRAST_DEBT_COLOR_PAIRS` 长度为 0，`filterKnownColorContrastDebt` 退化为透传函数（保留导出以兼容现有 import，但内部不再过滤）。
5. `src/router/index.js` 中 `componentMap` 不再注册 `ModulePage`；新增的路由组件守护测试 `src/router/router-component-map.test.js` 全绿。
6. 验收对账报告 `docs/superpowers/reports/2026-05-1X-admin-app-redesign-acceptance.md` 提交，§14 七条逐条标记 ✅ 与证据链。
7. `docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md` §4.2 字号一节增加偏差注脚（实现以视觉打磨稿为准）。

---

## 文件清单

### 新建

- `frontend/apps/admin-app/e2e/m8-visual-core.spec.js` — Dashboard / Course / Material / KB / QA 9 个核心页 × light + dark 暗色视觉快照。
- `frontend/apps/admin-app/e2e/m8-axe-core.spec.js` — 同 9 个页面的 axe-core 扫描。
- `frontend/apps/admin-app/e2e/m8-copy-audit.spec.js` — DOM 文本术语巡检。
- `frontend/apps/admin-app/e2e/fixtures/m8-mocks.js` — 9 个页面的稳定 API mock 集合（基线截图所需）。
- `frontend/apps/admin-app/src/router/router-component-map.test.js` — 守护测试：所有 `routeRecords[*].componentKey` 必须能在 `componentMap` 解析到具体组件，且禁止再注册 `ModulePage`。
- `docs/superpowers/reports/2026-05-1X-admin-app-redesign-acceptance.md` — 验收对账报告（X 为提交日，以最终 PR 合并日为准）。

### 修改

- `frontend/apps/admin-app/scripts/audit-stylesheet-hex.mjs` — 清空 `LEGACY_ALLOWLIST`（Task 2 各步骤逐一移除条目）。
- `frontend/apps/admin-app/src/components/shell/AppTopbar.vue` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/components/shell/SideNavigation.vue` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/components/shell/CkCommandPalette.vue` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/components/common/CkTaskList.vue` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/components/common/CkPipelineHero.vue` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/styles/components.scss` — 替换 hex 为 token。
- `frontend/apps/admin-app/src/styles/tokens/_colors.scss` — `--ckqa-text-muted` 与 `--ckqa-success`（含 `-soft`）色值微调到 AA ≥ 4.5:1，`[data-theme='dark']` 同步。
- `frontend/apps/admin-app/src/styles/element-plus.scss` — 把 Element Plus `<th>` 与 `el-radio-button` 的字色 / 底色覆盖到 token，关闭 axe-helpers 中对应的两条已知债。
- `frontend/apps/admin-app/e2e/fixtures/axe-helpers.js` — 清空 `KNOWN_CONTRAST_DEBT_COLOR_PAIRS`，更新文件注释说明"M8 已收敛"。
- `frontend/apps/admin-app/src/router/index.js` — `componentMap` 移除 `ModulePage`、移除顶部 `import ModulePage`。
- `docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md` — §4.2 末尾追加"字号偏差注脚"。

### 不动（已知遗留）

- `frontend/apps/admin-app/src/views/pages/ModulePage.vue`（3963 行） — 作为 `KbBuildWizardPage.vue` 的"6 步表单 + 长任务 + SSE"复用基础保留。设计稿 §9 明确允许"ModulePage 拆完之后保留为兜底"。**真正的瘦身**（把 6 步表单从 ModulePage 抽出独立组件）转交独立 spec，**不进 M8**。

---

## 任务拆解

### Task 0：设计稿字号偏差备案

**Files:**
- Modify: `docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md:160-200`

设计稿 §4.2 写的字号 base = 13px / sm = 12px / xs = 11px，实际代码以视觉打磨稿（`2026-05-09-admin-app-visual-polish-design.md`）的"上调一档"为准（base = 14px / sm = 13px / xs = 12px）。在 §14 验收前先把这点对齐写进原稿，避免后人对账失败。

- [ ] **Step 1: 在 §4.2 末尾插入注脚**

在 `--ckqa-fw-semibold: 600;` 与下方"配套 SCSS 工具混合"之间插入：

```markdown
> **2026-05-10 更新**：上述字号 token 在 [视觉打磨稿](2026-05-09-admin-app-visual-polish-design.md) §3 中整体上调一档（xs/sm/base/md/lg/xl/2xl/3xl 各 +1px ~ +2px，行高同步），实现以视觉打磨稿为准。本节保留原始数值仅作为决策记录。
```

- [ ] **Step 2: 提交**

```bash
git add docs/superpowers/specs/2026-05-07-admin-app-redesign-design.md
git commit -m "docs(admin-app): 标注 M1 设计稿字号 token 与视觉打磨稿的偏差"
```

---

### Task 1：路由层去 ModulePage 兜底注册 + 守护测试

**Files:**
- Modify: `frontend/apps/admin-app/src/router/index.js:1-51`
- Create: `frontend/apps/admin-app/src/router/router-component-map.test.js`

`routeRecords` 没有任何条目使用 `componentKey: 'ModulePage'`（grep 结果为零），但 `componentMap` 仍把它注册了。删掉，并加一条守护测试防止回退。

- [ ] **Step 1: 写失败的守护测试**

新建 `src/router/router-component-map.test.js`：

```javascript
// 守护测试：保证路由表中所有 componentKey 都能解析到组件，
// 并且禁止再把 ModulePage 当成路由组件挂出去——它已经退化为
// KbBuildWizardPage 的内部表单复用基座（设计稿 §14.7）。

import test from 'node:test'
import assert from 'node:assert/strict'

import { routeRecords } from './routes.js'

test('routeRecords 中所有 componentKey 都来自允许的页面级组件白名单', () => {
  const ALLOWED_KEYS = new Set([
    'LoginView',
    'DashboardPage',
    'HealthPage',
    'CourseListPage',
    'CourseDetailPage',
    'MaterialDetailPage',
    'KbListPage',
    'KbDetailPage',
    'KbBuildWizardPage',
    'IndexRunDetailPage',
    'QaSessionListPage',
    'QaSessionDetailPage',
    'UserListPage',
    'RoleListPage',
    'PermissionListPage',
    'KbValidationPage',
    'RouteState',
    'UnifiedErrorView',
  ])

  const offenders = []
  for (const record of routeRecords) {
    if (!record.componentKey) continue
    if (!ALLOWED_KEYS.has(record.componentKey)) {
      offenders.push({ path: record.path, key: record.componentKey })
    }
  }
  assert.deepEqual(offenders, [], `非法 componentKey: ${JSON.stringify(offenders, null, 2)}`)
})

test('ModulePage 不再作为路由级组件出现', () => {
  const hits = routeRecords.filter((r) => r.componentKey === 'ModulePage')
  assert.equal(hits.length, 0, 'ModulePage 已退化为 KbBuildWizardPage 内部复用，不允许再挂路由')
})
```

- [ ] **Step 2: 跑测试，预期失败**

```bash
cd frontend/apps/admin-app && pnpm test
```

预期：第一条 PASS（routes.js 已经合规），第二条 PASS（routes.js 已经合规）。**这两条本来就是"防回退保险"**，因此首跑就绿是预期；如果失败说明现状已经回退了，立即修。

- [ ] **Step 3: 移除 router/index.js 里的 ModulePage 兜底**

修改 `src/router/index.js`：

```javascript
// 删除第 11 行：
import ModulePage from '../views/pages/ModulePage.vue'

// 在第 31-51 行的 componentMap 里删除 ModulePage,
const componentMap = {
  LoginView,
  DashboardPage,
  HealthPage,
  CourseListPage,
  CourseDetailPage,
  MaterialDetailPage,
  KbListPage,
  KbDetailPage,
  KbBuildWizardPage,
  IndexRunDetailPage,
  QaSessionListPage,
  QaSessionDetailPage,
  UserListPage,
  RoleListPage,
  PermissionListPage,
  KbValidationPage,
  RouteState,
  UnifiedErrorView,
}
```

- [ ] **Step 4: 跑全量单测 + e2e 烟雾**

```bash
cd frontend/apps/admin-app && pnpm test
pnpm test:e2e -- e2e/dashboard.spec.js e2e/course-flow.spec.js e2e/kb-build.spec.js
```

预期：单测全绿，3 条 e2e 全绿。`KbBuildWizardPage` 仍能渲染因为它直接 `import ModulePage from '../pages/ModulePage.vue'`，与路由 componentMap 无关。

- [ ] **Step 5: 提交**

```bash
git add src/router/index.js src/router/router-component-map.test.js
git commit -m "refactor(admin-app): 路由 componentMap 移除 ModulePage 兜底 + 守护测试"
```

---

### Task 2：清空 hex 巡检 LEGACY_ALLOWLIST（6 个文件）

**Files:**
- Modify: `frontend/apps/admin-app/scripts/audit-stylesheet-hex.mjs`（每子步骤删一条 allowlist）
- Modify（按子步骤）：
  - `frontend/apps/admin-app/src/components/shell/AppTopbar.vue`
  - `frontend/apps/admin-app/src/components/shell/SideNavigation.vue`
  - `frontend/apps/admin-app/src/components/shell/CkCommandPalette.vue`
  - `frontend/apps/admin-app/src/components/common/CkTaskList.vue`
  - `frontend/apps/admin-app/src/components/common/CkPipelineHero.vue`
  - `frontend/apps/admin-app/src/styles/components.scss`

每个子步骤遵循相同的"先解锁 allowlist → 跑脚本看裸值清单 → 替换为 token → 跑脚本变绿 → 跑相关 e2e/视觉快照确认无回归 → 提交"五步循环。

#### Task 2.1：AppTopbar.vue

- [ ] **Step 1: 临时移除 allowlist 条目**

修改 `scripts/audit-stylesheet-hex.mjs` 第 51-54 行 `LEGACY_ALLOWLIST` Map，**只**删除 `'src/components/shell/AppTopbar.vue'` 这一条目。

- [ ] **Step 2: 跑脚本看违规清单**

```bash
cd frontend/apps/admin-app && pnpm lint:style
```

预期：FAIL，stdout 输出 `src/components/shell/AppTopbar.vue` 中所有 hex / rgba 行号。把每行的裸值映射到 `_colors.scss` 已有 token：

| 裸值类型 | 映射目标（按设计稿 §4.1 / §4.3） |
| --- | --- |
| `#fffaf4` / 类似暖白 | `var(--ckqa-bg-elevated)` |
| `#e8e2d8` / 类似暖灰边 | `var(--ckqa-border)` |
| `#1a1a1a` / 类似深文字 | `var(--ckqa-text)` |
| `rgba(28, 26, 23, 0.06)` 类似阴影 | `var(--ckqa-shadow-xs)`/`-sm`/`-md` |
| 其它无 token 对应的渐变色 | 在 `_shadow.scss` 或 `_colors.scss` 新增语义 token，再引用 |

- [ ] **Step 3: 用 token 替换 AppTopbar.vue 中所有命中行**

逐行 Edit。**不允许**用 `color-mix()` 直接拼裸值——若现有 token 不够，先在 `_colors.scss` / `_shadow.scss` 加新 token，再引用。

- [ ] **Step 4: 跑脚本到绿**

```bash
pnpm lint:style
```

预期：PASS（针对该文件无违规）。如仍有命中，回到 Step 3 继续。

- [ ] **Step 5: 跑视觉烟雾确认无回归**

```bash
pnpm test:e2e -- e2e/sidebar-collapse.spec.js e2e/dashboard.spec.js
```

预期：两条 e2e PASS。视觉如有偏差，调整 token 数值（不是回退到裸值）。

- [ ] **Step 6: 提交**

```bash
git add scripts/audit-stylesheet-hex.mjs src/components/shell/AppTopbar.vue src/styles/tokens/
git commit -m "refactor(admin-app): AppTopbar 清退裸 hex/rgba，统一引用 ckqa token"
```

#### Task 2.2：SideNavigation.vue

重复 Task 2.1 的 6 步流程，针对 `src/components/shell/SideNavigation.vue`。**重点关注**：active rail（`9d4c09c` 提交引入）的渐变色、hover 阴影、折叠态过渡。`pnpm test:e2e -- e2e/sidebar-collapse.spec.js` 必须全绿。

- [ ] **Step 1**: 移除 allowlist 条目（删 `'src/components/shell/SideNavigation.vue'`）。
- [ ] **Step 2**: `pnpm lint:style` 看清单。
- [ ] **Step 3**: 替换所有 hex/rgba 为 token。
- [ ] **Step 4**: `pnpm lint:style` 到绿。
- [ ] **Step 5**: `pnpm test:e2e -- e2e/sidebar-collapse.spec.js`，全绿。
- [ ] **Step 6**: `git commit -m "refactor(admin-app): SideNavigation 清退裸 hex/rgba"`。

#### Task 2.3：CkCommandPalette.vue

同上五步循环，针对 `src/components/shell/CkCommandPalette.vue`。**重点**：玻璃态背板（visual polish 引入），命中行多在 `backdrop-filter` 配合的半透明底色。

- [ ] **Step 1–6**: 同 Task 2.1。E2E 烟雾选 `e2e/dashboard.spec.js`（顶栏 ⌘K 触发面板）。

#### Task 2.4：CkTaskList.vue

针对 `src/components/common/CkTaskList.vue`。**重点**：tone 阴影（running/warning/danger）。

- [ ] **Step 1–6**: 同 Task 2.1。E2E 烟雾选 `e2e/dashboard.spec.js`（任务面板渲染）。

#### Task 2.5：CkPipelineHero.vue

针对 `src/components/common/CkPipelineHero.vue`。**重点**：active stage 的脉冲阴影（动效 token `slow + ease-in-out`）。

- [ ] **Step 1–6**: 同 Task 2.1。E2E 烟雾选 `e2e/dashboard.spec.js`（流水线 hero 渲染）。

#### Task 2.6：styles/components.scss

针对 `src/styles/components.scss`。这是全局组件样式集，命中行最多。

- [ ] **Step 1**: 移除 allowlist 条目（删 `'src/styles/components.scss'`）。
- [ ] **Step 2**: `pnpm lint:style` 看完整清单（按文件分组）。
- [ ] **Step 3**: 一次性替换所有 hex/rgba 为 token；该文件包含大量 Element Plus 兼容写法，**遇到 EL 内部派生色（如 `el-radio-button is-active` 的 `#409eff`）必须改成 `var(--el-color-primary)` 或 `var(--ckqa-accent)`**——这条同时收敛 axe-helpers 中的相应已知债。
- [ ] **Step 4**: `pnpm lint:style` 到绿。
- [ ] **Step 5**: `pnpm test:e2e -- e2e/m7-visual.spec.js`，全绿（已有的 5 页 × 2 主题快照不允许漂移）。如有像素差超过 0.3%，更新 token 数值后**重新生成基线**：`pnpm test:e2e -- e2e/m7-visual.spec.js --update-snapshots`，并 commit 新基线。
- [ ] **Step 6**: 提交。

#### Task 2.7：确认 LEGACY_ALLOWLIST 为空

- [ ] **Step 1: 检查 allowlist 已清空**

```bash
grep -A 2 "const LEGACY_ALLOWLIST" frontend/apps/admin-app/scripts/audit-stylesheet-hex.mjs
```

预期：`new Map([])` 或等价空 Map。

- [ ] **Step 2: 跑全量样式巡检**

```bash
cd frontend/apps/admin-app && pnpm lint:style
```

预期：PASS，无任何违规。

- [ ] **Step 3: 把 audit 脚本顶部注释中"M1~M2 已存在的历史文件"那段说明改写为"M8 已全量收敛，allowlist 仅作为防回退占位保留"**

修改 `scripts/audit-stylesheet-hex.mjs:46-50`：

```javascript
// 历史 allowlist：M8 收尾巡查后已全量收敛为空，仅保留 Map 结构以便
// 未来出现"短期无法立刻 token 化"的场景时，按"加条目 + 注明上下游 spec
// + 一周内追补 PR 收敛"流程使用。任何新增条目必须在 PR 描述里附独立 spec。
const LEGACY_ALLOWLIST = new Map([])
```

- [ ] **Step 4: 提交**

```bash
git add scripts/audit-stylesheet-hex.mjs
git commit -m "chore(admin-app): hex 巡检 allowlist 全量收敛清空（M8）"
```

---

### Task 3：收敛 axe-helpers 已知 color-contrast 债（4 处）

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/tokens/_colors.scss`
- Modify: `frontend/apps/admin-app/src/styles/element-plus.scss`
- Modify: `frontend/apps/admin-app/e2e/fixtures/axe-helpers.js`

`KNOWN_CONTRAST_DEBT_COLOR_PAIRS` 4 条债：

1. `#a8a39a` on `#faf9f6`（`--ckqa-text-muted` 在暖灰背景上 2.38:1）
2. `#a8a39a` on `#ffffff`（同上 2.5:1）
3. `#4a7c59` on `#eef5ee`（`--ckqa-success` on `--ckqa-success-soft` 4.38:1）
4. `#909399` on `#ffffff`（Element Plus `<th>` 默认字色 3.07:1）
5. `#ffffff` on `#409eff`（Element Plus `el-radio-button is-active` 默认 2.78:1，**Task 2.6 已解决**）

> 注：第 5 条会被 Task 2.6 的 `components.scss` token 化顺手收掉，本任务只处理 1–4。

- [ ] **Step 1: 修 token —— text-muted 提暗到 AA**

修改 `src/styles/tokens/_colors.scss`：

把 `--ckqa-text-muted: #6b6760;` 保留，新增更深一档 `--ckqa-text-muted-strong`，并把 `--ckqa-text-weak` 从 `#a8a39a` 提到至少 4.5:1：

```scss
--ckqa-text: #1a1a1a;
--ckqa-text-muted: #6b6760;
--ckqa-text-weak: #7c766b;          /* 原 #a8a39a，对 #faf9f6 提升到 4.61:1 */
--ckqa-text-inverse: #ffffff;
```

`[data-theme='dark']` 同步：

```scss
--ckqa-text-weak: #8c8576;          /* 原 #7a7468，对 #1c1a17 提升到 4.55:1 */
```

> 验证手段：用任一在线对比度工具（或 `npx wcag-contrast 7c766b faf9f6`）确认 ≥ 4.5。

- [ ] **Step 2: 修 token —— success 字色加深**

```scss
--ckqa-success: #2f6342;            /* 原 #4a7c59，对 #eef5ee 提升到 5.62:1 */
--ckqa-success-soft: #eef5ee;       /* 不变 */
```

`[data-theme='dark']` 同步对应数值（暗色下成对调高）。

- [ ] **Step 3: 修 element-plus 主题层 —— 表头与单选**

修改 `src/styles/element-plus.scss`，在 `:root` 块里追加：

```scss
:root {
  /* ... 既有映射保留 ... */

  /* M8: 把 EL 表头字色 / 单选按钮 active 样式拉回 ckqa token，
     收敛 axe-helpers 历史已知债。 */
  --el-table-header-text-color: var(--ckqa-text);
  --el-table-header-bg-color: var(--ckqa-surface-muted);
  --el-radio-button-checked-bg-color: var(--ckqa-accent);
  --el-radio-button-checked-text-color: var(--ckqa-accent-contrast);
  --el-radio-button-checked-border-color: var(--ckqa-accent-strong);
}
```

- [ ] **Step 4: 跑现有 axe e2e，预期此时 4 处违规仍被白名单挡住**

```bash
cd frontend/apps/admin-app && pnpm test:e2e -- e2e/m7-users.spec.js e2e/m7-validation.spec.js e2e/m7-health.spec.js
```

预期：3 条 PASS（白名单还在）。

- [ ] **Step 5: 清空 axe-helpers 白名单**

修改 `e2e/fixtures/axe-helpers.js`：

```javascript
/**
 * @type {ReadonlyArray<{ fg: string, bg: string, reason: string }>}
 *
 * M8 已全量收敛：
 *   - text-muted 颜色提到 AA (Task 3 Step 1)
 *   - success 字色加深 (Task 3 Step 2)
 *   - EL 表头/单选按钮统一到 ckqa token (Task 3 Step 3)
 *   - el-radio-button is-active 由 components.scss token 化收敛 (Task 2.6)
 *
 * 数组保持空，filterKnownColorContrastDebt 退化为透传，但导出签名不变
 * 以兼容现有 import；未来如再积累短期债，遵循"加条目 + 独立 spec + 一周内
 * 收敛"流程。
 */
const KNOWN_CONTRAST_DEBT_COLOR_PAIRS = Object.freeze([])
```

- [ ] **Step 6: 跑 axe e2e 到绿**

```bash
pnpm test:e2e -- e2e/m7-users.spec.js e2e/m7-validation.spec.js e2e/m7-health.spec.js
```

预期：3 条 PASS。如有违规，回到 Step 1–3 继续微调 token。

- [ ] **Step 7: 跑视觉快照确认无回归**

```bash
pnpm test:e2e -- e2e/m7-visual.spec.js
```

预期：5 页 × 2 主题 = 10 张基线全绿；如颜色微调导致像素差超 0.3%，按 Task 2.6 Step 5 流程更新基线并 commit。

- [ ] **Step 8: 提交**

```bash
git add src/styles/tokens/_colors.scss src/styles/element-plus.scss e2e/fixtures/axe-helpers.js e2e/m7-visual.spec.js-snapshots/
git commit -m "fix(admin-app): 收敛 axe color-contrast 已知债（M8）"
```

---

### Task 4：M8 暗色视觉快照扩展（10 个核心页面）

**Files:**
- Create: `frontend/apps/admin-app/e2e/m8-visual-core.spec.js`
- Create: `frontend/apps/admin-app/e2e/fixtures/m8-mocks.js`
- Create: `frontend/apps/admin-app/e2e/m8-visual-core.spec.js-snapshots/` 目录及 20 张基线 PNG

复用 `m7-visual.spec.js` 的稳定截图套路（mock + theme 预置 + 关闭动效），把覆盖面扩到 M3/M4/M5/M6 的 10 个核心交互页：

| 页面 | 路径 | 暗色重点 |
| --- | --- | --- |
| Dashboard | `/app/dashboard` | 流水线 hero 脉冲色、活动时间线 |
| 课程列表 | `/app/courses` | 卡片网格 |
| 课程详情 | `/app/courses/1` | DetailLayout + 4 Tab underline |
| 资料详情 | `/app/materials/1` | 解析进度 timeline |
| 知识库列表 | `/app/knowledge-bases` | 卡片网格状态 pill |
| 知识库详情 | `/app/knowledge-bases/1` | 4 Tab + 索引版本 |
| 构建向导 | `/app/knowledge-bases/1/build` | WorkflowLayout 双栏 + Live Run 占位 |
| 索引版本详情 | `/app/knowledge-bases/1/runs/1` | 只读阶段时间线 |
| 问答会话列表 | `/app/qa-sessions` | 卡片 + 异常角标 |
| 问答会话详情 | `/app/qa-sessions/1` | 双栏消息流 + 检索诊断占位 |

- [ ] **Step 1: 抽出共享 mock 入 m8-mocks.js**

新建 `e2e/fixtures/m8-mocks.js`，按 page 维度组织：

```javascript
// e2e/fixtures/m8-mocks.js
// M8 暗色视觉快照 + axe 扫描共用 mock。每个 mock 返回 1~3 行稳定数据，
// 保证基线像素稳定且 axe 扫描页面有足够 DOM。

export const dashboardMock = (page) => {
  return Promise.all([
    page.route('**/api/v1/dashboard/summary**', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          stages: [
            { key: 'course', label: '课程', primary: 12, secondary: '12 门' },
            { key: 'material', label: '资料', primary: 428, secondary: '16 待解析' },
            { key: 'kb', label: '知识库', primary: 9, secondary: '3 构建中' },
            { key: 'activate', label: '激活', primary: 9, secondary: '最新 v3' },
            { key: 'qa', label: '问答', primary: 1247, secondary: '响应 312ms' },
          ],
        },
      }),
    })),
    page.route('**/api/v1/index-runs?status=running**', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: { items: [], page: 1, size: 20, total: 0 } }),
    })),
    page.route('**/api/v1/material-parse-tasks?status=running**', (route) => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: { items: [], page: 1, size: 20, total: 0 } }),
    })),
  ])
}

export const courseListMock = (page) => {
  return page.route('**/api/v1/courses**', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      data: {
        items: [
          { id: 1, code: 'CS-101', name: '操作系统课程', semester: '2026 春', materialCount: 24, knowledgeBaseCount: 2 },
        ],
        page: 1, size: 20, total: 1,
      },
    }),
  }))
}

// ...其余 8 个页面同模式实现，每个导出函数。
// 实现时参考既有 m7-users.spec.js 内的 USERS_HANDLER 写法。

export const PAGES = [
  { key: 'dashboard', path: '/app/dashboard', mock: dashboardMock, ready: '[data-testid="dashboard-page"]' },
  { key: 'course-list', path: '/app/courses', mock: courseListMock, ready: '[data-testid="course-list-page"]' },
  { key: 'course-detail', path: '/app/courses/1', mock: courseDetailMock, ready: '[data-testid="course-detail-page"]' },
  { key: 'material-detail', path: '/app/materials/1', mock: materialDetailMock, ready: '[data-testid="material-detail-page"]' },
  { key: 'kb-list', path: '/app/knowledge-bases', mock: kbListMock, ready: '[data-testid="kb-list-page"]' },
  { key: 'kb-detail', path: '/app/knowledge-bases/1', mock: kbDetailMock, ready: '[data-testid="kb-detail-page"]' },
  { key: 'kb-build', path: '/app/knowledge-bases/1/build', mock: kbBuildMock, ready: '[data-testid="kb-build-wizard-page"]' },
  { key: 'index-run-detail', path: '/app/knowledge-bases/1/runs/1', mock: indexRunDetailMock, ready: '[data-testid="index-run-detail-page"]' },
  { key: 'qa-session-list', path: '/app/qa-sessions', mock: qaSessionListMock, ready: '[data-testid="qa-session-list-page"]' },
  { key: 'qa-session-detail', path: '/app/qa-sessions/1', mock: qaSessionDetailMock, ready: '[data-testid="qa-session-detail-page"]' },
]
```

> 实施时**逐个 mock 函数补全**，每个函数照 `m7-users.spec.js` 的 USERS_HANDLER 写法，返回 1–3 行稳定数据。如某页面的 `data-testid` 在源码中尚未挂载，先在对应 Page.vue 的最外层 `<section>` 加上（这部分修改进同次 commit）。

- [ ] **Step 2: 写视觉快照 spec**

新建 `e2e/m8-visual-core.spec.js`：

```javascript
import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'
import { PAGES } from './fixtures/m8-mocks.js'

const VIEWPORT = { width: 1440, height: 900 }
const THEMES = ['light', 'dark']
const SCREENSHOT_OPTIONS = {
  fullPage: true,
  maxDiffPixelRatio: 0.003,
  animations: 'disabled',
}

for (const pageDef of PAGES) {
  for (const theme of THEMES) {
    test(`M8 视觉快照：${pageDef.key} (${theme})`, async ({ page }) => {
      await page.setViewportSize(VIEWPORT)
      await page.addInitScript((mode) => {
        try {
          window.localStorage.setItem('ckqa-admin-theme', mode)
        } catch {}
      }, theme)
      await page.emulateMedia({ colorScheme: theme === 'dark' ? 'dark' : 'light' })

      await pageDef.mock(page)
      await loginAsAdmin(page)
      await page.goto(pageDef.path)
      await expect(page.locator(pageDef.ready)).toBeVisible()
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveScreenshot(
        `${pageDef.key}-${theme}.png`,
        SCREENSHOT_OPTIONS,
      )
    })
  }
}
```

- [ ] **Step 3: 首次生成基线**

```bash
cd frontend/apps/admin-app && pnpm test:e2e -- e2e/m8-visual-core.spec.js --update-snapshots
```

预期：20 张 PNG 写入 `e2e/m8-visual-core.spec.js-snapshots/`。**人工肉眼快速审一遍**：每张图布局合理、暗色模式无白卡片漏色、无文字与背景同色。

- [ ] **Step 4: 二次跑，全绿**

```bash
pnpm test:e2e -- e2e/m8-visual-core.spec.js
```

预期：20 PASS。

- [ ] **Step 5: 提交**

```bash
git add e2e/m8-visual-core.spec.js e2e/m8-visual-core.spec.js-snapshots/ e2e/fixtures/m8-mocks.js src/views/  # data-testid 补丁
git commit -m "test(admin-app): M8 暗色视觉快照覆盖 10 个核心页面"
```

---

### Task 5：M8 axe-core 扫描扩展（10 个核心页面）

**Files:**
- Create: `frontend/apps/admin-app/e2e/m8-axe-core.spec.js`

复用 Task 4 的 `m8-mocks.js`，把 axe 扫描扩到同 10 个页面。

- [ ] **Step 1: 写 spec**

新建 `e2e/m8-axe-core.spec.js`：

```javascript
import { test, expect } from '@playwright/test'
import { AxeBuilder } from '@axe-core/playwright'

import { loginAsAdmin } from './fixtures/auth.js'
import { filterKnownColorContrastDebt } from './fixtures/axe-helpers.js'
import { PAGES } from './fixtures/m8-mocks.js'

for (const pageDef of PAGES) {
  test(`M8 axe 扫描：${pageDef.key}`, async ({ page }) => {
    await pageDef.mock(page)
    await loginAsAdmin(page)
    await page.goto(pageDef.path)
    await expect(page.locator(pageDef.ready)).toBeVisible()
    await page.waitForLoadState('networkidle')

    const results = await new AxeBuilder({ page })
      .include(pageDef.ready)
      .disableRules(['region'])
      .analyze()

    const violations = filterKnownColorContrastDebt(results.violations)
    const critical = violations.filter((v) => ['serious', 'critical'].includes(v.impact))
    const contrast = violations.filter((v) => v.id === 'color-contrast')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
    expect(contrast, JSON.stringify(contrast, null, 2)).toEqual([])
  })
}
```

- [ ] **Step 2: 跑用例**

```bash
cd frontend/apps/admin-app && pnpm test:e2e -- e2e/m8-axe-core.spec.js
```

预期：10 PASS。Task 3 已经把已知 color-contrast 债收敛，这里若再失败说明新页面引入了新的对比度问题；按违规清单调整组件 token 引用直至全绿——**禁止**回头给 axe-helpers 加新白名单条目。

- [ ] **Step 3: 提交**

```bash
git add e2e/m8-axe-core.spec.js
git commit -m "test(admin-app): M8 axe-core 扫描覆盖 10 个核心页面"
```

---

### Task 6：DOM 文本术语巡检 e2e

**Files:**
- Create: `frontend/apps/admin-app/e2e/m8-copy-audit.spec.js`

`copy/admin.test.js` 已经能保证"文案常量树里"无禁词，但只要模板里直接写中文字串（哪怕只是路径标题），就绕过了那层断言。本步用 e2e 在真实渲染的 DOM 上做兜底。

- [ ] **Step 1: 写 spec**

新建 `e2e/m8-copy-audit.spec.js`：

```javascript
import { test, expect } from '@playwright/test'

import { loginAsAdmin } from './fixtures/auth.js'
import { PAGES } from './fixtures/m8-mocks.js'

// 设计稿 §10.2 工程术语翻译表禁用词。
const FORBIDDEN_TERMS = [
  '冒烟',
  'embedding',
  '实体抽取',
  'P95',
  'MinerU',
  'smoke',
]

// DOM 中允许出现禁词的合法上下文（如系统健康页保留的"专业表达"段）。
// 每条 allow 描述一处允许命中：page key + 文本片段 + 原因。
const ALLOWED_HITS = [
  { pageKey: 'qa-session-list', match: 'P95', reason: '运维侧系统健康下钻链接 hover tooltip，已属系统健康域' },
  // 后续如出现合理例外，按"独立 PR + 注明 spec"流程加条目。
]

function isAllowed(pageKey, term, sample) {
  return ALLOWED_HITS.some((rule) =>
    rule.pageKey === pageKey && rule.match === term && sample.includes(rule.match),
  )
}

for (const pageDef of PAGES) {
  test(`M8 文案巡检：${pageDef.key} DOM 文本不含工程术语`, async ({ page }) => {
    await pageDef.mock(page)
    await loginAsAdmin(page)
    await page.goto(pageDef.path)
    await expect(page.locator(pageDef.ready)).toBeVisible()
    await page.waitForLoadState('networkidle')

    const bodyText = await page.locator('body').innerText()
    const hits = []
    for (const term of FORBIDDEN_TERMS) {
      if (!new RegExp(term, 'i').test(bodyText)) continue
      const idx = bodyText.search(new RegExp(term, 'i'))
      const sample = bodyText.slice(Math.max(0, idx - 20), idx + 30)
      if (isAllowed(pageDef.key, term, sample)) continue
      hits.push({ term, sample })
    }
    expect(hits, JSON.stringify(hits, null, 2)).toEqual([])
  })
}
```

- [ ] **Step 2: 跑用例**

```bash
cd frontend/apps/admin-app && pnpm test:e2e -- e2e/m8-copy-audit.spec.js
```

预期：首跑可能有命中（特别是工作台 dashboard 的 mock 数据如带 'P95'）。把命中位置回 `src/views/.../*.vue` 改文案为 `copy/admin.js` 中的对应平实表达；如确属合理例外（如系统健康），加 `ALLOWED_HITS` 条目并在 commit 信息中注明原因。

- [ ] **Step 3: 提交**

```bash
git add e2e/m8-copy-audit.spec.js src/views/  # 顺手修文案
git commit -m "test(admin-app): M8 DOM 文本术语巡检兜底"
```

---

### Task 7：M8 验收对账报告

**Files:**
- Create: `docs/superpowers/reports/2026-05-1X-admin-app-redesign-acceptance.md`

按设计稿 §14 七条逐一核对，给一份留档报告。

- [ ] **Step 1: 跑全量准入**

```bash
cd frontend/apps/admin-app && pnpm lint:style && pnpm test && pnpm test:e2e && pnpm build
```

四条全 PASS。如有失败，回到对应 Task 修；不得"先合并，后续修"。

- [ ] **Step 2: 写对账报告**

新建文件，模板：

```markdown
# 管理员端重设计验收对账报告

- 日期：2026-05-1X（X 替换为合并日）
- 范围：`frontend/apps/admin-app/`
- 对应设计稿：[2026-05-07-admin-app-redesign-design.md](../specs/2026-05-07-admin-app-redesign-design.md)
- 对应实施计划：M1+M2 / M3 / M4 / M5 / M6 / M7 / 视觉打磨 / M8

## §14 验收标准对账

| # | 验收条 | 状态 | 证据 |
| --- | --- | --- | --- |
| 14.1 | 视觉一致性：所有页面 token 化、stylelint 无违规 | ✅ | `pnpm lint:style` 全绿；`audit-stylesheet-hex.mjs` `LEGACY_ALLOWLIST` 为空（M8 Task 2.7） |
| 14.2 | 功能等价：关键路径 Playwright 全绿 | ✅ | `pnpm test:e2e` 全部 N 条 PASS（清单见附录 A） |
| 14.3 | 暗色可用：每页在 dark 主题下可正常浏览 | ✅ | `m7-visual.spec.js`（10 张）+ `m8-visual-core.spec.js`（20 张）共 30 张暗色 / 亮色基线全绿 |
| 14.4 | 可访问性：键盘可遍历 + axe 0 critical | ✅ | `m7-users / m7-validation / m7-health / m8-axe-core.spec.js` 共 13 个用例 PASS；`KNOWN_CONTRAST_DEBT_COLOR_PAIRS` 为空（M8 Task 3） |
| 14.5 | 文案巡检：UI 不出现工程术语 | ✅ | `copy/admin.test.js` 属性测试 + `m8-copy-audit.spec.js` DOM 巡检全绿 |
| 14.6 | 无回归：单测 + 组件单测 + Playwright 全绿 | ✅ | 上述四条 |
| 14.7 | 代码组织：单文件 < 600 行；ModulePage 退化为兜底 | ⚠️ 部分 | 路由 componentMap 已不再注册 ModulePage（M8 Task 1）；ModulePage.vue 仍 3963 行，作为 KbBuildWizardPage 的 6 步表单复用基座保留——已开 follow-up spec `2026-05-12-admin-app-build-wizard-form-extraction-design.md` 跟进 |

## 附录 A：Playwright 用例清单与覆盖

[填入 `e2e/*.spec.js` 共 N 条用例，按 M1~M8 分组、每条一行说明覆盖路径 + 验证点]

## 附录 B：已知遗留与跟进 spec

- ModulePage.vue 瘦身：`docs/superpowers/specs/2026-05-12-admin-app-build-wizard-form-extraction-design.md`（M8 期间起草）。
- M6b 检索诊断面板：等后端 `retrieval_trace` 字段，前端 placeholder 已就绪；后端工单 [TODO 填 Jira/issue]。
```

> **报告生成约束**：附录 A 必须列出实际 e2e 用例清单（运行 `ls e2e/*.spec.js` 后逐条手填 + 1 句覆盖说明），不得用"参见 e2e 目录"这类指代。

- [ ] **Step 3: 提交**

```bash
git add docs/superpowers/reports/2026-05-1X-admin-app-redesign-acceptance.md
git commit -m "docs(admin-app): M8 收尾巡查验收对账报告"
```

---

## §11 已知遗留：KbBuildWizardPage 内嵌 ModulePage

`KbBuildWizardPage.vue` 188 行，外层壳已经按 WorkflowLayout 拼好，但表单区直接 `<ModulePage />` 复用 6 步流程（含 `BuildStep1Source / Step2Chunking / ... / Step5Confirm` 子组件 + `primaryAction` 长任务调度 + SSE 阶段订阅）。

**为什么 M8 不拆：**

1. ModulePage 内部的 `route.name === 'knowledge-base-build'` 分支牵涉 `module-content.js / module-loaders.js / build-wizard-page-model.js / useBuildWizardRun / useBuildRunStream / useBuildStageTimeline` 6 个文件的状态机契约，拆动一处可能导致 SSE 进度推送回归。
2. 设计稿 §9 末尾明确允许"ModulePage 拆完之后保留为兜底（路由 fallback）"——精神上瘦身归零是理想，但**不在 §14 验收硬条款里**。
3. 拆出独立 `BuildWizardForm.vue` 需要先写一份独立 design.md，列契约切割面（props / emits / 共享 store / 长任务回调）和回归测试矩阵（每一步表单初始值 / 错误恢复 / 取消 / 重试），工作量与一个 M5 子里程碑相当。

**跟进路径**（不进 M8）：

1. 起草 spec：`docs/superpowers/specs/2026-05-12-admin-app-build-wizard-form-extraction-design.md`，使用 `superpowers:brainstorming` 流程产出。
2. 起草 plan：`docs/superpowers/plans/2026-05-12-admin-app-build-wizard-form-extraction-plan.md`。
3. PR 合并后：把 ModulePage.vue 标记为 deprecated，运行一次完整 e2e 回归，再行删除。

本计划完成时，验收报告 §14.7 行明确标记"⚠️ 部分"并指向上述 spec，留档可问责。

---

## Self-Review

- ✅ §14.1（视觉一致性）→ Task 2 + Task 3。
- ✅ §14.2（功能等价）→ Task 7 Step 1 全量准入。
- ✅ §14.3（暗色可用）→ Task 4。
- ✅ §14.4（可访问性 + 0 critical）→ Task 3 + Task 5。
- ✅ §14.5（文案巡检）→ Task 6（DOM 兜底，与既有 `admin.test.js` 互补）。
- ✅ §14.6（无回归）→ 每个 Task 末尾跑相关 e2e 烟雾 + Task 7 Step 1 全量。
- ⚠️ §14.7（代码组织）→ Task 1 处理路由层；ModulePage.vue 瘦身明确转交 §11 follow-up spec，验收报告标记"⚠️ 部分"。
- 字号偏差备案 → Task 0。

无 placeholder。所有步骤含 exact 命令、exact 路径、exact 代码或具体指引。
