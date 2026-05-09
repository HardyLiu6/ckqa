# 管理员端视觉打磨（M1-M2 之后的视觉迭代）

- 日期：2026-05-09
- 范围：`frontend/apps/admin-app/`
- 上游基线：[2026-05-07-admin-app-redesign-design.md](../specs/2026-05-07-admin-app-redesign-design.md) 与 [2026-05-07-admin-app-redesign-m1-m2-foundation-plan.md](../plans/2026-05-07-admin-app-redesign-m1-m2-foundation-plan.md)
- 视觉走查产物：`.superpowers/brainstorm/<session>/content/{card-styles,dashboard-glass-v1,sidebar-v3}.html`（迭代过程的可交互对照稿，非仓库代码）

> 本设计是 **视觉迭代**，不是新一轮 M3。M1+M2 已经把暖色 token、4 个布局壳、流水线侧栏、12 个 `Ck*` 组件落地；M3 已交付 `DashboardPage.vue + CkPipelineHero + CkActivityFeed + CkTaskList`。运行起来后的视觉走查发现：基线"功能可用、视觉中规中矩"，但还达不到"产品级质感"。本文档定义在不动信息架构与组件契约的前提下，针对 **字号系统、表面层级（玻璃态）、Sidebar 识别度与动效语言、品牌名** 这四件事做的系统级调优。

## 1. 背景

### 1.1 走查发现的问题

针对 M1+M2+M3 已落地的真实页面（亮色亮 / 默认 rust accent / 1280px 桌面）做的对照式走查：

1. **字号体系整体偏小，可读性不足。** 当前 `--ckqa-text-base-size: 13px` / `text-sm: 12px` / `text-xs: 11px`。对比 Tailwind / Linear / Anthropic Console 等同类后台的正文 14–15px、辅助 12–13px，CKQA 的次级文字（"已开课程 / 资料就绪 / 构建中" 等）落在 11–12px 区间，肉眼明显偏紧。
2. **页面整体扁平、卡片缺少层次。** Dashboard 的 hero、流水线、动态、任务、快捷入口五块直接铺在 `--ckqa-bg` 上，没有"面板感"；当数据为空时（`—`占位）整页像还没渲染完。
3. **Sidebar 识别度不足。** 仅文字 + Section 标题，无图标、无激活态视觉锚点；底部 footer 仅 1 行身份信息浪费了空间；折叠状态当前没有实现。
4. **Toggle / 按钮缺少反馈语言。** Hover 仅做颜色切换、无形变；没有按压感；折叠/展开等"状态切换"操作缺少动效记忆点。
5. **品牌名错位。** 多处 UI 显示 `CKQA Console`，实际产品对外名称是「**智课问答**」。CKQA 是仓库 / 工程上下文标识，不应出现在面向教师 / 教务的界面。
6. **Hero 区信息重复 + Fallback Banner 喧宾夺主。** 面包屑「工作台 / 工作台」叠 hero eyebrow「工作台」三处重复；`fallback-data` 的黄色 banner 紧贴 hero 顶部、占据视觉中心，本应是次要状态提示。
7. **Active rail 缺少持续视觉联系。** Section 之间分组明确但激活项只靠浅背景，扫读时无法立即定位"我现在在哪"。
8. **课程 / 知识库图标渲染异常。** 之前候选 SVG 用了相对弧线指令（`a9 3 0 0 0 18 0`），在主流浏览器渲染时 stroke 易出现细线断点。

### 1.2 不在本次范围内的事

- 不重新分组路由 / 信息架构（与 M1+M2 一致：`工作台 / 生产 / 运维 / 设置`）。
- 不替换组件库；继续在 Element Plus + `Ck*` 组件之上叠加。
- 不改后端契约 / API 路径 / SSE 通道。
- 不做 M4–M7（构建向导、资料详情、问答会话）的页面重做。
- 不做暗色 token 的细节调优（仅做语义对齐，不重新调色）。
- 不做移动端（< 1024px）适配。
- 不做命令面板 / 通知 / 主题切换的功能层重构（仅吸收新视觉规范）。

## 2. 目标与非目标

### 2.1 设计目标

1. **字号体系上调一档**，让管理员端正文与同类产品对齐（正文 14–15px、辅助 12–13px、Hero 30px），并在不破坏现有版式的前提下完成。
2. **建立"玻璃态表面层级"**，把面板从扁平背景转成有"frost glass"质感的可识别容器；ambient 光照让暖灰底有产品级气质。
3. **Sidebar v3** 成为后台的视觉记忆点：图标 + 暖色 active rail + 状态卡 + 折叠态 + 折叠/展开 / hover / press 全套动效。
4. **建立"弹簧反馈"动效语言**：所有 hover / press / 状态切换共用一条 `cubic-bezier(.34,1.56,.64,1)` 弹簧曲线，区别于 M1-M2 已有的 `--ckqa-ease-standard` 标准曲线。
5. **品牌一致性**：UI 全面替换为「智课问答」；CKQA 名称仅保留在工程上下文（README / package.json / API 路径）。
6. **可访问性**：上调字号后所有页面 AA 对比度不退化；动效遵循 `prefers-reduced-motion`。

### 2.2 非目标

- 不重写已有 `Ck*` 组件契约（props / events / slots 不变，仅样式与内部动效升级）。
- 不引入新字体（保留 Inter + DM Sans + PingFang SC 栈）。
- 不引入动效库（Framer Motion / Motion One 等），仅 CSS transition + animation。
- 不引入新的全局状态（折叠态局部 state，不进 Pinia）。

## 3. 设计决策摘要

| 维度 | 决策 |
| --- | --- |
| 视觉方向 | 沿用 M1+M2 暖灰 + 暖橙；新增"frost glass"表面层级 + ambient 光照 |
| 字号基线 | base 13→14.5、md 14→15、xl 18→20、2xl 22→24、新增 hero 30px |
| 卡片语言 | Style A：软阴影 + 大数字（30px）+ 微趋势条；玻璃态作为容器底座 |
| Sidebar | v3：图标 + 暖色 active rail（呼吸光晕）+ 用户身份卡 + 折叠态 |
| 动效 | 引入弹簧曲线 `--ckqa-ease-spring`；保留标准曲线给页面切换 / 进度条 |
| 品牌 | 「智课问答」（UI）/ CKQA（工程） |
| 主题 | 默认亮色；玻璃态在暗色下退化为半透明深色面板（不引入暗色专属配色） |

## 4. Token 调整

### 4.1 字号上调一档

`src/styles/tokens/_typography.scss` 改动（`size`/`line-height` 双变量结构不变；**全部 1px 等差，避免 0.5px 半像素落到子像素渲染上**）：

```scss
:root {
  /* 上调后的 type scale —— 小字阶 1px 等差，大字阶按 4–6px 阶梯 */
  --ckqa-text-xs-size:    12px;  --ckqa-text-xs-line:    16px;  /* 11→12 */
  --ckqa-text-sm-size:    13px;  --ckqa-text-sm-line:    18px;  /* 12→13 */
  --ckqa-text-base-size:  14px;  --ckqa-text-base-line:  22px;  /* 13→14 */
  --ckqa-text-md-size:    15px;  --ckqa-text-md-line:    24px;  /* 14→15 */
  --ckqa-text-lg-size:    16px;  --ckqa-text-lg-line:    26px;  /* 15→16 */
  --ckqa-text-xl-size:    20px;  --ckqa-text-xl-line:    28px;  /* 18→20 */
  --ckqa-text-2xl-size:   24px;  --ckqa-text-2xl-line:   32px;  /* 22→24 */
  --ckqa-text-3xl-size:   30px;  --ckqa-text-3xl-line:   38px;  /* 28→30，作为 hero */
  --ckqa-text-display-size: 36px; --ckqa-text-display-line: 44px; /* 预留，本期不使用 */

  /* 字符间距 token（新增） */
  --ckqa-tracking-tight:  -0.4px;
  --ckqa-tracking-normal: 0;
  --ckqa-tracking-wide:   1px;
}
```

> **不再单独定义 `--ckqa-text-hero`**：hero 标题与流水线主数字直接复用 `--ckqa-text-3xl-*`，避免两个名字一个值时实现侧随机选取。

页面级用法约定（落地到 `src/styles/mixins/_typography.scss` 或 hero 组件局部）：

| 角色 | size / weight | 备注 |
| --- | --- | --- |
| Page hero H1 | 3xl / 600 / tracking-tight | `CkPageHero` 标题 |
| Page hero subtitle | md / 400 | hero 副标题 |
| Pipeline 数字 | 3xl / 600 / `font-feature-settings: "tnum"` | `CkPipelineHero` 主指标 |
| 卡片标题 | md / 500 | `CkActivityFeed`/`CkTaskList` 列表头 |
| 正文 | base / 400 | 列表行、表单提示 |
| 辅助说明 | sm / 400 | hint、metadata |
| Section 标题 | xs / 600 / tracking-wide / uppercase | sidebar `<h4>` |

### 4.2 玻璃态表面（新增）

`src/styles/tokens/_colors.scss` 新增"glass surface"语义层（亮色 / 暗色双套）：

```scss
:root {
  /* glass surface — 半透明 + backdrop-blur 表达"frost glass"
   * 暖灰底色对比度本就低，blur 给到 12px 即可看出层级；
   * 透明度 0.65 而非 0.55，让玻璃边界在低对比底色上更清晰。 */
  --ckqa-surface-glass:        rgba(255, 252, 246, 0.65);
  --ckqa-surface-glass-strong: rgba(255, 252, 246, 0.82);
  --ckqa-surface-glass-elev:   rgba(255, 255, 255, 0.88);

  /* glass border — 顶部 highlight 用 */
  --ckqa-border-glass:         rgba(255, 255, 255, 0.7);

  /* ambient — 用于全局背景光斑 */
  --ckqa-ambient-warm:         #f4c89e;
  --ckqa-ambient-rust:         #e9b3a3;

  /* 顶部内 highlight（白色 → 透明，模拟玻璃反光） */
  --ckqa-highlight-top: linear-gradient(180deg,
    rgba(255, 255, 255, 0.85) 0%,
    rgba(255, 255, 255, 0)    30%);

  /* 玻璃模糊参数（统一引用，方便整体调） */
  --ckqa-glass-blur:       12px;
  --ckqa-glass-saturate:   160%;
}

[data-theme='dark'] {
  --ckqa-surface-glass:        rgba(37, 34, 29, 0.65);
  --ckqa-surface-glass-strong: rgba(37, 34, 29, 0.82);
  --ckqa-surface-glass-elev:   rgba(46, 42, 36, 0.88);
  --ckqa-border-glass:         rgba(255, 255, 255, 0.06);
  --ckqa-ambient-warm:         #3a2a1c;
  --ckqa-ambient-rust:         #3a2622;
  --ckqa-highlight-top: linear-gradient(180deg,
    rgba(255, 255, 255, 0.06) 0%,
    rgba(255, 255, 255, 0)    30%);
}
```

`src/styles/tokens/_shadow.scss` 新增双层卡片阴影（玻璃态卡片用）：

```scss
$shadow-card:       0 1px 1px rgb(28 26 23 / 4%), 0 8px 24px rgb(110 70 40 / 6%);
$shadow-card-hover: 0 2px 4px rgb(28 26 23 / 5%), 0 18px 44px rgb(110 70 40 / 12%);

:root {
  --ckqa-shadow-card:       #{$shadow-card};
  --ckqa-shadow-card-hover: #{$shadow-card-hover};
}
```

### 4.3 动效曲线扩展

`src/styles/tokens/_motion.scss` 在 M1 已有 `--ckqa-duration-fast/base/slow` + `ease-standard/entrance/exit` 的基础上，新增弹簧曲线与玻璃过渡时长（**不替换** 已有 token）：

```scss
:root {
  /* M1 已有，仅列出便于全局参考 */
  --ckqa-duration-fast:  160ms;
  --ckqa-duration-base:  220ms;
  --ckqa-duration-slow:  320ms;
  --ckqa-ease-standard:  cubic-bezier(0.2, 0, 0, 1);
  --ckqa-ease-entrance:  cubic-bezier(0, 0, 0.2, 1);
  --ckqa-ease-exit:      cubic-bezier(0.4, 0, 1, 1);

  /* 本期新增 */
  --ckqa-ease-spring:    cubic-bezier(.34, 1.56, .64, 1);  /* 弹簧带超调 */
  --ckqa-ease-glass:     cubic-bezier(.2, .8, .2, 1);      /* 玻璃态平滑 */
  --ckqa-duration-press: 80ms;                              /* 按压瞬间 */
  --ckqa-duration-glass: 350ms;                             /* sidebar 折叠 / 卡片态切换 */
}
```

使用约定：

- 状态切换（折叠 / 展开 / Tab 切换）→ `--ckqa-ease-glass` + `--ckqa-duration-glass`
- 元素 hover 上浮、入场 pop、active rail 滑动 → `--ckqa-ease-spring` + `--ckqa-duration-base`
- 按压（按下瞬间 scale）→ `ease-out` + `--ckqa-duration-press`
- 进度条 / Skeleton / 页面切换 → `--ckqa-ease-standard`（沿用）

### 4.4 全局背景光斑（ambient）

在 `ConsoleLayout.vue` 的根容器（或 `src/styles/index.scss` 的 `body` 上）注入两个固定定位的渐变光斑，模拟"暖光从角落洒下"。

```scss
body::before, body::after {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
body::before {
  width: 60vw; height: 60vw;
  top: -20vw; left: -10vw;
  background: radial-gradient(circle at 30% 30%, var(--ckqa-ambient-warm) 0%, transparent 60%);
  filter: blur(120px);
  opacity: 0.55;
  border-radius: 50%;
}
body::after {
  width: 60vw; height: 60vw;
  bottom: -25vw; right: -15vw;
  background: radial-gradient(circle at 70% 70%, var(--ckqa-ambient-rust) 0%, transparent 60%);
  filter: blur(120px);
  opacity: 0.4;
  border-radius: 50%;
}
.app-shell, .app-shell > * { position: relative; z-index: 1; }
```

暗色下 ambient 自动用对应的深色变量，强度退化为不喧宾。

## 5. Sidebar v3

### 5.1 视觉结构

```
┌─────────────────────────┐
│ [mark] 智课问答  ‹       │   brand row（折叠时只剩 mark）
│       教学知识平台 v0.7 │
├─────────────────────────┤
│  ▣ 工作台              │   单项 section
│                         │
│  生产                   │   section h4（uppercase / xs）
│  ┃ ▣ 课程         12   │   active rail（左侧 3px 暖色）
│    ▣ 知识库        6   │
│                         │
│  运维                   │
│    ▣ 问答会话           │
│    ▣ 检索日志           │
│    ▣ 知识库验证         │
│                         │
│  设置                   │
│    ▣ 用户与权限         │
│    ▣ 系统健康           │
│    ▣ 审计日志           │
├─────────────────────────┤
│ [何] 何启航            │   status card
│      管理员·全平台      │
│ ─────────────────────── │
│ ● API   ● DB  ⚠ 任务  │   health pills
└─────────────────────────┘
```

### 5.2 关键尺寸 / 间距

| 元素 | 展开态 | 折叠态 |
| --- | --- | --- |
| Sidebar 总宽 | 240px | 64px |
| Sidebar padding | 14px 12px | 14px 8px |
| Brand mark | 34×34 / 圆角 10 | 34×34（居中） |
| Section title | xs / 600 / tracking-wide / 14px 高 | `height:0; overflow:hidden;` 完全收起 |
| Nav item | 36px 高 / padding 8/10 / 圆角 9 | 48×40 / 圆角 11（居中） |
| Active rail | 3px 宽 / 36px 高 / `left:-12px` | 3px / 40px / `left:-8px` |
| Status card | padding 14 / 圆角 14 | padding 8（仅头像居中） |

> **注意**：上述数值在第 4.1 节字号上调后整体已经放过 1px 余量，与新 base / md 字号节奏对齐。

**关键容器约束**（cc 实现时必读）：

- `.sb-list` 必须 `position: relative; overflow: visible;` —— rail 用 `position: absolute; left: -12px;` 才不会被裁切。
- `.sidebar` 也必须 `overflow: visible;`（折叠态 toggle 需要溢出右边缘 `right: -13px`）。
- `.sb-section h4` 折叠态除了 `height: 0` 还要 `overflow: hidden; margin-bottom: 0;`，否则文字会从分组顶部漏出。
- 滚动容器是 `.sb-body`（中段 nav 列表的可滚区），不是 `.sidebar` 本身；`.sb-body { overflow-y: auto; overflow-x: visible; }` —— x 必须 visible，否则展开态 rail 也会被截掉。

### 5.3 图标（9 项）

全部使用 24×24 viewBox / `stroke-width:1.6` / `currentColor` 的线性图标。Hover 时通过 CSS 把 stroke 加粗到 1.95，制造"图标变实"的微反馈。

| 路由 | 图标语义 | 备注 |
| --- | --- | --- |
| 工作台 | 田字格仪表盘 | 4 矩形组合 |
| 课程 | 闭合书本 + 内文横线 | 用闭合书本而非"开本"，避免视觉与"知识库"撞概念 |
| 知识库 | 数据库圆柱 | 椭圆 + 二次贝塞尔分层（避免相对弧线渲染断点） |
| 问答会话 | 对话气泡 | |
| 检索日志 | 横线列表 | |
| 知识库验证 | 盾牌 + 对勾 | |
| 用户与权限 | 双人 | |
| 系统健康 | 心电波形 | |
| 审计日志 | 文件 + 横线 | |

具体 SVG path 在视觉走查 `sidebar-v3.html` 中已固化，落地时直接 inline 到 Vue 组件。

### 5.4 Active rail（暖色色条 + 呼吸光晕）

- 展开态：`position:absolute; left:-12px; width:3px; height:36px;` 渐变 `--ckqa-accent → --ckqa-accent-strong`，外发光 `box-shadow: 0 0 12px rgb(217 119 87 / 50%)`。
- 切换路由时，rail 通过 JS 计算目标 `top` 用 `--ckqa-ease-spring` + `--ckqa-duration-base` 平滑滑动（下文 6.1 节说明实现）。
- 折叠态：`left:-8px; height:40px;`，呼吸由 active item 的 `box-shadow` 动画承担，rail 本身不呼吸（避免叠加噪音）。

### 5.5 折叠态完整规则

```scss
.sidebar.is-collapsed {
  width: 64px;
  padding: 14px 8px;

  .sb-brand-text  { max-width: 0; opacity: 0; }
  .sb-section h4  { height: 0; opacity: 0; }
  .sb-label, .sb-count { max-width: 0; opacity: 0; }
  .sb-id          { max-width: 0; opacity: 0; }
  .sb-health      { height: 0; padding: 0; opacity: 0; border: none; }

  .sb-item {
    width: 48px; height: 40px;
    padding: 0;
    margin: 0 auto 3px;
    justify-content: center;
    border-radius: 11px;
    animation: collapse-pop 350ms var(--ckqa-ease-spring) backwards;
  }
  .sb-list .sb-item:nth-of-type(1) { animation-delay: 0ms; }
  .sb-list .sb-item:nth-of-type(2) { animation-delay: 30ms; }
  .sb-list .sb-item:nth-of-type(3) { animation-delay: 60ms; }

  .sb-item.is-active {
    animation:
      collapse-pop 350ms var(--ckqa-ease-spring) backwards,
      breathe-glow 4500ms ease-in-out 500ms infinite;
  }
}

@keyframes collapse-pop {
  0%   { opacity: 0; transform: translateY(6px) scale(0.85); }
  70%  { opacity: 1; transform: translateY(-1px) scale(1.05); }
  100% { opacity: 1; transform: translateY(0)    scale(1);    }
}

@keyframes breathe-glow {
  0%, 100% {
    box-shadow:
      inset 0 0 0 1px rgb(217 119 87 / 20%),
      inset 0 1px 0 rgb(255 255 255 / 50%),
      0 0 0 0 rgb(217 119 87 / 0%);
  }
  50% {
    box-shadow:
      inset 0 0 0 1px rgb(217 119 87 / 40%),
      inset 0 1px 0 rgb(255 255 255 / 60%),
      0 0 0 5px rgb(217 119 87 / 8%);
  }
}
```

折叠按钮（toggle）见第 7 节。

### 5.6 Status card

```scss
.sb-status {
  margin-top: 12px;
  padding: 14px;
  background: var(--ckqa-surface-glass-strong);
  backdrop-filter: blur(12px);
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 14px;
  box-shadow:
    0 1px 1px rgb(28 26 23 / 4%),
    0 6px 16px rgb(110 70 40 / 8%),
    inset 0 1px 0 rgb(255 255 255 / 60%);
}
.sb-avatar { /* 34×34 圆形 / 暖色渐变 / 内 highlight / 阴影 */ }
.sb-id     { /* 13/600 用户名 + 11.5/text-muted 角色范围 */ }
.sb-health {
  display: flex; gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgb(232 226 216 / 50%);
  & > .sb-health-pill { /* 9.5px 文字 + 5px 圆点 + 颜色门类 */ }
}
```

折叠态把 `.sb-id` 与 `.sb-health` 全部 `max-width:0 / height:0`，仅保留居中头像。

## 6. Sidebar 实现切口

### 6.1 状态管理

折叠态是局部 UI 状态，不进 Pinia：

- `SideNavigation.vue` 内部 `const collapsed = ref(false)`
- 通过 `provide / inject` 暴露给 `ConsoleLayout`，layout 根节点上挂 `:data-sb-collapsed` 同步给 main 区，main 区用 CSS 变量 `--sb-w` 驱动 `padding-left`，sidebar 切换折叠时同步写变量（避免依赖 `:has()`）。
- 持久化用 `localStorage.setItem('ckqa.sidebar.collapsed', '1')`，初始化时同步读取。
- 全局快捷键：`⌘ \`（Mac）/ `Ctrl \`（Windows / Linux）触发折叠 / 展开。**不用 `[` / `]`** —— 这两个键在中文输入法激活时会被 IME 截获、教师用户切到中文后快捷键失效。
- 快捷键监听必须做 IME 守卫：`if (event.isComposing || event.keyCode === 229) return;`，避免在拼音输入候选词时误触。
- 与已有顶栏 ⌘K（命令面板）不冲突；快捷键文案在命令面板里展示成 "折叠侧栏 ⌘ \\"。

### 6.2 Active rail 滑动

抽出 `useActiveRail(listEl, activeSelector, { collapsedRef })` 组合式：

- 监听路由变化 / 折叠态变化。
- 用 `getBoundingClientRect()` 计算 active item 相对 list 的 top，写到 `rail.style.top`。
- **动画期间禁止重算**：折叠 / 展开切换瞬间，sidebar 进入 `--ckqa-duration-glass`（350ms）的过渡，期间 rect 是过渡中间值。`useActiveRail` 监听 `collapsedRef` 变化时，必须 `await new Promise(r => setTimeout(r, 360))`（多 10ms 余量）后再读 rect，否则 rail 会停在过渡中间位置。
- 路由切换的常规重算用 `nextTick()` 兜底，等待 DOM diff 完成后再测量。
- 切换 transition 在 CSS 上：`transition: top var(--ckqa-duration-base) var(--ckqa-ease-spring), height var(--ckqa-duration-glass) var(--ckqa-ease-glass), left var(--ckqa-duration-glass) var(--ckqa-ease-glass)`。
- 首次挂载（mounted 之前 active item 高度为 0）时 rail 初始 `opacity: 0`，等首测计算成功后才 `opacity: 1`，避免从 (0,0) 起步的闪烁。

逻辑放进 `src/components/shell/active-rail-model.js`（与 `navigation-model.js` 同级），用 `node:test` 单测 top 计算（mock DOM rect）。

### 6.3 Brand 改名

- `AppTopbar.vue:34` `<span>CKQA Console</span>` → `<span>智课问答</span>`
- `AuthLayout.vue:9` 同上
- `SideNavigation.vue` 新增 brand 区文字，主标"智课问答"，副标"教学知识平台 · v{appVersion}"
- `index.html` `<title>` 与 `package.json` 的 `description` 不动（保留 CKQA 工程标识）。
- 新建 `src/copy/brand.js` 集中导出：

```js
export const BRAND = {
  name: '智课问答',
  tagline: '教学知识平台',
  version: __APP_VERSION__, // vite define 注入（已存在）
};
```

## 7. 按钮 / Toggle 反馈语言

### 7.1 通用按钮基础（在 `components.scss` 增量）

所有 `Ck*` 按钮 / 可点击容器接入这套基线：

```scss
.ck-pressable {
  transition:
    background var(--ckqa-duration-fast),
    color var(--ckqa-duration-fast),
    transform var(--ckqa-duration-base) var(--ckqa-ease-spring),
    box-shadow var(--ckqa-duration-base);
  will-change: transform;

  &:hover  { transform: translateY(-1px); }
  &:active { transform: translateY(0) scale(0.94);
             transition: transform var(--ckqa-duration-press) ease-out; }
  &:focus-visible { outline: none; box-shadow: var(--ckqa-focus-ring); }
}
```

### 7.2 Sidebar Toggle

展开态（嵌在 brand row 末尾）：

```scss
.sb-toggle {
  width: 26px; height: 26px;
  background: linear-gradient(180deg, rgb(255 255 255 / 85%), rgb(255 252 246 / 70%));
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgb(28 26 23 / 4%), inset 0 1px 0 rgb(255 255 255 / 70%);
}
.sb-toggle:hover {
  background: linear-gradient(180deg, #fff, rgb(255 250 242 / 95%));
  color: var(--ckqa-accent-strong);
  border-color: rgb(217 119 87 / 35%);
  transform: translateY(-1px);
  box-shadow:
    0 0 0 4px rgb(217 119 87 / 8%),
    0 4px 10px rgb(110 70 40 / 12%),
    inset 0 1px 0 rgb(255 255 255 / 85%);
}
.sb-toggle:active {
  transform: translateY(0) scale(0.9);
  transition: transform var(--ckqa-duration-press) ease-out;
}
```

折叠态（脱出 brand row，悬浮在 sidebar 右边缘）：

```scss
.sidebar.is-collapsed .sb-toggle {
  position: absolute;
  /* 与 brand mark 中心对齐：
     mark 顶部 y = sidebar 14 + brand 6 = 20
     mark 中心 y = 20 + 34/2 = 37
     toggle 28px 高，top = 37 - 14 = 23 */
  top: 23px;
  right: -13px;
  width: 28px; height: 28px;
  border-radius: 9px;
  background: linear-gradient(180deg, #fff, #fdf9f1);
  box-shadow:
    0 4px 14px rgb(110 70 40 / 18%),
    0 1px 2px rgb(28 26 23 / 6%),
    inset 0 1px 0 rgb(255 255 255 / 95%);
  z-index: 20;
}
.sidebar.is-collapsed .sb-toggle:hover {
  background: linear-gradient(180deg, var(--ckqa-accent), var(--ckqa-accent-strong));
  color: #fff;
  border-color: transparent;
  transform: translateY(-1px) scale(1.08);
  box-shadow:
    0 0 0 4px rgb(217 119 87 / 18%),
    0 6px 16px rgb(217 119 87 / 40%),
    inset 0 1px 0 rgb(255 255 255 / 35%);
}
.sidebar.is-collapsed .sb-toggle svg {
  transform: rotate(180deg);
  transition: transform var(--ckqa-duration-base) var(--ckqa-ease-spring);
}
.sidebar.is-collapsed .sb-toggle:hover svg { transform: rotate(180deg) scale(1.15); }
```

> **关键 bug 修复**：旧实现 `top:46px` 与 brand mark（占用 y=20–54px）在 y=46–54px 区间重叠；新实现 `top:23px` 让 28px 高的 toggle 中心精确落在 mark 中心 y=37px，彻底消除重叠。

### 7.3 折叠态导航项交互

```scss
.sidebar.is-collapsed .sb-item {
  &:hover {
    background: rgb(255 252 246 / 92%);
    transform: scale(1.07);
    box-shadow:
      0 0 0 1px rgb(217 119 87 / 18%),    /* 内 ring */
      0 0 0 5px rgb(217 119 87 / 8%),     /* 外 ring */
      0 6px 16px rgb(110 70 40 / 12%),
      inset 0 1px 0 rgb(255 255 255 / 80%);
  }
  &:hover .sb-icon { color: var(--ckqa-accent-strong); transform: scale(1.05); }
  &:hover .sb-icon svg { stroke-width: 1.95; }
  &:active { transform: scale(0.92); }
}
```

> Hover ring 使用双层 `box-shadow` 实现：内 ring（1px accent 18%）+ 外 ring（5px accent 8%）。比单层 ring 更有"光晕"感。

### 7.4 命令面板触发器（顶栏 ⌘K）

沿用 M2 已有 `CkCommandPalette`，只把按钮接入 `.ck-pressable`：hover 上浮 1px、active 按压。不动其交互逻辑。

## 8. 卡片风格（Style A · Glass）

### 8.1 通用卡片基底

新增 `src/styles/components.scss` 内 `.ck-glass-card` 工具类，给所有 dashboard / 列表卡片用：

```scss
.ck-glass-card {
  position: relative;
  background: var(--ckqa-surface-glass);
  backdrop-filter: blur(var(--ckqa-glass-blur)) saturate(var(--ckqa-glass-saturate));
  -webkit-backdrop-filter: blur(var(--ckqa-glass-blur)) saturate(var(--ckqa-glass-saturate));
  border: 1px solid var(--ckqa-border-glass);
  border-radius: 16px;
  box-shadow: var(--ckqa-shadow-card);
  overflow: hidden;
  transition:
    transform var(--ckqa-duration-base) var(--ckqa-ease-spring),
    box-shadow var(--ckqa-duration-base),
    background var(--ckqa-duration-base);
}
.ck-glass-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 50%;
  background: var(--ckqa-highlight-top);
  border-radius: 16px 16px 0 0;
  pointer-events: none;
}
.ck-glass-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--ckqa-shadow-card-hover);
  background: var(--ckqa-surface-glass-strong);
}
.ck-glass-card.is-active {
  border-color: rgb(217 119 87 / 50%);
  box-shadow: 0 0 0 3px rgb(217 119 87 / 12%), var(--ckqa-shadow-card);
}

/* 退化方案：低端 GPU / 旧浏览器不支持 backdrop-filter 时
 * 用实色 surface 兜底，保留顶部 highlight 与边框，视觉层级仍可分辨 */
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .ck-glass-card {
    background: var(--ckqa-surface);
  }
  .ck-glass-card:hover {
    background: var(--ckqa-surface-strong);
  }
}
```

### 8.2 CkPipelineHero 升级

5 段卡片改用 `.ck-glass-card`，主数字升到 `text-hero`（30px / 600 / `tnum`），底部从"hint 文字"改为"微趋势条"：

```html
<div class="ck-glass-card is-active">
  <div class="p-label">课程<span class="pulse" v-if="active" /></div>
  <div class="p-value">{{ count }}</div>
  <div class="p-trend">
    <span v-for="(h, i) in trend" :key="i" :style="{ height: `${h}%` }" />
  </div>
</div>
```

`pulse` 是单个 6px 圆点 + `keyframes pulse 2s` 缩放透明度；趋势条 6 段渐变（rgb(217 119 87 / 50%) → 18%）。当 trend 数据缺失时不渲染条形（与现有 fallback 行为一致）。

### 8.3 ActivityFeed / TaskList / 快捷入口

外壳从直接铺背景改为 `.ck-glass-card`，padding 16→18，title 用 md / 500，内容字号同步上调到 base。

`CkTaskList` 进度条改成发光渐变：

```scss
.ck-task-progress {
  height: 4px; border-radius: 2px;
  background: rgb(232 226 216 / 60%);
  & > i {
    height: 100%;
    background: linear-gradient(90deg, var(--ckqa-accent), var(--ckqa-accent-strong));
    box-shadow: 0 0 8px rgb(217 119 87 / 30%);
    transition: width var(--ckqa-duration-glass) var(--ckqa-ease-glass);
  }
}
```

### 8.4 Fallback Banner 收编

把现在贴顶部的黄色长条改成 hero 下方 inline 圆角 pill：

```html
<CkPageHero ... />
<div v-if="hasFallback" class="ck-fallback-pill">
  <i class="ck-spinner" /> 正在以分资源接口聚合数据
</div>
```

```scss
.ck-fallback-pill {
  display: inline-flex; align-items: center; gap: 8px;
  margin-top: 12px;
  padding: 6px 12px;
  font-size: var(--ckqa-text-sm-size);
  background: var(--ckqa-running-soft);
  color: var(--ckqa-running);
  border-radius: 999px;
  border: 1px solid rgb(192 138 58 / 25%);
}
.ck-spinner {
  width: 12px; height: 12px;
  border: 2px solid rgb(192 138 58 / 30%);
  border-top-color: var(--ckqa-running);
  border-radius: 50%;
  animation: spin 800ms linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
```

## 9. Dashboard 信息层级修正

### 9.1 Hero 重复信息处理

- 面包屑 `工作台 / 工作台` → 单段 `工作台`（M1+M2 的 `console-breadcrumb-model.js` 在 `meta.section` 与 page title 相同的 leaf 路由上不重复输出）。
- `CkPageHero` 移除 `eyebrow` slot 在工作台路由的注入；仅保留 H1 + subtitle。
- subtitle 文案改为强调数字（不是简单一句话）：

```
欢迎回来，今天有 <b>{n} 个进行中任务</b> 和 <b>{m} 次本周问答</b>。
```

**数据源对应（cc 实现时直接接入，不要重新猜字段）**：

| 占位 | 来源 | 字段路径 | 缺值兜底 |
| --- | --- | --- | --- |
| `{n}` 进行中任务数 | M3 已实现的 `useDashboardSummary` | `summary.tasks.in_progress`（首选）/ 降级时 `tasksFeed.value.filter(t => t.status === 'running').length` | 0 时改为"暂无进行中任务" |
| `{m}` 本周问答次数 | 同上 | `summary.qa.weekly_count` / 降级时 `summary.qa.recent.length` | 0 时改为"本周还没有问答" |

完整 fallback 模板见 `src/copy/admin.js` 的 `dashboard.heroSubtitle.*` 文案常量（在 P3 一并补齐）。subtitle 文案不直接写在组件，全部走 copy 常量。

### 9.2 空态视觉

`CkActivityFeed` / `CkTaskList` / `CkPipelineHero` 三处空态：

- 不再渲染"超小圆点 + 一行小字"，改用 `CkEmptyState` 居中：32×32 图标（线性）+ md 标题 + sm 副标题 + 14px 间距。
- 卡片高度统一 `min-height: 200px`，避免空数据时塌缩。

### 9.3 快捷入口

从被淹没的"次级文字 grid"提升为带图标的卡片：

```html
<div class="ck-quick-actions">
  <a v-for="a in actions" :key="a.id" class="ck-glass-card ck-quick-action" :href="a.to">
    <span class="qa-icon"><svg .../></span>
    <span class="qa-label">{{ a.label }}</span>
    <span class="qa-hint">{{ a.hint }}</span>
  </a>
</div>
```

3–4 列 grid / gap 12 / hover 整卡上浮。

**`CkQuickActions` 组件契约（cc 实现时严格遵守）**：

```ts
// src/components/common/CkQuickActions.vue
defineProps({
  actions: {
    type: Array,
    required: true,
    /* 每项形状：
       {
         id:    string,        // 用作 key、analytics 标识
         label: string,        // 主标题（md / 500）
         hint:  string,        // 副标题（sm / text-muted）
         icon:  string,        // 图标名，对应 sidebar/icons 同名 SVG（复用图标库）
         to:    string,        // 路由 path，由组件做 router.push（不做 href 跳转）
       }
    */
  },
});
defineEmits(['select']);  // 可选：分析埋点上钩
```

数据从 `src/copy/admin.js` 新增 `dashboard.quickActions` 常量数组注入：

```js
dashboard: {
  quickActions: [
    { id: 'create-course',  label: '新建课程',     hint: '从课程目录开始',  icon: 'book',     to: '/app/courses/new' },
    { id: 'upload-pdf',     label: '上传资料',     hint: 'PDF / DOCX 解析', icon: 'file',     to: '/app/courses' },
    { id: 'build-kb',       label: '构建知识库',   hint: '4 步引导式向导', icon: 'database', to: '/app/knowledge/new' },
    { id: 'verify-kb',      label: '验证知识库',   hint: '冒烟问答抽样',    icon: 'shield',   to: '/app/qa-smoke' },
  ],
},
```

`icon` 字段映射到 P4a 新建的 `src/components/shell/icons/` SVG 组件库（与 sidebar 共用）。

## 10. 文件清单（落地切口）

### 10.1 修改

```
src/styles/tokens/_typography.scss     # 字号 token 上调 + 新增 hero/display + tracking
src/styles/tokens/_colors.scss         # 新增 glass surface / ambient / highlight 变量（亮+暗）
src/styles/tokens/_motion.scss         # 新增 ease-spring / ease-glass / duration-press / duration-glass
src/styles/tokens/_shadow.scss         # 新增 shadow-card / shadow-card-hover
src/styles/components.scss             # 新增 .ck-glass-card / .ck-pressable / .ck-fallback-pill / .ck-spinner
src/styles/index.scss                  # body::before / body::after ambient 光斑

src/components/shell/SideNavigation.vue          # v3 完整改造（图标 / rail / status / collapse）
src/components/shell/AppTopbar.vue               # CKQA Console → 智课问答
src/layouts/AuthLayout.vue                       # 同上
src/layouts/ConsoleLayout.vue                    # main 区根节点接 ambient + sidebar collapse 状态

src/components/common/CkPageHero.vue             # 字号映射到新 token / 移除 eyebrow 重复
src/components/common/CkPipelineHero.vue         # 改用 .ck-glass-card / 主数字 hero / 微趋势条
src/components/common/CkActivityFeed.vue         # 改用 .ck-glass-card / 空态接 CkEmptyState
src/components/common/CkTaskList.vue             # 改用 .ck-glass-card / 发光进度条
src/components/common/CkEmptyState.vue           # 字号上调对齐 / 容器 min-height
src/views/dashboard/DashboardPage.vue            # fallback banner 改 pill / 快捷入口卡片化

src/components/shell/navigation-model.js         # （视实现可不动）
src/layouts/console-breadcrumb-model.js          # leaf 路由与 section 同名时去重
```

### 10.2 新建

```
src/copy/brand.js                                  # BRAND 常量（name / tagline / version）
src/components/shell/active-rail-model.js          # rail top 计算
src/components/shell/active-rail-model.test.js
src/components/shell/sidebar-collapse-model.js     # 折叠态读写 + 快捷键
src/components/shell/sidebar-collapse-model.test.js
src/components/shell/icons/                        # 9 个 sidebar 线性图标 .vue（或单个 SidebarIcon.vue + name prop）
src/components/common/CkQuickActions.vue           # 工作台快捷入口
src/styles/mixins/_typography.scss                 # 增加 @mixin hero / @mixin display
```

### 10.3 不动（明确边界）

```
src/api/                # 数据契约不变
src/axios/              # 拦截器不变
src/stores/             # 仅可能新增 sidebar 折叠 store（如选 store 方案），其余不动
src/router/routes.js    # 路径 / 文案不变
e2e/                    # 文案断言已用 data-test-id；本次只在视觉变化处补 selector
```

## 11. 测试与验收

### 11.1 单元测试（`node:test`）

| 模块 | 用例 |
| --- | --- |
| `active-rail-model` | 给定 list 高度 / 各 item rect / 当前 active idx，计算 rail top；折叠态 height=40 / 展开态 height=36 |
| `sidebar-collapse-model` | 切换、读取 localStorage、`prefers-reduced-motion` 时禁用动画 keyframe（CSS 层做 `@media`，逻辑层只读偏好） |
| `console-breadcrumb-model` | leaf 与 section 同名时 trim 重复 |
| `brand.js` | 常量 export 形状（轻测试） |

### 11.2 Playwright E2E

- `dashboard.spec.js`（已存在）补 case：折叠 / 展开 sidebar 后，主区域宽度变化、active rail 仍指向当前路由。
- 新增 `sidebar-collapse.spec.js`：键盘 `Ctrl+\` 折叠 / 展开（Playwright 用 `Meta+\` 模拟 Mac）、`event.isComposing` 守卫验证（模拟 IME 合成中不触发）、刷新后状态保留。
- 视觉走查：截图 1280×800 与 1024×768 两档，diff 上一版（容差 0.05）。

### 11.3 验收清单

- [ ] 所有修改文件 `pnpm run build` 通过、`pnpm run dev` 启动后页面无控制台报错。
- [ ] 字号上调后所有 `Ck*` 组件 AA 对比度不退化（暗色亦然）。
- [ ] Sidebar v3 折叠 / 展开动画流畅；连续切换 5 次不出现错位。
- [ ] 玻璃态卡片在 1280px 与 1024px 上 backdrop-filter 渲染正常；`@supports not` 分支在 Firefox 关闭 `layout.css.backdrop-filter.enabled` 时验证一次。
- [ ] `prefers-reduced-motion: reduce` 时所有动画退化为瞬时切换（无 transform 回弹、无 keyframe 循环）。
- [ ] **品牌清理验证**：
  - `grep -rn "CKQA Console" frontend/apps/admin-app/src/` 应无结果。
  - `grep -rn "CKQA" frontend/apps/admin-app/src/` 余下结果人工逐条 review，确认全部位于工程上下文（`api/client.js`、`axios/index.js`、错误对象、test snapshot 等），而非用户可见 UI（`<title>` / `aria-label` / toast / loading 文案 / meta description）。
- [ ] Active rail 在跨 section 切换路由时平滑滑过、不闪烁；首次挂载有渐显，无 (0,0) 起步抖动。
- [ ] 工作台 hero subtitle 显示真实数字而非纯文字；当 `summary.tasks.in_progress = 0` 时切换到"暂无进行中任务"文案。
- [ ] Fallback Banner 不再独占顶部一行，转为 hero 下方 inline pill。
- [ ] 折叠态下 toggle 按钮位置正确（`top: 23px`，与 28px mark 中心 y=37px 对齐），与 mark 不重叠。
- [ ] 折叠态下「课程」「知识库」图标渲染清晰、无 stroke 断点。
- [ ] 折叠快捷键 `⌘ \` / `Ctrl \` 在中文输入法激活时不误触；在拼音候选词浮动时按 `\` 不触发折叠。

### 11.4 prefers-reduced-motion 退化规则

`src/styles/components.scss` 末尾追加全局兜底，覆盖本期新增的所有 transition + keyframe：

```scss
@media (prefers-reduced-motion: reduce) {
  /* 1) 关闭所有过渡（保留 visibility/opacity 的瞬时切换） */
  *, *::before, *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
  }

  /* 2) 关闭本期新增的 keyframe 循环 */
  .sidebar.is-collapsed .sb-item,
  .sidebar.is-collapsed .sb-item.is-active,
  .ck-pulse-dot,
  .ck-spinner {
    animation: none !important;
  }

  /* 3) 卡片 hover 上浮、按压 scale 全部禁用 */
  .ck-glass-card:hover,
  .ck-pressable:hover,
  .ck-pressable:active,
  .sidebar.is-collapsed .sb-item:hover,
  .sidebar.is-collapsed .sb-item:active {
    transform: none !important;
  }
}
```

**注意**：`!important` 是必须的 —— 当前 sidebar / card 的 transform 写在元素直接选择器上，特异性高于通用 `*` 规则；不加 important 退化无效。`useActiveRail` 在 JS 层也读 `window.matchMedia('(prefers-reduced-motion: reduce)').matches`，命中时直接 set `top` 不走过渡。

## 12. 风险与依赖

| 风险 | 缓解 |
| --- | --- |
| 字号上调后部分既存页面（M3 之外的 ModulePage）版式溢出 | 本次先在 token 层完成，但上调步进保守（base 13→14.5 / 1px 一步），M5–M7 重做时再各自微调；ModulePage 内联硬编码字号在本期前置 patch 一次（替换 12px / 13px 为 var） |
| `backdrop-filter` 在低端 GPU 性能差 | 给玻璃卡片加 `@supports not (backdrop-filter: blur(20px))` 退化到 `--ckqa-surface` 实色 |
| Sidebar 折叠态对现有 ConsoleLayout 主区宽度计算有影响 | layout 用 CSS 变量 `--sb-w` 驱动 main padding-left；`SideNavigation` 切换折叠时同步写变量 |
| ambient 光斑对暗色不友好 / 过亮 | `[data-theme='dark']` 对应变量降到 0.15–0.2 不透明度；走查 |
| Active rail 滑动逻辑与路由切换时机错位（rail 闪烁） | rail 监听 `nextTick` 后再读 rect；transition 在 CSS 层兜底 |
| 旧 E2E 用 CSS class 选择器（如 `.dashboard-fallback-banner`）会失效 | 重命名走查；新 selector 全部用 `data-test-id` |

## 13. 实施分段建议（writing-plans 阶段拆）

> 本节仅为后续 plan 生成提供拆分参考，不约束最终任务粒度。

- **P1 Token & Brand**（半天）：4.1 字号 + 4.2 玻璃 surface + 4.3 动效曲线 + 4.4 ambient + brand.js + AppTopbar/AuthLayout 文字替换。
- **P2 通用样式工具类**（半天）：components.scss 新增 `.ck-glass-card` / `.ck-pressable` / `.ck-fallback-pill` / `.ck-spinner` + mixins + 11.4 prefers-reduced-motion 全局退化。
- **P3 Dashboard 与 Hero**（1 天）：CkPageHero / CkPipelineHero / CkActivityFeed / CkTaskList / CkEmptyState / DashboardPage 接入新样式 + fallback pill + CkQuickActions（含 copy 常量与 props 契约）。
- **P4a Sidebar v3 — 图标与 rail**（1 天）：9 个图标组件（`src/components/shell/icons/`）+ SideNavigation v3 展开态完整重写（图标 + 状态卡 + 文字层级）+ active-rail-model（含动画期间禁重算）+ 单测。展开态先达到产品级完成度，折叠态留 stub。
- **P4b Sidebar v3 — 折叠与状态**（半天）：sidebar-collapse-model（含 `⌘ \` 快捷键 + `event.isComposing` 守卫 + localStorage）+ 折叠态 CSS（toggle / collapse-pop / breathe-glow）+ ConsoleLayout 接 `--sb-w` CSS 变量 + Playwright `sidebar-collapse.spec.js`。
- **P5 测试与走查**（半天）：剩余单测补全 / Playwright 视觉 diff / 13 项验收清单核对 / 暗色与 1024px 走查。

**P4 拆 a/b 的理由**：active-rail-model 依赖真实 DOM rect（必须在 SideNavigation 已渲染后才能写单测）；sidebar-collapse-model 是纯逻辑（localStorage + 快捷键），可独立编写测试。耦合在一起会让两块的失败模式互相干扰，拆开 cc 实现路径更清晰。

合计 ~3.5 工作日。

## 14. 附录：与上游设计的兼容映射

| M1+M2 设计决策 | 本期变更 |
| --- | --- |
| 暖灰 + 暖橙 token 体系 | 保留；新增 glass / ambient 语义层 |
| `Ck*` 组件契约 | props/events 不变；样式与内部动效升级 |
| `工作台 / 生产 / 运维 / 设置` 信息架构 | 保留；sidebar v3 仍按此分组 |
| 默认亮色 + rust accent | 保留；玻璃态在暗色下退化 |
| 文案集中在 `src/copy/admin.js` | 保留；新增 `src/copy/brand.js`（独立切面） |
| Element Plus 主题层 | 不动 |
| 路由 path / SSE 通道 | 不动 |

## 15. 实现注意事项（cc 必读）

以下是评审过程中沉淀的"容易出错的边角"，cc 在 plan 拆解 / 任务执行时必须主动检查：

1. **`.sb-list` 容器约束**
   - `.sb-list { position: relative; overflow: visible; }` —— rail 用 `position: absolute; left: -12px;` 相对 `.sb-list` 定位，**不要相对 `.sb-item`**。
   - `.sb-body { overflow-x: visible; overflow-y: auto; }` —— y 滚动但 x 必须可溢出，否则展开态 rail 也会被裁切。
   - `.sidebar { overflow: visible; }` —— 折叠态 toggle 用 `right: -13px`，sidebar 本身不能裁。

2. **`useActiveRail` 动画期保护**
   - 监听 `collapsedRef` 变化时：`watch(collapsedRef, async () => { await new Promise(r => setTimeout(r, 360)); recompute(); })`。
   - 监听路由变化时：`router.afterEach(() => { nextTick().then(recompute); })`。
   - 首次挂载：`onMounted(() => { nextTick().then(() => { recompute(); rail.style.opacity = '1'; }); })`，rail 初始 `opacity: 0`。

3. **prefers-reduced-motion 必须覆盖 keyframe**
   - `@keyframes collapse-pop` 与 `@keyframes breathe-glow` 必须在 `@media (prefers-reduced-motion: reduce)` 块内用 `animation: none !important` 覆盖。
   - 仅 `transition-duration: 0.001ms` 不够 —— animation 与 transition 是两套机制。
   - 见 11.4 节完整退化代码。

4. **`.ck-glass-card::before` 的 highlight 在退化分支也要保留**
   - `@supports not (backdrop-filter: blur(1px))` 分支仅替换 `background`，**不要删除 `::before`** —— 顶部白色 highlight 是玻璃态视觉的一部分，在实色 surface 上同样有效（模拟"光从顶部洒下"）。

5. **`CkQuickActions` props 契约固定**
   - `actions: Array<{ id, label, hint, icon, to }>` —— 见 9.3 节完整定义。
   - 数据从 `src/copy/admin.js` 的 `dashboard.quickActions` 注入，**不要在组件内 hard-code 列表**。
   - `icon` 字段引用 P4a 新建的 `src/components/shell/icons/` 复用 sidebar 图标库，不重复定义 SVG。

6. **快捷键 IME 守卫**
   - `⌘ \` / `Ctrl \` 监听必须 `if (event.isComposing || event.keyCode === 229) return;` 在最前面。
   - 监听放在 `window` 上，`SideNavigation.vue` 的 `onMounted` 注册、`onBeforeUnmount` 清理。
   - 命令面板的快捷键提示文案在 `command-palette-model.js` 同步加一项 "折叠侧栏 ⌘ \\"。

7. **品牌 grep 不能只查 `CKQA Console`**
   - `grep -rn "CKQA" frontend/apps/admin-app/src/` 全量搜索后人工 review：
     - 允许保留：`api/`、`axios/`、`stores/auth.js`、`*.test.js` 内的 fixture 字符串。
     - 必须替换为「智课问答」：任何渲染到 DOM 的字符串（`<title>`、`<meta>`、aria-label、文案常量、错误提示、loading 文案）。
   - `package.json` / `README.md` / 路由 path / API endpoint 不动。

8. **Toggle top 数学**
   - 折叠态 toggle 是 28×28，`top: 23px` 让其中心 y=37 与 brand mark 中心对齐（`14 + 6 + 17 - 14 = 23`）。
   - 如果将来调整 sidebar padding-top（14）或 brand padding-top（6）或 mark height（34），重新算：`top = (sidebar_pt + brand_pt + mark_h/2) - toggle_h/2`。
   - 不要硬抄数字，记住公式。

## 16. 评审决议（2026-05-09）

第一轮设计稿评审中提出的 4 个开放问题已固化为以下决议，并已反向贯穿到第 4 / 6 / 13 节中：

| 问题 | 决议 | 落点章节 |
| --- | --- | --- |
| 字号 base 13→14.5 是否过激？ | **改为 base 14**（统一 1px 等差，避免 14.5 半像素子像素渲染问题）；hero 复用 3xl=30，删除独立的 `--ckqa-text-hero`。 | 4.1 |
| 玻璃态 `blur(20px) saturate(180%)` 是否合适？ | **降到 `blur(12px) saturate(160%)`**，并把 `--ckqa-surface-glass` 透明度从 0.55 提到 0.65 —— 暖灰底色对比度本身低，强模糊看不出层级；同时 GPU 压力更小，退化触发更少。封装成 `--ckqa-glass-blur` / `--ckqa-glass-saturate` token，方便整体调。 | 4.2 |
| 折叠快捷键 `[` / `]` 是否合适？ | **改为 `⌘ \` / `Ctrl \`**（VS Code / Linear 惯例，IME 不截获）；监听必须 `if (event.isComposing || event.keyCode === 229) return;` 守卫拼音候选词。 | 6.1、15.6 |
| P4 是否拆 P4a + P4b？ | **拆**。P4a：图标 + 展开态 + active-rail-model（1 天）。P4b：折叠态 + sidebar-collapse-model（半天）。耦合在一起会让两块失败模式互相干扰，拆开 cc 实现路径更清晰。 | 13 |

第一轮还修补了以下隐性漏洞（直接落到正文）：

- 4.1：`--ckqa-text-3xl` 与 `--ckqa-text-hero` 名字重叠 → 删除 hero token。
- 4.3：补全 M1 已有的 `--ckqa-duration-base/fast/slow` 说明，避免被误认为未定义。
- 5.2：补 `.sb-list / .sb-body / .sidebar` 的 overflow / position 容器约束。
- 5.5：`.sb-section h4` 折叠态补 `overflow: hidden`，避免文字溢出。
- 6.2：`useActiveRail` 增加动画期间禁重算（350ms 等待）+ 首次挂载渐显。
- 7.2：折叠态 toggle `top` 数学修正为 `23px`（28px 高 toggle 配 mark 中心 y=37）。
- 8.1：`@supports not` 退化分支显式写进代码块，不再只在风险表里口述。
- 9.1：hero subtitle 的 `{n}` / `{m}` 数据源对应 `summary.tasks.in_progress` / `summary.qa.weekly_count`，并指明降级路径。
- 9.3：`CkQuickActions` props 契约 + `dashboard.quickActions` 常量结构落定。
- 11.3：品牌 grep 改为先 `CKQA Console`、再全量 `CKQA` 人工 review，覆盖 aria-label / meta / toast 等隐藏文案。
- 11.4：新增 `prefers-reduced-motion` 全局退化规则，覆盖 transition + keyframe + transform。
- 15：新增"实现注意事项"章节，把 cc 容易踩的 8 个边角集中陈列。

如对决议或正文还有调整，告诉我；否则下一步我用 `superpowers:writing-plans` 把本文转成 P1–P5 的实施计划文件。
