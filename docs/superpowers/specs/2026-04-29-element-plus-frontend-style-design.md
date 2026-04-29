# CKQA 前端 Element Plus 样式美化设计稿

- 日期：2026-04-29
- 范围：`frontend/apps/student-app/`、`frontend/apps/admin-app/`
- 主题：基于 Element Plus 的前端样式统一、美化、亮暗模式与主题色扩展
- 约束：只更新组件样式和主题层，不改变现有页面逻辑、路由、接口、store、loader 和业务状态流
- 优先级：先落地 `student-app`，再评估 `admin-app` 是否引入 Element Plus

## 1. 背景

当前仓库里有两个前端应用：

1. `frontend/apps/student-app/`
   - 已经使用 Vue 3、Vite、Element Plus、Pinia、Vue Router、Sass。
   - 页面包含落地页、首页、课程、问答、知识图谱和用户中心等原型。
   - 已有 SCSS token、模块色、毛玻璃和 Element Plus 组件使用基础。
   - 适合优先做 Element Plus 主题变量覆盖和组件样式统一。

2. `frontend/apps/admin-app/`
   - 当前是 Vue 3、Vite、Vue Router、Axios、lucide-vue-next。
   - 已有自研主题系统：`light / dark / auto`，以及固定主题色色板。
   - 已接 Java `/api/v1` 的核心运维页面，不应为视觉改造破坏业务联调边界。
   - 当前没有 Element Plus 依赖，因此本设计把它列为第二阶段：先复用同一套视觉 token，后续如需要再引入 Element Plus。

本设计稿承接现有设计文档：

- `docs/superpowers/specs/2026-04-21-student-app-ui-redesign-design.md`
- `docs/superpowers/archive/specs/2026-04-26-admin-app-ui-redesign-design.md`

本次不是重做页面信息架构，而是在已有页面结构上建立一套可落地的 Element Plus 主题与样式规范。

## 2. 目标与非目标

### 2.1 目标

1. **样式统一**：让按钮、输入框、选择器、标签、表格、弹窗、消息提示等 Element Plus 组件共享 CKQA 视觉 token。
2. **主题可切换**：提供亮色、暗色、跟随系统三种显示模式。
3. **主题色可配置**：提供固定色板，支持蓝、靛蓝、青绿、紫、琥珀等主题色，不开放任意取色器。
4. **模块有差异但不割裂**：学生端继续保留课程、问答、知识图谱等模块色；全局组件仍保持一致的圆角、阴影、字体和间距。
5. **管理端保持克制**：管理端优先保证信息密度和可扫读，不引入营销式 hero、强渐变或过重动效。
6. **可渐进落地**：第一阶段只改主题文件和样式覆盖；第二阶段再考虑组件替换或依赖引入。

### 2.2 非目标

1. 不改业务逻辑。
2. 不改路由结构。
3. 不改 API 调用边界。
4. 不迁移 store。
5. 不把 admin-app 直接改造成 Element Plus 全量组件库页面。
6. 不在本次样式工作中实现正式登录、上传、图谱真实渲染等业务功能。
7. 不引入任意取色器、用户自定义 CSS、在线主题编辑器等高复杂度能力。

## 3. 推荐方案

### 3.1 方案 A：student-app 优先深度换肤，admin-app token 对齐

这是推荐方案。

做法：

1. 在 `student-app` 中新增 Element Plus 主题覆盖文件，直接覆盖 `--el-*` CSS 变量和常用组件类。
2. 把现有 SCSS token 映射为运行时 CSS 变量，例如 `--ckqa-bg`、`--ckqa-surface`、`--ckqa-text`、`--ckqa-accent`。
3. 增加轻量主题 store 或 composable，负责 `data-theme`、`data-accent` 和 `localStorage`。
4. `admin-app` 暂不引入 Element Plus，只把已有 `tokens.css` 与该视觉规范对齐。

优点：

1. 风险低，符合“只改样式，不改逻辑”的要求。
2. `student-app` 已有 Element Plus，落地成本最低。
3. `admin-app` 真实联调页面较多，避免为了视觉重构引入不必要的不稳定因素。

取舍：

1. 两个前端不会在第一阶段完全使用同一套组件库。
2. 管理端若后续确实要全面 Element Plus 化，需要单独立计划。

### 3.2 方案 B：两个应用同时引入 Element Plus 主题系统

做法：

1. `student-app` 深度换肤。
2. `admin-app` 安装 Element Plus，并逐步把按钮、表格、表单、弹窗替换为 Element Plus 组件。

优点：

1. 长期组件体验更统一。
2. 后续表单、弹窗、表格可以少写自定义基础组件。

风险：

1. 改动面大，会触碰管理端真实业务页。
2. 容易从“样式优化”变成“组件迁移”。
3. 会增加测试和回归成本。

本设计不建议把方案 B 作为本次首选。

### 3.3 方案 C：只调整现有 CSS，不碰 Element Plus 变量

做法：

1. 只改页面级 CSS 和 SCSS。
2. 不建立 Element Plus 主题变量映射。

优点：

1. 短期改动最小。

风险：

1. Element Plus 组件和自定义组件会继续割裂。
2. 后续每个页面都可能重复覆盖样式。
3. 亮暗模式和主题色难以稳定传导到 `el-button`、`el-input`、`el-dialog` 等组件。

本设计不建议采用。

## 4. 视觉方向

整体方向是“清爽可信的课程知识工具界面”。

学生端更轻盈，有模块色和学习氛围；管理端更克制，强调可扫描、可操作和状态清晰。两个应用共享基础 token，但视觉强度不同。

设计原则：

1. 页面背景使用浅色 Slate 或暗色深蓝黑，不使用纯白铺满全屏。
2. 卡片只用于独立内容单元，不做卡片套卡片。
3. 圆角默认控制在 8px，较大的展示卡片可到 12px。
4. 光效只用于 hover、focus、active 和关键状态提示。
5. 字体不随 viewport 缩放，避免移动端或宽屏下失控。
6. 按钮内文本必须在常见中文长度下不溢出。
7. 所有交互元素都保留清晰的 `focus-visible` 状态。

## 5. 设计 Token

### 5.1 基础色

亮色主题：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#f8fafc` | 页面背景 |
| `--ckqa-surface` | `#ffffff` | 卡片、表格、弹窗 |
| `--ckqa-surface-muted` | `#f1f5f9` | 筛选条、浅色块、空态背景 |
| `--ckqa-surface-strong` | `#e7ebf1` | 强分隔、悬浮背景 |
| `--ckqa-border` | `#e2e8f0` | 默认边框 |
| `--ckqa-border-strong` | `#cbd5e1` | 强边框 |
| `--ckqa-text` | `#0f172a` | 主文本 |
| `--ckqa-text-muted` | `#64748b` | 次级文本 |
| `--ckqa-text-weak` | `#94a3b8` | 占位、时间、辅助说明 |

暗色主题：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-bg` | `#0a0f1f` | 页面背景 |
| `--ckqa-surface` | `#121826` | 卡片、表格、弹窗 |
| `--ckqa-surface-muted` | `#1b2333` | 筛选条、浅色块、空态背景 |
| `--ckqa-surface-strong` | `#243044` | 强分隔、悬浮背景 |
| `--ckqa-border` | `#334155` | 默认边框 |
| `--ckqa-border-strong` | `#475569` | 强边框 |
| `--ckqa-text` | `#f8fafc` | 主文本 |
| `--ckqa-text-muted` | `#cbd5e1` | 次级文本 |
| `--ckqa-text-weak` | `#94a3b8` | 占位、时间、辅助说明 |

### 5.2 主题色

| 主题 | `--ckqa-accent` | `--ckqa-accent-strong` | 使用建议 |
| --- | --- | --- | --- |
| blue | `#2563eb` | `#1d4ed8` | 默认主色，稳定专业 |
| indigo | `#6366f1` | `#4f46e5` | 工作台、首页、品牌感 |
| teal | `#0d9488` | `#0f766e` | 知识图谱、连接关系 |
| violet | `#9333ea` | `#7e22ce` | AI 问答、智能感 |
| amber | `#d97706` | `#b45309` | 提醒、实验性主题 |

语义色：

| Token | 色值 | 用途 |
| --- | --- | --- |
| `--ckqa-success` | `#10b981` | 成功、已完成、ready |
| `--ckqa-running` | `#3b82f6` | 运行中、处理中 |
| `--ckqa-warning` | `#f59e0b` | 警告、需处理 |
| `--ckqa-danger` | `#ef4444` | 错误、失败 |
| `--ckqa-blocked` | `#64748b` | 阻塞、未开放、禁用 |

### 5.3 字体

学生端继续使用：

- 正文：`Manrope` + `Noto Sans SC`
- 展示标题：`Space Grotesk` + `Noto Sans SC`

管理端继续使用：

- 正文：`DM Sans` + 系统中文字体
- 等宽：`DM Mono`

全局规则：

1. 中文说明文本保持系统中文字体回退。
2. 数字、任务 ID、日志、状态码使用等宽字体。
3. 字间距默认 `0`，不使用负字距。
4. 按钮、表格、表单内字号保持 13px 到 14px，避免膨胀。

### 5.4 圆角与阴影

| Token | 值 | 用途 |
| --- | --- | --- |
| `--ckqa-radius-sm` | `6px` | tag、小按钮 |
| `--ckqa-radius` | `8px` | 按钮、输入框、普通面板 |
| `--ckqa-radius-lg` | `12px` | 学生端展示卡片 |
| `--ckqa-radius-pill` | `999px` | 胶囊导航、头像、色板按钮 |

阴影：

| Token | 值 | 用途 |
| --- | --- | --- |
| `--ckqa-shadow-soft` | `0 12px 32px rgb(15 23 42 / 8%)` | 默认卡片 |
| `--ckqa-shadow-hover` | `0 18px 42px rgb(15 23 42 / 12%)` | hover |
| `--ckqa-shadow-dialog` | `0 24px 70px rgb(15 23 42 / 22%)` | 弹窗、抽屉 |

## 6. Element Plus 主题映射

在 `student-app` 中新增：

```text
frontend/apps/student-app/src/styles/theme-vars.scss
frontend/apps/student-app/src/styles/element-plus-theme.scss
```

`theme-vars.scss` 负责 CKQA 运行时 CSS 变量：

```scss
:root {
  color-scheme: light;
  --ckqa-bg: #f8fafc;
  --ckqa-surface: #ffffff;
  --ckqa-surface-muted: #f1f5f9;
  --ckqa-border: #e2e8f0;
  --ckqa-text: #0f172a;
  --ckqa-text-muted: #64748b;
  --ckqa-accent: #2563eb;
  --ckqa-accent-strong: #1d4ed8;
  --ckqa-accent-contrast: #ffffff;
  --ckqa-radius: 8px;
  --ckqa-radius-sm: 6px;
  --ckqa-radius-pill: 999px;
}

html[data-theme='dark'] {
  color-scheme: dark;
  --ckqa-bg: #0a0f1f;
  --ckqa-surface: #121826;
  --ckqa-surface-muted: #1b2333;
  --ckqa-border: #334155;
  --ckqa-text: #f8fafc;
  --ckqa-text-muted: #cbd5e1;
}

html[data-accent='blue'] {
  --ckqa-accent: #2563eb;
  --ckqa-accent-strong: #1d4ed8;
}

html[data-accent='indigo'] {
  --ckqa-accent: #6366f1;
  --ckqa-accent-strong: #4f46e5;
}

html[data-accent='teal'] {
  --ckqa-accent: #0d9488;
  --ckqa-accent-strong: #0f766e;
}

html[data-accent='violet'] {
  --ckqa-accent: #9333ea;
  --ckqa-accent-strong: #7e22ce;
}

html[data-accent='amber'] {
  --ckqa-accent: #d97706;
  --ckqa-accent-strong: #b45309;
}
```

`element-plus-theme.scss` 负责映射 Element Plus：

```scss
:root {
  --el-color-primary: var(--ckqa-accent);
  --el-color-success: var(--ckqa-success, #10b981);
  --el-color-warning: var(--ckqa-warning, #f59e0b);
  --el-color-danger: var(--ckqa-danger, #ef4444);
  --el-color-info: var(--ckqa-running, #3b82f6);

  --el-bg-color: var(--ckqa-surface);
  --el-bg-color-page: var(--ckqa-bg);
  --el-bg-color-overlay: var(--ckqa-surface);
  --el-text-color-primary: var(--ckqa-text);
  --el-text-color-regular: var(--ckqa-text);
  --el-text-color-secondary: var(--ckqa-text-muted);
  --el-border-color: var(--ckqa-border);
  --el-border-color-light: color-mix(in srgb, var(--ckqa-border) 70%, transparent);
  --el-border-radius-base: var(--ckqa-radius);
  --el-font-family: var(--font-sans, var(--ckqa-font-sans, system-ui, sans-serif));
}
```

样式导入顺序：

```javascript
import './styles/theme-vars.scss'
import './styles/index.scss'
import 'element-plus/dist/index.css'
import './styles/element-plus-theme.scss'
```

说明：Element Plus 原始 CSS 之后再导入覆盖文件，确保自定义变量和类选择器生效。

## 7. 组件样式规范

### 7.1 Button

适用组件：`el-button`

规则：

1. 默认高度：小按钮 32px，普通按钮 38px，大按钮 44px。
2. 圆角：8px；圆形按钮保持圆形。
3. 主按钮使用 `--ckqa-accent-strong`，hover 微抬升。
4. 文本按钮不使用强背景，hover 只加浅色底。
5. 禁用态透明度降低，不出现 hover 抬升。

验收重点：

1. 中文长按钮文本不溢出。
2. 图标按钮有可访问名称或 tooltip。
3. 暗色模式下主按钮对比度达标。

### 7.2 Form

适用组件：`el-input`、`el-select`、`el-textarea`、`el-radio-group`、`el-checkbox`、`el-switch`

规则：

1. 输入框高度统一，边框使用 `--ckqa-border`。
2. focus 状态使用主题色边框和浅色外环。
3. 表单 label 使用 13px 次级文字。
4. 错误状态使用 `--ckqa-danger`，提示文案保留足够行高。
5. `el-switch` 用于亮暗模式切换；主题色选择使用色板按钮，不使用输入框。

### 7.3 Tag 与状态徽标

适用组件：`el-tag`

规则：

1. `success / warning / danger / info` 映射语义状态。
2. 课程、问答、知识图谱等业务标签可使用模块色浅底。
3. 标签圆角 6px，文本 12px 到 13px。
4. 同一行标签过多时允许换行，不挤压主体内容。

### 7.4 Table

适用组件：`el-table`

规则：

1. 表头使用弱背景，不使用大面积深色表头。
2. 行 hover 使用 `--ckqa-surface-muted`。
3. ID、任务编号、时间戳使用等宽字体。
4. 操作列按钮使用图标 + tooltip 或短文本，不堆叠过多主按钮。
5. 空态使用统一 `el-empty` 样式。

### 7.5 Dialog 与 Drawer

适用组件：`el-dialog`、`el-drawer`

规则：

1. 弹窗宽度按内容约束，不使用过宽大弹窗。
2. 标题区和内容区有清晰分隔。
3. 底部操作区右对齐，主操作按钮最多一个。
4. 暗色模式下 overlay、弹窗背景、边框和文字都走 token。

### 7.6 Message 与 MessageBox

适用组件：`ElMessage`、`ElMessageBox`

规则：

1. 成功、警告、错误、信息使用语义色。
2. 不使用过重阴影。
3. 错误消息需要明确动作含义，避免只显示“失败”。
4. 删除确认类弹窗保留 Element Plus 的确认流程，不改变逻辑。

## 8. 亮暗模式与主题色切换

### 8.1 状态模型

显示模式：

```javascript
const THEME_MODES = ['light', 'dark', 'auto']
```

主题色：

```javascript
const THEME_ACCENTS = ['blue', 'indigo', 'teal', 'violet', 'amber']
```

文档属性：

```text
html[data-theme='light']
html[data-theme='dark']
html[data-accent='blue']
```

持久化：

```text
localStorage['ckqa-student-theme']
```

### 8.2 UI 控件

推荐放置位置：

1. 学生端：`NavHeader.vue` 的头像菜单或 `UserSettings.vue`。
2. 管理端：沿用现有 `ThemeControl.vue`。

控件形态：

1. 亮色、暗色、跟随系统使用 segmented control 或 icon button。
2. 主题色使用圆形色板按钮。
3. 当前选中态用 `aria-pressed` 标识。
4. 色板按钮必须有 `aria-label`，例如“主题色 青绿”。

## 9. 文件落地计划

第一阶段：`student-app` 样式层。

| 文件 | 作用 |
| --- | --- |
| `src/styles/theme-vars.scss` | 新增 CKQA 运行时主题变量 |
| `src/styles/element-plus-theme.scss` | 新增 Element Plus 变量映射和组件覆盖 |
| `src/styles/index.scss` | 调整全局背景、文字、focus、滚动条等基础样式 |
| `src/main.js` | 调整样式导入顺序 |
| `src/composables/useThemePreference.js` 或 `src/stores/theme.js` | 新增轻量主题偏好状态 |
| `src/components/NavHeader.vue` 或 `src/views/user/UserSettings.vue` | 新增主题切换入口 |

第二阶段：`admin-app` token 对齐。

| 文件 | 作用 |
| --- | --- |
| `src/styles/tokens.css` | 对齐本设计的 token 命名和值 |
| `src/components/shell/ThemeControl.vue` | 复用现有亮暗模式和色板交互 |
| `src/styles/components.css` | 对按钮、输入、表格、面板做同源样式微调 |

第三阶段：是否引入 Element Plus 到 `admin-app`。

只有在确认需要统一组件库后再做，并单独编写实施计划。该阶段不属于本设计首轮落地范围。

## 10. 验收方式

### 10.1 构建与测试

学生端：

```bash
cd frontend/apps/student-app
pnpm build
node --test tests/*.test.js
```

管理端：

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

### 10.2 浏览器检查

学生端重点页面：

```text
http://127.0.0.1:5174/
http://127.0.0.1:5174/home
http://127.0.0.1:5174/course/list
http://127.0.0.1:5174/course/detail/1
http://127.0.0.1:5174/qa/ask
http://127.0.0.1:5174/qa/history
http://127.0.0.1:5174/knowledge/graph
http://127.0.0.1:5174/user/settings
```

管理端重点页面：

```text
http://127.0.0.1:5173/app/dashboard
http://127.0.0.1:5173/app/health
http://127.0.0.1:5173/app/courses
http://127.0.0.1:5173/app/knowledge-bases
```

### 10.3 视觉验收清单

1. 亮色、暗色、跟随系统均可切换。
2. 主题色切换后，Element Plus 主按钮、输入框 focus、标签、分页、tabs 激活态同步变化。
3. 暗色模式下弹窗、下拉菜单、消息提示不出现白底刺眼区域。
4. 按钮文字不溢出。
5. 输入框、选择器、表格、弹窗的圆角和边框一致。
6. 页面没有明显的文本重叠、遮挡或布局跳动。
7. 键盘 focus 状态清晰可见。
8. `prefers-reduced-motion` 下动效被压低。

## 11. 风险与处理

| 风险 | 表现 | 处理 |
| --- | --- | --- |
| Element Plus 原始 CSS 覆盖自定义样式 | 主题变量不生效 | 确保 `element-plus-theme.scss` 在 `element-plus/dist/index.css` 后导入 |
| 组件局部 scoped 样式权重过高 | 全局主题无法覆盖 | 对少量组件用 `:deep()` 精准覆盖，避免大范围 `!important` |
| 暗色模式遗漏浮层 | Dropdown/Dialog 仍为亮色 | 覆盖 `--el-bg-color-overlay` 与相关浮层类 |
| admin-app 引入 Element Plus 影响联调 | 页面结构和测试大面积变化 | 第一阶段不引入，仅 token 对齐 |
| 模块色过多导致视觉杂乱 | 页面像彩虹拼盘 | 模块色只用于激活态、标签、重点按钮和局部强调 |

## 12. 下一步建议

1. 先按本设计为 `student-app` 新增主题变量、Element Plus 覆盖和主题切换入口。
2. 完成后跑 `pnpm build`，并检查学生端重点页面。
3. 再对 `admin-app` 做 token 对齐，不急于引入 Element Plus。
4. 如果后续确认管理端也要使用 Element Plus，再单独写管理端组件迁移计划。
