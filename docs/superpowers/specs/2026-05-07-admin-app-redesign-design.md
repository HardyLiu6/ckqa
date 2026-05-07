# 管理员端前端视觉与交互重设计

- 日期：2026-05-07
- 范围：`frontend/apps/admin-app/`
- 目标：把现有管理员端从"功能可用、视觉中规中矩、ModulePage 巨石承载多种页面"的状态，整体升级为一套自洽的设计系统 + 流水线导向的信息架构 + 4 个重做的关键交互，让平台看起来像一个真实的"AI 课程知识库平台"，而不是"功能堆叠出来的运维台"。

## 1. 背景

### 1.1 现状摘要

`frontend/apps/admin-app/` 当前是 Vue 3 + Vite + Element Plus + Pinia + Vue Router，已经接入 Java `/api/v1` 的真实数据：

1. 路由层 `src/router/routes.js` 定义了约 20 个页面，按 `navGroup` 划分为 `dashboard / courses / knowledge / qa / users / system` 六组。
2. 视图层最重的文件是 `src/views/pages/ModulePage.vue`，约 3957 行，承担了课程列表/详情、课程成员、课程资料、资料详情（含 SSE 解析进度）、解析结果详情、知识库列表/详情、构建向导、索引详情、问答会话、用户/角色/权限等几乎所有列表 + 详情类页面的渲染逻辑。
3. 布局壳 `src/layouts/` 已有 `AuthLayout / ConsoleLayout / DetailLayout / WorkflowLayout` 四种，结构合理但视觉表达较朴素。
4. 设计 Token `src/styles/tokens/` 已经具备 colors / spacing / radius / typography / motion / shadow / breakpoints / z-index 的基础变量，亮色 + 暗色 + 多 accent 配色都有占位，但缺少完整的语义层（如状态色、状态色软底、文本层级）和与组件的系统映射。
5. 共享组件 `src/components/` 分为 `build-wizard / common / shell / system`，覆盖了导航、面包屑等基础壳层，但业务级共享组件（卡片、状态徽章、空态、活动时间线、流水线条等）尚未沉淀。
6. 文案上有少量内部术语（如"QA 冒烟"、"P95 延迟"）暴露在管理员/教师视图中，对非工程师有误导风险。

### 1.2 主要痛点

1. **页面承载在巨石组件里，难以分别打磨**：4 个最值得做出"产品质感"的交互（Dashboard、构建向导、资料详情、问答会话+检索诊断）目前都通过 `ModulePage.vue` 的 `module-content.js` + `module-loaders.js` + 资源 schema 拼装，难以投入精细的视觉与交互设计。
2. **导航分组按资源切，没体现 CKQA 主链路**：`课程→资料→PDF 解析→GraphRAG 输入→索引构建→激活→问答→检索日志` 是平台真正的生产流水线，但当前左侧栏看不到这条线，新人难以建立心智模型。
3. **视觉气质偏运维风，AI/智能感不强**：现状 indigo 主色 + slate 中性色虽然专业，但与"AI 课程知识库平台"的产品定位之间缺少情绪锚点。
4. **文案中夹杂内部术语**：`QA 冒烟` / `embedding` / `实体抽取` / `P95` 等字样直接出现在面向教师的工作台，会让用户困惑。
5. **暗色模式只搭了 token，没有系统验证**：`[data-theme='dark']` 已写但许多组件未照顾到，实际切换会出现脏面板。

### 1.3 技术约束

1. 必须保留 Vue 3 + Vite + Element Plus + Pinia + Vue Router 技术栈。
2. 不替换 Element Plus，在其之上叠加 CKQA 主题层 + 业务组件封装。
3. 必须保留对 Java `/api/v1` 的现有真实数据接入，不重写 API 层。
4. 必须保留现有路由路径（用户已有书签 / E2E 已覆盖），分组与导航结构可重组，但路由 path 不变。
5. 必须保留对 SSE 解析进度推送的现有实现（`efdeebd` 已落地），新设计在其之上做 UI 升级。
6. 桌面端为第一优先（`≥ 1280px` 基准），`≥ 1024px` 做基础退化，移动端不在本次范围。
7. 必须通过现有 Playwright E2E（用例随重构同步更新）。

## 2. 目标与非目标

### 2.1 设计目标

1. **建立完整的设计系统**：从颜色 / 字体 / 间距 / 圆角 / 阴影 / 动效到状态语义，全部沉淀为 Token，所有页面只通过 Token 而非裸色值描述视觉。
2. **流水线导向的信息架构**：左侧栏明确分为 `工作台 / 生产 / 运维 / 设置` 四段，工作台用一条 5 段流水线作为视觉锚点，让任何角色打开都能瞬间知道"现在在哪、下一步去哪"。
3. **4 个关键交互重做**：Dashboard、知识库构建向导、资料详情 + 解析进度、问答会话 + 检索诊断，每个都拆出独立页面组件并重新设计交互。
4. **拆解 ModulePage.vue 巨石**：按资源切出独立页面组件（CourseList / CourseDetail / MaterialList / MaterialDetail / KbList / KbDetail / BuildWizard / QaSessionList / QaSessionDetail / IndexRunDetail / Members / Roles / Permissions / Health），每个文件控制在 600 行以内，逻辑放进组合式函数。
5. **文案对教师友好**：UI 文案统一使用平实表达，工程术语只在系统/审计页保留并附人话注释。
6. **暗色模式可用**：所有新组件天生支持亮 + 暗双主题；管理员端默认亮色，顶栏提供切换。
7. **可访问性**：键盘导航、焦点环、ARIA 标签、AA 对比度全部纳入组件库基线。

### 2.2 非目标（本次明确不做）

1. **不替换组件库**：Element Plus 留用，只通过主题覆盖与业务组件封装吸收其风格。
2. **不重写状态管理 / API 层**：`stores/` 与 `axios/` 现状保留，业务接入点不动。
3. **不动后端 API 契约**：所有数据仍来自 Java `/api/v1`。
4. **不做移动端精细适配**：`< 1024px` 仅保底可用。
5. **不做国际化**：界面继续中文，但所有可见文案集中在常量文件，便于将来抽 i18n。
6. **不上新业务能力**：现有所有 `coming-soon` 路由（用户详情、检索日志详情、授权审计日志、索引版本列表）维持原状，仅更新 `RouteState` 视觉。
7. **不做实时图表/监控仪表板的可视化升级**：系统健康页只做视觉对齐，不引入 ECharts / D3。

## 3. 设计决策摘要

| 维度 | 决策 |
| --- | --- |
| 视觉方向 | Anthropic / Claude 风：暖灰底 `#faf9f6` + 橙赭重音 `#d97757` + 圆润几何 |
| 信息密度 | 平衡（D2）：单屏 6–8 项卡片化列表，呼吸感与扫读效率兼顾 |
| 信息架构 | 流水线分组：`工作台 / 生产 / 运维 / 设置` 四段式 |
| 改造深度 | 设计系统 + 页面拆分 + 4 大关键交互重做（保留 Element Plus） |
| 重做交互 | Dashboard 生产架势 / 知识库构建向导 / 资料详情 + 解析进度 / 问答会话 + 检索诊断 |
| 主题 | 默认亮色，提供暗色切换；橙赭主色亮暗双套映射 |
| 文案 | 面向教师/教务的平实表达，工程术语集中在系统与审计页且加注释 |

## 4. 视觉系统 / Design Tokens

### 4.1 颜色 Token

亮色（默认）：

```scss
// surface scale
--ckqa-bg: #faf9f6;             // 应用背景，暖灰
--ckqa-bg-elevated: #fffaf4;    // 抬升背景，hero / 强调容器
--ckqa-surface: #ffffff;        // 默认面板
--ckqa-surface-muted: #f5f3ee;  // 静态面板（信息块）
--ckqa-surface-strong: #f0ebe1; // 强分隔面板

// border scale
--ckqa-border: #e8e2d8;
--ckqa-border-soft: #f0ebe1;
--ckqa-border-strong: #d4cdbe;

// text scale
--ckqa-text: #1a1a1a;
--ckqa-text-muted: #6b6760;
--ckqa-text-weak: #a8a39a;
--ckqa-text-inverse: #ffffff;

// accent (orange-rust)
--ckqa-accent: #d97757;
--ckqa-accent-strong: #c4633a;
--ckqa-accent-soft: #fdf3ee;
--ckqa-accent-contrast: #ffffff;

// semantic — each has solid + soft pair
--ckqa-success: #4a7c59;
--ckqa-success-soft: #eef5ee;
--ckqa-running: #b06c2c;
--ckqa-running-soft: #fdf3ee;
--ckqa-warning: #b8860b;
--ckqa-warning-soft: #fef5d7;
--ckqa-blocked: #6b6760;
--ckqa-blocked-soft: #f0ebe1;
--ckqa-danger: #c4413a;
--ckqa-danger-soft: #fceeec;

// focus ring
--ckqa-focus: rgb(217 119 87 / 35%);
```

暗色映射：

```scss
[data-theme='dark'] {
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

  --ckqa-accent: #e8916f;
  --ckqa-accent-strong: #d97757;
  --ckqa-accent-soft: #38221a;
  --ckqa-accent-contrast: #1a1a1a;

  --ckqa-success: #6a9b78;
  --ckqa-success-soft: #1f2d22;
  --ckqa-running: #d4914c;
  --ckqa-running-soft: #382a1d;
  --ckqa-warning: #d4a13d;
  --ckqa-warning-soft: #38301a;
  --ckqa-blocked: #8a847a;
  --ckqa-blocked-soft: #2e2a24;
  --ckqa-danger: #d96860;
  --ckqa-danger-soft: #38201e;

  --ckqa-focus: rgb(232 145 111 / 40%);
}
```

非橙赭 accent 通道（`indigo / blue / teal / violet / amber`）保留但默认隐藏，未来如果做角色色或多租户主题再启用。

### 4.2 字体 Token

```scss
--ckqa-font-sans: "Inter", "DM Sans", "PingFang SC", "Hiragino Sans GB",
                  "Microsoft YaHei", system-ui, sans-serif;
--ckqa-font-mono: "JetBrains Mono", "DM Mono", "Cascadia Code", ui-monospace, monospace;

// type scale
--ckqa-text-xs:    11px / 16px;
--ckqa-text-sm:    12px / 18px;
--ckqa-text-base:  13px / 20px;
--ckqa-text-md:    14px / 22px;
--ckqa-text-lg:    15px / 24px;
--ckqa-text-xl:    18px / 26px;
--ckqa-text-2xl:   22px / 30px;
--ckqa-text-3xl:   28px / 36px;

// weights
--ckqa-fw-regular: 400;
--ckqa-fw-medium: 500;
--ckqa-fw-semibold: 600;
```

页面标题统一使用 `text-2xl / weight-medium`（仿 Anthropic 文档），不用 weight 700 的"硬粗体"。

### 4.3 间距 / 圆角 / 阴影

```scss
--ckqa-space-1: 4px;
--ckqa-space-2: 8px;
--ckqa-space-3: 12px;
--ckqa-space-4: 16px;
--ckqa-space-5: 20px;
--ckqa-space-6: 24px;
--ckqa-space-8: 32px;
--ckqa-space-10: 40px;
--ckqa-space-14: 56px;
--ckqa-space-18: 72px;

--ckqa-radius-xs: 4px;
--ckqa-radius-sm: 6px;
--ckqa-radius-md: 8px;
--ckqa-radius-lg: 10px;
--ckqa-radius-xl: 12px;
--ckqa-radius-2xl: 14px;
--ckqa-radius-full: 999px;

--ckqa-shadow-xs: 0 1px 2px rgb(28 26 23 / 6%);
--ckqa-shadow-sm: 0 2px 8px rgb(28 26 23 / 5%);
--ckqa-shadow-md: 0 8px 24px rgb(28 26 23 / 8%);
--ckqa-shadow-lg: 0 18px 42px rgb(28 26 23 / 12%);
--ckqa-shadow-focus: 0 0 0 3px var(--ckqa-focus);
```

### 4.4 动效 Token

```scss
--ckqa-duration-fast: 140ms;
--ckqa-duration-base: 200ms;
--ckqa-duration-slow: 320ms;

--ckqa-ease-standard: cubic-bezier(0.2, 0, 0, 1);
--ckqa-ease-decelerate: cubic-bezier(0, 0, 0.2, 1);
--ckqa-ease-accelerate: cubic-bezier(0.4, 0, 1, 1);
```

原则：

1. 所有交互态变化（hover、focus、激活）使用 `fast + standard`。
2. 跨视图切换（步骤切换、抽屉、对话框）使用 `base + decelerate`。
3. 长流程进度脉冲（活动指示点）使用 `slow + ease-in-out`。
4. 出现 / 消失类（加载骨架）只用透明度 + 极小位移，不用大幅缩放。

### 4.5 Element Plus 主题映射

通过 `styles/element-plus.scss` 把 Element Plus 的关键 CSS 变量映射到 CKQA Token：

```scss
:root {
  --el-color-primary: var(--ckqa-accent-strong);
  --el-color-primary-light-3: var(--ckqa-accent);
  --el-color-primary-light-9: var(--ckqa-accent-soft);
  --el-color-success: var(--ckqa-success);
  --el-color-warning: var(--ckqa-warning);
  --el-color-danger: var(--ckqa-danger);

  --el-bg-color: var(--ckqa-surface);
  --el-bg-color-page: var(--ckqa-bg);
  --el-fill-color-light: var(--ckqa-surface-muted);

  --el-text-color-primary: var(--ckqa-text);
  --el-text-color-regular: var(--ckqa-text-muted);
  --el-text-color-placeholder: var(--ckqa-text-weak);

  --el-border-color: var(--ckqa-border);
  --el-border-color-light: var(--ckqa-border-soft);
  --el-border-color-lighter: var(--ckqa-border-soft);

  --el-border-radius-base: var(--ckqa-radius-md);
  --el-border-radius-small: var(--ckqa-radius-sm);
  --el-border-radius-round: var(--ckqa-radius-full);

  --el-font-family: var(--ckqa-font-sans);

  --el-box-shadow-light: var(--ckqa-shadow-sm);
  --el-box-shadow: var(--ckqa-shadow-md);
}
```

这样无论是 `el-button` / `el-input` / `el-table` / `el-dialog`，都自然继承 CKQA 视觉，不需要逐一改 class。

## 5. 信息架构与导航

### 5.1 一级导航分组

```text
工作台
─────────────
生产
  · 课程
  · 资料
  · 知识库
─────────────
运维
  · 问答会话
  · 检索日志
  · 知识库验证   ← 原 "QA 冒烟" 改名
─────────────
设置
  · 用户与权限
  · 系统健康
  · 审计日志
```

`primaryNavigation` 在 `router/routes.js` 中重写为带 `section` 字段的扁平数组，由 `SideNavigation.vue` 按 `section` 分段渲染。每个 item 可附 `count` 字段用于显示徽标（如未读、构建中数量）。

### 5.2 路由层调整

1. 保留所有现有 path（不破坏书签 / E2E）。
2. 给 `route.meta` 增加 `section`（`dashboard / production / operations / settings`），`navGroup` 字段保留作为详情页面的"所属一级"线索。
3. 把 `qa-smoke` 路径展示文案改为"知识库验证"，路由 path 维持为 `/app/qa-smoke`（用户的旧链接不破）。

### 5.3 顶栏

```text
[Logo + CKQA Console] ── [范围芯片 ◉ 教师 · 操作系统课程] ───── [⌘K 命令面板] [◐ 主题] [🔔 通知] [LJ 头像]
```

- 范围芯片显示当前用户视角的实际数据范围；点击展开角色 / 课程切换。
- ⌘K 是新增的全局命令面板，覆盖：跳转、搜索课程/资料/知识库、新建。
- 通知图标点开下拉，展示进行中的任务、近期失败、需要操作的事项；这是 Dashboard "进行中任务"模块的轻量替身，让任意页都可一窥状态。

### 5.4 左侧栏

宽度 220px，可折叠到 64px（仅图标）。

- 段标题 `工作台 / 生产 / 运维 / 设置` 使用 `text-xs / text-weak / uppercase`。
- 每个 item 高度 32px，左右内边距 10px，圆角 7px。
- 激活态背景 `accent-soft`、文本 `accent-strong`、加 `medium` 字重；徽标背景反白。
- 底部固定一个"系统状态"小卡，绿点 + 一句平实的"API 正常 · GraphRAG 在线"，点击进系统健康。

### 5.5 面包屑

```text
[一级段名] / [所在路径资源] / [当前]
```

- 一级段名是导航段名（生产 / 运维 / 设置）或工作台。
- 中间层级是上下文资源（如：操作系统课程）。
- 当前层不可点。

`console-breadcrumb-model.js` 已经存在，扩展支持新的段映射；移除原始的 status badge（"mvp / upcoming"），改为只在 `RouteState` 页面内部显示。

## 6. 布局壳

### 6.1 ConsoleLayout（默认）

```text
┌────────────────────────────────────────────────────────────┐
│  Topbar (52px)                                             │
├──────────┬─────────────────────────────────────────────────┤
│          │  Crumbs                                         │
│  Side    │  Page Title  + Subtitle                         │
│  220px   │                                                 │
│          │  ┌──────────────────────────────────────────┐   │
│          │  │  Page Content (max-width: 1280)          │   │
│          │  │                                          │   │
│          │  └──────────────────────────────────────────┘   │
└──────────┴─────────────────────────────────────────────────┘
```

- 主区左右内边距 28px，顶部 22px。
- 主区最大宽度 1280px，超大屏（≥ 1600）居中。
- 不再在标题旁显示 `mvp / upcoming` 状态标签。

### 6.2 DetailLayout

详情页（课程 / 资料 / 知识库 / 索引版本 / 问答会话 / 用户）通用：

```text
Topbar
Side                Content
                    ┌──────────────────────────────┐
                    │  ←  back-link    actions ⋯   │
                    │                              │
                    │  Resource Title              │
                    │  status pill · meta          │
                    │  description (1 line)        │
                    │                              │
                    │  ┌────────┬────────┐         │
                    │  │ Tab1   │ Tab2   │         │
                    │  └────────┴────────┘         │
                    │  Tab content                 │
                    └──────────────────────────────┘
```

- 顶部资源信息块作为唯一标题区，不再有面包屑下"路由标题"重复。
- Tabs 在标题块下方，使用 underline 样式而非分段控件，保持简洁。
- 右上 `⋯` 是次要操作菜单（删除、复制 ID、归档等）。

### 6.3 WorkflowLayout

知识库构建向导专用，重做为分屏式：

```text
┌────────────────────────────────────────────────────────────┐
│  Topbar                                                    │
├──────────┬─────────────────────────────┬───────────────────┤
│          │  Step Indicator (横向 5 步) │                   │
│  Side    │ ───────────────────────────│  Live Run Panel   │
│          │                             │                   │
│          │  Current Step Form         │  实时进度 / 日志   │
│          │  (主交互区)                 │                   │
│          │                             │  (sticky)         │
│          │  [  上一步  ] [  下一步  ] │                   │
└──────────┴─────────────────────────────┴───────────────────┘
```

- 左侧为表单区（占 7/12），右侧为构建运行实时面板（占 5/12，sticky 可独立滚动）。
- 步骤指示器始终可见，每一步是一个 chip，已完成 = 暖橙实心，进行中 = 暖橙脉冲，未到 = 灰描边。
- 构建尚未启动时，右侧面板显示"提交后将在此实时显示构建过程"。
- 启动后右侧面板转为活动流：当前阶段、阶段进度条、日志摘要（最多 5 行）、错误高亮、继续/重试/取消按钮。

### 6.4 AuthLayout

登录、403、404、500 共用。结构：

```text
        [品牌图形 + 大字标题]
        [副标题]

        ┌───────────────┐
        │   表单 / 提示  │
        └───────────────┘

        [脚注]
```

- 背景使用渐进暖灰 + 微弱橙赭光晕（顶部 / 底部各一处径向渐变）。
- 错误页（403/404/500）保留各自语义化插画位置（暂用 emoji 占位 → 后续可用 svg 替换）。

## 7. 通用业务组件

新增到 `src/components/common/` 的组件：

| 组件 | 用途 | 关键 Props |
| --- | --- | --- |
| `<CkPageHero>` | 页头：标题 + 副标 + 操作区 | `title / subtitle / actions` slot |
| `<CkBreadcrumbs>` | 重写后的面包屑 | `items[]` |
| `<CkStatusPill>` | 通用状态徽章 | `tone: success / running / warning / blocked / danger / neutral`, `label` |
| `<CkPipelineHero>` | Dashboard 5 段生产流水线 | `stages[]`, `activeKey` |
| `<CkActivityFeed>` | 活动时间线 | `events[]`（type / title / sub / when） |
| `<CkTaskList>` | 进行中任务（带进度条） | `tasks[]` |
| `<CkResourceCard>` | 资源列表卡片 | `title / description / status / meta / actions` |
| `<CkEmptyState>` | 空态 | `icon / title / description / cta` |
| `<CkInfoTable>` | 详情页 key-value 信息块 | `entries[]` |
| `<CkSplitProgress>` | 分阶段进度条（向导 + 解析共用） | `steps[]`, `currentStep`, `currentPct` |
| `<CkLogStream>` | 滚动日志面板（向导右侧、解析进度共用） | `lines[]`, `autoFollow` |
| `<CkRetrievalPanel>` | 问答检索诊断面板 | `chunks / subQueries / trace / sources` |
| `<CkCommandPalette>` | ⌘K 命令面板 | 全局挂载 |

每个组件单文件 < 300 行，对应一个 `*.test.js` 单元测试（基于现有 vitest）。

## 8. 重做交互详细方案

### 8.1 Dashboard "生产架势"

布局：

```text
[ 问候语 + 摘要句 ]

[ 生产流水线 Hero ]
  ┌ 01 课程 ─→ 02 资料 ─→ 03 知识库 ─→ 04 激活 ─→ 05 问答 ┐
  │ 12         428/412    3/9 构建中    9            1.2k    │
  │            16 待解析   65% / 32%    最新 v3      P95 312ms│
  └─────────────────────────────────────────────────────────┘

[ 近期动态 (1.55fr) ]                  [ 进行中任务 (1fr) ]
  · 操作系统知识库 v2 激活完成              · 数据结构 v2 索引 65%
  · 数据结构开始构建索引                    · 机器学习 v0 索引 32%
  · 5 份 PDF 解析失败 已重试中              · 5 份 PDF 重新解析 8%
  · 知识库验证通过 — 12/12
  · 新上传 23 份课件                      [ 快捷入口 ]
                                           + 新建知识库 / ↑ 上传资料
                                           ▷ 知识库验证 / ≡ 检索日志
```

数据来源：

| 区块 | 数据来源 |
| --- | --- |
| 流水线 5 段 | 已有 `/api/v1/courses?summary=1`、`/api/v1/materials?summary=1`、`/api/v1/knowledge-bases?summary=1`、`/api/v1/qa-sessions?summary=1`；如果 summary 接口不存在，先在前端聚合各列表分页 total |
| 进行中任务 | `/api/v1/index-runs?status=running` + `/api/v1/material-parse-tasks?status=running` 聚合 |
| 近期动态 | 短期前端聚合：从上述列表各取最近 N 条 + 状态变更时间，按时间倒序合并；后续可由后端提供统一 audit-events 接口 |
| 快捷入口 | 静态 4 项，权限不足时隐藏 |

角色差异：

- 平台管理员：流水线 5 段统计为全平台；任务列表显示全平台进行中。
- 教师 / 助教：流水线统计自动按 `course_membership` 过滤；不展示其他课程任务。
- 只读运维：隐藏快捷入口区，扩大近期动态区。

交互细节：

1. 流水线 stage 卡片可点击，对应跳转到下一级列表（如点"03 知识库"跳到知识库列表，并把"构建中"作为默认筛选）。
2. 当前活跃 stage 右上角脉冲点（动效 token `slow + ease-in-out`）。
3. 近期动态行 hover 显示"查看详情"按钮，点击进入对应资源。
4. 任务行的进度条颜色随状态变（运行 = 暖橙，等待 = 中性灰，失败 = 暗红）。

文案规则：

- 不出现"冒烟"，统一用"知识库验证"。
- 不出现"P95"在卡片内（移到卡片 hover tooltip），主显示用"响应时间 312ms（高负载下）"。
- 不出现"embedding"/"实体抽取"，统一用"识别课程概念 / 构建检索索引"。

### 8.2 知识库构建向导

5 步流程（路径维持 `/app/knowledge-bases/:kbId/build`）：

```text
① 选资料        ②  内容切分       ③  检索模型        ④  索引参数      ⑤  启动构建
└─ 已激活的    └─ 块大小 / 重叠   └─ 模型提供商    └─ 概念识别精度    └─ 提交后
   课程资料       的视觉调节器        与维度选择       / 关联密度        到右侧实时面板
   勾选树
```

左侧表单区：

- ① 资料勾选：树形展示 课程 → 章节 → 资料；过滤可按"已就绪 / 待解析 / 失败"；显示已选数 / 总大小。
- ② 内容切分：滑动条调"块大小"与"重叠百分比"，旁边显示"将切出约 N 个片段"的实时估算（前端公式）；顶部三个预设按钮（保守 / 标准 / 激进）。
- ③ 检索模型：下拉选检索模型（数据来自后端 `/api/v1/llm-providers?usage=embedding`，按提供商分组）；旁边显示该模型的"维度 / 推荐用途"短描述，不暴露"embedding"字样。
- ④ 索引参数：概念识别精度 + 关联密度滑块；顶部预设"轻量 / 标准 / 完整"；下方显示"预计耗时 X 分钟（基于历史）"。
- ⑤ 启动确认：摘要全部参数；底部主按钮"开始构建"。

右侧实时运行面板（重要重做点）：

- 启动前：浅色占位 + 一句"提交后将在这里实时显示构建过程"。
- 启动后：
  - 顶部：当前阶段标题 + 整体进度（基于阶段权重加权计算）。
  - 中部：阶段时间线（垂直 5 段），每段显示 "已完成 / 进行中 / 等待"，进行中段附蜂窝图标 + 当前子任务。
  - 底部：滚动日志面板，使用 `<CkLogStream>` 自动滚动到底部，遇到 ERROR 行自动高亮且暂停自动滚动等待用户操作。
  - 失败时：阶段卡片标红，下方出现"重试当前阶段 / 跳过当前阶段 / 取消构建"操作。
- 所有日志文案前端做术语清洗（同上一节）。

数据来源：

- 已有 `/api/v1/knowledge-bases/:kbId/build` 启动接口。
- SSE `/api/v1/index-runs/:runId/stream` 推送阶段进度（已有，沿用）。
- 失败状态来自 SSE 事件 `stage.failed`。

可返回修改：

- 在第 ⑤ 步之前每一步底部都有"上一步"按钮；表单状态保留在 Pinia `useBuildWizardStore`。
- 启动后表单变只读；右上提供"复制为新构建"按钮，可基于本次参数立即开第二轮。

### 8.3 资料详情 + 解析进度

新页面 `MaterialDetailPage.vue`。

```text
[ ← 课程资料 ]                                          [ ⋯ 更多操作 ]

数据结构课件 第3章.pdf
[● 解析中 38%]   操作系统课程 · 8.4 MB · 上传于 2 小时前

—————————————————————————————————————————————————
| 解析进度 | 解析结果 | 知识库引用 | 操作日志 |
—————————————————————————————————————————————————

[ 当前 Tab 内容 ]
```

#### Tab 1：解析进度

```text
┌─ 阶段时间线 (左 1/3) ─┬─ 实时输出 (右 2/3) ──────────┐
│ ✓ 上传完成 12:08      │ 当前：正在识别图表区域       │
│ ✓ 文档预处理 12:09     │ ─────────────────────────────│
│ ⏵ OCR + 文本抽取 12:11 │ 进度条 38%                   │
│ ○ 结构化（章节）      │                              │
│ ○ 切分块             │  日志流                       │
│ ○ 入库                │  · 已识别页码 38 / 124        │
│                      │  · 提取图片 12                │
│                      │  · 表格区域 5                  │
│                      │  · 当前页：识别公式中…        │
└──────────────────────┴───────────────────────────────┘
```

- 时间线使用 `<CkSplitProgress>`（向导也用同款）。
- 实时输出使用 `<CkLogStream>`，订阅现有 SSE。
- 失败时阶段卡片标红，顶部 banner 提示"PDF 解析超时，已自动重试 1 次。可手动重试或联系管理员"。

#### Tab 2：解析结果

子 Tab：`Markdown 预览 / 切分块 / 图片 / 原始 PDF`。

- Markdown：单列长文，左右留白，等宽字体；锚点目录在右侧 sticky。
- 切分块：列表卡片，每张卡片显示块编号、字符数、首句预览；hover 可查看完整内容。
- 图片：网格瀑布流，每张点击放大；提供"在原 PDF 中定位"链接。
- 原始 PDF：iframe 嵌入或下载链接。

#### Tab 3：知识库引用

显示这份资料被哪些知识库引用（多对多关系）：

- 卡片列表（`<CkResourceCard>`），每张展示知识库名 + 当前版本 + 状态。
- 卡片右侧 `跳转 →` 按钮去 KB 详情。

#### Tab 4：操作日志

时间倒序的事件列表（上传 / 重新解析 / 删除 / 复制等动作）；与系统审计日志同源但只过滤本资料。

### 8.4 问答会话 + 检索诊断

新页面 `QaSessionDetailPage.vue`。重做核心：把"我看到 AI 这段回答"和"它是怎么得到的"放在同一个屏内，方便教务做检查。

```text
[ ← 问答会话 ]            数据结构与算法 v2 · 2026-05-07 14:32 · 学员 张同学

┌─ 消息流 (左 1/2) ───────┬─ 检索诊断 (右 1/2) ─────┐
│ 学员：什么是动态规划？  │  当前消息：第 1 条 AI 回答  │
│ AI：动态规划是…        │  ─────────────────────────│
│  └─ [查看检索过程]      │  ◐ 子问题拆分                │
│                        │  · 动态规划的定义           │
│ 学员：举例呢？         │  · 经典例题                 │
│ AI：经典例子…          │                              │
│  └─ [查看检索过程]      │  ◐ 检索片段 (TOP 5)         │
│                        │  · §动态规划 — 4.2 节        │
│                        │  · §最优子结构 — 4.3 节      │
│                        │  · …                        │
│                        │                              │
│                        │  ◐ 调用链 (耗时分布)         │
│                        │  · 检索 142ms                │
│                        │  · 模型生成 1.8s             │
│                        │  · 后处理 31ms               │
│                        │                              │
│                        │  ◐ 出处                       │
│                        │  · 数据结构课件 第4章.pdf    │
│                        │    第 12-15 页              │
└────────────────────────┴───────────────────────────────┘
```

交互：

- 消息流每条 AI 回答下有"查看检索过程"按钮，点击后右侧诊断面板切换到该条。
- 默认右侧锁定到"最新一条 AI 回答"。
- 右侧诊断面板可折叠成右抽屉模式（小屏退化）。
- 检索片段卡片可点击跳到资料详情对应页码。
- 当一条回答没有检索（直接生成）或检索失败时，右侧明确显示"本回答未触发检索"或"检索失败：原因…"。

数据来源：

- 已有 `/api/v1/qa-sessions/:id` 返回会话与消息。
- 新需求：每条消息扩展返回 `retrieval_trace`（子问题、片段、调用耗时、出处）；后端如已有则直接用，否则在本设计稿外通过单独工单跟进。如临时缺失，前端使用 N/A 占位，不阻塞本次重做。

会话列表 `QaSessionListPage.vue` 同步重做：

- 顶部筛选：知识库 / 课程 / 时间范围 / 异常会话。
- 列表项使用 `<CkResourceCard>`：会话标题 + 学员 + 消息数 + 总耗时 + 是否含异常。
- 异常会话用 `tone: warning` 角标标记，hover 显示原因。

## 9. 其他页面（重刷不重做）

下面页面统一吃新的设计系统 + 拆分到独立组件，但交互逻辑维持现状：

| 页面 | 拆出文件 | 备注 |
| --- | --- | --- |
| 课程列表 | `views/courses/CourseListPage.vue` | 用 `<CkResourceCard>` 网格，封面 16:9 |
| 课程详情 | `views/courses/CourseDetailPage.vue` | DetailLayout + 4 Tab：概览 / 成员 / 资料 / 知识库 |
| 课程成员 | `views/courses/CourseMembersTab.vue` | 表格用 Element Plus，自动主题映射 |
| 课程资料 | `views/courses/CourseMaterialsTab.vue` | 列表 + 上传抽屉 |
| 知识库列表 | `views/knowledge-bases/KbListPage.vue` | `<CkResourceCard>` 列表 |
| 知识库详情 | `views/knowledge-bases/KbDetailPage.vue` | DetailLayout + 4 Tab：概览 / 来源资料 / 索引版本 / 验证记录 |
| 索引版本详情 | `views/knowledge-bases/IndexRunDetailPage.vue` | 与构建向导右侧面板同款，但只读；UI 标题"索引版本详情"，文件名保留以兼容路由 |
| 用户列表 | `views/users/UserListPage.vue` | 表格 + 角色徽章 |
| 角色 / 权限 | `views/users/RoleListPage.vue / PermissionListPage.vue` | 信息表，整页 |
| 系统健康 | `views/system/HealthPage.vue` | 顶部健康总览 + 各服务卡片，状态用 `<CkStatusPill>` |
| 知识库验证 | `views/operations/KbValidationPage.vue` | 原 `/app/qa-smoke`，文案改"知识库验证" |
| 路由占位 | `views/status/RouteState.vue` | 视觉对齐 AuthLayout，主题感染 |

`ModulePage.vue` 拆完之后保留为兜底（路由 fallback），但所有 `componentKey: 'ModulePage'` 的路由都改指向上述具体页面。

## 10. 文案与术语规范

### 10.1 状态词

统一使用：

- 待解析 / 解析中 / 已就绪 / 解析失败
- 待构建 / 构建中 / 已激活 / 构建失败 / 已停用
- 已发起 / 进行中 / 已完成 / 已取消 / 异常

避免内部 enum（`PENDING / RUNNING / SUCCEEDED / FAILED`）暴露在 UI。

### 10.2 工程术语翻译

| 内部术语 | UI 文案 | 适用范围 |
| --- | --- | --- |
| 冒烟测试 / QA 冒烟 | 知识库验证 | 全局 |
| smoke session | 验证会话 | 全局 |
| embedding / 嵌入 | 构建检索索引 | 向导提示语 |
| 实体抽取 | 识别课程概念 | 向导参数说明 |
| chunking / 切分 | 内容切分 | 向导步骤标题 |
| P95 延迟 / latency | 高负载下响应时间 | 工作台 + 详情；系统健康页可保留专业表达 |
| MinerU 任务超时 | PDF 解析超时，正在重试 | 资料详情 |
| index run | 索引版本 | 知识库详情 |
| token 用量 | 模型用量 | 系统健康 / 审计 |

实现：所有文案集中到 `src/copy/admin.ts`，按页面 / 模块分组导出，单元测试覆盖关键替换。

### 10.3 操作动词

- 启动 / 重新启动 / 取消 / 重试 / 删除 / 复制 / 导出 / 上传 / 移交 / 标记
- 不使用："执行"（偏内部）、"触发"（偏内部）、"提交任务"（偏内部）。

### 10.4 提示语风格

- 主语先行，避免被动："正在解析 5 份 PDF" 而不是 "5 份 PDF 被解析中"。
- 加上数字与对象：避免空洞动作描述。
- 失败时给可操作建议："PDF 解析超时，已自动重试一次。可手动重试或检查文件大小。"

## 11. 主题与可访问性

### 11.1 主题

- 默认 `data-theme='light'`。
- 用户在顶栏 ◐ 切换；切换状态写入 `localStorage` 并广播给 Pinia `useThemeStore`，应用根 `<html>` 标签上 `data-theme`。
- Element Plus 暗色主题通过同名 CSS 变量自动接管，无需额外引入官方暗色 CSS。
- 所有自研组件必须声明颜色都来自 Token，不写死色值；Lint 规则：`stylelint --custom-syntax postcss-html` + 自定义规则禁止 hex 与 rgb 直写。

### 11.2 可访问性

| 维度 | 要求 |
| --- | --- |
| 对比度 | 文本 ≥ 4.5:1（WCAG AA），交互态 ≥ 3:1 |
| 焦点 | 所有可聚焦元素必须显示 `--ckqa-shadow-focus`，不允许 `outline: none` 不补 ring |
| 键盘 | 全部主要操作键盘可达：菜单（↑↓ Enter）、对话框（Tab 圆周）、抽屉（Esc 关闭）、Tab 切换（← →） |
| ARIA | 状态变化使用 `aria-live='polite'`（如 SSE 进度推送、活动反馈） |
| 跳过链接 | `Skip to main content` 已存在于 ConsoleLayout，扩展到 DetailLayout / WorkflowLayout |
| 文字缩放 | 默认 13px，浏览器缩放至 200% 不破版（容器使用 `rem`） |
| 表单错误 | 错误文本与字段使用 `aria-describedby` 关联 |

## 12. 实施范围（后续 plan 拆解依据）

本设计稿定义"目标态"，不包含具体实施步骤。实施侧的拆分将在下一阶段由 writing-plans skill 生成 `docs/superpowers/plans/2026-05-07-admin-app-redesign-plan.md`。

预期里程碑（仅信息：实施计划会重新审视）：

1. **M1 设计系统底座**：新增 token、Element Plus 主题映射、`<CkStatusPill>` 等基础组件。
2. **M2 布局壳与导航**：重写 ConsoleLayout / DetailLayout / WorkflowLayout / AuthLayout，左侧栏分段渲染。
3. **M3 Dashboard 重做**：流水线 hero、活动时间线、任务面板、快捷入口。
4. **M4 课程 / 资料模块拆分 + 资料详情重做**：MaterialDetailPage 4 Tab。
5. **M5 知识库 + 构建向导重做**：WorkflowLayout 分屏 + 实时面板。
6. **M6 问答会话 + 检索诊断重做**：QaSessionDetailPage 双栏。
7. **M7 其他页面拆分与适配**：用户、角色、权限、系统健康、知识库验证、RouteState。
8. **M8 文案巡检 + 暗色验证 + 可访问性巡检 + Playwright 用例同步**。

## 13. 风险与依赖

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| ModulePage.vue 拆分破坏现有真实数据接入 | 高 | 拆分时保留 `module-content.js / module-loaders.js` 作为各资源页的逻辑基础，逐页迁移并在每次迁移后跑 Playwright |
| Element Plus 局部组件视觉与 Token 不兼容（如 DatePicker 弹层） | 中 | 出现时局部覆盖；不为单个问题撤回 Token 体系 |
| 暗色模式因第三方组件露出脏色 | 中 | 主题切换通过 Playwright 跑一遍核心页截图比对 |
| 检索诊断需要后端补 `retrieval_trace` 字段 | 中 | 前端先按"字段缺失=占位"实现，工单跟进后端补全 |
| 文案改动可能撞 E2E 文本断言 | 低 | E2E 用例改用 `data-test-id` 选择器；新增 fixture 覆盖文案 |
| 设计与实施时间线超预期 | 中 | 里程碑独立可验收；M1~M3 单独成 PR 已可见效果 |

## 14. 验收标准

设计稿不包含实施验收，但实施完成时需满足：

1. **视觉一致性**：所有页面渲染时使用 Token，无裸色值；`stylelint` 无违规。
2. **功能等价或更好**：所有现有功能在新页面下可用，关键路径 Playwright 全绿。
3. **暗色可用**：每个页面在 `data-theme='dark'` 下可正常浏览，无文字与背景对比度不足。
4. **可访问性**：键盘可遍历主流程；`axe-core` 自动化扫描 0 critical。
5. **文案巡检**：UI 不出现"冒烟 / embedding / 实体抽取 / P95 / MinerU"等术语；通过 `copy/admin.ts` 单元测试。
6. **无回归**：现有 `app-shell.test.js` + 新增组件单元测试 + Playwright 全部通过。
7. **代码组织**：单文件 < 600 行；`ModulePage.vue` 退化为兜底，不再被任何路由直接引用。

## 15. 附录：与现有结构的兼容映射

| 现状文件 | 重设计后 |
| --- | --- |
| `views/dashboard/DashboardView.vue` | 整体重写为 `views/dashboard/DashboardPage.vue` + 新组件 |
| `views/system/HealthView.vue` | `views/system/HealthPage.vue`，吃新设计系统 |
| `views/pages/ModulePage.vue`（3957 行） | 拆分到上述 14+ 个独立页面 + 通用组件 |
| `views/pages/module-content.js` | 改为各页面的局部组合式函数 |
| `views/pages/module-loaders.js` | 改为各页面 `composables/use*.js` |
| `views/pages/material-lifecycle-actions.js` | 移入 `composables/useMaterialLifecycle.js` |
| `views/pages/qa-polling.js` | 移入 `composables/useQaPolling.js` |
| `views/pages/long-task-state.js` | 移入 `composables/useLongTaskState.js` |
| `views/pages/build-wizard/*` 现有占位 | 与新 WorkflowLayout 整合 |
| `views/status/RouteState.vue` | 视觉对齐 AuthLayout，配色 token 化 |
| `views/auth/LoginView.vue` | 视觉升级，结构沿用 |
| `views/status/UnifiedErrorView.vue` | 视觉升级，结构沿用 |
| `styles/tokens/_colors.scss` | 扩展为 4.1 节定义的全套语义 token |
| `styles/element-plus.scss` | 新增 4.5 节的 Element Plus 主题映射 |
| `components/shell/SideNavigation.vue` | 改为分段渲染 + 段标题 |
| `components/shell/AppTopbar.vue` | 增加范围芯片、⌘K、通知、主题切换 |

整个重设计完成后，`frontend/apps/admin-app/` 应该具备：

- 一份可见的 Token 文件（暖灰 + 暖橙）
- 一套可复用的业务组件
- 4 个真正"产品级"的关键交互
- 一致的导航 / 布局 / 文案
- 暗色与可访问性下沉为基线

让平台从"一个把所有路由跑通的工具"，长成"一个让教务老师愿意每天打开的 AI 课程知识库平台"。
