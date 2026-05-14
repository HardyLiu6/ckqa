# 构建向导第 4 步「提示词确认」重设计

**Goal:** 修复布局 bug、扩大策略卡视觉密度、补齐三种策略的优缺点对比、把状态条从独占行迁到 step header，让用户在选卡片时一眼看清差异并做出决策。

**Status:** Draft — 用于驱动 implementation plan。

---

## 背景与问题

当前 prompt-confirmation 步骤有四个具体问题（见 2026-05-14 用户反馈截图）：

1. **CSS 布局错位**：`.prompt-confirm-panel` 在 `frontend/apps/admin-app/src/styles/components.scss:2984` 被声明为 `display:flex; align-items:center`，把策略卡 grid 与详情面板压到同一水平行，导致：
   - 三张策略卡被挤到 ~33% 宽度，每张只剩 1 行描述
   - 详情面板被挤到右侧约 280px 窄条
   - "确认提示词策略" 按钮被推到左下角
2. **卡片信息不足**：每张卡只有标题 + 一句 desc，用户无法在选择前对比三种策略的优缺点
3. **状态条占行浪费**：`build-summary-strip` 独占一行，prompt 步骤兜底分支只有"已选资料 1 个"一个 chip，视觉占位与价值不匹配
4. **详情区文案散落**：现有 `PromptStrategyDetail` 有 4 种 variant，但每个分支内容/按钮位置不一致，用户切策略时面板视觉跳动

## 设计目标

- **决策导向**：用户扫一眼三张卡片就能对比清楚
- **视觉一致**：操作面板在三种策略下保持相同框架（避免切换时闪烁）
- **现状最小改动**：尽量复用 `PromptStrategyCard` / `PromptStrategyDetail` / `PromptTuneProgress` 现有组件，避免重写
- **响应式不破**：现有 build wizard 容器约 960px，新设计在该宽度下三栏不溢出
- **真实屏幕尺寸**：测试时使用浏览器实际视口（建议 1280×800 以下也可以正常排版）

## 整体布局

```
┌──────────────────────────────────────────────────────────────────────┐
│ <  STEP 04                              [可执行]  [已选资料 1 个]    │  step header（状态 chip 内联）
│    提示词确认                                                          │
│    选择本次索引使用的提示词策略                                        │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────┐ ┌──────────────┐ ┌──────────────┐                       策略卡 grid 3×1
│ 默认提示词   │ │ 自动调优     │ │ 手动调优     │                        ~180-220px 高
│              │ │              │ │              │
│ ✓ 优势 / ◯ 取舍 / 适合                                                  │
└──────────────┘ └──────────────┘ └──────────────┘
       └────────────┬───────────┘
                    ▼
┌──────────────────────────────────────────────────────────────────────┐
│  当前选中策略对应的操作面板                                            │  详情/操作面板
│  （文案与按钮见 §详情面板状态机）                                      │  整行宽度
└──────────────────────────────────────────────────────────────────────┘

                       [ 确认提示词策略 ]                                  sticky footer
```

## 文件结构与责任划分

### 修改文件

- `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue`：扩展 props 接受 `pros`、`cons`、`bestFor`，按新结构渲染
- `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`：策略元数据（描述/优缺点/适合）从 `STRATEGIES` 常量传入卡片
- `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue`：6 种 variant 文案统一为「策略名 + 状态消息 + 操作按钮」三段固定结构
- `frontend/apps/admin-app/src/styles/components.scss`：
  - `.prompt-confirm-panel` 改为 `display: grid; gap: ckqa-space-4`
  - `.prompt-strategy-card` 高度提高、内部改为多区块布局
  - `.prompt-strategy-detail` 整行宽度
  - `.build-step-stage__header` 容纳右侧状态 chip（旧的 `.build-summary-strip` 一行不再使用）
- `frontend/apps/admin-app/src/views/pages/ModulePage.vue`：把 `<div class="build-summary-strip">` 整段从 `.build-step-stage__body` 之前移到 `.build-step-stage__header` 内的右侧（与状态徽标同一容器，徽标始终最右）

### 不动的文件

- 后端：所有 prompt-tune / build run API、PromptTunePhase 阶段映射保持不变
- `PromptTuneProgress.vue`：内部已经够好，不动
- `prompt-tune-progress-model.js`：纯函数模型不动
- 路由 / store / 数据流：不变

## 策略卡片设计

每张卡片固定结构（顺序严格对仗）：

```
┌──────────────────────────────────┐
│ 图标 + 标题                      │  header
│                                  │
│ 一句话描述                        │  tagline
│                                  │
│ ✓ 优势 1                         │  pros（绿）
│ ✓ 优势 2                         │
│                                  │
│ ◯ 取舍 1                        │  cons（灰）
│ ◯ 取舍 2                        │
│                                  │
│ 适合：xxx                         │  best-for（小灰）
└──────────────────────────────────┘
```

**Element Plus 组件**：

- 优势 / 取舍：`<el-text>` 加自定义 class，前缀符号用纯 unicode（`✓` `◯`），不引入 icon 包
- 适合：`<small>` 配 muted 色

**完整文案**（已与用户对齐）：

| | 默认提示词 ⚙ | 自动调优提示词 ✨ | 手动调优提示词 🛠 |
| --- | --- | --- | --- |
| 标语 | 开箱即用，零等待。 | 基于本课程样本由 GraphRAG 自动调优。 | 进入独立工作台，3 步流程亲手调试。 |
| 优势 1 | 立即可用，无需调优 | 自动生成专家角色画像、领域识别、实体类型 | 完全控制实体抽取规则 |
| 优势 2 | 与官方语义保持一致 | 同一组资料命中缓存可秒级复用 | 可基于"系统默认"或"自动调优"为种子继续打磨 |
| 取舍 1 | 通用模板，未针对本课程语料优化 | 首次调优需要 10–20 分钟（受 LLM 速率限制） | 需要熟悉 GraphRAG prompt 模板结构 |
| 取舍 2 | 抽取的实体可能更倾向通用领域而非课程概念 | 资料重新解析后会自动重跑 | 需要 30 分钟以上人工编辑 |
| 适合 | 快速验证流程 / 跨课程通用知识库 | 单门课程长期沉淀 / 注重抽取质量 | 领域专家精细化迭代 / 已知抽取偏差需修正 |

## 详情面板状态机

固定三段式：`📌 标题 → 状态消息 → 操作按钮（可选）`。各 variant 文案完整版：

### default

```
⚙ 已选「默认提示词」

点击"确认提示词策略"即可进入索引构建。
graphrag 会按通用模板抽取实体与关系。

无需额外操作。
```

无按钮。

### graphrag_tuned · 未生成（not_started）

```
✨ 已选「自动调优提示词」

本次选材尚未生成调优产物。
GraphRAG 会按 12 个阶段自动学习样本，约 15 分钟。

         [ 开始调优 ]
```

按钮：触发 `triggerPromptTune({ force: false })`，沿用现有 `PromptTuneProgress` 已经布好的逻辑。

### graphrag_tuned · 生成中（pending / running）

```
✨ 正在生成自动调优提示词

当前阶段：生成实体关系示例
最近心跳 13:47:25

████████████░░░░░░░░░░░░  60%

▸ 查看最近日志
```

进度条 + 阶段标签 + 心跳时间 + 折叠的最近日志。**直接复用** `PromptTuneProgress.vue` 现有 pending/running 分支。

### graphrag_tuned · 已生成（success）

```
✓ 已生成自动调优提示词
（如果命中缓存：✓ 已生成自动调优提示词（复用历史缓存，无需重新生成））

完成于 2026/5/14 13:49:06

点击"确认提示词策略"即可使用本次产物。

         [ 重新生成 ]
```

**注意**：去掉指纹 sha256（用户看不懂）；保留"完成于" + 复用提示。

### graphrag_tuned · 失败（failed）

```
✗ 自动调优失败

错误：调优命令执行超时
你可以再次重试，或先选用默认策略。

         [ 重试 ]

▸ 查看最近日志
```

### custom_pipeline · 未构建（draft 不存在）

```
🛠 已选「手动调优提示词」

尚未构建草稿。本次构建专属，不复用历史。
从默认或自动调优为种子继续编辑实体抽取规则。

         [ 前往工作台 ]
```

按钮跳到 `knowledge-base-prompt-builder` 路由。

### custom_pipeline · 已构建（draft 存在）

```
🛠 已构建手动调优提示词

上次保存于 2026/5/14 13:30
已修改 1 个提示词块（实体抽取）

点击"确认提示词策略"即可使用本草稿。

         [ 编辑提示词 ]
```

## Step Header 内联状态条

### 现状

```html
<header class="build-step-stage__header">
  <el-button class="build-step-stage__back">←</el-button>
  <div>
    <p class="eyebrow">STEP 04</p>
    <h2>提示词确认</h2>
    <p>选择本次索引使用的提示词策略</p>
  </div>
  <StatusBadge :status="..." />  <!-- 可执行 / 已锁定 / 已完成 -->
</header>

<div class="build-summary-strip">  <!-- 独占整行 -->
  <span class="build-summary-chip">已选资料 1 个</span>
</div>

<div class="build-step-stage__body">...</div>
```

### 改后

```html
<header class="build-step-stage__header">
  <el-button class="build-step-stage__back">←</el-button>
  <div>
    <p class="eyebrow">STEP 04</p>
    <h2>提示词确认</h2>
    <p>选择本次索引使用的提示词策略</p>
  </div>
  <div class="build-step-stage__header-tail">
    <el-tag size="small" effect="plain"
            v-for="chip in buildSummaryChips" :key="chip.label">
      {{ chip.label }} {{ chip.value }}
    </el-tag>
    <StatusBadge :status="..." />  <!-- 始终最右 -->
  </div>
</header>

<!-- 不再有独立的 build-summary-strip -->
<div class="build-step-stage__body">...</div>
```

**布局规则**：
- `.build-step-stage__header-tail` 用 `display: flex; gap: ckqa-space-2; align-items: center; flex-wrap: wrap` 让 chips 在窄屏自动折行（仍跟 StatusBadge 同列）
- chips 数量超过 3 个时不主动截断，以 `flex-wrap` 让多余 chip 换行
- `.build-summary-strip` 旧 class 直接删除（搜索全仓库确认无其它引用）
- `.build-summary-chip` 旧样式可删，因为我们改用 `el-tag` 原生样式

### 状态条数据保留

`buildSummaryChips` 在不同步骤下展示的 chip 数量保持现有逻辑（material 步骤 2 个、parse 2 个、export 2 个、index/qa 1 个、prompt 兜底 1 个），不改 computed 的实现。

## CSS 改动详细列表

```scss
// frontend/apps/admin-app/src/styles/components.scss

.prompt-confirm-panel {
  display: grid;          // ← 从 flex / align-items: center 改回 grid
  gap: var(--ckqa-space-4);
}

.prompt-strategy-grid {
  // 保留 grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--ckqa-space-3);
}

.prompt-strategy-card {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-4);
  min-height: 220px;       // ← 新增
  // 保留边框 / 选中 / 禁用 / focus 等现有样式
}

.prompt-strategy-card__header {
  display: flex;
  align-items: center;
  gap: var(--ckqa-space-3);
}

.prompt-strategy-card__pros,
.prompt-strategy-card__cons {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12.5px;
  line-height: 1.6;
}

.prompt-strategy-card__pros {
  color: var(--ckqa-success-text, #0f766e);
}

.prompt-strategy-card__cons {
  color: var(--ckqa-text-muted);
}

.prompt-strategy-card__best-for {
  margin-top: auto;        // 顶到卡底
  padding-top: var(--ckqa-space-2);
  border-top: 1px dashed var(--ckqa-border);
  font-size: 12px;
  color: var(--ckqa-text-muted);
}

.prompt-strategy-detail {
  // 保持现有样式：padding, border, background。
  // 内部最大宽度限制以 768px 居中显示，避免在宽屏被拉伸。
  max-width: 768px;
  margin-inline: auto;
}

.build-step-stage__header {
  // 改为支持右侧 chip + 状态徽标共占一格
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.build-step-stage__header-tail {
  display: flex;
  gap: var(--ckqa-space-2);
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

// 删除：
//   .build-summary-strip { ... }
//   .build-summary-chip[data-tone='ok|warn|info'] { ... }
```

## 错误处理与边界

- **资料未选**（`materialIds 为空`）：策略卡仍可点击但操作面板内追加一行红色提示"请先回到 Step 01 选择资料"，并禁用主按钮（current 行为已支持，本设计不引入新分支）
- **prompt-tune 缓存命中但产物已被磁盘 GC 删除**：现有 `BuildRunPromptMaterializer` 已经按"成功记录 + 文件不存在"降级到 default 并写 fallbackReason，本设计不改这条链路
- **窄屏（<720px）**：`.prompt-strategy-grid` 自动折叠为 1 列（已有 media query 不动）

## 测试

### 现有测试影响

- `app-shell.test.js:2994` 断言 `.build-summary-chip` 存在：删除该断言（chip 改用 el-tag 后不再有这个 class）
- `app-shell.test.js:2997` 断言 build-step 6 列 grid：与本设计无关，保留

### 新增前端单测

- `frontend/apps/admin-app/src/__tests__/unit/build-step-prompt-strategies.test.js`：
  1. STRATEGIES 常量结构正确（每个有 key/title/icon/tagline/pros[2]/cons[2]/bestFor）
  2. 每个 STRATEGIES 项的 pros 与 cons 必须各有 2 条（防止文案被无意删空）
  3. 三个 key 必须对应到合法的 strategy 值（default / graphrag_tuned / custom_pipeline）

详情面板的渲染断言通过 PromptStrategyDetail 单测（如有）或 e2e Playwright 兜底；本次不新增详情面板单测，因为它是纯模板组件、变更只是文案。

### 浏览器手工 smoke

- 在 1024×768 / 1280×800 / 1440×900 三种视口下检查布局
- 切换三种策略，详情面板内容切换平滑
- 自动调优 4 种状态在测试 fixture 下逐个看（status: not_started → running → success → failed）
- step header 右侧 chip 在 prompt 步骤下不溢出

## 实现顺序提示（供 implementation plan 用）

1. CSS bug 修复（`.prompt-confirm-panel`）+ build-summary-strip 迁移到 header
2. 策略卡内容扩展（PromptStrategyCard 接受新 props，BuildStepPrompt 注入完整文案）
3. CSS 新结构（卡片 min-height、pros/cons 样式、best-for 分隔）
4. 详情面板文案微调（默认 / 已生成 / 已构建 三处的"下次点击" → "点击"，移除 sha256）
5. 删除旧 build-summary-strip class、更新 app-shell.test.js 断言
6. 新单测 + 浏览器手工 smoke
