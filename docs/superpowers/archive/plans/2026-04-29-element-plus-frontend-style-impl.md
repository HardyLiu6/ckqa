# CKQA 管理端 Element Plus + Pinia + Sass 样式重构 Implementation Plan

> 归档说明：本计划已执行完成，对应 Element Plus、Pinia、Sass 与 SCSS 分层成果已进入 `frontend/apps/admin-app/`。当前活跃入口请优先查看仓库根 [README.md](../../../../README.md)、[frontend/apps/admin-app/README.md](../../../../frontend/apps/admin-app/README.md) 与 [docs/admin-teacher-frontend-structure.md](../../../../docs/admin-teacher-frontend-structure.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/archive/specs/2026-04-29-element-plus-frontend-style-design.md`，只在 `frontend/apps/admin-app/` 内引入 Element Plus、Pinia、Sass，并把旧 CSS 迁移为可维护的 SCSS 分层，同时保持路由、loader、接口和业务状态流稳定。

**Architecture:** 先建立依赖、Vite 按需引入和 Pinia 单例边界，再用兼容导出迁移 `authStore`、`themeStore`，让现有组件无需大范围改调用。样式层采用 `styles/index.scss` 唯一入口，拆分 tokens、mixins、base、components、element-plus；首轮只做低风险视觉对齐和少量 Element Plus 表单控件替换。

**Tech Stack:** Vue 3.5 + Vite 8 + Vue Router 5 + Axios 1 + Element Plus 2.11 + Pinia 3 + Sass 1.93 + unplugin-auto-import + unplugin-vue-components + node:test + Playwright.

---

## 执行约定

1. **建议分支**：`feature/admin-app-element-plus-style`。
2. **提交粒度**：每个 Task 结束后提交一次。推荐提交信息：
   - `chore(admin-app): add element plus pinia sass dependencies`
   - `refactor(admin-app): migrate stores to pinia`
   - `style(admin-app): migrate global styles to sass tokens`
   - `style(admin-app): map ckqa tokens to element plus`
   - `style(admin-app): polish shell and login controls`
3. **不改范围**：不修改 `frontend/apps/student-app/`，不改变 Java `/api/v1` 浏览器边界，不改业务 loader、API 模型、Playwright mock。
4. **网络依赖**：安装依赖需要 `pnpm install` / `pnpm add` 能访问 registry；如果当前环境无法联网，先停在依赖步骤并记录。

## Review Fixes Incorporated

1. `fonts.scss` 使用的 `@fontsource/dm-sans` 和 `@fontsource/dm-mono` 先验证 admin-app 现有依赖，缺失时再安装。
2. `focus-ring` mixin 提供 `var(--ckqa-accent)` fallback，只在 `@supports` 内增强为 `color-mix()`。
3. 阴影 token 首轮不输出运行时 CSS 变量，`components.scss` 和 mixin 通过 Sass 变量使用。
4. `index.scss` 只作为浏览器全局样式入口，不再 `@forward` token。
5. router 守卫内部获取 Pinia auth store，`ThemeControl.vue` 和 `main.js` 显式使用共享 Pinia 实例。
6. `base.scss` 与 `components.scss` 的半径 token 替换规则分开写清。
7. `el-select` 首次接入后检查构建产物 CSS 中是否包含 `el-select` 样式。
8. 终验补充 `student-app` 构建回归。
9. `_responsive.scss` 的 `@use '../tokens/breakpoints' as *` 路径经核查正确，保留。
10. `element-plus.scss` 中的 focus ring 也必须采用 fallback + `@supports` 增强，不直接在完整 `box-shadow` 声明中裸用 `color-mix()`。
11. `el-select` 产物验证不能假设 CSS 固定输出到 `dist/assets/*.css`，应同时检查 CSS chunk 和 JS chunk。

## 文件结构与职责

- Modify: `frontend/apps/admin-app/package.json`
  新增 Element Plus、Pinia、Sass、按需引入插件依赖。
- Modify: `frontend/apps/admin-app/pnpm-lock.yaml`
  记录依赖解析结果。
- Modify: `frontend/apps/admin-app/vite.config.js`
  保留现有 `/api/v1` 代理，新增 Element Plus 自动导入和组件按需引入插件。
- Create: `frontend/apps/admin-app/src/stores/pinia.js`
  提供 admin-app 内共享 Pinia 实例，保证 `main.js`、router、axios、Node 测试使用同一 store 容器。
- Modify: `frontend/apps/admin-app/src/stores/theme.js`
  迁移为 Pinia setup store，同时保留 `themeStore.state`、`setMode()`、`setAccent()`、`initTheme()` 兼容 API。
- Modify: `frontend/apps/admin-app/src/stores/auth.js`
  迁移为 Pinia setup store，同时保留 `createAuthStore()`、`authStore.state`、`loginAs()`、`logout()`、`canAccess()`。
- Modify: `frontend/apps/admin-app/src/main.js`
  注册共享 Pinia 实例，切换全局样式入口到 `styles/index.scss`，在挂载前初始化主题。
- Delete: `frontend/apps/admin-app/src/style.css`
  删除旧全局样式入口。
- Delete: `frontend/apps/admin-app/src/styles/tokens.css`
  被 `styles/tokens/_colors.scss`、`_typography.scss`、`_radius.scss`、`_shadow.scss`、`_space.scss`、`_motion.scss`、`_breakpoints.scss`、`_z-index.scss` 取代。
- Delete: `frontend/apps/admin-app/src/styles/base.css`
  被 `styles/base.scss` 和 `styles/fonts.scss` 取代。
- Delete: `frontend/apps/admin-app/src/styles/components.css`
  被 `styles/components.scss` 和 `styles/element-plus.scss` 取代。
- Create: `frontend/apps/admin-app/src/styles/index.scss`
  唯一全局样式入口，负责加载 token CSS 输出、字体、base、Element Plus 覆盖、过渡期组件样式；不作为 SFC `@use` 的二次导出模块。
- Create: `frontend/apps/admin-app/src/styles/fonts.scss`
  加载 DM Sans、DM Mono 字体。
- Create: `frontend/apps/admin-app/src/styles/base.scss`
  reset、body、滚动条、focus-visible、低动效保护。
- Create: `frontend/apps/admin-app/src/styles/components.scss`
  迁移现有自研 class 样式，保留 `.primary-button`、`.module-hero`、`.ckqa-panel` 等兼容类名。
- Create: `frontend/apps/admin-app/src/styles/element-plus.scss`
  映射 CKQA token 到 Element Plus CSS 变量，补充 Button、Input、Select、Dialog、Drawer、Tag、Table 等基础覆盖。
- Create: `frontend/apps/admin-app/src/styles/tokens/_colors.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_typography.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_radius.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_shadow.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_space.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_motion.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_breakpoints.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_z-index.scss`
  管理端设计 token。颜色、字体、圆角输出 CSS 自定义属性；阴影首轮只作为 Sass 变量使用。
- Create: `frontend/apps/admin-app/src/styles/mixins/_focus.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_surface.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_status.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_responsive.scss`
  复用 focus ring、面板、状态、断点 mixin。
- Modify: `frontend/apps/admin-app/src/components/shell/ThemeControl.vue`
  使用 Pinia theme store，保留按钮组与色板交互。
- Modify: `frontend/apps/admin-app/src/views/auth/LoginView.vue`
  将开发态身份选择从原生 `select` 迁移为 `el-select` / `el-option`，不改变登录流程。
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
  补充依赖/Vite 插件、Pinia store 兼容、主题色枚举、SCSS 入口存在性测试。

---

## Phase 0 · 准备与基线

### Task 0.1 · 收口当前设计稿并创建实施分支

**Files:**
- Read: `docs/superpowers/archive/specs/2026-04-29-element-plus-frontend-style-design.md`
- Modify: git branch only

- [ ] **Step 1: 查看当前工作树**

Run:

```bash
git status --short
```

Expected: 只应看到设计稿和本计划文件，或能清楚区分与本任务无关的用户改动。

- [ ] **Step 2: 提交设计稿与实施计划**

Run:

```bash
git add docs/superpowers/archive/specs/2026-04-29-element-plus-frontend-style-design.md docs/superpowers/archive/plans/2026-04-29-element-plus-frontend-style-impl.md
git commit -m "docs(admin-app): add element plus style implementation plan"
```

Expected: 提交成功，设计稿与计划成为实施基线。

- [ ] **Step 3: 创建实施分支**

Run:

```bash
git switch -c feature/admin-app-element-plus-style
```

Expected: 当前分支为 `feature/admin-app-element-plus-style`。

- [ ] **Step 4: 记录 admin-app 当前测试基线**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: 两个命令通过；如果失败，先记录失败测试名和错误，不进入样式迁移。

---

## Phase 1 · 依赖与 Vite 按需引入

### Task 1.1 · 安装 Element Plus / Pinia / Sass 并确认字体依赖

**Files:**
- Modify: `frontend/apps/admin-app/package.json`
- Modify: `frontend/apps/admin-app/pnpm-lock.yaml`

- [ ] **Step 1: 安装运行依赖**

Run:

```bash
cd frontend/apps/admin-app
pnpm add element-plus@^2.11.5 @element-plus/icons-vue@^2.3.2 pinia@^3.0.3
```

Expected: `package.json` dependencies 新增：

```json
{
  "@element-plus/icons-vue": "^2.3.2",
  "element-plus": "^2.11.5",
  "pinia": "^3.0.3"
}
```

- [ ] **Step 2: 安装开发依赖**

Run:

```bash
pnpm add -D sass@^1.93.2 unplugin-auto-import@^20.2.0 unplugin-vue-components@^29.1.0
```

Expected: `package.json` devDependencies 新增：

```json
{
  "sass": "^1.93.2",
  "unplugin-auto-import": "^20.2.0",
  "unplugin-vue-components": "^29.1.0"
}
```

- [ ] **Step 3: 确认字体依赖仍存在**

`fonts.scss` 依赖 admin-app 当前已有的 `@fontsource/dm-sans` 和 `@fontsource/dm-mono`。先检查：

```bash
pnpm list @fontsource/dm-sans @fontsource/dm-mono --depth 0
```

Expected: 输出包含 `@fontsource/dm-sans` 和 `@fontsource/dm-mono`。If either package is missing, install it explicitly:

```bash
pnpm add @fontsource/dm-sans@^5.2.8 @fontsource/dm-mono@^5.2.7
```

- [ ] **Step 4: 验证依赖可解析**

Run:

```bash
pnpm list element-plus pinia sass unplugin-auto-import unplugin-vue-components @fontsource/dm-sans @fontsource/dm-mono --depth 0
```

Expected: 输出包含这 7 个包，版本满足 `package.json` 范围。

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/apps/admin-app/package.json frontend/apps/admin-app/pnpm-lock.yaml
git commit -m "chore(admin-app): add element plus pinia sass dependencies"
```

### Task 1.2 · 配置 Vite Element Plus 按需引入

**Files:**
- Modify: `frontend/apps/admin-app/vite.config.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 先写 Vite 插件测试**

Add to `frontend/apps/admin-app/src/app-shell.test.js` near the existing Vite proxy test:

```javascript
test('Vite 配置启用 Element Plus 自动导入插件且保留 API 代理', () => {
  const devConfig = createViteConfig({})
  const pluginNames = devConfig.plugins.map((plugin) => plugin.name)

  assert.ok(pluginNames.includes('vite:vue'))
  assert.ok(pluginNames.some((name) => name.includes('unplugin-auto-import')))
  assert.ok(pluginNames.some((name) => name.includes('unplugin-vue-components')))
  assert.equal(devConfig.server.proxy['/api/v1'].target, 'http://127.0.0.1:8080')
  assert.equal(devConfig.server.proxy['/api/v1'].changeOrigin, true)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd frontend/apps/admin-app
pnpm test -- --test-name-pattern="Vite 配置启用 Element Plus"
```

Expected: FAIL，原因是插件尚未加入 `createViteConfig()`。

- [ ] **Step 3: 修改 Vite 配置**

Replace `frontend/apps/admin-app/vite.config.js` with:

```javascript
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export function resolveApiProxyTarget(env = process.env) {
  const rawTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return rawTarget.trim().replace(/\/+$/, '')
}

export function createViteConfig(env = process.env) {
  return defineConfig({
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
      }),
      Components({
        resolvers: [ElementPlusResolver()],
      }),
    ],
    server: {
      proxy: {
        '/api/v1': {
          target: resolveApiProxyTarget(env),
          changeOrigin: true,
        },
      },
    },
  })
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return createViteConfig({ ...process.env, ...env })
})
```

- [ ] **Step 4: 验证 Vite 测试通过**

Run:

```bash
pnpm test -- --test-name-pattern="Vite 配置启用 Element Plus|开发服务器把同源"
```

Expected: PASS，且旧 `/api/v1` 代理测试仍通过。

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/apps/admin-app/vite.config.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "chore(admin-app): configure element plus auto imports"
```

---

## Phase 2 · Pinia Store 兼容迁移

### Task 2.1 · 新增共享 Pinia 实例

**Files:**
- Create: `frontend/apps/admin-app/src/stores/pinia.js`

- [ ] **Step 1: 创建 Pinia 单例文件**

Create `frontend/apps/admin-app/src/stores/pinia.js`:

```javascript
import { createPinia } from 'pinia'

export const adminPinia = createPinia()

export function getAdminPinia() {
  return adminPinia
}
```

- [ ] **Step 2: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/stores/pinia.js
git commit -m "refactor(admin-app): add shared pinia instance"
```

### Task 2.2 · 迁移 auth store 并保留旧 API

**Files:**
- Modify: `frontend/apps/admin-app/src/stores/auth.js`
- Modify: `frontend/apps/admin-app/src/router/index.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 扩展认证 store 测试**

Update the import block in `src/app-shell.test.js`:

```javascript
import { createPinia } from 'pinia'
import { createAuthStore, useAuthStore } from './stores/auth.js'
```

Add this test after the existing auth test:

```javascript
test('认证 store 迁移到 Pinia 后保留旧兼容 API', () => {
  const pinia = createPinia()
  const auth = createAuthStore(pinia)
  const sameAuth = useAuthStore(pinia)

  auth.loginAs('teacher')

  assert.equal(sameAuth.state.currentUser.role, 'teacher')
  assert.equal(auth.state.isAuthenticated, true)
  assert.equal(auth.canAccess(['material:parse']), true)

  auth.logout()
  assert.equal(sameAuth.state.currentUser, null)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd frontend/apps/admin-app
pnpm test -- --test-name-pattern="认证 store 迁移到 Pinia"
```

Expected: FAIL，原因是 `useAuthStore` 尚未导出。

- [ ] **Step 3: 改造 `auth.js`**

Replace `frontend/apps/admin-app/src/stores/auth.js` with:

```javascript
import { computed, reactive, readonly } from 'vue'
import { createPinia, defineStore } from 'pinia'

import { getAdminPinia } from './pinia.js'

export const ROLE_PROFILES = {
  admin: {
    id: 1,
    name: '平台管理员',
    role: 'admin',
    dataScope: '全部课程',
    token: 'dev-admin-token',
    permissions: ['*'],
  },
  teacher: {
    id: 2,
    name: '示例教师',
    role: 'teacher',
    dataScope: '授权课程',
    token: 'dev-teacher-token',
    permissions: [
      'course:read',
      'material:read',
      'material:parse',
      'material:export',
      'kb:read',
      'kb:write',
      'kb:index',
      'kb:activate',
      'qa:read',
      'qa:log:read',
      'membership:read',
      'system:read',
    ],
  },
}

function cloneProfile(profile) {
  return {
    id: profile.id,
    name: profile.name,
    role: profile.role,
    dataScope: profile.dataScope,
    permissions: [...profile.permissions],
  }
}

export const useAuthStore = defineStore('auth', () => {
  const mutableState = reactive({
    currentUser: null,
    token: null,
    isAuthenticated: false,
  })

  const state = readonly(mutableState)
  const currentUser = computed(() => mutableState.currentUser)

  function loginAs(role) {
    const profile = ROLE_PROFILES[role]

    if (!profile) {
      throw new Error(`未知开发态身份：${role}`)
    }

    mutableState.currentUser = cloneProfile(profile)
    mutableState.token = profile.token
    mutableState.isAuthenticated = true
  }

  function logout() {
    mutableState.currentUser = null
    mutableState.token = null
    mutableState.isAuthenticated = false
  }

  function canAccess(requiredPermissions = []) {
    if (!requiredPermissions.length) {
      return true
    }

    const permissions = mutableState.currentUser?.permissions ?? []

    if (permissions.includes('*')) {
      return true
    }

    return requiredPermissions.every((permission) => permissions.includes(permission))
  }

  return {
    state,
    currentUser,
    loginAs,
    logout,
    canAccess,
  }
})

export function createAuthStore(pinia = createPinia()) {
  return useAuthStore(pinia)
}

export const authStore = useAuthStore(getAdminPinia())
```

- [ ] **Step 4: 调整路由守卫的 store 获取时机**

In `frontend/apps/admin-app/src/router/index.js`, replace the top-level auth import:

```javascript
import { authStore } from '../stores/auth.js'
```

with:

```javascript
import { getAdminPinia } from '../stores/pinia.js'
import { useAuthStore } from '../stores/auth.js'
```

Then update the guard to obtain the store inside `beforeEach`:

```javascript
router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }

  const authStore = useAuthStore(getAdminPinia())

  if (!authStore.state.isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (!authStore.canAccess(to.meta.permissions)) {
    return '/403'
  }

  return true
})
```

This keeps the compatibility singleton for existing components, while avoiding a router-level top import that captures auth state before app startup.

- [ ] **Step 5: 验证认证测试**

Run:

```bash
pnpm test -- --test-name-pattern="认证状态|认证 store"
```

Expected: PASS。

- [ ] **Step 6: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/stores/auth.js frontend/apps/admin-app/src/router/index.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "refactor(admin-app): migrate auth store to pinia"
```

### Task 2.3 · 迁移 theme store 并处理 purple -> violet

**Files:**
- Modify: `frontend/apps/admin-app/src/stores/theme.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
- Modify: `frontend/apps/admin-app/src/components/shell/ThemeControl.vue`

- [ ] **Step 1: 更新主题测试**

In `src/app-shell.test.js`, update theme imports:

```javascript
import { createPinia } from 'pinia'
import {
  THEME_ACCENTS,
  isValidAccent,
  normalizeAccent,
  resolveTheme,
  themeStore,
  useThemeStore,
} from './stores/theme.js'
```

Replace the theme accent test with:

```javascript
test('主题色只允许固定枚举并提供强色阶', () => {
  assert.deepEqual(
    THEME_ACCENTS.map((item) => item.key),
    ['indigo', 'blue', 'teal', 'violet', 'amber'],
  )
  assert.equal(isValidAccent('teal'), true)
  assert.equal(isValidAccent('custom'), false)
  assert.equal(normalizeAccent('purple'), 'violet')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'teal').strong, '#0f766e')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'amber').strong, '#b45309')
})
```

Add this test:

```javascript
test('主题 store 迁移到 Pinia 后保留旧兼容 API', () => {
  const pinia = createPinia()
  const theme = useThemeStore(pinia)

  assert.equal(theme.state.mode, 'auto')
  assert.equal(theme.state.accent, 'indigo')
  theme.setMode('dark')
  theme.setAccent('violet')

  assert.equal(theme.state.mode, 'dark')
  assert.equal(theme.state.accent, 'violet')
  assert.equal(themeStore.state.mode, 'auto')
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd frontend/apps/admin-app
pnpm test -- --test-name-pattern="主题色|主题 store"
```

Expected: FAIL，原因是当前仍使用 `purple` 且没有 Pinia store。

- [ ] **Step 3: 改造 `theme.js`**

Replace `frontend/apps/admin-app/src/stores/theme.js` with:

```javascript
import { computed, reactive, readonly } from 'vue'
import { createPinia, defineStore } from 'pinia'

import { getAdminPinia } from './pinia.js'

export const THEME_MODES = ['light', 'dark', 'auto']

export const THEME_ACCENTS = [
  { key: 'indigo', label: 'Indigo', color: '#6366f1', strong: '#4f46e5', contrast: '#ffffff' },
  { key: 'blue', label: 'Blue', color: '#2563eb', strong: '#1d4ed8', contrast: '#ffffff' },
  { key: 'teal', label: 'Teal', color: '#0d9488', strong: '#0f766e', contrast: '#ffffff' },
  { key: 'violet', label: 'Violet', color: '#9333ea', strong: '#7e22ce', contrast: '#ffffff' },
  { key: 'amber', label: 'Amber', color: '#d97706', strong: '#b45309', contrast: '#ffffff' },
]

const STORAGE_KEY = 'ckqa-admin-theme'
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

export function isValidMode(mode) {
  return THEME_MODES.includes(mode)
}

export function normalizeAccent(accent) {
  if (accent === 'purple') return 'violet'
  return accent
}

export function isValidAccent(accent) {
  return THEME_ACCENTS.some((item) => item.key === normalizeAccent(accent))
}

export function resolveTheme(mode, prefersDark) {
  if (mode === 'dark') return 'dark'
  if (mode === 'light') return 'light'
  return prefersDark ? 'dark' : 'light'
}

function getMediaQuery() {
  if (!isBrowser || !window.matchMedia) return null
  return window.matchMedia('(prefers-color-scheme: dark)')
}

export const useThemeStore = defineStore('theme', () => {
  const mutableState = reactive({
    mode: 'auto',
    accent: 'indigo',
    resolvedTheme: 'light',
  })

  const state = readonly(mutableState)
  const activeAccent = computed(() => THEME_ACCENTS.find((item) => item.key === mutableState.accent))

  let mediaQuery = null

  function save() {
    if (!isBrowser) return
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode: mutableState.mode, accent: mutableState.accent }))
  }

  function load() {
    if (!isBrowser) return
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
      if (isValidMode(saved.mode)) mutableState.mode = saved.mode
      const savedAccent = normalizeAccent(saved.accent)
      if (isValidAccent(savedAccent)) mutableState.accent = savedAccent
    } catch {
      mutableState.mode = 'auto'
      mutableState.accent = 'indigo'
    }
  }

  function syncDocumentTheme() {
    mediaQuery = mediaQuery ?? getMediaQuery()
    mutableState.resolvedTheme = resolveTheme(mutableState.mode, Boolean(mediaQuery?.matches))
    if (!isBrowser) return
    document.documentElement.setAttribute('data-theme', mutableState.resolvedTheme)
    document.documentElement.setAttribute('data-accent', mutableState.accent)
  }

  function setMode(mode) {
    if (!isValidMode(mode)) return
    mutableState.mode = mode
    save()
    syncDocumentTheme()
  }

  function setAccent(accent) {
    const normalizedAccent = normalizeAccent(accent)
    if (!isValidAccent(normalizedAccent)) return
    mutableState.accent = normalizedAccent
    save()
    syncDocumentTheme()
  }

  function initTheme() {
    load()
    syncDocumentTheme()
    mediaQuery = mediaQuery ?? getMediaQuery()
    mediaQuery?.addEventListener?.('change', syncDocumentTheme)
  }

  return {
    state,
    activeAccent,
    init: initTheme,
    initTheme,
    setMode,
    setAccent,
    syncDocumentTheme,
  }
})

export function createThemeStore(pinia = createPinia()) {
  return useThemeStore(pinia)
}

export const themeStore = useThemeStore(getAdminPinia())
```

- [ ] **Step 4: 更新 `ThemeControl.vue` 的 store 获取方式**

In `frontend/apps/admin-app/src/components/shell/ThemeControl.vue`, replace:

```javascript
import { THEME_ACCENTS, themeStore } from '../../stores/theme.js'
```

with:

```javascript
import { getAdminPinia } from '../../stores/pinia.js'
import { THEME_ACCENTS, useThemeStore } from '../../stores/theme.js'

const themeStore = useThemeStore(getAdminPinia())
```

The template can stay because `themeStore.state.mode` and `themeStore.state.accent` remain available. The compatibility `themeStore` export remains for tests and legacy modules, but Vue components should prefer `useThemeStore(getAdminPinia())`.

- [ ] **Step 5: 确认没有路由层主题 store 顶层依赖**

Run:

```bash
rg -n "themeStore|useThemeStore" frontend/apps/admin-app/src/router frontend/apps/admin-app/src/main.js frontend/apps/admin-app/src/components/shell/ThemeControl.vue
```

Expected: router 目录无主题 store 命中；`main.js` 在 Task 2.4 中会使用 `useThemeStore(pinia)`；`ThemeControl.vue` 使用 `useThemeStore(getAdminPinia())`。

- [ ] **Step 6: 验证主题测试**

Run:

```bash
pnpm test -- --test-name-pattern="主题"
```

Expected: PASS。

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/stores/theme.js frontend/apps/admin-app/src/components/shell/ThemeControl.vue frontend/apps/admin-app/src/app-shell.test.js
git commit -m "refactor(admin-app): migrate theme store to pinia"
```

### Task 2.4 · main.js 注册共享 Pinia

**Files:**
- Modify: `frontend/apps/admin-app/src/main.js`

- [ ] **Step 1: 修改启动入口**

Replace `frontend/apps/admin-app/src/main.js` with:

```javascript
import { createApp } from 'vue'
import './styles/index.scss'
import App from './App.vue'
import router from './router/index.js'
import { getAdminPinia } from './stores/pinia.js'
import { useThemeStore } from './stores/theme.js'

const app = createApp(App)
const pinia = getAdminPinia()

app.use(pinia)
const themeStore = useThemeStore(pinia)
themeStore.init()
app.use(router)
app.mount('#app')
```

This step will not build until Phase 3 creates `styles/index.scss`.

- [ ] **Step 2: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/main.js
git commit -m "refactor(admin-app): register pinia before app mount"
```

---

## Phase 3 · SCSS 分层迁移

### Task 3.1 · 创建 token 与 mixin 文件

**Files:**
- Create: `frontend/apps/admin-app/src/styles/tokens/_colors.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_typography.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_radius.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_shadow.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_space.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_motion.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_breakpoints.scss`
- Create: `frontend/apps/admin-app/src/styles/tokens/_z-index.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_focus.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_surface.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_status.scss`
- Create: `frontend/apps/admin-app/src/styles/mixins/_responsive.scss`

- [ ] **Step 1: 创建目录**

Run:

```bash
mkdir -p frontend/apps/admin-app/src/styles/tokens frontend/apps/admin-app/src/styles/mixins
```

- [ ] **Step 2: 写 `_colors.scss`**

Create `frontend/apps/admin-app/src/styles/tokens/_colors.scss`:

```scss
$ckqa-white: #ffffff;
$ckqa-bg-light: #f8fafc;
$ckqa-bg-dark: #020617;

:root {
  color-scheme: light;
  --ckqa-bg: #f8fafc;
  --ckqa-bg-elevated: #eef2ff;
  --ckqa-surface: #ffffff;
  --ckqa-surface-muted: #f1f5f9;
  --ckqa-surface-strong: #e7ebf1;
  --ckqa-border: #e2e8f0;
  --ckqa-border-subtle: #edf2f7;
  --ckqa-border-strong: #cbd5e1;
  --ckqa-text: #0f172a;
  --ckqa-text-muted: #64748b;
  --ckqa-text-weak: #94a3b8;
  --ckqa-ink: #0f172a;
  --ckqa-accent: #6366f1;
  --ckqa-accent-strong: #4f46e5;
  --ckqa-accent-contrast: #ffffff;
  --ckqa-success: #10b981;
  --ckqa-running: #3b82f6;
  --ckqa-warning: #f59e0b;
  --ckqa-blocked: #64748b;
  --ckqa-danger: #ef4444;
}

[data-theme='dark'] {
  color-scheme: dark;
  --ckqa-bg: #020617;
  --ckqa-bg-elevated: #0f172a;
  --ckqa-surface: #0f172a;
  --ckqa-surface-muted: #1e293b;
  --ckqa-surface-strong: #243449;
  --ckqa-border: #334155;
  --ckqa-border-subtle: #243044;
  --ckqa-border-strong: #475569;
  --ckqa-text: #f8fafc;
  --ckqa-text-muted: #cbd5e1;
  --ckqa-text-weak: #94a3b8;
  --ckqa-ink: #f8fafc;
}

[data-accent='indigo'] { --ckqa-accent: #6366f1; --ckqa-accent-strong: #4f46e5; --ckqa-accent-contrast: #ffffff; }
[data-accent='blue'] { --ckqa-accent: #2563eb; --ckqa-accent-strong: #1d4ed8; --ckqa-accent-contrast: #ffffff; }
[data-accent='teal'] { --ckqa-accent: #0d9488; --ckqa-accent-strong: #0f766e; --ckqa-accent-contrast: #ffffff; }
[data-accent='violet'] { --ckqa-accent: #9333ea; --ckqa-accent-strong: #7e22ce; --ckqa-accent-contrast: #ffffff; }
[data-accent='amber'] { --ckqa-accent: #d97706; --ckqa-accent-strong: #b45309; --ckqa-accent-contrast: #ffffff; }
```

- [ ] **Step 3: 写其他 token 文件**

Create `frontend/apps/admin-app/src/styles/tokens/_typography.scss`:

```scss
$font-sans: 'DM Sans', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', system-ui, sans-serif;
$font-mono: 'DM Mono', 'JetBrains Mono', 'Cascadia Code', ui-monospace, monospace;

:root {
  --ckqa-font-sans: #{$font-sans};
  --ckqa-font-mono: #{$font-mono};
}
```

Create `frontend/apps/admin-app/src/styles/tokens/_radius.scss`:

```scss
$radius-sm: 6px;
$radius-md: 8px;
$radius-lg: 10px;
$radius-xl: 12px;
$radius-full: 999px;

:root {
  --ckqa-radius-sm: #{$radius-sm};
  --ckqa-radius-md: #{$radius-md};
  --ckqa-radius-lg: #{$radius-lg};
  --ckqa-radius-xl: #{$radius-xl};
  --ckqa-radius-full: #{$radius-full};
  --ckqa-radius: #{$radius-md};
  --ckqa-radius-pill: #{$radius-full};
}
```

Create `frontend/apps/admin-app/src/styles/tokens/_shadow.scss`:

```scss
$shadow-xs: 0 1px 2px rgba(15, 23, 42, 0.05);
$shadow-sm: 0 2px 8px rgba(15, 23, 42, 0.06);
$shadow-md: 0 8px 24px rgba(15, 23, 42, 0.08);
$shadow-lg: 0 16px 48px rgba(15, 23, 42, 0.12);
```

Do not output shadow CSS custom properties in the first pass. `components.scss` and mixins should import these Sass variables directly; Element Plus shadow CSS variables can be added later if a real popup/drawer override requires them.

Create `frontend/apps/admin-app/src/styles/tokens/_space.scss`:

```scss
$space-1: 4px;
$space-2: 8px;
$space-3: 12px;
$space-4: 16px;
$space-5: 20px;
$space-6: 24px;
$space-8: 32px;
$space-10: 40px;
$space-12: 48px;
```

Create `frontend/apps/admin-app/src/styles/tokens/_motion.scss`:

```scss
$duration-instant: 100ms;
$duration-fast: 160ms;
$duration-base: 220ms;
$duration-slow: 360ms;

$ease-out: cubic-bezier(0.22, 1, 0.36, 1);
$ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);

:root {
  --ckqa-duration-fast: #{$duration-fast};
  --ckqa-duration-base: #{$duration-base};
}
```

Create `frontend/apps/admin-app/src/styles/tokens/_breakpoints.scss`:

```scss
$breakpoint-sm: 640px;
$breakpoint-md: 768px;
$breakpoint-lg: 1024px;
$breakpoint-xl: 1280px;
```

Create `frontend/apps/admin-app/src/styles/tokens/_z-index.scss`:

```scss
$z-sidebar: 20;
$z-topbar: 30;
$z-overlay: 1000;
$z-message: 2000;
```

- [ ] **Step 4: 写 mixin 文件**

Create `frontend/apps/admin-app/src/styles/mixins/_focus.scss`:

```scss
@mixin focus-ring {
  outline: 3px solid var(--ckqa-accent);
  outline-offset: 2px;

  @supports (color: color-mix(in srgb, white, black)) {
    outline-color: color-mix(in srgb, var(--ckqa-accent-strong) 70%, white);
  }
}
```

Create `frontend/apps/admin-app/src/styles/mixins/_surface.scss`:

```scss
@use '../tokens/shadow' as *;

@mixin panel-surface {
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  box-shadow: $shadow-md;
}
```

Create `frontend/apps/admin-app/src/styles/mixins/_status.scss`:

```scss
@mixin status-soft($color) {
  color: $color;
  border-color: color-mix(in srgb, #{$color} 24%, transparent);
  background: color-mix(in srgb, #{$color} 8%, transparent);
}
```

Create `frontend/apps/admin-app/src/styles/mixins/_responsive.scss`:

```scss
@use '../tokens/breakpoints' as *;

@mixin below-lg {
  @media (max-width: #{$breakpoint-lg - 1px}) {
    @content;
  }
}

@mixin below-md {
  @media (max-width: #{$breakpoint-md - 1px}) {
    @content;
  }
}
```

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/styles/tokens frontend/apps/admin-app/src/styles/mixins
git commit -m "style(admin-app): add sass tokens and mixins"
```

### Task 3.2 · 迁移全局入口、base、components

**Files:**
- Create: `frontend/apps/admin-app/src/styles/index.scss`
- Create: `frontend/apps/admin-app/src/styles/fonts.scss`
- Create: `frontend/apps/admin-app/src/styles/base.scss`
- Create: `frontend/apps/admin-app/src/styles/components.scss`
- Delete: `frontend/apps/admin-app/src/style.css`
- Delete: `frontend/apps/admin-app/src/styles/base.css`
- Delete: `frontend/apps/admin-app/src/styles/components.css`
- Delete: `frontend/apps/admin-app/src/styles/tokens.css`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写 SCSS 入口文件**

Create `frontend/apps/admin-app/src/styles/index.scss`:

```scss
@use './fonts';
@use './tokens/colors';
@use './tokens/typography';
@use './tokens/radius';
@use './tokens/shadow';
@use './tokens/motion';
@use './base';
@use './components';
```

`index.scss` is the browser global style entry, not a shared Sass library. Do not add `@forward` here. If an SFC or another SCSS file needs a Sass variable, it should import the specific token file directly, for example `@use './tokens/shadow' as *` inside `components.scss`.

Create `frontend/apps/admin-app/src/styles/fonts.scss`:

```scss
@import '@fontsource/dm-sans/400.css';
@import '@fontsource/dm-sans/700.css';
@import '@fontsource/dm-sans/800.css';
@import '@fontsource/dm-mono/400.css';
```

- [ ] **Step 2: 迁移 base 样式**

Move the current reset and global rules from `styles/base.css` into `styles/base.scss`, replacing the low-motion block with:

```scss
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
```

Keep the existing scrollbar, focus, heading, body, `#app`, and `.app-layout` rules unchanged except for these token replacements:

```scss
var(--ckqa-radius-pill) -> var(--ckqa-radius-full)
var(--ckqa-radius) -> var(--ckqa-radius-md)
```

- [ ] **Step 3: 迁移 components 样式**

Move the complete contents of `frontend/apps/admin-app/src/styles/components.css` into `frontend/apps/admin-app/src/styles/components.scss`. Make these targeted replacements:

Add this import at the top:

```scss
@use './tokens/shadow' as *;
```

```scss
border-radius: var(--ckqa-radius);
```

to:

```scss
border-radius: var(--ckqa-radius-md);
```

and:

```scss
border-radius: var(--ckqa-radius-pill);
```

to:

```scss
border-radius: var(--ckqa-radius-full);
```

Replace runtime shadow variables with Sass variables:

```scss
box-shadow: var(--ckqa-shadow-soft);
```

to:

```scss
box-shadow: $shadow-md;
```

and:

```scss
box-shadow: var(--ckqa-shadow-hover);
```

to:

```scss
box-shadow: $shadow-lg;
```

- [ ] **Step 4: 删除旧入口与旧 CSS 文件**

Run:

```bash
git rm frontend/apps/admin-app/src/style.css frontend/apps/admin-app/src/styles/tokens.css frontend/apps/admin-app/src/styles/base.css frontend/apps/admin-app/src/styles/components.css
```

- [ ] **Step 5: 补充样式入口测试**

Add to `frontend/apps/admin-app/src/app-shell.test.js`:

```javascript
test('全局样式入口迁移为 SCSS 并移除旧 CSS 入口', async () => {
  const fs = await import('node:fs')
  const path = await import('node:path')
  const root = path.resolve('src')

  assert.equal(fs.existsSync(path.join(root, 'style.css')), false)
  assert.equal(fs.existsSync(path.join(root, 'styles', 'index.scss')), true)
  assert.equal(fs.existsSync(path.join(root, 'styles', 'tokens.css')), false)
  assert.equal(fs.existsSync(path.join(root, 'styles', 'tokens', '_colors.scss')), true)
})
```

- [ ] **Step 6: 验证 Node 测试**

Run:

```bash
cd frontend/apps/admin-app
pnpm test -- --test-name-pattern="全局样式入口"
```

Expected: PASS。

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/styles frontend/apps/admin-app/src/app-shell.test.js frontend/apps/admin-app/src/main.js
git commit -m "style(admin-app): migrate global styles to sass entry"
```

---

## Phase 4 · Element Plus 主题映射与首个控件替换

### Task 4.1 · 创建 Element Plus 主题覆盖

**Files:**
- Create: `frontend/apps/admin-app/src/styles/element-plus.scss`
- Modify: `frontend/apps/admin-app/src/styles/index.scss`

- [ ] **Step 1: 写 Element Plus 映射**

Create `frontend/apps/admin-app/src/styles/element-plus.scss`:

```scss
:root {
  --el-color-primary: var(--ckqa-accent);
  --el-color-success: var(--ckqa-success);
  --el-color-warning: var(--ckqa-warning);
  --el-color-danger: var(--ckqa-danger);
  --el-color-info: var(--ckqa-running);
  --el-bg-color: var(--ckqa-surface);
  --el-bg-color-page: var(--ckqa-bg);
  --el-bg-color-overlay: var(--ckqa-surface);
  --el-text-color-primary: var(--ckqa-text);
  --el-text-color-regular: var(--ckqa-text);
  --el-text-color-secondary: var(--ckqa-text-muted);
  --el-border-color: var(--ckqa-border);
  --el-border-color-light: var(--ckqa-border-subtle);
  --el-border-radius-base: var(--ckqa-radius-md);
  --el-font-family: var(--ckqa-font-sans);
}

@supports (color: color-mix(in srgb, white, black)) {
  :root {
    --el-border-color-light: color-mix(in srgb, var(--ckqa-border) 70%, transparent);
  }
}

.el-button--primary {
  --el-button-bg-color: var(--ckqa-accent-strong);
  --el-button-border-color: var(--ckqa-accent-strong);
  --el-button-hover-bg-color: var(--ckqa-accent);
  --el-button-hover-border-color: var(--ckqa-accent);
  --el-button-text-color: var(--ckqa-accent-contrast);
}

.el-input__wrapper,
.el-select__wrapper {
  background: var(--ckqa-surface);
  box-shadow: 0 0 0 1px var(--ckqa-border) inset;
}

.el-input__wrapper.is-focus,
.el-select__wrapper.is-focused {
  box-shadow: 0 0 0 1px var(--ckqa-accent) inset;
}

@supports (color: color-mix(in srgb, white, black)) {
  .el-input__wrapper.is-focus,
  .el-select__wrapper.is-focused {
    box-shadow:
      0 0 0 1px var(--ckqa-accent) inset,
      0 0 0 3px color-mix(in srgb, var(--ckqa-accent) 18%, transparent);
  }
}

.el-popper,
.el-message,
.el-dialog,
.el-drawer {
  color: var(--ckqa-text);
}

.el-dialog,
.el-drawer {
  background: var(--ckqa-surface);
}
```

- [ ] **Step 2: 接入 SCSS 入口**

In `frontend/apps/admin-app/src/styles/index.scss`, add Element Plus overrides between `base` and `components`:

```scss
@use './base';
@use './element-plus';
@use './components';
```

- [ ] **Step 3: 验证构建**

Run:

```bash
cd frontend/apps/admin-app
pnpm build
```

Expected: PASS，Sass 编译不报错。

- [ ] **Step 4: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/styles/element-plus.scss frontend/apps/admin-app/src/styles/index.scss
git commit -m "style(admin-app): map ckqa tokens to element plus"
```

### Task 4.2 · LoginView 身份选择迁移为 el-select

**Files:**
- Modify: `frontend/apps/admin-app/src/views/auth/LoginView.vue`

- [ ] **Step 1: 修改模板中的身份选择控件**

Replace the native select block:

```vue
<select v-model="selectedRole">
  <option v-for="role in roleOptions" :key="role.value" :value="role.value">
    {{ role.label }}
  </option>
</select>
```

with:

```vue
<el-select v-model="selectedRole" class="login-role-select" aria-label="开发态身份">
  <el-option
    v-for="role in roleOptions"
    :key="role.value"
    :label="role.label"
    :value="role.value"
  />
</el-select>
```

Keep `submit()` unchanged:

```javascript
function submit() {
  authStore.loginAs(selectedRole.value)
  router.replace(route.query.redirect || '/app/dashboard')
}
```

- [ ] **Step 2: 补充样式**

Append to `frontend/apps/admin-app/src/styles/components.scss`:

```scss
.login-role-select {
  width: 100%;
}
```

- [ ] **Step 3: 验证测试与构建**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: PASS。

- [ ] **Step 4: 验证 el-select 样式进入构建产物**

Run:

```bash
find dist -name "*.css" | xargs grep -l "el-select" 2>/dev/null || find dist -name "*.js" | xargs grep -l "el-select" 2>/dev/null
```

Expected: CSS chunk 或 JS chunk 中至少一处包含 `el-select`，说明按需引入插件已经把 `el-select` 相关样式或组件代码打包进 `dist`。

- [ ] **Step 5: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/views/auth/LoginView.vue frontend/apps/admin-app/src/styles/components.scss
git commit -m "style(admin-app): use element plus select in login view"
```

---

## Phase 5 · 页面视觉低风险微调

### Task 5.1 · Shell、主题控件和通用类视觉收口

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`
- Read: `frontend/apps/admin-app/src/components/shell/AppTopbar.vue`
- Read: `frontend/apps/admin-app/src/components/shell/SideNavigation.vue`
- Read: `frontend/apps/admin-app/src/components/shell/ThemeControl.vue`

- [ ] **Step 1: 检查壳组件没有业务变更需求**

Run:

```bash
rg -n "authStore|themeStore|router|buildNavigationGroups|findActiveNavigationPath" frontend/apps/admin-app/src/components/shell
```

Expected: 只看到现有 store、router、导航模型调用；本任务不修改这些逻辑。

- [ ] **Step 2: 加强通用按钮与面板样式**

In `frontend/apps/admin-app/src/styles/components.scss`, ensure these rules exist:

```scss
.ckqa-button,
.primary-button {
  color: var(--ckqa-accent-contrast);
  background: var(--ckqa-accent-strong);
}

.primary-button:disabled,
.secondary-button:disabled,
.plain-button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
  transform: none;
  box-shadow: none;
}

.theme-button[aria-pressed='true'],
.swatch-button[aria-pressed='true'] {
  box-shadow:
    0 0 0 2px var(--ckqa-surface),
    0 0 0 4px var(--ckqa-accent);
}
```

- [ ] **Step 3: 验证构建**

Run:

```bash
cd frontend/apps/admin-app
pnpm build
```

Expected: PASS。

- [ ] **Step 4: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "style(admin-app): refine shell controls with sass tokens"
```

### Task 5.2 · 表格、状态、浮层暗色检查补强

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`
- Modify: `frontend/apps/admin-app/src/styles/element-plus.scss`

- [ ] **Step 1: 给过渡期表格和状态类补齐 token**

Ensure `components.scss` contains:

```scss
.status-badge {
  min-height: 26px;
  padding: 0 9px;
  font-size: 12px;
  font-weight: 800;
}

.data-table,
.table-shell {
  color: var(--ckqa-text);
}

.data-table thead,
.table-shell thead {
  background: var(--ckqa-surface-muted);
}
```

- [ ] **Step 2: 给 Element Plus 浮层补齐暗色变量**

Append to `element-plus.scss`:

```scss
.el-select__popper,
.el-dropdown__popper,
.el-picker__popper {
  --el-bg-color-overlay: var(--ckqa-surface);
  --el-border-color-light: var(--ckqa-border-subtle);
  color: var(--ckqa-text);
}
```

- [ ] **Step 3: 验证**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: PASS。

- [ ] **Step 4: Commit**

Run:

```bash
git add frontend/apps/admin-app/src/styles/components.scss frontend/apps/admin-app/src/styles/element-plus.scss
git commit -m "style(admin-app): refine table status and overlay styles"
```

---

## Phase 6 · 终验与浏览器检查

### Task 6.1 · 命令回归

**Files:**
- No code changes expected

- [ ] **Step 1: 运行 Node 测试**

Run:

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: PASS。

- [ ] **Step 2: 运行构建并记录 chunk 体积**

Run:

```bash
pnpm build
```

Expected: PASS。记录输出中的 JS/CSS chunk 体积到最终实施说明；如果 Element Plus 体积显著增大，确认 Vite 配置仍使用按需引入插件。

- [ ] **Step 3: 运行 Playwright 故障注入测试**

Run:

```bash
pnpm test:e2e
```

Expected: PASS。若本地缺少浏览器依赖，记录 Playwright 的原始错误，并至少保留 `pnpm test` 与 `pnpm build` 通过证据。

- [ ] **Step 4: 回归 student-app 构建**

Run:

```bash
cd ../../apps/student-app
pnpm build
```

Expected: PASS。This verifies the admin-app dependency changes did not disturb the sibling frontend app through workspace-level lockfile or package resolution.

### Task 6.2 · 本地浏览器验收

**Files:**
- No code changes expected unless visual bugs are found

- [ ] **Step 1: 启动开发服务器**

Run:

```bash
cd frontend/apps/admin-app
pnpm dev:local
```

Expected: Vite serves `http://127.0.0.1:5173/`。

- [ ] **Step 2: 检查关键页面**

Open:

```text
http://127.0.0.1:5173/login
http://127.0.0.1:5173/app/dashboard
http://127.0.0.1:5173/app/health
http://127.0.0.1:5173/app/courses
http://127.0.0.1:5173/app/knowledge-bases
```

Expected:

1. 亮色、暗色、跟随系统均可用。
2. `indigo / blue / teal / violet / amber` 主题色切换后，按钮、focus、侧栏激活态同步变化。
3. LoginView 的 `el-select` 可选择 `平台管理员` 和 `教师`，提交后仍进入对应页面。
4. 弹窗、下拉、消息提示暗色模式不出现白底。
5. 表格、按钮、标签、侧栏文字不溢出、不重叠。

- [ ] **Step 3: 停止开发服务器**

Stop the `pnpm dev:local` process with `Ctrl+C` after verification.

### Task 6.3 · 最终检查与提交

**Files:**
- All changed files

- [ ] **Step 1: 检查差异**

Run:

```bash
git status --short
git diff --check
git diff --stat
```

Expected: `git diff --check` 无输出；改动范围只在 `frontend/apps/admin-app/` 和本计划/设计稿。

- [ ] **Step 2: 处理剩余改动**

If `git status --short` shows files that belong to this task, run:

```bash
git add frontend/apps/admin-app
git commit -m "style(admin-app): implement element plus pinia sass style foundation"
```

Expected: 提交成功。If `git status --short` is empty, record that all phase commits are already clean and skip this commit.

- [ ] **Step 3: 输出实施结果**

Final implementation report must include:

1. 修改了哪些文件。
2. 每个文件的作用。
3. 如何运行：`pnpm dev:local`。
4. 如何验证：`pnpm test`、`pnpm build`、`pnpm test:e2e`、浏览器页面清单。
5. 当前遗留问题：未一次性替换所有自研组件；后续可评估 `DataTableShell`、创建表单、Dialog/Drawer 迁移。

---

## 自检清单

1. 设计稿 §5 依赖设计由 Task 1.1 覆盖。
2. 设计稿 §6 样式目录由 Task 3.1 和 Task 3.2 覆盖；旧 `src/style.css` 在 Task 3.2 Step 4 删除。
3. 设计稿 §7 Vite 与 `main.js` 顺序由 Task 1.2 和 Task 2.4 覆盖。
4. 设计稿 §8 Pinia 状态由 Task 2.1、2.2、2.3 覆盖。
5. 设计稿 §9 token 由 Task 3.1 覆盖，阴影只保留 Sass 内部变量。
6. 设计稿 §10 Element Plus 主题映射由 Task 4.1 覆盖。
7. 设计稿 §11 组件规范由 Task 4.2、5.1、5.2 覆盖。
8. 设计稿 §12 页面级边界通过“不改 loader / router / API / Playwright mock”执行约定约束。
9. 设计稿 §13 验收方式由 Phase 6 覆盖。
10. 设计稿 §14 风险处理通过每 Task 测试、构建、分阶段提交覆盖。
11. `student-app` 不在实施范围内，但通过 Task 6.1 Step 4 做构建回归，防止 workspace 依赖解析意外影响兄弟应用。
