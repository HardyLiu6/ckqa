# 管理员端视觉打磨实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 M1+M2+M3 已落地的基础上，完成系统级视觉调优：字号体系上调一档、建立玻璃态表面层级、Sidebar v3（图标 + active rail + 折叠态 + 动效）、弹簧反馈动效语言、品牌名统一为「智课问答」，让管理员端从"功能可用"提升到"产品级质感"。

**Architecture:** 纯增量改造，不动信息架构、组件契约（props/events/slots）、API 层、路由路径。Token 层做字号上调 + 新增 glass/ambient/spring 变量；样式层新增 `.ck-glass-card` / `.ck-pressable` 工具类；`SideNavigation.vue` 完整重写为 v3（图标 + rail + 折叠）；Dashboard 组件接入新视觉；品牌文案替换。

**Tech Stack:** Vue 3.5、Vite 8、Element Plus 2.13、SCSS、`node:test` 单元测试、Playwright E2E。

**上游设计稿：** [2026-05-09-admin-app-visual-polish-design.md](../specs/2026-05-09-admin-app-visual-polish-design.md)

**前置依赖：**

- M1+M2 已落地：暖色 token、4 个布局壳、流水线侧栏、12 个 `Ck*` 组件。
- M3 已交付：`DashboardPage.vue` + `CkPipelineHero` + `CkActivityFeed` + `CkTaskList`。
- 不依赖后端变更；不依赖新 API。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run test` 全绿（含本计划新增的 `active-rail-model` / `sidebar-collapse-model` / `brand.js` 测试）。
2. `pnpm --dir frontend/apps/admin-app run build` 通过。
3. `pnpm --dir frontend/apps/admin-app run dev` 启动后：
   - 字号上调后所有 `Ck*` 组件 AA 对比度不退化。
   - 玻璃态卡片在 1280px 与 1024px 上 backdrop-filter 渲染正常。
   - Sidebar v3 折叠/展开动画流畅；连续切换 5 次不出现错位。
   - `prefers-reduced-motion: reduce` 时所有动画退化为瞬时切换。
   - 品牌清理：`grep -rn "CKQA Console" frontend/apps/admin-app/src/` 无结果。
4. Playwright E2E 现有用例全部通过；新增 `sidebar-collapse.spec.js` 通过。

---

## 文件清单

### 修改

```
src/styles/tokens/_typography.scss     # 字号 token 上调 + 新增 display + tracking
src/styles/tokens/_colors.scss         # 新增 glass surface / ambient / highlight 变量（亮+暗）
src/styles/tokens/_motion.scss         # 新增 ease-spring / ease-glass / duration-press / duration-glass
src/styles/tokens/_shadow.scss         # 新增 shadow-card / shadow-card-hover
src/styles/components.scss             # 新增 .ck-glass-card / .ck-pressable / .ck-fallback-pill / .ck-spinner
src/styles/index.scss                  # body::before / body::after ambient 光斑
src/styles/mixins/_typography.scss     # 新增 @mixin hero / @mixin display

src/components/shell/SideNavigation.vue          # v3 完整改造（图标 / rail / status / collapse）
src/components/shell/AppTopbar.vue               # CKQA Console → 智课问答
src/components/shell/command-palette-model.js    # 新增"折叠侧栏"快捷键条目
src/layouts/AuthLayout.vue                       # CKQA Console → 智课问答
src/layouts/ConsoleLayout.vue                    # main 区根节点接 ambient + sidebar collapse --sb-w

src/components/common/CkPageHero.vue             # 字号映射到新 token / 移除 eyebrow 重复
src/components/common/CkPipelineHero.vue         # 改用 .ck-glass-card / 主数字 3xl / 微趋势条
src/components/common/CkActivityFeed.vue         # 改用 .ck-glass-card / 空态接 CkEmptyState
src/components/common/CkTaskList.vue             # 改用 .ck-glass-card / 发光进度条
src/components/common/CkEmptyState.vue           # 字号上调对齐 / 容器 min-height
src/views/dashboard/DashboardPage.vue            # fallback banner 改 pill / 快捷入口卡片化 / hero subtitle 数字化

src/layouts/console-breadcrumb-model.js          # leaf 路由与 section 同名时去重
src/copy/admin.js                                # 新增 dashboard.heroSubtitle / dashboard.quickActions 文案常量
```

### 新建

```
src/copy/brand.js                                  # BRAND 常量（name / tagline / version）
src/copy/brand.test.js                             # 常量形状轻测试
src/components/shell/active-rail-model.js          # rail top 计算 + 动画期保护
src/components/shell/active-rail-model.test.js
src/components/shell/sidebar-collapse-model.js     # 折叠态读写 + 快捷键 + IME 守卫
src/components/shell/sidebar-collapse-model.test.js
src/components/shell/icons/                        # 9 个 sidebar 线性图标 .vue
src/components/common/CkQuickActions.vue           # 工作台快捷入口卡片
e2e/sidebar-collapse.spec.js                       # Playwright 折叠态 E2E
```

### 不动（明确边界）

```
src/api/                # 数据契约不变
src/axios/              # 拦截器不变
src/stores/             # 不新增全局 store（折叠态用局部 ref + localStorage）
src/router/routes.js    # 路径 / 文案不变
```

---

## Task 1：Token 层 — 字号上调 + 玻璃态 + 动效曲线 + 阴影

**Files:**

- Modify: `src/styles/tokens/_typography.scss`
- Modify: `src/styles/tokens/_colors.scss`
- Modify: `src/styles/tokens/_motion.scss`
- Modify: `src/styles/tokens/_shadow.scss`

**目标：** 把设计稿 §4 的全部 token 变更落地，为后续 Task 的样式工具类和组件升级提供变量基础。

- [ ] **Step 1: 字号上调**

`src/styles/tokens/_typography.scss` — 把 type scale 从 11–28 上调到 12–30，新增 display 与 tracking token：

```scss
:root {
  --ckqa-font-sans: "Inter", "DM Sans", "PingFang SC", "Hiragino Sans GB",
                    "Microsoft YaHei", system-ui, sans-serif;
  --ckqa-font-mono: "JetBrains Mono", "DM Mono", "Cascadia Code", ui-monospace, monospace;

  /* 上调后的 type scale —— 小字阶 1px 等差，大字阶按 4–6px 阶梯 */
  --ckqa-text-xs-size:    12px;  --ckqa-text-xs-line:    16px;
  --ckqa-text-sm-size:    13px;  --ckqa-text-sm-line:    18px;
  --ckqa-text-base-size:  14px;  --ckqa-text-base-line:  22px;
  --ckqa-text-md-size:    15px;  --ckqa-text-md-line:    24px;
  --ckqa-text-lg-size:    16px;  --ckqa-text-lg-line:    26px;
  --ckqa-text-xl-size:    20px;  --ckqa-text-xl-line:    28px;
  --ckqa-text-2xl-size:   24px;  --ckqa-text-2xl-line:   32px;
  --ckqa-text-3xl-size:   30px;  --ckqa-text-3xl-line:   38px;
  --ckqa-text-display-size: 36px; --ckqa-text-display-line: 44px;

  /* 字符间距 token（新增） */
  --ckqa-tracking-tight:  -0.4px;
  --ckqa-tracking-normal: 0;
  --ckqa-tracking-wide:   1px;

  --ckqa-fw-regular: 400;
  --ckqa-fw-medium: 500;
  --ckqa-fw-semibold: 600;
}
```

- [ ] **Step 2: 玻璃态 surface + ambient 变量**

`src/styles/tokens/_colors.scss` — 在 `:root` 块末尾追加 glass surface / ambient / highlight token；在 `[data-theme='dark']` 块末尾追加暗色对应：

亮色新增：
```scss
  /* glass surface */
  --ckqa-surface-glass:        rgba(255, 252, 246, 0.65);
  --ckqa-surface-glass-strong: rgba(255, 252, 246, 0.82);
  --ckqa-surface-glass-elev:   rgba(255, 255, 255, 0.88);
  --ckqa-border-glass:         rgba(255, 255, 255, 0.7);
  --ckqa-ambient-warm:         #f4c89e;
  --ckqa-ambient-rust:         #e9b3a3;
  --ckqa-highlight-top: linear-gradient(180deg,
    rgba(255, 255, 255, 0.85) 0%, rgba(255, 255, 255, 0) 30%);
  --ckqa-glass-blur:       12px;
  --ckqa-glass-saturate:   160%;
```

暗色新增：
```scss
  --ckqa-surface-glass:        rgba(37, 34, 29, 0.65);
  --ckqa-surface-glass-strong: rgba(37, 34, 29, 0.82);
  --ckqa-surface-glass-elev:   rgba(46, 42, 36, 0.88);
  --ckqa-border-glass:         rgba(255, 255, 255, 0.06);
  --ckqa-ambient-warm:         #3a2a1c;
  --ckqa-ambient-rust:         #3a2622;
  --ckqa-highlight-top: linear-gradient(180deg,
    rgba(255, 255, 255, 0.06) 0%, rgba(255, 255, 255, 0) 30%);
```

- [ ] **Step 3: 动效曲线扩展**

`src/styles/tokens/_motion.scss` — 在已有 token 之后追加弹簧 / 玻璃 / 按压时长：

```scss
  /* 本期新增 */
  --ckqa-ease-spring:    cubic-bezier(.34, 1.56, .64, 1);
  --ckqa-ease-glass:     cubic-bezier(.2, .8, .2, 1);
  --ckqa-duration-press: 80ms;
  --ckqa-duration-glass: 350ms;
```

- [ ] **Step 4: 双层卡片阴影**

`src/styles/tokens/_shadow.scss` — 新增 `$shadow-card` / `$shadow-card-hover` 及对应 CSS 变量：

```scss
$shadow-card:       0 1px 1px rgb(28 26 23 / 4%), 0 8px 24px rgb(110 70 40 / 6%);
$shadow-card-hover: 0 2px 4px rgb(28 26 23 / 5%), 0 18px 44px rgb(110 70 40 / 12%);

:root {
  /* ... 已有 ... */
  --ckqa-shadow-card:       #{$shadow-card};
  --ckqa-shadow-card-hover: #{$shadow-card-hover};
}
```

- [ ] **Step 5: 验证构建**

```bash
pnpm --dir frontend/apps/admin-app run build
```

确认无 SCSS 编译错误。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/styles/tokens/
git commit -m "style(admin-app): 字号上调 + glass surface + spring 动效 + card 阴影 token"
```

---

## Task 2：通用样式工具类 + ambient 光斑 + prefers-reduced-motion

**Files:**

- Modify: `src/styles/components.scss`
- Modify: `src/styles/index.scss`
- Modify: `src/styles/mixins/_typography.scss`

**目标：** 落地设计稿 §7.1 / §8.1 / §4.4 / §11.4 的通用样式基础设施。

- [ ] **Step 1: `.ck-glass-card` 工具类**

在 `src/styles/components.scss` 末尾追加 `.ck-glass-card` 完整实现（含 `::before` highlight、hover、`is-active`、`@supports not` 退化）。严格按设计稿 §8.1 代码。

- [ ] **Step 2: `.ck-pressable` 工具类**

在 `src/styles/components.scss` 追加 `.ck-pressable`（hover 上浮 1px、active 按压 scale 0.94、focus-visible ring）。严格按设计稿 §7.1 代码。

- [ ] **Step 3: `.ck-fallback-pill` + `.ck-spinner`**

在 `src/styles/components.scss` 追加 fallback pill 与 spinner 样式。严格按设计稿 §8.4 代码。

- [ ] **Step 4: ambient 光斑**

在 `src/styles/index.scss` 追加 `body::before` / `body::after` 两个固定定位渐变光斑 + `.app-shell` z-index 提升。严格按设计稿 §4.4 代码。

- [ ] **Step 5: prefers-reduced-motion 全局退化**

在 `src/styles/components.scss` 末尾追加 `@media (prefers-reduced-motion: reduce)` 块，覆盖所有 transition + keyframe + transform。严格按设计稿 §11.4 代码。

- [ ] **Step 6: typography mixin 扩展**

在 `src/styles/mixins/_typography.scss` 新增 `@mixin ckqa-hero` 和 `@mixin ckqa-display`：

```scss
@mixin ckqa-hero {
  font-size: var(--ckqa-text-3xl-size);
  line-height: var(--ckqa-text-3xl-line);
  font-weight: var(--ckqa-fw-semibold);
  letter-spacing: var(--ckqa-tracking-tight);
}

@mixin ckqa-display {
  font-size: var(--ckqa-text-display-size);
  line-height: var(--ckqa-text-display-line);
  font-weight: var(--ckqa-fw-semibold);
  letter-spacing: var(--ckqa-tracking-tight);
}
```

- [ ] **Step 7: 验证构建**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/styles/
git commit -m "style(admin-app): 新增 .ck-glass-card / .ck-pressable / ambient 光斑 / reduced-motion 退化"
```

---

## Task 3：品牌常量 + 文案替换

**Files:**

- Create: `src/copy/brand.js`
- Create: `src/copy/brand.test.js`
- Modify: `src/components/shell/AppTopbar.vue`
- Modify: `src/layouts/AuthLayout.vue`
- Modify: `src/copy/admin.js`

**目标：** 落地设计稿 §6.3 品牌改名 + §9.1 / §9.3 文案常量。

- [ ] **Step 1: 创建 brand.js**

`src/copy/brand.js`：

```js
export const BRAND = {
  name: '智课问答',
  tagline: '教学知识平台',
  version: __APP_VERSION__,
}
```

- [ ] **Step 2: 创建 brand.test.js**

`src/copy/brand.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { BRAND } from './brand.js'

test('BRAND 导出包含 name / tagline / version', () => {
  assert.equal(BRAND.name, '智课问答')
  assert.equal(BRAND.tagline, '教学知识平台')
  assert.ok('version' in BRAND)
})
```

- [ ] **Step 3: AppTopbar 品牌替换**

`src/components/shell/AppTopbar.vue` — 把所有 `CKQA Console` 文字替换为 `BRAND.name`（从 `brand.js` 导入）。

- [ ] **Step 4: AuthLayout 品牌替换**

`src/layouts/AuthLayout.vue` — 同上。

- [ ] **Step 5: admin.js 新增 dashboard 文案**

在 `src/copy/admin.js` 中新增：

```js
dashboard: {
  heroSubtitle: {
    withTasks: (n, m) => `欢迎回来，今天有 ${n} 个进行中任务 和 ${m} 次本周问答。`,
    noTasks: '暂无进行中任务',
    noQa: '本周还没有问答',
  },
  quickActions: [
    { id: 'create-course',  label: '新建课程',   hint: '从课程目录开始',  icon: 'book',     to: '/app/courses/new' },
    { id: 'upload-pdf',     label: '上传资料',   hint: 'PDF / DOCX 解析', icon: 'file',     to: '/app/courses' },
    { id: 'build-kb',       label: '构建知识库', hint: '4 步引导式向导',  icon: 'database', to: '/app/knowledge/new' },
    { id: 'verify-kb',      label: '验证知识库', hint: '冒烟问答抽样',    icon: 'shield',   to: '/app/qa-smoke' },
  ],
},
```

- [ ] **Step 6: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 7: 品牌 grep 验证**

```bash
grep -rn "CKQA Console" frontend/apps/admin-app/src/
```

预期无结果。再全量搜索 `CKQA` 确认余下结果全在工程上下文（api/、axios/、test fixture）。

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/copy/ \
        frontend/apps/admin-app/src/components/shell/AppTopbar.vue \
        frontend/apps/admin-app/src/layouts/AuthLayout.vue
git commit -m "feat(admin-app): 品牌统一为「智课问答」+ dashboard 文案常量"
```

---

## Task 4：Dashboard 视觉升级 — Hero + Pipeline + Feed + TaskList + QuickActions

**Files:**

- Modify: `src/components/common/CkPageHero.vue`
- Modify: `src/components/common/CkPipelineHero.vue`
- Modify: `src/components/common/CkActivityFeed.vue`
- Modify: `src/components/common/CkTaskList.vue`
- Modify: `src/components/common/CkEmptyState.vue`
- Modify: `src/views/dashboard/DashboardPage.vue`
- Modify: `src/layouts/console-breadcrumb-model.js`
- Create: `src/components/common/CkQuickActions.vue`

**目标：** 落地设计稿 §8.2–§8.4 / §9 的 Dashboard 视觉升级。

- [ ] **Step 1: CkPageHero 字号对齐**

- 标题改用 `--ckqa-text-3xl-*` + `--ckqa-fw-semibold` + `--ckqa-tracking-tight`。
- 副标题改用 `--ckqa-text-md-*`。
- 移除 eyebrow slot 在工作台路由的重复注入（如有）。

- [ ] **Step 2: CkPipelineHero 玻璃态升级**

- 卡片外壳从 `--ckqa-surface` + `border` 改为 `.ck-glass-card`。
- 主数字升到 `--ckqa-text-3xl-size` / 600 / `font-feature-settings: "tnum"`。
- 底部新增微趋势条（6 段渐变条形，数据缺失时不渲染）。
- `is-active` 卡片新增 pulse 圆点。

- [ ] **Step 3: CkActivityFeed 玻璃态升级**

- 外壳改为 `.ck-glass-card`，padding 调到 18px。
- title 用 `--ckqa-text-md-size` / 500。
- 内容字号同步上调到 `--ckqa-text-base-size`。

- [ ] **Step 4: CkTaskList 玻璃态 + 发光进度条**

- 外壳改为 `.ck-glass-card`。
- 进度条改为发光渐变（设计稿 §8.3 `.ck-task-progress` 样式）。

- [ ] **Step 5: CkEmptyState 对齐**

- 图标 32×32、标题 `--ckqa-text-md-size`、副标题 `--ckqa-text-sm-size`。
- 容器 `min-height: 200px`。

- [ ] **Step 6: CkQuickActions 新组件**

创建 `src/components/common/CkQuickActions.vue`：

```vue
<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  actions: { type: Array, required: true },
})
defineEmits(['select'])

const router = useRouter()
function go(action) {
  router.push(action.to)
}
</script>

<template>
  <div class="ck-quick-actions">
    <button
      v-for="a in actions"
      :key="a.id"
      class="ck-glass-card ck-quick-action ck-pressable"
      @click="go(a); $emit('select', a)"
    >
      <span class="qa-icon"><!-- 图标由 icon prop 映射 --></span>
      <span class="qa-label">{{ a.label }}</span>
      <span class="qa-hint">{{ a.hint }}</span>
    </button>
  </div>
</template>
```

Props 契约严格遵循设计稿 §9.3：`actions: Array<{ id, label, hint, icon, to }>`。

- [ ] **Step 7: DashboardPage 接入**

- Fallback banner 从顶部长条改为 hero 下方 `.ck-fallback-pill` inline pill。
- Hero subtitle 改为数字化文案（从 `admin.js` 的 `dashboard.heroSubtitle` 取）。
- 新增 `<CkQuickActions :actions="quickActions" />` 区块。
- 面包屑去重：修改 `console-breadcrumb-model.js`，leaf 路由与 section 同名时不重复输出。

- [ ] **Step 8: 验证构建 + 测试**

```bash
pnpm --dir frontend/apps/admin-app run build
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 9: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/ \
        frontend/apps/admin-app/src/views/dashboard/ \
        frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js
git commit -m "feat(admin-app): Dashboard 玻璃态升级 + CkQuickActions + fallback pill"
```

---

## Task 5：Sidebar v3 — 图标库 + 展开态 + Active Rail

**Files:**

- Create: `src/components/shell/icons/SbIconDashboard.vue`
- Create: `src/components/shell/icons/SbIconBook.vue`
- Create: `src/components/shell/icons/SbIconDatabase.vue`
- Create: `src/components/shell/icons/SbIconChat.vue`
- Create: `src/components/shell/icons/SbIconList.vue`
- Create: `src/components/shell/icons/SbIconShield.vue`
- Create: `src/components/shell/icons/SbIconUsers.vue`
- Create: `src/components/shell/icons/SbIconHeart.vue`
- Create: `src/components/shell/icons/SbIconFile.vue`
- Create: `src/components/shell/active-rail-model.js`
- Create: `src/components/shell/active-rail-model.test.js`
- Modify: `src/components/shell/SideNavigation.vue`

**目标：** 落地设计稿 §5.1–§5.4 / §5.6 / §6.2 的 Sidebar v3 展开态完整重写。

- [ ] **Step 1: 9 个图标组件**

在 `src/components/shell/icons/` 下创建 9 个 Vue SFC，每个导出一个 24×24 viewBox / `stroke-width: 1.6` / `currentColor` 的 inline SVG。图标语义按设计稿 §5.3 表格：

| 文件名 | 路由 | 图标语义 |
| --- | --- | --- |
| `SbIconDashboard.vue` | 工作台 | 田字格仪表盘 |
| `SbIconBook.vue` | 课程 | 闭合书本 + 内文横线 |
| `SbIconDatabase.vue` | 知识库 | 数据库圆柱（椭圆 + 二次贝塞尔） |
| `SbIconChat.vue` | 问答会话 | 对话气泡 |
| `SbIconList.vue` | 检索日志 | 横线列表 |
| `SbIconShield.vue` | 知识库验证 | 盾牌 + 对勾 |
| `SbIconUsers.vue` | 用户与权限 | 双人 |
| `SbIconHeart.vue` | 系统健康 | 心电波形 |
| `SbIconFile.vue` | 审计日志 | 文件 + 横线 |

SVG path 使用绝对坐标指令（避免相对弧线渲染断点问题）。

- [ ] **Step 2: active-rail-model 测试**

`src/components/shell/active-rail-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { computeRailTop, RAIL_HEIGHT_EXPANDED, RAIL_HEIGHT_COLLAPSED } from './active-rail-model.js'

test('computeRailTop 返回 active item 相对 list 的 top 偏移', () => {
  // mock: list top=100, active item top=160, item height=36
  const result = computeRailTop(
    { top: 100 },
    { top: 160, height: 36 },
    false,
  )
  assert.equal(result, 60) // 160 - 100
})

test('computeRailTop 折叠态使用 RAIL_HEIGHT_COLLAPSED', () => {
  const result = computeRailTop(
    { top: 50 },
    { top: 130, height: 40 },
    true,
  )
  assert.equal(result, 80) // 130 - 50
})

test('computeRailTop 无 active item 时返回 0', () => {
  assert.equal(computeRailTop({ top: 100 }, null, false), 0)
})

test('RAIL_HEIGHT_EXPANDED 为 36', () => {
  assert.equal(RAIL_HEIGHT_EXPANDED, 36)
})

test('RAIL_HEIGHT_COLLAPSED 为 40', () => {
  assert.equal(RAIL_HEIGHT_COLLAPSED, 40)
})
```

- [ ] **Step 3: active-rail-model 实现**

`src/components/shell/active-rail-model.js`：

```js
export const RAIL_HEIGHT_EXPANDED = 36
export const RAIL_HEIGHT_COLLAPSED = 40
export const RAIL_WIDTH = 3

/**
 * 计算 rail 的 top 偏移（相对 list 容器）。
 * @param {DOMRect|{top:number}} listRect - .sb-list 的 rect
 * @param {DOMRect|{top:number,height:number}|null} activeRect - 当前 active item 的 rect
 * @param {boolean} collapsed - 是否折叠态
 * @returns {number} rail 应设置的 top 值（px）
 */
export function computeRailTop(listRect, activeRect, collapsed) {
  if (!activeRect) return 0
  return activeRect.top - listRect.top
}

/**
 * 获取 rail 高度。
 */
export function getRailHeight(collapsed) {
  return collapsed ? RAIL_HEIGHT_COLLAPSED : RAIL_HEIGHT_EXPANDED
}
```

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: SideNavigation.vue v3 展开态重写**

完整重写 `src/components/shell/SideNavigation.vue`，实现设计稿 §5 的展开态结构：

- Brand row：mark（34×34 圆角 10）+ 主标"智课问答" + 副标"教学知识平台 · v{version}" + toggle 按钮。
- Section 分组：`工作台 / 生产 / 运维 / 设置`，section title 用 xs / 600 / tracking-wide / uppercase。
- Nav item：36px 高 / padding 8/10 / 圆角 9 / 图标 + 文字 + 可选 count badge。
- Active rail：`position: absolute; left: -12px; width: 3px; height: 36px;` 渐变 accent → accent-strong + 外发光。
- Status card：底部用户身份卡（头像 + 用户名 + 角色 + health pills）。
- 使用 `useActiveRail` composable（从 `active-rail-model.js` 导入 `computeRailTop`），监听路由变化重算 rail top。
- Rail 首次挂载 `opacity: 0`，首测成功后 `opacity: 1`。
- 容器约束：`.sb-list { position: relative; overflow: visible; }`、`.sb-body { overflow-x: visible; overflow-y: auto; }`、`.sidebar { overflow: visible; }`。

**关键实现细节（设计稿 §15 必读）：**

- `.sb-list` 必须 `position: relative; overflow: visible;` —— rail 用 `position: absolute; left: -12px;` 相对 `.sb-list` 定位。
- `.sb-body { overflow-x: visible; overflow-y: auto; }` —— x 必须 visible，否则展开态 rail 被裁切。
- Rail 监听路由变化时用 `router.afterEach(() => { nextTick().then(recompute); })`。

- [ ] **Step 6: 验证构建 + 测试**

```bash
pnpm --dir frontend/apps/admin-app run build
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/icons/ \
        frontend/apps/admin-app/src/components/shell/active-rail-model.js \
        frontend/apps/admin-app/src/components/shell/active-rail-model.test.js \
        frontend/apps/admin-app/src/components/shell/SideNavigation.vue
git commit -m "feat(admin-app): Sidebar v3 展开态 — 图标 + active rail + status card"
```

---

## Task 6：Sidebar v3 — 折叠态 + 快捷键 + ConsoleLayout 联动

**Files:**

- Create: `src/components/shell/sidebar-collapse-model.js`
- Create: `src/components/shell/sidebar-collapse-model.test.js`
- Modify: `src/components/shell/SideNavigation.vue`
- Modify: `src/components/shell/command-palette-model.js`
- Modify: `src/layouts/ConsoleLayout.vue`

**目标：** 落地设计稿 §5.5 / §6.1 / §7.2 / §7.3 的折叠态完整规则。

- [ ] **Step 1: sidebar-collapse-model 测试**

`src/components/shell/sidebar-collapse-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  STORAGE_KEY,
  readCollapsed,
  writeCollapsed,
  isToggleKey,
} from './sidebar-collapse-model.js'

test('STORAGE_KEY 为 ckqa.sidebar.collapsed', () => {
  assert.equal(STORAGE_KEY, 'ckqa.sidebar.collapsed')
})

test('readCollapsed 从 localStorage 读取', () => {
  // mock localStorage
  const store = {}
  const mockStorage = {
    getItem: (k) => store[k] ?? null,
    setItem: (k, v) => { store[k] = v },
  }
  assert.equal(readCollapsed(mockStorage), false)
  mockStorage.setItem(STORAGE_KEY, '1')
  assert.equal(readCollapsed(mockStorage), true)
})

test('writeCollapsed 写入 localStorage', () => {
  const store = {}
  const mockStorage = {
    getItem: (k) => store[k] ?? null,
    setItem: (k, v) => { store[k] = v },
    removeItem: (k) => { delete store[k] },
  }
  writeCollapsed(true, mockStorage)
  assert.equal(store[STORAGE_KEY], '1')
  writeCollapsed(false, mockStorage)
  assert.equal(store[STORAGE_KEY], undefined)
})

test('isToggleKey 识别 Ctrl+\\ 和 Meta+\\', () => {
  assert.equal(isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, isComposing: false, keyCode: 220 }), true)
  assert.equal(isToggleKey({ key: '\\', ctrlKey: false, metaKey: true, isComposing: false, keyCode: 220 }), true)
  assert.equal(isToggleKey({ key: '\\', ctrlKey: false, metaKey: false, isComposing: false, keyCode: 220 }), false)
})

test('isToggleKey 在 IME 合成中返回 false', () => {
  assert.equal(isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, isComposing: true, keyCode: 220 }), false)
  assert.equal(isToggleKey({ key: '\\', ctrlKey: true, metaKey: false, isComposing: false, keyCode: 229 }), false)
})
```

- [ ] **Step 2: sidebar-collapse-model 实现**

`src/components/shell/sidebar-collapse-model.js`：

```js
export const STORAGE_KEY = 'ckqa.sidebar.collapsed'

export function readCollapsed(storage = localStorage) {
  try {
    return storage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

export function writeCollapsed(collapsed, storage = localStorage) {
  try {
    if (collapsed) {
      storage.setItem(STORAGE_KEY, '1')
    } else {
      storage.removeItem(STORAGE_KEY)
    }
  } catch { /* noop */ }
}

export function isToggleKey(event) {
  if (event.isComposing || event.keyCode === 229) return false
  if (event.key !== '\\') return false
  return event.ctrlKey || event.metaKey
}
```

- [ ] **Step 3: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 4: SideNavigation 折叠态 CSS + 逻辑**

在 `SideNavigation.vue` 中接入折叠态：

- 内部 `const collapsed = ref(readCollapsed())`。
- `provide('sidebarCollapsed', collapsed)` 暴露给 ConsoleLayout。
- `onMounted` 注册 `window` keydown 监听（`isToggleKey` 守卫）。
- `onBeforeUnmount` 清理监听。
- 折叠态 CSS 严格按设计稿 §5.5（`.sidebar.is-collapsed` 全套规则 + `@keyframes collapse-pop` + `@keyframes breathe-glow`）。
- Toggle 按钮样式按设计稿 §7.2（展开态嵌 brand row、折叠态 `position: absolute; top: 23px; right: -13px;`）。
- 折叠态导航项交互按设计稿 §7.3。
- `useActiveRail` 监听 `collapsed` 变化时 `await setTimeout(360)` 后再重算（设计稿 §6.2 动画期保护）。

- [ ] **Step 5: ConsoleLayout 联动**

`src/layouts/ConsoleLayout.vue`：

- `inject('sidebarCollapsed')` 获取折叠态。
- 根节点挂 `:data-sb-collapsed="collapsed"`。
- main 区用 CSS 变量 `--sb-w` 驱动 `padding-left`：展开 240px、折叠 64px。
- sidebar 切换折叠时同步写 `--sb-w`（避免依赖 `:has()`）。

- [ ] **Step 6: command-palette-model 新增条目**

在 `src/components/shell/command-palette-model.js` 的命令列表中新增：

```js
{ id: 'toggle-sidebar', label: '折叠侧栏', shortcut: '⌘ \\', section: '导航' }
```

- [ ] **Step 7: 验证构建 + 测试**

```bash
pnpm --dir frontend/apps/admin-app run build
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/sidebar-collapse-model.js \
        frontend/apps/admin-app/src/components/shell/sidebar-collapse-model.test.js \
        frontend/apps/admin-app/src/components/shell/SideNavigation.vue \
        frontend/apps/admin-app/src/components/shell/command-palette-model.js \
        frontend/apps/admin-app/src/layouts/ConsoleLayout.vue
git commit -m "feat(admin-app): Sidebar v3 折叠态 + ⌘\\ 快捷键 + ConsoleLayout 联动"
```

---

## Task 7：测试与走查

**Files:**

- Create: `e2e/sidebar-collapse.spec.js`
- Modify: `src/app-shell.test.js`

**目标：** 落地设计稿 §11 的测试与验收清单。

- [ ] **Step 1: app-shell.test.js 补充 smoke 断言**

追加对新模块的 import smoke 测试：

```js
import { BRAND } from './copy/brand.js'
import { computeRailTop, RAIL_HEIGHT_EXPANDED } from './components/shell/active-rail-model.js'
import { readCollapsed, isToggleKey } from './components/shell/sidebar-collapse-model.js'

test('brand.js exports BRAND with name', () => {
  assert.equal(BRAND.name, '智课问答')
})

test('active-rail-model exports computeRailTop', () => {
  assert.equal(typeof computeRailTop, 'function')
  assert.equal(RAIL_HEIGHT_EXPANDED, 36)
})

test('sidebar-collapse-model exports readCollapsed + isToggleKey', () => {
  assert.equal(typeof readCollapsed, 'function')
  assert.equal(typeof isToggleKey, 'function')
})
```

- [ ] **Step 2: Playwright sidebar-collapse.spec.js**

`e2e/sidebar-collapse.spec.js`：

```js
import { test, expect } from '@playwright/test'

test.describe('Sidebar 折叠态', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/app/dashboard')
    await page.waitForSelector('[data-test-id="sidebar"]')
  })

  test('点击 toggle 按钮折叠 sidebar', async ({ page }) => {
    await page.click('[data-test-id="sb-toggle"]')
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)
    // main 区宽度变化
    const mainWidth = await page.locator('[data-test-id="main-content"]').boundingBox()
    expect(mainWidth.width).toBeGreaterThan(900)
  })

  test('再次点击展开 sidebar', async ({ page }) => {
    await page.click('[data-test-id="sb-toggle"]')
    await page.click('[data-test-id="sb-toggle"]')
    await expect(page.locator('[data-test-id="sidebar"]')).not.toHaveClass(/is-collapsed/)
  })

  test('Ctrl+\\ 快捷键切换折叠', async ({ page }) => {
    await page.keyboard.press('Control+\\')
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)
    await page.keyboard.press('Control+\\')
    await expect(page.locator('[data-test-id="sidebar"]')).not.toHaveClass(/is-collapsed/)
  })

  test('刷新后折叠状态保留', async ({ page }) => {
    await page.click('[data-test-id="sb-toggle"]')
    await page.reload()
    await page.waitForSelector('[data-test-id="sidebar"]')
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)
  })

  test('active rail 在路由切换后仍指向当前路由', async ({ page }) => {
    await page.click('[data-test-id="sb-toggle"]')
    // 切换到课程页
    await page.click('[data-test-id="nav-courses"]')
    await page.waitForURL('**/courses')
    const rail = page.locator('[data-test-id="active-rail"]')
    await expect(rail).toBeVisible()
  })
})
```

- [ ] **Step 3: 跑全部测试**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run test:e2e
```

- [ ] **Step 4: 验收清单核对**

逐项核对设计稿 §11.3 的 13 项验收清单：

1. `pnpm run build` 通过、`pnpm run dev` 无控制台报错。
2. 字号上调后 AA 对比度不退化。
3. Sidebar 折叠/展开连续 5 次无错位。
4. 玻璃态 backdrop-filter 在 1280px 和 1024px 正常。
5. `prefers-reduced-motion: reduce` 时动画退化。
6. 品牌 grep 验证通过。
7. Active rail 跨 section 切换平滑、首次挂载渐显。
8. Hero subtitle 显示真实数字。
9. Fallback banner 改为 inline pill。
10. 折叠态 toggle `top: 23px` 与 mark 不重叠。
11. 折叠态图标渲染清晰无断点。
12. 快捷键 `⌘ \` / `Ctrl \` 在中文输入法激活时不误触。
13. 暗色模式切换无脏色。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/app-shell.test.js \
        frontend/apps/admin-app/e2e/sidebar-collapse.spec.js
git commit -m "test(admin-app): 视觉打磨 smoke 测试 + sidebar 折叠 E2E"
```

---

## 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| 字号上调后部分既存页面版式溢出 | 上调步进保守（1px 等差）；ModulePage 内联硬编码字号前置 patch 为 var |
| `backdrop-filter` 在低端 GPU 性能差 | `@supports not` 退化到实色 surface |
| Sidebar 折叠态对 ConsoleLayout 主区宽度计算有影响 | CSS 变量 `--sb-w` 驱动 main padding-left |
| ambient 光斑对暗色过亮 | `[data-theme='dark']` 对应变量降到 0.15–0.2 不透明度 |
| Active rail 滑动与路由切换时机错位 | `nextTick` 后读 rect；折叠切换等 360ms 后再重算 |
| 旧 E2E 用 CSS class 选择器会失效 | 新 selector 全部用 `data-test-id` |

---

## 时间估算

| 阶段 | 对应 Task | 预估 |
| --- | --- | --- |
| P1 Token & Brand | Task 1 + Task 3 | 半天 |
| P2 通用样式工具类 | Task 2 | 半天 |
| P3 Dashboard 与 Hero | Task 4 | 1 天 |
| P4a Sidebar v3 — 图标与 rail | Task 5 | 1 天 |
| P4b Sidebar v3 — 折叠与状态 | Task 6 | 半天 |
| P5 测试与走查 | Task 7 | 半天 |

合计约 **3.5 工作日**。
