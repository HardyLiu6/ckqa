# 管理员端前端美化重构设计规格

- 日期：2026-04-26
- 范围：`frontend/apps/admin-app/`
- 参考：`docs/admin-teacher-frontend-structure.md`、`docs/superpowers/specs/2026-04-21-student-app-ui-redesign-design.md`、`frontend/apps/student-app/`
- 字体方案：DM Sans（正文）+ DM Mono（等宽）
- 目标：在不破坏当前管理员端结构骨架的前提下，把管理员/教师共用前端重构为一套高密度、可扫读、可联调的课程知识库运维界面，并与学生端保持同源但更克制的设计语言

---

## 1. 背景

### 1.1 当前仓库事实

`frontend/apps/admin-app/` 当前已经具备管理端骨架，而不是纯 Vite 起步页：

1. 技术栈是 Vue 3 + Vite + Vue Router + Axios，暂未引入 Element Plus、Pinia、Sass 或图标库。
2. `src/router/routes.js` 已定义登录、状态页、工作台、课程与资料、知识库构建、问答运维、用户与权限、系统与审计等路由。
3. `src/layouts/` 已有 `AuthLayout`、`ConsoleLayout`、`DetailLayout`、`WorkflowLayout`。
4. `src/stores/auth.js` 已有开发态管理员/教师身份切换、权限点判断和数据范围展示。
5. `src/axios/index.js` 默认请求 Java `/api/v1`，并保留认证头注入与错误收敛。
6. `src/views/pages/ModulePage.vue` 与 `module-content.js` 已经把多数业务页面收敛为表格、概览、工作流三类模型。
7. `src/views/system/HealthView.vue` 已经调用 `GET /api/v1/system/health`，但界面仍偏原始 JSON 展示。
8. `src/style.css` 目前是单文件全局样式，已具备基础色、卡片、侧栏、表格和工作流样式，但视觉层级、状态表现和页面密度还需要系统化。

### 1.2 与既有结构文档的关系

`docs/admin-teacher-frontend-structure.md` 解决的是信息架构问题：有哪些页面、路由、布局壳、权限点和首版范围。

本设计解决的是界面美化与交互重构问题：这些页面应该长什么样、如何扫读、如何把复杂工作流呈现给管理员/教师、如何参考学生端但不照搬学生端。

因此本设计不替换结构文档，而是作为后续实现视觉重构和交互优化的规格输入。

---

## 2. 设计目标与非目标

### 2.1 设计目标

1. **运维台气质明确**：界面应像课程知识库生产与运维平台，而不是营销页或通用后台模板。
2. **信息密度提升**：管理员和教师需要快速判断课程、资料、解析、导出、索引、问答验证、系统健康的状态，首屏要支持扫读。
3. **复用学生端设计资产**：继承学生端的 Slate 中性色、模块色、毛玻璃、轻动效、统一 token 思路，但降低光效和装饰强度。
4. **保持管理端克制**：管理端以表格、状态矩阵、工作流、日志摘要为核心，不使用大面积落地页式 Hero、粒子、强渐变和过重动效。
5. **真实边界清晰**：已接 Java `/api/v1` 的页面与仍使用本地示例数据的页面必须在视觉上区分，避免把占位数据包装成已联调完成。
6. **可分阶段实现**：第一阶段只做视觉系统、布局壳、工作台、健康页和通用页面组件；不强行接入所有业务 API。

### 2.2 非目标

1. 不在本次视觉重构中直接实现正式登录接口。
2. 不直接请求 GraphRAG Python `/v1`，正式业务边界仍是 Java `/api/v1`。
3. 不实现资料上传 UI，直到 Java 上传链路确认。
4. 不新增复杂教务能力，例如排课、选课、成绩管理。
5. 不把学生端的深色落地页、强光晕和营销排版搬到管理端。
6. 不把所有占位页面一次性改成真实业务页，真实接入按后续 API 优先级推进。

---

## 3. 设计方向

### 3.1 最终方向：运维玻璃控制台（方案 A）

基调是亮色高密度控制台，配合半透明顶栏、冷静的侧边导航、状态矩阵、紧凑表格和流程轨道。

选择理由：与学生端共享毛玻璃、模块色和轻动效体系，视觉上属于同一产品家族；更适合管理端长时间使用，表格、日志和状态信息不会被强装饰干扰；能直接承接当前 `ConsoleLayout`、`ModulePage`、`WorkflowLayout` 的代码结构。

### 3.2 局部诊断语言（方案 C 的局部应用）

方案 C 的深色高对比风格**只**用于以下区域，不作为全站风格：

- 系统健康页中的实时诊断面板。
- 索引运行详情中的日志与命令行式输出区域。
- 检索日志详情中的 GraphRAG / LanceDB / 任务状态排障区域。
- 失败解析、失败导出、失败索引等需要强提醒的详情块。

### 3.3 交互确认结论

1. 全站默认采用方案 A（运维玻璃控制台）。
2. 暗色模式是方案 A 的暗色 token 版本，不是方案 C 的全站大屏形态。
3. 主题能力采用"受控主题系统"：`light / dark / auto` 三种模式。
4. 主题色从固定色板中选择，不开放任意取色器。
5. 工作台首屏采用"指标 + 生产链路轨道 + 近期任务 + 异常摘要"的高信息密度布局。
6. 知识库构建向导采用"左侧步骤 + 中央当前动作 + 右侧任务状态/日志常驻"的布局。

---

## 4. 视觉系统

### 4.1 总体气质

管理员端使用"明亮运维台"风格：

1. 页面底色使用浅 Slate，不使用纯白大面积铺底。
2. 内容容器使用白色或半透明白，边框明确，阴影克制。
3. 模块主色只用于导航激活态、状态强调、按钮、图标和关键指标。
4. 圆角默认不超过 8px，保持工具型界面的利落感。
5. 卡片只用于独立信息单元、表格容器、状态面板和工作流步骤，不把页面区块层层包成嵌套卡片。

### 4.2 字体系统

#### 4.2.1 字体选型：DM Sans + DM Mono

选用 [DM Sans](https://fonts.google.com/specimen/DM+Sans)（正文无衬线）和 [DM Mono](https://fonts.google.com/specimen/DM+Mono)（等宽）作为全站字体。

选型理由：字形开放圆润，数字间距宽松辨识度高；14px 小字号下仍清晰，管理端表格密集场景下不会疲劳；等宽字体（DM Mono）与正文（DM Sans）风格同源，日志面板与表格之间的视觉反差自然。

注意：DM Sans / DM Mono 主要覆盖拉丁字母、数字、ID、状态码和日志字符。中文内容仍通过 `PingFang SC`、`Hiragino Sans GB`、`Microsoft YaHei` 等系统中文字体回退。验收重点不是“完全没有系统字体”，而是中英文混排稳定、行高一致、数字和任务 ID 清晰。

#### 4.2.2 安装

```bash
# 在 admin-app 中安装
pnpm add @fontsource/dm-sans @fontsource/dm-mono
```

#### 4.2.3 引入方式

在 `src/main.js` 中引入所需字重：

```javascript
// DM Sans：400 正文 / 500 中等 / 700 粗体
import '@fontsource/dm-sans/400.css'
import '@fontsource/dm-sans/500.css'
import '@fontsource/dm-sans/700.css'

// DM Mono：400 等宽正文
import '@fontsource/dm-mono/400.css'
```

#### 4.2.4 CSS 字体 Token

在 `src/styles/tokens.css` 中定义：

```css
:root {
  /* 正文字体：DM Sans 优先，中文回退到系统字体 */
  --ckqa-font-sans:
    "DM Sans",
    "PingFang SC",
    "Hiragino Sans GB",
    "Microsoft YaHei",
    system-ui,
    sans-serif;

  /* 等宽字体：DM Mono 优先，用于日志、ID、代码、原始 JSON */
  --ckqa-font-mono:
    "DM Mono",
    "JetBrains Mono",
    "Cascadia Code",
    "Fira Code",
    ui-monospace,
    monospace;
}
```

全局应用：

```css
body {
  font-family: var(--ckqa-font-sans);
}

/* 所有需要等宽渲染的场景统一使用 --ckqa-font-mono */
.log-panel,
.diagnostic-panel,
.raw-json,
.task-id,
code,
pre {
  font-family: var(--ckqa-font-mono);
}
```

#### 4.2.5 字号 Token

| Token | 字号 | 字重 | 用途 |
| --- | --- | --- | --- |
| `--fs-page-title` | 26px | 700 | 页面主标题 |
| `--fs-section-title` | 18px | 700 | 面板标题、分区标题 |
| `--fs-card-title` | 15px | 500 | 卡片标题、表格组标题 |
| `--fs-body` | 14px | 400 | 正文、表格正文 |
| `--fs-body-sm` | 13px | 400 | 辅助说明、次要字段 |
| `--fs-caption` | 12px | 500 | 状态标签、小标题、徽标文字 |
| `--fs-metric` | 28px–32px | 700 | 工作台指标数字 |
| `--fs-log` | 12px | 400 | 日志输出（等宽） |

**关于数字指标**：`--fs-metric` 使用固定容器高度，避免数据刷新时造成布局跳动。管理端不使用超过 32px 的 hero 级大字。

### 4.3 颜色 Token

#### 4.3.1 亮色主题基础 Token

| 语义 Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#f8fafc` | 主工作区页面底色 |
| `--ckqa-surface` | `#ffffff` | 表格、面板、浮层 |
| `--ckqa-surface-muted` | `#f1f5f9` | 筛选条、状态块、空状态底 |
| `--ckqa-border` | `#e2e8f0` | 面板、表格、输入框边框 |
| `--ckqa-text` | `#0f172a` | 标题、关键数字 |
| `--ckqa-text-muted` | `#64748b` | 描述、辅助字段 |
| `--ckqa-text-weak` | `#94a3b8` | 占位、时间、注释 |

#### 4.3.2 暗色主题基础 Token

| 语义 Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#020617` | 夜间主工作区页面底色 |
| `--ckqa-surface` | `#0f172a` | 夜间表格、面板、浮层 |
| `--ckqa-surface-muted` | `#1e293b` | 夜间筛选条、状态块、空状态底 |
| `--ckqa-border` | `#334155` | 夜间面板、表格、输入框边框 |
| `--ckqa-text` | `#f8fafc` | 夜间标题、关键数字 |
| `--ckqa-text-muted` | `#cbd5e1` | 夜间描述、辅助字段 |
| `--ckqa-text-weak` | `#94a3b8` | 夜间占位、时间、注释 |

#### 4.3.3 模块色

| 管理端模块 | 主色 | 色值 | 使用位置 |
| --- | --- | --- | --- |
| 工作台 | Indigo | `#6366f1` | 生产链路总览、主入口 |
| 课程与资料 | Blue | `#2563eb` | 课程、资料、解析 |
| 知识库构建 | Teal | `#0d9488` | GraphRAG 导出、索引、激活 |
| 问答运维 | Purple | `#9333ea` | QA 会话、检索诊断 |
| 用户与权限 | Amber | `#d97706` | RBAC、课程成员 |
| 系统与审计 | Slate | `#475569` | 健康、审计、运维状态 |

#### 4.3.4 主题色固定色板（用户可选）

| 色板 | 色值 | 推荐场景 |
| --- | --- | --- |
| Indigo | `#6366f1` | 默认主题、工作台 |
| Blue | `#2563eb` | 课程与资料 |
| Teal | `#0d9488` | 知识库构建、健康诊断 |
| Purple | `#9333ea` | 问答运维 |
| Amber | `#d97706` | 用户、权限、告警辅助 |

不提供任意取色器。固定色板应区分 `accent`、`accent-strong` 和 `accent-contrast` 三类用途，避免把装饰色直接用于小字号正文。

#### 4.3.5 语义状态色

| 状态 | 色值 | 用途 |
| --- | --- | --- |
| 成功 `success` | `#10b981` | done、ready、success、reachable |
| 运行中 `running` | `#3b82f6` | running、processing、indexing |
| 等待 `pending` | `#f59e0b` | pending、skipped、manual check |
| 阻塞 `blocked` | `#64748b` | blocked、not configured |
| 失败 `failed` | `#ef4444` | failed、timeout、unreachable |

#### 4.3.6 对比度验收要求

验收标准遵循 WCAG AA：

- 14px 正常字重文字在对应背景上对比度不低于 4.5:1。
- badge 和状态 label 的文字（12px 粗体）对比度不低于 3:1。
- 所有主题色（Indigo / Blue / Teal / Purple / Amber）在亮色和暗色主题下均需通过以上标准。
- `--ckqa-accent` 可用于装饰、边框、淡背景和非文字强调；小字号文字必须使用 `--ckqa-accent-strong`。
- 按钮上的文字必须使用 `--ckqa-accent-contrast`，通常为 `#ffffff`，并且按钮底色应选择满足对比度的强色阶。

已验证示例（亮色主题，按标准 WCAG 公式计算）：

| 前景 | 背景 | 对比度 | 结论 |
| --- | --- | --- | --- |
| Indigo strong `#4f46e5` | `#ffffff` | 6.29:1 | 通过 AA |
| Teal strong `#0f766e` | `#ffffff` | 5.47:1 | 通过 AA |
| Amber strong `#b45309` | `#fef3c7` | 4.51:1 | 通过 AA |
| Slate text `#0f172a` | `#ffffff` | 17.85:1 | 通过 AAA |

以下色值不应直接作为普通 14px 文字使用在白底上：Teal `#0d9488`、Indigo `#6366f1`。它们可以作为装饰色、按钮底色、图标色或浅色背景的边框色。固定色板变更后，应在测试中重新校验 strong / contrast 派生 token，而不是依赖运行时任意取色。

### 4.4 间距、圆角、阴影

1. 间距使用 4px 基准：`4 / 8 / 12 / 16 / 20 / 24 / 32`。
2. 页面主 padding 桌面端 24px，窄屏降到 16px。
3. 容器圆角：按钮、输入框、表格容器、卡片统一 8px。
4. 胶囊标签可使用 `999px`。
5. 阴影只在顶栏、浮层、悬浮行和关键面板使用，默认面板优先靠边框建立层级。

### 4.5 毛玻璃与动效

1. 顶栏使用 `rgba(255,255,255,0.88)` 加 `backdrop-filter: blur(16px)`。
2. 侧栏不使用强玻璃，保持稳定浅色底，便于长时间识别导航。
3. 面板默认不使用大面积 glass，只有登录页、系统健康摘要和工作流聚焦区允许轻玻璃。
4. hover 动效只做 `translateY(-1px)`、边框变色和轻阴影。
5. 页面切换不做复杂动画；工作流步骤切换可用 160ms 到 220ms 的淡入与状态色过渡。
6. 必须支持 `prefers-reduced-motion`，减少或关闭 transform 动效。

### 4.6 骨架屏规范

**组件**：`SkeletonBlock.vue`

```css
.skeleton-block {
  background: var(--ckqa-surface-muted);
  border-radius: 4px;
  overflow: hidden;
  position: relative;
}

.skeleton-block::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255,255,255,0.18) 50%,
    transparent 100%
  );
  animation: skeleton-sweep 1.4s ease-in-out infinite;
}

@keyframes skeleton-sweep {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block::after {
    animation: none;
    background: transparent;
  }
}
```

暗色主题下：将扫光透明度调整为 `rgba(255,255,255,0.06)`，不需要额外的 token 覆盖。

### 4.7 主题模式系统

#### 4.7.1 模式定义

| 模式 | 行为 | 使用场景 |
| --- | --- | --- |
| `light` | 强制使用亮色 A 控制台 | 白天办公、投影、强光环境 |
| `dark` | 强制使用 A 的暗色 token 版本 | 夜间办公、弱光排障 |
| `auto` | 跟随系统 `prefers-color-scheme` | 默认模式、无明确偏好 |

#### 4.7.2 CSS 落地

根节点属性控制：

```html
<html data-theme="light" data-accent="indigo">
```

完整 CSS 变量定义：

```css
:root {
  --ckqa-bg:           #f8fafc;
  --ckqa-surface:      #ffffff;
  --ckqa-surface-muted:#f1f5f9;
  --ckqa-border:       #e2e8f0;
  --ckqa-text:         #0f172a;
  --ckqa-text-muted:   #64748b;
  --ckqa-text-weak:    #94a3b8;
  --ckqa-accent:          #6366f1;
  --ckqa-accent-strong:   #4f46e5;
  --ckqa-accent-contrast: #ffffff;

  --ckqa-font-sans:
    "DM Sans", "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", system-ui, sans-serif;

  --ckqa-font-mono:
    "DM Mono", "JetBrains Mono", "Cascadia Code",
    ui-monospace, monospace;
}

[data-theme="dark"] {
  --ckqa-bg:           #020617;
  --ckqa-surface:      #0f172a;
  --ckqa-surface-muted:#1e293b;
  --ckqa-border:       #334155;
  --ckqa-text:         #f8fafc;
  --ckqa-text-muted:   #cbd5e1;
  --ckqa-text-weak:    #94a3b8;
}

[data-accent="indigo"] {
  --ckqa-accent:          #6366f1;
  --ckqa-accent-strong:   #4f46e5;
  --ckqa-accent-contrast: #ffffff;
}

[data-accent="blue"] {
  --ckqa-accent:          #2563eb;
  --ckqa-accent-strong:   #1d4ed8;
  --ckqa-accent-contrast: #ffffff;
}

[data-accent="teal"] {
  --ckqa-accent:          #0d9488;
  --ckqa-accent-strong:   #0f766e;
  --ckqa-accent-contrast: #ffffff;
}

[data-accent="purple"] {
  --ckqa-accent:          #9333ea;
  --ckqa-accent-strong:   #7e22ce;
  --ckqa-accent-contrast: #ffffff;
}

[data-accent="amber"] {
  --ckqa-accent:          #d97706;
  --ckqa-accent-strong:   #b45309;
  --ckqa-accent-contrast: #ffffff;
}
```

#### 4.7.3 主题 Store（`src/stores/theme.js`）

```javascript
import { reactive } from 'vue'

const STORAGE_KEY = 'ckqa-theme'
const VALID_MODES = new Set(['light', 'dark', 'auto'])
const VALID_ACCENTS = new Set(['indigo', 'blue', 'teal', 'purple', 'amber'])
const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

const state = reactive({
  mode:          'auto',    // 'light' | 'dark' | 'auto'
  accent:        'indigo',  // 'indigo' | 'blue' | 'teal' | 'purple' | 'amber'
  resolvedTheme: 'light',   // 实际应用的主题
})

let mediaQuery = null

function getMediaQuery() {
  if (!isBrowser || !window.matchMedia) return null
  return window.matchMedia('(prefers-color-scheme: dark)')
}

function resolve() {
  mediaQuery = mediaQuery ?? getMediaQuery()
  state.resolvedTheme =
    state.mode === 'auto'
      ? (mediaQuery?.matches ? 'dark' : 'light')
      : state.mode
}

function syncDocumentTheme() {
  resolve()
  if (!isBrowser) return
  document.documentElement.setAttribute('data-theme', state.resolvedTheme)
  document.documentElement.setAttribute('data-accent', state.accent)
}

function setMode(mode) {
  if (!VALID_MODES.has(mode)) return
  state.mode = mode
  save()
  syncDocumentTheme()
}

function setAccent(accent) {
  if (!VALID_ACCENTS.has(accent)) return
  state.accent = accent
  save()
  syncDocumentTheme()
}

function save() {
  if (!isBrowser) return
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    mode: state.mode,
    accent: state.accent,
  }))
}

function load() {
  if (!isBrowser) return
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
    if (VALID_MODES.has(saved.mode)) state.mode = saved.mode
    if (VALID_ACCENTS.has(saved.accent)) state.accent = saved.accent
  } catch {}
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

实现要求：`themeStore` 不得在模块顶层直接访问 `window`、`document` 或 `localStorage`，以便 `node --test` 可以在无 DOM 环境中安全导入；应用启动时由 `main.js` 调用 `themeStore.initTheme()`。

---

## 5. 数据来源标记规范

### 5.1 背景

当前多数业务页面仍来自 `module-content.js` 本地配置，必须统一标记数据来源，避免视觉完成度掩盖真实接入状态。

### 5.2 DataSourceChip 组件规范

**位置**：每个业务页面的页面标题右侧，不在顶栏（顶栏 chip 是全局基线，页面级来源标记应在内容区）。

**组件接口**：

```vue
<!-- src/components/common/DataSourceChip.vue -->
<DataSourceChip source="live"     :refreshed-at="lastFetch" />
<DataSourceChip source="mock" />
<DataSourceChip source="skeleton" />
```

**视觉规格**：

| source | 圆点色 | 标签文案 | 附加信息 |
| --- | --- | --- | --- |
| `live` | `#10b981`（绿） | 实时 API | 显示最近刷新时间 |
| `mock` | `#f59e0b`（黄） | 示例数据 | 无 |
| `skeleton` | `#94a3b8`（灰） | 页面骨架 | 无 |

**样式示例**：

```css
.data-source-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface-muted);
  font-family: var(--ckqa-font-mono);
  font-size: 11px;
  font-weight: 500;
  color: var(--ckqa-text-muted);
  white-space: nowrap;
}
```

---

## 6. 布局与交互结构

### 6.1 ConsoleLayout

推荐结构：

```
顶部栏（固定，毛玻璃）
  左：CKQA 标识 + 平台名称 + 环境基线 chip
  中：全局搜索（课程 / 知识库 / 任务 ID）
  右：系统健康入口 + 主题模式 + 主题色 + 当前身份 + 数据范围 + 退出

左侧导航（固定，260px，窄屏收为 72px 图标栏或抽屉）
  一级模块图标 + 文案 + 当前模块以模块色高亮
  upcoming 模块显示轻提示，不隐藏已规划边界

主工作区（独立滚动）
  面包屑（由路由元信息自动生成）
  页面标题 + DataSourceChip
  页面级主操作
  页面主体
```

交互要求：

1. 顶部栏固定，主工作区独立滚动。
2. 角色切换保留开发态身份语义，视觉上标注"开发态"，避免误认为真实登录。
3. `/api/v1` 运行基线移动到环境 chip 中，不占用主操作区。
4. 面包屑使用路由元信息生成，不手写每页重复文案。
5. 主题模式入口使用顶栏轻量控件或弹层：`light / dark / auto` 分段 + 固定主题色色板。

### 6.2 生产链路轨道（ProductionTrack）

工作台核心组件。6 个步骤节点形成横向轨道：

```
课程资料 → PDF 解析 → GraphRAG 导出 → 索引构建 → 索引激活 → 问答验证
```

#### 6.2.1 节点状态组合规则

每个轨道节点同时展示数量和状态，混合态时按以下优先级取节点颜色：

1. 有 `failed` → 节点显示失败红，数量文字显示 `N failed`。
2. 有 `running` 且无 `failed` → 节点显示运行蓝，数量显示 `N running`。
3. 全部 `done` → 节点显示成功绿，数量显示总数。
4. 全部 `pending` → 节点显示等待黄。
5. 未配置 / 无数据 → 节点显示阻塞灰。

示例显示文字：`18 done / 2 failed` → 节点色取失败红。

#### 6.2.2 响应式降级

| 宽度 | 轨道表现 |
| --- | --- |
| `>= 1280px` | 6 节点横向轨道，固定宽度，带连接箭头 |
| `1024px – 1279px` | 节点文字截短，保留图标和数量 |
| `< 1024px` | 降级为竖向步骤列表，不再横向排列 |

### 6.3 DashboardView

工作台首屏布局：

```
顶部栏
  CKQA 运维台 | 全局搜索 | 运行基线 chip | 主题 chip | 当前身份 chip

左侧导航

主内容
  ┌─ 页面标题：课程知识库生产链路 [DataSourceChip: 示例数据] ─────── [进入构建向导]
  │
  ├─ 指标卡（5 个）
  │  可管理课程 / 待处理资料 / 可用知识库 / 问答异常 / 系统健康
  │
  ├─ 生产链路轨道
  │  课程资料 → PDF 解析 → GraphRAG 导出 → 索引构建 → 索引激活 → 问答验证
  │
  ├─ 最近任务列表（时间线感）
  │
  └─ 深色异常摘要面板（C 风格局部）
     [qa-timeout] taskId=qa-20260426-002 timeout 42s
     → 查看系统健康 / 重试索引任务
```

首屏指标：

| 指标 | 说明 |
| --- | --- |
| 可管理课程 | 按当前身份和数据范围过滤 |
| 待处理资料 | `pending`、`failed`、需要解析的资料 |
| 可用知识库 | 有 active index 的知识库 |
| 问答异常 | 失败或超时任务 |
| 系统健康 | Java、MySQL、pdf_ingest、GraphRAG API、LanceDB |

示例内容应使用真实业务语言，不使用灰块或泛化占位：

| 区域 | 示例文案 |
| --- | --- |
| 近期任务 | `操作系统知识库 #2`、`数据结构课程资料`、`问答冒烟验证` |
| 状态 | `索引成功`、`解析中`、`等待确认` |
| 异常摘要（等宽体） | `[qa-timeout] taskId=qa-20260426-002 timeout 42s` |
| 诊断建议 | `查看系统健康 / 重试索引任务` |

### 6.4 HealthView

推荐结构：

```
健康总览条
  总状态 | 最近刷新时间 | 刷新按钮 | API 基线

服务矩阵（区分 reachable 和 ready）
  Java API
  MySQL
  pdf_ingest root
  graphrag_pipeline root
  GraphRAG API
  output/lancedb

局部深色诊断面板（DM Mono，C 风格）
  GraphRAG API 状态
  LanceDB 路径
  最近错误摘要
  建议动作

原始响应（可折叠 pre，DM Mono）
```

交互要求：

1. `reachable` 和 `ready` 必须分开展示，不合并为单一状态。
2. 刷新时只禁用刷新按钮，不阻塞页面阅读。
3. 请求失败显示可恢复错误面板，保留错误状态码和 message。
4. 没有刷新过时显示清晰的 idle 状态，不使用空白卡片。
5. 深色诊断面板服从 `light / dark / auto` 主题：亮色主题下保持深色面板（独立于页面主题），暗色主题下将边框提升到 `#475569`、正文提升到 `#f8fafc`，保证日志内容可读。

### 6.5 ModulePage 变体

#### 6.5.1 table 变体

用于课程列表、知识库列表、问答会话列表、用户列表、课程成员列表。

结构：

```
模块头部（页面标题 + DataSourceChip + 主操作）
筛选工具条（状态分段控件 + 搜索框 + 次要筛选）
数据表格（行高 48px–56px）
分页或记录数
批量操作区
```

大数据量处理：

- 默认分页，每页 20 条或 50 条。
- 不做无限滚动；若后续需要不分页的长日志列表，使用虚拟列表（`vue-virtual-scroller`）。
- `DataTableShell.vue` 预留 `virtualScroll` prop 接口，首版不强制实现。

#### 6.5.2 overview 变体

用于课程详情、资料详情、解析结果详情、知识库详情、索引运行详情、问答会话详情。

结构：

```
摘要条（主对象名 + 当前状态 + 关键时间戳）
关键字段网格（2–3 列）
Tabs（固定高度标题栏，避免切换时跳动）
接入进度 / 运行时间线
关联对象入口
```

#### 6.5.3 workflow 变体（知识库构建向导）

布局：

```
页面标题：OS 知识库构建向导
Tabs：构建流程 / 文档映射 / 索引运行 / 问答验证

左侧（纵向步骤条，220px）
  1. 选择课程资料        done
  2. 解析状态检查        done
  3. 导出 GraphRAG 输入  ready   ← 当前
  4. 创建索引运行        blocked
  5. 激活索引版本        blocked
  6. 问答冒烟验证        blocked

中央动作区
  当前步骤说明
  前置条件检查列表
  资料 / 结果表格
  执行当前步骤按钮

右侧常驻区（280px）
  任务状态
  最近导出 / 前置条件 / 当前阻塞原因
  深色日志预览（DM Mono）
```

首版采用纵向步骤条（不用横向），原因：包含 6 个步骤 + 任务日志，纵向更利于扩展和滚动。

日志更新机制：首版使用轮询（interval 5s），任务运行中自动刷新，完成或失败后停止。后续若 Java 后端支持 SSE，可替换为 EventSource，组件接口保持不变。

步骤状态规格：

| 状态 | 说明 |
| --- | --- |
| `todo` | 未到达，灰色 |
| `ready` | 前置满足，可执行，模块色 |
| `running` | 执行中，蓝色脉冲 |
| `done` | 完成，绿色，可查看结果和重跑 |
| `skipped` | 已跳过，灰色 |
| `blocked` | 前置未满足，按钮禁用并显示原因 |
| `failed` | 失败，红色，优先显示错误摘要 |

### 6.6 LoginView 与状态页

登录页推荐双区布局：

- 左侧：平台定位、生产链路简图、开发态说明。
- 右侧：登录表单或开发态身份选择。
- 明确显示"当前为开发态身份切换，正式登录接口待接入"。

状态页规格：

| 状态 | 呈现 |
| --- | --- |
| 403 | 权限不足，显示当前身份和需要权限 |
| 404 | 页面不存在，提供返回工作台 |
| 500 | 服务异常，提供刷新和系统健康入口 |
| coming-soon | 未开放，显示规划状态和所属模块 |

---

## 7. 组件清单

| 文件 | 类型 | 职责 |
| --- | --- | --- |
| `src/styles/tokens.css` | 新建 | 颜色、字号、间距、圆角、阴影、字体、动效变量 |
| `src/styles/base.css` | 新建 | reset、焦点、基础排版、滚动条、无障碍辅助 |
| `src/styles/components.css` | 新建 | 按钮、标签、表格、面板、状态徽标、空状态 |
| `src/stores/theme.js` | 新建 | 主题模式、主题色、`localStorage` 持久化和根节点属性同步 |
| `src/style.css` | 修改 | 只负责导入分层样式 |
| `src/components/shell/AppTopbar.vue` | 新建 | 顶栏、身份、健康入口、环境 chip |
| `src/components/shell/SideNavigation.vue` | 新建 | 一级导航、激活态、权限过滤后的导航渲染 |
| `src/components/shell/ThemeControl.vue` | 新建 | `light / dark / auto` 模式切换和固定主题色色板 |
| `src/components/common/StatusBadge.vue` | 新建 | 统一状态色、状态文案、可访问标签 |
| `src/components/common/MetricTile.vue` | 新建 | 工作台指标块 |
| `src/components/common/DataSourceChip.vue` | 新建 | 数据来源标记（live / mock / skeleton） |
| `src/components/common/DataTableShell.vue` | 新建 | 表格壳、筛选条、记录数、空状态、虚拟滚动接口 |
| `src/components/common/SkeletonBlock.vue` | 新建 | 骨架屏扫光块，支持 reduced-motion |
| `src/components/common/WorkflowStepper.vue` | 新建 | 构建向导步骤条 |
| `src/components/common/ProductionTrack.vue` | 新建 | 生产链路轨道，含响应式降级 |
| `src/components/common/DiagnosticLogPanel.vue` | 新建 | 局部深色诊断日志面板（DM Mono） |
| `src/components/system/HealthMatrix.vue` | 新建 | 健康检查矩阵和诊断详情 |
| `src/layouts/ConsoleLayout.vue` | 修改 | 组合顶栏、侧栏、工作区 |
| `src/views/dashboard/DashboardView.vue` | 修改 | 按生产链路、异常优先、近期活动重排 |
| `src/views/system/HealthView.vue` | 修改 | 原始 JSON 改为次要折叠信息 |
| `src/views/pages/ModulePage.vue` | 修改 | 使用 DataTableShell、StatusBadge、WorkflowStepper 组合页面 |

### 图标策略

优先引入 `lucide-vue-next`（轻量，Tree-shaking 友好），不同时引入多套图标库。图标只用于导航、行操作、状态提示和按钮辅助，不用作装饰背景。

---

## 8. 交互与状态规范

### 8.1 数据状态四态

| 状态 | UI 表现 |
| --- | --- |
| `idle` | 尚未刷新或等待选择，显示简短空状态文案 |
| `loading` | 局部 SkeletonBlock 或按钮 loading，不遮盖整个页面 |
| `success` | 展示数据、更新时间和 DataSourceChip |
| `error` | 展示错误信息、状态码、重试按钮和相关入口 |

### 8.2 示例数据标记

1. 本地配置数据：页面标题旁显示 `DataSourceChip source="mock"`。
2. Java `/api/v1` 实时数据：`DataSourceChip source="live"` + 最近刷新时间。
3. 未开放路由：使用 `RouteState`，不展示假表格。

### 8.3 权限与数据范围

1. 菜单是否显示由 `authStore.canAccess()` 控制。
2. 按钮层面也要使用同一权限判断，不只依赖路由守卫。
3. 教师身份必须持续展示"授权课程"数据范围。
4. 403 页面应显示当前角色和缺失权限，便于开发态调试。

### 8.4 表格交互

1. 筛选条使用 select、分段控件或搜索框，不把筛选按钮堆满页面。
2. 状态筛选优先用分段控件：全部 / 运行中 / 失败 / 已完成。
3. 行点击进入详情，行内操作只保留查看、重试、日志、更多。
4. 所有按钮文本不换行，窄屏时转为图标按钮加 tooltip。

### 8.5 工作流交互

1. 当前步骤与下一步动作必须在同一视区内。
2. 每步显示前置条件、可执行动作、最近结果和日志入口。
3. 失败步骤优先显示错误摘要，再给"查看日志"和"重试"。
4. 运行中步骤显示任务 ID（DM Mono）、心跳时间和最近日志摘要。

---

## 9. 响应式与无障碍

### 9.1 桌面优先

| 宽度 | 处理 |
| --- | --- |
| `>= 1440px` | 完整侧栏、两到三列内容、表格完整字段 |
| `1024px – 1439px` | 保留侧栏，减少统计卡或改为横向滚动 |
| `768px – 1023px` | 侧栏收窄或抽屉化，表格减少次要列 |
| `< 768px` | 保底可用，优先完成导航、阅读和关键按钮 |

生产链路轨道 `< 1024px` 降级为竖向步骤列表（见 6.2.2 节）。

### 9.2 无障碍要求

1. 保留 skip link。
2. 所有交互元素必须有 `:focus-visible`，焦点环清晰。
3. 状态不能只靠颜色区分，`StatusBadge` 必须有文字标签。
4. 表格使用语义化 `<table>` 或 `role="table"`。
5. loading、error、refresh 状态应使用 `aria-live="polite"`。
6. 触控目标不小于 36px。
7. 长文件名、课程名、错误信息必须可换行或 `text-overflow: ellipsis` 加 `title` 属性，不能撑破布局。
8. 暗色主题下所有文字、状态 badge、按钮、表格边框仍满足 4.3.6 节对比度要求。

---

## 10. 与后端和学生端的边界

### 10.1 后端边界

1. 管理端正式接口统一走 `VITE_API_BASE_URL`，默认 `http://127.0.0.1:8080/api/v1`。
2. GraphRAG Python `/v1` 不进入管理端正式请求层。
3. 健康页当前优先实接 `/system/health`。
4. 其他业务页面在 API 未补齐前继续使用示例数据，但必须通过 `DataSourceChip` 明确标记。

### 10.2 学生端参考边界

可复用：模块色 token 思路、Slate 中性色、毛玻璃轻层级、统一状态页、轻动效和 reduced-motion 约束、模块布局和可复用组件意识。

不可照搬：落地页深色大 Hero、大面积光晕和强渐变、学生端内容消费卡片瀑布、学习社区和个人中心的相关动效。

---

## 11. 分阶段实施方案

### 11.1 第一阶段 1a：CSS Token 与主题系统（不动布局）

目标：建立稳定的设计基础，对页面布局和业务行为零破坏。

范围：

1. 新建 `src/styles/tokens.css`，定义颜色、字体（DM Sans / DM Mono）、字号、间距、圆角、状态色、主题切换变量。
2. 安装 `@fontsource/dm-sans` 和 `@fontsource/dm-mono`，在 `main.js` 引入三个字重。
3. 新建 `src/stores/theme.js`，实现 `light / dark / auto` 和 `localStorage` 持久化。
4. `src/style.css` 改为只导入分层样式。

验证：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

检查字体是否正确加载（DM Sans 在表格和标题处应与系统字体有明显差异）。

### 11.2 第一阶段 1b：壳层重构

目标：在 token 稳定后重构布局壳，顶栏和侧栏组件化。

范围：

1. 重构 `ConsoleLayout.vue`，拆出 `AppTopbar.vue` 和 `SideNavigation.vue`。
2. 顶栏增加 `ThemeControl.vue`（`light / dark / auto` + 固定色板）、身份 chip 和数据范围提示。
3. 优化登录页、状态页、按钮、标签、面板、表格基础样式。
4. 新建 `StatusBadge.vue`、`DataSourceChip.vue`、`SkeletonBlock.vue`。

验证：

```bash
pnpm test && pnpm build
```

手工检查：`/login`、`/app/dashboard`、管理员/教师身份切换、`light/dark/auto` 切换、主题色色板切换。

### 11.3 第二阶段：工作台和健康页

目标：优先完成最有管理端价值的两个页面。

范围：

1. `DashboardView.vue` 改为指标卡 + `ProductionTrack` + 近期任务 + 深色异常摘要。
2. `HealthView.vue` 改为 `HealthMatrix.vue`，原始 JSON 进入折叠区域。
3. 健康页增加 `DiagnosticLogPanel.vue`（DM Mono，局部深色）。
4. 明确所有页面的四态（idle / loading / success / error）。

验证：

```bash
pnpm test && pnpm build && pnpm dev
```

手工检查：健康接口失败时的错误显示、`ProductionTrack` 响应式降级、骨架屏动效。

### 11.4 第三阶段：通用业务页模板

目标：让业务页面虽仍有示例数据，但看起来像可联调的真实页面。

范围：

1. `ModulePage.vue` 拆出 table、overview、workflow 子组件。
2. `module-content.js` 增加数据来源、状态字段、主次操作、权限要求字段。
3. 表格增加状态分段筛选、`DataTableShell.vue` 和空状态。
4. 详情页增加摘要条、字段网格、关联入口和时间线。
5. 构建向导采用左侧纵向步骤 + 中央动作区 + 右侧任务状态/日志常驻布局。
6. 向导增加前置条件、blocked 原因、日志入口和重跑确认文案。

### 11.5 第四阶段：真实 API 渐进接入

优先顺序：

1. 系统健康（已有入口）
2. 课程列表和课程详情
3. 资料详情和解析结果
4. 知识库列表、构建向导和索引运行
5. QA 会话与检索日志
6. 用户、角色、课程成员

每接入一个页面需更新：请求方法、四态处理、`DataSourceChip` 改为 `live`、现有 node test 或新增测试、README 中的状态说明。

---

## 12. 验收标准

### 12.1 视觉验收

1. 拉丁字母、数字、ID、日志优先使用 DM Sans / DM Mono；中文按系统中文字体回退，中英文混排稳定，行高和数字对齐清晰。
2. 管理端整体不再像 Vite 起步页或零散骨架，具有明确的运维台气质。
3. 页面气质与学生端同源，但明显更克制、更高密度。
4. 一级导航、按钮、StatusBadge、表格、面板风格统一。
5. 首屏可快速判断知识库生产链路状态。
6. 健康页能区分 `reachable` 和 `ready`。
7. 所有页面的数据来源通过 `DataSourceChip` 清晰标记。
8. 未开放页面不出现空白或误导性假功能。
9. 亮色和暗色主题保持同一信息结构，暗色模式不是全站监控大屏。
10. 用户能手动选择 `light / dark / auto` 和固定主题色，刷新后仍生效。
11. 局部深色诊断面板只出现在健康、索引运行、检索日志、失败任务等高注意力场景。
12. 骨架屏扫光动效在 `prefers-reduced-motion` 下静默降级为纯色块。

### 12.2 工程验收

1. 不破坏现有路由路径和权限元信息。
2. `authStore` 的开发态身份切换仍可用。
3. `VITE_API_BASE_URL` 仍默认指向 Java `/api/v1`。
4. 主题偏好保存到 `localStorage`，刷新后仍生效。
5. `auto` 模式能读取并响应系统 `prefers-color-scheme` 变化。
6. 现有测试继续通过，构建通过。
7. 不修改 `.env`、`node_modules/`、`dist/`、生成缓存或真实凭据。

### 12.3 可访问性验收

1. 键盘可访问主要导航、按钮、筛选、角色切换。
2. 焦点态清晰，`:focus-visible` 有对比度足够的焦点环。
3. 状态既有颜色也有文字，不依赖纯色区分。
4. 文本不重叠、不溢出按钮和标签。
5. 窄屏下不出现不可恢复的横向撑破，表格可横向滚动。
6. 暗色主题下文字、badge、按钮、边框满足 WCAG AA 对比度。
7. 固定色板不提供任意取色；实现时用测试覆盖 `accent / accent-strong / accent-contrast` 派生 token 的对比度，`ThemeControl.vue` 只允许保存枚举中的主题模式和主题色。

---

## 13. 遗留问题与下一步建议

### 13.1 已落地状态

1. 视觉基础已落地：`tokens.css` / `base.css` / `components.css` 分层样式、DM Sans / DM Mono 本地字体、`light / dark / auto` 主题模式和固定主题色色板已经接入。
2. 控制台壳层已落地：`ConsoleLayout` 拆分为 `AppTopbar`、`SideNavigation` 和 `ThemeControl`，顶栏显示 Java `/api/v1` 运行基线，侧栏按权限过滤并保留未开放入口。
3. 公共状态组件已落地：`StatusBadge`、`DataSourceChip`、`SkeletonBlock`、`DiagnosticLogPanel`、`MetricTile`、`DataTableShell`、`WorkflowStepper` 已用于工作台、健康页和通用业务页。
4. 工作台已落地：`DashboardView` 使用 5 个指标块、`ProductionTrack`、近期任务和局部深色异常摘要；生产链路混合态规则已有 `node --test` 覆盖。
5. 健康页已落地：`HealthView` 继续请求 Java `/api/v1/system/health`，通过 `HealthMatrix` 区分 `reachable` 与 `ready`，原始 JSON 收进折叠区，诊断摘要使用局部深色面板。
6. 通用业务页模板已落地：`ModulePage` 按 table / overview / workflow 三类变体渲染；业务页配置显式声明 `dataSource: 'mock'`、主次操作和权限字段；表格筛选按显式列匹配。
7. 登录页和状态页已落地：登录页保留开发态身份切换并明确标注正式登录接口待接入；403 / 404 / 500 / coming-soon 状态页提供可读原因和恢复入口。
8. 自动化验证已覆盖主题、导航、数据来源、生产链路、健康归一化、业务页模型、表格筛选和 workflow 阻塞动作；当前验收命令是 `pnpm test` 与 `pnpm build`。

### 13.2 仍需后续推进

1. `admin-app` 仍缺真实登录接口；开发态身份切换应继续保留明显的"开发态"视觉标识，直到后端认证契约确定。
2. 除系统健康外，多数业务页面仍使用本地示例数据；后续每接入一个 Java `/api/v1` 页面，都要把对应 `DataSourceChip` 从 `mock` 调整为 `live` 并补四态处理。
3. 403 页面当前只能显示兜底缺失权限；如果要展示精确权限点，需要路由守卫跳转时附带 `required` 查询参数。
4. `DiagnosticLogPanel` 目前用于示例诊断摘要；真实索引运行、检索日志、失败任务摘要还需要后端日志 API 或任务状态接口。
5. `DataTableShell` 首版不实现虚拟滚动；若出现大数据量表格，再接入 `vue-virtual-scroller` 或后端分页。
6. 对比度与无障碍仍建议后续引入 `pa11y` 或等效检查，避免主题色和密集表格迭代时退化。
