# Admin App UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/specs/2026-04-26-admin-app-ui-redesign-design.md`，把 `frontend/apps/admin-app/` 重构为 A 方案为主的高密度运维玻璃控制台，并保留少量局部深色诊断面板。

**Architecture:** 先建立 CSS token、主题 store 和可测试的纯逻辑模型，再用小组件组合改造现有 `ConsoleLayout`、`DashboardView`、`HealthView`、`ModulePage`。正式接口边界保持 Java `/api/v1`，真实数据页与示例数据页通过 `DataSourceChip` 区分。

**Tech Stack:** Vue 3 + Vite + Vue Router + Axios + CSS 自定义属性 + `@fontsource/dm-sans` + `@fontsource/dm-mono` + `lucide-vue-next` + `node --test`

---

## 文件结构与职责

### 依赖与入口

- Modify: `frontend/apps/admin-app/package.json` · 增加字体和图标依赖，保留现有脚本。
- Modify: `frontend/apps/admin-app/pnpm-lock.yaml` · 由 `pnpm add` 自动更新。
- Modify: `frontend/apps/admin-app/src/main.js` · 初始化主题 store，导入样式入口。
- Modify: `frontend/apps/admin-app/src/style.css` · 改为导入分层 CSS。

### 样式系统

- Create: `frontend/apps/admin-app/src/styles/tokens.css` · 亮色/暗色、固定主题色、字号、间距、圆角、阴影、动效变量。
- Create: `frontend/apps/admin-app/src/styles/base.css` · reset、字体加载、焦点态、滚动条、可访问性基础。
- Create: `frontend/apps/admin-app/src/styles/components.css` · 按钮、chip、面板、表格、状态、骨架屏、局部深色诊断面板。

### Store 与纯逻辑

- Create: `frontend/apps/admin-app/src/stores/theme.js` · `light / dark / auto`、固定主题色、`localStorage` 持久化、Node 安全导入。
- Create: `frontend/apps/admin-app/src/components/common/status-model.js` · 状态文案、状态色、优先级映射。
- Create: `frontend/apps/admin-app/src/views/dashboard/production-track-model.js` · 生产链路节点混合态计算。
- Create: `frontend/apps/admin-app/src/views/system/health-model.js` · `/system/health` 响应归一化。
- Create: `frontend/apps/admin-app/src/components/shell/navigation-model.js` · 一级导航分组、权限过滤、面包屑输入模型。

### Shell 组件

- Create: `frontend/apps/admin-app/src/components/shell/AppTopbar.vue` · 顶栏、全局搜索壳、运行基线 chip、身份、数据范围、主题控件入口。
- Create: `frontend/apps/admin-app/src/components/shell/SideNavigation.vue` · 一级模块导航、激活态、upcoming 提示、窄屏收缩态。
- Create: `frontend/apps/admin-app/src/components/shell/ThemeControl.vue` · `light / dark / auto` 分段控件和固定主题色色板。
- Modify: `frontend/apps/admin-app/src/layouts/ConsoleLayout.vue` · 组合顶栏、侧栏、主内容，不再内联导航细节。
- Modify: `frontend/apps/admin-app/src/layouts/DetailLayout.vue` · 对齐主题 token 和页面标题区。
- Modify: `frontend/apps/admin-app/src/layouts/WorkflowLayout.vue` · 对齐主题 token 和工作流主视区。

### 公共组件

- Create: `frontend/apps/admin-app/src/components/common/StatusBadge.vue` · 统一状态显示。
- Create: `frontend/apps/admin-app/src/components/common/DataSourceChip.vue` · 示例数据、实时数据、页面骨架、未开放标记。
- Create: `frontend/apps/admin-app/src/components/common/SkeletonBlock.vue` · 局部加载骨架，支持 reduced motion。
- Create: `frontend/apps/admin-app/src/components/common/MetricTile.vue` · 工作台指标块。
- Create: `frontend/apps/admin-app/src/components/common/DiagnosticLogPanel.vue` · 局部深色诊断/日志摘要。
- Create: `frontend/apps/admin-app/src/components/common/DataTableShell.vue` · 筛选条、状态分段、表格壳、记录数、空状态。
- Create: `frontend/apps/admin-app/src/components/common/WorkflowStepper.vue` · 构建向导步骤条和状态说明。

### 页面组件

- Create: `frontend/apps/admin-app/src/views/dashboard/ProductionTrack.vue` · 生产链路轨道视图。
- Create: `frontend/apps/admin-app/src/components/system/HealthMatrix.vue` · 服务健康矩阵和诊断详情。
- Modify: `frontend/apps/admin-app/src/views/dashboard/DashboardView.vue` · 指标、轨道、异常优先区、近期活动重排。
- Modify: `frontend/apps/admin-app/src/views/system/HealthView.vue` · 四态、健康矩阵、原始响应折叠。
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue` · 使用 DataSourceChip、DataTableShell、StatusBadge、WorkflowStepper。
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js` · 增加 `dataSource`、状态、主次操作、权限字段。
- Modify: `frontend/apps/admin-app/src/views/auth/LoginView.vue` · 双区登录页和开发态身份说明。
- Modify: `frontend/apps/admin-app/src/views/status/RouteState.vue` · 403/404/500/coming-soon 统一状态页。

### 测试

- Modify: `frontend/apps/admin-app/src/app-shell.test.js` · 增加主题、导航、数据来源、生产链路、健康归一化测试。

---

## 全局约束

1. 不改变现有路由 path、name、权限 meta 和 `authStore` 开发态身份切换语义。
2. `VITE_API_BASE_URL` 默认继续是 `http://127.0.0.1:8080/api/v1`。
3. 管理端不直连 GraphRAG Python `/v1`。
4. `DataSourceChip` 出现在页面标题区，顶栏只展示运行基线 chip。
5. 暗色主题保持同一信息结构，局部深色面板只用于健康、索引运行、检索日志和失败任务摘要。
6. 不修改 `.env`、`node_modules/`、`dist/`、生成缓存或真实凭据。

---

## Phase 0 · 准备与基线

### Task 0.1 · 确认当前基线

**Files:**
- Read: `frontend/apps/admin-app/package.json`
- Read: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 进入 admin-app**

```bash
cd frontend/apps/admin-app
```

Expected: 当前工作目录是 `frontend/apps/admin-app`。

- [ ] **Step 2: 运行现有测试**

```bash
pnpm test
```

Expected: `node --test` 通过。如果失败，先记录失败用例和错误，不开始视觉改造。

- [ ] **Step 3: 运行现有构建**

```bash
pnpm build
```

Expected: Vite 构建通过。如果失败，先修复与本计划无关的构建阻断点，修复范围只限必要文件。

- [ ] **Step 4: Commit 基线记录**

```bash
git add frontend/apps/admin-app/src/app-shell.test.js
git commit -m "test(admin-app): capture current shell baseline"
```

Expected: 如果 Step 2/3 未产生文件变更，跳过本 commit。

---

## Phase 1 · 主题、字体、Token

### Task 1.1 · 安装字体和图标依赖

**Files:**
- Modify: `frontend/apps/admin-app/package.json`
- Modify: `frontend/apps/admin-app/pnpm-lock.yaml`

- [ ] **Step 1: 安装依赖**

```bash
cd frontend/apps/admin-app
pnpm add @fontsource/dm-sans @fontsource/dm-mono lucide-vue-next
```

Expected: `package.json` dependencies 新增 `@fontsource/dm-sans`、`@fontsource/dm-mono`、`lucide-vue-next`，`pnpm-lock.yaml` 同步更新。

- [ ] **Step 2: 验证包文件存在**

```bash
pnpm exec vite --version
```

Expected: 输出 Vite 版本，说明依赖解析正常。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/package.json frontend/apps/admin-app/pnpm-lock.yaml
git commit -m "feat(admin-app): add local fonts and icons"
```

### Task 1.2 · 主题 Store 的测试先行

**Files:**
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加失败测试**

在 `frontend/apps/admin-app/src/app-shell.test.js` 顶部 import 后追加：

```javascript
import {
  THEME_ACCENTS,
  isValidAccent,
  resolveTheme,
  themeStore,
} from './stores/theme.js'
```

在文件末尾追加：

```javascript
test('主题 store 可在 Node 环境安全导入并解析主题', () => {
  assert.equal(themeStore.state.mode, 'auto')
  assert.equal(themeStore.state.accent, 'indigo')
  assert.equal(resolveTheme('auto', false), 'light')
  assert.equal(resolveTheme('auto', true), 'dark')
  assert.equal(resolveTheme('light', true), 'light')
  assert.equal(resolveTheme('dark', false), 'dark')
})

test('主题色只允许固定枚举并提供强色阶', () => {
  assert.deepEqual(
    THEME_ACCENTS.map((item) => item.key),
    ['indigo', 'blue', 'teal', 'purple', 'amber'],
  )
  assert.equal(isValidAccent('teal'), true)
  assert.equal(isValidAccent('custom'), false)
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'teal').strong, '#0f766e')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'amber').strong, '#b45309')
})
```

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `Cannot find module './stores/theme.js'`。

### Task 1.3 · 实现主题 Store

**Files:**
- Create: `frontend/apps/admin-app/src/stores/theme.js`
- Modify: `frontend/apps/admin-app/src/main.js`

- [ ] **Step 1: 新建主题 Store**

```javascript
// frontend/apps/admin-app/src/stores/theme.js
import { reactive } from 'vue'

export const THEME_MODES = ['light', 'dark', 'auto']

export const THEME_ACCENTS = [
  { key: 'indigo', label: 'Indigo', color: '#6366f1', strong: '#4f46e5', contrast: '#ffffff' },
  { key: 'blue', label: 'Blue', color: '#2563eb', strong: '#1d4ed8', contrast: '#ffffff' },
  { key: 'teal', label: 'Teal', color: '#0d9488', strong: '#0f766e', contrast: '#ffffff' },
  { key: 'purple', label: 'Purple', color: '#9333ea', strong: '#7e22ce', contrast: '#ffffff' },
  { key: 'amber', label: 'Amber', color: '#d97706', strong: '#b45309', contrast: '#ffffff' },
]

const STORAGE_KEY = 'ckqa-theme'
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function isValidMode(mode) {
  return THEME_MODES.includes(mode)
}

export function isValidAccent(accent) {
  return THEME_ACCENTS.some((item) => item.key === accent)
}

export function resolveTheme(mode, prefersDark) {
  if (mode === 'dark') return 'dark'
  if (mode === 'light') return 'light'
  return prefersDark ? 'dark' : 'light'
}

const state = reactive({
  mode: 'auto',
  accent: 'indigo',
  resolvedTheme: 'light',
})

let mediaQuery = null

function getMediaQuery() {
  if (!isBrowser || !window.matchMedia) return null
  return window.matchMedia('(prefers-color-scheme: dark)')
}

function save() {
  if (!isBrowser) return
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ mode: state.mode, accent: state.accent }),
  )
}

function load() {
  if (!isBrowser) return
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    if (isValidMode(saved.mode)) state.mode = saved.mode
    if (isValidAccent(saved.accent)) state.accent = saved.accent
  } catch {
    state.mode = 'auto'
    state.accent = 'indigo'
  }
}

function syncDocumentTheme() {
  mediaQuery = mediaQuery ?? getMediaQuery()
  state.resolvedTheme = resolveTheme(state.mode, Boolean(mediaQuery?.matches))
  if (!isBrowser) return
  document.documentElement.setAttribute('data-theme', state.resolvedTheme)
  document.documentElement.setAttribute('data-accent', state.accent)
}

function setMode(mode) {
  if (!isValidMode(mode)) return
  state.mode = mode
  save()
  syncDocumentTheme()
}

function setAccent(accent) {
  if (!isValidAccent(accent)) return
  state.accent = accent
  save()
  syncDocumentTheme()
}

function initTheme() {
  load()
  syncDocumentTheme()
  mediaQuery = mediaQuery ?? getMediaQuery()
  mediaQuery?.addEventListener?.('change', syncDocumentTheme)
}

export const themeStore = {
  state,
  initTheme,
  setMode,
  setAccent,
  syncDocumentTheme,
}
```

- [ ] **Step 2: 在应用入口初始化主题**

将 `frontend/apps/admin-app/src/main.js` 改为：

```javascript
import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router/index.js'
import { themeStore } from './stores/theme.js'

themeStore.initTheme()

createApp(App).use(router).mount('#app')
```

- [ ] **Step 3: 运行主题测试**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 新增主题测试通过，原有测试继续通过。

### Task 1.4 · CSS Token 与基础样式

**Files:**
- Create: `frontend/apps/admin-app/src/styles/tokens.css`
- Create: `frontend/apps/admin-app/src/styles/base.css`
- Create: `frontend/apps/admin-app/src/styles/components.css`
- Modify: `frontend/apps/admin-app/src/style.css`

- [ ] **Step 1: 写 Token 文件**

```css
/* frontend/apps/admin-app/src/styles/tokens.css */
:root {
  color-scheme: light;
  --ckqa-bg: #f8fafc;
  --ckqa-surface: #ffffff;
  --ckqa-surface-muted: #f1f5f9;
  --ckqa-border: #e2e8f0;
  --ckqa-text: #0f172a;
  --ckqa-text-muted: #64748b;
  --ckqa-text-weak: #94a3b8;
  --ckqa-accent: #6366f1;
  --ckqa-accent-strong: #4f46e5;
  --ckqa-accent-contrast: #ffffff;
  --ckqa-success: #10b981;
  --ckqa-running: #3b82f6;
  --ckqa-warning: #f59e0b;
  --ckqa-blocked: #64748b;
  --ckqa-danger: #ef4444;
  --ckqa-radius: 8px;
  --ckqa-radius-pill: 999px;
  --ckqa-shadow-soft: 0 16px 40px rgb(15 23 42 / 10%);
  --ckqa-shadow-hover: 0 18px 42px rgb(15 23 42 / 14%);
  --ckqa-font-sans: "DM Sans", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", system-ui, sans-serif;
  --ckqa-font-mono: "DM Mono", "JetBrains Mono", "Cascadia Code", ui-monospace, monospace;
  --ckqa-duration-fast: 160ms;
  --ckqa-duration-base: 220ms;
}

[data-theme="dark"] {
  color-scheme: dark;
  --ckqa-bg: #020617;
  --ckqa-surface: #0f172a;
  --ckqa-surface-muted: #1e293b;
  --ckqa-border: #334155;
  --ckqa-text: #f8fafc;
  --ckqa-text-muted: #cbd5e1;
  --ckqa-text-weak: #94a3b8;
}

[data-accent="indigo"] { --ckqa-accent: #6366f1; --ckqa-accent-strong: #4f46e5; --ckqa-accent-contrast: #ffffff; }
[data-accent="blue"] { --ckqa-accent: #2563eb; --ckqa-accent-strong: #1d4ed8; --ckqa-accent-contrast: #ffffff; }
[data-accent="teal"] { --ckqa-accent: #0d9488; --ckqa-accent-strong: #0f766e; --ckqa-accent-contrast: #ffffff; }
[data-accent="purple"] { --ckqa-accent: #9333ea; --ckqa-accent-strong: #7e22ce; --ckqa-accent-contrast: #ffffff; }
[data-accent="amber"] { --ckqa-accent: #d97706; --ckqa-accent-strong: #b45309; --ckqa-accent-contrast: #ffffff; }
```

- [ ] **Step 2: 写基础样式入口**

```css
/* frontend/apps/admin-app/src/styles/base.css */
@import "@fontsource/dm-sans/400.css";
@import "@fontsource/dm-sans/700.css";
@import "@fontsource/dm-sans/800.css";
@import "@fontsource/dm-mono/400.css";

* {
  box-sizing: border-box;
}

html {
  font-family: var(--ckqa-font-sans);
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
}

button,
input,
select,
textarea {
  font: inherit;
}

:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--ckqa-accent-strong) 70%, white);
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 1ms !important;
    scroll-behavior: auto !important;
    transition-duration: 1ms !important;
  }
}
```

- [ ] **Step 3: 写组件基础样式**

```css
/* frontend/apps/admin-app/src/styles/components.css */
.ckqa-panel {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius);
}

.ckqa-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  gap: 6px;
  padding: 0 10px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-pill);
  color: var(--ckqa-text-muted);
  background: var(--ckqa-surface);
  white-space: nowrap;
}

.ckqa-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  gap: 8px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: var(--ckqa-radius);
  background: var(--ckqa-accent-strong);
  color: var(--ckqa-accent-contrast);
  cursor: pointer;
}

.ckqa-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--ckqa-shadow-hover);
}

/* 仅限 DiagnosticLogPanel 组件使用：health / index / log / failed-task 区域。 */
.diagnostic-log-panel {
  background: #020617;
  border: 1px solid #1e293b;
  border-radius: var(--ckqa-radius);
  color: #e2e8f0;
  font-family: var(--ckqa-font-mono);
}
```

- [ ] **Step 4: 改造全局入口**

```css
/* frontend/apps/admin-app/src/style.css */
@import "./styles/tokens.css";
@import "./styles/base.css";
@import "./styles/components.css";
```

- [ ] **Step 5: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 测试和构建通过。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/styles frontend/apps/admin-app/src/style.css frontend/apps/admin-app/src/stores/theme.js frontend/apps/admin-app/src/main.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): add theme tokens and safe theme store"
```

---

## Phase 2 · Shell 与主题交互

### Task 2.1 · 导航模型测试先行

**Files:**
- Create: `frontend/apps/admin-app/src/components/shell/navigation-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加失败测试**

在测试文件 import 后追加：

```javascript
import { buildNavigationGroups } from './components/shell/navigation-model.js'
```

在测试文件末尾追加：

```javascript
test('控制台导航按权限过滤并保留模块分组', () => {
  const canAccessWithoutUserWrite = (permissions = []) => {
    return !permissions.includes('user:write')
  }

  const groups = buildNavigationGroups(routeRecords, canAccessWithoutUserWrite)

  assert.deepEqual(
    groups.map((group) => group.key),
    ['dashboard', 'courses', 'knowledge', 'qa', 'users', 'system'],
  )
  assert.equal(groups.find((group) => group.key === 'dashboard').items[0].path, '/app/dashboard')
  assert.equal(
    groups.find((group) => group.key === 'users').items.some((item) => item.permissions.includes('user:write')),
    false,
  )
  assert.equal(
    groups.find((group) => group.key === 'knowledge').items.find((item) => item.path === '/app/knowledge-bases/:kbId/index-runs').displayState,
    'coming-soon',
  )
})
```

说明：`routeRecords` 已在现有 `app-shell.test.js` 中从 `./router/routes.js` 导入；本测试只追加导航模型 import 和断言。

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `navigation-model.js` 不存在。

### Task 2.2 · 实现导航模型和 Shell 组件

**Files:**
- Create: `frontend/apps/admin-app/src/components/shell/navigation-model.js`
- Create: `frontend/apps/admin-app/src/components/shell/AppTopbar.vue`
- Create: `frontend/apps/admin-app/src/components/shell/SideNavigation.vue`
- Create: `frontend/apps/admin-app/src/components/shell/ThemeControl.vue`
- Modify: `frontend/apps/admin-app/src/layouts/ConsoleLayout.vue`

- [ ] **Step 1: 实现导航纯逻辑**

```javascript
// frontend/apps/admin-app/src/components/shell/navigation-model.js
export const NAV_GROUPS = [
  { key: 'dashboard', label: '工作台', hint: '生产链路总览', accent: 'indigo' },
  { key: 'courses', label: '课程与资料', hint: '课程、资料、解析', accent: 'blue' },
  { key: 'knowledge', label: '知识库构建', hint: '导出、索引、激活', accent: 'teal' },
  { key: 'qa', label: '问答运维', hint: '会话、验证、检索', accent: 'purple' },
  { key: 'users', label: '用户与权限', hint: '角色、成员、审计', accent: 'amber' },
  { key: 'system', label: '系统与审计', hint: '健康、配置、日志', accent: 'slate' },
]

export function buildNavigationGroups(routes, canAccess) {
  return NAV_GROUPS.map((group) => {
    const items = routes
      .filter((route) => route.meta?.layout === 'console' || route.meta?.layout === 'detail' || route.meta?.layout === 'workflow')
      .filter((route) => route.meta?.navGroup === group.key)
      .filter((route) => !route.meta?.hidden)
      .filter((route) => canAccess(route.meta?.permissions || []))
      .map((route) => ({
        path: route.path,
        name: route.name,
        title: route.meta.title,
        status: route.meta.status,
        routeState: route.meta.routeState,
        displayState: route.meta.routeState || (route.meta.status === 'upcoming' ? 'coming-soon' : 'ready'),
        permissions: route.meta.permissions || [],
      }))

    return { ...group, items }
  })
}
```

显示规则：`meta.hidden` 是唯一的导航隐藏字段；`meta.routeState` 与 `meta.status === 'upcoming'` 只决定导航项的显示状态和浅色标签，不负责隐藏路由。`displayState === 'coming-soon'` 的导航项可以出现在侧栏，点击后进入同 URL 的 coming-soon 状态页。

- [ ] **Step 2: 实现 ThemeControl**

`ThemeControl.vue` 使用 `themeStore.state`、`THEME_ACCENTS`、`themeStore.setMode()`、`themeStore.setAccent()`；控件包含三个模式按钮和五个色板按钮，每个按钮设置 `aria-pressed`。

- [ ] **Step 3: 实现 AppTopbar**

`AppTopbar.vue` props:

```javascript
defineProps({
  apiBaseUrl: { type: String, required: true },
  currentUser: { type: Object, default: null },
  dataScopeLabel: { type: String, default: '未登录' },
})
```

顶栏内容顺序固定为：品牌、全局搜索壳、运行基线 chip、`ThemeControl`、身份 chip、退出按钮。运行基线文案从 `apiBaseUrl` prop 解析，不写死 localhost：

```javascript
function formatApiBaseline(apiBaseUrl) {
  try {
    const url = new URL(apiBaseUrl)
    return `API ${url.pathname || '/api/v1'} · 开发态`
  } catch {
    return `API ${apiBaseUrl} · 开发态`
  }
}
```

`ConsoleLayout.vue` 从 `src/axios/index.js` 导入 `API_BASE_URL` 并传给 `AppTopbar`，确保显示值跟 `VITE_API_BASE_URL` 的运行时配置一致。

- [ ] **Step 4: 实现 SideNavigation**

`SideNavigation.vue` props:

```javascript
defineProps({
  groups: { type: Array, required: true },
  activeGroup: { type: String, required: true },
  compact: { type: Boolean, default: false },
})
```

每个一级模块展示 label、hint、状态；`upcoming` item 显示浅色标签，不隐藏已规划边界。

- [ ] **Step 5: 改造 ConsoleLayout**

`ConsoleLayout.vue` 组合 `AppTopbar` 和 `SideNavigation`，用 `buildNavigationGroups(routeRecords, authStore.canAccess)` 生成导航；主内容保留 `<router-view />`。

- [ ] **Step 6: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 导航测试通过，构建通过。

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/components/shell frontend/apps/admin-app/src/layouts/ConsoleLayout.vue frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): rebuild console shell"
```

---

## Phase 3 · 公共状态组件

### Task 3.1 · 状态模型和数据来源测试先行

**Files:**
- Create: `frontend/apps/admin-app/src/components/common/status-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加失败测试**

```javascript
import {
  DATA_SOURCE_LABELS,
  getDataSourceLabel,
  getStatusTone,
} from './components/common/status-model.js'
```

```javascript
test('状态和数据来源有稳定映射', () => {
  assert.equal(getStatusTone('failed'), 'danger')
  assert.equal(getStatusTone('running'), 'running')
  assert.equal(getStatusTone('blocked'), 'blocked')
  assert.equal(getStatusTone('unknown'), 'blocked')
  assert.equal(getDataSourceLabel('mock'), '示例数据')
  assert.equal(getDataSourceLabel('live'), '实时数据')
  assert.equal(DATA_SOURCE_LABELS.skeleton, '页面骨架')
})
```

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `status-model.js` 不存在。

### Task 3.2 · 实现公共组件

**Files:**
- Create: `frontend/apps/admin-app/src/components/common/status-model.js`
- Create: `frontend/apps/admin-app/src/components/common/StatusBadge.vue`
- Create: `frontend/apps/admin-app/src/components/common/DataSourceChip.vue`
- Create: `frontend/apps/admin-app/src/components/common/SkeletonBlock.vue`
- Create: `frontend/apps/admin-app/src/components/common/DiagnosticLogPanel.vue`

- [ ] **Step 1: 实现状态模型**

```javascript
// frontend/apps/admin-app/src/components/common/status-model.js
export const STATUS_TONES = {
  done: 'success',
  ready: 'success',
  success: 'success',
  reachable: 'success',
  running: 'running',
  processing: 'running',
  indexing: 'running',
  pending: 'warning',
  skipped: 'warning',
  blocked: 'blocked',
  failed: 'danger',
  timeout: 'danger',
  unreachable: 'danger',
}

export const DATA_SOURCE_LABELS = {
  mock: '示例数据',
  live: '实时数据',
  skeleton: '页面骨架',
  comingSoon: '未开放',
}

export function getStatusTone(status) {
  return STATUS_TONES[status] || 'blocked'
}

export function getDataSourceLabel(source) {
  return DATA_SOURCE_LABELS[source] || DATA_SOURCE_LABELS.skeleton
}
```

- [ ] **Step 2: 实现 StatusBadge**

Props:

```javascript
defineProps({
  status: { type: String, required: true },
  label: { type: String, default: '' },
})
```

模板使用 `.status-badge` 和 `data-tone`，展示 `label || status`，并带 `aria-label`。

- [ ] **Step 3: 实现 DataSourceChip**

Props:

```javascript
defineProps({
  source: { type: String, required: true },
  refreshedAt: { type: String, default: '' },
})
```

展示 `getDataSourceLabel(source)`；`source === 'live'` 且有刷新时间时追加 `· 已刷新 HH:mm`。

- [ ] **Step 4: 实现 SkeletonBlock**

Props:

```javascript
defineProps({
  rows: { type: Number, default: 3 },
  compact: { type: Boolean, default: false },
})
```

生成固定行数的骨架块，CSS 中用 `prefers-reduced-motion` 静默降级。

- [ ] **Step 5: 实现 DiagnosticLogPanel**

Props:

```javascript
defineProps({
  title: { type: String, required: true },
  lines: { type: Array, default: () => [] },
  actions: { type: Array, default: () => [] },
})
```

用于局部深色诊断，不作为全站主题。组件根节点使用 `.diagnostic-log-panel`，不要在普通页面中直接复用这个类名；新增局部深色区域时必须通过 `DiagnosticLogPanel.vue` 组件承载。

- [ ] **Step 6: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 状态模型测试通过，构建通过。

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/components/common frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): add common state components"
```

---

## Phase 4 · 工作台

说明：工作台只依赖 `MetricTile`、`ProductionTrack`、`SkeletonBlock` 和 `DiagnosticLogPanel`；`DataTableShell` 与 `WorkflowStepper` 在 Phase 6 随 `ModulePage` 一起建立。

### Task 4.1 · 生产链路模型测试先行

**Files:**
- Create: `frontend/apps/admin-app/src/views/dashboard/production-track-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加失败测试**

```javascript
import {
  deriveTrackNodeState,
  PRODUCTION_STEPS,
} from './views/dashboard/production-track-model.js'
```

```javascript
test('生产链路节点按失败优先规则归一化', () => {
  assert.equal(PRODUCTION_STEPS.length, 6)
  assert.deepEqual(
    deriveTrackNodeState({ done: 18, failed: 2 }),
    { tone: 'danger', label: '18 done / 2 failed', priority: 5 },
  )
  assert.deepEqual(
    deriveTrackNodeState({ running: 3, done: 7 }),
    { tone: 'running', label: '3 running / 7 done', priority: 4 },
  )
  assert.equal(deriveTrackNodeState({ done: 10 }).tone, 'success')
  assert.equal(deriveTrackNodeState({ pending: 4 }).tone, 'warning')
  assert.equal(deriveTrackNodeState({}).tone, 'blocked')
})
```

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `production-track-model.js` 不存在。

### Task 4.2 · 实现工作台组件和页面

**Files:**
- Create: `frontend/apps/admin-app/src/views/dashboard/production-track-model.js`
- Create: `frontend/apps/admin-app/src/views/dashboard/ProductionTrack.vue`
- Create: `frontend/apps/admin-app/src/components/common/MetricTile.vue`
- Modify: `frontend/apps/admin-app/src/views/dashboard/DashboardView.vue`

- [ ] **Step 1: 实现生产链路模型**

```javascript
// frontend/apps/admin-app/src/views/dashboard/production-track-model.js
export const PRODUCTION_STEPS = [
  { key: 'material', label: '课程资料' },
  { key: 'parse', label: 'PDF 解析' },
  { key: 'export', label: 'GraphRAG 导出' },
  { key: 'index', label: '索引构建' },
  { key: 'activate', label: '索引激活' },
  { key: 'smoke', label: '问答验证' },
]

const ORDERED_KEYS = ['failed', 'running', 'done', 'pending', 'blocked']

export function deriveTrackNodeState(counts = {}) {
  const visible = ORDERED_KEYS
    .filter((key) => Number(counts[key]) > 0)
    .map((key) => `${counts[key]} ${key}`)

  if (counts.failed > 0) return { tone: 'danger', label: visible.join(' / '), priority: 5 }
  if (counts.running > 0) return { tone: 'running', label: visible.join(' / '), priority: 4 }
  if (counts.done > 0 && visible.length === 1) return { tone: 'success', label: visible[0], priority: 3 }
  if (counts.pending > 0 && visible.length === 1) return { tone: 'warning', label: visible[0], priority: 2 }
  return { tone: 'blocked', label: visible.join(' / ') || '未配置', priority: 1 }
}
```

- [ ] **Step 2: 实现 MetricTile**

Props:

```javascript
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  tone: { type: String, default: 'neutral' },
  loading: { type: Boolean, default: false },
})
```

固定高度，数字使用 tabular-nums，避免刷新时布局跳动。`loading === true` 时内部渲染 `SkeletonBlock`，骨架高度与正常数字区一致。

- [ ] **Step 3: 实现 ProductionTrack**

Props:

```javascript
defineProps({
  nodes: { type: Array, required: true },
})
```

每个 node 包含 `{ key, label, counts }`，组件内部调用 `deriveTrackNodeState`，桌面横向轨道，小屏改竖向列表。

- [ ] **Step 4: 改造 DashboardView**

页面结构固定为：页面标题 + `DataSourceChip`、5 个 `MetricTile`、`ProductionTrack`、最近活动、`DiagnosticLogPanel` 异常摘要。首版数据继续来自本地数组，`DataSourceChip source="mock"`，因此不触发加载态；后续切到 live 数据时，刷新期间将 5 个 `MetricTile` 的 `loading` 置为 `true`，避免指标区空白或跳动。

- [ ] **Step 5: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 生产链路模型测试通过，构建通过。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/views/dashboard frontend/apps/admin-app/src/components/common/MetricTile.vue frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): redesign dashboard production track"
```

---

## Phase 5 · 系统健康页

### Task 5.1 · 健康响应模型测试先行

**Files:**
- Create: `frontend/apps/admin-app/src/views/system/health-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加失败测试**

```javascript
import { normalizeHealthResponse } from './views/system/health-model.js'
```

```javascript
test('健康响应同时保留 reachable 和 ready', () => {
  const result = normalizeHealthResponse({
    status: 'degraded',
    services: {
      javaApi: { reachable: true, ready: true, message: 'ok' },
      graphRagApi: { reachable: true, ready: false, message: 'index missing' },
    },
  })

  assert.equal(result.overallStatus, 'degraded')
  assert.equal(result.services.length, 2)
  assert.deepEqual(result.services[1], {
    key: 'graphRagApi',
    label: 'GraphRAG API',
    reachable: true,
    ready: false,
    message: 'index missing',
    tone: 'warning',
  })
})
```

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `health-model.js` 不存在。

### Task 5.2 · 实现健康矩阵

**Files:**
- Create: `frontend/apps/admin-app/src/views/system/health-model.js`
- Create: `frontend/apps/admin-app/src/components/system/HealthMatrix.vue`
- Modify: `frontend/apps/admin-app/src/views/system/HealthView.vue`

- [ ] **Step 1: 实现健康模型**

```javascript
// frontend/apps/admin-app/src/views/system/health-model.js
const SERVICE_LABELS = {
  javaApi: 'Java API',
  mysql: 'MySQL',
  pdfIngestRoot: 'pdf_ingest root',
  graphRagRoot: 'graphrag_pipeline root',
  graphRagApi: 'GraphRAG API',
  lanceDb: 'output/lancedb',
}

function serviceTone(service) {
  if (!service.reachable) return 'danger'
  if (!service.ready) return 'warning'
  return 'success'
}

export function normalizeHealthResponse(payload = {}) {
  const services = Object.entries(payload.services || {}).map(([key, value]) => ({
    key,
    label: SERVICE_LABELS[key] || key,
    reachable: Boolean(value.reachable),
    ready: Boolean(value.ready),
    message: value.message || '',
    path: value.path || value.endpoint || '',
    tone: serviceTone(value),
  }))

  return {
    overallStatus: payload.status || 'unknown',
    checkedAt: payload.checkedAt || '',
    services,
    raw: payload,
  }
}
```

- [ ] **Step 2: 实现 HealthMatrix**

Props:

```javascript
defineProps({
  services: { type: Array, required: true },
})
```

矩阵卡片分别显示服务名、reachable、ready、message、path/endpoint；状态必须同时有颜色和文字。

- [ ] **Step 3: 改造 HealthView**

保留当前 `GET /api/v1/system/health` 请求；页面支持 idle、loading、success、error 四态。原始 JSON 放进 `<details>`，默认收起。刷新时只禁用刷新按钮。

- [ ] **Step 4: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 健康模型测试通过，构建通过。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/system frontend/apps/admin-app/src/components/system frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): redesign system health matrix"
```

---

## Phase 6 · 通用业务页模板

### Task 6.1 · 扩展页面模型

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 增加模型断言**

在现有 `getModulePageConfig` 测试后追加：

```javascript
test('业务页模型显式声明数据来源和主操作', () => {
  const courses = getModulePageConfig('courses')
  const build = getModulePageConfig('knowledge-base-build')

  assert.equal(courses.dataSource, 'mock')
  assert.equal(courses.primaryAction.label, '新建课程')
  assert.equal(build.dataSource, 'mock')
  assert.equal(build.workflowSteps.every((step) => Boolean(step.status)), true)
  assert.equal(build.workflowSteps.every((step) => Array.isArray(step.conditions)), true)
})
```

说明：`status` 和 `conditions` 只要求出现在 workflow 变体的 `workflowSteps` 中；table / overview 变体只需要 `dataSource`、状态字段和主次操作字段，不需要 `workflowSteps`。

- [ ] **Step 2: 验证测试失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 失败原因是 `dataSource`、`primaryAction` 或 `conditions` 字段缺失。

- [ ] **Step 3: 修改 module-content.js**

为每个 table / overview / workflow 配置增加：

```javascript
dataSource: 'mock',
primaryAction: { label: '新建课程', permission: 'course:write' },
```

workflow step 增加：

```javascript
{
  key: 'index',
  label: '索引构建',
  status: 'ready',
  conditions: ['GraphRAG 导出产物存在', 'output/lancedb 可写'],
  actionLabel: '开始构建索引',
  logLabel: '查看索引日志',
}
```

每一个 workflow step 都必须补齐 `status`、`conditions`、`actionLabel` 和 `logLabel`；table / overview 页面不添加 `workflowSteps`。

- [ ] **Step 4: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 页面模型新增断言通过。

### Task 6.2 · 实现 DataTableShell 和 WorkflowStepper

**Files:**
- Create: `frontend/apps/admin-app/src/components/common/DataTableShell.vue`
- Create: `frontend/apps/admin-app/src/components/common/WorkflowStepper.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`

- [ ] **Step 1: 实现 DataTableShell**

Props:

```javascript
defineProps({
  title: { type: String, required: true },
  columns: { type: Array, required: true },
  rows: { type: Array, required: true },
  filters: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无记录' },
})
```

使用语义化 `<table>`，第一列显示主名称和次要 ID，状态列使用插槽交给 `StatusBadge`。

- [ ] **Step 2: 实现 WorkflowStepper**

Props:

```javascript
defineProps({
  steps: { type: Array, required: true },
  activeKey: { type: String, default: '' },
})
```

每步展示状态、前置条件、执行按钮、日志入口。`blocked` 状态按钮禁用并显示阻塞原因。

- [ ] **Step 3: 改造 ModulePage**

table 变体使用 `DataTableShell`；overview 变体添加摘要条、字段网格、关联入口；workflow 变体使用左侧步骤、中央动作、右侧任务状态/日志三栏。页面标题旁统一放 `DataSourceChip :source="config.dataSource"`。

- [ ] **Step 4: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 模型测试通过，构建通过，所有现有路由仍可解析。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages frontend/apps/admin-app/src/components/common/DataTableShell.vue frontend/apps/admin-app/src/components/common/WorkflowStepper.vue frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): refactor module page templates"
```

---

## Phase 7 · 登录页、状态页与可访问性收口

### Task 7.1 · 登录页和状态页视觉重排

**Files:**
- Modify: `frontend/apps/admin-app/src/views/auth/LoginView.vue`
- Modify: `frontend/apps/admin-app/src/views/status/RouteState.vue`

- [ ] **Step 1: 改造 LoginView**

布局为左侧平台定位和生产链路简图，右侧开发态身份选择。右侧必须显示文案：`当前为开发态身份切换，正式登录接口待接入`。

- [ ] **Step 2: 改造 RouteState**

403 显示当前身份和缺失权限；404 提供返回工作台；500 提供刷新和系统健康入口；coming-soon 显示所属模块和规划状态。

- [ ] **Step 3: 验证**

```bash
cd frontend/apps/admin-app
pnpm build
```

Expected: 构建通过。

### Task 7.2 · 响应式和可访问性检查

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.css`
- Modify: `frontend/apps/admin-app/src/styles/base.css`

- [ ] **Step 1: 补齐响应式规则**

在 CSS 中加入：

```css
@media (max-width: 1023px) {
  .console-shell {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .production-track {
    grid-template-columns: 1fr;
  }

  .table-scroll {
    overflow-x: auto;
  }
}

@media (max-width: 767px) {
  .app-topbar {
    min-height: auto;
    flex-wrap: wrap;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

- [ ] **Step 2: 补齐可访问性规则**

确保按钮、链接、表单控件、导航项全部有 `:focus-visible`；状态 badge 文案不只依赖颜色；表格外层使用 `.table-scroll`。

- [ ] **Step 3: 验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 测试和构建通过。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/auth/LoginView.vue frontend/apps/admin-app/src/views/status/RouteState.vue frontend/apps/admin-app/src/styles
git commit -m "style(admin-app): polish auth and state pages"
```

---

## Phase 8 · 最终验证与文档收口

### Task 8.1 · 最终自动化验证

**Files:**
- Read: `frontend/apps/admin-app/package.json`
- Read: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 运行测试**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: 所有 `node --test` 用例通过。

- [ ] **Step 2: 运行构建**

```bash
cd frontend/apps/admin-app
pnpm build
```

Expected: Vite 构建通过。

- [ ] **Step 3: 启动开发服务器**

```bash
cd frontend/apps/admin-app
pnpm dev
```

Expected: Vite 输出本地访问地址，例如 `http://localhost:5173/`。

- [ ] **Step 4: 手工检查路由**

浏览器检查：

```text
/login
/app/dashboard
/app/health
/app/courses
/app/knowledge-bases/:kbId/build
/403
/404
/500
```

Expected: 主题切换可用，DataSourceChip 位置正确，工作台可扫读，健康页区分 reachable/ready，未开放页面不误导。

### Task 8.2 · 更新执行状态文档

**Files:**
- Modify: `docs/superpowers/specs/2026-04-26-admin-app-ui-redesign-design.md`
- Modify: `frontend/apps/admin-app/README.md`

- [ ] **Step 1: 更新规格中的落地状态**

在规格文档的“遗留问题与下一步建议”中，把已落地的视觉基础、工作台、健康页状态改为已完成描述，未接真实 API 的页面继续标明示例数据。

- [ ] **Step 2: 更新 admin-app README**

补充运行命令：

```bash
pnpm install
pnpm test
pnpm build
pnpm dev
```

补充说明：管理端正式接口通过 `VITE_API_BASE_URL` 指向 Java `/api/v1`，GraphRAG Python `/v1` 不作为正式前端请求目标。

- [ ] **Step 3: 最终状态检查**

```bash
git status --short
```

Expected: 只包含本计划相关文件变更。

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-04-26-admin-app-ui-redesign-design.md frontend/apps/admin-app/README.md
git commit -m "docs(admin-app): record redesign rollout status"
```

---

## 自查清单

1. 规格覆盖：主题系统、固定色板、DataSourceChip、ProductionTrack、HealthMatrix、ModulePage 三变体、LoginView、RouteState、响应式、可访问性均有对应任务。
2. 工程边界：计划不改变路由 path、权限 meta、`authStore`、`VITE_API_BASE_URL`、正式接口边界。
3. 数据边界：示例数据页面保持 `DataSourceChip source="mock"`，健康页为 `source="live"`。
4. 风险控制：主题 store Node 安全导入；Accent 色板只允许枚举固定 5 色，每色的 `strong` / `contrast` 字段用于保证按钮文字对比度满足 WCAG AA；局部深色面板不扩散成全站大屏。
5. 验证闭环：每个阶段至少运行 `pnpm test` 或 `pnpm build`，最终增加手工路由检查。
