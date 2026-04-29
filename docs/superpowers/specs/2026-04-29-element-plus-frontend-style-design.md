# CKQA 管理端 Element Plus + Pinia + Sass 样式重构设计稿

- 日期：2026-04-29
- 范围：`frontend/apps/admin-app/`
- 参考：`frontend/apps/student-app/src/styles/`、`docs/superpowers/specs/2026-04-21-student-app-ui-redesign-design.md`、`docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md`
- 主题：只重构管理端 UI 样式体系，引入 Element Plus、Pinia、Sass，并把原有 CSS 迁移为 SCSS 分层
- 约束：不改变现有页面逻辑、路由、接口、loader、业务状态流；`student-app` UI 不在本次范围内改动

## 1. 背景

`frontend/apps/admin-app/` 当前已经是管理员/教师共用控制台前端，具备主题系统、路由守卫、工作台、系统健康、课程、资料、知识库、构建向导和 QA 冒烟验证等页面。它的真实业务边界是 Java `/api/v1`，因此本次 UI 美化必须保持业务流程稳定。

当前 admin-app 的前端事实：

1. 技术栈是 Vue 3 + Vite + Vue Router + Axios + lucide-vue-next。
2. 目前没有 Element Plus、Pinia、Sass。
3. 全局样式入口是 `src/style.css`，内部导入 `styles/tokens.css`、`styles/base.css`、`styles/components.css`。
4. 主题状态通过 `src/stores/theme.js` 的 reactive 对象维护，不是 Pinia store。
5. 学生端已经有更完整的 Sass 分层：`styles/index.scss`、`tokens/`、`mixins/`、`fonts.scss`，可作为管理端样式工程化参考。

本次修订后的方向是：学生端 UI 不做变化；管理端引入 Element Plus、Pinia、Sass，并参照学生端的 token / mixin / index.scss 组织方式重构样式层。

## 2. 目标与非目标

### 2.1 目标

1. **只改管理端**：所有实施范围限定在 `frontend/apps/admin-app/`。
2. **引入 Element Plus**：管理端后续表单、按钮、弹窗、标签、分页、提示等基础交互统一使用 Element Plus 主题能力。
3. **引入 Pinia**：把管理端已有 `auth`、`theme` 轻量 store 迁移或包裹成 Pinia store，主题偏好继续持久化。
4. **引入 Sass**：把 `style.css`、`tokens.css`、`base.css`、`components.css` 迁移为 SCSS 分层。
5. **复用学生端规范**：借鉴学生端 `tokens/`、`mixins/`、`index.scss` 的文件结构和变量分层，但不复用学生端高光、落地页、营销式视觉强度。
6. **保持业务稳定**：现有页面逻辑、API 请求、路由、loader、测试入口不因样式重构改变。
7. **主题能力保留并增强**：继续支持 `light / dark / auto` 与固定主题色色板，并把它们映射给 Element Plus。

### 2.2 非目标

1. 不修改 `frontend/apps/student-app/` 的 UI、样式、组件或主题。
2. 不重做管理端信息架构。
3. 不改变 Java `/api/v1` 作为浏览器正式业务边界的约定。
4. 不把管理端改成营销页或学生端风格页面。
5. 不引入任意取色器、在线主题编辑器或用户自定义 CSS。
6. 不一次性替换所有自研组件；优先建立 Sass 和 Element Plus 主题底座，再按页面需要渐进替换。

## 3. 推荐方案

### 3.1 推荐方案：管理端独立升级，学生端只作规范参考

做法：

1. 在 admin-app 中安装 `element-plus`、`@element-plus/icons-vue`、`pinia`、`sass`。
2. 把 `src/style.css` 迁移为 `src/styles/index.scss`。
3. 把 `tokens.css`、`base.css`、`components.css` 拆成 `tokens/`、`mixins/`、`base.scss`、`components.scss`、`element-plus.scss`。
4. 新增 Pinia 主题 store，继续负责 `data-theme`、`data-accent`、`localStorage` 和系统暗色偏好监听。
5. 在 `main.js` 中注册 Pinia，并按顺序导入 Element Plus 与管理端覆盖样式。
6. 页面组件暂时保持现有业务逻辑，后续只在表单、弹窗、输入、选择器等位置渐进使用 Element Plus。

选择理由：

1. 符合用户“学生端 UI 不用变，只变更 admin-app”的要求。
2. 能复用学生端成熟的 Sass 组织方式，又不会把学生端视觉直接搬到管理端。
3. 能在不重写页面逻辑的前提下，为后续 Element Plus 组件替换建立统一主题底座。

### 3.2 备选方案：只迁移 SCSS，不引入 Element Plus

这个方案只把 CSS 改成 SCSS，不安装 Element Plus。

优点是改动更小；缺点是后续表单、弹窗、提示、下拉等组件仍需自研，无法满足“使用 Element Plus 美化前端页面”的方向。因此不推荐。

### 3.3 备选方案：一次性全面替换为 Element Plus 页面

这个方案会把所有按钮、表格、表单、弹窗、状态标签一次性替换为 Element Plus。

优点是组件库统一最彻底；缺点是改动面太大，容易碰到现有 loader、测试和真实联调页。本设计不建议首轮采用。

## 4. 视觉方向

管理端采用“克制的课程知识库运维台”方向。

气质关键词：

1. 高密度但不拥挤。
2. 状态清晰，可快速扫读。
3. 色彩受控，模块色只做强调。
4. 暗色模式能长时间使用，但不做大屏炫技风。
5. 与学生端同源，但更安静、更工具化。

从学生端借鉴的规范：

1. `styles/index.scss` 作为唯一全局样式入口。
2. `tokens/` 存放颜色、字体、圆角、阴影、间距、动效、断点。
3. `mixins/` 存放毛玻璃、主题色、响应式和组件状态复用逻辑。
4. 使用 Sass `@use` / `@forward` 管理变量。
5. 运行时主题仍通过 CSS 自定义属性落到 `:root` 和 `html[data-theme]`。

管理端不借鉴的学生端内容：

1. 不做落地页式大 hero。
2. 不做强荧光、粒子、磁吸按钮和大面积渐变。
3. 不把模块色铺满整屏。
4. 不把常规页面区块包成多层玻璃卡片。

## 5. 依赖设计

admin-app 的依赖新增：

```bash
cd frontend/apps/admin-app
pnpm add element-plus @element-plus/icons-vue pinia
pnpm add -D sass
```

`package.json` 预期变化：

```json
{
  "dependencies": {
    "@element-plus/icons-vue": "^2.x",
    "element-plus": "^2.x",
    "pinia": "^3.x"
  },
  "devDependencies": {
    "sass": "^1.x"
  }
}
```

说明：

1. Element Plus 用于基础 UI 组件与主题变量。
2. Pinia 用于规范化 `auth`、`theme` 这类前端状态。
3. Sass 用于样式 token、mixin 和组件分层。
4. `lucide-vue-next` 可以保留；管理端已有图标不强制迁移到 Element Plus Icons。

## 6. 样式目录设计

迁移前：

```text
frontend/apps/admin-app/src/
  style.css
  styles/
    tokens.css
    base.css
    components.css
```

迁移后：

```text
frontend/apps/admin-app/src/
  styles/
    index.scss
    fonts.scss
    base.scss
    components.scss
    element-plus.scss
    tokens/
      _colors.scss
      _typography.scss
      _radius.scss
      _shadow.scss
      _space.scss
      _motion.scss
      _breakpoints.scss
      _z-index.scss
    mixins/
      _focus.scss
      _surface.scss
      _status.scss
      _responsive.scss
```

职责说明：

| 文件 | 作用 |
| --- | --- |
| `index.scss` | 样式总入口，聚合 fonts、tokens、base、Element Plus 覆盖和组件样式 |
| `fonts.scss` | 引入 DM Sans、DM Mono 字体 |
| `base.scss` | reset、body、滚动条、focus-visible、基础排版 |
| `components.scss` | 现有自研类名的过渡期样式，例如 `.primary-button`、`.module-hero` |
| `element-plus.scss` | Element Plus CSS 变量映射和必要组件覆盖 |
| `tokens/_colors.scss` | 亮暗主题、主题色、语义色 |
| `tokens/_typography.scss` | 字体族、字号、行高 |
| `tokens/_radius.scss` | 圆角 |
| `tokens/_shadow.scss` | 阴影 |
| `tokens/_space.scss` | 间距 |
| `tokens/_motion.scss` | 动效时长和曲线 |
| `tokens/_breakpoints.scss` | 响应式断点 |
| `tokens/_z-index.scss` | 顶栏、侧栏、弹窗、消息层级 |
| `mixins/_focus.scss` | focus ring |
| `mixins/_surface.scss` | 面板、浮层、弱背景 |
| `mixins/_status.scss` | 状态色和状态徽标 |
| `mixins/_responsive.scss` | 断点 mixin |

## 7. 样式导入顺序

`src/main.js` 需要从 CSS 入口改为 SCSS 入口，并注册 Pinia 与 Element Plus。

推荐顺序：

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/index.scss'

import App from './App.vue'
import router from './router/index.js'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
```

说明：

1. `element-plus/dist/index.css` 先导入。
2. `styles/index.scss` 后导入，用于覆盖 Element Plus 变量和组件细节。
3. Pinia 在应用启动时注册。
4. 主题 store 的初始化仍应在应用挂载前执行，避免首屏闪烁。

## 8. Pinia 状态设计

### 8.1 主题 Store

路径建议：

```text
frontend/apps/admin-app/src/stores/theme.js
```

职责：

1. 保存 `mode: 'light' | 'dark' | 'auto'`。
2. 保存 `accent: 'blue' | 'indigo' | 'teal' | 'violet' | 'amber'`。
3. 计算 `resolvedTheme`。
4. 写入 `document.documentElement.dataset.theme`。
5. 写入 `document.documentElement.dataset.accent`。
6. 监听 `prefers-color-scheme`。
7. 使用 `localStorage['ckqa-admin-theme']` 持久化。

状态模型：

```javascript
export const THEME_MODES = ['light', 'dark', 'auto']
export const THEME_ACCENTS = ['blue', 'indigo', 'teal', 'violet', 'amber']
```

迁移原则：

1. 保留现有 `ThemeControl.vue` 的交互能力。
2. 模板中从 `themeStore.state.mode` 逐步迁移为 Pinia store 字段。
3. 保持 `aria-pressed`、`title`、`aria-label` 等可访问性属性。

### 8.2 认证 Store

路径建议：

```text
frontend/apps/admin-app/src/stores/auth.js
```

职责保持不变：

1. 开发态身份切换。
2. 权限点判断。
3. 用户角色、数据范围、登录状态。

迁移原则：

1. 不改变路由守卫行为。
2. 不改变登录页开发态身份切换逻辑。
3. 优先做 API 兼容封装，避免一次性改动所有调用处。

## 9. 管理端视觉 Token

### 9.1 基础色

亮色主题：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#f8fafc` | 页面背景 |
| `--ckqa-bg-elevated` | `#eef2ff` | 顶栏、侧栏弱强调 |
| `--ckqa-surface` | `#ffffff` | 面板、表格、弹窗 |
| `--ckqa-surface-muted` | `#f1f5f9` | 筛选条、空态、表头 |
| `--ckqa-surface-strong` | `#e7ebf1` | hover、强分隔 |
| `--ckqa-border` | `#e2e8f0` | 默认边框 |
| `--ckqa-border-strong` | `#cbd5e1` | 强边框 |
| `--ckqa-text` | `#0f172a` | 标题、正文 |
| `--ckqa-text-muted` | `#64748b` | 描述、辅助字段 |
| `--ckqa-text-weak` | `#94a3b8` | 占位、时间 |

暗色主题：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#020617` | 页面背景 |
| `--ckqa-bg-elevated` | `#0f172a` | 顶栏、侧栏 |
| `--ckqa-surface` | `#0f172a` | 面板、表格、弹窗 |
| `--ckqa-surface-muted` | `#1e293b` | 筛选条、空态、表头 |
| `--ckqa-surface-strong` | `#243449` | hover、强分隔 |
| `--ckqa-border` | `#334155` | 默认边框 |
| `--ckqa-border-strong` | `#475569` | 强边框 |
| `--ckqa-text` | `#f8fafc` | 标题、正文 |
| `--ckqa-text-muted` | `#cbd5e1` | 描述、辅助字段 |
| `--ckqa-text-weak` | `#94a3b8` | 占位、时间 |

### 9.2 主题色

| 主题 | `--ckqa-accent` | `--ckqa-accent-strong` | 使用建议 |
| --- | --- | --- | --- |
| indigo | `#6366f1` | `#4f46e5` | 默认主题，延续当前 admin-app |
| blue | `#2563eb` | `#1d4ed8` | 课程、资料、通用业务 |
| teal | `#0d9488` | `#0f766e` | 知识库、图谱、ready |
| violet | `#9333ea` | `#7e22ce` | QA、AI、检索 |
| amber | `#d97706` | `#b45309` | 警告、实验性主题 |

语义色：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-success` | `#10b981` | 成功、ready、done |
| `--ckqa-running` | `#3b82f6` | running、processing |
| `--ckqa-warning` | `#f59e0b` | 警告、部分可用 |
| `--ckqa-danger` | `#ef4444` | 错误、failed |
| `--ckqa-blocked` | `#64748b` | 未开放、阻塞、禁用 |

### 9.3 字体

管理端继续采用：

1. 正文：DM Sans + 系统中文字体。
2. 等宽：DM Mono。

字体规则：

1. 页面标题 24px 到 28px。
2. 面板标题 16px 到 18px。
3. 表格、表单、按钮主体 13px 到 14px。
4. 标签和辅助说明 12px 到 13px。
5. 任务 ID、日志、状态码使用等宽字体。
6. 字间距默认 `0`。

### 9.4 圆角、阴影、间距

圆角：

| Token | 值 | 用途 |
| --- | --- | --- |
| `$radius-sm` | `6px` | 标签、小按钮 |
| `$radius-md` | `8px` | 按钮、输入框、面板 |
| `$radius-lg` | `10px` | 大按钮、局部浮层 |
| `$radius-xl` | `12px` | 模态框、少量重点面板 |
| `$radius-full` | `999px` | 头像、色板、胶囊 |

阴影：

| Token | 值 | 用途 |
| --- | --- | --- |
| `$shadow-xs` | `0 1px 2px rgba(15, 23, 42, 0.05)` | 轻分层 |
| `$shadow-sm` | `0 2px 8px rgba(15, 23, 42, 0.06)` | 普通面板 |
| `$shadow-md` | `0 8px 24px rgba(15, 23, 42, 0.08)` | hover |
| `$shadow-lg` | `0 16px 48px rgba(15, 23, 42, 0.12)` | 弹窗、抽屉 |

间距：

1. 基准 4px。
2. 常用间距：4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48。
3. 表格和表单区域优先使用 12px、16px、20px，避免管理端过松散。

## 10. Element Plus 主题映射

`styles/element-plus.scss` 负责将 CKQA token 映射给 Element Plus：

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
  --el-border-color-light: color-mix(in srgb, var(--ckqa-border) 70%, transparent);
  --el-border-radius-base: var(--ckqa-radius);
  --el-font-family: var(--ckqa-font-sans);
}
```

覆盖原则：

1. 优先覆盖 CSS 变量，其次覆盖组件类。
2. 尽量不使用 `!important`。
3. 对 scoped 组件中无法覆盖的 Element Plus 内部结构，用 `:deep()` 精准处理。
4. 先覆盖常用组件：Button、Input、Select、Dialog、Drawer、Message、Tag、Table、Tabs、Pagination、Switch、Dropdown。

## 11. 组件规范

### 11.1 Button

适用：`el-button` 和过渡期 `.primary-button`、`.secondary-button`。

规则：

1. 普通按钮高度 36px 到 38px。
2. 大按钮高度 42px 到 44px。
3. 圆角 8px。
4. 主按钮使用 `--ckqa-accent-strong`。
5. 操作列尽量使用文本按钮或图标按钮，避免一行多个实心主按钮。
6. 禁用态只降低透明度，不保留 hover 抬升。

### 11.2 Form

适用：`el-input`、`el-select`、`el-radio-group`、`el-checkbox`、`el-switch`。

规则：

1. 输入框、选择器高度统一。
2. focus 状态使用主题色外环。
3. label 使用 13px 次级文字。
4. 错误提示使用 `--ckqa-danger`，并保留足够行高。
5. 表单中的说明文字不使用过浅灰，暗色模式下必须可读。

### 11.3 Table

适用：`el-table` 和过渡期 `DataTableShell.vue`。

规则：

1. 表头使用 `--ckqa-surface-muted`。
2. 行 hover 使用 `--ckqa-surface-muted` 或轻微主题色混合。
3. 数字、ID、任务号使用等宽字体。
4. 操作列固定最小宽度，避免按钮换行挤压数据列。
5. 空态使用统一 `el-empty` 或现有空态容器。

### 11.4 Dialog / Drawer

适用：`el-dialog`、`el-drawer`。

规则：

1. 标题区、内容区、操作区分层明确。
2. 主操作按钮最多一个。
3. 宽度按内容限制，避免宽屏上铺满。
4. 暗色模式下 overlay、背景、边框、文本全部走 token。

### 11.5 Tag / Badge / 状态

适用：`el-tag`、现有 `StatusBadge.vue`。

规则：

1. `success / warning / danger / info` 映射真实业务状态。
2. `ready`、`done` 用 success。
3. `running`、`processing` 用 running/info。
4. `failed` 用 danger。
5. `blocked`、`unavailable`、`未开放` 用 neutral。
6. 标签允许换行，不能挤压主内容。

## 12. 页面级落地边界

首轮只做样式底座和低风险页面微调：

1. `AppTopbar.vue`：适配 Element Plus/SCSS token，不改导航逻辑。
2. `SideNavigation.vue`：只调整视觉，不改导航模型。
3. `ThemeControl.vue`：接入 Pinia theme store，保留原交互。
4. `DataTableShell.vue`：对齐表格样式，后续可渐进改为 `el-table`。
5. `ModulePage.vue`：只调整 class 和视觉层，不改 loader 和操作逻辑。
6. `LoginView.vue`：保留开发态身份切换，只调整表单外观。
7. `HealthView.vue` / `HealthMatrix.vue`：保留数据结构，只强化状态矩阵视觉。

暂不做：

1. 不把所有页面一次性替换为 Element Plus 组件。
2. 不重写创建表单逻辑。
3. 不改 Playwright mock 和 API fault injection。
4. 不移动路由与权限配置。

## 13. 验收方式

### 13.1 命令验收

```bash
cd frontend/apps/admin-app
pnpm install
pnpm test
pnpm build
pnpm test:e2e
```

说明：

1. `pnpm test` 验证现有 Node 测试。
2. `pnpm build` 验证 Sass、Element Plus 和依赖打包。
3. `pnpm test:e2e` 验证核心本地操作错误面板不被样式重构破坏。

### 13.2 浏览器验收

```bash
cd frontend/apps/admin-app
pnpm dev:local
```

重点页面：

```text
http://127.0.0.1:5173/login
http://127.0.0.1:5173/app/dashboard
http://127.0.0.1:5173/app/health
http://127.0.0.1:5173/app/courses
http://127.0.0.1:5173/app/knowledge-bases
```

视觉检查：

1. 亮色、暗色、跟随系统均可用。
2. 主题色切换后，按钮、输入框 focus、标签、侧栏激活态同步变化。
3. 弹窗、下拉、消息提示暗色模式不出现白底刺眼区域。
4. 表格、表单、按钮不出现文字溢出。
5. 页面没有明显重叠、遮挡和布局跳动。
6. 键盘 focus 状态清晰。
7. `prefers-reduced-motion` 下动效被压低。

## 14. 风险与处理

| 风险 | 表现 | 处理 |
| --- | --- | --- |
| CSS 迁移为 SCSS 后导入顺序改变 | 原样式丢失或覆盖异常 | 先保留 `components.scss` 兼容旧类名，再逐步拆分 |
| Element Plus 样式覆盖不足 | 按钮、弹窗、下拉仍是默认视觉 | `element-plus.scss` 在 Element Plus CSS 后导入 |
| Pinia 迁移影响路由守卫 | 登录态或权限判断异常 | 保留兼容 API，先迁移内部实现，再逐步更新调用 |
| 暗色浮层遗漏 | Dialog/Dropdown/Message 白底 | 覆盖 `--el-bg-color-overlay` 和浮层类 |
| 一次性组件替换过多 | 测试和联调风险上升 | 首轮只做主题底座和低风险组件样式 |

## 15. 下一步建议

1. 先按本设计写实施计划，拆成依赖引入、Sass 迁移、Pinia store、Element Plus 主题覆盖、页面微调五步。
2. 实施时先保证 `pnpm build` 通过，再做浏览器截图检查。
3. 首轮结束后再评估是否把 `DataTableShell`、创建表单、登录页表单进一步迁移为 Element Plus 组件。
