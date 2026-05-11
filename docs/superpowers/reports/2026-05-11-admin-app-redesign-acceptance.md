# 管理员端重设计验收对账报告

- 日期：2026-05-11
- 范围：`frontend/apps/admin-app/`
- 分支：`feature/admin-app-redesign-m1-m2`
- 对应设计稿：[2026-05-07-admin-app-redesign-design.md](../specs/2026-05-07-admin-app-redesign-design.md)
- 对应实施计划：M1+M2 / M3 / M4 / M5 / M6 / M7 / 视觉打磨 / [M8 收尾巡查](../plans/2026-05-10-admin-app-redesign-m8-final-audit-plan.md)

## 准入闸结果

| 命令 | 结果 |
| --- | --- |
| `pnpm --dir frontend/apps/admin-app run lint:style` | ✅ 91 个样式文件零裸 hex/rgb |
| `pnpm --dir frontend/apps/admin-app run test` | ✅ 483 / 483 PASS |
| `pnpm --dir frontend/apps/admin-app run test:e2e` | ✅ 90 PASS / 5 skipped / 0 fail |
| `pnpm --dir frontend/apps/admin-app run build` | ✅ Vite build 成功 |

## §14 验收标准对账

| # | 验收条 | 状态 | 证据 |
| --- | --- | --- | --- |
| 14.1 | 视觉一致性：所有页面 token 化、stylelint 无违规 | ✅ | `pnpm lint:style` 通过；`scripts/audit-stylesheet-hex.mjs` 的 `LEGACY_ALLOWLIST` 为空 Map（M8 Task 2.7，commit `5d01ae0`） |
| 14.2 | 功能等价：关键路径 Playwright 全绿 | ✅ | `pnpm test:e2e` 90 PASS（清单见附录 A） |
| 14.3 | 暗色可用：每页在 dark 主题下可正常浏览 | ✅ | `m7-visual.spec.js`（10 张）+ `m8-visual-core.spec.js`（20 张）共 30 张 light/dark 基线全绿（M8 Task 4，commit `e533c65`） |
| 14.4 | 可访问性：键盘可遍历 + axe 0 critical | ✅ | `m7-users / m7-validation / m7-health / m8-axe-core` 共 13 个 axe 用例全 PASS；`e2e/fixtures/axe-helpers.js` 中 `KNOWN_CONTRAST_DEBT_COLOR_PAIRS` 为空数组（M8 Task 3，commit `53161c5`） |
| 14.5 | 文案巡检：UI 不出现工程术语 | ✅ | `src/copy/admin.test.js` 静态属性测试 + `e2e/m8-copy-audit.spec.js` DOM 文本巡检（10 页）全绿；`ALLOWED_HITS` 为空（M8 Task 6，commit `7ca0833`） |
| 14.6 | 无回归：单测 + 组件单测 + Playwright 全绿 | ✅ | 上方准入闸四条 |
| 14.7 | 代码组织：单文件 < 600 行；ModulePage 退化为兜底 | ⚠️ 部分 | 路由 `componentMap` 已不再注册 `ModulePage`，新增 `router-component-map.test.js` 守护测试（M8 Task 1，commit `b7a31e3`）；`ModulePage.vue` 仍 3963 行，作为 `KbBuildWizardPage` 的 6 步表单复用基座保留——详见 §11 与附录 B follow-up |

> Task 0（commit `108a14d`）已在 [2026-05-07-admin-app-redesign-design.md](../specs/2026-05-07-admin-app-redesign-design.md) §4.2 末尾补注脚，说明字号 token 以视觉打磨稿为准。

## M8 收敛清单

| 项目 | 之前 | 之后 |
| --- | --- | --- |
| `LEGACY_ALLOWLIST`（hex 巡检白名单） | 6 个历史文件 | 空 Map |
| `KNOWN_CONTRAST_DEBT_COLOR_PAIRS`（axe 对比度白名单） | 4 条已知债 | 空数组 |
| 路由 `componentMap` 是否含 `ModulePage` | 是 | 否（+ 守护测试） |
| 暗色/亮色视觉基线覆盖 | 5 页 × 2 主题 | 15 页 × 2 主题（M7 10 张 + M8 20 张） |
| axe-core 扫描覆盖 | 3 页（M7） | 13 页（M7 3 + M8 10） |
| DOM 文本术语巡检 | 仅静态属性测试 | + 10 页 e2e DOM 兜底 |

### M8 Task 5 收敛的 axe 违规明细

1. **color-contrast × 5 处** — 通过加深 token 收敛，不引入新白名单：
   - `--ckqa-accent-strong`：`#c4633a` → `#a85530`（on white 5.24:1 / on `--ckqa-bg` 4.98:1，AA ✓）
   - `--ckqa-accent-deepest`：`#a85530` → `#8a3f1f`
   - `--ckqa-running`：`#c08a3a` → `#8c5d1c`（on `--ckqa-running-soft` 5.07:1）
   - `--ckqa-danger`（light）：`#c4413a` → `#b8362f`（on `--ckqa-danger-soft` 5.15:1）
   - `--ckqa-danger`（dark）：`#d96860` → `#e07a72`（on dark soft 5.16:1）
   - CTA 实心背景：9 处 `background: var(--ckqa-accent)` 切到 `var(--ckqa-accent-strong)`
2. **aria-required-children × 1** — `CkSplitProgress`：移除 wrapper 的 `role="list"` / `aria-label`，由 `<ol>` 直接承载语义。
3. **aria-progressbar-name × 1** — `WorkflowStepper` 给 `el-progress` 加 `aria-label`；`CkTaskList` progress 条加 `role="progressbar"` + `aria-valuenow/min/max`。

## 附录 A：Playwright e2e 用例清单（17 spec / 95 用例 / 5 skipped）

> 命令：`pnpm --dir frontend/apps/admin-app exec playwright test --list`。
> 用例覆盖范围按文件分组列出；M8 Task 7 期间发现两条历史回归（见末尾），已就地修复。

### M1+M2 / 视觉打磨基础

- `sidebar-collapse.spec.js`（5）— Sidebar v3 折叠态：toggle 按钮、`Ctrl+\` 快捷键、`localStorage` 持久化、active rail 路由联动、连续切换无错位。
- `data-table-layout.spec.js`（1）— 课程列表操作列固定右侧 + 横向滚动不遮挡。

### M3 / 课程域

- `course-flow.spec.js`（2）— 课程列表 → 详情 → 资料/成员 Tab 切换 + 直链 `/members` 默认激活成员 Tab。

### M4 / 资料域

- `material-detail.spec.js`（3）— 资料详情 4 Tab、`/parse-results` 直链默认激活、`tab=kb-references` URL 同步。

### M5 / 知识库域

- `kb-detail.spec.js`（3）— 列表卡片 + 详情 4 Tab + 「开始/继续构建」CTA。
- `kb-build.spec.js`（2）— 构建向导初始渲染 + buildRunId 续跑轮询。

### M5 局部错误反馈

- `local-operation-errors.spec.js`（14）— 解析失败 / 导出冲突 / 构建失败 / smoke 失败 / 多资料恢复 / 资料选择 / 产物缺失 / Prompt 确认等的局部反馈与状态机。

### M6 / 问答会话域

- `qa-session-list.spec.js`（3）— 卡片化列表、`sessionType=smoke` 过滤、`hasAnomaly=1` URL 写入。
- `qa-session-detail.spec.js`（3）— 双栏骨架 + 默认锁定最新 AI 回答、缺 `retrievalTrace` 时的占位与禁用「查看检索过程」按钮。

### M7 / 系统设置 + 暗色视觉基线

- `m7-users.spec.js`（3）— 用户列表 + 分页同步 + axe 扫描。
- `m7-validation.spec.js`（3）— 知识库验证成功路径 / 失败路径 / axe 扫描。
- `m7-health.spec.js`（2）— 系统健康聚合 + axe 扫描。
- `m7-visual.spec.js`（10）— 5 页 × light/dark 暗色基线。

### M8 / 收尾巡查（本计划产物）

- `m8-visual-core.spec.js`（20）— 10 个核心页 × light/dark 暗色基线（Task 4）。
- `m8-axe-core.spec.js`（10）— 10 个核心页 axe 扫描，无 serious/critical/color-contrast 违规（Task 5）。
- `m8-copy-audit.spec.js`（10）— 10 个核心页 DOM 文本术语巡检（Task 6）。

### 仪表盘（合并门附加修复）

- `dashboard.spec.js`（1）— 看板核心结构 + 流水线段跳转。M8 Task 7 期间修复重复 `aria-label` 与已被淘汰的 `/新建知识库/` 锚点查询。

## 附录 B：已知遗留与跟进 spec

- **ModulePage.vue 瘦身**：路由层已经移除 `ModulePage` 兜底，但文件本身 3963 行仍作为 `KbBuildWizardPage.vue` 的「6 步表单 + 长任务 + SSE 阶段订阅」复用基座保留（设计稿 §9 末尾明确允许）。真正的瘦身（把 6 步表单从 ModulePage 抽出独立组件）转交独立 spec：`docs/superpowers/specs/2026-05-12-admin-app-build-wizard-form-extraction-design.md`（待起草）。
- **M6b 检索诊断面板**：等后端 `retrieval_trace` 字段落地；前端 `QaRetrievalPanelPlaceholder` 占位 + 顶部提示语已就绪（commit `a22cc32`），按设计稿 §6.2「占位 + 顶部提示」对待，不阻塞 M8 收尾。
