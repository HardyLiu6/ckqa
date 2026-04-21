# 学员端前端视觉与页面组织重设计

- 日期：2026-04-21
- 范围：`frontend/apps/student-app/`
- 目标：将现有学员端原型（落地页、首页、问答、课程）统一重构为一套自洽的视觉体系——混合深色落地 + 亮色产品页、按模块多色系、毛玻璃 + 克制荧光，并补齐知识图谱、个人中心两个视觉壳，保证整个学员端看起来像一个"完整产品"而非多个独立原型

## 1. 背景

### 1.1 现状摘要

`frontend/apps/student-app/` 是 Vue 3 + Vite + Element Plus + Pinia + Vue Router 的学员端原型。路由树和页面骨架已初步成型，但视觉层面存在明显割裂：

1. 落地页 `views/layout/index.vue`（2401 行）采用深色 + 渐变光晕 + 粒子特效，偏营销科技风。
2. 首页 `views/index.vue`（1779 行）同样是深色 + 渐变，但交互和信息密度更接近一个产品仪表盘。
3. 问答页 `views/qa/index.vue`（1091 行）及其二级页使用亮色 + 侧边栏，偏工具感。
4. 课程页 `views/course/index.vue`（680 行）是亮色 + 紫色渐变大头图，偏营销卡片风。
5. `NavHeader.vue` 只有深色一版，目前仅在特定路由下才应显示，但 `App.vue` 中的相关条件未真正生效。
6. `views/author/` 目录为空，`src/components/` 下除 `NavHeader` 外几乎没有公共组件，设计 token 缺失。

上述问题意味着学员端在同一个仓库里呈现出三种以上的设计语言，用户连续浏览不同模块时会感觉像在多个产品之间切换。

### 1.2 技术约束

1. 必须保留 Vue 3 + Vite + Element Plus + Pinia + Vue Router 技术栈。
2. 必须保留 `pnpm-lock.yaml` 的依赖管理方式。
3. 已有 GSAP、AOS、Lenis 可用，本设计会在落地页继续使用 GSAP，简化对 AOS 的依赖。
4. 必须保持桌面端为第一优先（`≥ 1440px` 基准），平板做基础退化，手机端不在本次范围。

## 2. 设计目标与非目标

### 2.1 设计目标

1. **视觉一致性**：整个学员端看起来是一套产品，不同模块差异仅来自主色，而非完全不同的设计语言。
2. **产品差异化**：视觉层面能一眼看出这是"知识图谱问答"产品，而非通用学习平台。
3. **页面组织合理**：导航结构、模块边界、信息层级清晰；内容密集模块（如课程、问答）支持模块内副导航。
4. **可持续基础**：建立 design token、组件共享基础，后续任何新页面都能直接复用。

### 2.2 非目标（本次明确不做）

1. **不做代码层面的深度重构**：不重写 Pinia store 架构、不接入真实 API、不打通路由守卫、不做 TypeScript 迁移。
2. **不新建业务页面**：知识图谱和个人中心只做视觉壳，不接数据、不写业务逻辑。
3. **不做未规划模块的实现**：学习社区、学习分析、登录 / 注册等路由仍保持 coming-soon，但 `RouteState` 页面视觉会更新。
4. **不做移动端精细适配**：`< 768px` 仅保底可用，不做专门的响应式设计。
5. **不做暗色模式切换**：落地页深色 + 产品页亮色已是系统性安排，不提供手动切换。
6. **不在本次引入新的 UI 库或替换 Element Plus**：继续基于 Element Plus 做样式覆盖。

## 3. 整体方向

### 3.1 视觉基调：混合模式（落地深 + 产品亮）

落地页 `/` 保留深色科技风，作为访客进入的"第一印象"；其余所有登录后业务页（首页、问答、课程、知识图谱、个人中心、分析、社区）统一切到亮色学习台风格。

选择混合方向的原因：

1. 深色落地页保留"抓眼球"作用，访客转化率优势不丢。
2. 亮色产品页更利于长文阅读、表格、对话、图谱等实际使用场景。
3. 宣传和使用分离，两个场景各自做到位，而非互相让步。

被淘汰的方向：

- **全深色方向**：长内容阅读和表格类信息在深色下对比度难处理，不适合作为日常使用。
- **全亮色方向**：落地页失去冲击力，学员端整体显得偏保守。

### 3.2 多色系 + 统一设计语言

每个主要模块分配一个"魂色"，视觉差异仅体现在主色、光晕、激活态；其他设计元素（圆角、阴影、间距、字体、毛玻璃档位、动效曲线）在全站严格一致。

| 模块 | 主色 | 色号 | 视觉含义 |
| --- | --- | --- | --- |
| 首页 | Indigo | `#6366f1` | 主入口，延续品牌基调 |
| 课程 | Blue | `#2563eb` | 学习、可信、专业 |
| 问答 | Purple | `#9333ea` | AI、智能、探索 |
| 知识图谱 | Teal | `#0d9488` | 连接、结构、关系 |
| 社区 | Orange | `#ea580c` | 交流、热度、社交 |
| 学习分析 | Pink | `#db2777` | 数据、洞察、成长 |
| 个人中心 | 中性灰 + 场景色 | `#64748b` + 琥珀/柠檬 | 克制、当前场景微染 |

### 3.3 视觉质感：毛玻璃 + 克制的荧光

1. **毛玻璃**：分三档（light / base / strong），背景用 `backdrop-filter: blur(...)` 配合半透明白色。顶栏、主卡片、Modal 全部采用玻璃质感。
2. **荧光**：仅用在 hover / active / focus 等交互状态的"点睛"位置，而非默认态。主要形式：
   - 按钮悬浮：`box-shadow: 0 8px 24px rgba(模块色, 0.5), 0 0 0 3px rgba(模块色, 0.15)`。
   - 输入框聚焦：外发光环。
   - 卡片悬浮：模块色 shadow 抬升。
   - 激活导航：白底胶囊 + 模块色文字 + 轻微外发光。
3. **克制原则**：一个屏幕内 glow 元素数量 `≤ 3`，避免"圣诞树"效果。

## 4. 设计 Token 系统

所有页面和组件的度量衡，落到 `src/styles/tokens/` 下的 SCSS 变量 + CSS 自定义属性。以下是权威数值。

### 4.1 颜色：模块主色阶（每模块 3 档）

每个模块主色提供三档浓度：`50` 极浅底（用于浅色区域、tag 底）、`500` 主色、`700` 悬浮/激活深态。

| 模块 | 50 | 500 | 700 |
| --- | --- | --- | --- |
| 首页 Indigo | `#eef2ff` | `#6366f1` | `#4338ca` |
| 课程 Blue | `#eff6ff` | `#2563eb` | `#1d4ed8` |
| 问答 Purple | `#faf5ff` | `#9333ea` | `#7e22ce` |
| 图谱 Teal | `#f0fdfa` | `#0d9488` | `#0f766e` |
| 社区 Orange | `#fff7ed` | `#ea580c` | `#c2410c` |
| 分析 Pink | `#fdf2f8` | `#db2777` | `#be185d` |

### 4.2 中性色阶（全站共用）

`white / 50 / 100 / 200 / 300 / 400 / 500 / 600 / 700 / 900` 共 10 档，采用 Tailwind Slate 序列：

```
white   #ffffff   卡片底
50      #f8fafc   页面底
100     #f1f5f9   分隔线、浅边框
200     #e2e8f0   默认边框
300     #cbd5e1   占位文字
400     #94a3b8   辅助说明
500     #64748b   次要文字
600     #475569   强调次要
700     #334155   标题辅助
900     #0f172a   主文字
```

### 4.3 语义色

| 语义 | 500 | 渐变（用于按钮） |
| --- | --- | --- |
| Success | `#10b981` | `linear-gradient(135deg, #10b981, #34d399)` |
| Warning | `#f59e0b` | `linear-gradient(135deg, #f59e0b, #fbbf24)` |
| Error | `#ef4444` | `linear-gradient(135deg, #ef4444, #f87171)` |
| Info | `#3b82f6` | `linear-gradient(135deg, #3b82f6, #60a5fa)` |

### 4.4 字体

**字体族**：Manrope + Noto Sans SC（主字体）· Space Grotesk（展示字体，仅用于落地页 Hero 和模块大标题）。

**加载策略**：使用 `@fontsource/manrope`、`@fontsource/noto-sans-sc`、`@fontsource/space-grotesk` npm 包本地打包，避免依赖 Google Fonts CDN。预加载 400/500/600/700 四个字重，Noto Sans SC 做常用 GBK 字符子集化。

**字号尺度**：

| 名称 | 字号 / 字重 / 行高 | 用途 |
| --- | --- | --- |
| display-xl | 32px / 800 / 1.2 | 页面主标题 |
| display-lg | 24px / 700 / 1.3 | 区块标题 |
| title-md | 18px / 600 / 1.4 | 卡片标题 |
| title-sm | 15px / 500 / 1.5 | 子标题 / 强调 |
| body | 14px / 400 / 1.6 | 正文 |
| body-sm | 13px / 400 / 1.5 | 辅助说明 |
| caption | 12px / 500 / 1.4 · uppercase · tracking 0.05em | 标签 / 小标题 |

### 4.5 圆角

| Token | 值 | 用途 |
| --- | --- | --- |
| radius-sm | 6px | 小标签、Tag |
| radius-md | 8px | 输入框 |
| radius-lg | 10px | 按钮 |
| radius-xl | 12px | 卡片 |
| radius-2xl | 16px | 大容器、面板 |
| radius-full | 999px | 胶囊、头像 |

### 4.6 阴影

| Token | 值 | 用途 |
| --- | --- | --- |
| shadow-xs | `0 1px 2px rgba(15,23,42,0.05)` | 边缘分层 |
| shadow-sm | `0 2px 8px rgba(15,23,42,0.06)` | 默认卡片 |
| shadow-md | `0 8px 24px rgba(15,23,42,0.08)` | 悬浮卡片 |
| shadow-lg | `0 16px 48px rgba(15,23,42,0.12)` | Modal / Popover |
| glow-{模块} | `0 8px 32px rgba(模块色, 0.25)` | hover / 激活荧光 |

### 4.7 毛玻璃

| Token | 背景 | blur | 边框 | 用途 |
| --- | --- | --- | --- | --- |
| glass-light | `rgba(255,255,255,0.5)` | `12px` | `rgba(255,255,255,0.6)` | 普通悬浮卡片 |
| glass-base | `rgba(255,255,255,0.7)` | `20px` | `rgba(255,255,255,0.8)` | 主卡片、顶栏 |
| glass-strong | `rgba(255,255,255,0.88)` | `28px` | `rgba(255,255,255,0.95)` | Modal、Dropdown |

深色场景（落地页）对应变体：背景改 `rgba(15,15,26,{0.5|0.7|0.88})`，边框改 `rgba(255,255,255,{0.08|0.12|0.15})`。

### 4.8 间距

基准 4px，常用 9 档：`4 / 8 / 12 / 16 / 24 / 32 / 48 / 64 / 96`。SCSS 变量命名采用 `$space-1` 到 `$space-24`（以 4 为单位）。

### 4.9 动效

**时长**：

| Token | 值 | 用途 |
| --- | --- | --- |
| duration-instant | 100ms | 按钮按下、颜色切换 |
| duration-fast | 200ms | hover、聚焦、标签切换 |
| duration-base | 300ms | 卡片悬浮抬升、弹出 |
| duration-slow | 500ms | Modal、大型切换 |

**曲线**：

| Token | 值 | 用途 |
| --- | --- | --- |
| ease-out | `cubic-bezier(0.22, 1, 0.36, 1)` | 出场、悬浮（默认） |
| ease-in-out | `cubic-bezier(0.65, 0, 0.35, 1)` | 往返、过渡 |
| ease-snap | `cubic-bezier(0.4, 0, 0.2, 1)` | 确认、吸附 |
| ease-spring | `cubic-bezier(0.34, 1.56, 0.64, 1)` | 磁吸按钮、弹性复位 |

### 4.10 响应式断点

| 断点 | 宽度区间 | 处理 |
| --- | --- | --- |
| bp-desktop | ≥ 1440px | 设计基准 |
| bp-laptop | 1024–1440px | 密度微调，完整支持 |
| bp-tablet | 768–1024px | 基础退化：左侧导航变抽屉、三栏变两栏 |
| bp-mobile | < 768px | 不做精细适配，保底可用（未来 scope） |

## 5. 壳层与导航结构

### 5.1 AppLayout 三种壳层模式

通过 `route.meta.layout` 决定渲染哪一种壳：

1. **Landing 模式**（`layout: 'landing'`）：不渲染 AppLayout，直接全屏 RouterView。仅 `/` 落地页使用。
2. **Product 模式**（`layout: 'product'`，默认）：顶部渲染 NavHeader，下方 RouterView 全宽。适用：首页、学习分析、学习社区等顶栏 + 全宽内容页。
3. **Module 模式**（`layout: 'module-xxx'`）：在 Product 模式之上嵌入一层模块副 Layout，包含左侧副导航 + 主内容。适用：课程、问答、知识图谱、个人中心四个模块。

### 5.2 NavHeader · 全站顶栏（居中版）

**结构**：`grid-template-columns: 1fr auto 1fr` 三分，确保中间的模块导航真正居中。

- **左**（justify-self: start）：Logo 图标（36×36 渐变方块 + glow）+ "智课问答"（Space Grotesk 18px 700）。
- **中**（justify-self: center）：模块导航整体包裹在胶囊容器中——背景 `rgba(248,250,252,0.6)`，padding 4px，`radius-full`。每一项默认 `color: slate-500`，激活态切换为白底胶囊 + 模块色文字 + `box-shadow: 0 1px 3px rgba(模块色,0.1), 0 0 0 1px rgba(模块色,0.15), 0 0 16px rgba(模块色,0.15)`。
- **右**（justify-self: end）：全站搜索输入框（`⌘K`/`Ctrl+K` 呼出 Modal 级搜索）+ 通知铃铛（带红点 Badge）+ 用户头像（36×36 胶囊，下拉菜单：个人中心/设置/退出）。

**规格**：

- 高度 `64px`。
- 左右内边距 `32px`。
- 背景默认 `glass-base`（`rgba(255,255,255,0.8) + blur(20px)`）。
- 分隔底线 `rgba(229,231,235,0.6)`。
- 滚动行为：页面 `scrollY > 80px` 时背景透明度由 `0.8` 过渡到 `0.95`，`shadow-xs` 提升到 `shadow-sm`。

### 5.3 Module 子导航（四个模块）

**公共规格**：

- 背景：`rgba(255,255,255,0.6) + blur(16px)`（浅毛玻璃）。
- 宽度默认 `220px`（问答模块为 `240px` 因需展示会话摘要）。
- 内边距 `16px 12px`。
- 分组标题采用 `caption` 样式（12px / 500 / uppercase / tracking 0.05em / color slate-400）。
- 激活项：模块 50 底色 + 模块 500 文字 + `font-weight: 600`。
- `< 1200px` 宽时副导航收成 60px 纯图标条。
- `< 1024px` 宽时变成抽屉式，顶栏左端增加菜单按钮呼出。

**四个模块各自的内容**：

1. **课程**（`/course/*`，蓝）：全部课程 / 我的课程 / 收藏课程 / 学习报告（本次"学习报告"指向占位）。
2. **问答**（`/qa/*`，紫）：顶部"+ 新建对话"按钮 + 历史会话列表（每条显示标题 + 条数 + 时间 + 学科 tag）。可折叠成 60px 条。
3. **知识图谱**（`/knowledge/*`，青）：图谱浏览 / 知识检索 + 学科多选（chip 复选）+ 节点类型色例（概念/实例/错题）。
4. **个人中心**（`/user/*`，中性灰 + 场景色）：顶部个人卡（头像 + 昵称 + 学院年级）+ 个人资料 / 账号设置 / 消息通知（琥珀色图标 + 数字 Badge）/ 我的收藏（柠檬色图标）。

### 5.4 RouteState · 统一状态页

所有未开放 / 404 / 403 / 500 等状态页使用同一组件 `src/views/status/RouteState.vue`，视觉更新后结构：

- 毛玻璃状态卡（`glass-base`）+ 与状态类型对应的背景光晕。
- 居中图标（72×72 圆角方块 + 外发光环）+ 状态标签 chip + 标题 + 说明 + 双按钮（主 CTA 返回对应目标，次按钮返回上一页）。

**状态配色**：

| 状态 | 色系 | 图标 | 标签文案 |
| --- | --- | --- | --- |
| coming-soon | Indigo `#6366f1` | ⏳ | "未开放 · Coming Soon" |
| 404 | Amber `#f59e0b` | 🧭 | "页面不存在" |
| 403 | Rose `#e11d48` | 🔒 | "暂无权限" |
| 500 | Orange `#ea580c` | ⚠ | "页面暂不可用" |

## 6. 逐页设计

### 6.1 落地页（深色 + Indigo）

**保留**：深色基调、Hero + 特性展示 + 数据统计 + CTA 的整体叙事结构。

**改动**（编号对应 §7.1 动效表）：

- **动效 ①｜动态知识节点云**：删除原有格子背景、多数 floating-shape。以 Canvas 绘制 Hero 背景——约 20 个节点、`≤ 40` 条连线，节点缓慢漂浮（每帧 `0.3–0.6px`），鼠标 `120px` 半径内节点放大 `1.2x`、连线亮度从 `0.3 → 0.9`。节点色使用 Indigo `#6366f1` + 辅助色 `#c4b5fd`。
- **动效 ②｜Pin-scroll 特性流**：`#showcase` 段落用 GSAP ScrollTrigger pin，垂直钉住 `300vh`；三张大字报（问答 / 知识图谱 / 课程学习）横向切换，每张停留 `100vh`。切换曲线 `ease-out`。
- **动效 ③｜磁吸 CTA + 涟漪**：主 CTA（"立即体验"、"观看演示"）在鼠标进入外扩 `80px` 圆时磁吸跟随，最大位移 `8px`，缓动 `ease-spring`；点击时从点击点发出 `500ms` 扩散到 `300px` 的涟漪后淡出。
- **动效 ⑥｜特性卡 3D 倾斜**：特性卡根据鼠标在卡内位置产生 `rotateX/Y max ±8deg` 的 3D 倾斜，恢复 `ease-snap 300ms`。触控设备全禁用。
- **删除清单**：自定义光标、过多 floating-shape、通用 AOS fade、grid-lines 格子背景。
- **延后项**（见 §8）：动效 ④ 标题碎聚、动效 ⑤ 滚动光带 Ribbon。

**字体**：Hero 主标题切换到 Space Grotesk 700 + letter-spacing `-0.02em`。

### 6.2 首页（Indigo，Product Layout）

**整体结构**：

1. **欢迎 / 继续学习卡**（glass-base）：左侧个性化问候 + 继续上次学习的课程标题 + 进度描述；右侧"继续学习"主按钮（Indigo 荧光按钮）。
2. **快捷问答区**：嵌在欢迎卡下半部——搜索输入框 + 3–4 个热门问题 chip（紫色 tag，点击跳转问答页并预填）。
3. **4 个模块入口卡**：课程 / 问答 / 知识图谱 / 学习分析，每张卡使用自己的模块色（32×32 渐变方块图标 + glow-{模块} + 卡右上角径向光晕装饰）。
4. **双栏最近内容**：
   - 左栏"我的课程"：3 条最近课程，每条带封面缩略图 + 进度条（进度条带荧光）+ 标题。
   - 右栏"最近问答"：3 条最近对话，高亮当前一条（紫色左边条 + 浅紫底）。

**去除**：原首页的 Hero 大屏占用（1779 行大量装饰代码），功能卡从网格式四格改为 `grid-template-columns: repeat(4, 1fr)` 的紧凑四宫格。

### 6.3 问答三页（Purple，Module Layout）

#### 6.3.1 问答 · 提问（`/qa/ask`）

- **左侧**：240px 会话列表副导航 + 顶部"+ 新建对话"按钮（紫色渐变）+ 会话卡（当前对话高亮 `glass-light` + 紫色边框 glow）。
- **主区**：对话流。用户消息右对齐（紫色渐变气泡），AI 消息左对齐（`glass-base` 毛玻璃 + 紫色 `0.25` 边框 + glow-purple 阴影）。AI 消息内：顶部知识来源 chip（紫色）+ Markdown 正文 + 底部相关知识点 tag。
- **输入区**：`glass-base` 输入框 + 紫色边框 + `0 0 0 4px rgba(紫色,0.1)` 外发光 + 右侧渐变圆角发送按钮。

#### 6.3.2 问答 · 历史（`/qa/history`）

- 副导航变成筛选 tab（全部 / 未读 / 已收藏）+ 学科筛选 chip。
- 主区卡片网格（`grid-template-columns: repeat(3, 1fr)`），每张会话卡显示：头像方块 + 主题 + 条数 + 时间 + 学科 tag 组。
- 卡片 hover 态：抬升 + glow-purple。

#### 6.3.3 问答 · 详情（`/qa/detail/:id`）

- 顶部面包屑："问答 / {会话主题} / 详情"。
- 主毛玻璃卡展示完整问答内容（支持 Markdown、代码块、高亮引用段落）。
- 底部绑定一个**青色"知识图谱关联"区块**——展示该问答涉及的知识点 chip，点击跳转到知识图谱页。这是跨模块引导，强化 CKQA 的差异化定位。

### 6.4 课程四页（Blue，Module Layout）

#### 6.4.1 课程列表（`/course/list`）

- 副导航：全部 / 我的 / 收藏 / 报告。
- 主区顶部：分类 chip（全部激活态是蓝色渐变 pill，其余白底描边）+ 搜索 / 排序 / 筛选栏（`glass-base` 容器）。
- 课程网格（`grid-template-columns: repeat(auto-fill, minmax(300px, 1fr))`）：每张卡含封面渐变 + 课程标题 + 讲师 + 评分 + 学员数 + 价格 tag。
- hover 态：抬升 4px + 封面渐变加深 + glow-blue。

#### 6.4.2 课程详情（`/course/detail/:id`）

- 主毛玻璃卡（`glass-base`）：封面 + 标题 + 讲师 + 评分 + 学员数 + 价格。
- 继续学习高亮条：若已入门，展示"已学 xx% · 继续学习"的蓝色渐变条 + 蓝色荧光按钮。
- 章节目录：每章前带完成态图标（已完成 = 蓝底勾、当前 = 蓝底播放、未开始 = 灰色数字）。

#### 6.4.3 课程学习（`/course/learn/:id`，本次唯一深色产品页例外）

- 例外说明：为保证视频观看时的沉浸感，此页使用深色背景（slate-900），是本次方案中唯一的深色产品页。顶栏仍使用 glass-base（但切到深色变体）。
- 左侧主区：视频播放器（播放按钮使用毛玻璃圆 + blue glow），底部进度条带蓝色荧光。
- 视频下方：双 tab 切换"📝 笔记"和"💬 问问 AI"——笔记蓝色系，问 AI 切换到紫色系（让问答模块在这里也有一席之地）。
- 右侧：章节目录侧栏（深色 glass + 青色 `0.15` 边框），当前播放章节高亮。

#### 6.4.4 我的课程（`/course/my`）

- 顶部 4 个统计卡（总课程 / 已完成 / 进行中 / 收藏），每个数字使用不同颜色：蓝 / 绿 / 琥珀 / 柠檬。
- 下方 Tab 切换：进行中 / 已完成 / 收藏。
- 课程列表：横向卡片，每条含缩略图 + 标题 + 进度条 + 上次学习时间。

### 6.5 知识图谱（Teal，Module Layout，新视觉壳）

这是本次新增的视觉壳，不接数据，数据从本地 `mock/knowledge.json` 读取。

**结构**：左 120px 副导航 + 中央图谱画布 + 右 160px 详情面板。

- **左侧**：图谱浏览 / 知识检索 tab；学科多选（OS、算法、数据结构等 chip）；节点类型色例（概念 = Teal 500、实例 = Teal 400、错题 = Amber）。
- **中央画布**：SVG 渲染的交互式知识图谱。中心主题节点（`r=18` + radial glow halo）+ 一级子节点（`r=13`）+ 二级叶子节点（`r=8`）。节点间用 `stroke-opacity: 0.35` 的 Teal 连线连接。
- **交互**（视觉壳最小可用）：
  - 点击节点 → 右侧详情面板切换为该节点内容。
  - 鼠标拖拽画布 → 平移整个图谱。
  - 滚轮 → 缩放。
  - 右上角三个浮动按钮（放大 / 缩小 / 重置视图）。
- **右侧详情面板**（glass-base）：节点名称 + 类型 chip + 关联错题数 chip（若有）+ 描述 + 关联知识点列表 + 底部"去问答"按钮（青色渐变）——点击跳转到 `/qa/ask?topic={节点}`，实现跨模块引导。

### 6.6 个人中心（Neutral + Scene，Module Layout，新视觉壳）

新增视觉壳，不接数据。只完整实现"个人资料"子页，其余三个（账号设置 / 消息通知 / 我的收藏）做"骨架级"壳。

- **左侧副导航**：顶部个人卡（头像 + 昵称 + 学院年级）+ 4 条菜单项。每条菜单前带图标，图标颜色按场景：个人资料 = Slate、账号设置 = Slate、消息通知 = Amber（带数字 Badge）、我的收藏 = Lemon。
- **个人资料页**：
  - 头部：头像（56×56 渐变方块 + 右下角上传浮标）+ 昵称 + 用户 ID。
  - 基本信息表单（2 列）：昵称 / 学号 / 学院 / 专业。非编辑项（学号等）使用灰底禁用状态。
  - 底部统计卡组（4 个）：已学课程（Blue）、提问次数（Purple）、连续学习天数（Pink）、收藏数（Lemon）。
- **其余三子页**：只提供页面框架和标题占位，暂用"骨架屏 + 预留区块"样式，不做完整功能视觉。

## 7. 交互与动效规范

### 7.1 落地页四项动效明细

| 编号 | 动效 | 触发 | 参数 | 性能上限 |
| --- | --- | --- | --- | --- |
| ① | 知识节点云 | 页面可见即启动 | 节点 `20` · 连线 `≤40` · 推进 `0.3–0.6px/帧` · 鼠标 `120px` 半径放大 `1.2x`、连线亮度 `0.3→0.9` | 固定 `60fps` · 视窗不可见时 rAF 暂停 |
| ② | Pin-scroll 特性流 | 滚至 `#showcase` | 垂直 pin `300vh` · 三张横向切换 · 每张停 `100vh` · 曲线 `ease-out` | `prefers-reduced-motion` 降级为普通滚 |
| ③ | 磁吸 CTA + 涟漪 | 鼠标进入按钮外扩 `80px` 圆 | 最大位移 `8px` · `ease-spring` · 点击涟漪 `500ms` 扩至 `300px` | 仅主 CTA 生效 |
| ⑥ | 特性卡 3D 倾斜 | 鼠标进入卡 | `perspective 1000px` · `rotateX/Y max ±8deg` · 恢复 `ease-snap 300ms` | 触控设备全禁用 |

**全局开关**：所有落地页动效受 `prefers-reduced-motion: reduce` 控制——开启时 ① 缓速至 `0.1x`、② 降级为普通滚动、③ 禁用磁吸、⑥ 禁用倾斜。

### 7.2 全站通用微交互

**按钮**：
- Hover：`translateY(-1px)` + `shadow-md → glow-{模块}` · `200ms ease-out`。
- Active：`translateY(0)` + `scale(0.98)` · `100ms ease-snap`。
- Disabled：`opacity: 0.5` + 禁用光晕 + `cursor: not-allowed`。

**卡片**：
- Hover：`translateY(-4px)` + `shadow-sm → shadow-md` + 边框染模块色 `rgba(色,0.4)` · `300ms ease-out`。
- Active：位移复位 + `0 0 0 3px rgba(色,0.15)` 焦点环。

**输入框聚焦**：
- 边框 `neutral-200 → 模块色 500`。
- 外发光环 `0 0 0 4px rgba(色,0.12)`。
- 过渡 `200ms ease-out`。
- 错误态：红色边框 + 轻微抖动关键帧（x: `-2, 0, 2, 0, -1, 0, 1, 0, 0`，总 `200ms`）。

**路由切换**：
- 默认 `fade + translateY(8px → 0)` · `250ms ease-out`。
- RouterView 包裹 `<Transition>`，出去 `fade 150ms`、进来 `fade + slide 250ms`。

**Loading**：
- 局部：骨架屏 shimmer（从左到右 `1.5s` 循环）。
- 全页：顶栏底部 indeterminate 进度条（模块色），不做全屏 spinner。

**空状态**：
- 每模块一张占位 160×160 SVG 图示（后期补真实插图）。
- 标题 + 描述 + 引导 CTA。

**模态 / 弹层**：
- 遮罩 `rgba(15,23,42,0.6) + backdrop-filter: blur(4px)`。
- 弹出：`scale(0.96) + opacity(0) → scale(1) + opacity(1)` · `300ms ease-out`。
- 关闭：反向 · `200ms ease-in`。

**Toast 通知**：
- 右下角进入：`translateX(100% → 0)` · `300ms ease-out`。
- 驻留 `3s` 后反向退出：`200ms ease-in`。
- 四色对应 Info / Success / Warning / Error。

## 8. 延后迭代

下列内容属于本次设计范围之外，但已经在设计时被考虑过，留待后续独立迭代推进：

1. **落地页动效 ④ 标题碎聚**：GSAP SplitText 实现 Hero 标题逐字从四散聚合，流过渐变色。延后原因：只影响首屏的一眼，应在整体视觉稳定后再决定是"锦上添花"还是"多此一举"。
2. **落地页动效 ⑤ 滚动光带 Ribbon**：贯穿全页的 SVG path 动态渐变光带。延后原因：工作量较大（约 200 行 + 调试），且需要在全页布局稳定后才能设计路径。"要么做到位，要么别做"的类型，不适合匆忙并行。
3. **学习社区 / 学习分析 / 登录注册**：本次仍保持 coming-soon，新视觉版的 RouteState 会覆盖它们。
4. **真实数据接入**：本次视觉壳的数据来自本地 mock；待 `backend/ckqa-back` 编排层稳定后再按模块接入。
5. **暗色模式切换**：本次已经是混合方案（深色 Landing + 亮色 Product），若后续需要产品页支持暗色主题，应独立立项。
6. **移动端精细适配**：`< 768px` 本次只保底可用，未来若要做移动端，建议作为独立 scope 重新设计。

## 9. 附录

### 9.1 文件结构规划

仅列出本次设计涉及的新增 / 重大修改文件，不含 node_modules：

```
frontend/apps/student-app/src/
├── App.vue                          # 选择 Layout 组件
├── layouts/                         # 新增
│   ├── LandingLayout.vue           # 壳 A（仅透传）
│   ├── ProductLayout.vue           # 壳 B（NavHeader + main）
│   └── ModuleLayout.vue            # 壳 C（副导航 + main）
├── components/
│   ├── NavHeader.vue               # 重构：居中导航 + 胶囊
│   ├── common/                     # 新增公共组件
│   │   ├── GlassCard.vue
│   │   ├── GlowButton.vue
│   │   ├── ModuleTag.vue
│   │   └── SkeletonBlock.vue
│   ├── landing/                    # 新增落地页专用组件
│   │   ├── KnowledgeNodeCloud.vue  # Canvas 节点云
│   │   ├── PinScrollShowcase.vue   # Pin-scroll 特性流
│   │   ├── MagneticButton.vue      # 磁吸 + 涟漪
│   │   └── Tilt3DCard.vue          # 3D 倾斜卡
│   └── module-nav/                 # 四个模块副导航
│       ├── CourseSideNav.vue
│       ├── QASideNav.vue
│       ├── KnowledgeSideNav.vue
│       └── UserSideNav.vue
├── views/
│   ├── layout/index.vue            # 重构：落地页
│   ├── index.vue                   # 重构：首页
│   ├── qa/                         # 重构三页
│   ├── course/                     # 重构四页
│   ├── knowledge/                  # 新增视觉壳
│   │   ├── KnowledgeGraph.vue
│   │   └── KnowledgeSearch.vue     # 骨架级壳
│   ├── user/                       # 新增视觉壳
│   │   ├── UserProfile.vue
│   │   ├── UserSettings.vue        # 骨架级壳
│   │   ├── UserNotification.vue    # 骨架级壳
│   │   └── UserFavorite.vue        # 骨架级壳
│   └── status/RouteState.vue       # 重构
├── styles/
│   ├── tokens/                     # 新增 token 系统
│   │   ├── _colors.scss
│   │   ├── _typography.scss
│   │   ├── _radius.scss
│   │   ├── _shadow.scss
│   │   ├── _glass.scss
│   │   ├── _space.scss
│   │   ├── _motion.scss
│   │   └── _breakpoints.scss
│   ├── mixins/
│   │   ├── _module-color.scss      # 模块色可复用 mixin
│   │   └── _glass.scss
│   └── index.scss                  # 集中 import
├── router/
│   └── routes.js                   # 补齐 layout meta + 新壳路由
└── mock/                           # 新增视觉壳用的假数据
    ├── knowledge.json
    └── user.json
```

### 9.2 路由 Layout meta 约定

```js
// 示例
{
  path: '/',
  meta: { layout: 'landing' }
}
{
  path: '/home',
  meta: { layout: 'product' }  // 或省略（默认）
}
{
  path: '/course/list',
  meta: { layout: 'module-course' }
}
```

`App.vue` 根据 `route.meta.layout` 动态选择渲染 `LandingLayout` / `ProductLayout` / `ModuleLayout`。

### 9.3 模块 → 路由前缀 映射

| 模块 | 路由前缀 | 色系 | 本次状态 |
| --- | --- | --- | --- |
| 首页 | `/home` | Indigo | 重构视觉 |
| 课程 | `/course/*` | Blue | 重构视觉（4 页） |
| 问答 | `/qa/*` | Purple | 重构视觉（3 页） |
| 知识图谱 | `/knowledge/*` | Teal | 新视觉壳 |
| 学习社区 | `/community/*` | Orange | 保持 coming-soon（新 RouteState） |
| 学习分析 | `/analysis/*` | Pink | 保持 coming-soon（新 RouteState） |
| 个人中心 | `/user/*` | Neutral + Scene | 新视觉壳（主要做 profile） |

### 9.4 设计原则小结

1. **一致来自共性**：全站共用设计 token、圆角、字体、毛玻璃档位、动效曲线、间距尺度；模块差异仅在主色与光晕。
2. **克制优先**：荧光、毛玻璃、动画都应"点到为止"——单屏 glow 元素 `≤ 3`、卡片抬升不超过 `4px`、动画时长 `≤ 500ms`。
3. **产品而非演示**：页面应优先服务"用户看懂并使用"，装饰元素在功能需求面前退让。
4. **桌面先行**：不为响应式牺牲桌面端的信息密度与美感。移动端单独 scope。
