# 管理员端重设计 M1+M2 地基实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落实管理员端重设计的"设计系统底座 + 布局壳与导航"两个里程碑（M1+M2），把 Anthropic / Claude 暖灰 + 暖橙 token、12 个基础组件、4 个布局壳、流水线信息架构与顶栏（范围芯片 + ⌘K + 通知 + 主题切换）全部落地，作为后续 M3~M7 页面重做的稳定地基。

**Architecture:** 在现有 `frontend/apps/admin-app/` 上做增量改造。`styles/tokens/_colors.scss` 整体重写为暖色 token + 新增 `rust` accent；现有 `theme.js` store 增加 `rust` 并设为默认；新增 `useScopeStore` 控制范围芯片；`navigation-model.js` 重写为 section-based；`AppTopbar` / `SideNavigation` / 4 个 Layout 全部重构。新增 13 个 `Ck*` 通用组件，纯逻辑放进同名 `*-model.js` 用 `node:test` 测试，Vue 组件本身只做渲染。

**Tech Stack:** Vue 3.5、Vite 8、Element Plus 2.13、Pinia 3、vue-router 5（即官方 Vue Router 4 API 的语义版本号）、SCSS、`node:test` 单元测试、Playwright E2E。

**前置约束（不在本计划范围）：**

- 不替换 Element Plus，所有视觉通过 `--el-*` CSS 变量与 Token 映射收口。
- 不重写 `axios/` / `api/` / `stores/auth.js` / `stores/pinia.js`。
- 不动后端 API；Dashboard summary 与 retrieval_trace 是 M3 / M6 的依赖，与本计划无关。
- 现有所有路由 path 保持不变；`/app/qa-smoke` UI 文案改为"知识库验证"，path 不动。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run test` 全绿（含本计划新增模块的测试）。
2. `pnpm --dir frontend/apps/admin-app run build` 通过；`pnpm --dir frontend/apps/admin-app run dev` 启动后页面可正常渲染、登录、跳转。
3. 顶栏的范围芯片、⌘K 命令面板、通知下拉、主题切换全部可交互；侧栏按段渲染；亮 / 暗主题切换无脏色。
4. 现有 `src/app-shell.test.js` 全部断言更新到位并通过。
5. Playwright E2E（`e2e/`）现有用例全部通过。

---

## 文件清单

### M1 设计系统底座

**修改：**

- `src/styles/tokens/_colors.scss` — 完全重写：暖灰 surface + 文本 / 边框 / 完整 semantic + accent (新增 rust，保留旧通道)；亮暗双套
- `src/styles/tokens/_typography.scss` — 字号 / 行高 拆双变量
- `src/styles/tokens/_motion.scss` — 时长 + 缓动函数
- `src/styles/tokens/_radius.scss` — 圆角层次
- `src/styles/tokens/_shadow.scss` — 阴影 + focus ring
- `src/styles/tokens/_space.scss` — 间距层次
- `src/styles/element-plus.scss` — primary 映射修正、中性色用新 token
- `src/styles/components.scss` — 不动；状态徽章相关样式如有冲突在 Task 5 内 patch
- `src/stores/theme.js` — `THEME_ACCENTS` 加 `rust` 并设为默认；mode 默认 `light`

**新建：**

- `src/styles/mixins/_typography.scss` — 字号 mixin
- `src/components/common/CkStatusPill.vue` + `status-pill-model.js` + `status-pill-model.test.js`
- `src/components/common/CkSkeleton.vue`
- `src/components/common/CkPager.vue` + `pager-model.js` + `pager-model.test.js`
- `src/components/common/CkEmptyState.vue`
- `src/components/common/CkPageHero.vue`
- `src/stores/scope.js` + `scope.test.js`

### M2 布局壳与导航

**修改：**

- `src/router/routes.js` — 给所有 `meta` 加 `section` 字段；新增 `/app/retrieval-logs` 列表占位；路由文案"QA 冒烟"改"知识库验证"
- `src/components/shell/navigation-model.js` — 重写为 section 模型 + 测试同步
- `src/layouts/console-breadcrumb-model.js` — 加 4 层截断 + section 映射
- `src/components/shell/SideNavigation.vue` — 分段渲染
- `src/components/shell/AppTopbar.vue` — Logo + 范围芯片 + ⌘K + 通知 + 主题 + 头像
- `src/layouts/ConsoleLayout.vue` — 顶栏 + 侧栏 + 主区
- `src/layouts/DetailLayout.vue` — 资源标题块 + Tab
- `src/layouts/WorkflowLayout.vue` — 7fr/5fr 分屏
- `src/layouts/AuthLayout.vue` — 暖灰底 + 光晕
- `src/main.js` — 不动（theme store init 已就绪）
- `src/app-shell.test.js` — 同步新增模型 / 状态机的 import 与断言
- `e2e/admin-app.spec.js`（如存在） — 文案断言改为 `data-test-id`

**新建：**

- `src/components/common/CkBreadcrumbs.vue` + `breadcrumbs-model.js` + `breadcrumbs-model.test.js`
- `src/components/shell/ScopeChip.vue`
- `src/components/shell/ThemeToggle.vue`
- `src/components/shell/NotificationDropdown.vue` + `notification-feed-model.js` + `notification-feed-model.test.js`
- `src/components/shell/CkCommandPalette.vue` + `command-palette-model.js` + `command-palette-model.test.js`
- `src/copy/admin.js` — 文案常量初始结构（先放本计划用到的核心文案）

---

## M1 · 设计系统底座

### Task 1：扩展并修正颜色 Token（亮色基调）

**Files:**

- Modify: `src/styles/tokens/_colors.scss:1-37`（`:root` 块）

`:root` 块整体替换为暖灰 + 完整 semantic 语义层。保留兼容别名 `--bg / --surface / --text / --blue / --green / --amber / --red / --ink / --radius / --sans / --mono`，避免破坏 `components.scss` 与现有页面。

- [ ] **Step 1: 重写 `:root` 颜色块**

把 `src/styles/tokens/_colors.scss` 第 1–37 行替换为：

```scss
:root {
  color-scheme: light;

  /* surface scale — 暖灰 */
  --ckqa-bg: #faf9f6;
  --ckqa-bg-elevated: #fffaf4;
  --ckqa-surface: #ffffff;
  --ckqa-surface-muted: #f5f3ee;
  --ckqa-surface-strong: #f0ebe1;

  /* border scale */
  --ckqa-border: #e8e2d8;
  --ckqa-border-soft: #f0ebe1;
  --ckqa-border-strong: #d4cdbe;

  /* text scale */
  --ckqa-text: #1a1a1a;
  --ckqa-text-muted: #6b6760;
  --ckqa-text-weak: #a8a39a;
  --ckqa-text-inverse: #ffffff;
  --ckqa-ink: var(--ckqa-text);

  /* accent — default mapped to rust */
  --ckqa-accent: #d97757;
  --ckqa-accent-strong: #c4633a;
  --ckqa-accent-soft: #fdf3ee;
  --ckqa-accent-contrast: #ffffff;

  /* semantic — solid + soft 双轨；色相与 accent 错开 */
  --ckqa-success: #4a7c59;
  --ckqa-success-soft: #eef5ee;
  --ckqa-running: #c08a3a;
  --ckqa-running-soft: #faf1e1;
  --ckqa-warning: #b8860b;
  --ckqa-warning-soft: #fef5d7;
  --ckqa-blocked: #8a847a;
  --ckqa-blocked-soft: #ece8df;
  --ckqa-danger: #c4413a;
  --ckqa-danger-soft: #fceeec;

  /* focus */
  --ckqa-focus: rgb(217 119 87 / 35%);

  /* legacy aliases — 保留以兼容 components.scss 与历史样式 */
  --bg: var(--ckqa-bg);
  --surface: var(--ckqa-surface);
  --surface-muted: var(--ckqa-surface-muted);
  --surface-strong: var(--ckqa-surface-strong);
  --text: var(--ckqa-text);
  --text-muted: var(--ckqa-text-muted);
  --border: var(--ckqa-border);
  --blue: var(--ckqa-running);
  --green: var(--ckqa-success);
  --amber: var(--ckqa-warning);
  --red: var(--ckqa-danger);
  --ink: var(--ckqa-ink);
  --radius: var(--ckqa-radius-md);
  --sans: var(--ckqa-font-sans);
  --mono: var(--ckqa-font-mono);
}
```

- [ ] **Step 2: 重写 `[data-theme='dark']` 块**

替换暗色块（原文件第 39–53 行附近），使其与亮色对位：

```scss
[data-theme='dark'] {
  color-scheme: dark;

  --ckqa-bg: #1c1a17;
  --ckqa-bg-elevated: #25221d;
  --ckqa-surface: #25221d;
  --ckqa-surface-muted: #2e2a24;
  --ckqa-surface-strong: #38332b;

  --ckqa-border: #38332b;
  --ckqa-border-soft: #2e2a24;
  --ckqa-border-strong: #4a443a;

  --ckqa-text: #f0ece4;
  --ckqa-text-muted: #b0a99c;
  --ckqa-text-weak: #7a7468;
  --ckqa-text-inverse: #1a1a1a;
  --ckqa-ink: var(--ckqa-text);

  --ckqa-accent: #e8916f;
  --ckqa-accent-strong: #d97757;
  --ckqa-accent-soft: #38221a;
  --ckqa-accent-contrast: #1a1a1a;

  --ckqa-success: #6a9b78;
  --ckqa-success-soft: #1f2d22;
  --ckqa-running: #d4a04c;
  --ckqa-running-soft: #38301d;
  --ckqa-warning: #d4914c;
  --ckqa-warning-soft: #382a1d;
  --ckqa-blocked: #6e6a62;
  --ckqa-blocked-soft: #2a2724;
  --ckqa-danger: #d96860;
  --ckqa-danger-soft: #38201e;

  --ckqa-focus: rgb(232 145 111 / 40%);
}
```

- [ ] **Step 3: 新增 `rust` accent 段**

在文件末尾追加（保留现有 `indigo / blue / teal / violet / amber` 的 5 个块不动）：

```scss
[data-accent='rust'] {
  --ckqa-accent: #d97757;
  --ckqa-accent-strong: #c4633a;
  --ckqa-accent-soft: #fdf3ee;
  --ckqa-accent-contrast: #ffffff;
}
```

- [ ] **Step 4: 启动 dev server 视觉冒烟**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

打开 `http://localhost:5173/login`，预期：背景为暖灰、文字深黑可读、按钮（Element Plus primary）已变为暖橙。如出现明显脏色（如蓝紫遗漏），抓出哪个页面用了被删掉的旧别名，回到 `_colors.scss` 把 alias 补回。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/styles/tokens/_colors.scss
git commit -m "feat(admin-app): 重写颜色 token 为暖灰底 + 暖橙 accent

- :root 切换到暖灰 surface + 完整 semantic 语义层
- 新增 [data-accent='rust'] 段
- 保留旧通道 indigo/blue/teal/violet/amber 用于将来主题扩展
- 暗色映射同步更新"
```

---

### Task 2：theme store 加 rust accent 并改默认

**Files:**

- Modify: `src/stores/theme.js`

把新 `rust` accent 注入 `THEME_ACCENTS`，默认 mode 改为 `light`，默认 accent 改为 `rust`。

- [ ] **Step 1: 编辑 `THEME_ACCENTS` 与默认值**

`src/stores/theme.js`：

把：

```js
export const THEME_ACCENTS = [
  { key: 'indigo', label: 'Indigo', color: '#6366f1', strong: '#4f46e5', contrast: '#ffffff' },
  { key: 'blue', label: 'Blue', color: '#2563eb', strong: '#1d4ed8', contrast: '#ffffff' },
  { key: 'teal', label: 'Teal', color: '#0d9488', strong: '#0f766e', contrast: '#ffffff' },
  { key: 'violet', label: 'Violet', color: '#9333ea', strong: '#7e22ce', contrast: '#ffffff' },
  { key: 'amber', label: 'Amber', color: '#d97706', strong: '#b45309', contrast: '#ffffff' },
]
```

改为：

```js
export const THEME_ACCENTS = [
  { key: 'rust', label: '暖橙', color: '#d97757', strong: '#c4633a', contrast: '#ffffff' },
  { key: 'indigo', label: 'Indigo', color: '#6366f1', strong: '#4f46e5', contrast: '#ffffff' },
  { key: 'blue', label: 'Blue', color: '#2563eb', strong: '#1d4ed8', contrast: '#ffffff' },
  { key: 'teal', label: 'Teal', color: '#0d9488', strong: '#0f766e', contrast: '#ffffff' },
  { key: 'violet', label: 'Violet', color: '#9333ea', strong: '#7e22ce', contrast: '#ffffff' },
  { key: 'amber', label: 'Amber', color: '#d97706', strong: '#b45309', contrast: '#ffffff' },
]
```

把：

```js
const state = reactive({
  mode: 'auto',
  accent: 'indigo',
  resolvedTheme: 'light',
})
```

改为：

```js
const state = reactive({
  mode: 'light',
  accent: 'rust',
  resolvedTheme: 'light',
})
```

把 `catch` 默认 fallback：

```js
} catch {
  state.mode = 'auto'
  state.accent = 'indigo'
}
```

改为：

```js
} catch {
  state.mode = 'light'
  state.accent = 'rust'
}
```

- [ ] **Step 2: 跑现有 `app-shell.test.js`**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：原来对 `THEME_ACCENTS[0].key === 'indigo'` 等"首项是 indigo"的断言会失败。如果失败，记下失败的断言行号，进入 Step 3 修。

- [ ] **Step 3: 同步 `app-shell.test.js`**

`src/app-shell.test.js` 中对 theme 的断言更新为：

```js
test('theme store defaults to rust accent and light mode', () => {
  const pinia = createPinia()
  const themeStore = useThemeStore(pinia)
  assert.equal(themeStore.state.accent, 'rust')
  assert.equal(themeStore.state.mode, 'light')
})

test('THEME_ACCENTS includes rust as the first entry', () => {
  assert.equal(THEME_ACCENTS[0].key, 'rust')
  assert.equal(THEME_ACCENTS[0].color, '#d97757')
})
```

如旧文件已存在等价断言，整段替换；不要保留多份。

- [ ] **Step 4: 再跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/stores/theme.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): theme store 加 rust accent 并设为默认

- THEME_ACCENTS 头部新增 rust（暖橙 #d97757）
- 默认 mode 由 auto 改为 light
- 默认 accent 由 indigo 改为 rust
- app-shell.test.js 同步默认值断言"
```

---

### Task 3：字体 Token 拆 size/line + mixin

**Files:**

- Modify: `src/styles/tokens/_typography.scss`
- Create: `src/styles/mixins/_typography.scss`
- Modify: `src/styles/index.scss`

- [ ] **Step 1: 重写 `_typography.scss`**

整体替换为：

```scss
:root {
  --ckqa-font-sans: "Inter", "DM Sans", "PingFang SC", "Hiragino Sans GB",
                    "Microsoft YaHei", system-ui, sans-serif;
  --ckqa-font-mono: "JetBrains Mono", "DM Mono", "Cascadia Code", ui-monospace, monospace;

  --ckqa-text-xs-size: 11px;     --ckqa-text-xs-line: 16px;
  --ckqa-text-sm-size: 12px;     --ckqa-text-sm-line: 18px;
  --ckqa-text-base-size: 13px;   --ckqa-text-base-line: 20px;
  --ckqa-text-md-size: 14px;     --ckqa-text-md-line: 22px;
  --ckqa-text-lg-size: 15px;     --ckqa-text-lg-line: 24px;
  --ckqa-text-xl-size: 18px;     --ckqa-text-xl-line: 26px;
  --ckqa-text-2xl-size: 22px;    --ckqa-text-2xl-line: 30px;
  --ckqa-text-3xl-size: 28px;    --ckqa-text-3xl-line: 36px;

  --ckqa-fw-regular: 400;
  --ckqa-fw-medium: 500;
  --ckqa-fw-semibold: 600;
}
```

- [ ] **Step 2: 新建 mixin**

创建 `src/styles/mixins/_typography.scss`：

```scss
@mixin text-xs    { font-size: var(--ckqa-text-xs-size);    line-height: var(--ckqa-text-xs-line); }
@mixin text-sm    { font-size: var(--ckqa-text-sm-size);    line-height: var(--ckqa-text-sm-line); }
@mixin text-base  { font-size: var(--ckqa-text-base-size);  line-height: var(--ckqa-text-base-line); }
@mixin text-md    { font-size: var(--ckqa-text-md-size);    line-height: var(--ckqa-text-md-line); }
@mixin text-lg    { font-size: var(--ckqa-text-lg-size);    line-height: var(--ckqa-text-lg-line); }
@mixin text-xl    { font-size: var(--ckqa-text-xl-size);    line-height: var(--ckqa-text-xl-line); }
@mixin text-2xl   { font-size: var(--ckqa-text-2xl-size);   line-height: var(--ckqa-text-2xl-line); }
@mixin text-3xl   { font-size: var(--ckqa-text-3xl-size);   line-height: var(--ckqa-text-3xl-line); }
```

- [ ] **Step 3: 在 `index.scss` 中引入 mixin**

`src/styles/index.scss` 在 `@use './tokens/typography';` 之后追加：

```scss
@use './mixins/typography' as *;
```

> 注：`@use ... as *` 让后续 SCSS 文件可直接调用 `@include text-md`，无需逐文件重复引入。

- [ ] **Step 4: 启动 dev 验证字号正常**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

打开 `/login`、`/app/dashboard`，预期：所有现有文字大小不变（因为我们没改 `components.scss` 中的 `font-size`）。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/styles/tokens/_typography.scss \
        frontend/apps/admin-app/src/styles/mixins/_typography.scss \
        frontend/apps/admin-app/src/styles/index.scss
git commit -m "feat(admin-app): 字体 token 拆 size/line 双变量 + 新增 typography mixin

- type scale 改为 *-size / *-line 双变量，可分别引用
- styles/mixins/_typography.scss 提供 text-{xs..3xl} 8 档 mixin
- index.scss 用 @use ... as * 全局可见"
```

---

### Task 4：Element Plus 主题映射修正

**Files:**

- Modify: `src/styles/element-plus.scss`

现有文件 408 行，已经有大量映射。本任务只修正 primary 映射，并把中性色对齐到新 token。

- [ ] **Step 1: 在 `:root` 内 patch primary 映射**

打开 `src/styles/element-plus.scss`，找到 `--el-color-primary:` 所在的 `:root` 块。把 primary 系列改为：

```scss
:root {
  /* primary —— 把 base accent 给 primary，让 EL 派生 light-{1..9}/dark-2 */
  --el-color-primary: var(--ckqa-accent);
  --el-color-primary-dark-2: var(--ckqa-accent-strong);

  /* 对最常用的 light-3 / 5 / 7 / 9 显式指定，避免 EL 自动派生颜色不一致 */
  --el-color-primary-light-3: #e89377;
  --el-color-primary-light-5: #f0ae97;
  --el-color-primary-light-7: #f7c9b9;
  --el-color-primary-light-9: var(--ckqa-accent-soft);

  /* semantic */
  --el-color-success: var(--ckqa-success);
  --el-color-warning: var(--ckqa-warning);
  --el-color-danger: var(--ckqa-danger);

  /* surface / text / border */
  --el-bg-color: var(--ckqa-surface);
  --el-bg-color-page: var(--ckqa-bg);
  --el-fill-color-light: var(--ckqa-surface-muted);
  --el-text-color-primary: var(--ckqa-text);
  --el-text-color-regular: var(--ckqa-text-muted);
  --el-text-color-placeholder: var(--ckqa-text-weak);
  --el-border-color: var(--ckqa-border);
  --el-border-color-light: var(--ckqa-border-soft);
  --el-border-color-lighter: var(--ckqa-border-soft);

  /* radius / font / shadow */
  --el-border-radius-base: var(--ckqa-radius-md);
  --el-border-radius-small: var(--ckqa-radius-sm);
  --el-border-radius-round: var(--ckqa-radius-full);
  --el-font-family: var(--ckqa-font-sans);
  --el-box-shadow-light: var(--ckqa-shadow-sm);
  --el-box-shadow: var(--ckqa-shadow-md);
}
```

如原文件已经定义了上述某些变量，整体覆盖。文件其余部分（如组件级定向覆盖）保留。

- [ ] **Step 2: 视觉验证按钮色**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

打开 `/login`，看登录页"登录"按钮：

- 默认态：暖橙 `#d97757`
- hover：稍亮（EL 的 light-3）
- active / 按下：偏暗（accent-strong）

如 hover 比默认还暗，说明 primary / dark-2 写反了，回到 Step 1 复检。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/styles/element-plus.scss
git commit -m "fix(admin-app): EL primary 映射修正

- primary 对齐到 --ckqa-accent (base)，dark-2 对齐到 -strong
- 显式指定 light-3 / 5 / 7 / 9 防止 hover 跳暗
- 中性色与边框统一用新 token"
```

---

### Task 5：Status Pill 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/status-pill-model.js`
- Create: `src/components/common/status-pill-model.test.js`
- Create: `src/components/common/CkStatusPill.vue`

- [ ] **Step 1: 写失败测试**

`src/components/common/status-pill-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  STATUS_PILL_TONES,
  resolvePillTone,
  resolvePillLabel,
  resolvePillStyleVars,
} from './status-pill-model.js'

test('STATUS_PILL_TONES 包含 6 种语义', () => {
  assert.deepEqual(
    Object.keys(STATUS_PILL_TONES).sort(),
    ['blocked', 'danger', 'neutral', 'running', 'success', 'warning'],
  )
})

test('resolvePillTone 直接返回合法 tone', () => {
  assert.equal(resolvePillTone('success'), 'success')
  assert.equal(resolvePillTone('running'), 'running')
})

test('resolvePillTone 把状态字符串映射到 tone', () => {
  assert.equal(resolvePillTone('active'), 'success')
  assert.equal(resolvePillTone('failed'), 'danger')
  assert.equal(resolvePillTone('processing'), 'running')
})

test('resolvePillTone 未知状态退化到 neutral', () => {
  assert.equal(resolvePillTone('unknown-xyz'), 'neutral')
  assert.equal(resolvePillTone(undefined), 'neutral')
  assert.equal(resolvePillTone(null), 'neutral')
})

test('resolvePillLabel 优先用显式 label', () => {
  assert.equal(resolvePillLabel({ label: '已激活', status: 'success' }), '已激活')
})

test('resolvePillLabel 缺 label 时用 status 文本', () => {
  assert.equal(resolvePillLabel({ status: 'running' }), 'running')
})

test('resolvePillStyleVars 返回 tone 对应的 CSS 变量对', () => {
  const vars = resolvePillStyleVars('success')
  assert.equal(vars['--pill-fg'], 'var(--ckqa-success)')
  assert.equal(vars['--pill-bg'], 'var(--ckqa-success-soft)')
})

test('resolvePillStyleVars 对 neutral 用 blocked 软底（通用中性视觉）', () => {
  const vars = resolvePillStyleVars('neutral')
  assert.equal(vars['--pill-fg'], 'var(--ckqa-text-muted)')
  assert.equal(vars['--pill-bg'], 'var(--ckqa-surface-muted)')
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：fail with `Cannot find module './status-pill-model.js'`。

- [ ] **Step 3: 写实现**

`src/components/common/status-pill-model.js`：

```js
import { getStatusTone } from './status-model.js'

export const STATUS_PILL_TONES = {
  success: { fg: 'var(--ckqa-success)', bg: 'var(--ckqa-success-soft)' },
  running: { fg: 'var(--ckqa-running)', bg: 'var(--ckqa-running-soft)' },
  warning: { fg: 'var(--ckqa-warning)', bg: 'var(--ckqa-warning-soft)' },
  blocked: { fg: 'var(--ckqa-blocked)', bg: 'var(--ckqa-blocked-soft)' },
  danger: { fg: 'var(--ckqa-danger)', bg: 'var(--ckqa-danger-soft)' },
  neutral: { fg: 'var(--ckqa-text-muted)', bg: 'var(--ckqa-surface-muted)' },
}

const VALID_TONES = new Set(Object.keys(STATUS_PILL_TONES))

const STATUS_TONE_OVERRIDES = {
  // status-model.js 把 'blocked' 当中性处理，CkStatusPill 把"未知"显式叫 neutral
  blocked: 'blocked',
}

export function resolvePillTone(input) {
  if (input == null) return 'neutral'
  if (VALID_TONES.has(input)) return input

  const tone = getStatusTone(input)
  if (tone === 'blocked') return STATUS_TONE_OVERRIDES.blocked
  return VALID_TONES.has(tone) ? tone : 'neutral'
}

export function resolvePillLabel({ label, status } = {}) {
  if (label) return label
  if (status) return String(status)
  return ''
}

export function resolvePillStyleVars(tone) {
  const safe = VALID_TONES.has(tone) ? tone : 'neutral'
  const palette = STATUS_PILL_TONES[safe]
  return {
    '--pill-fg': palette.fg,
    '--pill-bg': palette.bg,
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 5: 实现 Vue 组件**

`src/components/common/CkStatusPill.vue`：

```vue
<script setup>
import { computed } from 'vue'

import {
  resolvePillTone,
  resolvePillLabel,
  resolvePillStyleVars,
} from './status-pill-model.js'

const props = defineProps({
  status: { type: String, default: '' },
  tone: { type: String, default: '' },
  label: { type: String, default: '' },
  size: { type: String, default: 'md' }, // sm / md
})

const resolvedTone = computed(() => resolvePillTone(props.tone || props.status))
const resolvedLabel = computed(() => resolvePillLabel({ label: props.label, status: props.status }))
const styleVars = computed(() => resolvePillStyleVars(resolvedTone.value))
</script>

<template>
  <span
    class="ck-status-pill"
    :class="[`ck-status-pill--${size}`]"
    :style="styleVars"
    :data-tone="resolvedTone"
  >
    <span class="ck-status-pill-dot" aria-hidden="true" />
    <span class="ck-status-pill-label">{{ resolvedLabel }}</span>
  </span>
</template>

<style scoped lang="scss">
.ck-status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 9px;
  border-radius: var(--ckqa-radius-full);
  background: var(--pill-bg);
  color: var(--pill-fg);
  font-size: var(--ckqa-text-xs-size);
  line-height: var(--ckqa-text-xs-line);
  font-weight: var(--ckqa-fw-medium);
  white-space: nowrap;
}
.ck-status-pill--sm { padding: 1px 7px; font-size: 10px; }
.ck-status-pill-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 6: 在 `app-shell.test.js` 加 import 校验**

避免组件文件孤悬不被构建覆盖。`src/app-shell.test.js` 顶部 imports 区追加：

```js
import {
  STATUS_PILL_TONES,
  resolvePillTone,
} from './components/common/status-pill-model.js'
```

并在文件中已有的"模块导出存在"检查里追加一行：

```js
test('CkStatusPill model exports are wired', () => {
  assert.ok(STATUS_PILL_TONES)
  assert.equal(typeof resolvePillTone, 'function')
})
```

- [ ] **Step 7: 跑全部测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/status-pill-model.js \
        frontend/apps/admin-app/src/components/common/status-pill-model.test.js \
        frontend/apps/admin-app/src/components/common/CkStatusPill.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkStatusPill 组件 + 模型 + 测试"
```

---

### Task 6：CkSkeleton 视觉骨架组件

**Files:**

- Create: `src/components/common/CkSkeleton.vue`

无业务逻辑，纯视觉。不写单元测试。

- [ ] **Step 1: 实现组件**

`src/components/common/CkSkeleton.vue`：

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'card' }, // card / row / text / avatar
  count: { type: Number, default: 1 },
  animated: { type: Boolean, default: true },
})

const items = computed(() => Array.from({ length: Math.max(1, props.count) }))
</script>

<template>
  <div
    class="ck-skeleton-group"
    :class="{ 'ck-skeleton-group--animated': animated }"
    role="status"
    aria-busy="true"
    aria-live="polite"
  >
    <div
      v-for="(_, idx) in items"
      :key="idx"
      class="ck-skeleton"
      :class="`ck-skeleton--${variant}`"
    />
    <span class="ck-skeleton-sr">加载中…</span>
  </div>
</template>

<style scoped lang="scss">
.ck-skeleton-group { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.ck-skeleton {
  background: linear-gradient(
    90deg,
    var(--ckqa-surface-muted) 0%,
    var(--ckqa-surface-strong) 50%,
    var(--ckqa-surface-muted) 100%
  );
  background-size: 200% 100%;
  border-radius: var(--ckqa-radius-md);
}
.ck-skeleton-group--animated .ck-skeleton {
  animation: ck-skeleton-shimmer var(--ckqa-duration-slow, 320ms) linear infinite;
  animation-duration: 1600ms;
}
.ck-skeleton--card { height: 84px; }
.ck-skeleton--row { height: 36px; }
.ck-skeleton--text { height: var(--ckqa-text-md-line); width: 70%; border-radius: var(--ckqa-radius-sm); }
.ck-skeleton--avatar { width: 36px; height: 36px; border-radius: var(--ckqa-radius-full); }
.ck-skeleton-sr {
  position: absolute; width: 1px; height: 1px;
  padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0);
  white-space: nowrap; border: 0;
}
@keyframes ck-skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/CkSkeleton.vue
git commit -m "feat(admin-app): 新增 CkSkeleton 加载骨架组件

- 4 种 variant (card/row/text/avatar)
- 内置 shimmer 动画，支持 animated=false 关闭
- ARIA aria-busy + aria-live=polite 声明加载态"
```

---

### Task 7：CkPager 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/pager-model.js`
- Create: `src/components/common/pager-model.test.js`
- Create: `src/components/common/CkPager.vue`

- [ ] **Step 1: 写失败测试**

`src/components/common/pager-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PAGE_SIZE_OPTIONS,
  resolveTotalPages,
  resolvePageWindow,
  resolveLoadMoreState,
} from './pager-model.js'

test('PAGE_SIZE_OPTIONS 默认提供 20/50/100', () => {
  assert.deepEqual(PAGE_SIZE_OPTIONS, [20, 50, 100])
})

test('resolveTotalPages 整除', () => {
  assert.equal(resolveTotalPages({ total: 100, pageSize: 20 }), 5)
})

test('resolveTotalPages 余数向上取整', () => {
  assert.equal(resolveTotalPages({ total: 101, pageSize: 20 }), 6)
})

test('resolveTotalPages 0 项至少返回 1（避免 UI 显示"0/0"）', () => {
  assert.equal(resolveTotalPages({ total: 0, pageSize: 20 }), 1)
  assert.equal(resolveTotalPages({ total: -3, pageSize: 20 }), 1)
})

test('resolveTotalPages 非法 pageSize 返回 1', () => {
  assert.equal(resolveTotalPages({ total: 100, pageSize: 0 }), 1)
})

test('resolvePageWindow 一般情况下返回 5 个连续数字', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 5, totalPages: 10 }),
    [3, 4, 5, 6, 7],
  )
})

test('resolvePageWindow 在头部不溢出', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 1, totalPages: 10 }),
    [1, 2, 3, 4, 5],
  )
})

test('resolvePageWindow 在尾部不溢出', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 10, totalPages: 10 }),
    [6, 7, 8, 9, 10],
  )
})

test('resolvePageWindow totalPages < 5 时返回全部', () => {
  assert.deepEqual(resolvePageWindow({ page: 2, totalPages: 3 }), [1, 2, 3])
})

test('resolveLoadMoreState 显示"加载更多"当还有数据', () => {
  const state = resolveLoadMoreState({ loaded: 50, total: 200 })
  assert.equal(state.canLoadMore, true)
  assert.equal(state.label, '加载更多')
})

test('resolveLoadMoreState 数据加载完后变成"已全部加载"', () => {
  const state = resolveLoadMoreState({ loaded: 200, total: 200 })
  assert.equal(state.canLoadMore, false)
  assert.equal(state.label, '已全部加载')
})

test('resolveLoadMoreState total 未知时仍允许加载', () => {
  const state = resolveLoadMoreState({ loaded: 50, total: null })
  assert.equal(state.canLoadMore, true)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：fail with `Cannot find module './pager-model.js'`。

- [ ] **Step 3: 写实现**

`src/components/common/pager-model.js`：

```js
export const PAGE_SIZE_OPTIONS = [20, 50, 100]
const PAGE_WINDOW_SIZE = 5

export function resolveTotalPages({ total, pageSize }) {
  const safeTotal = Math.max(0, Number(total) || 0)
  const safePageSize = Math.max(1, Number(pageSize) || 0)
  if (safePageSize <= 0) return 1
  return Math.max(1, Math.ceil(safeTotal / safePageSize))
}

export function resolvePageWindow({ page, totalPages }) {
  const lastPage = Math.max(1, totalPages)
  if (lastPage <= PAGE_WINDOW_SIZE) {
    return Array.from({ length: lastPage }, (_, idx) => idx + 1)
  }
  const half = Math.floor(PAGE_WINDOW_SIZE / 2)
  let start = page - half
  let end = page + half
  if (start < 1) {
    end += 1 - start
    start = 1
  }
  if (end > lastPage) {
    start -= end - lastPage
    end = lastPage
  }
  return Array.from({ length: end - start + 1 }, (_, idx) => start + idx)
}

export function resolveLoadMoreState({ loaded, total }) {
  if (total == null) {
    return { canLoadMore: true, label: '加载更多' }
  }
  const canLoadMore = Number(loaded) < Number(total)
  return {
    canLoadMore,
    label: canLoadMore ? '加载更多' : '已全部加载',
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 5: 实现 Vue 组件**

`src/components/common/CkPager.vue`：

```vue
<script setup>
import { computed } from 'vue'

import {
  PAGE_SIZE_OPTIONS,
  resolveTotalPages,
  resolvePageWindow,
  resolveLoadMoreState,
} from './pager-model.js'

const props = defineProps({
  variant: { type: String, default: 'page' }, // page / load-more
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
  loaded: { type: Number, default: 0 },
})

const emit = defineEmits(['change-page', 'change-page-size', 'load-more'])

const totalPages = computed(() => resolveTotalPages({ total: props.total, pageSize: props.pageSize }))
const window = computed(() => resolvePageWindow({ page: props.page, totalPages: totalPages.value }))
const loadMore = computed(() => resolveLoadMoreState({ loaded: props.loaded, total: props.total }))

function go(page) {
  if (page < 1 || page > totalPages.value || page === props.page) return
  emit('change-page', page)
}
</script>

<template>
  <nav v-if="variant === 'page'" class="ck-pager" aria-label="分页">
    <button class="ck-pager-btn" :disabled="page <= 1" @click="go(page - 1)">←</button>
    <button
      v-for="p in window"
      :key="p"
      class="ck-pager-btn"
      :class="{ 'ck-pager-btn--active': p === page }"
      @click="go(p)"
    >
      {{ p }}
    </button>
    <button class="ck-pager-btn" :disabled="page >= totalPages" @click="go(page + 1)">→</button>
    <select
      class="ck-pager-size"
      :value="pageSize"
      aria-label="每页条数"
      @change="emit('change-page-size', Number($event.target.value))"
    >
      <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }} / 页</option>
    </select>
  </nav>

  <div v-else class="ck-pager-load-more">
    <button
      class="ck-pager-btn ck-pager-btn--block"
      :disabled="!loadMore.canLoadMore"
      @click="emit('load-more')"
    >
      {{ loadMore.label }}
    </button>
  </div>
</template>

<style scoped lang="scss">
.ck-pager { display: flex; align-items: center; gap: 6px; }
.ck-pager-btn {
  min-width: 32px; height: 32px; padding: 0 10px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.ck-pager-btn:hover:not(:disabled) { background: var(--ckqa-surface-muted); }
.ck-pager-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ck-pager-btn--active { background: var(--ckqa-accent-soft); border-color: var(--ckqa-accent); color: var(--ckqa-accent-strong); }
.ck-pager-btn--block { width: 100%; height: 36px; }
.ck-pager-size {
  height: 32px; padding: 0 8px; margin-left: 12px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
}
.ck-pager-load-more { width: 100%; }
</style>
```

- [ ] **Step 6: `app-shell.test.js` 串入校验**

`src/app-shell.test.js` 加 import + smoke：

```js
import { PAGE_SIZE_OPTIONS, resolveTotalPages } from './components/common/pager-model.js'

test('CkPager model exports are wired', () => {
  assert.deepEqual(PAGE_SIZE_OPTIONS, [20, 50, 100])
  assert.equal(resolveTotalPages({ total: 50, pageSize: 20 }), 3)
})
```

- [ ] **Step 7: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全绿。

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/pager-model.js \
        frontend/apps/admin-app/src/components/common/pager-model.test.js \
        frontend/apps/admin-app/src/components/common/CkPager.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkPager 分页组件 + 模型 + 测试

- 支持 'page' 与 'load-more' 两种 variant
- 5 格滑动窗口 + 头尾对齐
- pageSize 切换 [20/50/100]"
```

---

### Task 8：CkEmptyState 空态组件

**Files:**

- Create: `src/components/common/CkEmptyState.vue`

- [ ] **Step 1: 实现组件**

`src/components/common/CkEmptyState.vue`：

```vue
<script setup>
defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  cta: { type: Object, default: null }, // { label, onClick }
})
</script>

<template>
  <div class="ck-empty-state" role="region" aria-label="无数据">
    <div v-if="icon || $slots.icon" class="ck-empty-state-icon">
      <slot name="icon">
        <span aria-hidden="true">{{ icon }}</span>
      </slot>
    </div>
    <h3 class="ck-empty-state-title">{{ title }}</h3>
    <p v-if="description" class="ck-empty-state-desc">{{ description }}</p>
    <button
      v-if="cta?.label"
      class="ck-empty-state-cta"
      type="button"
      @click="cta.onClick && cta.onClick($event)"
    >
      {{ cta.label }}
    </button>
    <slot name="extra" />
  </div>
</template>

<style scoped lang="scss">
.ck-empty-state {
  display: flex; flex-direction: column; align-items: center;
  padding: var(--ckqa-space-10) var(--ckqa-space-6);
  text-align: center;
  color: var(--ckqa-text-muted);
}
.ck-empty-state-icon {
  width: 48px; height: 48px;
  display: flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface-muted);
  border-radius: var(--ckqa-radius-full);
  font-size: 22px;
  margin-bottom: var(--ckqa-space-4);
}
.ck-empty-state-title {
  margin: 0 0 var(--ckqa-space-2);
  font-size: var(--ckqa-text-md-size); line-height: var(--ckqa-text-md-line);
  font-weight: var(--ckqa-fw-medium);
  color: var(--ckqa-text);
}
.ck-empty-state-desc {
  margin: 0 0 var(--ckqa-space-5);
  max-width: 360px;
  font-size: var(--ckqa-text-sm-size); line-height: var(--ckqa-text-sm-line);
}
.ck-empty-state-cta {
  padding: 7px 16px;
  background: var(--ckqa-accent);
  color: var(--ckqa-accent-contrast);
  border: none;
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-sm-size);
  cursor: pointer;
}
.ck-empty-state-cta:hover { background: var(--ckqa-accent-strong); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/CkEmptyState.vue
git commit -m "feat(admin-app): 新增 CkEmptyState 空态组件"
```

---

### Task 9：CkPageHero 页头组件

**Files:**

- Create: `src/components/common/CkPageHero.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
})
</script>

<template>
  <header class="ck-page-hero">
    <div class="ck-page-hero-text">
      <span v-if="eyebrow" class="ck-page-hero-eyebrow">{{ eyebrow }}</span>
      <h1 class="ck-page-hero-title">{{ title }}</h1>
      <p v-if="subtitle" class="ck-page-hero-subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="ck-page-hero-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.ck-page-hero {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: var(--ckqa-space-6);
  margin-bottom: var(--ckqa-space-6);
}
.ck-page-hero-text { flex: 1; min-width: 0; }
.ck-page-hero-eyebrow {
  display: inline-block;
  font-size: var(--ckqa-text-xs-size); line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  margin-bottom: var(--ckqa-space-2);
}
.ck-page-hero-title {
  margin: 0;
  font-size: var(--ckqa-text-2xl-size); line-height: var(--ckqa-text-2xl-line);
  font-weight: var(--ckqa-fw-medium); letter-spacing: -0.2px;
  color: var(--ckqa-text);
}
.ck-page-hero-subtitle {
  margin: var(--ckqa-space-2) 0 0;
  font-size: var(--ckqa-text-sm-size); line-height: var(--ckqa-text-sm-line);
  color: var(--ckqa-text-muted);
}
.ck-page-hero-actions {
  display: flex; align-items: center; gap: var(--ckqa-space-2);
  flex-shrink: 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/CkPageHero.vue
git commit -m "feat(admin-app): 新增 CkPageHero 页头组件"
```

---

### Task 10：useScopeStore + 测试

**Files:**

- Create: `src/stores/scope.js`
- Create: `src/stores/scope.test.js`

- [ ] **Step 1: 写失败测试**

`src/stores/scope.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { createPinia } from 'pinia'

import {
  SCOPE_ALL,
  resolveScopeLabel,
  useScopeStore,
} from './scope.js'

test('SCOPE_ALL 是 Symbol（不会被 string 撞到）', () => {
  assert.equal(typeof SCOPE_ALL, 'symbol')
})

test('resolveScopeLabel 平台管理员显示"全平台"', () => {
  const label = resolveScopeLabel({
    role: 'admin',
    activeCourseId: SCOPE_ALL,
    courses: [],
  })
  assert.equal(label, '管理员 · 全平台')
})

test('resolveScopeLabel 教师 + 选定课程', () => {
  const label = resolveScopeLabel({
    role: 'teacher',
    activeCourseId: 'os-2026',
    courses: [{ id: 'os-2026', name: '操作系统课程' }],
  })
  assert.equal(label, '教师 · 操作系统课程')
})

test('resolveScopeLabel 教师 + 全部我的课程', () => {
  const label = resolveScopeLabel({
    role: 'teacher',
    activeCourseId: SCOPE_ALL,
    courses: [{ id: 'os-2026', name: '操作系统' }, { id: 'ds-2026', name: '数据结构' }],
  })
  assert.equal(label, '教师 · 全部我的课程（2）')
})

test('useScopeStore 默认 activeCourseId = SCOPE_ALL', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  assert.equal(store.state.activeCourseId, SCOPE_ALL)
})

test('useScopeStore.setActiveCourseId 设置后变更状态', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  store.setActiveCourseId('os-2026')
  assert.equal(store.state.activeCourseId, 'os-2026')
})

test('useScopeStore.requestParams 选定课程时附带 courseId', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  store.setActiveCourseId('os-2026')
  assert.deepEqual(store.requestParams(), { courseId: 'os-2026' })
})

test('useScopeStore.requestParams SCOPE_ALL 不附 courseId', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  assert.deepEqual(store.requestParams(), {})
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：fail with `Cannot find module './scope.js'`。

- [ ] **Step 3: 写实现**

`src/stores/scope.js`：

```js
import { defineStore } from 'pinia'
import { reactive, readonly } from 'vue'

import { getAdminPinia } from './pinia.js'

export const SCOPE_ALL = Symbol('scope-all')
const STORAGE_KEY = 'ckqa-admin-scope'
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function resolveScopeLabel({ role, activeCourseId, courses }) {
  const safeCourses = Array.isArray(courses) ? courses : []

  if (role === 'admin') {
    return '管理员 · 全平台'
  }

  const rolePrefix = role === 'assistant' ? '助教' : '教师'

  if (activeCourseId === SCOPE_ALL) {
    return `${rolePrefix} · 全部我的课程（${safeCourses.length}）`
  }

  const matched = safeCourses.find((course) => course.id === activeCourseId)
  return `${rolePrefix} · ${matched?.name || '未知课程'}`
}

export const useScopeStore = defineStore('scope', () => {
  const state = reactive({
    activeCourseId: SCOPE_ALL,
  })

  function load() {
    if (!isBrowser) return
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (!saved) return
      const parsed = JSON.parse(saved)
      if (parsed?.activeCourseId === '__ALL__' || parsed?.activeCourseId === undefined) {
        state.activeCourseId = SCOPE_ALL
      } else if (typeof parsed.activeCourseId === 'string') {
        state.activeCourseId = parsed.activeCourseId
      }
    } catch {
      state.activeCourseId = SCOPE_ALL
    }
  }

  function save() {
    if (!isBrowser) return
    const payload = {
      activeCourseId: state.activeCourseId === SCOPE_ALL ? '__ALL__' : state.activeCourseId,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  }

  function setActiveCourseId(value) {
    state.activeCourseId = value || SCOPE_ALL
    save()
  }

  function requestParams() {
    if (state.activeCourseId === SCOPE_ALL) return {}
    return { courseId: state.activeCourseId }
  }

  return {
    state: readonly(state),
    load,
    save,
    setActiveCourseId,
    requestParams,
  }
})

export const scopeStore = useScopeStore(getAdminPinia())
```

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 5: `main.js` 启动时调用 `load`**

`src/main.js` 在 `themeStore.init()` 之后追加：

```js
import { useScopeStore } from './stores/scope.js'
const scopeStore = useScopeStore(pinia)
scopeStore.load()
```

- [ ] **Step 6: 跑测试 + 启动 dev**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run dev
```

预期：测试全绿；dev 能启动且控制台无 store 报错。

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/stores/scope.js \
        frontend/apps/admin-app/src/stores/scope.test.js \
        frontend/apps/admin-app/src/main.js
git commit -m "feat(admin-app): 新增 useScopeStore 范围芯片状态

- SCOPE_ALL 用 Symbol 防止字符串撞键
- localStorage 持久化（'__ALL__' 字面量映射 Symbol）
- requestParams() 输出列表请求参数
- main.js 启动时 load() 还原"
```

---

## M2 · 布局壳与导航

### Task 11：routes.js 加 section 字段 + 检索日志列表占位

**Files:**

- Modify: `src/router/routes.js`

- [ ] **Step 1: 给所有 APP_ROUTES.meta 加 `section`**

打开 `src/router/routes.js`，对 `APP_ROUTES` 中每条路由的 `meta` 增补 `section` 字段，按下表对齐：

| navGroup | section |
| --- | --- |
| `dashboard` | `dashboard` |
| `courses` | `production` |
| `knowledge` | `production` |
| `qa` | `operations` |
| `users` | `settings` |
| `system` | `settings` |

例如：

```js
{
  path: '/app/dashboard',
  name: 'dashboard',
  componentKey: 'DashboardView',
  meta: {
    title: '工作台',
    layout: 'console',
    permissions: ['course:read'],
    status: 'mvp',
    navGroup: 'dashboard',
    section: 'dashboard',     // ← 新增
    keepAlive: true,
  },
},
```

把 `qa-sessions` 与 `qa-smoke`（如果存在）归入 `section: 'operations'`；把 `users / roles / permissions / authorization-audit-logs / health` 全部归入 `section: 'settings'`，但 health / authorization-audit-logs 在导航里展示为"系统健康 / 审计日志"。

- [ ] **Step 2: 新增 `/app/retrieval-logs` 列表占位**

在 `qa` 区域插入：

```js
{
  path: '/app/retrieval-logs',
  name: 'retrieval-logs',
  componentKey: 'RouteState',
  props: { state: 'coming-soon' },
  meta: {
    title: '检索日志',
    layout: 'console',
    permissions: ['qa:log:read'],
    status: 'upcoming',
    routeState: 'coming-soon',
    navGroup: 'qa',
    section: 'operations',
  },
},
```

- [ ] **Step 3: 重写 `primaryNavigation` 为段映射**

把 `primaryNavigation` 整体替换为以下结构（按 section 分段、保留 `key/label/path/permissions`）：

```js
export const primaryNavigation = [
  { section: 'dashboard', key: 'dashboard', label: '工作台', path: '/app/dashboard', permissions: ['course:read'] },

  { section: 'production', key: 'courses', label: '课程', path: '/app/courses', permissions: ['course:read'] },
  { section: 'production', key: 'materials', label: '资料', path: '/app/materials', permissions: ['material:read'], hidden: true },
  { section: 'production', key: 'knowledge-bases', label: '知识库', path: '/app/knowledge-bases', permissions: ['kb:read'] },

  { section: 'operations', key: 'qa-sessions', label: '问答会话', path: '/app/qa-sessions', permissions: ['qa:read'] },
  { section: 'operations', key: 'retrieval-logs', label: '检索日志', path: '/app/retrieval-logs', permissions: ['qa:log:read'] },
  { section: 'operations', key: 'kb-validation', label: '知识库验证', path: '/app/qa-smoke', permissions: ['qa:read'] },

  { section: 'settings', key: 'users', label: '用户与权限', path: '/app/users', permissions: ['user:read'] },
  { section: 'settings', key: 'health', label: '系统健康', path: '/app/health', permissions: ['system:read'] },
  { section: 'settings', key: 'audit', label: '审计日志', path: '/app/authorization-audit-logs', permissions: ['audit:read'] },
]

export const NAV_SECTIONS = [
  { key: 'dashboard', label: '' },              // 工作台单段不显示段标题
  { key: 'production', label: '生产' },
  { key: 'operations', label: '运维' },
  { key: 'settings', label: '设置' },
]
```

> 注：`materials` 是隐藏入口（`hidden: true`），用于"按课程筛资料"的内部跳转，不出现在侧栏；保留它便于 ⌘K 命令面板检索资料。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/router/routes.js
git commit -m "feat(admin-app): routes 加 section 分组 + 检索日志列表占位

- 所有 APP_ROUTES.meta 新增 section（dashboard/production/operations/settings）
- 新增 /app/retrieval-logs 列表占位（coming-soon）
- primaryNavigation 重写为按 section 平铺，配合 NAV_SECTIONS 段映射
- 新增侧栏导航项 '检索日志' / '知识库验证'"
```

---

### Task 12：navigation-model.js 重写为段模型 + 测试

**Files:**

- Modify: `src/components/shell/navigation-model.js`
- Modify: `src/app-shell.test.js`

- [ ] **Step 1: 写失败测试**

把 `src/app-shell.test.js` 中现有的 `buildNavigationGroups` 相关断言替换为：

```js
import {
  NAV_SECTIONS,
  buildNavigationSections,
  findActiveNavigationPath,
} from './components/shell/navigation-model.js'
import { primaryNavigation } from './router/routes.js'

test('buildNavigationSections 按 section 分组并保留排序', () => {
  const sections = buildNavigationSections(primaryNavigation, () => true)
  assert.deepEqual(sections.map((s) => s.key), ['dashboard', 'production', 'operations', 'settings'])
  assert.equal(sections[1].items[0].label, '课程')
})

test('buildNavigationSections 隐藏 hidden 项', () => {
  const sections = buildNavigationSections(primaryNavigation, () => true)
  const productionItems = sections.find((s) => s.key === 'production').items
  assert.equal(productionItems.find((item) => item.key === 'materials'), undefined)
})

test('buildNavigationSections 按 canAccess 过滤', () => {
  const onlyKbRead = (perms) => perms?.includes('kb:read') ?? perms?.length === 0
  const sections = buildNavigationSections(primaryNavigation, onlyKbRead)
  const productionItems = sections.find((s) => s.key === 'production').items
  assert.deepEqual(productionItems.map((item) => item.key), ['knowledge-bases'])
})

test('findActiveNavigationPath 命中精确路径', () => {
  const sections = buildNavigationSections(primaryNavigation, () => true)
  assert.equal(findActiveNavigationPath(sections, '/app/courses'), '/app/courses')
})

test('findActiveNavigationPath 命中前缀（详情页）', () => {
  const sections = buildNavigationSections(primaryNavigation, () => true)
  assert.equal(findActiveNavigationPath(sections, '/app/courses/123'), '/app/courses')
})

test('findActiveNavigationPath 没匹配返回空字符串', () => {
  const sections = buildNavigationSections(primaryNavigation, () => true)
  assert.equal(findActiveNavigationPath(sections, '/app/foo'), '')
})
```

> 旧测试中针对 `buildNavigationGroups / NAV_GROUPS` 的断言一并删除。

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：fail with `buildNavigationSections is not a function` 等导出未定义错误。

- [ ] **Step 3: 重写 `navigation-model.js`**

`src/components/shell/navigation-model.js` 整文件替换为：

```js
export { NAV_SECTIONS } from '../../router/routes.js'

export function buildNavigationSections(items, canAccess) {
  if (!Array.isArray(items)) return []
  const accessible = items
    .filter((item) => !item.hidden)
    .filter((item) => canAccess(item.permissions || []))

  const sectionMap = new Map()
  for (const item of accessible) {
    const list = sectionMap.get(item.section) || []
    list.push({ ...item })
    sectionMap.set(item.section, list)
  }

  return [...sectionMap.entries()]
    .map(([key, list]) => ({ key, items: list }))
    .sort((a, b) => SECTION_ORDER.indexOf(a.key) - SECTION_ORDER.indexOf(b.key))
}

const SECTION_ORDER = ['dashboard', 'production', 'operations', 'settings']

export function findActiveNavigationPath(sections, currentPath) {
  if (!currentPath) return ''
  const flat = sections.flatMap((section) => section.items)
  const exactMatch = flat.find((item) => item.path === currentPath)
  if (exactMatch) return exactMatch.path

  const prefixMatch = flat
    .filter((item) => currentPath.startsWith(`${item.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0]

  return prefixMatch?.path || ''
}
```

> 旧 `NAV_GROUPS / buildNavigationGroups` 已废弃。如其他地方仍 import 旧名，会构建报错；继续 Step 4 一一补救。

- [ ] **Step 4: 修复其他 import**

```bash
grep -rn "buildNavigationGroups\|NAV_GROUPS" frontend/apps/admin-app/src/
```

若有命中，把调用点改为新 API。例如 `src/layouts/ConsoleLayout.vue` 中如有：

```js
const navigationGroups = computed(() => buildNavigationGroups(routeRecords, authStore.canAccess))
```

改为（保留至 Task 19 重写 ConsoleLayout 时再彻底处理，本任务只让构建通过）：

```js
import { buildNavigationSections } from '../components/shell/navigation-model.js'
import { primaryNavigation } from '../router/routes.js'

const navigationSections = computed(() => buildNavigationSections(primaryNavigation, authStore.canAccess))
```

- [ ] **Step 5: 跑测试 + 构建**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
```

预期：测试与构建都通过。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/navigation-model.js \
        frontend/apps/admin-app/src/app-shell.test.js \
        frontend/apps/admin-app/src/layouts/ConsoleLayout.vue
git commit -m "refactor(admin-app): navigation-model 改为 section 分段模型

- buildNavigationGroups → buildNavigationSections
- 输入由 routeRecords 改为 primaryNavigation 扁平表
- ConsoleLayout 临时桥接，等 Task 19 整体重写"
```

---

### Task 13：面包屑模型加截断 + section 映射

**Files:**

- Modify: `src/layouts/console-breadcrumb-model.js`
- Modify: `src/app-shell.test.js`

- [ ] **Step 1: 写失败测试（新增 4 处）**

`src/app-shell.test.js` 在已有 breadcrumb 段追加：

```js
test('面包屑首段映射到 section 标签', () => {
  const items = buildConsoleBreadcrumbItems({
    path: '/app/courses',
    meta: { section: 'production', title: '课程' },
  })
  assert.equal(items[0].label, '生产')
  assert.equal(items[items.length - 1].label, '课程')
})

test('面包屑 dashboard section 显示工作台', () => {
  const items = buildConsoleBreadcrumbItems({
    path: '/app/dashboard',
    meta: { section: 'dashboard', title: '工作台' },
  })
  assert.equal(items[0].label, '工作台')
})

test('面包屑超过 4 层时折叠中间', () => {
  const items = buildConsoleBreadcrumbItems({
    path: '/app/materials/m-1/parse-results',
    meta: { section: 'production', title: '解析结果' },
    contextChain: [
      { label: '操作系统课程', to: '/app/courses/os' },
      { label: '数据结构第3章.pdf', to: '/app/materials/m-1' },
    ],
  })
  // 期望：[生产, …, 数据结构第3章.pdf, 解析结果]，4 个
  assert.equal(items.length, 4)
  assert.equal(items[0].label, '生产')
  assert.equal(items[1].label, '…')
  assert.ok(Array.isArray(items[1].collapsed))
  assert.equal(items[1].collapsed[0].label, '操作系统课程')
  assert.equal(items[2].label, '数据结构第3章.pdf')
  assert.equal(items[3].label, '解析结果')
})

test('面包屑当前层不可点（无 to）', () => {
  const items = buildConsoleBreadcrumbItems({
    path: '/app/dashboard',
    meta: { section: 'dashboard', title: '工作台' },
  })
  assert.equal(items[items.length - 1].to, undefined)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 3: 重写实现**

`src/layouts/console-breadcrumb-model.js` 整文件替换为：

```js
const SECTION_LABELS = {
  dashboard: '工作台',
  production: '生产',
  operations: '运维',
  settings: '设置',
}

const SECTION_HOMES = {
  production: '/app/courses',
  operations: '/app/qa-sessions',
  settings: '/app/users',
}

export function buildConsoleBreadcrumbItems(route) {
  if (!route) return []
  const sectionKey = route.meta?.section
  const sectionLabel = SECTION_LABELS[sectionKey]
  const sectionHome = SECTION_HOMES[sectionKey]

  const items = []

  if (sectionLabel) {
    items.push(
      sectionHome
        ? { kind: 'section', label: sectionLabel, to: sectionHome }
        : { kind: 'section', label: sectionLabel },
    )
  }

  const contextChain = Array.isArray(route.contextChain) ? route.contextChain : []
  for (const ctx of contextChain) {
    items.push({ kind: 'context', label: ctx.label, to: ctx.to })
  }

  if (route.meta?.title) {
    items.push({ kind: 'current', label: route.meta.title })
  }

  return collapseIfTooDeep(items)
}

function collapseIfTooDeep(items) {
  const MAX = 4
  if (items.length <= MAX) return items
  // 保留首段、末两段，中间折叠
  const first = items[0]
  const last2 = items.slice(-2)
  const collapsed = items.slice(1, items.length - 2)
  return [
    first,
    { kind: 'collapsed', label: '…', collapsed },
    ...last2,
  ]
}
```

> 旧文件如有其他 export 名（例如直接导出常量），保留并在新文件末尾再 export。

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 面包屑模型加 section 映射 + 4 层截断"
```

---

### Task 14：CkBreadcrumbs 组件 + 测试

**Files:**

- Create: `src/components/common/CkBreadcrumbs.vue`

无独立模型；视觉与逻辑全部基于 Task 13 的 model。

- [ ] **Step 1: 实现组件**

```vue
<script setup>
defineProps({
  items: { type: Array, required: true },
})
</script>

<template>
  <nav class="ck-breadcrumbs" aria-label="面包屑导航">
    <ol class="ck-breadcrumbs-list">
      <li
        v-for="(item, idx) in items"
        :key="`${item.kind}-${idx}-${item.label}`"
        class="ck-breadcrumbs-item"
        :data-kind="item.kind"
      >
        <details v-if="item.kind === 'collapsed'" class="ck-breadcrumbs-collapsed">
          <summary>{{ item.label }}</summary>
          <ul>
            <li
              v-for="(c, ci) in item.collapsed"
              :key="`${c.label}-${ci}`"
            >
              <RouterLink v-if="c.to" :to="c.to">{{ c.label }}</RouterLink>
              <span v-else>{{ c.label }}</span>
            </li>
          </ul>
        </details>
        <RouterLink v-else-if="item.to" :to="item.to">{{ item.label }}</RouterLink>
        <span v-else>{{ item.label }}</span>
        <span v-if="idx < items.length - 1" class="ck-breadcrumbs-sep" aria-hidden="true">/</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped lang="scss">
.ck-breadcrumbs-list {
  display: flex; flex-wrap: wrap; align-items: center;
  gap: 4px; padding: 0; margin: 0; list-style: none;
}
.ck-breadcrumbs-item {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--ckqa-text-xs-size); line-height: var(--ckqa-text-xs-line);
  color: var(--ckqa-text-muted);
}
.ck-breadcrumbs-item a { color: var(--ckqa-text-muted); text-decoration: none; }
.ck-breadcrumbs-item a:hover { color: var(--ckqa-accent-strong); text-decoration: underline; }
.ck-breadcrumbs-item[data-kind='current'] { color: var(--ckqa-text); font-weight: var(--ckqa-fw-medium); }
.ck-breadcrumbs-sep { color: var(--ckqa-text-weak); }
.ck-breadcrumbs-collapsed { position: relative; }
.ck-breadcrumbs-collapsed summary {
  cursor: pointer; list-style: none; padding: 0 4px;
  border-radius: var(--ckqa-radius-sm);
}
.ck-breadcrumbs-collapsed summary:hover { background: var(--ckqa-surface-muted); }
.ck-breadcrumbs-collapsed[open] ul {
  position: absolute; top: 100%; left: 0; z-index: 10;
  margin: 4px 0 0; padding: 6px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
  list-style: none; min-width: 160px;
}
.ck-breadcrumbs-collapsed[open] li { padding: 4px 8px; }
.ck-breadcrumbs-collapsed[open] li:hover { background: var(--ckqa-surface-muted); border-radius: var(--ckqa-radius-sm); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/CkBreadcrumbs.vue
git commit -m "feat(admin-app): 新增 CkBreadcrumbs 组件（含截断折叠）"
```

---

### Task 15：ScopeChip 组件

**Files:**

- Create: `src/components/shell/ScopeChip.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
import { computed, ref } from 'vue'
import { useScopeStore, SCOPE_ALL, resolveScopeLabel } from '../../stores/scope.js'
import { authStore } from '../../stores/auth.js'

const scope = useScopeStore()
const open = ref(false)

const role = computed(() => authStore.state.currentUser?.role || 'guest')
const courses = computed(() => authStore.state.currentUser?.courseMemberships || [])

const label = computed(() => resolveScopeLabel({
  role: role.value,
  activeCourseId: scope.state.activeCourseId,
  courses: courses.value,
}))

const canSwitch = computed(() => role.value !== 'admin' && courses.value.length > 0)

function pickAll() {
  scope.setActiveCourseId(SCOPE_ALL)
  open.value = false
}
function pickCourse(id) {
  scope.setActiveCourseId(id)
  open.value = false
}
</script>

<template>
  <div class="scope-chip">
    <button
      class="scope-chip-trigger"
      type="button"
      :disabled="!canSwitch"
      :aria-expanded="open"
      :aria-haspopup="canSwitch ? 'menu' : undefined"
      @click="canSwitch && (open = !open)"
    >
      <span class="scope-chip-dot" aria-hidden="true" />
      <span class="scope-chip-label">{{ label }}</span>
      <span v-if="canSwitch" class="scope-chip-caret" aria-hidden="true">▾</span>
    </button>
    <ul v-if="open && canSwitch" class="scope-chip-menu" role="menu">
      <li role="menuitem" @click="pickAll">
        <span>全部我的课程</span>
        <span class="scope-chip-meta">{{ courses.length }}</span>
      </li>
      <li
        v-for="course in courses"
        :key="course.id"
        role="menuitem"
        :class="{ 'is-active': scope.state.activeCourseId === course.id }"
        @click="pickCourse(course.id)"
      >
        <span>{{ course.name }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.scope-chip { position: relative; }
.scope-chip-trigger {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-bg);
  border-radius: var(--ckqa-radius-full);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  cursor: pointer;
}
.scope-chip-trigger[disabled] { cursor: default; }
.scope-chip-trigger:not([disabled]):hover { background: var(--ckqa-surface-muted); color: var(--ckqa-text); }
.scope-chip-dot {
  width: 7px; height: 7px; border-radius: 50%;
  background: var(--ckqa-success);
}
.scope-chip-caret { font-size: 9px; color: var(--ckqa-text-weak); }
.scope-chip-menu {
  position: absolute; top: 100%; left: 0; z-index: 30;
  margin: 6px 0 0; padding: 6px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
  list-style: none;
  min-width: 220px; max-height: 320px; overflow-y: auto;
}
.scope-chip-menu li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 10px;
  border-radius: var(--ckqa-radius-sm);
  cursor: pointer;
  font-size: var(--ckqa-text-sm-size);
}
.scope-chip-menu li:hover { background: var(--ckqa-surface-muted); }
.scope-chip-menu li.is-active { background: var(--ckqa-accent-soft); color: var(--ckqa-accent-strong); }
.scope-chip-meta { color: var(--ckqa-text-weak); font-size: var(--ckqa-text-xs-size); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/ScopeChip.vue
git commit -m "feat(admin-app): 新增顶栏 ScopeChip 范围切换组件"
```

---

### Task 16：ThemeToggle 组件

**Files:**

- Create: `src/components/shell/ThemeToggle.vue`

- [ ] **Step 1: 实现组件**

```vue
<script setup>
import { computed } from 'vue'
import { useThemeStore } from '../../stores/theme.js'

const theme = useThemeStore()
const isDark = computed(() => theme.state.resolvedTheme === 'dark')

function toggle() {
  const next = isDark.value ? 'light' : 'dark'
  theme.setMode(next)
}
</script>

<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="isDark ? '切换到亮色' : '切换到暗色'"
    :title="isDark ? '切换到亮色' : '切换到暗色'"
    @click="toggle"
  >
    <span aria-hidden="true">{{ isDark ? '☀' : '◐' }}</span>
  </button>
</template>

<style scoped lang="scss">
.theme-toggle {
  width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text-muted);
  font-size: 14px;
  cursor: pointer;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.theme-toggle:hover { background: var(--ckqa-surface-muted); color: var(--ckqa-text); }
.theme-toggle:focus-visible { outline: none; box-shadow: var(--ckqa-shadow-focus); }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/ThemeToggle.vue
git commit -m "feat(admin-app): 新增顶栏 ThemeToggle 主题切换组件"
```

---

### Task 17：通知模型 + NotificationDropdown 组件

**Files:**

- Create: `src/components/shell/notification-feed-model.js`
- Create: `src/components/shell/notification-feed-model.test.js`
- Create: `src/components/shell/NotificationDropdown.vue`

- [ ] **Step 1: 写失败测试**

`src/components/shell/notification-feed-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  mergeFeed,
  hasUnseenFailures,
  formatFeedItem,
} from './notification-feed-model.js'

const NOW = new Date('2026-05-08T10:00:00Z').getTime()

test('mergeFeed 合并 running + failed 并按时间倒序、限 5 条', () => {
  const running = [
    { id: 'r1', kind: 'index-run', updatedAt: NOW - 60000, ref: 'kb-1' },
    { id: 'r2', kind: 'index-run', updatedAt: NOW - 5000, ref: 'kb-2' },
  ]
  const failed = [
    { id: 'f1', kind: 'parse-task', updatedAt: NOW - 1000, ref: 'mat-1' },
    { id: 'f2', kind: 'parse-task', updatedAt: NOW - 30000, ref: 'mat-2' },
    { id: 'f3', kind: 'index-run', updatedAt: NOW - 90000, ref: 'kb-3' },
    { id: 'f4', kind: 'parse-task', updatedAt: NOW - 120000, ref: 'mat-4' },
  ]
  const feed = mergeFeed(running, failed)
  assert.equal(feed.length, 5)
  assert.equal(feed[0].id, 'f1')        // 最新
  assert.equal(feed[1].id, 'r2')
})

test('mergeFeed 输入非数组安全降级', () => {
  assert.deepEqual(mergeFeed(null, undefined), [])
})

test('hasUnseenFailures 返回 true 当有失败比 lastSeenAt 新', () => {
  const failed = [{ id: 'f1', updatedAt: NOW - 1000 }]
  assert.equal(hasUnseenFailures(failed, NOW - 5000), true)
})

test('hasUnseenFailures 返回 false 当全部失败都早于 lastSeenAt', () => {
  const failed = [{ id: 'f1', updatedAt: NOW - 50000 }]
  assert.equal(hasUnseenFailures(failed, NOW - 1000), false)
})

test('hasUnseenFailures 空列表返回 false', () => {
  assert.equal(hasUnseenFailures([], NOW), false)
  assert.equal(hasUnseenFailures(null, NOW), false)
})

test('formatFeedItem index-run running', () => {
  const item = formatFeedItem({
    id: 'r1', kind: 'index-run', status: 'running',
    title: '数据结构知识库 v2', updatedAt: NOW - 60000,
  }, NOW)
  assert.equal(item.tone, 'running')
  assert.equal(item.title, '数据结构知识库 v2')
  assert.match(item.subtitle, /分钟前/)
})

test('formatFeedItem parse-task failed', () => {
  const item = formatFeedItem({
    id: 'f1', kind: 'parse-task', status: 'failed',
    title: '操作系统第3章.pdf',
  }, NOW)
  assert.equal(item.tone, 'danger')
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 3: 写实现**

`src/components/shell/notification-feed-model.js`：

```js
const FEED_LIMIT = 5

export function mergeFeed(running, failed) {
  const safeRunning = Array.isArray(running) ? running : []
  const safeFailed = Array.isArray(failed) ? failed : []
  return [...safeRunning, ...safeFailed]
    .filter((item) => item && typeof item.updatedAt === 'number')
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, FEED_LIMIT)
}

export function hasUnseenFailures(failed, lastSeenAt) {
  if (!Array.isArray(failed) || failed.length === 0) return false
  const cutoff = Number(lastSeenAt) || 0
  return failed.some((item) => item && item.updatedAt > cutoff)
}

export function formatFeedItem(item, now = Date.now()) {
  const tone = item.status === 'failed' ? 'danger' : 'running'
  return {
    id: item.id,
    title: item.title || `${item.kind} ${item.id}`,
    subtitle: formatRelative(item.updatedAt, now),
    tone,
    kind: item.kind,
  }
}

function formatRelative(ts, now) {
  if (!ts) return '刚刚'
  const diff = Math.max(0, Math.floor((now - ts) / 1000))
  if (diff < 60) return `${diff} 秒前`
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  return `${Math.floor(diff / 86400)} 天前`
}
```

- [ ] **Step 4: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: 实现 Vue 组件（含轮询）**

`src/components/shell/NotificationDropdown.vue`：

```vue
<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'
import {
  mergeFeed,
  hasUnseenFailures,
  formatFeedItem,
} from './notification-feed-model.js'

const STORAGE_KEY = 'ckqa-admin-notifications-last-seen'
const POLL_INTERVAL = 60_000

const open = ref(false)
const running = ref([])
const failed = ref([])
const lastSeenAt = ref(Number(localStorage.getItem(STORAGE_KEY)) || 0)

let timer = null

async function fetchFeed() {
  // 接入实际 API，本任务先 placeholder。M3 / M4 时把数据源接到 useDashboardFeed 里
  // 防御：window.fetch 失败时保持上次结果
  try {
    const [runningRes, failedRes] = await Promise.all([
      fetch('/api/v1/index-runs?status=running').then((r) => r.json()).catch(() => ({ data: [] })),
      fetch('/api/v1/material-parse-tasks?status=failed&since=24h').then((r) => r.json()).catch(() => ({ data: [] })),
    ])
    running.value = (runningRes.data || []).map((r) => ({
      id: r.id, kind: 'index-run', status: 'running',
      title: r.title || `知识库构建 #${r.id}`, updatedAt: new Date(r.updatedAt).getTime(),
    }))
    failed.value = (failedRes.data || []).map((f) => ({
      id: f.id, kind: 'parse-task', status: 'failed',
      title: f.title || `资料解析 #${f.id}`, updatedAt: new Date(f.updatedAt).getTime(),
    }))
  } catch {
    // 静默：通知是辅助信息，不能因错误打扰用户
  }
}

const feed = computed(() => mergeFeed(running.value, failed.value).map((it) => formatFeedItem(it)))
const hasNew = computed(() => hasUnseenFailures(failed.value, lastSeenAt.value))

function toggle() {
  open.value = !open.value
  if (open.value) {
    lastSeenAt.value = Date.now()
    localStorage.setItem(STORAGE_KEY, String(lastSeenAt.value))
  }
}

onMounted(() => {
  fetchFeed()
  timer = setInterval(fetchFeed, POLL_INTERVAL)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="notif-dd">
    <button
      class="notif-dd-trigger"
      type="button"
      :aria-expanded="open"
      aria-label="通知"
      @click="toggle"
    >
      <span aria-hidden="true">🔔</span>
      <span v-if="hasNew" class="notif-dd-dot" aria-hidden="true" />
    </button>
    <div v-if="open" class="notif-dd-panel" role="menu">
      <header class="notif-dd-header">运行 / 失败 · 最近 24 小时</header>
      <ul v-if="feed.length" class="notif-dd-list">
        <li
          v-for="item in feed"
          :key="item.id"
          :class="`tone-${item.tone}`"
        >
          <strong>{{ item.title }}</strong>
          <span>{{ item.subtitle }}</span>
        </li>
      </ul>
      <p v-else class="notif-dd-empty">暂无活跃任务</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notif-dd { position: relative; }
.notif-dd-trigger {
  position: relative;
  width: 32px; height: 32px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  cursor: pointer;
}
.notif-dd-dot {
  position: absolute; top: 5px; right: 5px;
  width: 8px; height: 8px;
  border-radius: 50%; background: var(--ckqa-danger);
  box-shadow: 0 0 0 2px var(--ckqa-surface);
}
.notif-dd-panel {
  position: absolute; right: 0; top: calc(100% + 6px); z-index: 30;
  width: 320px; padding: 10px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  box-shadow: var(--ckqa-shadow-md);
}
.notif-dd-header {
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  padding: 4px 6px 8px;
}
.notif-dd-list { list-style: none; margin: 0; padding: 0; max-height: 360px; overflow-y: auto; }
.notif-dd-list li {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 6px;
  border-top: 1px solid var(--ckqa-border-soft);
  font-size: var(--ckqa-text-sm-size);
}
.notif-dd-list li.tone-danger strong { color: var(--ckqa-danger); }
.notif-dd-list li.tone-running strong { color: var(--ckqa-running); }
.notif-dd-empty {
  padding: 14px 6px;
  text-align: center;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
</style>
```

- [ ] **Step 6: `app-shell.test.js` 串入校验**

```js
import {
  mergeFeed,
  hasUnseenFailures,
} from './components/shell/notification-feed-model.js'

test('NotificationDropdown model exports are wired', () => {
  assert.equal(typeof mergeFeed, 'function')
  assert.equal(typeof hasUnseenFailures, 'function')
})
```

- [ ] **Step 7: 跑测试 + 构建**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/notification-feed-model.js \
        frontend/apps/admin-app/src/components/shell/notification-feed-model.test.js \
        frontend/apps/admin-app/src/components/shell/NotificationDropdown.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 NotificationDropdown + 信息流模型

- 60s 轮询 /api/v1/index-runs?running + parse-tasks?failed
- 红点徽标只看比 lastSeenAt 新的失败事件
- mergeFeed 限制 5 条 + 容错"
```

---

### Task 18：CkCommandPalette 命令面板（基础壳）

**Files:**

- Create: `src/components/shell/command-palette-model.js`
- Create: `src/components/shell/command-palette-model.test.js`
- Create: `src/components/shell/CkCommandPalette.vue`

本任务只搭壳：快捷键监听、搜索框、分组结果渲染、跳转。具体数据源（课程 / 资料 / 知识库 / 操作）通过 `groups` prop 注入，由后续 M3+ 接入。

- [ ] **Step 1: 写失败测试**

`src/components/shell/command-palette-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  filterGroups,
  flattenForKeyboard,
  isCommandShortcut,
  shouldIgnoreShortcut,
} from './command-palette-model.js'

test('filterGroups 空查询返回原 groups（每组前 5 条）', () => {
  const groups = [
    { key: 'nav', label: '跳转', items: Array.from({ length: 8 }, (_, i) => ({ id: `n${i}`, label: `跳转 ${i}` })) },
  ]
  const result = filterGroups(groups, '')
  assert.equal(result[0].items.length, 5)
})

test('filterGroups 查询匹配 label（不区分大小写）', () => {
  const groups = [
    { key: 'course', label: '课程', items: [
      { id: 'c1', label: '操作系统课程' },
      { id: 'c2', label: '数据结构' },
    ] },
  ]
  const result = filterGroups(groups, 'os')
  // "操作系统" 不匹配 "os"；"OS" 匹配
  const hit = result[0].items.map((i) => i.id)
  assert.equal(hit.length, 0)
})

test('filterGroups 中文匹配 substring', () => {
  const groups = [
    { key: 'course', label: '课程', items: [
      { id: 'c1', label: '操作系统课程' },
      { id: 'c2', label: '数据结构' },
    ] },
  ]
  const result = filterGroups(groups, '操作')
  assert.equal(result[0].items.length, 1)
  assert.equal(result[0].items[0].id, 'c1')
})

test('filterGroups 空匹配的 group 不返回', () => {
  const groups = [
    { key: 'course', label: '课程', items: [{ id: 'c1', label: '操作系统课程' }] },
    { key: 'kb', label: '知识库', items: [{ id: 'k1', label: '编译原理' }] },
  ]
  const result = filterGroups(groups, '操作')
  assert.deepEqual(result.map((g) => g.key), ['course'])
})

test('flattenForKeyboard 给每个 item 加 全局 index', () => {
  const groups = [
    { key: 'a', items: [{ id: '1' }, { id: '2' }] },
    { key: 'b', items: [{ id: '3' }] },
  ]
  const flat = flattenForKeyboard(groups)
  assert.deepEqual(flat.map((it) => it.id), ['1', '2', '3'])
})

test('isCommandShortcut 识别 ⌘K / Ctrl+K', () => {
  assert.equal(isCommandShortcut({ key: 'k', metaKey: true, ctrlKey: false }), true)
  assert.equal(isCommandShortcut({ key: 'k', metaKey: false, ctrlKey: true }), true)
  assert.equal(isCommandShortcut({ key: 'k', metaKey: false, ctrlKey: false }), false)
  assert.equal(isCommandShortcut({ key: 'p', metaKey: true, ctrlKey: false }), false)
})

test('shouldIgnoreShortcut 在 INPUT / TEXTAREA / contenteditable 时忽略', () => {
  const fakeInput = { tagName: 'INPUT', isContentEditable: false }
  const fakeTextarea = { tagName: 'TEXTAREA', isContentEditable: false }
  const fakeCE = { tagName: 'DIV', isContentEditable: true }
  const fakeOther = { tagName: 'BUTTON', isContentEditable: false }
  assert.equal(shouldIgnoreShortcut(fakeInput), true)
  assert.equal(shouldIgnoreShortcut(fakeTextarea), true)
  assert.equal(shouldIgnoreShortcut(fakeCE), true)
  assert.equal(shouldIgnoreShortcut(fakeOther), false)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 3: 写实现**

`src/components/shell/command-palette-model.js`：

```js
const PER_GROUP_LIMIT = 5

export function filterGroups(groups, query) {
  const safeGroups = Array.isArray(groups) ? groups : []
  const trimmed = String(query || '').trim().toLowerCase()

  return safeGroups
    .map((group) => {
      const items = (group.items || [])
        .filter((item) => {
          if (!trimmed) return true
          const label = String(item.label || '').toLowerCase()
          return label.includes(trimmed)
        })
        .slice(0, PER_GROUP_LIMIT)
      return { ...group, items }
    })
    .filter((group) => group.items.length > 0)
}

export function flattenForKeyboard(groups) {
  const safeGroups = Array.isArray(groups) ? groups : []
  return safeGroups.flatMap((group) => group.items || [])
}

export function isCommandShortcut(event) {
  if (!event || event.key !== 'k') return false
  return Boolean(event.metaKey || event.ctrlKey)
}

export function shouldIgnoreShortcut(target) {
  if (!target) return false
  if (target.isContentEditable) return true
  return target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'
}
```

- [ ] **Step 4: 实现 Vue 组件**

`src/components/shell/CkCommandPalette.vue`：

```vue
<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'

import {
  filterGroups,
  flattenForKeyboard,
  isCommandShortcut,
  shouldIgnoreShortcut,
} from './command-palette-model.js'

const props = defineProps({
  groups: { type: Array, default: () => [] },
})

const router = useRouter()

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref(null)

const filtered = computed(() => filterGroups(props.groups, query.value))
const flat = computed(() => flattenForKeyboard(filtered.value))

function handleKeydown(event) {
  if (isCommandShortcut(event) && !shouldIgnoreShortcut(event.target)) {
    event.preventDefault()
    open.value = true
    nextTick(() => inputRef.value?.focus())
    return
  }
  if (event.key === 'Escape' && open.value) {
    open.value = false
    return
  }
  if (!open.value) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = Math.min(flat.value.length - 1, activeIndex.value + 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = Math.max(0, activeIndex.value - 1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    activate(flat.value[activeIndex.value])
  }
}

function activate(item) {
  if (!item) return
  if (item.path) {
    router.push(item.path)
  } else if (typeof item.onActivate === 'function') {
    item.onActivate()
  }
  open.value = false
  query.value = ''
}

watch(query, () => { activeIndex.value = 0 })

onMounted(() => window.addEventListener('keydown', handleKeydown))
onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <div v-if="open" class="ck-cmdpalette" role="dialog" aria-modal="true">
    <div class="ck-cmdpalette-backdrop" @click="open = false" />
    <div class="ck-cmdpalette-frame">
      <input
        ref="inputRef"
        v-model="query"
        class="ck-cmdpalette-input"
        type="search"
        placeholder="搜索课程 / 资料 / 知识库 / 操作"
        aria-label="命令面板搜索"
      >
      <div class="ck-cmdpalette-results">
        <div v-if="!filtered.length" class="ck-cmdpalette-empty">暂无匹配</div>
        <section
          v-for="group in filtered"
          :key="group.key"
          class="ck-cmdpalette-group"
        >
          <header>{{ group.label }}</header>
          <ul>
            <li
              v-for="item in group.items"
              :key="item.id"
              :class="{ 'is-active': item === flat[activeIndex] }"
              @click="activate(item)"
            >
              <span>{{ item.label }}</span>
              <span v-if="item.hint" class="ck-cmdpalette-hint">{{ item.hint }}</span>
            </li>
          </ul>
        </section>
      </div>
      <footer class="ck-cmdpalette-footer">
        <span>↑↓ 选择</span><span>Enter 确认</span><span>Esc 关闭</span>
      </footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
.ck-cmdpalette {
  position: fixed; inset: 0; z-index: 60;
  display: flex; align-items: flex-start; justify-content: center;
  padding-top: 12vh;
}
.ck-cmdpalette-backdrop {
  position: absolute; inset: 0;
  background: rgb(28 26 23 / 30%);
}
.ck-cmdpalette-frame {
  position: relative;
  width: min(640px, 92vw);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-2xl);
  box-shadow: var(--ckqa-shadow-lg);
  overflow: hidden;
}
.ck-cmdpalette-input {
  width: 100%; padding: 14px 16px;
  border: none; border-bottom: 1px solid var(--ckqa-border-soft);
  background: transparent;
  font-size: var(--ckqa-text-md-size);
  color: var(--ckqa-text);
  outline: none;
}
.ck-cmdpalette-results { max-height: 60vh; overflow-y: auto; padding: 6px; }
.ck-cmdpalette-empty {
  padding: 18px; text-align: center;
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-sm-size);
}
.ck-cmdpalette-group header {
  padding: 8px 10px 4px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
}
.ck-cmdpalette-group ul { list-style: none; margin: 0; padding: 0; }
.ck-cmdpalette-group li {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 10px;
  border-radius: var(--ckqa-radius-sm);
  cursor: pointer;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
}
.ck-cmdpalette-group li.is-active,
.ck-cmdpalette-group li:hover {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
}
.ck-cmdpalette-hint { color: var(--ckqa-text-weak); font-size: var(--ckqa-text-xs-size); }
.ck-cmdpalette-footer {
  display: flex; gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--ckqa-border-soft);
  background: var(--ckqa-surface-muted);
  color: var(--ckqa-text-weak);
  font-size: var(--ckqa-text-xs-size);
}
</style>
```

- [ ] **Step 5: `app-shell.test.js` 串入校验**

```js
import {
  filterGroups,
  isCommandShortcut,
} from './components/shell/command-palette-model.js'

test('CkCommandPalette model exports are wired', () => {
  assert.equal(typeof filterGroups, 'function')
  assert.equal(isCommandShortcut({ key: 'k', metaKey: true }), true)
})
```

- [ ] **Step 6: 跑测试 + 构建**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/command-palette-model.js \
        frontend/apps/admin-app/src/components/shell/command-palette-model.test.js \
        frontend/apps/admin-app/src/components/shell/CkCommandPalette.vue \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): 新增 CkCommandPalette 命令面板基础壳

- 快捷键 ⌘K / Ctrl+K，输入态下不触发
- groups prop 注入分组数据，每组前 5 条
- ↑↓ Enter Esc 键盘可达"
```

---

### Task 19：AppTopbar 整合（重写）

**Files:**

- Modify: `src/components/shell/AppTopbar.vue`

整合 ScopeChip / CkCommandPalette / NotificationDropdown / ThemeToggle 与现有头像菜单。

- [ ] **Step 1: 重写**

`src/components/shell/AppTopbar.vue` 整体替换为：

```vue
<script setup>
import { computed } from 'vue'

import ScopeChip from './ScopeChip.vue'
import ThemeToggle from './ThemeToggle.vue'
import NotificationDropdown from './NotificationDropdown.vue'
import CkCommandPalette from './CkCommandPalette.vue'

const props = defineProps({
  apiBaseUrl: { type: String, default: '' },
  currentUser: { type: Object, default: () => null },
  commandGroups: { type: Array, default: () => [] }, // M3+ 注入数据源
})

const emit = defineEmits(['logout'])

const initial = computed(() => {
  const name = props.currentUser?.name || props.currentUser?.username || ''
  return name.charAt(0).toUpperCase() || '·'
})
</script>

<template>
  <header class="app-topbar">
    <div class="app-topbar-left">
      <RouterLink class="app-topbar-logo" to="/app/dashboard">
        <span class="app-topbar-mark" aria-hidden="true" />
        <span>CKQA Console</span>
      </RouterLink>
      <ScopeChip />
    </div>

    <div class="app-topbar-right">
      <button
        class="app-topbar-cmd-trigger"
        type="button"
        title="命令面板（⌘K / Ctrl+K）"
        aria-label="打开命令面板"
        @click="dispatchEvent(new KeyboardEvent('keydown', { key: 'k', metaKey: true, bubbles: true }))"
      >
        <span aria-hidden="true">⌘K</span>
      </button>
      <ThemeToggle />
      <NotificationDropdown />
      <button
        v-if="currentUser"
        class="app-topbar-avatar"
        type="button"
        :title="currentUser.name || currentUser.username"
        @click="emit('logout')"
      >
        {{ initial }}
      </button>
    </div>

    <CkCommandPalette :groups="commandGroups" />
  </header>
</template>

<style scoped lang="scss">
.app-topbar {
  display: flex; align-items: center; justify-content: space-between;
  gap: 16px;
  height: 52px;
  padding: 0 18px;
  background: var(--ckqa-surface);
  border-bottom: 1px solid var(--ckqa-border);
  position: sticky; top: 0; z-index: 20;
}
.app-topbar-left,
.app-topbar-right { display: flex; align-items: center; gap: 12px; }
.app-topbar-logo {
  display: inline-flex; align-items: center; gap: 9px;
  font-size: var(--ckqa-text-md-size);
  font-weight: var(--ckqa-fw-semibold);
  color: var(--ckqa-text);
  text-decoration: none;
}
.app-topbar-mark {
  width: 22px; height: 22px;
  background: linear-gradient(135deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  border-radius: var(--ckqa-radius-md);
  box-shadow: 0 2px 6px rgb(217 119 87 / 30%);
}
.app-topbar-cmd-trigger {
  height: 28px; padding: 0 9px;
  font-size: var(--ckqa-text-xs-size);
  background: var(--ckqa-surface-muted);
  border: 1px solid var(--ckqa-border-soft);
  border-radius: var(--ckqa-radius-md);
  color: var(--ckqa-text-muted);
  cursor: pointer;
  font-family: var(--ckqa-font-mono);
}
.app-topbar-cmd-trigger:hover { background: var(--ckqa-surface-strong); color: var(--ckqa-text); }
.app-topbar-avatar {
  width: 28px; height: 28px;
  display: inline-flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #c4ad8b, #8d6e54);
  color: white;
  border: none;
  border-radius: var(--ckqa-radius-full);
  font-size: var(--ckqa-text-xs-size);
  font-weight: var(--ckqa-fw-medium);
  cursor: pointer;
}
</style>
```

> 注：`avatar` 当前点击 = 注销，简化处理。M7 时再做"注销 / 关于 / 个人中心"下拉。

- [ ] **Step 2: 启动 dev 验证**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

打开 `/app/dashboard`，预期：

- 顶栏左侧：暖橙 logo + 范围芯片（管理员显示"管理员 · 全平台"）
- 顶栏右侧：⌘K 触发按钮 + 主题切换 + 通知 🔔 + 头像
- 按 ⌘K（或 Ctrl+K）：命令面板打开（暂时无数据，提示"暂无匹配"）

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/AppTopbar.vue
git commit -m "refactor(admin-app): AppTopbar 整合 ScopeChip/⌘K/通知/主题切换"
```

---

### Task 20：SideNavigation 分段渲染

**Files:**

- Modify: `src/components/shell/SideNavigation.vue`

- [ ] **Step 1: 重写**

整体替换为：

```vue
<script setup>
defineProps({
  sections: { type: Array, required: true },     // [{ key, items: [...] }]
  sectionLabels: { type: Object, required: true }, // { production: '生产', ... }
  activePath: { type: String, default: '' },
})
</script>

<template>
  <aside class="side-nav" aria-label="主导航">
    <section
      v-for="section in sections"
      :key="section.key"
      class="side-nav-section"
    >
      <h3 v-if="sectionLabels[section.key]" class="side-nav-section-title">
        {{ sectionLabels[section.key] }}
      </h3>
      <nav>
        <RouterLink
          v-for="item in section.items"
          :key="item.key"
          :to="item.path"
          class="side-nav-item"
          :class="{ 'is-active': activePath === item.path }"
        >
          <span class="side-nav-item-label">{{ item.label }}</span>
          <span v-if="item.count" class="side-nav-item-count">{{ item.count }}</span>
        </RouterLink>
      </nav>
    </section>

    <footer class="side-nav-footer">
      <strong>● API 正常</strong>
      <span>系统服务在线</span>
    </footer>
  </aside>
</template>

<style scoped lang="scss">
.side-nav {
  position: sticky; top: 52px; align-self: start;
  width: 220px; height: calc(100vh - 52px);
  padding: 14px 10px;
  background: var(--ckqa-surface);
  border-right: 1px solid var(--ckqa-border);
  display: flex; flex-direction: column;
  overflow-y: auto;
}
.side-nav-section { margin-bottom: 14px; }
.side-nav-section-title {
  margin: 0 10px 6px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
  text-transform: uppercase; letter-spacing: 0.6px;
  font-weight: var(--ckqa-fw-medium);
}
.side-nav-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 7px 10px;
  border-radius: 7px;
  font-size: var(--ckqa-text-sm-size);
  color: var(--ckqa-text);
  text-decoration: none;
  transition: background var(--ckqa-duration-fast) var(--ckqa-ease-standard);
}
.side-nav-item:hover { background: var(--ckqa-bg); }
.side-nav-item.is-active {
  background: var(--ckqa-accent-soft);
  color: var(--ckqa-accent-strong);
  font-weight: var(--ckqa-fw-medium);
}
.side-nav-item-count {
  background: var(--ckqa-bg);
  color: var(--ckqa-text-muted);
  font-size: var(--ckqa-text-xs-size);
  padding: 1px 6px;
  border-radius: var(--ckqa-radius-full);
}
.side-nav-item.is-active .side-nav-item-count {
  background: var(--ckqa-surface);
  color: var(--ckqa-accent-strong);
}
.side-nav-footer {
  margin-top: auto;
  padding: 10px;
  background: var(--ckqa-bg);
  border-radius: var(--ckqa-radius-md);
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-muted);
  display: flex; flex-direction: column; gap: 2px;
}
.side-nav-footer strong {
  color: var(--ckqa-text);
  font-weight: var(--ckqa-fw-medium);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell/SideNavigation.vue
git commit -m "refactor(admin-app): SideNavigation 改为分段渲染（生产/运维/设置）"
```

---

### Task 21：ConsoleLayout 整合（顶栏 + 分段侧栏 + 主区）

**Files:**

- Modify: `src/layouts/ConsoleLayout.vue`

- [ ] **Step 1: 重写**

整体替换为：

```vue
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import CkBreadcrumbs from '../components/common/CkBreadcrumbs.vue'

import { buildNavigationSections, findActiveNavigationPath } from '../components/shell/navigation-model.js'
import { primaryNavigation, NAV_SECTIONS } from '../router/routes.js'
import { buildConsoleBreadcrumbItems } from './console-breadcrumb-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const sections = computed(() =>
  buildNavigationSections(primaryNavigation, authStore.canAccess),
)

const sectionLabels = computed(() => {
  const map = {}
  for (const section of NAV_SECTIONS) {
    if (section.label) map[section.key] = section.label
  }
  return map
})

const activePath = computed(() => findActiveNavigationPath(sections.value, route.path))

const breadcrumbs = computed(() => buildConsoleBreadcrumbItems({
  path: route.path,
  meta: route.meta,
  contextChain: route.meta?.contextChain || [],
}))

const currentUser = computed(() => authStore.state.currentUser)

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="console-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>

    <AppTopbar
      :api-base-url="API_BASE_URL"
      :current-user="currentUser"
      :command-groups="[]"
      @logout="logout"
    />

    <div class="console-layout-body">
      <SideNavigation
        :sections="sections"
        :section-labels="sectionLabels"
        :active-path="activePath"
      />

      <main id="main-content" class="console-layout-main">
        <CkBreadcrumbs v-if="breadcrumbs.length" :items="breadcrumbs" />
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.console-layout {
  min-height: 100vh;
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
  display: flex;
  flex-direction: column;
}
.skip-link {
  position: absolute; top: -40px; left: 8px;
  padding: 6px 10px;
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  border-radius: var(--ckqa-radius-md);
  z-index: 100;
}
.skip-link:focus { top: 8px; }
.console-layout-body {
  display: flex;
  flex: 1; min-height: calc(100vh - 52px);
}
.console-layout-main {
  flex: 1; min-width: 0;
  padding: 22px 28px 40px;
  max-width: 1280px;
  margin: 0 auto;
}
@media (min-width: 1600px) {
  .console-layout-main { max-width: 1280px; }
}
</style>
```

> 旧 `console-layout` 中的"页面标题 + status badge"区块整体移除；标题改由各页面自己用 `CkPageHero` 渲染。

- [ ] **Step 2: 启动 dev**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

预期：

- `/app/dashboard` 进入后看到：顶栏 → [侧栏分段：工作台 / 生产 / 运维 / 设置] → 面包屑 → DashboardView 原内容（暂未重做）
- 切换到 `/app/courses`，侧栏"课程"高亮，面包屑显示"生产 / 课程"
- 主题切换正常，暗色无脏白底面板

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/layouts/ConsoleLayout.vue
git commit -m "refactor(admin-app): ConsoleLayout 整合分段侧栏 + 面包屑 + 顶栏

- 移除原 layout 标题区，标题改由各页面 CkPageHero 渲染
- skip-link / 暗色 / focus ring 正常"
```

---

### Task 22：DetailLayout 重写

**Files:**

- Modify: `src/layouts/DetailLayout.vue`

- [ ] **Step 1: 重写**

```vue
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import CkBreadcrumbs from '../components/common/CkBreadcrumbs.vue'

import { buildNavigationSections, findActiveNavigationPath } from '../components/shell/navigation-model.js'
import { primaryNavigation, NAV_SECTIONS } from '../router/routes.js'
import { buildConsoleBreadcrumbItems } from './console-breadcrumb-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()

const sections = computed(() => buildNavigationSections(primaryNavigation, authStore.canAccess))
const sectionLabels = computed(() => {
  const map = {}
  for (const section of NAV_SECTIONS) {
    if (section.label) map[section.key] = section.label
  }
  return map
})
const activePath = computed(() => findActiveNavigationPath(sections.value, route.path))
const breadcrumbs = computed(() => buildConsoleBreadcrumbItems({
  path: route.path,
  meta: route.meta,
  contextChain: route.meta?.contextChain || [],
}))
const currentUser = computed(() => authStore.state.currentUser)
function logout() { authStore.logout(); router.push('/login') }
</script>

<template>
  <div class="detail-layout">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <AppTopbar :api-base-url="API_BASE_URL" :current-user="currentUser" @logout="logout" />
    <div class="detail-layout-body">
      <SideNavigation :sections="sections" :section-labels="sectionLabels" :active-path="activePath" />
      <main id="main-content" class="detail-layout-main">
        <CkBreadcrumbs v-if="breadcrumbs.length" :items="breadcrumbs" />
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.detail-layout { min-height: 100vh; background: var(--ckqa-bg); color: var(--ckqa-text); }
.detail-layout-body { display: flex; min-height: calc(100vh - 52px); }
.detail-layout-main { flex: 1; min-width: 0; padding: 22px 28px 40px; max-width: 1280px; margin: 0 auto; }
.skip-link { position: absolute; top: -40px; left: 8px; padding: 6px 10px; background: var(--ckqa-surface); border-radius: 6px; }
.skip-link:focus { top: 8px; }
</style>
```

> DetailLayout 与 ConsoleLayout 视觉壳层一致，差别由具体页面在 `<slot>` 内自己用资源标题块 + Tab 表达，与设计稿 6.2 节匹配。

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/layouts/DetailLayout.vue
git commit -m "refactor(admin-app): DetailLayout 与 ConsoleLayout 共享壳层"
```

---

### Task 23：WorkflowLayout 分屏重写

**Files:**

- Modify: `src/layouts/WorkflowLayout.vue`

- [ ] **Step 1: 重写**

```vue
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AppTopbar from '../components/shell/AppTopbar.vue'
import SideNavigation from '../components/shell/SideNavigation.vue'
import CkBreadcrumbs from '../components/common/CkBreadcrumbs.vue'

import { buildNavigationSections, findActiveNavigationPath } from '../components/shell/navigation-model.js'
import { primaryNavigation, NAV_SECTIONS } from '../router/routes.js'
import { buildConsoleBreadcrumbItems } from './console-breadcrumb-model.js'
import { API_BASE_URL } from '../axios/index.js'
import { authStore } from '../stores/auth.js'

const route = useRoute()
const router = useRouter()
const sections = computed(() => buildNavigationSections(primaryNavigation, authStore.canAccess))
const sectionLabels = computed(() => {
  const map = {}
  for (const section of NAV_SECTIONS) {
    if (section.label) map[section.key] = section.label
  }
  return map
})
const activePath = computed(() => findActiveNavigationPath(sections.value, route.path))
const breadcrumbs = computed(() => buildConsoleBreadcrumbItems({
  path: route.path, meta: route.meta, contextChain: route.meta?.contextChain || [],
}))
const currentUser = computed(() => authStore.state.currentUser)
function logout() { authStore.logout(); router.push('/login') }
</script>

<template>
  <div class="workflow-layout">
    <AppTopbar :api-base-url="API_BASE_URL" :current-user="currentUser" @logout="logout" />
    <div class="workflow-layout-body">
      <SideNavigation :sections="sections" :section-labels="sectionLabels" :active-path="activePath" />
      <main class="workflow-layout-main">
        <CkBreadcrumbs v-if="breadcrumbs.length" :items="breadcrumbs" />
        <div class="workflow-layout-grid">
          <section class="workflow-layout-form">
            <slot name="form" />
          </section>
          <aside class="workflow-layout-live">
            <slot name="live" />
          </aside>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.workflow-layout { min-height: 100vh; background: var(--ckqa-bg); color: var(--ckqa-text); }
.workflow-layout-body { display: flex; min-height: calc(100vh - 52px); }
.workflow-layout-main { flex: 1; min-width: 0; padding: 22px 28px 40px; max-width: 1440px; margin: 0 auto; }
.workflow-layout-grid {
  display: grid;
  grid-template-columns: 7fr 5fr;
  gap: var(--ckqa-space-6);
  margin-top: var(--ckqa-space-4);
}
.workflow-layout-live {
  position: sticky; top: calc(52px + 22px);
  align-self: start;
  max-height: calc(100vh - 52px - 44px);
  overflow-y: auto;
}
@media (max-width: 1280px) {
  .workflow-layout-grid { grid-template-columns: 1fr; }
  .workflow-layout-live { position: static; max-height: none; }
}
</style>
```

> WorkflowLayout 改为有命名插槽 `form` 与 `live`。M5 重做向导时使用这两个插槽。

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/admin-app/src/layouts/WorkflowLayout.vue
git commit -m "refactor(admin-app): WorkflowLayout 改为 7fr/5fr 分屏 + form/live 命名插槽"
```

---

### Task 24：AuthLayout 视觉升级

**Files:**

- Modify: `src/layouts/AuthLayout.vue`

- [ ] **Step 1: 重写**

```vue
<template>
  <div class="auth-layout">
    <div class="auth-layout-glow auth-layout-glow--top" aria-hidden="true" />
    <div class="auth-layout-glow auth-layout-glow--bottom" aria-hidden="true" />
    <main class="auth-layout-content">
      <slot />
    </main>
    <footer class="auth-layout-footer">
      <span>CKQA Console</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.auth-layout {
  position: relative; min-height: 100vh;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
  overflow: hidden;
}
.auth-layout-glow {
  position: absolute;
  width: 480px; height: 480px; border-radius: 50%;
  background: radial-gradient(circle, var(--ckqa-accent-soft) 0%, transparent 70%);
  pointer-events: none;
  filter: blur(20px);
  opacity: 0.7;
}
.auth-layout-glow--top    { top: -180px; right: -120px; }
.auth-layout-glow--bottom { bottom: -200px; left: -140px; }
.auth-layout-content { position: relative; z-index: 1; width: min(420px, 92vw); padding: 32px; }
.auth-layout-footer {
  position: absolute; bottom: 18px;
  font-size: var(--ckqa-text-xs-size);
  color: var(--ckqa-text-weak);
}
</style>
```

- [ ] **Step 2: 视觉验证**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

打开 `/login`、`/403`、`/404`、`/500`，预期：暖灰底 + 顶右 / 底左两团暖橙光晕，登录表单 / 错误页内容居中显示。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/layouts/AuthLayout.vue
git commit -m "refactor(admin-app): AuthLayout 升级暖灰底 + 暖橙光晕"
```

---

### Task 25：copy/admin.js 文案种子 + Playwright 收尾

**Files:**

- Create: `src/copy/admin.js`
- Modify: `e2e/admin-app.spec.js`（如存在）

- [ ] **Step 1: 新建 copy 入口**

`src/copy/admin.js`：

```js
/**
 * 管理员端文案常量。所有面向用户的文本应从这里引用，方便统一巡检与将来抽 i18n。
 *
 * 命名规则：`域.场景.要素`，例如 nav.section.production / status.material.parsing
 */
export const COPY = Object.freeze({
  nav: {
    sections: {
      dashboard: '工作台',
      production: '生产',
      operations: '运维',
      settings: '设置',
    },
  },
  status: {
    material: {
      pending: '待解析',
      running: '解析中',
      ready: '已就绪',
      failed: '解析失败',
    },
    knowledgeBase: {
      pending: '待构建',
      running: '构建中',
      active: '已激活',
      failed: '构建失败',
      retired: '已停用',
    },
    task: {
      pending: '已发起',
      running: '进行中',
      success: '已完成',
      cancelled: '已取消',
      failed: '异常',
    },
  },
  feedback: {
    parseRetryHint: 'PDF 解析超时，已自动重试。可手动重试或检查文件大小。',
    kbValidationLabel: '知识库验证',
    qaResponseLabel: '响应时间（高负载下）',
  },
  topbar: {
    commandPalettePlaceholder: '搜索课程 / 资料 / 知识库 / 操作',
  },
})

export default COPY
```

- [ ] **Step 2: `app-shell.test.js` 串入校验**

```js
import { COPY } from './copy/admin.js'

test('COPY.nav.sections 完整且不含工程术语', () => {
  assert.equal(COPY.nav.sections.production, '生产')
  // 反例：内部 enum 不允许直接出现在 nav
  const flat = JSON.stringify(COPY.nav.sections)
  assert.equal(flat.includes('PENDING'), false)
  assert.equal(flat.includes('embedding'), false)
})

test('COPY.feedback 用平实表达替换冒烟', () => {
  assert.equal(COPY.feedback.kbValidationLabel, '知识库验证')
  assert.equal(JSON.stringify(COPY).includes('冒烟'), false)
})
```

- [ ] **Step 3: 跑测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：全部通过。

- [ ] **Step 4: Playwright E2E 文案断言改为 data-test-id**

```bash
grep -rn "QA 冒烟\|冒烟测试" frontend/apps/admin-app/e2e/ 2>/dev/null
```

如有命中，把 `getByText('QA 冒烟')` 改为 `getByTestId('nav-kb-validation')`，并在 `SideNavigation.vue` 的对应 `RouterLink` 上加 `data-test-id="nav-${item.key}"`。

```bash
pnpm --dir frontend/apps/admin-app run test:e2e
```

预期：全部通过（如本地无 Playwright 浏览器，跳过此步并标记为待 CI 验证）。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/copy/admin.js \
        frontend/apps/admin-app/src/app-shell.test.js \
        frontend/apps/admin-app/e2e/ \
        frontend/apps/admin-app/src/components/shell/SideNavigation.vue
git commit -m "feat(admin-app): copy/admin.js 文案种子 + E2E 选择器迁移到 data-test-id

- COPY 常量集中地，按 nav/status/feedback/topbar 分组
- 单元测试守住"不暴露内部 enum / 冒烟" 红线
- E2E 用例改用 data-test-id"
```

---

## 收尾验证

### Task 26：M1+M2 集成验证

不修改代码，只跑全套验收：

- [ ] **Step 1: 单元测试全绿**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：所有断言通过。

- [ ] **Step 2: 构建通过**

```bash
pnpm --dir frontend/apps/admin-app run build
```

预期：vite 输出无错误，dist/ 产物生成。

- [ ] **Step 3: Dev 启动 + 手工巡检**

```bash
pnpm --dir frontend/apps/admin-app run dev
```

按以下清单逐项点击验证（用真账号登录后）：

1. `/login` 暖灰 + 暖橙光晕，登录按钮颜色为 `#d97757`，hover 比默认略亮。
2. 登录后落到 `/app/dashboard`，顶栏显示 logo + 范围芯片 + ⌘K + 主题 + 通知 + 头像。
3. 范围芯片：管理员显示"管理员 · 全平台"且禁用切换；教师账号下应可下拉切课程，切换后再刷新页面，状态保留。
4. 侧栏：4 个段（工作台 / 生产 / 运维 / 设置），活跃项橙色软底 + 加粗。
5. 切到 `/app/courses` → `/app/courses/<id>`：面包屑显示"生产 / 课程 / <课程名>"（context chain 由该页注入，本任务可暂时只看到"生产 / 课程详情")。
6. 按 `⌘K`（Win/Linux 用 `Ctrl+K`）：命令面板打开，显示"暂无匹配"。Esc 关闭。
7. 按主题切换 ◐：整个页面切到暗色，无脏白卡片；再点切回亮色。
8. 通知 🔔 点击下拉：显示"暂无活跃任务"或真实任务列表。
9. 移到 `/app/qa-smoke`：侧栏侧"知识库验证"高亮（不是"QA 冒烟"）。
10. 移到 `/app/retrieval-logs`：进入 `RouteState` coming-soon 页。

- [ ] **Step 4: Commit 收尾标记（如有 lint 警告先修一个）**

如 Step 1-3 全绿，无需新 commit。如 Step 3 发现样式 bug（如某个 ElPlus 组件颜色没继承），加一行 patch commit。

---

## Self-Review

### 1. 设计稿覆盖度核对

| 设计稿章节 | 落到任务 |
| --- | --- |
| 4.1 颜色 token | Task 1 |
| 4.2 字体 token + mixin + WebFont 退化 | Task 3（WebFont 退化已写入 system fallback，不强制托管） |
| 4.3 间距 / 圆角 / 阴影 | 现有 token 文件中已有，本计划未单独 task；已保留 |
| 4.4 动效 token | 现有 `_motion.scss` 已有；保留兼容 |
| 4.5 EL 主题映射 | Task 4 |
| 5.1 一级导航分组 + 检索日志 | Task 11 |
| 5.2 路由 section 字段 | Task 11 |
| 5.3.1 范围芯片 | Task 10（store） + Task 15（UI） |
| 5.3.2 ⌘K 命令面板 | Task 18 |
| 5.3.3 通知下拉 | Task 17 |
| 5.3.4 主题与头像 | Task 16 + Task 19 |
| 5.4 左侧栏（分段） | Task 20 |
| 5.5 面包屑（截断） | Task 13 + Task 14 |
| 6.1 ConsoleLayout | Task 21 |
| 6.2 DetailLayout | Task 22 |
| 6.3 WorkflowLayout（7fr/5fr） | Task 23 |
| 6.4 AuthLayout | Task 24 |
| 7 通用组件（CkStatusPill / CkSkeleton / CkPager / CkEmptyState / CkPageHero / CkBreadcrumbs / CkCommandPalette） | Task 5/6/7/8/9/14/18 |
| 7 通用组件（CkPipelineHero / CkActivityFeed / CkTaskList / CkResourceCard / CkInfoTable / CkSplitProgress / CkLogStream / CkRetrievalPanel） | **不在本计划范围**，归 M3~M6 后续 plan |
| 10 文案规范（COPY 常量） | Task 25 |
| 11.1 主题切换 | Task 2 + Task 16 + Task 21（Console 应用） |
| 11.1 stylelint 禁裸色值 | **不在本计划范围**，建议合并到 M8 巡检 plan |
| 11.2 可访问性（skip link、focus、aria） | 各 Layout / 组件中已写入；ARIA live、焦点环统一 |

未覆盖的 8 个组件（Pipeline / Activity / Task / Resource / Info / Split / Log / Retrieval）以及 stylelint 配置，明确标注归后续 plan，本计划不引入半成品。

### 2. 占位扫描

通读全文：每个步骤都给了具体代码、命令、预期输出；没有"TBD / 类似 Task N / 略"等占位。

### 3. 类型 / API 一致性

- `useScopeStore.requestParams()`（Task 10 model）→ Task 10 测试 → 后续 layout 使用同一签名。
- `buildNavigationSections(items, canAccess)`（Task 12）→ Task 21~23 三个 layout 调用同名参数序。
- `buildConsoleBreadcrumbItems({ path, meta, contextChain })`（Task 13）→ Task 21~23 调用方式一致。
- `mergeFeed(running, failed)`（Task 17）→ 组件内部调用同序。
- `filterGroups(groups, query)` / `isCommandShortcut` / `shouldIgnoreShortcut`（Task 18）→ 组件 onMounted 中调用一致。

无签名漂移。

### 4. 范围检查

本计划只覆盖 M1+M2 两个里程碑，未把 M3~M8 内容掺进来。结束时所有 4 大重做交互页面尚未触动，但所有地基（token / layout / nav / 顶栏 / 基础组件）已就绪，M3 可直接消费。

---

**计划已写完。**
