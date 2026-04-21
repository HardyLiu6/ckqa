# 学员端前端视觉与页面组织重设计 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按设计稿 `docs/superpowers/specs/2026-04-21-student-app-ui-redesign-design.md`，把 `frontend/apps/student-app/` 落地页、首页、问答三页、课程四页重做视觉，补齐知识图谱、个人中心视觉壳，建立 design token + 三层 Layout + 公共组件库，不改 API / Store / 路由守卫架构。

**Architecture:** 新增 `styles/tokens/` 设计 token + `styles/mixins/` 模块色与毛玻璃 mixin，作为所有组件的单一样式源头。新增三层 Layout（Landing / Product / Module）由 `App.vue` 按 `route.meta.layout` 动态选择。新增 `components/common/`（GlassCard / GlowButton / ModuleTag / SkeletonBlock）、`components/landing/`（KnowledgeNodeCloud / PinScrollShowcase / MagneticButton / Tilt3DCard）、`components/module-nav/`（四个模块副导航）。视觉壳数据来自本地 `src/mock/`。

**Tech Stack:** Vue 3.5 + Vite 7 + Element Plus 2.11 + Pinia 3 + Vue Router 4.5 + Sass 1.93 + GSAP 3.14 + @fontsource（新增）· 测试 `node --test tests/*.test.js`

---

## 文件结构与职责

### 样式基础

- Create: `frontend/apps/student-app/src/styles/tokens/_colors.scss` · 模块主色阶 + 中性色阶 + 语义色。
- Create: `frontend/apps/student-app/src/styles/tokens/_typography.scss` · 字号 / 字重 / 行高 token。
- Create: `frontend/apps/student-app/src/styles/tokens/_radius.scss` · 圆角 6 档。
- Create: `frontend/apps/student-app/src/styles/tokens/_shadow.scss` · 阴影 4 档 + 模块 glow 函数。
- Create: `frontend/apps/student-app/src/styles/tokens/_glass.scss` · 毛玻璃 3 档（亮 + 深双套）。
- Create: `frontend/apps/student-app/src/styles/tokens/_space.scss` · 间距 4px 基准。
- Create: `frontend/apps/student-app/src/styles/tokens/_motion.scss` · 动效时长 + 曲线。
- Create: `frontend/apps/student-app/src/styles/tokens/_breakpoints.scss` · 响应式断点。
- Create: `frontend/apps/student-app/src/styles/mixins/_module-color.scss` · 按模块生成色变体。
- Create: `frontend/apps/student-app/src/styles/mixins/_glass.scss` · 毛玻璃快捷 mixin。
- Create: `frontend/apps/student-app/src/styles/fonts.scss` · 字体加载入口。
- Modify: `frontend/apps/student-app/src/styles/index.scss` · 汇总 import。
- Modify: `frontend/apps/student-app/src/main.js` · import 样式入口。

### 模块色与 Layout 协作

- Create: `frontend/apps/student-app/src/composables/useCurrentModule.js` · 根据当前路由解析所属模块及其主色。
- Create: `frontend/apps/student-app/src/layouts/LandingLayout.vue` · 纯透传壳，给深色落地页用。
- Create: `frontend/apps/student-app/src/layouts/ProductLayout.vue` · 顶栏 + 主内容。
- Create: `frontend/apps/student-app/src/layouts/ModuleLayout.vue` · 顶栏 + 左侧副导航 + 主内容。
- Modify: `frontend/apps/student-app/src/App.vue` · 按 `route.meta.layout` 动态渲染 Layout。
- Modify: `frontend/apps/student-app/src/router/routes.js` · 补齐 layout meta、新增视觉壳路由。
- Modify: `frontend/apps/student-app/tests/router-routes.test.js` · 更新 coming-soon 路由清单与 layout 断言。

### 公共组件

- Create: `frontend/apps/student-app/src/components/common/GlassCard.vue`
- Create: `frontend/apps/student-app/src/components/common/GlowButton.vue`
- Create: `frontend/apps/student-app/src/components/common/ModuleTag.vue`
- Create: `frontend/apps/student-app/src/components/common/SkeletonBlock.vue`

### 顶栏与模块副导航

- Modify: `frontend/apps/student-app/src/components/NavHeader.vue` · 重写为居中胶囊导航。
- Create: `frontend/apps/student-app/src/components/module-nav/CourseSideNav.vue`
- Create: `frontend/apps/student-app/src/components/module-nav/QASideNav.vue`
- Create: `frontend/apps/student-app/src/components/module-nav/KnowledgeSideNav.vue`
- Create: `frontend/apps/student-app/src/components/module-nav/UserSideNav.vue`

### 落地页动效组件

- Create: `frontend/apps/student-app/src/components/landing/KnowledgeNodeCloud.vue` · Canvas 节点云。
- Create: `frontend/apps/student-app/src/components/landing/PinScrollShowcase.vue` · GSAP pin-scroll 特性流。
- Create: `frontend/apps/student-app/src/components/landing/MagneticButton.vue` · 磁吸按钮 + 涟漪。
- Create: `frontend/apps/student-app/src/utils/magnetic.js` · 磁吸向量计算纯函数（可单测）。
- Create: `frontend/apps/student-app/src/components/landing/Tilt3DCard.vue` · 3D 倾斜卡。

### 页面重写

- Modify: `frontend/apps/student-app/src/views/layout/index.vue` · 落地页。
- Modify: `frontend/apps/student-app/src/views/index.vue` · 首页。
- Modify: `frontend/apps/student-app/src/views/qa/index.vue` · 问答 / 提问。
- Modify: `frontend/apps/student-app/src/views/qa/QAHistory.vue` · 问答 / 历史。
- Modify: `frontend/apps/student-app/src/views/qa/QADetail.vue` · 问答 / 详情。
- Modify: `frontend/apps/student-app/src/views/course/index.vue` · 课程 / 列表。
- Modify: `frontend/apps/student-app/src/views/course/CourseDetail.vue` · 课程 / 详情。
- Modify: `frontend/apps/student-app/src/views/course/CourseLearn.vue` · 课程 / 学习（深色例外）。
- Modify: `frontend/apps/student-app/src/views/course/MyCourse.vue` · 课程 / 我的。
- Modify: `frontend/apps/student-app/src/views/status/RouteState.vue` · 状态页。

### 视觉壳新页

- Create: `frontend/apps/student-app/src/views/knowledge/KnowledgeGraph.vue`
- Create: `frontend/apps/student-app/src/views/knowledge/KnowledgeSearch.vue`
- Create: `frontend/apps/student-app/src/views/user/UserProfile.vue`
- Create: `frontend/apps/student-app/src/views/user/UserSettings.vue`
- Create: `frontend/apps/student-app/src/views/user/UserNotification.vue`
- Create: `frontend/apps/student-app/src/views/user/UserFavorite.vue`
- Create: `frontend/apps/student-app/src/mock/knowledge.json`
- Create: `frontend/apps/student-app/src/mock/user.json`

### 测试

- Create: `frontend/apps/student-app/tests/module-resolver.test.js` · useCurrentModule 纯逻辑。
- Create: `frontend/apps/student-app/tests/magnetic.test.js` · 磁吸向量计算。

---

## 约定

1. **提交粒度**：每个 Task 结束都 commit。消息前缀：`feat(student-app):` / `style(student-app):` / `refactor(student-app):` / `test(student-app):`。
2. **分支**：可选，建议在 `.worktrees/student-app-redesign/` worktree 里做。
3. **验证方式**：每个 Task 结束前运行 `pnpm dev`（如果没启动）打开 `http://localhost:8080` 目测对应页面，或跑 `node --test tests/*.test.js`。
4. **Element Plus 保留**：所有组件继续基于 Element Plus，用 `:deep()` 做样式覆盖，不引入新 UI 库。
5. **中文注释**：复杂逻辑用中文注释，和仓库整体风格保持一致。
6. **跳过的路由**：学习社区、学习分析、登录注册仍指向 `RouteState`，本次不建实体文件。

---

## Phase 0 · 准备

### Task 0.1 · 安装字体依赖

**Files:**
- Modify: `frontend/apps/student-app/package.json`

- [ ] **Step 1: 安装 @fontsource 系列字体包**

```bash
cd frontend/apps/student-app
pnpm add @fontsource/manrope @fontsource/noto-sans-sc @fontsource/space-grotesk
```

预期：`package.json` 中 dependencies 新增三条字体依赖，lockfile 更新。

- [ ] **Step 2: 验证安装成功**

```bash
ls node_modules/@fontsource/manrope/files | head -5
ls node_modules/@fontsource/noto-sans-sc/files | head -5
ls node_modules/@fontsource/space-grotesk/files | head -5
```

预期：每个包下 `files/` 目录存在 woff2 文件。

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/package.json frontend/apps/student-app/pnpm-lock.yaml
git commit -m "feat(student-app): add fontsource packages for manrope / noto-sans-sc / space-grotesk"
```

---

## Phase 1 · Design Tokens & 样式基础

### Task 1.1 · 颜色 Token

**Files:**
- Create: `frontend/apps/student-app/src/styles/tokens/_colors.scss`

- [ ] **Step 1: 写颜色 token 文件**

```scss
// frontend/apps/student-app/src/styles/tokens/_colors.scss
// 颜色 token · 详见 docs/superpowers/specs/2026-04-21-student-app-ui-redesign-design.md §4.1-§4.3

// ========== 模块主色 · 每模块 3 档 ==========
// 命名：$color-{module}-{50|500|700}

// 首页 Indigo
$color-home-50: #eef2ff;
$color-home-500: #6366f1;
$color-home-700: #4338ca;

// 课程 Blue
$color-course-50: #eff6ff;
$color-course-500: #2563eb;
$color-course-700: #1d4ed8;

// 问答 Purple
$color-qa-50: #faf5ff;
$color-qa-500: #9333ea;
$color-qa-700: #7e22ce;

// 知识图谱 Teal
$color-knowledge-50: #f0fdfa;
$color-knowledge-500: #0d9488;
$color-knowledge-700: #0f766e;

// 学习社区 Orange
$color-community-50: #fff7ed;
$color-community-500: #ea580c;
$color-community-700: #c2410c;

// 学习分析 Pink
$color-analysis-50: #fdf2f8;
$color-analysis-500: #db2777;
$color-analysis-700: #be185d;

// 个人中心 中性 + 场景色（琥珀用于消息，柠檬用于收藏）
$color-user-500: #64748b;
$color-user-700: #334155;
$color-user-accent-amber: #f59e0b;
$color-user-accent-lemon: #eab308;

// ========== 中性色阶（Slate 家族） ==========
$color-white: #ffffff;
$color-neutral-50: #f8fafc;
$color-neutral-100: #f1f5f9;
$color-neutral-200: #e2e8f0;
$color-neutral-300: #cbd5e1;
$color-neutral-400: #94a3b8;
$color-neutral-500: #64748b;
$color-neutral-600: #475569;
$color-neutral-700: #334155;
$color-neutral-900: #0f172a;

// ========== 语义化映射 ==========
$text-primary: $color-neutral-900;
$text-secondary: $color-neutral-500;
$text-tertiary: $color-neutral-400;
$text-placeholder: $color-neutral-300;
$border-default: $color-neutral-200;
$border-subtle: $color-neutral-100;
$bg-page: $color-neutral-50;
$bg-card: $color-white;

// ========== 语义色（状态反馈） ==========
$color-success: #10b981;
$color-success-light: #34d399;
$color-warning: #f59e0b;
$color-warning-light: #fbbf24;
$color-error: #ef4444;
$color-error-light: #f87171;
$color-info: #3b82f6;
$color-info-light: #60a5fa;

// ========== CSS 自定义属性（用于运行时切换模块色） ==========
:root {
  --module-color-50: #{$color-home-50};
  --module-color-500: #{$color-home-500};
  --module-color-700: #{$color-home-700};
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/styles/tokens/_colors.scss
git commit -m "feat(student-app): add color tokens (module scales + neutral + semantic)"
```

---

### Task 1.2 · 字号 Token

**Files:**
- Create: `frontend/apps/student-app/src/styles/tokens/_typography.scss`

- [ ] **Step 1: 写字号 token**

```scss
// frontend/apps/student-app/src/styles/tokens/_typography.scss
// 字号尺度 · 详见设计稿 §4.4

// ========== 字体族 ==========
$font-sans: 'Manrope', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Roboto, Arial, sans-serif;
$font-display: 'Space Grotesk', 'Noto Sans SC', $font-sans;
$font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, 'SF Mono', Consolas, monospace;

// ========== 字号尺度 ==========
$font-size-display-xl: 32px;
$font-size-display-lg: 24px;
$font-size-title-md: 18px;
$font-size-title-sm: 15px;
$font-size-body: 14px;
$font-size-body-sm: 13px;
$font-size-caption: 12px;

// ========== 字重 ==========
$font-weight-regular: 400;
$font-weight-medium: 500;
$font-weight-semibold: 600;
$font-weight-bold: 700;
$font-weight-black: 800;

// ========== 行高 ==========
$line-height-tight: 1.2;
$line-height-heading: 1.3;
$line-height-snug: 1.4;
$line-height-base: 1.5;
$line-height-relaxed: 1.6;
$line-height-loose: 1.75;

// ========== 字间距 ==========
$letter-spacing-tight: -0.02em;
$letter-spacing-normal: 0;
$letter-spacing-wide: 0.05em;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/styles/tokens/_typography.scss
git commit -m "feat(student-app): add typography tokens"
```

---

### Task 1.3 · 圆角 / 阴影 / 毛玻璃 / 间距 Token

**Files:**
- Create: `frontend/apps/student-app/src/styles/tokens/_radius.scss`
- Create: `frontend/apps/student-app/src/styles/tokens/_shadow.scss`
- Create: `frontend/apps/student-app/src/styles/tokens/_glass.scss`
- Create: `frontend/apps/student-app/src/styles/tokens/_space.scss`

- [ ] **Step 1: 写 _radius.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_radius.scss
$radius-sm: 6px;
$radius-md: 8px;
$radius-lg: 10px;
$radius-xl: 12px;
$radius-2xl: 16px;
$radius-full: 999px;
```

- [ ] **Step 2: 写 _shadow.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_shadow.scss
// 阴影 · 详见设计稿 §4.6

$shadow-xs: 0 1px 2px rgba(15, 23, 42, 0.05);
$shadow-sm: 0 2px 8px rgba(15, 23, 42, 0.06);
$shadow-md: 0 8px 24px rgba(15, 23, 42, 0.08);
$shadow-lg: 0 16px 48px rgba(15, 23, 42, 0.12);

// 模块色荧光（用 rgba + color mix 不可用，改由 mixin 产出）
// 见 styles/mixins/_module-color.scss 的 glow-{module}
```

- [ ] **Step 3: 写 _glass.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_glass.scss
// 毛玻璃 · 详见设计稿 §4.7

// 亮色场景
$glass-light-bg: rgba(255, 255, 255, 0.5);
$glass-light-blur: 12px;
$glass-light-border: rgba(255, 255, 255, 0.6);

$glass-base-bg: rgba(255, 255, 255, 0.7);
$glass-base-blur: 20px;
$glass-base-border: rgba(255, 255, 255, 0.8);

$glass-strong-bg: rgba(255, 255, 255, 0.88);
$glass-strong-blur: 28px;
$glass-strong-border: rgba(255, 255, 255, 0.95);

// 深色场景（落地页、课程学习页）
$glass-dark-light-bg: rgba(15, 15, 26, 0.5);
$glass-dark-light-border: rgba(255, 255, 255, 0.08);

$glass-dark-base-bg: rgba(15, 15, 26, 0.7);
$glass-dark-base-border: rgba(255, 255, 255, 0.12);

$glass-dark-strong-bg: rgba(15, 15, 26, 0.88);
$glass-dark-strong-border: rgba(255, 255, 255, 0.15);
```

- [ ] **Step 4: 写 _space.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_space.scss
// 间距 · 基准 4px

$space-1: 4px;
$space-2: 8px;
$space-3: 12px;
$space-4: 16px;
$space-6: 24px;
$space-8: 32px;
$space-12: 48px;
$space-16: 64px;
$space-24: 96px;
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/student-app/src/styles/tokens/
git commit -m "feat(student-app): add radius/shadow/glass/space tokens"
```

---

### Task 1.4 · 动效 + 断点 Token

**Files:**
- Create: `frontend/apps/student-app/src/styles/tokens/_motion.scss`
- Create: `frontend/apps/student-app/src/styles/tokens/_breakpoints.scss`

- [ ] **Step 1: 写 _motion.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_motion.scss
// 动效 · 详见设计稿 §4.9

$duration-instant: 100ms;
$duration-fast: 200ms;
$duration-base: 300ms;
$duration-slow: 500ms;

$ease-out: cubic-bezier(0.22, 1, 0.36, 1);
$ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
$ease-snap: cubic-bezier(0.4, 0, 0.2, 1);
$ease-spring: cubic-bezier(0.34, 1.56, 0.64, 1);
```

- [ ] **Step 2: 写 _breakpoints.scss**

```scss
// frontend/apps/student-app/src/styles/tokens/_breakpoints.scss
// 响应式断点 · 详见设计稿 §4.10

$bp-desktop: 1440px;
$bp-laptop: 1024px;
$bp-tablet: 768px;

// 使用示例：
// @media (max-width: $bp-laptop) { ... }
// @media (max-width: $bp-tablet) { ... }
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/styles/tokens/_motion.scss frontend/apps/student-app/src/styles/tokens/_breakpoints.scss
git commit -m "feat(student-app): add motion + breakpoint tokens"
```

---

### Task 1.5 · 模块色 Mixin

**Files:**
- Create: `frontend/apps/student-app/src/styles/mixins/_module-color.scss`
- Create: `frontend/apps/student-app/src/styles/mixins/_glass.scss`

- [ ] **Step 1: 写 _module-color.scss**

```scss
// frontend/apps/student-app/src/styles/mixins/_module-color.scss
// 模块色 mixin · 一处改全局换色

@use '../tokens/colors' as *;

// 模块色 Map：名称 → {50, 500, 700}
$module-colors: (
  'home': ($color-home-50, $color-home-500, $color-home-700),
  'course': ($color-course-50, $color-course-500, $color-course-700),
  'qa': ($color-qa-50, $color-qa-500, $color-qa-700),
  'knowledge': ($color-knowledge-50, $color-knowledge-500, $color-knowledge-700),
  'community': ($color-community-50, $color-community-500, $color-community-700),
  'analysis': ($color-analysis-50, $color-analysis-500, $color-analysis-700),
);

// 根据模块名获取三档色
@function module-50($module) {
  @return nth(map-get($module-colors, $module), 1);
}

@function module-500($module) {
  @return nth(map-get($module-colors, $module), 2);
}

@function module-700($module) {
  @return nth(map-get($module-colors, $module), 3);
}

// 生成模块色 glow 阴影
@function module-glow($module, $alpha: 0.25) {
  $c: module-500($module);
  @return 0 8px 32px rgba(red($c), green($c), blue($c), $alpha);
}

// 一键输出"模块配色全套" CSS 变量（给组件局部作用域用）
@mixin module-vars($module) {
  --module-color-50: #{module-50($module)};
  --module-color-500: #{module-500($module)};
  --module-color-700: #{module-700($module)};
}
```

- [ ] **Step 2: 写 _glass.scss mixin**

```scss
// frontend/apps/student-app/src/styles/mixins/_glass.scss
// 毛玻璃 mixin

@use '../tokens/glass' as *;
@use '../tokens/radius' as *;

@mixin glass-light {
  background: $glass-light-bg;
  backdrop-filter: blur($glass-light-blur);
  -webkit-backdrop-filter: blur($glass-light-blur);
  border: 1px solid $glass-light-border;
}

@mixin glass-base {
  background: $glass-base-bg;
  backdrop-filter: blur($glass-base-blur);
  -webkit-backdrop-filter: blur($glass-base-blur);
  border: 1px solid $glass-base-border;
}

@mixin glass-strong {
  background: $glass-strong-bg;
  backdrop-filter: blur($glass-strong-blur);
  -webkit-backdrop-filter: blur($glass-strong-blur);
  border: 1px solid $glass-strong-border;
}

// 深色场景（落地页、课程学习页）
@mixin glass-dark-light {
  background: $glass-dark-light-bg;
  backdrop-filter: blur($glass-light-blur);
  -webkit-backdrop-filter: blur($glass-light-blur);
  border: 1px solid $glass-dark-light-border;
}

@mixin glass-dark-base {
  background: $glass-dark-base-bg;
  backdrop-filter: blur($glass-base-blur);
  -webkit-backdrop-filter: blur($glass-base-blur);
  border: 1px solid $glass-dark-base-border;
}

@mixin glass-dark-strong {
  background: $glass-dark-strong-bg;
  backdrop-filter: blur($glass-strong-blur);
  -webkit-backdrop-filter: blur($glass-strong-blur);
  border: 1px solid $glass-dark-strong-border;
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/styles/mixins/
git commit -m "feat(student-app): add module-color + glass mixins"
```

---

### Task 1.6 · 字体加载入口

**Files:**
- Create: `frontend/apps/student-app/src/styles/fonts.scss`

- [ ] **Step 1: 写字体加载**

```scss
// frontend/apps/student-app/src/styles/fonts.scss
// 字体加载 · 本地打包，避免依赖 Google CDN

// Manrope 主字体
@import '@fontsource/manrope/400.css';
@import '@fontsource/manrope/500.css';
@import '@fontsource/manrope/600.css';
@import '@fontsource/manrope/700.css';
@import '@fontsource/manrope/800.css';

// Noto Sans SC 中文主字体
@import '@fontsource/noto-sans-sc/400.css';
@import '@fontsource/noto-sans-sc/500.css';
@import '@fontsource/noto-sans-sc/700.css';
@import '@fontsource/noto-sans-sc/900.css';

// Space Grotesk 展示字体（仅落地页 Hero + 模块大标题）
@import '@fontsource/space-grotesk/400.css';
@import '@fontsource/space-grotesk/500.css';
@import '@fontsource/space-grotesk/600.css';
@import '@fontsource/space-grotesk/700.css';
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/styles/fonts.scss
git commit -m "feat(student-app): load manrope / noto-sans-sc / space-grotesk via fontsource"
```

---

### Task 1.7 · 重构 styles/index.scss + 挂载

**Files:**
- Modify: `frontend/apps/student-app/src/styles/index.scss`
- Modify: `frontend/apps/student-app/src/main.js`

- [ ] **Step 1: 查看现有 main.js 结构**

```bash
cat frontend/apps/student-app/src/main.js
```

- [ ] **Step 2: 重写 styles/index.scss**

完全覆盖原有 318 行内容为：

```scss
// frontend/apps/student-app/src/styles/index.scss
// 全站样式入口

// 字体加载
@use './fonts';

// 设计 token 聚合导出（给 Vue 组件 @use 时可快速引入）
@forward './tokens/colors';
@forward './tokens/typography';
@forward './tokens/radius';
@forward './tokens/shadow';
@forward './tokens/glass';
@forward './tokens/space';
@forward './tokens/motion';
@forward './tokens/breakpoints';

// ========== 全局重置 ========== 
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html {
  font-size: 16px;
  font-family: 'Manrope', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI',
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Roboto, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #0f172a;
  background: #f8fafc;
}

body {
  min-height: 100vh;
}

a {
  color: inherit;
  text-decoration: none;
}

ul, ol {
  list-style: none;
}

img {
  display: block;
  max-width: 100%;
}

// ========== 全局滚动条 ========== 
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.3);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.5);
}

// ========== 全局焦点环 ========== 
:focus-visible {
  outline: 2px solid var(--module-color-500, #6366f1);
  outline-offset: 2px;
}

// ========== prefers-reduced-motion ========== 
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

- [ ] **Step 3: 修改 main.js import 样式入口**

在 `frontend/apps/student-app/src/main.js` 顶部（Vue/Pinia/Router 之前）加入：

```js
import './styles/index.scss'
```

（如果原来已有 `import './assets/main.css'` 或类似 CSS import，替换为这行。）

- [ ] **Step 4: 跑 dev 验证样式加载**

```bash
cd frontend/apps/student-app
pnpm dev
```

浏览器打开 `http://localhost:8080`，目测：
- 页面字体变为 Manrope（英文）/ Noto Sans SC（中文）
- DevTools Network 面板能看到 woff2 字体文件加载成功

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/student-app/src/styles/index.scss frontend/apps/student-app/src/main.js
git commit -m "refactor(student-app): replace legacy index.scss with token-based foundation"
```

---

## Phase 2 · Layout 系统

### Task 2.1 · 写 useCurrentModule composable + 单元测试

**Files:**
- Create: `frontend/apps/student-app/src/composables/useCurrentModule.js`
- Create: `frontend/apps/student-app/tests/module-resolver.test.js`

- [ ] **Step 1: 先写测试（TDD）**

```js
// frontend/apps/student-app/tests/module-resolver.test.js
import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveModule, MODULE_COLORS } from '../src/composables/useCurrentModule.js'

test('根据路径前缀解析出模块 key', () => {
  assert.equal(resolveModule('/'), 'landing')
  assert.equal(resolveModule('/home'), 'home')
  assert.equal(resolveModule('/course/list'), 'course')
  assert.equal(resolveModule('/course/detail/3'), 'course')
  assert.equal(resolveModule('/qa/ask'), 'qa')
  assert.equal(resolveModule('/qa/history'), 'qa')
  assert.equal(resolveModule('/knowledge/graph'), 'knowledge')
  assert.equal(resolveModule('/community/discuss'), 'community')
  assert.equal(resolveModule('/analysis/wrong'), 'analysis')
  assert.equal(resolveModule('/user/profile'), 'user')
})

test('未知路径回退到 home', () => {
  assert.equal(resolveModule('/nonexistent'), 'home')
})

test('MODULE_COLORS 每个模块都有 50 / 500 / 700 三档', () => {
  const required = ['home', 'course', 'qa', 'knowledge', 'community', 'analysis']
  for (const key of required) {
    assert.ok(MODULE_COLORS[key], `缺 ${key}`)
    assert.ok(MODULE_COLORS[key][50], `${key} 缺 50`)
    assert.ok(MODULE_COLORS[key][500], `${key} 缺 500`)
    assert.ok(MODULE_COLORS[key][700], `${key} 缺 700`)
  }
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend/apps/student-app
node --test tests/module-resolver.test.js
```

预期：FAIL，提示模块找不到。

- [ ] **Step 3: 写 composable**

```js
// frontend/apps/student-app/src/composables/useCurrentModule.js
// 根据路由解析当前所属模块 + 提供模块色

import { computed } from 'vue'
import { useRoute } from 'vue-router'

// 模块 → 主色三档（和 styles/tokens/_colors.scss 保持严格一致）
export const MODULE_COLORS = {
  home: { 50: '#eef2ff', 500: '#6366f1', 700: '#4338ca' },
  course: { 50: '#eff6ff', 500: '#2563eb', 700: '#1d4ed8' },
  qa: { 50: '#faf5ff', 500: '#9333ea', 700: '#7e22ce' },
  knowledge: { 50: '#f0fdfa', 500: '#0d9488', 700: '#0f766e' },
  community: { 50: '#fff7ed', 500: '#ea580c', 700: '#c2410c' },
  analysis: { 50: '#fdf2f8', 500: '#db2777', 700: '#be185d' },
  // user 和 landing 使用中性灰，不列入主模块色
  user: { 50: '#f8fafc', 500: '#64748b', 700: '#334155' },
  landing: { 50: '#eef2ff', 500: '#6366f1', 700: '#4338ca' },
}

// 路径前缀 → 模块 key
const PREFIX_MAP = [
  ['/home', 'home'],
  ['/course', 'course'],
  ['/qa', 'qa'],
  ['/knowledge', 'knowledge'],
  ['/community', 'community'],
  ['/analysis', 'analysis'],
  ['/user', 'user'],
]

/**
 * 纯函数：根据路径返回模块 key
 * @param {string} path
 * @returns {'landing'|'home'|'course'|'qa'|'knowledge'|'community'|'analysis'|'user'}
 */
export function resolveModule(path) {
  if (path === '/' || path === '') return 'landing'
  for (const [prefix, key] of PREFIX_MAP) {
    if (path === prefix || path.startsWith(prefix + '/')) return key
  }
  return 'home'
}

/**
 * Composable：响应式拿到当前模块 key 与色卡
 */
export function useCurrentModule() {
  const route = useRoute()
  const moduleKey = computed(() => resolveModule(route.path))
  const colors = computed(() => MODULE_COLORS[moduleKey.value] || MODULE_COLORS.home)
  return { moduleKey, colors }
}
```

- [ ] **Step 4: 再跑测试确认通过**

```bash
node --test tests/module-resolver.test.js
```

预期：PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/student-app/src/composables/useCurrentModule.js frontend/apps/student-app/tests/module-resolver.test.js
git commit -m "feat(student-app): add useCurrentModule composable + tests"
```

---

### Task 2.2 · LandingLayout

**Files:**
- Create: `frontend/apps/student-app/src/layouts/LandingLayout.vue`

- [ ] **Step 1: 写纯透传壳**

```vue
<!-- frontend/apps/student-app/src/layouts/LandingLayout.vue -->
<!-- 深色落地页专用壳，不渲染顶栏，让落地页自己掌控首屏 -->
<script setup>
</script>

<template>
  <div class="landing-layout">
    <RouterView />
  </div>
</template>

<style scoped lang="scss">
.landing-layout {
  min-height: 100vh;
  background: #0f0f1a;
  color: #fff;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/layouts/LandingLayout.vue
git commit -m "feat(student-app): add LandingLayout shell"
```

---

### Task 2.3 · ProductLayout

**Files:**
- Create: `frontend/apps/student-app/src/layouts/ProductLayout.vue`

- [ ] **Step 1: 写 ProductLayout**

```vue
<!-- frontend/apps/student-app/src/layouts/ProductLayout.vue -->
<!-- 亮色业务页默认壳：顶栏 + 主内容 -->
<script setup>
import NavHeader from '@/components/NavHeader.vue'
</script>

<template>
  <div class="product-layout">
    <NavHeader />
    <main class="product-main">
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;

.product-layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.product-main {
  padding-top: 64px; // NavHeader 高度
  min-height: 100vh;
}

// 路由切换过渡
.page-enter-active,
.page-leave-active {
  transition: opacity $duration-base $ease-out, transform $duration-base $ease-out;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/layouts/ProductLayout.vue
git commit -m "feat(student-app): add ProductLayout with NavHeader + page transition"
```

---

### Task 2.4 · ModuleLayout

**Files:**
- Create: `frontend/apps/student-app/src/layouts/ModuleLayout.vue`

- [ ] **Step 1: 写 ModuleLayout**

```vue
<!-- frontend/apps/student-app/src/layouts/ModuleLayout.vue -->
<!-- 模块壳：顶栏 + 左侧副导航 + 主内容 -->
<!-- 副导航组件按 route.meta.moduleNav 动态装载 -->
<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import NavHeader from '@/components/NavHeader.vue'
import { useCurrentModule } from '@/composables/useCurrentModule'

const route = useRoute()
const { moduleKey, colors } = useCurrentModule()

// 按模块懒加载副导航
const sideNavMap = {
  course: defineAsyncComponent(() => import('@/components/module-nav/CourseSideNav.vue')),
  qa: defineAsyncComponent(() => import('@/components/module-nav/QASideNav.vue')),
  knowledge: defineAsyncComponent(() => import('@/components/module-nav/KnowledgeSideNav.vue')),
  user: defineAsyncComponent(() => import('@/components/module-nav/UserSideNav.vue')),
}

const SideNav = computed(() => sideNavMap[moduleKey.value] || null)

// 为主区注入模块色 CSS 变量
const moduleStyle = computed(() => ({
  '--module-color-50': colors.value[50],
  '--module-color-500': colors.value[500],
  '--module-color-700': colors.value[700],
}))
</script>

<template>
  <div class="module-layout" :style="moduleStyle">
    <NavHeader />
    <div class="module-body">
      <aside class="module-sidebar">
        <component :is="SideNav" v-if="SideNav" />
      </aside>
      <main class="module-main">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.module-layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fafc 0%, #ffffff 100%);
}

.module-body {
  display: flex;
  padding-top: 64px; // NavHeader
  min-height: 100vh;
}

.module-sidebar {
  width: 220px;
  flex-shrink: 0;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;

  @media (max-width: $bp-laptop) {
    width: 60px; // 只显示图标
  }

  @media (max-width: $bp-tablet) {
    // 改为抽屉，实际抽屉交互在 NavHeader 里触发
    display: none;
  }
}

.module-main {
  flex: 1;
  min-width: 0;
  padding: 24px 32px;

  @media (max-width: $bp-tablet) {
    padding: 16px;
  }
}

.page-enter-active,
.page-leave-active {
  transition: opacity $duration-base $ease-out, transform $duration-base $ease-out;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/layouts/ModuleLayout.vue
git commit -m "feat(student-app): add ModuleLayout with dynamic side nav + module color CSS vars"
```

---

### Task 2.5 · 重写 App.vue 动态选择 Layout

**Files:**
- Modify: `frontend/apps/student-app/src/App.vue`

- [ ] **Step 1: 全量替换 App.vue**

```vue
<!-- frontend/apps/student-app/src/App.vue -->
<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { userLoadingStore } from './stores'

const userLoading = userLoadingStore()
const route = useRoute()

// 按 route.meta.layout 选择外壳
const LandingLayout = defineAsyncComponent(() => import('@/layouts/LandingLayout.vue'))
const ProductLayout = defineAsyncComponent(() => import('@/layouts/ProductLayout.vue'))
const ModuleLayout = defineAsyncComponent(() => import('@/layouts/ModuleLayout.vue'))

const layoutComponent = computed(() => {
  const layout = route.meta.layout
  if (layout === 'landing') return LandingLayout
  if (layout === 'module') return ModuleLayout
  // 默认 product
  return ProductLayout
})
</script>

<template>
  <component :is="layoutComponent" v-loading.fullscreen.lock="userLoading.loading" />
</template>

<style>
/* 全局样式由 styles/index.scss 注入，这里保持干净 */
</style>
```

- [ ] **Step 2: Commit（此时 dev 会报找不到 NavHeader / SideNav，Phase 3-5 继续）**

```bash
git add frontend/apps/student-app/src/App.vue
git commit -m "refactor(student-app): App.vue dynamically selects layout by route.meta.layout"
```

---

### Task 2.6 · 补齐 routes.js 的 layout meta + 新壳路由

**Files:**
- Modify: `frontend/apps/student-app/src/router/routes.js`
- Modify: `frontend/apps/student-app/tests/router-routes.test.js`

- [ ] **Step 1: 修改 routes.js**

完整替换文件（文件原本用 `createComingSoonRoute` / `createSystemStateRoute` 助手，我们保留这两个助手并在里面追加 `layout` meta）：

```js
// frontend/apps/student-app/src/router/routes.js
// 路由表 · layout meta 决定壳层（landing / product / module）

const routeStateView = () => import('../views/status/RouteState.vue')

function createComingSoonDescription(title, section) {
  if (section) {
    return `${section}中的"${title}"仍在建设中，当前学生端原型暂未开放该页面。`
  }
  return `"${title}"仍在建设中，当前学生端原型暂未开放该页面。`
}

function createComingSoonRoute({
  path, name, title, icon, hidden = false, noAuth = false, section = '',
  primaryActionTarget = '/home', primaryActionText = '返回首页',
}) {
  return {
    path,
    name,
    component: routeStateView,
    meta: {
      title, icon, hidden, noAuth,
      layout: 'product', // coming-soon 页复用 product 壳（顶栏 + 主区）
      routeState: 'coming-soon',
      routeStateLabel: '未开放',
      stateTitle: `${title}暂未开放`,
      stateDescription: createComingSoonDescription(title, section),
      primaryActionTarget, primaryActionText,
    },
  }
}

function createSystemStateRoute({
  path, name, title, routeState, stateTitle, stateDescription,
}) {
  return {
    path, name,
    component: routeStateView,
    meta: {
      title, noAuth: true,
      layout: 'product',
      routeState, stateTitle, stateDescription,
      primaryActionTarget: '/', primaryActionText: '返回介绍页',
    },
  }
}

export const routes = [
  {
    path: '/',
    name: 'Intro',
    component: () => import('../views/layout/index.vue'),
    meta: { title: '介绍', icon: 'House', noAuth: true, layout: 'landing' },
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/index.vue'),
    meta: { title: '首页', icon: 'House', keepAlive: true, layout: 'product' },
  },
  {
    path: '/qa',
    name: 'QA',
    redirect: '/qa/ask',
    meta: { title: '智能问答', icon: 'ChatDotRound' },
  },
  {
    path: '/qa/ask',
    name: 'QAAsk',
    component: () => import('../views/qa/index.vue'),
    meta: { title: '提问', icon: 'Edit', layout: 'module' },
  },
  {
    path: '/qa/history',
    name: 'QAHistory',
    component: () => import('../views/qa/QAHistory.vue'),
    meta: { title: '问答记录', icon: 'Clock', layout: 'module' },
  },
  {
    path: '/qa/detail/:id',
    name: 'QADetail',
    component: () => import('../views/qa/QADetail.vue'),
    meta: { title: '问题详情', hidden: true, layout: 'module' },
  },
  {
    path: '/course',
    name: 'Course',
    redirect: '/course/list',
    meta: { title: '课程中心', icon: 'Reading' },
  },
  {
    path: '/course/list',
    name: 'CourseList',
    component: () => import('../views/course/index.vue'),
    meta: { title: '课程列表', icon: 'Files', layout: 'module' },
  },
  {
    path: '/course/detail/:id',
    name: 'CourseDetail',
    component: () => import('../views/course/CourseDetail.vue'),
    meta: { title: '课程详情', hidden: true, layout: 'module' },
  },
  {
    path: '/course/learn/:id',
    name: 'CourseLearn',
    component: () => import('../views/course/CourseLearn.vue'),
    meta: { title: '课程学习', hidden: true, layout: 'product' }, // 深色页自带侧栏，不用 ModuleLayout
  },
  {
    path: '/course/my',
    name: 'MyCourse',
    component: () => import('../views/course/MyCourse.vue'),
    meta: { title: '我的课程', icon: 'Collection', layout: 'module' },
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    redirect: '/knowledge/graph',
    meta: { title: '知识图谱', icon: 'Share' },
  },
  {
    path: '/knowledge/graph',
    name: 'KnowledgeGraph',
    component: () => import('../views/knowledge/KnowledgeGraph.vue'),
    meta: { title: '图谱浏览', icon: 'Connection', layout: 'module' },
  },
  {
    path: '/knowledge/search',
    name: 'KnowledgeSearch',
    component: () => import('../views/knowledge/KnowledgeSearch.vue'),
    meta: { title: '知识检索', icon: 'Search', layout: 'module' },
  },
  createComingSoonRoute({
    path: '/knowledge/detail/:id',
    name: 'KnowledgeDetail',
    title: '知识点详情',
    hidden: true,
    section: '知识图谱',
  }),
  {
    path: '/community',
    name: 'Community',
    redirect: '/community/discuss',
    meta: { title: '学习社区', icon: 'UserFilled' },
  },
  createComingSoonRoute({ path: '/community/discuss', name: 'CommunityDiscuss', title: '讨论区', icon: 'ChatLineRound', section: '学习社区' }),
  createComingSoonRoute({ path: '/community/post/:id', name: 'CommunityPost', title: '帖子详情', hidden: true, section: '学习社区' }),
  createComingSoonRoute({ path: '/community/create', name: 'CommunityCreate', title: '发布讨论', icon: 'EditPen', section: '学习社区' }),
  createComingSoonRoute({ path: '/community/rank', name: 'CommunityRank', title: '排行榜', icon: 'Trophy', section: '学习社区' }),
  {
    path: '/analysis',
    name: 'Analysis',
    redirect: '/analysis/wrong',
    meta: { title: '学习分析', icon: 'DataAnalysis' },
  },
  createComingSoonRoute({ path: '/analysis/wrong', name: 'WrongAnalysis', title: '错题分析', icon: 'Warning', section: '学习分析' }),
  createComingSoonRoute({ path: '/analysis/report', name: 'LearningReport', title: '学习报告', icon: 'Document', section: '学习分析' }),
  createComingSoonRoute({ path: '/analysis/recommend', name: 'SmartRecommend', title: '智能推荐', icon: 'MagicStick', section: '学习分析' }),
  {
    path: '/user',
    name: 'User',
    redirect: '/user/profile',
    meta: { title: '个人中心', icon: 'User', hidden: true },
  },
  {
    path: '/user/profile',
    name: 'UserProfile',
    component: () => import('../views/user/UserProfile.vue'),
    meta: { title: '个人资料', icon: 'User', layout: 'module' },
  },
  {
    path: '/user/settings',
    name: 'UserSettings',
    component: () => import('../views/user/UserSettings.vue'),
    meta: { title: '账号设置', icon: 'Setting', layout: 'module' },
  },
  {
    path: '/user/notification',
    name: 'UserNotification',
    component: () => import('../views/user/UserNotification.vue'),
    meta: { title: '消息通知', icon: 'Bell', layout: 'module' },
  },
  {
    path: '/user/favorite',
    name: 'UserFavorite',
    component: () => import('../views/user/UserFavorite.vue'),
    meta: { title: '我的收藏', icon: 'Star', layout: 'module' },
  },
  createComingSoonRoute({ path: '/login', name: 'Login', title: '登录', noAuth: true, primaryActionTarget: '/', primaryActionText: '返回介绍页', section: '账号系统' }),
  createComingSoonRoute({ path: '/register', name: 'Register', title: '注册', noAuth: true, primaryActionTarget: '/', primaryActionText: '返回介绍页', section: '账号系统' }),
  createComingSoonRoute({ path: '/forgot-password', name: 'ForgotPassword', title: '忘记密码', noAuth: true, primaryActionTarget: '/', primaryActionText: '返回介绍页', section: '账号系统' }),
  createSystemStateRoute({ path: '/403', name: 'Forbidden', title: '无权限', routeState: '403', stateTitle: '暂无权限访问', stateDescription: '当前原型尚未接入完整鉴权流程，请返回可用页面继续浏览。' }),
  createSystemStateRoute({ path: '/404', name: 'NotFound', title: '页面不存在', routeState: '404', stateTitle: '页面不存在', stateDescription: '你访问的页面不存在，或者当前学生端原型尚未提供该地址对应的页面。' }),
  createSystemStateRoute({ path: '/500', name: 'ServerError', title: '服务器错误', routeState: '500', stateTitle: '页面暂时不可用', stateDescription: '当前页面暂时无法展示，请稍后重试，或先返回其他已开放页面继续浏览。' }),
  { path: '/:pathMatch(.*)*', redirect: '/404' },
]

export const whiteList = ['/', '/login', '/register', '/forgot-password', '/403', '/404', '/500']
```

- [ ] **Step 2: 更新 router-routes.test.js**

```js
// frontend/apps/student-app/tests/router-routes.test.js
import test from 'node:test'
import assert from 'node:assert/strict'

import { routes, whiteList } from '../src/router/routes.js'

const routeMap = new Map(routes.map((route) => [route.name, route]))

test('新增视觉壳路由已从 coming-soon 清单移除', () => {
  // 这些原本是 coming-soon，本次改成真实视觉壳
  const nowImplementedNames = [
    'KnowledgeGraph',
    'KnowledgeSearch',
    'UserProfile',
    'UserSettings',
    'UserNotification',
    'UserFavorite',
  ]
  for (const name of nowImplementedNames) {
    const route = routeMap.get(name)
    assert.ok(route, `路由 ${name} 不存在`)
    assert.notEqual(route.meta.routeState, 'coming-soon', `${name} 不应再是 coming-soon`)
    assert.equal(route.meta.layout, 'module', `${name} 应该使用 module layout`)
  }
})

test('剩余 coming-soon 路由仍显式标记', () => {
  const comingSoonNames = [
    'KnowledgeDetail',
    'CommunityDiscuss', 'CommunityPost', 'CommunityCreate', 'CommunityRank',
    'WrongAnalysis', 'LearningReport', 'SmartRecommend',
    'Login', 'Register', 'ForgotPassword',
  ]
  for (const routeName of comingSoonNames) {
    const route = routeMap.get(routeName)
    assert.ok(route, `${routeName} 路由不存在`)
    assert.equal(route.meta.routeState, 'coming-soon', `${routeName} 未标 coming-soon`)
  }
})

test('关键主路由都有 layout meta', () => {
  const cases = [
    ['Intro', 'landing'],
    ['Home', 'product'],
    ['QAAsk', 'module'],
    ['CourseList', 'module'],
    ['CourseLearn', 'product'], // 深色例外
    ['KnowledgeGraph', 'module'],
    ['UserProfile', 'module'],
  ]
  for (const [name, expected] of cases) {
    const route = routeMap.get(name)
    assert.ok(route, `${name} 不存在`)
    assert.equal(route.meta.layout, expected, `${name} layout 不等于 ${expected}`)
  }
})

test('whiteList 覆盖未登录可访问的基础路径', () => {
  assert.ok(whiteList.includes('/'))
  assert.ok(whiteList.includes('/login'))
  assert.ok(whiteList.includes('/404'))
})
```

- [ ] **Step 3: 跑测试**

```bash
cd frontend/apps/student-app
node --test tests/router-routes.test.js tests/module-resolver.test.js
```

预期：所有测试 PASS。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/student-app/src/router/routes.js frontend/apps/student-app/tests/router-routes.test.js
git commit -m "refactor(student-app): add layout meta + real routes for knowledge / user shells"
```

---

## Phase 3 · 公共组件

### Task 3.1 · GlassCard

**Files:**
- Create: `frontend/apps/student-app/src/components/common/GlassCard.vue`

- [ ] **Step 1: 写 GlassCard**

```vue
<!-- frontend/apps/student-app/src/components/common/GlassCard.vue -->
<!-- 通用毛玻璃卡片容器 · 支持三档档位 + 深色变体 + 模块色边框 -->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  tier: {
    type: String,
    default: 'base', // 'light' | 'base' | 'strong'
    validator: (v) => ['light', 'base', 'strong'].includes(v),
  },
  dark: { type: Boolean, default: false }, // 深色场景用
  padding: { type: String, default: 'md' }, // 'none' | 'sm' | 'md' | 'lg'
  hover: { type: Boolean, default: false }, // 是否开启 hover 抬升
})

const classes = computed(() => [
  'glass-card',
  `tier-${props.tier}`,
  `padding-${props.padding}`,
  { dark: props.dark, hoverable: props.hover },
])
</script>

<template>
  <div :class="classes">
    <slot />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/shadow' as *;
@use '@/styles/tokens/motion' as *;

.glass-card {
  border-radius: $radius-xl;
  transition: transform $duration-base $ease-out, box-shadow $duration-base $ease-out,
    border-color $duration-base $ease-out;
}

.tier-light { @include glass.glass-light; }
.tier-base { @include glass.glass-base; }
.tier-strong { @include glass.glass-strong; }

.dark.tier-light { @include glass.glass-dark-light; }
.dark.tier-base { @include glass.glass-dark-base; }
.dark.tier-strong { @include glass.glass-dark-strong; }

.padding-none { padding: 0; }
.padding-sm { padding: 12px; }
.padding-md { padding: 20px; }
.padding-lg { padding: 28px; }

.hoverable {
  cursor: pointer;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-md;
    border-color: rgba(var(--module-color-500-rgb, 99, 102, 241), 0.4);
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/common/GlassCard.vue
git commit -m "feat(student-app): add GlassCard common component"
```

---

### Task 3.2 · GlowButton

**Files:**
- Create: `frontend/apps/student-app/src/components/common/GlowButton.vue`

- [ ] **Step 1: 写 GlowButton**

```vue
<!-- frontend/apps/student-app/src/components/common/GlowButton.vue -->
<!-- 模块色荧光按钮 · 替代部分 el-button 主按钮场景 -->
<script setup>
const props = defineProps({
  variant: { type: String, default: 'primary' }, // 'primary' | 'secondary' | 'ghost'
  size: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg'
  block: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['click'])
</script>

<template>
  <button
    :class="['glow-btn', `variant-${variant}`, `size-${size}`, { block, disabled }]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot name="prefix" />
    <span class="btn-text"><slot /></span>
    <slot name="suffix" />
  </button>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.glow-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: $radius-lg;
  cursor: pointer;
  font-family: inherit;
  font-weight: 600;
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:active {
    transform: translateY(0) scale(0.98);
    transition-duration: $duration-instant;
  }

  &.disabled {
    opacity: 0.5;
    cursor: not-allowed;
    pointer-events: none;
  }

  &.block { width: 100%; }
}

.size-sm { height: 32px; padding: 0 14px; font-size: 12px; }
.size-md { height: 40px; padding: 0 20px; font-size: 14px; }
.size-lg { height: 48px; padding: 0 28px; font-size: 16px; }

.variant-primary {
  background: linear-gradient(135deg, var(--module-color-500, #6366f1), var(--module-color-700, #4338ca));
  color: #fff;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(99, 102, 241, 0.5), 0 0 0 3px rgba(99, 102, 241, 0.15);
  }
}

.variant-secondary {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  color: var(--module-color-500, #6366f1);
  border: 1px solid rgba(99, 102, 241, 0.3);

  &:hover {
    background: rgba(255, 255, 255, 0.9);
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.15);
  }
}

.variant-ghost {
  background: transparent;
  color: var(--module-color-500, #6366f1);

  &:hover {
    background: rgba(99, 102, 241, 0.08);
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/common/GlowButton.vue
git commit -m "feat(student-app): add GlowButton common component"
```

---

### Task 3.3 · ModuleTag

**Files:**
- Create: `frontend/apps/student-app/src/components/common/ModuleTag.vue`

- [ ] **Step 1: 写 ModuleTag**

```vue
<!-- frontend/apps/student-app/src/components/common/ModuleTag.vue -->
<!-- 按模块色渲染的 chip/tag -->
<script setup>
import { computed } from 'vue'
import { MODULE_COLORS } from '@/composables/useCurrentModule'

const props = defineProps({
  module: { type: String, default: 'home' },
  size: { type: String, default: 'md' }, // 'sm' | 'md'
  active: { type: Boolean, default: false },
})

const style = computed(() => {
  const c = MODULE_COLORS[props.module] || MODULE_COLORS.home
  return props.active
    ? {
        background: `linear-gradient(135deg, ${c[500]}, ${c[700]})`,
        color: '#fff',
        border: `1px solid ${c[500]}`,
      }
    : {
        background: c[50],
        color: c[500],
        border: `1px solid rgba(${hexToRgb(c[500])}, 0.25)`,
      }
})

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `${r}, ${g}, ${b}`
}
</script>

<template>
  <span :class="['module-tag', `size-${size}`]" :style="style">
    <slot />
  </span>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.module-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: $radius-full;
  font-weight: 500;
  white-space: nowrap;
}

.size-sm { font-size: 11px; padding: 2px 8px; }
.size-md { font-size: 12px; padding: 4px 12px; }
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/common/ModuleTag.vue
git commit -m "feat(student-app): add ModuleTag common component"
```

---

### Task 3.4 · SkeletonBlock

**Files:**
- Create: `frontend/apps/student-app/src/components/common/SkeletonBlock.vue`

- [ ] **Step 1: 写 SkeletonBlock**

```vue
<!-- frontend/apps/student-app/src/components/common/SkeletonBlock.vue -->
<!-- 骨架屏方块 · shimmer 动画 -->
<script setup>
defineProps({
  width: { type: String, default: '100%' },
  height: { type: String, default: '16px' },
  rounded: { type: String, default: 'md' }, // 'sm' | 'md' | 'lg' | 'full'
})
</script>

<template>
  <div class="skeleton-block" :class="`rounded-${rounded}`" :style="{ width, height }"></div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.skeleton-block {
  background: linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.rounded-sm { border-radius: $radius-sm; }
.rounded-md { border-radius: $radius-md; }
.rounded-lg { border-radius: $radius-lg; }
.rounded-full { border-radius: $radius-full; }

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/common/SkeletonBlock.vue
git commit -m "feat(student-app): add SkeletonBlock common component"
```

---

## Phase 4 · NavHeader 重构

### Task 4.1 · 重写 NavHeader（居中胶囊导航）

**Files:**
- Modify: `frontend/apps/student-app/src/components/NavHeader.vue`

- [ ] **Step 1: 全量覆写 NavHeader.vue**

```vue
<!-- frontend/apps/student-app/src/components/NavHeader.vue -->
<!-- 全站顶栏 · 居中胶囊导航 + 激活态荧光 · 详见设计稿 §5.2 -->
<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCurrentModule, MODULE_COLORS } from '@/composables/useCurrentModule'
import { Bell, ChatDotRound, Search } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const { moduleKey } = useCurrentModule()

const globalSearch = ref('')
const unreadCount = ref(3)
const isScrolled = ref(false)

// 顶栏主导航项
const modules = [
  { key: 'home', path: '/home', label: '首页' },
  { key: 'course', path: '/course', label: '课程' },
  { key: 'qa', path: '/qa', label: '问答' },
  { key: 'knowledge', path: '/knowledge', label: '图谱' },
  { key: 'community', path: '/community', label: '社区' },
  { key: 'analysis', path: '/analysis', label: '分析' },
]

const activeModule = computed(() => moduleKey.value)

// 当前激活模块的色卡
const activeColors = computed(() => MODULE_COLORS[activeModule.value] || MODULE_COLORS.home)

function isActive(key) {
  return activeModule.value === key
}

function itemStyle(key) {
  if (!isActive(key)) return {}
  const c = MODULE_COLORS[key] || MODULE_COLORS.home
  return {
    background: '#fff',
    color: c[500],
    boxShadow: `0 1px 3px rgba(${hexToRgb(c[500])}, 0.1), 0 0 0 1px rgba(${hexToRgb(c[500])}, 0.15), 0 0 16px rgba(${hexToRgb(c[500])}, 0.15)`,
  }
}

function hexToRgb(hex) {
  const h = hex.replace('#', '')
  return `${parseInt(h.slice(0, 2), 16)}, ${parseInt(h.slice(2, 4), 16)}, ${parseInt(h.slice(4, 6), 16)}`
}

function handleUserCommand(command) {
  switch (command) {
    case 'profile':
      router.push('/user/profile')
      break
    case 'settings':
      router.push('/user/settings')
      break
    case 'logout':
      userStore.logout()
      router.push('/login')
      break
  }
}

// 滚动监听：scrollY > 80 时背景收浓
function handleScroll() {
  isScrolled.value = window.scrollY > 80
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<template>
  <header class="nav-header" :class="{ scrolled: isScrolled }">
    <div class="nav-grid">
      <!-- 左：Logo -->
      <div class="nav-left">
        <RouterLink to="/home" class="logo-section">
          <div class="logo-icon">
            <el-icon :size="20"><ChatDotRound /></el-icon>
          </div>
          <span class="logo-text">智课问答</span>
        </RouterLink>
      </div>

      <!-- 中：模块胶囊导航 -->
      <nav class="nav-center">
        <RouterLink
          v-for="m in modules"
          :key="m.key"
          :to="m.path"
          class="nav-item"
          :class="{ active: isActive(m.key) }"
          :style="itemStyle(m.key)"
        >
          {{ m.label }}
        </RouterLink>
      </nav>

      <!-- 右：搜索 + 通知 + 头像 -->
      <div class="nav-right">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input v-model="globalSearch" placeholder="搜索课程、问题或知识点" />
          <kbd class="shortcut">⌘K</kbd>
        </div>

        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notify-badge">
          <button class="icon-btn" aria-label="通知">
            <el-icon :size="16"><Bell /></el-icon>
          </button>
        </el-badge>

        <el-dropdown trigger="click" @command="handleUserCommand">
          <div class="avatar">
            {{ userStore.user?.name?.charAt(0) || 'U' }}
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="settings">设置</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/shadow' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.nav-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  height: 64px;
  @include glass.glass-base;
  background: rgba(255, 255, 255, 0.8);
  border-left: 0;
  border-right: 0;
  border-top: 0;
  border-bottom-color: rgba(229, 231, 235, 0.6);
  transition: background $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &.scrolled {
    background: rgba(255, 255, 255, 0.95);
    box-shadow: $shadow-sm;
  }
}

.nav-grid {
  height: 100%;
  padding: 0 32px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  max-width: 1600px;
  margin: 0 auto;

  @media (max-width: $bp-laptop) {
    padding: 0 20px;
  }
}

.nav-left { justify-self: start; }
.nav-center { justify-self: center; }
.nav-right { justify-self: end; display: flex; align-items: center; gap: 10px; }

.logo-section {
  display: flex;
  align-items: center;
  gap: 10px;

  .logo-icon {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    border-radius: $radius-lg;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
  }

  .logo-text {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
}

.nav-center {
  display: flex;
  gap: 4px;
  background: rgba(248, 250, 252, 0.6);
  border: 1px solid rgba(229, 231, 235, 0.4);
  border-radius: $radius-full;
  padding: 4px;

  .nav-item {
    padding: 6px 14px;
    color: #64748b;
    font-size: 13px;
    font-weight: 500;
    border-radius: $radius-full;
    transition: color $duration-fast $ease-out, background $duration-fast $ease-out,
      box-shadow $duration-fast $ease-out;

    &:hover:not(.active) {
      color: #334155;
      background: rgba(255, 255, 255, 0.5);
    }

    &.active {
      font-weight: 600;
    }
  }

  @media (max-width: $bp-laptop) {
    .nav-item { padding: 6px 10px; font-size: 12px; }
  }
}

.nav-right {
  .search-box {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 7px 12px;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid #e5e7eb;
    border-radius: $radius-lg;
    min-width: 240px;
    transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

    .search-icon { color: #9ca3af; font-size: 14px; }

    input {
      flex: 1;
      border: 0;
      outline: 0;
      background: transparent;
      font-size: 13px;
      color: #0f172a;
      font-family: inherit;

      &::placeholder { color: #9ca3af; }
    }

    .shortcut {
      font-size: 10px;
      padding: 2px 6px;
      background: #f1f5f9;
      border-radius: $radius-sm;
      color: #64748b;
      font-family: 'JetBrains Mono', monospace;
    }

    &:focus-within {
      border-color: var(--module-color-500, #6366f1);
      box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.12);
    }

    @media (max-width: $bp-laptop) {
      min-width: 180px;
    }
  }

  .icon-btn {
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid #e5e7eb;
    border-radius: $radius-lg;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #64748b;
    transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

    &:hover {
      background: #fff;
      color: #0f172a;
    }
  }

  .avatar {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, #6366f1, #818cf8);
    border-radius: $radius-lg;
    color: #fff;
    font-weight: 700;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
    transition: transform $duration-fast $ease-out;

    &:hover { transform: scale(1.05); }
  }
}

// 小屏：隐藏搜索框，只保留搜索图标
@media (max-width: $bp-tablet) {
  .search-box { display: none; }
}
</style>
```

- [ ] **Step 2: 启动 dev 验证**

```bash
cd frontend/apps/student-app
pnpm dev
```

浏览器打开 `http://localhost:8080/home`，检查：
- 顶栏居中显示 Logo / 胶囊导航 / 工具区
- 默认激活"首页"（Indigo glow）
- 点击"课程"，文字颜色变成蓝色并有淡淡光晕
- 页面滚动 >80px，顶栏背景明显变白

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/components/NavHeader.vue
git commit -m "refactor(student-app): rebuild NavHeader with centered pill nav + module glow"
```

---

## Phase 5 · 模块副导航

### Task 5.1 · CourseSideNav

**Files:**
- Create: `frontend/apps/student-app/src/components/module-nav/CourseSideNav.vue`

- [ ] **Step 1: 写 CourseSideNav**

```vue
<!-- frontend/apps/student-app/src/components/module-nav/CourseSideNav.vue -->
<!-- 课程模块副导航 · 蓝色系 -->
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Files, Collection, Star, Document } from '@element-plus/icons-vue'

const route = useRoute()

const items = [
  { path: '/course/list', label: '全部课程', icon: Files },
  { path: '/course/my', label: '我的课程', icon: Collection },
  { path: '/course/favorite', label: '收藏课程', icon: Star, comingSoon: true },
  { path: '/course/report', label: '学习报告', icon: Document, comingSoon: true },
]

const activePath = computed(() => route.path)
</script>

<template>
  <nav class="side-nav course-side-nav">
    <div class="nav-label">课程</div>
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.comingSoon ? '/course/list' : item.path"
      class="nav-link"
      :class="{ active: activePath === item.path, disabled: item.comingSoon }"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <span v-if="item.comingSoon" class="tag-coming">未开放</span>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/typography' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-label {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 6px 10px 10px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $radius-md;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover:not(.disabled) {
    background: rgba(37, 99, 235, 0.05);
    color: #334155;
  }

  &.active {
    background: var(--module-color-50, #eff6ff);
    color: var(--module-color-500, #2563eb);
    font-weight: 600;
  }

  &.disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .tag-coming {
    margin-left: auto;
    font-size: 10px;
    padding: 1px 6px;
    background: #f1f5f9;
    border-radius: $radius-sm;
    color: #94a3b8;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/module-nav/CourseSideNav.vue
git commit -m "feat(student-app): add CourseSideNav"
```

---

### Task 5.2 · QASideNav

**Files:**
- Create: `frontend/apps/student-app/src/components/module-nav/QASideNav.vue`

- [ ] **Step 1: 写 QASideNav**

```vue
<!-- frontend/apps/student-app/src/components/module-nav/QASideNav.vue -->
<!-- 问答模块副导航 · 紫色系 · 顶部"新建对话"按钮 + 会话列表 -->
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Clock } from '@element-plus/icons-vue'

const router = useRouter()

// mock 会话列表
const sessions = ref([
  { id: 1, title: 'OS · 进程调度', messageCount: 5, lastTime: '10 分钟前', active: true },
  { id: 2, title: '死锁检测', messageCount: 3, lastTime: '昨天', active: false },
  { id: 3, title: '虚拟内存', messageCount: 8, lastTime: '3 天前', active: false },
])

function createNew() {
  router.push('/qa/ask')
}

function viewHistory() {
  router.push('/qa/history')
}

function selectSession(session) {
  sessions.value.forEach((s) => (s.active = s.id === session.id))
  router.push('/qa/ask')
}
</script>

<template>
  <nav class="side-nav qa-side-nav">
    <button class="btn-new" @click="createNew">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新建对话</span>
    </button>

    <div class="session-label">历史会话</div>
    <div class="session-list">
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.active }"
        @click="selectSession(session)"
      >
        <div class="session-title">{{ session.title }}</div>
        <div class="session-meta">{{ session.messageCount }} 条 · {{ session.lastTime }}</div>
      </div>
    </div>

    <button class="btn-history" @click="viewHistory">
      <el-icon :size="14"><Clock /></el-icon>
      查看全部历史
    </button>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-new {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: linear-gradient(135deg, #9333ea, #a855f7);
  color: #fff;
  border: 0;
  border-radius: $radius-lg;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(147, 51, 234, 0.35);
  transition: transform $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(147, 51, 234, 0.5);
  }
}

.session-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 8px 6px 4px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  padding: 8px 10px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.05); }

  &.active {
    background: rgba(147, 51, 234, 0.1);
    border: 1px solid rgba(147, 51, 234, 0.2);
    box-shadow: 0 0 12px rgba(147, 51, 234, 0.1);

    .session-title { color: #9333ea; font-weight: 600; }
  }

  .session-title {
    font-size: 12px;
    color: #334155;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .session-meta {
    font-size: 10px;
    color: #94a3b8;
    margin-top: 2px;
  }
}

.btn-history {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  background: transparent;
  color: #64748b;
  border: 1px solid transparent;
  border-radius: $radius-md;
  font-size: 12px;
  cursor: pointer;

  &:hover { background: rgba(147, 51, 234, 0.05); color: #9333ea; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/module-nav/QASideNav.vue
git commit -m "feat(student-app): add QASideNav"
```

---

### Task 5.3 · KnowledgeSideNav

**Files:**
- Create: `frontend/apps/student-app/src/components/module-nav/KnowledgeSideNav.vue`

- [ ] **Step 1: 写 KnowledgeSideNav**

```vue
<!-- frontend/apps/student-app/src/components/module-nav/KnowledgeSideNav.vue -->
<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Connection, Search } from '@element-plus/icons-vue'

const route = useRoute()

const items = [
  { path: '/knowledge/graph', label: '图谱浏览', icon: Connection },
  { path: '/knowledge/search', label: '知识检索', icon: Search },
]

const activePath = computed(() => route.path)

const subjects = ref([
  { id: 'os', label: 'OS', selected: true },
  { id: 'algo', label: '算法', selected: false },
  { id: 'ds', label: '数据结构', selected: false },
])

const legends = [
  { color: '#0d9488', label: '概念' },
  { color: '#2dd4bf', label: '实例' },
  { color: '#f59e0b', label: '错题' },
]

function toggleSubject(s) {
  s.selected = !s.selected
}
</script>

<template>
  <nav class="side-nav knowledge-side-nav">
    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="nav-link"
      :class="{ active: activePath === item.path }"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
    </RouterLink>

    <div class="section-label">学科</div>
    <div class="subjects">
      <button
        v-for="s in subjects"
        :key="s.id"
        class="subject-chip"
        :class="{ selected: s.selected }"
        @click="toggleSubject(s)"
      >{{ s.label }}</button>
    </div>

    <div class="section-label">关系</div>
    <div class="legends">
      <div v-for="l in legends" :key="l.label" class="legend-item">
        <span class="legend-dot" :style="{ background: l.color }"></span>
        <span>{{ l.label }}</span>
      </div>
    </div>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $radius-md;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover { background: rgba(13, 148, 136, 0.05); color: #334155; }

  &.active {
    background: rgba(13, 148, 136, 0.1);
    color: #0d9488;
    font-weight: 600;
  }
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 12px 10px 4px;
}

.subjects {
  display: flex;
  gap: 6px;
  padding: 0 4px;
  flex-wrap: wrap;

  .subject-chip {
    padding: 3px 10px;
    background: rgba(13, 148, 136, 0.06);
    border: 1px solid rgba(13, 148, 136, 0.15);
    color: #0d9488;
    font-size: 11px;
    border-radius: $radius-full;
    cursor: pointer;
    transition: background $duration-fast $ease-out, border-color $duration-fast $ease-out;

    &.selected {
      background: rgba(13, 148, 136, 0.15);
      border-color: rgba(13, 148, 136, 0.35);
      font-weight: 600;
    }
  }
}

.legends {
  padding: 0 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: #475569;
  }
  .legend-dot {
    width: 8px; height: 8px; border-radius: 50%;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/module-nav/KnowledgeSideNav.vue
git commit -m "feat(student-app): add KnowledgeSideNav"
```

---

### Task 5.4 · UserSideNav

**Files:**
- Create: `frontend/apps/student-app/src/components/module-nav/UserSideNav.vue`

- [ ] **Step 1: 写 UserSideNav**

```vue
<!-- frontend/apps/student-app/src/components/module-nav/UserSideNav.vue -->
<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, Setting, Bell, Star } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()

const items = [
  { path: '/user/profile', label: '个人资料', icon: User, accent: 'neutral' },
  { path: '/user/settings', label: '账号设置', icon: Setting, accent: 'neutral' },
  { path: '/user/notification', label: '消息通知', icon: Bell, accent: 'amber', badge: 12 },
  { path: '/user/favorite', label: '我的收藏', icon: Star, accent: 'lemon' },
]

const activePath = computed(() => route.path)

const profile = computed(() => ({
  name: userStore.user?.name || '刘俊达',
  meta: '计算机学院 · 大三',
}))
</script>

<template>
  <nav class="side-nav user-side-nav">
    <div class="profile-card">
      <div class="avatar"></div>
      <div>
        <div class="profile-name">{{ profile.name }}</div>
        <div class="profile-meta">{{ profile.meta }}</div>
      </div>
    </div>

    <RouterLink
      v-for="item in items"
      :key="item.path"
      :to="item.path"
      class="nav-link"
      :class="[{ active: activePath === item.path }, `accent-${item.accent}`]"
    >
      <el-icon :size="16"><component :is="item.icon" /></el-icon>
      <span>{{ item.label }}</span>
      <span v-if="item.badge" class="badge">{{ item.badge }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.side-nav {
  @include glass.glass-light;
  background: rgba(255, 255, 255, 0.6);
  border-top: 0;
  border-bottom: 0;
  border-left: 0;
  padding: 16px 12px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #e5e7eb;
  border-radius: $radius-xl;
  margin-bottom: 8px;

  .avatar {
    width: 36px;
    height: 36px;
    background: linear-gradient(135deg, #64748b, #94a3b8);
    border-radius: $radius-lg;
    flex-shrink: 0;
  }
  .profile-name {
    font-size: 13px;
    font-weight: 700;
    color: #0f172a;
  }
  .profile-meta {
    font-size: 11px;
    color: #64748b;
  }
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $radius-md;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &:hover { background: rgba(100, 116, 139, 0.05); color: #0f172a; }

  &.active {
    background: rgba(100, 116, 139, 0.1);
    color: #334155;
    font-weight: 600;
  }

  &.accent-amber .el-icon { color: #f59e0b; }
  &.accent-lemon .el-icon { color: #eab308; }

  .badge {
    margin-left: auto;
    font-size: 10px;
    padding: 1px 6px;
    background: #f59e0b;
    color: #fff;
    border-radius: $radius-full;
    font-weight: 700;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/module-nav/UserSideNav.vue
git commit -m "feat(student-app): add UserSideNav"
```

---

## Phase 6 · 落地页动效组件

### Task 6.1 · magnetic.js 纯函数 + 单测

**Files:**
- Create: `frontend/apps/student-app/src/utils/magnetic.js`
- Create: `frontend/apps/student-app/tests/magnetic.test.js`

- [ ] **Step 1: 先写测试**

```js
// frontend/apps/student-app/tests/magnetic.test.js
import test from 'node:test'
import assert from 'node:assert/strict'

import { computeMagneticOffset } from '../src/utils/magnetic.js'

test('鼠标在吸附半径外：返回 (0,0)', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 500, y: 500 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.deepEqual(offset, { x: 0, y: 0 })
})

test('鼠标在吸附半径内：向鼠标位移、最大 maxShift', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 150, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  // 距离 50 < 80，比例 50/80 = 0.625，x 向量 50，实际位移 (50/50)*8*0.625 = 5
  assert.ok(offset.x > 0 && offset.x <= 8)
  assert.equal(offset.y, 0)
})

test('鼠标刚好在中心：返回 (0,0)，不报除零错误', () => {
  const offset = computeMagneticOffset({
    cursor: { x: 100, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.deepEqual(offset, { x: 0, y: 0 })
})

test('位移永远不会超过 maxShift', () => {
  // 即便鼠标在吸附圆内边缘，也不超过上限
  const offset = computeMagneticOffset({
    cursor: { x: 100 + 79, y: 100 },
    center: { x: 100, y: 100 },
    radius: 80,
    maxShift: 8,
  })
  assert.ok(Math.abs(offset.x) <= 8)
  assert.ok(Math.abs(offset.y) <= 8)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd frontend/apps/student-app
node --test tests/magnetic.test.js
```

预期：FAIL，提示函数不存在。

- [ ] **Step 3: 写实现**

```js
// frontend/apps/student-app/src/utils/magnetic.js
/**
 * 计算磁吸按钮的位移向量
 * @param {object} opts
 * @param {{x: number, y: number}} opts.cursor 鼠标当前坐标
 * @param {{x: number, y: number}} opts.center 按钮中心坐标
 * @param {number} opts.radius 吸附半径（超过此距离不吸附）
 * @param {number} opts.maxShift 最大位移
 * @returns {{x: number, y: number}}
 */
export function computeMagneticOffset({ cursor, center, radius, maxShift }) {
  const dx = cursor.x - center.x
  const dy = cursor.y - center.y
  const distance = Math.sqrt(dx * dx + dy * dy)

  if (distance >= radius || distance === 0) {
    return { x: 0, y: 0 }
  }

  // 比例：越靠近中心吸附越强
  const strength = 1 - distance / radius
  // 单位向量 * 最大位移 * 强度
  const unitX = dx / distance
  const unitY = dy / distance

  return {
    x: unitX * maxShift * strength,
    y: unitY * maxShift * strength,
  }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
node --test tests/magnetic.test.js
```

预期：4 测试全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/student-app/src/utils/magnetic.js frontend/apps/student-app/tests/magnetic.test.js
git commit -m "feat(student-app): add magnetic offset pure function with tests"
```

---

### Task 6.2 · MagneticButton 组件

**Files:**
- Create: `frontend/apps/student-app/src/components/landing/MagneticButton.vue`

- [ ] **Step 1: 写 MagneticButton**

```vue
<!-- frontend/apps/student-app/src/components/landing/MagneticButton.vue -->
<!-- 磁吸按钮 + 点击涟漪 · 详见设计稿 §7.1 动效 ③ -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { computeMagneticOffset } from '@/utils/magnetic'

defineProps({
  variant: { type: String, default: 'primary' }, // 'primary' | 'secondary'
})

const emit = defineEmits(['click'])

const btnRef = ref(null)
const offsetX = ref(0)
const offsetY = ref(0)
const ripples = ref([])
let rippleId = 0

// prefers-reduced-motion 检测
const prefersReduced = ref(false)

function updateMotionPreference() {
  prefersReduced.value = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
}

function onMouseMove(e) {
  if (prefersReduced.value || !btnRef.value) return
  const rect = btnRef.value.getBoundingClientRect()
  const center = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 }
  const offset = computeMagneticOffset({
    cursor: { x: e.clientX, y: e.clientY },
    center,
    radius: 80, // 外扩吸附圆
    maxShift: 8,
  })
  offsetX.value = offset.x
  offsetY.value = offset.y
}

function onMouseLeave() {
  offsetX.value = 0
  offsetY.value = 0
}

function onClick(e) {
  if (!prefersReduced.value && btnRef.value) {
    const rect = btnRef.value.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const id = ++rippleId
    ripples.value.push({ id, x, y })
    setTimeout(() => {
      ripples.value = ripples.value.filter((r) => r.id !== id)
    }, 500)
  }
  emit('click', e)
}

onMounted(() => {
  updateMotionPreference()
  const mq = window.matchMedia?.('(prefers-reduced-motion: reduce)')
  mq?.addEventListener?.('change', updateMotionPreference)
  // 监听整个 window 的 mousemove 以实现"鼠标靠近就吸附"（而非要进入按钮本身才触发）
  window.addEventListener('mousemove', onMouseMove, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
})
</script>

<template>
  <button
    ref="btnRef"
    :class="['magnetic-btn', `variant-${variant}`]"
    :style="{ transform: `translate(${offsetX}px, ${offsetY}px)` }"
    @mouseleave="onMouseLeave"
    @click="onClick"
  >
    <slot />
    <span
      v-for="r in ripples"
      :key="r.id"
      class="ripple"
      :style="{ left: r.x + 'px', top: r.y + 'px' }"
    ></span>
  </button>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.magnetic-btn {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 28px;
  font-family: inherit;
  font-weight: 600;
  font-size: 15px;
  border: 0;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: transform $duration-base $ease-spring, box-shadow $duration-fast $ease-out;
}

.variant-primary {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #fff;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.4);

  &:hover {
    box-shadow: 0 12px 40px rgba(99, 102, 241, 0.55), 0 0 0 3px rgba(99, 102, 241, 0.2);
  }
}

.variant-secondary {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.15);

  &:hover {
    background: rgba(255, 255, 255, 0.12);
  }
}

.ripple {
  position: absolute;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: ripple-anim 500ms $ease-out forwards;
}

@keyframes ripple-anim {
  0% { width: 0; height: 0; opacity: 0.5; }
  100% { width: 300px; height: 300px; opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .magnetic-btn { transform: none !important; }
  .ripple { display: none; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/landing/MagneticButton.vue
git commit -m "feat(student-app): add MagneticButton with cursor-tracking + ripple"
```

---

### Task 6.3 · KnowledgeNodeCloud（Canvas 节点云）

**Files:**
- Create: `frontend/apps/student-app/src/components/landing/KnowledgeNodeCloud.vue`

- [ ] **Step 1: 写节点云**

```vue
<!-- frontend/apps/student-app/src/components/landing/KnowledgeNodeCloud.vue -->
<!-- Hero 背景动态知识节点云 · Canvas 绘制 · 详见设计稿 §7.1 动效 ① -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const canvasRef = ref(null)
let ctx = null
let rafId = null
let nodes = []
let edges = []
let mouseX = -9999
let mouseY = -9999
let isVisible = true
let visibilityObserver = null

const NODE_COUNT = 20
const MAX_EDGES = 40
const CURSOR_RADIUS = 120
const DRIFT_MIN = 0.3
const DRIFT_MAX = 0.6

function initNodes(width, height) {
  nodes = []
  for (let i = 0; i < NODE_COUNT; i++) {
    nodes.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * (DRIFT_MAX - DRIFT_MIN) * 2,
      vy: (Math.random() - 0.5) * (DRIFT_MAX - DRIFT_MIN) * 2,
      radius: 2 + Math.random() * 3,
      baseRadius: 2 + Math.random() * 3,
    })
  }
  buildEdges()
}

function buildEdges() {
  edges = []
  // 基于最近邻构造连线（每个节点和距离最近的 2 个连）
  for (let i = 0; i < nodes.length; i++) {
    const distances = []
    for (let j = 0; j < nodes.length; j++) {
      if (i === j) continue
      const dx = nodes[i].x - nodes[j].x
      const dy = nodes[i].y - nodes[j].y
      distances.push({ j, d: dx * dx + dy * dy })
    }
    distances.sort((a, b) => a.d - b.d)
    for (let k = 0; k < 2 && edges.length < MAX_EDGES; k++) {
      const pair = [i, distances[k].j].sort((a, b) => a - b).join('-')
      if (!edges.find((e) => e.key === pair)) {
        edges.push({ key: pair, a: i, b: distances[k].j })
      }
    }
  }
}

function distanceToCursor(node) {
  const dx = node.x - mouseX
  const dy = node.y - mouseY
  return Math.sqrt(dx * dx + dy * dy)
}

function step(width, height) {
  ctx.clearRect(0, 0, width, height)

  // 更新节点位置
  for (const n of nodes) {
    n.x += n.vx
    n.y += n.vy
    if (n.x < 0 || n.x > width) n.vx *= -1
    if (n.y < 0 || n.y > height) n.vy *= -1

    // 鼠标附近放大
    const d = distanceToCursor(n)
    const boost = d < CURSOR_RADIUS ? 1 + (1 - d / CURSOR_RADIUS) * 0.2 : 1
    n.radius = n.baseRadius * boost
  }

  // 画连线
  for (const e of edges) {
    const a = nodes[e.a]
    const b = nodes[e.b]
    const da = distanceToCursor(a)
    const db = distanceToCursor(b)
    const nearCursor = da < CURSOR_RADIUS || db < CURSOR_RADIUS
    ctx.strokeStyle = nearCursor ? 'rgba(165, 180, 252, 0.9)' : 'rgba(99, 102, 241, 0.3)'
    ctx.lineWidth = nearCursor ? 1.2 : 0.8
    ctx.beginPath()
    ctx.moveTo(a.x, a.y)
    ctx.lineTo(b.x, b.y)
    ctx.stroke()
  }

  // 画节点 + 光晕
  for (const n of nodes) {
    const d = distanceToCursor(n)
    const nearCursor = d < CURSOR_RADIUS
    const alpha = nearCursor ? 0.95 : 0.6
    const glow = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.radius * 6)
    glow.addColorStop(0, `rgba(165, 180, 252, ${alpha * 0.6})`)
    glow.addColorStop(1, 'rgba(99, 102, 241, 0)')
    ctx.fillStyle = glow
    ctx.beginPath()
    ctx.arc(n.x, n.y, n.radius * 6, 0, Math.PI * 2)
    ctx.fill()
    ctx.fillStyle = `rgba(196, 181, 253, ${alpha})`
    ctx.beginPath()
    ctx.arc(n.x, n.y, n.radius, 0, Math.PI * 2)
    ctx.fill()
  }
}

function loop() {
  if (!isVisible || !canvasRef.value) {
    rafId = requestAnimationFrame(loop)
    return
  }
  const canvas = canvasRef.value
  step(canvas.width, canvas.height)
  rafId = requestAnimationFrame(loop)
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  ctx.scale(dpr, dpr)
  // 重置节点以匹配新尺寸
  initNodes(rect.width, rect.height)
}

function onMouseMove(e) {
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  mouseX = e.clientX - rect.left
  mouseY = e.clientY - rect.top
}

onMounted(() => {
  const canvas = canvasRef.value
  ctx = canvas.getContext('2d')
  resize()
  window.addEventListener('resize', resize)
  canvas.addEventListener('mousemove', onMouseMove)

  // 视窗不可见时暂停
  visibilityObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => { isVisible = entry.isIntersecting })
  })
  visibilityObserver.observe(canvas)

  loop()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resize)
  canvasRef.value?.removeEventListener('mousemove', onMouseMove)
  visibilityObserver?.disconnect()
})
</script>

<template>
  <canvas ref="canvasRef" class="node-cloud" aria-hidden="true"></canvas>
</template>

<style scoped>
.node-cloud {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: auto;
}

@media (prefers-reduced-motion: reduce) {
  .node-cloud { opacity: 0.3; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/landing/KnowledgeNodeCloud.vue
git commit -m "feat(student-app): add KnowledgeNodeCloud canvas for landing hero"
```

---

### Task 6.4 · PinScrollShowcase（GSAP 钉滚特性流）

**Files:**
- Create: `frontend/apps/student-app/src/components/landing/PinScrollShowcase.vue`

- [ ] **Step 1: 写 PinScrollShowcase**

```vue
<!-- frontend/apps/student-app/src/components/landing/PinScrollShowcase.vue -->
<!-- 特性横向钉滚 · 详见设计稿 §7.1 动效 ② -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

gsap.registerPlugin(ScrollTrigger)

const wrapperRef = ref(null)
const trackRef = ref(null)
let trigger = null

const features = [
  { key: 'qa', title: '智能问答', subtitle: 'AI + 知识图谱，精准解答每一个课程问题', color: '#9333ea' },
  { key: 'kg', title: '知识图谱', subtitle: '可视化学科脉络，让知识连成网', color: '#0d9488' },
  { key: 'learn', title: '沉浸学习', subtitle: '边看边问，笔记与提问一体', color: '#2563eb' },
]

onMounted(() => {
  const prefersReduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (prefersReduced) return

  const wrapper = wrapperRef.value
  const track = trackRef.value
  if (!wrapper || !track) return

  // 横向滚动距离 = 轨道总宽 - 视窗宽
  const distance = () => track.scrollWidth - window.innerWidth

  trigger = gsap.to(track, {
    x: () => -distance(),
    ease: 'none',
    scrollTrigger: {
      trigger: wrapper,
      start: 'top top',
      end: () => `+=${distance()}`,
      scrub: 0.5,
      pin: true,
      anticipatePin: 1,
      invalidateOnRefresh: true,
    },
  })
})

onBeforeUnmount(() => {
  trigger?.scrollTrigger?.kill()
  trigger?.kill()
})
</script>

<template>
  <section id="showcase" ref="wrapperRef" class="showcase-wrapper">
    <div ref="trackRef" class="showcase-track">
      <div
        v-for="(f, i) in features"
        :key="f.key"
        class="showcase-panel"
        :style="{ '--accent': f.color }"
      >
        <div class="panel-inner">
          <div class="panel-index">0{{ i + 1 }}</div>
          <h2 class="panel-title">{{ f.title }}</h2>
          <p class="panel-subtitle">{{ f.subtitle }}</p>
          <div class="panel-glow" :style="{ background: `radial-gradient(circle, ${f.color} 0%, transparent 70%)` }"></div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.showcase-wrapper {
  height: 100vh;
  overflow: hidden;
  background: #0f0f1a;
  position: relative;
}

.showcase-track {
  display: flex;
  height: 100%;
  // 每张占一屏
  will-change: transform;
}

.showcase-panel {
  flex: 0 0 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  .panel-inner {
    position: relative;
    text-align: center;
    padding: 0 32px;
    max-width: 800px;
  }

  .panel-glow {
    position: absolute;
    inset: -100px;
    opacity: 0.25;
    filter: blur(60px);
    z-index: 0;
    pointer-events: none;
  }

  .panel-index {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 96px;
    font-weight: 700;
    color: var(--accent);
    opacity: 0.15;
    line-height: 1;
    margin-bottom: 16px;
    position: relative;
  }

  .panel-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 64px;
    font-weight: 700;
    color: #fff;
    margin-bottom: 16px;
    letter-spacing: -0.02em;
    position: relative;
  }

  .panel-subtitle {
    font-size: 18px;
    color: rgba(255, 255, 255, 0.65);
    position: relative;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/landing/PinScrollShowcase.vue
git commit -m "feat(student-app): add PinScrollShowcase with GSAP ScrollTrigger"
```

---

### Task 6.5 · Tilt3DCard

**Files:**
- Create: `frontend/apps/student-app/src/components/landing/Tilt3DCard.vue`

- [ ] **Step 1: 写 Tilt3DCard**

```vue
<!-- frontend/apps/student-app/src/components/landing/Tilt3DCard.vue -->
<!-- 鼠标跟踪 3D 倾斜 · 详见设计稿 §7.1 动效 ⑥ -->
<script setup>
import { ref, onMounted } from 'vue'

const cardRef = ref(null)
const rotX = ref(0)
const rotY = ref(0)

const MAX = 8
let isTouch = false

onMounted(() => {
  isTouch = matchMedia('(hover: none)').matches
})

function onMouseMove(e) {
  if (isTouch || !cardRef.value) return
  const rect = cardRef.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width // 0..1
  const y = (e.clientY - rect.top) / rect.height
  // y 越小（靠上），rotateX 越正（卡顶前倾）
  rotY.value = (x - 0.5) * 2 * MAX
  rotX.value = -(y - 0.5) * 2 * MAX
}

function onMouseLeave() {
  rotX.value = 0
  rotY.value = 0
}
</script>

<template>
  <div
    ref="cardRef"
    class="tilt-card"
    :style="{ transform: `perspective(1000px) rotateX(${rotX}deg) rotateY(${rotY}deg)` }"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
  >
    <slot />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/motion' as *;

.tilt-card {
  transition: transform $duration-base $ease-snap;
  transform-style: preserve-3d;
  will-change: transform;
}

@media (prefers-reduced-motion: reduce), (hover: none) {
  .tilt-card { transform: none !important; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/components/landing/Tilt3DCard.vue
git commit -m "feat(student-app): add Tilt3DCard with cursor-driven 3D rotation"
```

---

## Phase 7 · 落地页重写

### Task 7.1 · 重写 `views/layout/index.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/layout/index.vue`

> **范围约定**：本次重写保留原落地页的"Hero / 特性 / 数据统计 / 用户评价 / CTA / Footer"叙事结构，但把装饰层全部换成新的节点云 + Pin-scroll + 磁吸按钮 + 3D 倾斜卡。Hero 字体切 Space Grotesk，配色切 Indigo-Cyan。

- [ ] **Step 1: 全量覆写文件**

用以下内容完整替换原文件（2401 行原内容可参考但不保留）。文件较长，分两段：

```vue
<!-- frontend/apps/student-app/src/views/layout/index.vue -->
<!-- 落地页 · 深色 + 节点云 + Pin-scroll + 磁吸 · 详见设计稿 §6.1 -->
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import KnowledgeNodeCloud from '@/components/landing/KnowledgeNodeCloud.vue'
import PinScrollShowcase from '@/components/landing/PinScrollShowcase.vue'
import MagneticButton from '@/components/landing/MagneticButton.vue'
import Tilt3DCard from '@/components/landing/Tilt3DCard.vue'

const router = useRouter()
const isScrolled = ref(false)
const isMobileMenuOpen = ref(false)

function handleScroll() {
  isScrolled.value = window.scrollY > 60
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true })
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
})

function goToRegister() { router.push('/register') }
function goToLogin() { router.push('/login') }
function playDemo() { /* 预留：后续接入演示视频 */ }

const stats = [
  { num: '50K+', label: '活跃用户' },
  { num: '120+', label: '精品课程' },
  { num: '98%', label: '好评率' },
  { num: '24/7', label: 'AI 随问随答' },
]

const features = [
  { icon: '💬', title: '多轮 AI 问答', desc: '上下文记忆，像和讲师对话' },
  { icon: '🕸', title: '知识图谱', desc: '知识点自动连接，脉络一眼清' },
  { icon: '📚', title: '课程学习', desc: '沉浸式视频 + 笔记 + 问问' },
  { icon: '📊', title: '学习分析', desc: '错题 / 报告 / 推荐一体' },
]

const testimonials = [
  { name: '陈同学', role: '计算机 · 大三', quote: '第一次感觉课本里的知识点真的"串"起来了。' },
  { name: '王同学', role: '软件工程 · 大二', quote: 'AI 回答不止抄课本，还能结合图谱给出上下文。' },
  { name: '李同学', role: '数据科学 · 研一', quote: '期末复习阶段，错题推荐帮我定位到薄弱环节。' },
]
</script>

<template>
  <div class="landing-page">
    <!-- 顶栏 -->
    <nav class="navbar" :class="{ scrolled: isScrolled }">
      <div class="nav-container">
        <div class="logo">
          <div class="logo-icon">
            <svg viewBox="0 0 40 40" fill="none">
              <path d="M20 4L36 12V28L20 36L4 28V12L20 4Z" stroke="currentColor" stroke-width="2" fill="none" />
              <circle cx="20" cy="20" r="6" fill="currentColor" />
            </svg>
          </div>
          <span class="logo-text">智课问答</span>
        </div>
        <div class="nav-links">
          <a href="#features" class="nav-link">功能特性</a>
          <a href="#showcase" class="nav-link">产品展示</a>
          <a href="#stats" class="nav-link">数据统计</a>
          <a href="#testimonials" class="nav-link">用户评价</a>
        </div>
        <div class="nav-actions">
          <button class="btn-ghost" @click="goToLogin">登录</button>
          <button class="btn-primary" @click="goToRegister">免费开始</button>
        </div>
      </div>
    </nav>

    <!-- Hero 区 -->
    <section class="hero">
      <!-- 背景节点云 -->
      <KnowledgeNodeCloud class="hero-bg" />

      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          <span>AI 驱动的智能学习平台</span>
        </div>
        <h1 class="hero-title">
          <span class="title-line">重新定义</span>
          <span class="title-line gradient-text">课程问答体验</span>
        </h1>
        <p class="hero-subtitle">
          基于知识图谱和大语言模型，让每一次学习都有迹可循。<br />
          多轮对话、上下文记忆、跨章节推理，支持你和课本之间的每一次灵感追问。
        </p>
        <div class="hero-actions">
          <MagneticButton variant="primary" @click="goToRegister">
            <span>立即体验</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
          </MagneticButton>
          <MagneticButton variant="secondary" @click="playDemo">
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
              <path d="M8 5v14l11-7z" />
            </svg>
            <span>观看演示</span>
          </MagneticButton>
        </div>
      </div>
    </section>

    <!-- 特性卡 · 3D 倾斜 -->
    <section id="features" class="features-section">
      <div class="section-header">
        <h2 class="section-title">学习，被重新设计</h2>
        <p class="section-desc">四大核心能力，环环相扣</p>
      </div>
      <div class="features-grid">
        <Tilt3DCard v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-icon">{{ f.icon }}</div>
          <h3 class="feature-title">{{ f.title }}</h3>
          <p class="feature-desc">{{ f.desc }}</p>
        </Tilt3DCard>
      </div>
    </section>

    <!-- Pin-scroll 特性流 -->
    <PinScrollShowcase />

    <!-- 数据统计 -->
    <section id="stats" class="stats-section">
      <div class="stats-grid">
        <div v-for="s in stats" :key="s.label" class="stat-item">
          <div class="stat-num">{{ s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 用户评价 -->
    <section id="testimonials" class="testimonials-section">
      <div class="section-header">
        <h2 class="section-title">学员们怎么说</h2>
      </div>
      <div class="testimonials-grid">
        <div v-for="t in testimonials" :key="t.name" class="testimonial-card">
          <p class="quote">"{{ t.quote }}"</p>
          <div class="author">
            <div class="author-avatar"></div>
            <div>
              <div class="author-name">{{ t.name }}</div>
              <div class="author-role">{{ t.role }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Final CTA -->
    <section class="cta-section">
      <div class="cta-inner">
        <h2 class="cta-title">准备好重新学习了吗？</h2>
        <p class="cta-subtitle">完全免费注册，立即体验 AI + 知识图谱的学习方式</p>
        <MagneticButton variant="primary" @click="goToRegister">
          <span>免费开始</span>
        </MagneticButton>
      </div>
    </section>

    <footer class="footer">
      <div class="footer-inner">
        <div>© 2026 智课问答 · CKQA</div>
        <div>基于 Vue 3 + GraphRAG</div>
      </div>
    </footer>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/mixins/glass' as glass;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.landing-page {
  font-family: 'Manrope', 'Noto Sans SC', sans-serif;
  color: #fff;
  background: #0f0f1a;
  overflow-x: hidden;
}

// ========== Navbar ==========
.navbar {
  position: fixed;
  top: 0; left: 0; right: 0;
  z-index: 100;
  padding: 16px 0;
  transition: background $duration-fast $ease-out, padding $duration-fast $ease-out,
    backdrop-filter $duration-fast $ease-out;

  &.scrolled {
    background: rgba(15, 15, 26, 0.7);
    backdrop-filter: blur(20px);
    padding: 10px 0;
  }
}

.nav-container {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;

  .logo-icon {
    width: 32px; height: 32px;
    color: #818cf8;
  }
  .logo-text {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-weight: 700;
    font-size: 18px;
  }
}

.nav-links {
  display: flex;
  gap: 28px;

  .nav-link {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.7);
    transition: color $duration-fast $ease-out;

    &:hover { color: #fff; }
  }

  @media (max-width: $bp-tablet) { display: none; }
}

.nav-actions {
  display: flex;
  gap: 12px;

  .btn-ghost, .btn-primary {
    padding: 8px 18px;
    border-radius: $radius-lg;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    font-family: inherit;
    transition: transform $duration-fast $ease-out;
  }
  .btn-ghost {
    background: transparent;
    color: #fff;
    border: 1px solid rgba(255, 255, 255, 0.2);

    &:hover { background: rgba(255, 255, 255, 0.05); }
  }
  .btn-primary {
    background: linear-gradient(135deg, #6366f1, #818cf8);
    color: #fff;
    border: 0;
    box-shadow: 0 4px 16px rgba(99, 102, 241, 0.35);

    &:hover { transform: translateY(-1px); box-shadow: 0 8px 24px rgba(99, 102, 241, 0.5); }
  }
}

// ========== Hero ==========
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  .hero-bg {
    z-index: 0;
  }

  .hero-content {
    position: relative;
    z-index: 1;
    text-align: center;
    padding: 0 32px;
    max-width: 860px;
  }
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(99, 102, 241, 0.12);
  border: 1px solid rgba(99, 102, 241, 0.3);
  border-radius: $radius-full;
  color: #c4b5fd;
  font-size: 13px;
  margin-bottom: 24px;

  .badge-dot {
    width: 6px; height: 6px;
    background: #a5b4fc;
    border-radius: 50%;
    box-shadow: 0 0 8px #a5b4fc;
  }
}

.hero-title {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 72px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.02em;
  margin-bottom: 24px;

  .title-line { display: block; }
  .gradient-text {
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  @media (max-width: $bp-tablet) { font-size: 44px; }
}

.hero-subtitle {
  font-size: 18px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.65);
  margin-bottom: 40px;

  @media (max-width: $bp-tablet) { font-size: 15px; }
}

.hero-actions {
  display: inline-flex;
  gap: 16px;
  flex-wrap: wrap;
  justify-content: center;
}

// ========== Features ==========
.features-section {
  padding: 120px 32px;
  max-width: 1280px;
  margin: 0 auto;

  .section-header { text-align: center; margin-bottom: 64px; }
  .section-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 12px;
    letter-spacing: -0.02em;
  }
  .section-desc {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.6);
  }
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;

  @media (max-width: $bp-laptop) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.feature-card {
  padding: 28px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;
  transition: border-color $duration-base $ease-out;

  &:hover { border-color: rgba(99, 102, 241, 0.4); }

  .feature-icon { font-size: 32px; margin-bottom: 16px; }
  .feature-title {
    font-size: 18px;
    font-weight: 700;
    margin-bottom: 8px;
  }
  .feature-desc {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.6);
    line-height: 1.6;
  }
}

// ========== Stats ==========
.stats-section {
  padding: 80px 32px;
  background: linear-gradient(180deg, transparent, rgba(99, 102, 241, 0.05));
}

.stats-grid {
  max-width: 1280px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 32px;
  text-align: center;

  @media (max-width: $bp-tablet) { grid-template-columns: repeat(2, 1fr); }

  .stat-num {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 48px;
    font-weight: 700;
    background: linear-gradient(135deg, #6366f1, #06b6d4);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
  .stat-label {
    font-size: 14px;
    color: rgba(255, 255, 255, 0.6);
    margin-top: 4px;
  }
}

// ========== Testimonials ==========
.testimonials-section {
  padding: 120px 32px;
  max-width: 1280px;
  margin: 0 auto;

  .section-header { text-align: center; margin-bottom: 64px; }
  .section-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 40px;
    font-weight: 700;
  }
}

.testimonials-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.testimonial-card {
  padding: 28px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;

  .quote {
    font-size: 15px;
    line-height: 1.7;
    color: rgba(255, 255, 255, 0.85);
    margin-bottom: 20px;
  }
  .author {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .author-avatar {
    width: 40px; height: 40px;
    background: linear-gradient(135deg, #6366f1, #818cf8);
    border-radius: 50%;
  }
  .author-name { font-weight: 600; }
  .author-role { font-size: 12px; color: rgba(255, 255, 255, 0.5); }
}

// ========== CTA ==========
.cta-section {
  padding: 120px 32px;
  text-align: center;

  .cta-inner {
    max-width: 680px;
    margin: 0 auto;
  }
  .cta-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 16px;
    letter-spacing: -0.02em;
  }
  .cta-subtitle {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.65);
    margin-bottom: 32px;
  }
}

// ========== Footer ==========
.footer {
  padding: 40px 32px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);

  .footer-inner {
    max-width: 1280px;
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    color: rgba(255, 255, 255, 0.4);
    font-size: 13px;

    @media (max-width: $bp-tablet) {
      flex-direction: column;
      gap: 8px;
    }
  }
}
</style>
```

- [ ] **Step 2: 启动 dev 验证**

```bash
pnpm dev
```

浏览器打开 `http://localhost:8080/`，检查：
- Hero 背景有缓慢漂浮的节点云，鼠标靠近时节点和连线高亮
- CTA 按钮（立即体验 / 观看演示）随鼠标靠近产生磁吸位移
- 特性卡鼠标悬浮时有 3D 倾斜
- 滚动到"产品展示"区（#showcase），页面垂直钉住，三张大字报横向切换
- 滚动页面时顶栏 60px 处开始毛玻璃变化

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/views/layout/index.vue
git commit -m "refactor(student-app): rewrite landing page with node cloud + pin-scroll + magnetic CTA"
```

---

## Phase 8 · 首页重写

### Task 8.1 · 重写 `views/index.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/index.vue`

> 首页整体结构：欢迎卡 + 快捷问答 + 4 模块入口卡 + 双栏（我的课程 / 最近问答）· 详见设计稿 §6.2。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/index.vue -->
<!-- 登录后首页 · Indigo · Product Layout -->
<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCourseStore } from '@/stores'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { ArrowRight, Reading, ChatDotRound, Share, DataAnalysis, Search, Collection } from '@element-plus/icons-vue'

const router = useRouter()
const courseStore = useCourseStore()

const greetingName = '俊达' // mock，后续从 user store 取
const timeOfDay = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// 最近学习的课程（从 course store mock 读）
const recentCourse = computed(() => {
  const my = courseStore.myCoursesWithDetail?.[0]
  return my || { id: 1, title: '操作系统', progress: 70, lastLearnAt: '上次学到：页面置换算法' }
})

// 热门提问
const hotQuestions = [
  '什么是进程同步？',
  '红黑树的插入规则',
  'Vue3 的响应式原理',
  '动态规划怎么入手？',
]

// 四大模块入口
const modules = [
  { key: 'course', label: '课程中心', desc: '120+ 门精品课程', route: '/course/list', icon: Reading },
  { key: 'qa', label: '智能问答', desc: 'AI 专业解答', route: '/qa/ask', icon: ChatDotRound },
  { key: 'knowledge', label: '知识图谱', desc: '可视化学科脉络', route: '/knowledge/graph', icon: Share },
  { key: 'analysis', label: '学习分析', desc: '错题 / 报告 / 推荐', route: '/analysis/wrong', icon: DataAnalysis },
]

const myCourses = computed(() => (courseStore.myCoursesWithDetail || []).slice(0, 3))

const recentQAs = [
  { id: 1, title: '进程间通信的管道方式？', time: '10 分钟前', subject: '操作系统', active: true },
  { id: 2, title: '红黑树为何要保证黑高一致？', time: '昨天', subject: '数据结构', active: false },
  { id: 3, title: 'ResNet 的残差连接作用？', time: '3 天前', subject: '深度学习', active: false },
]

function goQA(prefill) {
  router.push({ path: '/qa/ask', query: prefill ? { topic: prefill } : {} })
}
function goCourse(id) {
  router.push(`/course/detail/${id}`)
}
function goQADetail(id) {
  router.push(`/qa/detail/${id}`)
}
</script>

<template>
  <div class="home-page">
    <div class="page-glow"></div>

    <div class="home-inner">
      <!-- 欢迎 / 继续学习 -->
      <GlassCard tier="base" padding="lg" class="welcome-card">
        <div class="welcome-row">
          <div class="welcome-left">
            <ModuleTag module="home" size="sm">👋 {{ timeOfDay }}，{{ greetingName }}</ModuleTag>
            <h1 class="welcome-title">
              继续学习 <span class="module-accent">{{ recentCourse.title }}</span>
            </h1>
            <p class="welcome-sub">{{ recentCourse.lastLearnAt || '最近一次学习' }} · 已完成 {{ recentCourse.progress }}%</p>
          </div>
          <div class="welcome-right">
            <GlowButton size="md" @click="goCourse(recentCourse.id)">
              继续学习
              <template #suffix>
                <el-icon><ArrowRight /></el-icon>
              </template>
            </GlowButton>
          </div>
        </div>

        <!-- 快捷问答 -->
        <div class="quick-ask">
          <div class="quick-input" @click="goQA()">
            <el-icon><Search /></el-icon>
            <span>有什么课程问题想问？直接提问或选个热门话题</span>
          </div>
          <div class="quick-tags">
            <button v-for="q in hotQuestions" :key="q" class="hot-chip" @click="goQA(q)">
              {{ q }}
            </button>
          </div>
        </div>
      </GlassCard>

      <!-- 四大模块入口 -->
      <div class="modules-grid">
        <GlassCard
          v-for="m in modules"
          :key="m.key"
          tier="light"
          padding="md"
          :hover="true"
          :class="['module-card', `module-${m.key}`]"
          @click="router.push(m.route)"
        >
          <div class="module-halo"></div>
          <div class="module-icon-wrap">
            <el-icon :size="20"><component :is="m.icon" /></el-icon>
          </div>
          <div class="module-label">{{ m.label }}</div>
          <div class="module-desc">{{ m.desc }}</div>
        </GlassCard>
      </div>

      <!-- 双栏 -->
      <div class="dual-col">
        <GlassCard tier="light" padding="md" class="col-card">
          <div class="col-header">
            <div class="col-title">
              <el-icon><Collection /></el-icon>
              我的课程
            </div>
            <button class="link-btn" @click="router.push('/course/my')">查看全部 →</button>
          </div>
          <div class="course-list">
            <div
              v-for="c in myCourses"
              :key="c.id"
              class="course-row"
              @click="goCourse(c.id)"
            >
              <img :src="c.cover" :alt="c.title" class="course-cover" />
              <div class="course-info">
                <div class="course-name">{{ c.title }}</div>
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: c.progress + '%' }"></div>
                </div>
              </div>
              <div class="progress-text">{{ c.progress }}%</div>
            </div>
            <div v-if="!myCourses.length" class="empty-hint">还没有课程，先去 <button class="link-btn" @click="router.push('/course/list')">课程中心</button> 看看</div>
          </div>
        </GlassCard>

        <GlassCard tier="light" padding="md" class="col-card qa-col">
          <div class="col-header">
            <div class="col-title">
              <el-icon><ChatDotRound /></el-icon>
              最近问答
            </div>
            <button class="link-btn qa-link" @click="router.push('/qa/history')">查看全部 →</button>
          </div>
          <div class="qa-list">
            <div
              v-for="q in recentQAs"
              :key="q.id"
              class="qa-row"
              :class="{ active: q.active }"
              @click="goQADetail(q.id)"
            >
              <div class="qa-title">{{ q.title }}</div>
              <div class="qa-meta">{{ q.time }} · {{ q.subject }}</div>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.home-page {
  position: relative;
  min-height: 100vh;
  padding: 32px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2ff 100%);

  @media (max-width: $bp-tablet) { padding: 16px; }
}

.page-glow {
  position: absolute;
  width: 480px; height: 480px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.15), transparent 60%);
  border-radius: 50%;
  top: -120px; right: -80px;
  filter: blur(40px);
  pointer-events: none;
}

.home-inner {
  position: relative;
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

// ========== Welcome Card ==========
.welcome-card {
  --module-color-500: #6366f1;
  --module-color-700: #4338ca;
}

.welcome-row {
  display: flex;
  align-items: center;
  gap: 20px;

  @media (max-width: $bp-tablet) {
    flex-direction: column;
    align-items: stretch;
  }
}

.welcome-left { flex: 1; }

.welcome-title {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 8px 0 4px;

  .module-accent {
    background: linear-gradient(135deg, #6366f1, #818cf8);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

.welcome-sub {
  font-size: 14px;
  color: #64748b;
}

.quick-ask {
  margin-top: 20px;

  .quick-input {
    display: flex;
    align-items: center;
    gap: 10px;
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: $radius-lg;
    padding: 12px 16px;
    color: #9ca3af;
    font-size: 14px;
    cursor: pointer;
    transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

    &:hover {
      border-color: #9333ea;
      box-shadow: 0 0 0 4px rgba(147, 51, 234, 0.08);
    }
  }

  .quick-tags {
    margin-top: 10px;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .hot-chip {
    padding: 4px 12px;
    background: rgba(147, 51, 234, 0.08);
    border: 1px solid rgba(147, 51, 234, 0.2);
    color: #9333ea;
    font-family: inherit;
    font-size: 12px;
    border-radius: $radius-full;
    cursor: pointer;
    transition: background $duration-fast $ease-out;

    &:hover { background: rgba(147, 51, 234, 0.15); }
  }
}

// ========== Modules Grid ==========
.modules-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;

  @media (max-width: $bp-laptop) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.module-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;

  .module-halo {
    position: absolute;
    width: 120px; height: 120px;
    border-radius: 50%;
    top: -30px; right: -30px;
    filter: blur(20px);
    pointer-events: none;
    opacity: 0.5;
  }
  .module-icon-wrap {
    position: relative;
    width: 40px; height: 40px;
    border-radius: $radius-lg;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    margin-bottom: 12px;
  }
  .module-label {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
  .module-desc {
    font-size: 12px;
    color: #64748b;
    margin-top: 4px;
  }
}
.module-course .module-halo { background: radial-gradient(circle, rgba(37, 99, 235, 0.3), transparent 60%); }
.module-course .module-icon-wrap { background: linear-gradient(135deg, #2563eb, #60a5fa); box-shadow: 0 4px 16px rgba(37, 99, 235, 0.3); }
.module-qa .module-halo { background: radial-gradient(circle, rgba(147, 51, 234, 0.3), transparent 60%); }
.module-qa .module-icon-wrap { background: linear-gradient(135deg, #9333ea, #c084fc); box-shadow: 0 4px 16px rgba(147, 51, 234, 0.3); }
.module-knowledge .module-halo { background: radial-gradient(circle, rgba(13, 148, 136, 0.3), transparent 60%); }
.module-knowledge .module-icon-wrap { background: linear-gradient(135deg, #0d9488, #2dd4bf); box-shadow: 0 4px 16px rgba(13, 148, 136, 0.3); }
.module-analysis .module-halo { background: radial-gradient(circle, rgba(219, 39, 119, 0.3), transparent 60%); }
.module-analysis .module-icon-wrap { background: linear-gradient(135deg, #db2777, #f472b6); box-shadow: 0 4px 16px rgba(219, 39, 119, 0.3); }

// ========== Dual Col ==========
.dual-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;

  @media (max-width: $bp-laptop) { grid-template-columns: 1fr; }
}

.col-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;

  .col-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
  }
  .link-btn {
    background: transparent;
    border: 0;
    color: #2563eb;
    font-size: 12px;
    font-family: inherit;
    cursor: pointer;

    &.qa-link { color: #9333ea; }

    &:hover { opacity: 0.8; }
  }
}

.course-list, .qa-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.course-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(37, 99, 235, 0.04); }

  .course-cover {
    width: 60px; height: 40px;
    border-radius: $radius-md;
    object-fit: cover;
  }
  .course-info { flex: 1; }
  .course-name {
    font-size: 13px;
    font-weight: 600;
    color: #0f172a;
  }
  .progress-bar {
    height: 4px;
    background: #e5e7eb;
    border-radius: 2px;
    margin-top: 4px;
    overflow: hidden;

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #2563eb, #60a5fa);
      border-radius: 2px;
      box-shadow: 0 0 6px #60a5fa;
      transition: width $duration-base $ease-out;
    }
  }
  .progress-text {
    font-size: 11px;
    color: #2563eb;
    font-weight: 600;
  }
}

.qa-row {
  padding: 8px 10px;
  border-left: 2px solid #e5e7eb;
  border-radius: 0 $radius-md $radius-md 0;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(147, 51, 234, 0.04); }

  &.active {
    border-left-color: #9333ea;
    background: rgba(147, 51, 234, 0.05);

    .qa-title { color: #7e22ce; }
  }

  .qa-title {
    font-size: 13px;
    font-weight: 500;
    color: #0f172a;
  }
  .qa-meta {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 2px;
  }
}

.empty-hint {
  color: #94a3b8;
  font-size: 13px;
  padding: 12px;
  text-align: center;
}
</style>
```

- [ ] **Step 2: 验证 & Commit**

```bash
pnpm dev   # 访问 http://localhost:8080/home 目测欢迎卡、模块卡、双栏
git add frontend/apps/student-app/src/views/index.vue
git commit -m "refactor(student-app): rebuild home with welcome card + module entries + dual-col recent"
```

---

## Phase 9 · 问答三页重写

### Task 9.1 · 重写 `views/qa/index.vue`（提问）

**Files:**
- Modify: `frontend/apps/student-app/src/views/qa/index.vue`

> 设计稿 §6.3.1。ModuleLayout 已经提供了左侧 QASideNav，本页只做主对话区。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/qa/index.vue -->
<!-- 问答 / 提问 · Purple · Module Layout（侧栏由 ModuleLayout 注入） -->
<script setup>
import { ref, nextTick, computed } from 'vue'
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { Position } from '@element-plus/icons-vue'

const messages = ref([
  {
    id: 1,
    role: 'user',
    content: '什么是进程间通信的管道方式？',
    time: '10:23',
  },
  {
    id: 2,
    role: 'ai',
    sources: [{ label: '操作系统 · 第 3 章' }],
    content: '管道（Pipe）是一种半双工通信机制，在 Unix 系统中以字节流形式传递数据。管道分为匿名管道（pipe）和命名管道（FIFO）两类……',
    related: ['信号量', '消息队列', '共享内存'],
    time: '10:23',
  },
])

const input = ref('')
const mainRef = ref(null)

function send() {
  const text = input.value.trim()
  if (!text) return
  const id = Date.now()
  messages.value.push({ id, role: 'user', content: text, time: '刚刚' })
  input.value = ''

  // mock AI 回复
  setTimeout(() => {
    messages.value.push({
      id: id + 1,
      role: 'ai',
      sources: [{ label: '课程参考' }],
      content: '（示例回答）这是一个基于知识图谱的模拟回复，后续接入后端会替换为真实内容。',
      related: ['相关知识点 1', '相关知识点 2'],
      time: '刚刚',
    })
    nextTick(() => mainRef.value?.scrollTo({ top: mainRef.value.scrollHeight, behavior: 'smooth' }))
  }, 800)
}

const isEmpty = computed(() => messages.value.length === 0)
</script>

<template>
  <div class="qa-ask-page">
    <div class="qa-halo"></div>

    <div ref="mainRef" class="qa-main">
      <div v-if="isEmpty" class="empty-state">
        <div class="empty-icon">💬</div>
        <div class="empty-title">从一个问题开始</div>
        <div class="empty-desc">试试："什么是进程同步？"</div>
      </div>

      <template v-else>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="`role-${msg.role}`"
        >
          <!-- 用户 -->
          <div v-if="msg.role === 'user'" class="bubble user-bubble">
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>

          <!-- AI -->
          <GlassCard v-else tier="base" padding="md" class="ai-bubble">
            <div v-if="msg.sources?.length" class="msg-sources">
              <ModuleTag v-for="(s, i) in msg.sources" :key="i" module="qa" size="sm">📖 {{ s.label }}</ModuleTag>
            </div>
            <div class="msg-text ai-text">{{ msg.content }}</div>
            <div v-if="msg.related?.length" class="msg-related">
              <span class="related-label">相关：</span>
              <span v-for="r in msg.related" :key="r" class="related-chip">{{ r }}</span>
            </div>
            <div class="msg-time">{{ msg.time }}</div>
          </GlassCard>
        </div>
      </template>
    </div>

    <!-- 输入 -->
    <div class="qa-input-wrap">
      <GlassCard tier="base" padding="none" class="qa-input-card">
        <input
          v-model="input"
          class="qa-input"
          placeholder="继续追问，或换个话题…"
          @keyup.enter="send"
        />
        <button class="qa-send" :disabled="!input.trim()" @click="send">
          <el-icon :size="18"><Position /></el-icon>
        </button>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

// 模块色：Purple
.qa-ask-page {
  --module-color-500: #9333ea;
  --module-color-700: #7e22ce;

  position: relative;
  min-height: calc(100vh - 64px);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qa-halo {
  position: absolute;
  width: 420px; height: 420px;
  background: radial-gradient(circle, rgba(147, 51, 234, 0.2), transparent 60%);
  border-radius: 50%;
  top: -100px; right: -80px;
  filter: blur(40px);
  pointer-events: none;
}

.qa-main {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scroll-behavior: smooth;
}

.empty-state {
  text-align: center;
  padding: 80px 32px;

  .empty-icon { font-size: 56px; margin-bottom: 16px; }
  .empty-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 8px;
  }
  .empty-desc { font-size: 14px; color: #64748b; }
}

.msg-row { display: flex; }
.role-user { justify-content: flex-end; }
.role-ai { justify-content: flex-start; }

.user-bubble {
  max-width: 70%;
  padding: 10px 14px;
  background: linear-gradient(135deg, #9333ea, #a855f7);
  color: #fff;
  border-radius: $radius-xl $radius-xl 2px $radius-xl;
  box-shadow: 0 4px 16px rgba(147, 51, 234, 0.25);

  .msg-text { font-size: 14px; line-height: 1.6; }
  .msg-time { font-size: 11px; color: rgba(255, 255, 255, 0.8); margin-top: 4px; text-align: right; }
}

.ai-bubble {
  max-width: 80%;
  border-color: rgba(147, 51, 234, 0.25) !important;
  box-shadow: 0 8px 32px rgba(147, 51, 234, 0.12);
  border-radius: $radius-xl $radius-xl $radius-xl 2px !important;

  .msg-sources {
    display: flex;
    gap: 6px;
    margin-bottom: 10px;
  }
  .ai-text {
    font-size: 14px;
    line-height: 1.65;
    color: #0f172a;
  }
  .msg-related {
    margin-top: 12px;
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;

    .related-label {
      font-size: 11px;
      color: #64748b;
      font-weight: 600;
    }
    .related-chip {
      padding: 2px 8px;
      background: rgba(147, 51, 234, 0.05);
      border: 1px solid rgba(147, 51, 234, 0.15);
      color: #7e22ce;
      font-size: 11px;
      border-radius: $radius-md;
    }
  }
  .msg-time {
    font-size: 11px;
    color: #94a3b8;
    margin-top: 8px;
  }
}

.qa-input-wrap {
  position: sticky;
  bottom: 16px;
}

.qa-input-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 16px !important;
  border-color: rgba(147, 51, 234, 0.35) !important;
  box-shadow: 0 0 0 4px rgba(147, 51, 234, 0.1), 0 8px 24px rgba(147, 51, 234, 0.15);

  .qa-input {
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    font-family: inherit;
    font-size: 14px;
    color: #0f172a;

    &::placeholder { color: #9ca3af; }
  }

  .qa-send {
    width: 40px; height: 40px;
    background: linear-gradient(135deg, #9333ea, #a855f7);
    border: 0;
    border-radius: $radius-lg;
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 16px rgba(147, 51, 234, 0.35);
    transition: transform $duration-fast $ease-out;

    &:hover:not(:disabled) {
      transform: translateY(-1px);
      box-shadow: 0 8px 24px rgba(147, 51, 234, 0.5);
    }
    &:disabled { opacity: 0.4; cursor: not-allowed; }
  }
}
</style>
```

- [ ] **Step 2: 验证 & Commit**

```bash
pnpm dev   # 访问 /qa/ask 目测对话气泡、输入聚焦光晕
git add frontend/apps/student-app/src/views/qa/index.vue
git commit -m "refactor(student-app): rebuild QA ask page with glass bubbles + purple glow input"
```

---

### Task 9.2 · 重写 `views/qa/QAHistory.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/qa/QAHistory.vue`

> 设计稿 §6.3.2：卡片网格 + 筛选 tab + 学科 tag。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/qa/QAHistory.vue -->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'

const router = useRouter()
const filter = ref('all')
const subject = ref('all')

const sessions = [
  { id: 1, title: 'OS · 进程调度', messageCount: 5, lastTime: '10 分钟前', unread: true, subjects: ['OS', '调度'] },
  { id: 2, title: '死锁检测', messageCount: 3, lastTime: '昨天', unread: false, subjects: ['OS'] },
  { id: 3, title: '虚拟内存', messageCount: 8, lastTime: '3 天前', unread: false, subjects: ['OS', '内存'] },
  { id: 4, title: '红黑树插入', messageCount: 4, lastTime: '5 天前', unread: false, subjects: ['数据结构'] },
  { id: 5, title: 'Transformer 架构', messageCount: 12, lastTime: '上周', unread: false, subjects: ['深度学习'] },
  { id: 6, title: '动态规划入门', messageCount: 6, lastTime: '2 周前', unread: false, subjects: ['算法'] },
]

const filters = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'favorite', label: '收藏' },
]

const subjects = ['all', 'OS', '数据结构', '算法', '深度学习']

const filteredSessions = computed(() => {
  return sessions.filter((s) => {
    if (filter.value === 'unread' && !s.unread) return false
    if (subject.value !== 'all' && !s.subjects.includes(subject.value)) return false
    return true
  })
})

function openSession(id) {
  router.push(`/qa/detail/${id}`)
}
</script>

<template>
  <div class="qa-history-page">
    <header class="page-head">
      <h1 class="page-title">问答历史</h1>
      <p class="page-sub">每一次追问都是一次学习路径</p>
    </header>

    <!-- 筛选区 -->
    <GlassCard tier="base" padding="md" class="filter-bar">
      <div class="filter-row">
        <span class="filter-label">类型</span>
        <div class="filter-tabs">
          <button
            v-for="f in filters"
            :key="f.key"
            :class="['tab', { active: filter === f.key }]"
            @click="filter = f.key"
          >
            {{ f.label }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="filter-label">学科</span>
        <div class="subject-chips">
          <button
            v-for="s in subjects"
            :key="s"
            :class="['sub-chip', { active: subject === s }]"
            @click="subject = s"
          >
            {{ s === 'all' ? '全部' : s }}
          </button>
        </div>
      </div>
    </GlassCard>

    <!-- 卡片网格 -->
    <div class="session-grid">
      <GlassCard
        v-for="s in filteredSessions"
        :key="s.id"
        tier="light"
        padding="md"
        :hover="true"
        class="session-card"
        @click="openSession(s.id)"
      >
        <div class="card-head">
          <div class="avatar"></div>
          <div class="card-title">
            {{ s.title }}
            <span v-if="s.unread" class="unread-dot"></span>
          </div>
        </div>
        <div class="card-meta">{{ s.messageCount }} 条 · {{ s.lastTime }}</div>
        <div class="card-tags">
          <ModuleTag v-for="sb in s.subjects" :key="sb" module="qa" size="sm">{{ sb }}</ModuleTag>
        </div>
      </GlassCard>
    </div>

    <div v-if="!filteredSessions.length" class="empty-state">没有符合筛选的历史对话</div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.qa-history-page {
  --module-color-500: #9333ea;
  --module-color-700: #7e22ce;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  .page-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 28px;
    font-weight: 800;
    color: #0f172a;
  }
  .page-sub {
    font-size: 14px;
    color: #64748b;
    margin-top: 4px;
  }
}

.filter-bar { display: flex; flex-direction: column; gap: 12px; }
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  .filter-label {
    font-size: 12px;
    color: #64748b;
    font-weight: 600;
  }
}
.filter-tabs {
  display: flex;
  gap: 4px;
  background: #f1f5f9;
  padding: 3px;
  border-radius: $radius-full;

  .tab {
    padding: 5px 14px;
    font-family: inherit;
    font-size: 12px;
    color: #64748b;
    background: transparent;
    border: 0;
    border-radius: $radius-full;
    cursor: pointer;

    &.active {
      background: linear-gradient(135deg, #9333ea, #a855f7);
      color: #fff;
      font-weight: 600;
    }
  }
}
.subject-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  .sub-chip {
    padding: 4px 12px;
    font-family: inherit;
    font-size: 12px;
    background: #fff;
    color: #475569;
    border: 1px solid #e5e7eb;
    border-radius: $radius-full;
    cursor: pointer;

    &.active {
      background: rgba(147, 51, 234, 0.1);
      border-color: rgba(147, 51, 234, 0.3);
      color: #9333ea;
      font-weight: 600;
    }
  }
}

.session-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;

  @media (max-width: $bp-laptop) { grid-template-columns: repeat(2, 1fr); }
  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }
}

.session-card {
  .card-head {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;

    .avatar {
      width: 32px; height: 32px;
      background: linear-gradient(135deg, #9333ea, #c084fc);
      border-radius: $radius-md;
      flex-shrink: 0;
    }
    .card-title {
      font-size: 14px;
      font-weight: 700;
      color: #0f172a;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .unread-dot {
      width: 6px; height: 6px;
      background: #ef4444;
      border-radius: 50%;
      box-shadow: 0 0 6px #ef4444;
    }
  }
  .card-meta {
    font-size: 12px;
    color: #64748b;
    margin-bottom: 8px;
  }
  .card-tags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }
}

.empty-state {
  text-align: center;
  padding: 60px;
  color: #94a3b8;
  font-size: 14px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/qa/QAHistory.vue
git commit -m "refactor(student-app): rebuild QA history with filter bar + card grid"
```

---

### Task 9.3 · 重写 `views/qa/QADetail.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/qa/QADetail.vue`

> 设计稿 §6.3.3：面包屑 + 主毛玻璃卡 + 底部"知识图谱关联"跨模块引导。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/qa/QADetail.vue -->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import { ArrowRight, Share } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

// mock：从 id 拿详情（后续接真实数据）
const detail = computed(() => ({
  id: route.params.id,
  title: '进程同步与互斥',
  subject: 'OS · 第 3 章',
  related: ['信号量', '管程', '死锁'],
  body: '进程同步与互斥是操作系统中协调多进程并发执行的关键机制。互斥确保同一时间只有一个进程访问共享资源，同步确保进程按预定顺序协作……',
  kgNodes: ['信号量', '管程', '死锁', '生产者-消费者'],
}))

function goKG(topic) {
  router.push({ path: '/knowledge/graph', query: { focus: topic } })
}
</script>

<template>
  <div class="qa-detail-page">
    <div class="breadcrumb">
      <a href="#" @click.prevent="router.push('/qa/history')">问答</a>
      <span>/</span>
      <a href="#" @click.prevent="router.push('/qa/history')">{{ detail.subject }}</a>
      <span>/</span>
      <span class="current">详情</span>
    </div>

    <GlassCard tier="base" padding="lg" class="detail-card">
      <div class="card-head">
        <h1 class="detail-title">{{ detail.title }}</h1>
        <ModuleTag module="qa" size="md">{{ detail.subject }}</ModuleTag>
      </div>

      <div class="detail-body">{{ detail.body }}</div>

      <div class="related-tags">
        <span class="label">相关：</span>
        <ModuleTag v-for="r in detail.related" :key="r" module="qa" size="sm">{{ r }}</ModuleTag>
      </div>
    </GlassCard>

    <!-- 跨模块引导 · 知识图谱关联 -->
    <GlassCard tier="light" padding="md" class="kg-link-card">
      <div class="kg-head">
        <div class="kg-title">
          <el-icon :size="18"><Share /></el-icon>
          <span>知识图谱关联</span>
        </div>
        <GlowButton size="sm" variant="ghost" @click="goKG(detail.title)">
          查看图谱
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
      </div>
      <div class="kg-desc">本次提问涉及以下知识点，点击任意节点可在图谱中高亮展开：</div>
      <div class="kg-nodes">
        <button v-for="n in detail.kgNodes" :key="n" class="kg-chip" @click="goKG(n)">{{ n }}</button>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.qa-detail-page {
  --module-color-500: #9333ea;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 900px;
  margin: 0 auto;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;

  a {
    color: #475569;
    cursor: pointer;

    &:hover { color: #9333ea; }
  }
  .current { color: #9333ea; font-weight: 600; }
}

.detail-card {
  border-color: rgba(147, 51, 234, 0.2) !important;
  box-shadow: 0 8px 32px rgba(147, 51, 234, 0.08);

  .card-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
    flex-wrap: wrap;
  }
  .detail-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 24px;
    font-weight: 700;
    color: #0f172a;
  }
  .detail-body {
    font-size: 14px;
    line-height: 1.75;
    color: #334155;
    margin-bottom: 16px;
  }
  .related-tags {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;

    .label { font-size: 12px; color: #64748b; font-weight: 600; }
  }
}

.kg-link-card {
  border-color: rgba(13, 148, 136, 0.2) !important;
  background: linear-gradient(135deg, rgba(13, 148, 136, 0.04), rgba(45, 212, 191, 0.02)) !important;

  .kg-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .kg-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 700;
      color: #0d9488;
    }
  }
  .kg-desc {
    font-size: 13px;
    color: #475569;
    margin-bottom: 10px;
  }
  .kg-nodes {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;

    .kg-chip {
      padding: 4px 12px;
      background: rgba(13, 148, 136, 0.08);
      border: 1px solid rgba(13, 148, 136, 0.2);
      color: #0d9488;
      font-family: inherit;
      font-size: 12px;
      border-radius: $radius-md;
      cursor: pointer;
      transition: background $duration-fast $ease-out;

      &:hover { background: rgba(13, 148, 136, 0.15); }
    }
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/qa/QADetail.vue
git commit -m "refactor(student-app): rebuild QA detail with breadcrumb + KG link card"
```

---

## Phase 10 · 课程四页重写

### Task 10.1 · 重写 `views/course/index.vue`（列表）

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/index.vue`

> 设计稿 §6.4.1。ModuleLayout 已经提供 CourseSideNav，本页只做筛选 + 网格。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/course/index.vue -->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCourseStore } from '@/stores'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { Search, VideoPlay } from '@element-plus/icons-vue'

const router = useRouter()
const courseStore = useCourseStore()

const keyword = ref('')
const category = ref('全部')
const sort = ref('newest')

const categories = ['全部', '人工智能', '前端开发', '数据科学', '后端开发']

const allCourses = computed(() => courseStore.allCourses || [])

const filtered = computed(() => {
  let list = allCourses.value
  if (keyword.value) {
    const kw = keyword.value.toLowerCase()
    list = list.filter((c) => c.title.toLowerCase().includes(kw) || c.teacher?.name?.includes(keyword.value))
  }
  if (category.value !== '全部') {
    list = list.filter((c) => c.category === category.value)
  }
  if (sort.value === 'popular') {
    list = [...list].sort((a, b) => (b.studentCount || 0) - (a.studentCount || 0))
  } else if (sort.value === 'rating') {
    list = [...list].sort((a, b) => (b.rating || 0) - (a.rating || 0))
  }
  return list
})

function goDetail(id) {
  router.push(`/course/detail/${id}`)
}

function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return n.toString()
}
</script>

<template>
  <div class="course-list-page">
    <header class="page-head">
      <h1 class="page-title">探索精品课程</h1>
      <p class="page-sub">从基础到进阶，找到适合你的学习节奏</p>
    </header>

    <!-- 筛选 -->
    <GlassCard tier="base" padding="md" class="filter-card">
      <div class="search-row">
        <div class="search-box">
          <el-icon><Search /></el-icon>
          <input v-model="keyword" placeholder="搜索课程名称、讲师或关键词" />
        </div>
        <GlowButton size="md">搜索</GlowButton>
      </div>

      <div class="filter-row">
        <span class="f-label">分类</span>
        <div class="cat-chips">
          <button
            v-for="c in categories"
            :key="c"
            :class="['cat-chip', { active: category === c }]"
            @click="category = c"
          >{{ c }}</button>
        </div>
      </div>

      <div class="filter-row">
        <span class="f-label">排序</span>
        <el-radio-group v-model="sort">
          <el-radio-button value="newest">最新发布</el-radio-button>
          <el-radio-button value="popular">最受欢迎</el-radio-button>
          <el-radio-button value="rating">评分最高</el-radio-button>
        </el-radio-group>
      </div>
    </GlassCard>

    <!-- 课程网格 -->
    <div class="course-grid">
      <GlassCard
        v-for="c in filtered"
        :key="c.id"
        tier="light"
        padding="none"
        :hover="true"
        class="course-card"
        @click="goDetail(c.id)"
      >
        <div class="cover-wrap">
          <img :src="c.cover" :alt="c.title" class="cover" />
          <div class="cover-overlay">
            <div class="play-btn">
              <el-icon :size="20"><VideoPlay /></el-icon>
            </div>
          </div>
          <div class="price-tag" :class="c.price === 0 ? 'free' : 'paid'">
            {{ c.price === 0 ? '免费' : '¥' + c.price }}
          </div>
        </div>
        <div class="card-body">
          <h3 class="title">{{ c.title }}</h3>
          <p class="desc">{{ c.description }}</p>
          <div class="teacher-row">
            <img :src="c.teacher.avatar" :alt="c.teacher.name" class="teacher-avatar" />
            <div>
              <div class="teacher-name">{{ c.teacher.name }}</div>
              <div class="teacher-title">{{ c.teacher.title }}</div>
            </div>
          </div>
          <div class="meta">
            <span class="rating">★ {{ c.rating }}</span>
            <span>·</span>
            <span>{{ formatCount(c.studentCount) }} 学员</span>
            <span>·</span>
            <span>{{ c.lessonCount }} 课时</span>
          </div>
          <div class="tags">
            <ModuleTag v-for="t in c.tags.slice(0, 3)" :key="t" module="course" size="sm">{{ t }}</ModuleTag>
          </div>
        </div>
      </GlassCard>
    </div>

    <div v-if="!filtered.length" class="empty-state">没有符合筛选的课程</div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.course-list-page {
  --module-color-500: #2563eb;
  --module-color-700: #1d4ed8;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  .page-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 28px;
    font-weight: 800;
    color: #0f172a;
  }
  .page-sub {
    font-size: 14px;
    color: #64748b;
    margin-top: 4px;
  }
}

.filter-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-color: rgba(255, 255, 255, 0.9) !important;
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.08);
}

.search-row {
  display: flex;
  gap: 10px;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: $radius-lg;
  color: #9ca3af;

  input {
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    font-family: inherit;
    font-size: 14px;
    color: #0f172a;

    &::placeholder { color: #9ca3af; }
  }

  &:focus-within {
    border-color: #2563eb;
    box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.12);
  }
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;

  .f-label {
    font-size: 12px;
    color: #64748b;
    font-weight: 600;
  }
}

.cat-chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  .cat-chip {
    padding: 4px 14px;
    font-family: inherit;
    font-size: 12px;
    background: #fff;
    border: 1px solid #e5e7eb;
    color: #475569;
    border-radius: $radius-full;
    cursor: pointer;

    &.active {
      background: linear-gradient(135deg, #2563eb, #3b82f6);
      color: #fff;
      border-color: transparent;
      font-weight: 600;
    }
  }
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.course-card {
  overflow: hidden;

  .cover-wrap {
    position: relative;
    aspect-ratio: 16 / 9;
    overflow: hidden;

    .cover {
      width: 100%; height: 100%;
      object-fit: cover;
      transition: transform $duration-base $ease-out;
    }
    .cover-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, transparent 50%, rgba(37, 99, 235, 0.35));
      opacity: 0;
      transition: opacity $duration-fast $ease-out;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .play-btn {
      width: 56px; height: 56px;
      background: rgba(255, 255, 255, 0.85);
      backdrop-filter: blur(12px);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #2563eb;
      box-shadow: 0 8px 24px rgba(37, 99, 235, 0.3);
    }
    .price-tag {
      position: absolute;
      top: 12px; right: 12px;
      padding: 3px 10px;
      font-size: 11px;
      font-weight: 600;
      border-radius: $radius-full;
      backdrop-filter: blur(12px);

      &.free {
        background: rgba(16, 185, 129, 0.9);
        color: #fff;
      }
      &.paid {
        background: rgba(245, 158, 11, 0.9);
        color: #fff;
      }
    }
  }

  &:hover {
    .cover { transform: scale(1.08); }
    .cover-overlay { opacity: 1; }
  }

  .card-body {
    padding: 14px;

    .title {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 4px;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .desc {
      font-size: 12px;
      color: #64748b;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      margin-bottom: 10px;
    }
  }

  .teacher-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .teacher-avatar {
      width: 28px; height: 28px;
      border-radius: 50%;
    }
    .teacher-name {
      font-size: 12px;
      font-weight: 600;
      color: #334155;
    }
    .teacher-title {
      font-size: 10px;
      color: #94a3b8;
    }
  }

  .meta {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: #64748b;
    margin-bottom: 8px;

    .rating { color: #f59e0b; font-weight: 600; }
  }

  .tags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }
}

.empty-state {
  text-align: center;
  padding: 60px;
  color: #94a3b8;
  font-size: 14px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/course/index.vue
git commit -m "refactor(student-app): rebuild course list with filter + glass card grid"
```

---

### Task 10.2 · 重写 `views/course/CourseDetail.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/CourseDetail.vue`

> 设计稿 §6.4.2。ModuleLayout 提供 CourseSideNav。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/course/CourseDetail.vue -->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import { ArrowRight, VideoPlay, Check } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const store = useCourseStore()

const course = computed(() => store.getCourseById(route.params.id) || {})
const isEnrolled = computed(() => store.isEnrolled(route.params.id))
const myCourseState = computed(() => store.myCoursesWithDetail?.find?.((c) => c.id === Number(route.params.id)))
const progress = computed(() => myCourseState.value?.progress || 0)

// mock 章节
const chapters = [
  { id: 1, title: '第 1 章：概述', done: true, current: false },
  { id: 2, title: '第 2 章：基础概念', done: true, current: false },
  { id: 3, title: '第 3 章：核心原理', done: true, current: false },
  { id: 4, title: '第 4 章：优化器', done: false, current: true },
  { id: 5, title: '第 5 章：实战演练', done: false, current: false },
]

function goLearn() {
  router.push(`/course/learn/${course.value.id}`)
}

function enroll() {
  store.enrollCourse(course.value.id)
}

function formatCount(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  return n?.toString?.() || '0'
}
</script>

<template>
  <div v-if="course.id" class="course-detail-page">
    <GlassCard tier="base" padding="lg" class="main-card">
      <div class="hero">
        <img :src="course.cover" :alt="course.title" class="cover" />
        <div class="info">
          <h1 class="title">{{ course.title }}</h1>
          <p class="desc">{{ course.description }}</p>
          <div class="meta-row">
            <span class="rating">★ {{ course.rating }}</span>
            <span>·</span>
            <span>{{ formatCount(course.studentCount) }} 学员</span>
            <span>·</span>
            <span>{{ course.lessonCount }} 课时</span>
          </div>
          <div class="teacher">
            <img :src="course.teacher?.avatar" class="teacher-avatar" />
            <div>
              <div class="teacher-name">{{ course.teacher?.name }}</div>
              <div class="teacher-title">{{ course.teacher?.title }}</div>
            </div>
          </div>
          <div class="tags">
            <ModuleTag v-for="t in (course.tags || [])" :key="t" module="course" size="sm">{{ t }}</ModuleTag>
          </div>
        </div>
      </div>

      <!-- 继续学习 / 加入学习 条 -->
      <div v-if="isEnrolled" class="resume-bar">
        <div class="resume-left">
          <div class="resume-progress">
            <div class="fill" :style="{ width: progress + '%' }"></div>
          </div>
          <div class="resume-text">已学 {{ progress }}% · 继续学习</div>
        </div>
        <GlowButton size="md" @click="goLearn">
          继续学习
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
      </div>
      <div v-else class="resume-bar">
        <div class="resume-text">还未加入这门课程</div>
        <GlowButton size="md" @click="enroll">
          加入学习
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
      </div>
    </GlassCard>

    <!-- 章节目录 -->
    <GlassCard tier="light" padding="md" class="chapter-card">
      <h3 class="section-title">章节目录</h3>
      <div class="chapter-list">
        <div
          v-for="ch in chapters"
          :key="ch.id"
          class="chapter-item"
          :class="{ done: ch.done, current: ch.current }"
          @click="goLearn"
        >
          <div class="ch-icon">
            <el-icon v-if="ch.done" :size="14"><Check /></el-icon>
            <el-icon v-else-if="ch.current" :size="14"><VideoPlay /></el-icon>
            <span v-else>{{ ch.id }}</span>
          </div>
          <div class="ch-title">{{ ch.title }}</div>
        </div>
      </div>
    </GlassCard>
  </div>
  <div v-else class="empty-state">课程不存在或已下架</div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.course-detail-page {
  --module-color-500: #2563eb;
  --module-color-700: #1d4ed8;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1100px;
  margin: 0 auto;
}

.main-card {
  border-color: rgba(255, 255, 255, 0.9) !important;
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.08);
}

.hero {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }

  .cover {
    width: 100%;
    aspect-ratio: 16 / 9;
    object-fit: cover;
    border-radius: $radius-lg;
  }
  .title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 24px;
    font-weight: 800;
    color: #0f172a;
    margin-bottom: 8px;
  }
  .desc {
    font-size: 13px;
    color: #64748b;
    line-height: 1.6;
    margin-bottom: 12px;
  }
  .meta-row {
    display: flex;
    gap: 6px;
    align-items: center;
    font-size: 12px;
    color: #64748b;
    margin-bottom: 12px;

    .rating { color: #f59e0b; font-weight: 600; }
  }
  .teacher {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 12px;

    .teacher-avatar {
      width: 36px; height: 36px;
      border-radius: 50%;
    }
    .teacher-name { font-size: 13px; font-weight: 600; color: #334155; }
    .teacher-title { font-size: 11px; color: #94a3b8; }
  }
  .tags {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }
}

.resume-bar {
  margin-top: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(96, 165, 250, 0.03));
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: $radius-lg;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  .resume-left { flex: 1; }
  .resume-progress {
    height: 6px;
    background: rgba(255, 255, 255, 0.6);
    border-radius: 3px;
    overflow: hidden;
    margin-bottom: 6px;

    .fill {
      height: 100%;
      background: linear-gradient(90deg, #2563eb, #60a5fa);
      box-shadow: 0 0 8px #60a5fa;
      transition: width $duration-base $ease-out;
    }
  }
  .resume-text { font-size: 13px; color: #2563eb; font-weight: 600; }
}

.chapter-card {
  .section-title {
    font-size: 15px;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 12px;
  }
}

.chapter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chapter-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(37, 99, 235, 0.04); }

  .ch-icon {
    width: 24px; height: 24px;
    border-radius: $radius-md;
    background: #e5e7eb;
    color: #94a3b8;
    font-size: 11px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .ch-title { font-size: 13px; color: #475569; }

  &.done {
    .ch-icon { background: #2563eb; color: #fff; }
    .ch-title { color: #0f172a; }
  }

  &.current {
    background: rgba(37, 99, 235, 0.06);
    .ch-icon { background: #2563eb; color: #fff; box-shadow: 0 0 12px rgba(37, 99, 235, 0.4); }
    .ch-title { color: #2563eb; font-weight: 600; }
  }
}

.empty-state {
  padding: 80px;
  text-align: center;
  color: #94a3b8;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/course/CourseDetail.vue
git commit -m "refactor(student-app): rebuild course detail with glass hero + resume bar + chapter list"
```

---

### Task 10.3 · 重写 `views/course/CourseLearn.vue`（深色例外）

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/CourseLearn.vue`

> 设计稿 §6.4.3：**本次方案中唯一的深色产品页**。layout 已设为 `product`，但本页自己渲染深色背景 + 右侧目录。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/course/CourseLearn.vue -->
<!-- 沉浸式学习页 · 深色例外 · 详见设计稿 §6.4.3 -->
<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores'
import { VideoPlay, ArrowLeft, ChatDotRound, Edit } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const store = useCourseStore()

const course = computed(() => store.getCourseById(route.params.id) || {})

const chapters = [
  { id: 1, title: '第 1 章 · 概述', duration: '12:34', done: true },
  { id: 2, title: '第 2 章 · 基础概念', duration: '15:20', done: true },
  { id: 3, title: '第 3 章 · 核心原理', duration: '21:08', done: true },
  { id: 4, title: '第 4 章 · 优化器', duration: '18:45', current: true },
  { id: 5, title: '第 5 章 · 实战演练', duration: '24:10', done: false },
]

const activeTab = ref('note')
const noteText = ref('')

function goBack() {
  router.push(`/course/detail/${course.value.id}`)
}

function askAI() {
  router.push({ path: '/qa/ask', query: { course: course.value.title } })
}
</script>

<template>
  <div class="course-learn-page">
    <!-- 返回条 -->
    <div class="learn-bar">
      <button class="back-btn" @click="goBack">
        <el-icon :size="16"><ArrowLeft /></el-icon>
        返回课程详情
      </button>
      <div class="course-title">{{ course.title }}</div>
    </div>

    <div class="learn-body">
      <!-- 左侧主区 · 视频 + 标签 -->
      <div class="learn-main">
        <div class="video-wrap">
          <div class="video-placeholder">
            <div class="play-core">
              <el-icon :size="32"><VideoPlay /></el-icon>
            </div>
          </div>
          <div class="video-progress">
            <div class="vp-fill"></div>
          </div>
        </div>

        <!-- 双 tab：笔记 / 问 AI -->
        <div class="tabs">
          <button
            :class="['tab-btn', 'note', { active: activeTab === 'note' }]"
            @click="activeTab = 'note'"
          >
            <el-icon><Edit /></el-icon>
            <span>笔记</span>
          </button>
          <button
            :class="['tab-btn', 'ask', { active: activeTab === 'ask' }]"
            @click="activeTab = 'ask'"
          >
            <el-icon><ChatDotRound /></el-icon>
            <span>问问 AI</span>
          </button>
        </div>

        <div v-if="activeTab === 'note'" class="tab-panel note-panel">
          <textarea
            v-model="noteText"
            placeholder="在这里记录此章节的笔记，自动保存…"
          ></textarea>
        </div>

        <div v-else class="tab-panel ask-panel">
          <p class="ask-hint">遇到不懂的地方？基于当前课程上下文，快速提问：</p>
          <button class="ask-entry" @click="askAI">
            <span>去提问 · 带上本课程上下文</span>
            <el-icon><ArrowLeft style="transform: rotate(180deg);" /></el-icon>
          </button>
        </div>
      </div>

      <!-- 右侧目录 · 深色毛玻璃 -->
      <aside class="chapter-aside">
        <div class="aside-head">目录</div>
        <div class="ch-list">
          <div
            v-for="ch in chapters"
            :key="ch.id"
            class="ch-item"
            :class="{ done: ch.done, current: ch.current }"
          >
            <div class="ch-no">{{ ch.id }}</div>
            <div class="ch-info">
              <div class="ch-title">{{ ch.title }}</div>
              <div class="ch-duration">{{ ch.duration }}</div>
            </div>
            <div v-if="ch.done" class="ch-status">✓</div>
            <div v-else-if="ch.current" class="ch-status current">▶</div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.course-learn-page {
  min-height: calc(100vh - 64px);
  background: #0f172a;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.learn-bar {
  padding: 14px 24px;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(37, 99, 235, 0.15);
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: $radius-md;
  color: rgba(255, 255, 255, 0.8);
  font-family: inherit;
  font-size: 12px;
  cursor: pointer;

  &:hover { background: rgba(255, 255, 255, 0.15); color: #fff; }
}

.course-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.learn-body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  padding: 16px;

  @media (max-width: $bp-laptop) {
    grid-template-columns: 1fr;
  }
}

.learn-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.video-wrap {
  position: relative;
  background: #000;
  border-radius: $radius-xl;
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.video-placeholder {
  width: 100%; height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;

  .play-core {
    width: 72px; height: 72px;
    background: rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(12px);
    border: 1px solid rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    box-shadow: 0 0 32px rgba(37, 99, 235, 0.5);
    cursor: pointer;
  }
}

.video-progress {
  position: absolute;
  bottom: 12px; left: 12px; right: 12px;
  height: 4px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 2px;
  overflow: hidden;

  .vp-fill {
    width: 35%;
    height: 100%;
    background: linear-gradient(90deg, #2563eb, #60a5fa);
    box-shadow: 0 0 10px #60a5fa;
  }
}

.tabs {
  display: flex;
  gap: 6px;
}

.tab-btn {
  flex: 1;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-lg;
  color: rgba(255, 255, 255, 0.7);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;

  &.note.active {
    background: rgba(37, 99, 235, 0.15);
    border-color: rgba(96, 165, 250, 0.3);
    color: #60a5fa;
    box-shadow: 0 0 16px rgba(37, 99, 235, 0.15);
  }
  &.ask.active {
    background: rgba(147, 51, 234, 0.15);
    border-color: rgba(192, 132, 252, 0.3);
    color: #c084fc;
    box-shadow: 0 0 16px rgba(147, 51, 234, 0.15);
  }
}

.tab-panel {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: $radius-xl;
  padding: 14px;
}

.note-panel textarea {
  width: 100%;
  min-height: 120px;
  background: transparent;
  border: 0;
  outline: 0;
  resize: vertical;
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.7;

  &::placeholder { color: rgba(255, 255, 255, 0.4); }
}

.ask-panel {
  .ask-hint {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.65);
    margin-bottom: 10px;
  }
  .ask-entry {
    width: 100%;
    padding: 12px 16px;
    background: linear-gradient(135deg, #9333ea, #a855f7);
    color: #fff;
    border: 0;
    border-radius: $radius-lg;
    font-family: inherit;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    box-shadow: 0 4px 16px rgba(147, 51, 234, 0.4);

    &:hover {
      box-shadow: 0 8px 24px rgba(147, 51, 234, 0.55);
    }
  }
}

.chapter-aside {
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(37, 99, 235, 0.15);
  border-radius: $radius-xl;
  padding: 14px;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.aside-head {
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.5);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.ch-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ch-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: rgba(255, 255, 255, 0.04); }

  .ch-no {
    width: 22px; height: 22px;
    font-size: 10px;
    font-weight: 700;
    background: rgba(255, 255, 255, 0.08);
    color: rgba(255, 255, 255, 0.6);
    border-radius: $radius-sm;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .ch-info { flex: 1; }
  .ch-title {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.85);
  }
  .ch-duration {
    font-size: 10px;
    color: rgba(255, 255, 255, 0.4);
  }
  .ch-status {
    font-size: 12px;
    color: rgba(255, 255, 255, 0.5);

    &.current { color: #60a5fa; }
  }

  &.current {
    background: rgba(37, 99, 235, 0.15);
    border: 1px solid rgba(96, 165, 250, 0.3);

    .ch-no { background: #2563eb; color: #fff; box-shadow: 0 0 12px rgba(37, 99, 235, 0.5); }
    .ch-title { color: #60a5fa; font-weight: 600; }
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/course/CourseLearn.vue
git commit -m "refactor(student-app): rebuild course learn page (dark immersive video exception)"
```

---

### Task 10.4 · 重写 `views/course/MyCourse.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/course/MyCourse.vue`

> 设计稿 §6.4.4。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/course/MyCourse.vue -->
<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCourseStore } from '@/stores'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'

const router = useRouter()
const store = useCourseStore()

const tab = ref('progress') // progress | done | favorite

const myList = computed(() => store.myCoursesWithDetail || [])

const byTab = computed(() => {
  if (tab.value === 'progress') return myList.value.filter((c) => c.progress < 100)
  if (tab.value === 'done') return myList.value.filter((c) => c.progress >= 100)
  return [] // favorite 暂占位
})

const stats = computed(() => ({
  total: myList.value.length,
  done: myList.value.filter((c) => c.progress >= 100).length,
  inProgress: myList.value.filter((c) => c.progress < 100).length,
  favorite: 0,
}))

function goDetail(id) { router.push(`/course/detail/${id}`) }
function goLearn(id) { router.push(`/course/learn/${id}`) }
</script>

<template>
  <div class="my-course-page">
    <header class="page-head">
      <h1 class="page-title">我的课程</h1>
      <p class="page-sub">继续你的学习节奏</p>
    </header>

    <!-- 统计卡 -->
    <div class="stats-grid">
      <GlassCard tier="light" padding="md" class="stat stat-total">
        <div class="stat-label">总课程</div>
        <div class="stat-num course-blue">{{ stats.total }}</div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-done">
        <div class="stat-label">已完成</div>
        <div class="stat-num green">{{ stats.done }}</div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-progress">
        <div class="stat-label">进行中</div>
        <div class="stat-num amber">{{ stats.inProgress }}</div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-favorite">
        <div class="stat-label">收藏</div>
        <div class="stat-num lemon">{{ stats.favorite }}</div>
      </GlassCard>
    </div>

    <!-- Tab -->
    <div class="tabs">
      <button :class="['tab-btn', { active: tab === 'progress' }]" @click="tab = 'progress'">进行中</button>
      <button :class="['tab-btn', { active: tab === 'done' }]" @click="tab = 'done'">已完成</button>
      <button :class="['tab-btn', { active: tab === 'favorite' }]" @click="tab = 'favorite'">收藏</button>
    </div>

    <!-- 课程列表 -->
    <GlassCard tier="light" padding="md" class="list-card">
      <div v-if="byTab.length" class="course-rows">
        <div
          v-for="c in byTab"
          :key="c.id"
          class="course-row"
          @click="goDetail(c.id)"
        >
          <img :src="c.cover" :alt="c.title" class="cover" />
          <div class="info">
            <div class="title">{{ c.title }}</div>
            <div class="progress-bar">
              <div class="fill" :style="{ width: c.progress + '%' }"></div>
            </div>
            <div class="meta">{{ c.lastLearnAt || '尚未学习' }}</div>
          </div>
          <div class="progress-pct">{{ c.progress }}%</div>
          <GlowButton size="sm" @click.stop="goLearn(c.id)">继续</GlowButton>
        </div>
      </div>
      <div v-else class="empty-state">
        <p>这里还没有内容</p>
        <GlowButton size="sm" variant="secondary" @click="router.push('/course/list')">去课程中心</GlowButton>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.my-course-page {
  --module-color-500: #2563eb;
  --module-color-700: #1d4ed8;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  .page-title {
    font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
    font-size: 28px;
    font-weight: 800;
    color: #0f172a;
  }
  .page-sub {
    font-size: 14px;
    color: #64748b;
    margin-top: 4px;
  }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: repeat(2, 1fr); }

  .stat {
    .stat-label {
      font-size: 11px;
      color: #64748b;
    }
    .stat-num {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 28px;
      font-weight: 700;
      margin-top: 4px;
    }
    .course-blue { color: #2563eb; }
    .green { color: #10b981; }
    .amber { color: #f59e0b; }
    .lemon { color: #ca8a04; }
  }
  .stat-total { border-color: rgba(37, 99, 235, 0.15) !important; }
  .stat-done { border-color: rgba(16, 185, 129, 0.15) !important; }
  .stat-progress { border-color: rgba(245, 158, 11, 0.2) !important; }
  .stat-favorite { border-color: rgba(234, 179, 8, 0.2) !important; }
}

.tabs {
  display: flex;
  gap: 4px;
  background: #f1f5f9;
  padding: 3px;
  border-radius: $radius-full;
  width: fit-content;

  .tab-btn {
    padding: 5px 16px;
    font-family: inherit;
    font-size: 12px;
    color: #64748b;
    background: transparent;
    border: 0;
    border-radius: $radius-full;
    cursor: pointer;

    &.active {
      background: linear-gradient(135deg, #2563eb, #3b82f6);
      color: #fff;
      font-weight: 600;
    }
  }
}

.list-card {
  .course-rows {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .course-row {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 10px 12px;
    border-radius: $radius-md;
    cursor: pointer;
    transition: background $duration-fast $ease-out;

    &:hover { background: rgba(37, 99, 235, 0.05); }

    .cover {
      width: 80px; height: 50px;
      border-radius: $radius-md;
      object-fit: cover;
    }
    .info { flex: 1; }
    .title { font-size: 14px; font-weight: 600; color: #0f172a; }
    .progress-bar {
      height: 4px;
      background: #e5e7eb;
      border-radius: 2px;
      margin-top: 6px;
      overflow: hidden;

      .fill {
        height: 100%;
        background: linear-gradient(90deg, #2563eb, #60a5fa);
        box-shadow: 0 0 6px #60a5fa;
        transition: width $duration-base $ease-out;
      }
    }
    .meta { font-size: 11px; color: #94a3b8; margin-top: 4px; }
    .progress-pct {
      font-size: 13px;
      font-weight: 600;
      color: #2563eb;
    }
  }
  .empty-state {
    padding: 40px;
    text-align: center;
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: center;
    color: #94a3b8;
    font-size: 14px;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/course/MyCourse.vue
git commit -m "refactor(student-app): rebuild my course page with stats + tabs + progress list"
```

---

## Phase 11 · 知识图谱视觉壳

### Task 11.1 · mock 数据 + 搜索页骨架

**Files:**
- Create: `frontend/apps/student-app/src/mock/knowledge.json`
- Create: `frontend/apps/student-app/src/views/knowledge/KnowledgeSearch.vue`

- [ ] **Step 1: 写 mock 数据**

```json
// frontend/apps/student-app/src/mock/knowledge.json
{
  "nodes": [
    { "id": "os", "label": "操作系统", "type": "root", "x": 400, "y": 300, "r": 20 },
    { "id": "process", "label": "进程", "type": "concept", "x": 200, "y": 180, "r": 14 },
    { "id": "memory", "label": "内存", "type": "concept", "x": 600, "y": 180, "r": 14 },
    { "id": "file", "label": "文件系统", "type": "concept", "x": 180, "y": 420, "r": 14 },
    { "id": "io", "label": "I/O 设备", "type": "concept", "x": 620, "y": 420, "r": 14 },
    { "id": "schedule", "label": "进程调度", "type": "instance", "x": 90, "y": 100, "r": 9 },
    { "id": "ipc", "label": "进程间通信", "type": "instance", "x": 260, "y": 80, "r": 9 },
    { "id": "virtual", "label": "虚拟内存", "type": "instance", "x": 720, "y": 100, "r": 9 },
    { "id": "page", "label": "页面置换", "type": "error", "x": 540, "y": 80, "r": 9 },
    { "id": "fs", "label": "FAT / NTFS", "type": "instance", "x": 90, "y": 500, "r": 9 },
    { "id": "buffer", "label": "缓冲技术", "type": "instance", "x": 720, "y": 500, "r": 9 }
  ],
  "edges": [
    { "from": "os", "to": "process" }, { "from": "os", "to": "memory" },
    { "from": "os", "to": "file" }, { "from": "os", "to": "io" },
    { "from": "process", "to": "schedule" }, { "from": "process", "to": "ipc" },
    { "from": "memory", "to": "virtual" }, { "from": "memory", "to": "page" },
    { "from": "file", "to": "fs" }, { "from": "io", "to": "buffer" }
  ],
  "details": {
    "process": {
      "name": "进程",
      "type": "概念节点",
      "errorCount": 2,
      "desc": "程序的一次执行过程，是系统资源分配和调度的基本单位。",
      "related": ["线程", "进程调度", "进程间通信"]
    }
  }
}
```

- [ ] **Step 2: 写 KnowledgeSearch 骨架页**

```vue
<!-- frontend/apps/student-app/src/views/knowledge/KnowledgeSearch.vue -->
<!-- 知识检索 · 骨架级视觉壳 -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
</script>

<template>
  <div class="kg-search-page">
    <header class="page-head">
      <h1 class="page-title">知识检索</h1>
      <p class="page-sub">搜索学科中的任意知识点</p>
    </header>

    <GlassCard tier="base" padding="md" class="search-card">
      <div class="search-input">
        <span class="icon">🔍</span>
        <input placeholder="输入知识点名称、关键词或概念" />
      </div>
      <div class="skeleton-wrap">
        <div v-for="i in 4" :key="i" class="skel-row">
          <SkeletonBlock width="48px" height="48px" rounded="md" />
          <div class="skel-lines">
            <SkeletonBlock width="60%" height="14px" />
            <SkeletonBlock width="85%" height="10px" />
          </div>
        </div>
      </div>
      <div class="coming-hint">功能完善中 · 本次仅提供视觉稿</div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.kg-search-page {
  --module-color-500: #0d9488;
  padding: 24px;
}

.page-head {
  margin-bottom: 16px;
  .page-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 26px;
    font-weight: 800;
    color: #0f172a;
  }
  .page-sub { font-size: 14px; color: #64748b; margin-top: 4px; }
}

.search-card {
  border-color: rgba(13, 148, 136, 0.2) !important;
}

.search-input {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: $radius-lg;
  color: #9ca3af;
  margin-bottom: 16px;

  input {
    flex: 1;
    border: 0;
    outline: 0;
    background: transparent;
    font-family: inherit;
    font-size: 14px;
  }
}

.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .skel-row {
    display: flex;
    gap: 12px;
    align-items: center;
  }
  .skel-lines {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
}

.coming-hint {
  margin-top: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/mock/knowledge.json frontend/apps/student-app/src/views/knowledge/KnowledgeSearch.vue
git commit -m "feat(student-app): add knowledge mock + KnowledgeSearch skeleton view"
```

---

### Task 11.2 · KnowledgeGraph 交互式图谱

**Files:**
- Create: `frontend/apps/student-app/src/views/knowledge/KnowledgeGraph.vue`

> 设计稿 §6.5。SVG 渲染 + 拖拽 / 缩放 / 选中节点切详情。

- [ ] **Step 1: 写完整页面**

```vue
<!-- frontend/apps/student-app/src/views/knowledge/KnowledgeGraph.vue -->
<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import kgData from '@/mock/knowledge.json'
import { ArrowRight, Plus, Minus } from '@element-plus/icons-vue'

const router = useRouter()

const nodes = ref([...kgData.nodes])
const edges = ref([...kgData.edges])
const selectedId = ref('os')

const selected = computed(() => {
  const base = nodes.value.find((n) => n.id === selectedId.value)
  const detail = kgData.details[selectedId.value] || null
  return { ...base, detail }
})

// 视口变换
const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

// 拖拽状态
const isDragging = ref(false)
let lastX = 0
let lastY = 0

function onMouseDown(e) {
  isDragging.value = true
  lastX = e.clientX
  lastY = e.clientY
}
function onMouseMove(e) {
  if (!isDragging.value) return
  offsetX.value += e.clientX - lastX
  offsetY.value += e.clientY - lastY
  lastX = e.clientX
  lastY = e.clientY
}
function onMouseUp() { isDragging.value = false }

function onWheel(e) {
  e.preventDefault()
  const factor = e.deltaY > 0 ? 0.9 : 1.1
  scale.value = Math.max(0.5, Math.min(2.5, scale.value * factor))
}

function zoom(factor) {
  scale.value = Math.max(0.5, Math.min(2.5, scale.value * factor))
}
function reset() {
  scale.value = 1
  offsetX.value = 0
  offsetY.value = 0
}

function selectNode(id) {
  selectedId.value = id
}

function nodeColor(type) {
  if (type === 'root') return '#0d9488'
  if (type === 'concept') return '#14b8a6'
  if (type === 'instance') return '#2dd4bf'
  if (type === 'error') return '#f59e0b'
  return '#64748b'
}

function goQA() {
  if (!selected.value?.label) return
  router.push({ path: '/qa/ask', query: { topic: selected.value.label } })
}
</script>

<template>
  <div class="kg-page">
    <!-- 画布 -->
    <div
      class="canvas"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
      @wheel="onWheel"
    >
      <svg class="canvas-svg" viewBox="0 0 800 600" preserveAspectRatio="xMidYMid meet">
        <defs>
          <radialGradient id="nodeHalo">
            <stop offset="0%" stop-color="#5eead4" stop-opacity="0.7" />
            <stop offset="100%" stop-color="#0d9488" stop-opacity="0" />
          </radialGradient>
        </defs>

        <g :transform="`translate(${offsetX}, ${offsetY}) scale(${scale})`">
          <!-- 连线 -->
          <line
            v-for="(e, i) in edges"
            :key="i"
            :x1="nodes.find(n => n.id === e.from).x"
            :y1="nodes.find(n => n.id === e.from).y"
            :x2="nodes.find(n => n.id === e.to).x"
            :y2="nodes.find(n => n.id === e.to).y"
            stroke="rgba(13, 148, 136, 0.35)"
            stroke-width="1.2"
          />
          <!-- 节点 -->
          <g
            v-for="n in nodes"
            :key="n.id"
            class="node-group"
            :class="{ selected: selectedId === n.id }"
            @click.stop="selectNode(n.id)"
          >
            <circle :cx="n.x" :cy="n.y" :r="n.r * 2" fill="url(#nodeHalo)" />
            <circle
              :cx="n.x"
              :cy="n.y"
              :r="n.r"
              :fill="nodeColor(n.type)"
              stroke="#fff"
              stroke-width="2"
            />
            <text
              :x="n.x"
              :y="n.y + 4"
              text-anchor="middle"
              fill="#fff"
              :font-size="n.r > 12 ? 11 : 9"
              font-weight="600"
              font-family="Manrope"
              style="pointer-events: none;"
            >{{ n.label }}</text>
          </g>
        </g>
      </svg>

      <!-- 工具栏 -->
      <div class="tools">
        <button class="tool-btn" title="放大" @click="zoom(1.2)"><el-icon><Plus /></el-icon></button>
        <button class="tool-btn" title="缩小" @click="zoom(0.8)"><el-icon><Minus /></el-icon></button>
        <button class="tool-btn" title="重置" @click="reset">⊕</button>
      </div>
    </div>

    <!-- 右侧详情面板 -->
    <GlassCard tier="base" padding="md" class="detail-panel">
      <div v-if="selected" class="detail-content">
        <div class="detail-head">
          <span class="detail-dot" :style="{ background: nodeColor(selected.type) }"></span>
          <h3 class="detail-name">{{ selected.label }}</h3>
        </div>
        <div class="detail-tags">
          <ModuleTag module="knowledge" size="sm">{{ selected.type }}</ModuleTag>
          <ModuleTag v-if="selected.detail?.errorCount" module="analysis" size="sm">
            {{ selected.detail.errorCount }} 个错题
          </ModuleTag>
        </div>
        <p class="detail-desc">
          {{ selected.detail?.desc || '该节点暂无详细描述。' }}
        </p>

        <div v-if="selected.detail?.related?.length" class="related">
          <div class="related-label">关联知识点</div>
          <div class="related-list">
            <div
              v-for="r in selected.detail.related"
              :key="r"
              class="related-item"
            >{{ r }}</div>
          </div>
        </div>

        <GlowButton size="md" block @click="goQA">
          去问答
          <template #suffix><el-icon><ArrowRight /></el-icon></template>
        </GlowButton>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.kg-page {
  --module-color-500: #0d9488;
  --module-color-700: #0f766e;
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 16px;
  padding: 16px;
  height: calc(100vh - 64px - 32px);

  @media (max-width: $bp-laptop) {
    grid-template-columns: 1fr;
    height: auto;
  }
}

.canvas {
  position: relative;
  background: linear-gradient(180deg, #f0fdfa, #fff);
  border: 1px solid rgba(13, 148, 136, 0.1);
  border-radius: $radius-xl;
  overflow: hidden;
  cursor: grab;

  &:active { cursor: grabbing; }

  .canvas-svg {
    width: 100%;
    height: 100%;
    user-select: none;
  }
}

.node-group {
  cursor: pointer;

  &:hover circle:last-of-type {
    filter: brightness(1.1);
  }
  &.selected circle:last-of-type {
    filter: drop-shadow(0 0 12px rgba(45, 212, 191, 0.8));
  }
}

.tools {
  position: absolute;
  top: 16px; right: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-btn {
  width: 32px; height: 32px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(13, 148, 136, 0.2);
  border-radius: $radius-md;
  color: #0d9488;
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: #fff;
    box-shadow: 0 2px 8px rgba(13, 148, 136, 0.2);
  }
}

.detail-panel {
  border-color: rgba(13, 148, 136, 0.2) !important;
  height: fit-content;
  position: sticky;
  top: 80px;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .detail-dot {
    width: 10px; height: 10px;
    border-radius: 50%;
    box-shadow: 0 0 8px currentColor;
  }
  .detail-name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
}

.detail-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.detail-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.65;
  margin-bottom: 16px;
}

.related {
  margin-bottom: 16px;

  .related-label {
    font-size: 11px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 6px;
  }
  .related-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .related-item {
    padding: 6px 10px;
    background: rgba(13, 148, 136, 0.05);
    border-left: 2px solid #14b8a6;
    border-radius: 0 $radius-md $radius-md 0;
    font-size: 12px;
    color: #0f172a;
  }
}
</style>
```

- [ ] **Step 2: 验证 & Commit**

```bash
pnpm dev  # 访问 /knowledge/graph 目测 SVG 图谱、点击节点、拖拽、滚轮缩放
git add frontend/apps/student-app/src/views/knowledge/KnowledgeGraph.vue
git commit -m "feat(student-app): add KnowledgeGraph visual shell with interactive SVG + detail panel"
```

---

## Phase 12 · 个人中心视觉壳

### Task 12.1 · mock + 个人资料主页

**Files:**
- Create: `frontend/apps/student-app/src/mock/user.json`
- Create: `frontend/apps/student-app/src/views/user/UserProfile.vue`

- [ ] **Step 1: 写 mock**

```json
// frontend/apps/student-app/src/mock/user.json
{
  "profile": {
    "id": "LJD2024",
    "name": "刘俊达",
    "college": "计算机与软件工程学院",
    "major": "软件工程",
    "studentNo": "2024001",
    "email": "liujunda@example.edu",
    "bio": "喜欢折腾系统和 AI 的本科生。"
  },
  "stats": {
    "coursesLearned": 12,
    "coursesTotal": 24,
    "qaCount": 156,
    "streakDays": 28,
    "favorites": 43
  },
  "notifications": [
    { "id": 1, "type": "system", "title": "系统维护通知", "time": "今天 14:30", "read": false },
    { "id": 2, "type": "course", "title": "新课程《编译原理》上线", "time": "昨天", "read": false }
  ],
  "favorites": [
    { "id": 1, "title": "深度学习入门", "type": "course" },
    { "id": 2, "title": "红黑树的平衡策略", "type": "qa" }
  ]
}
```

- [ ] **Step 2: 写 UserProfile 主页**

```vue
<!-- frontend/apps/student-app/src/views/user/UserProfile.vue -->
<script setup>
import { ref } from 'vue'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import userMock from '@/mock/user.json'

const profile = ref({ ...userMock.profile })
const stats = userMock.stats
</script>

<template>
  <div class="profile-page">
    <header class="page-head">
      <h1 class="page-title">个人资料</h1>
      <GlowButton size="sm">保存</GlowButton>
    </header>

    <!-- 基本信息卡 -->
    <GlassCard tier="base" padding="lg" class="info-card">
      <div class="avatar-row">
        <div class="avatar-wrap">
          <div class="avatar"></div>
          <button class="avatar-btn" title="更换头像">📷</button>
        </div>
        <div>
          <div class="name">{{ profile.name }}</div>
          <div class="meta">ID · {{ profile.id }}</div>
        </div>
      </div>

      <div class="form-grid">
        <div class="field">
          <label>昵称</label>
          <input v-model="profile.name" />
        </div>
        <div class="field">
          <label>学号</label>
          <input :value="profile.studentNo" disabled />
        </div>
        <div class="field">
          <label>学院</label>
          <input :value="profile.college" disabled />
        </div>
        <div class="field">
          <label>专业</label>
          <input :value="profile.major" disabled />
        </div>
        <div class="field">
          <label>邮箱</label>
          <input v-model="profile.email" />
        </div>
        <div class="field field-wide">
          <label>个人简介</label>
          <textarea v-model="profile.bio" rows="2"></textarea>
        </div>
      </div>
    </GlassCard>

    <!-- 统计卡 -->
    <div class="stats-grid">
      <GlassCard tier="light" padding="md" class="stat stat-course">
        <div class="label">已学课程</div>
        <div class="num blue">
          {{ stats.coursesLearned }}<span class="sub">/{{ stats.coursesTotal }}</span>
        </div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-qa">
        <div class="label">提问次数</div>
        <div class="num purple">{{ stats.qaCount }}</div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-streak">
        <div class="label">连续学习</div>
        <div class="num pink">
          {{ stats.streakDays }}<span class="sub">天</span>
        </div>
      </GlassCard>
      <GlassCard tier="light" padding="md" class="stat stat-fav">
        <div class="label">收藏</div>
        <div class="num lemon">{{ stats.favorites }}</div>
      </GlassCard>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;
@use '@/styles/tokens/breakpoints' as *;

.profile-page {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 900px;
  margin: 0 auto;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .page-title {
    font-family: 'Space Grotesk', sans-serif;
    font-size: 24px;
    font-weight: 800;
    color: #0f172a;
  }
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;

  .avatar-wrap {
    position: relative;

    .avatar {
      width: 56px; height: 56px;
      background: linear-gradient(135deg, #64748b, #94a3b8);
      border-radius: $radius-2xl;
    }
    .avatar-btn {
      position: absolute;
      bottom: -4px; right: -4px;
      width: 24px; height: 24px;
      background: #fff;
      border: 2px solid #e5e7eb;
      border-radius: 50%;
      cursor: pointer;
      font-size: 11px;
    }
  }
  .name {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
  }
  .meta { font-size: 12px; color: #64748b; margin-top: 2px; }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }

  .field {
    label {
      display: block;
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
      margin-bottom: 4px;
    }
    input, textarea {
      width: 100%;
      padding: 8px 12px;
      background: #fff;
      border: 1px solid #e5e7eb;
      border-radius: $radius-md;
      font-family: inherit;
      font-size: 13px;
      color: #0f172a;
      outline: none;
      transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

      &:focus {
        border-color: #64748b;
        box-shadow: 0 0 0 3px rgba(100, 116, 139, 0.12);
      }
      &:disabled {
        background: #f8fafc;
        color: #9ca3af;
      }
    }
  }
  .field-wide { grid-column: 1 / -1; }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: repeat(2, 1fr); }

  .stat {
    .label { font-size: 10px; color: #64748b; }
    .num {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 24px;
      font-weight: 700;
      margin-top: 4px;
    }
    .sub {
      font-size: 11px;
      color: #64748b;
      font-weight: 400;
      margin-left: 2px;
    }
    .blue { color: #2563eb; }
    .purple { color: #9333ea; }
    .pink { color: #db2777; }
    .lemon { color: #ca8a04; }
  }
  .stat-course { border-color: rgba(37, 99, 235, 0.15) !important; }
  .stat-qa { border-color: rgba(147, 51, 234, 0.15) !important; }
  .stat-streak { border-color: rgba(219, 39, 119, 0.15) !important; }
  .stat-fav { border-color: rgba(234, 179, 8, 0.2) !important; }
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/apps/student-app/src/mock/user.json frontend/apps/student-app/src/views/user/UserProfile.vue
git commit -m "feat(student-app): add UserProfile shell with editable form + stats"
```

---

### Task 12.2 · 其余三个子页骨架

**Files:**
- Create: `frontend/apps/student-app/src/views/user/UserSettings.vue`
- Create: `frontend/apps/student-app/src/views/user/UserNotification.vue`
- Create: `frontend/apps/student-app/src/views/user/UserFavorite.vue`

- [ ] **Step 1: 写三个骨架页**

```vue
<!-- frontend/apps/student-app/src/views/user/UserSettings.vue -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
</script>

<template>
  <div class="user-skeleton-page">
    <h1 class="page-title">账号设置</h1>

    <GlassCard tier="base" padding="lg">
      <div v-for="i in 4" :key="i" class="setting-row">
        <div class="row-left">
          <SkeletonBlock width="160px" height="14px" />
          <SkeletonBlock width="240px" height="10px" />
        </div>
        <SkeletonBlock width="48px" height="24px" rounded="full" />
      </div>
      <div class="coming-hint">账号设置的具体表单设计在后续迭代补齐</div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.user-skeleton-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f1f5f9;

  &:last-of-type { border-bottom: 0; }

  .row-left {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
}

.coming-hint {
  margin-top: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}
</style>
```

```vue
<!-- frontend/apps/student-app/src/views/user/UserNotification.vue -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import userMock from '@/mock/user.json'

const notifications = userMock.notifications
</script>

<template>
  <div class="notif-page">
    <h1 class="page-title">消息通知</h1>

    <GlassCard tier="light" padding="none">
      <div class="notif-list">
        <div
          v-for="n in notifications"
          :key="n.id"
          class="notif-item"
          :class="{ unread: !n.read }"
        >
          <div class="type-dot" :class="`type-${n.type}`"></div>
          <div class="body">
            <div class="title">{{ n.title }}</div>
            <div class="time">{{ n.time }}</div>
          </div>
          <div v-if="!n.read" class="unread-dot"></div>
        </div>
      </div>
    </GlassCard>
    <p class="coming-hint">通知中心的完整视觉在后续迭代补齐</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;

.notif-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 14px;
}

.notif-list {
  display: flex;
  flex-direction: column;
}

.notif-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f1f5f9;
  transition: background 0.2s ease;

  &:last-of-type { border-bottom: 0; }
  &:hover { background: rgba(100, 116, 139, 0.04); }
  &.unread .title { color: #0f172a; font-weight: 600; }

  .type-dot {
    width: 8px; height: 8px;
    border-radius: 50%;

    &.type-system { background: #f59e0b; box-shadow: 0 0 6px #f59e0b; }
    &.type-course { background: #2563eb; box-shadow: 0 0 6px #60a5fa; }
  }
  .body { flex: 1; }
  .title { font-size: 13px; color: #475569; }
  .time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
  .unread-dot {
    width: 6px; height: 6px;
    background: #ef4444;
    border-radius: 50%;
  }
}

.coming-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  margin-top: 16px;
}
</style>
```

```vue
<!-- frontend/apps/student-app/src/views/user/UserFavorite.vue -->
<script setup>
import GlassCard from '@/components/common/GlassCard.vue'
import ModuleTag from '@/components/common/ModuleTag.vue'
import userMock from '@/mock/user.json'

const favorites = userMock.favorites
</script>

<template>
  <div class="fav-page">
    <h1 class="page-title">我的收藏</h1>

    <div class="fav-grid">
      <GlassCard
        v-for="f in favorites"
        :key="f.id"
        tier="light"
        padding="md"
        :hover="true"
        class="fav-card"
      >
        <ModuleTag :module="f.type === 'course' ? 'course' : 'qa'" size="sm">
          {{ f.type === 'course' ? '课程' : '问答' }}
        </ModuleTag>
        <div class="title">{{ f.title }}</div>
      </GlassCard>
    </div>

    <p class="coming-hint">收藏完整筛选和管理在后续迭代补齐</p>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/breakpoints' as *;

.fav-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-title {
  font-family: 'Space Grotesk', sans-serif;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 14px;
}

.fav-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;

  @media (max-width: $bp-tablet) { grid-template-columns: 1fr; }

  .fav-card {
    display: flex;
    flex-direction: column;
    gap: 8px;
    cursor: pointer;

    .title {
      font-size: 13px;
      font-weight: 600;
      color: #0f172a;
    }
  }
}

.coming-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  margin-top: 16px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/user/UserSettings.vue frontend/apps/student-app/src/views/user/UserNotification.vue frontend/apps/student-app/src/views/user/UserFavorite.vue
git commit -m "feat(student-app): add user settings / notification / favorite skeleton pages"
```

---

## Phase 13 · RouteState 重写 + 最终联通

### Task 13.1 · 重写 `views/status/RouteState.vue`

**Files:**
- Modify: `frontend/apps/student-app/src/views/status/RouteState.vue`

> 设计稿 §5.4：毛玻璃卡 + 模块色光晕 + 四种状态配色（coming-soon / 404 / 403 / 500）。

- [ ] **Step 1: 全量覆写**

```vue
<!-- frontend/apps/student-app/src/views/status/RouteState.vue -->
<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GlassCard from '@/components/common/GlassCard.vue'
import GlowButton from '@/components/common/GlowButton.vue'
import { Clock, Compass, Lock, Warning, ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const statePresetMap = {
  'coming-soon': {
    badge: '未开放 · Coming Soon',
    title: '功能建设中',
    description: '当前学生端原型暂未开放此页面。',
    icon: Clock,
    accent: '#6366f1',
    accentRgb: '99, 102, 241',
  },
  '404': {
    badge: '404',
    title: '页面不存在',
    description: '你访问的地址没有对应页面，或该地址已被移除。',
    icon: Compass,
    accent: '#f59e0b',
    accentRgb: '245, 158, 11',
  },
  '403': {
    badge: '403',
    title: '暂无权限访问',
    description: '当前页面需要更完整的权限体系支持，学生端原型暂未开放。',
    icon: Lock,
    accent: '#e11d48',
    accentRgb: '225, 29, 72',
  },
  '500': {
    badge: '500',
    title: '页面暂时不可用',
    description: '服务暂时出现异常，请稍后重试。',
    icon: Warning,
    accent: '#ea580c',
    accentRgb: '234, 88, 12',
  },
}

const routeState = computed(() => route.meta.routeState || 'coming-soon')
const preset = computed(() => statePresetMap[routeState.value] || statePresetMap['coming-soon'])

const pageState = computed(() => ({
  badge: route.meta.routeStateLabel || preset.value.badge,
  title: route.meta.stateTitle || preset.value.title,
  description: route.meta.stateDescription || preset.value.description,
  primaryTarget: route.meta.primaryActionTarget || '/home',
  primaryText: route.meta.primaryActionText || '返回首页',
  ...preset.value,
}))

function goPrimary() {
  router.push(pageState.value.primaryTarget)
}
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="route-state-page" :style="{ '--accent': pageState.accent, '--accent-rgb': pageState.accentRgb }">
    <div class="halo"></div>

    <GlassCard tier="base" padding="lg" class="state-card">
      <!-- 图标 -->
      <div class="icon-wrap">
        <el-icon :size="28"><component :is="pageState.icon" /></el-icon>
      </div>

      <!-- 徽章 -->
      <div class="badge">{{ pageState.badge }}</div>

      <h1 class="title">{{ pageState.title }}</h1>
      <p class="desc">{{ pageState.description }}</p>

      <div class="actions">
        <GlowButton size="md" @click="goPrimary">{{ pageState.primaryText }}</GlowButton>
        <button class="btn-back" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回上一页
        </button>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/tokens/radius' as *;
@use '@/styles/tokens/motion' as *;

.route-state-page {
  --module-color-500: var(--accent);
  position: relative;
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: linear-gradient(135deg, #f8fafc 0%, #fff 50%, rgba(var(--accent-rgb), 0.08) 100%);
  overflow: hidden;
}

.halo {
  position: absolute;
  width: 520px; height: 520px;
  background: radial-gradient(circle, rgba(var(--accent-rgb), 0.18) 0%, transparent 60%);
  border-radius: 50%;
  top: -120px; left: 50%;
  transform: translateX(-50%);
  filter: blur(40px);
  pointer-events: none;
}

.state-card {
  text-align: center;
  max-width: 480px;
  width: 100%;
  border-color: rgba(var(--accent-rgb), 0.2) !important;
  box-shadow: 0 16px 48px rgba(var(--accent-rgb), 0.12);
}

.icon-wrap {
  width: 72px; height: 72px;
  margin: 0 auto 16px;
  background: rgba(var(--accent-rgb), 0.1);
  border: 1px solid rgba(var(--accent-rgb), 0.3);
  border-radius: $radius-2xl;
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 32px rgba(var(--accent-rgb), 0.2), 0 0 0 4px rgba(var(--accent-rgb), 0.08);
}

.badge {
  display: inline-block;
  padding: 4px 12px;
  background: rgba(var(--accent-rgb), 0.1);
  border: 1px solid rgba(var(--accent-rgb), 0.25);
  color: var(--accent);
  font-size: 12px;
  font-weight: 600;
  border-radius: $radius-full;
  margin-bottom: 12px;
}

.title {
  font-family: 'Space Grotesk', 'Noto Sans SC', sans-serif;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.desc {
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 24px;
  max-width: 360px;
  margin-left: auto;
  margin-right: auto;
}

.actions {
  display: inline-flex;
  gap: 10px;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid #e5e7eb;
  border-radius: $radius-lg;
  color: #475569;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background $duration-fast $ease-out;

  &:hover { background: #fff; }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/apps/student-app/src/views/status/RouteState.vue
git commit -m "refactor(student-app): rebuild RouteState with glass card + per-state accent"
```

---

## Phase 14 · 最终联通 + 验证

### Task 14.1 · 构建通过性检查

**Files:**
- （无修改；仅验证）

- [ ] **Step 1: 跑生产构建**

```bash
cd frontend/apps/student-app
pnpm build
```

预期：构建成功，输出 `dist/` 目录。若 Sass `@use` 报 "module not found"，检查 `styles/` 下的路径是否都写成不带 `_` 前缀的形式（如 `@use '@/styles/tokens/colors' as *`，而不是 `_colors`）。

- [ ] **Step 2: 跑所有单元测试**

```bash
node --test tests/*.test.js
```

预期：全部 PASS。若某条测试失败，修到通过再继续。

- [ ] **Step 3: 构建产物体积检查**

```bash
ls -lh dist/assets/ | head -20
```

预期：CSS 合并产物 `< 200KB` gzipped（字体文件另算），主 JS chunk `< 500KB` gzipped。若超过，检查：
- 字体是否重复加载（main bundle 应只 import 一次 `styles/fonts.scss`）
- 是否把 mock JSON 打到主 chunk（应按路由懒加载）

- [ ] **Step 4: Commit（若有修正）**

```bash
git add -A
git commit -m "chore(student-app): verify build passes with all redesigned pages"
```

如果没有修改，跳过 Step 4。

---

### Task 14.2 · 手工视觉验收清单

**Files:**
- （无修改；目测清单）

- [ ] **Step 1: 启动 dev 并依次走查每条路由**

```bash
pnpm dev
```

打开 `http://localhost:8080/`，按清单逐项验收：

**落地页 `/`**
- [ ] 顶栏在滚动 60px 后变毛玻璃
- [ ] Hero 背景出现缓慢漂浮的节点云，鼠标靠近节点高亮
- [ ] 主 CTA "立即体验" 磁吸跟随鼠标；点击后从点击点涟漪扩散
- [ ] 特性卡区有 4 张卡，鼠标悬浮 3D 倾斜
- [ ] 滚动到 "#showcase" 区时页面钉住，三张大字报横向切换
- [ ] 字体显示为 Manrope + Noto Sans SC + Hero 用 Space Grotesk

**首页 `/home`**
- [ ] 顶栏中央胶囊显示 6 个模块，"首页"激活态 Indigo 荧光
- [ ] 欢迎卡显示问候语 + 继续学习按钮
- [ ] 快捷问答 chip 点击跳转到 `/qa/ask` 并带 topic query
- [ ] 4 个模块入口卡各带自己模块色的光晕
- [ ] 双栏"我的课程 / 最近问答"渲染正确

**问答 `/qa/ask`**
- [ ] 顶栏激活"问答"（紫色胶囊）
- [ ] 左侧副导航显示"新建对话"按钮 + 3 条历史会话（当前一条高亮紫色）
- [ ] 主区渲染一轮示例对话（用户气泡紫色渐变，AI 气泡毛玻璃）
- [ ] 输入框聚焦出现紫色荧光环
- [ ] 输入任意文字 + 回车，出现新气泡，800ms 后 mock AI 回复追加

**问答 `/qa/history`**
- [ ] 筛选 tab 点击切换（全部 / 未读）
- [ ] 学科 chip 点击筛选
- [ ] 卡片网格显示（hover 态抬升 + 紫色 glow）

**问答 `/qa/detail/1`**
- [ ] 面包屑显示"问答 / OS · 第 3 章 / 详情"
- [ ] 主毛玻璃卡渲染问答内容
- [ ] 底部"知识图谱关联"青色卡 + chip，点击跳 `/knowledge/graph?focus=xxx`

**课程 `/course/list`**
- [ ] 顶栏激活"课程"（蓝色胶囊）
- [ ] 左侧副导航显示"全部 / 我的 / 收藏 / 报告"
- [ ] 搜索框聚焦有蓝色荧光环
- [ ] 分类 chip 切换筛选
- [ ] 课程卡 hover 抬升，封面图缩放

**课程 `/course/detail/1`**
- [ ] 主毛玻璃卡渲染课程封面 + 介绍
- [ ] 已加入课程显示蓝色"继续学习"高亮条
- [ ] 章节目录渲染，已完成章节带蓝色勾选

**课程 `/course/learn/1`**
- [ ] 整页深色背景
- [ ] 视频占位区带蓝色光晕播放按钮
- [ ] 底部"笔记 / 问问 AI" tab 切换时背景色从蓝 → 紫
- [ ] 右侧目录深色毛玻璃，当前章节高亮

**课程 `/course/my`**
- [ ] 4 个统计卡（数字分别用蓝 / 绿 / 琥珀 / 柠檬）
- [ ] Tab 切换"进行中 / 已完成 / 收藏"
- [ ] 课程列表带进度条

**知识图谱 `/knowledge/graph`**
- [ ] 顶栏激活"图谱"（青色胶囊）
- [ ] 左侧副导航显示图谱浏览 / 知识检索 / 学科 chip / 类型色例
- [ ] SVG 图谱显示中心节点 + 4 个一级子节点 + 4 个叶子
- [ ] 点击节点 → 右侧详情面板切换
- [ ] 鼠标拖拽 → 平移图谱
- [ ] 滚轮 → 缩放
- [ ] 右上角 +/-/⊕ 工具按钮可用
- [ ] 右侧详情卡底部"去问答"按钮点击跳 `/qa/ask?topic=xxx`

**知识图谱 `/knowledge/search`**
- [ ] 显示骨架级视觉稿（shimmer 动画）

**个人中心 `/user/profile`**
- [ ] 顶栏无激活（User 路由不在顶栏主导航里）
- [ ] 左侧副导航显示个人卡 + 4 个菜单项
- [ ] 消息通知带琥珀色图标 + Badge "12"
- [ ] 我的收藏图标柠檬色
- [ ] 主区：头像 + 基本信息表单 + 4 个统计卡

**个人中心其余三页**
- [ ] `/user/settings`：骨架级 shimmer
- [ ] `/user/notification`：通知列表正常
- [ ] `/user/favorite`：2 张收藏卡

**状态页 `/404` / `/403` / `/500`**
- [ ] 每个状态有自己的配色：404 琥珀、403 玫红、500 橙
- [ ] 图标、徽章、标题、描述、按钮一致

**coming-soon 路由**（例如 `/community/discuss`）
- [ ] 显示 Indigo 配色的"未开放"状态卡

**响应式**
- [ ] 拖动浏览器从 1440 到 1024，页面不崩
- [ ] 1024 以下顶栏搜索框宽度收窄
- [ ] 768 以下顶栏搜索框隐藏，侧导航消失（抽屉后续迭代）

**动效无障碍**
- [ ] 在系统设置启用"减少动效"后，重新打开落地页：节点云速度大幅降低、特性卡不倾斜、磁吸禁用

- [ ] **Step 2: 所有项通过后记录最终状态**

```bash
git log --oneline -30   # 查看本轮所有 commit
git status              # 应该 clean
```

- [ ] **Step 3: 可选 · 打个里程碑 tag**

```bash
git tag -a student-app-redesign-v1 -m "学员端视觉与页面组织重设计 · v1"
```

---

## 后续迭代

设计稿 §8 明确列出的延后项，不在本轮 scope：

1. 落地页动效 ④ 标题碎聚（GSAP SplitText）
2. 落地页动效 ⑤ 滚动光带 Ribbon（SVG path 全页贯穿）
3. 学习社区 / 学习分析 / 登录注册模块的实际页面
4. 真实数据接入（替换所有 mock）
5. 暗色模式切换
6. 移动端精细适配（`< 768px`）

每一项在立项时，都先补一份独立的 spec（`docs/superpowers/specs/<date>-<topic>-design.md`），再生成对应的 plan。
