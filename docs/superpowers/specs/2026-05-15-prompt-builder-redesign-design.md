# 手动调优提示词页面重设计

| 项目 | 值 |
| --- | --- |
| 创建日期 | 2026-05-15 |
| 适用模块 | `frontend/apps/admin-app` 的 `PromptBuilderPage.vue` 与 `prompt-builder/*` 子组件 |
| 上游依赖 | `backend/ckqa-back/` 已有的 build run / prompt tune 编排，`graphrag_pipeline/scripts/prompt_tuning/*` 脚本 |
| 现状 | 前端是 3 步精简向导（选模板 / 分块编辑 / 预览保存），与后端 5 阶段流水线脱节 |

## 背景

后端"手动调优提示词"链路在 `graphrag_pipeline/scripts/prompt_tuning/run_material_prompt_pipeline.py` 中已经成形，是一条 fetch → build samples → build audit → generate candidates → extract → score → finalize 的完整流水线。前端当前的 3 步向导只覆盖了"挑选种子 / 编辑 extract_graph / 保存草稿"这条最简路径，没有把后端能力暴露给课程老师与助教。

本次重设计将前端向导从 3 步扩到 5 步，对齐后端 5 个核心阶段：选模板 → 构建准备材料 → 生成候选提示词 → 抽取评分 → 预览保存。同时引入 AI 预填、关系候选推荐、历史标注复用三类智能能力，把"从零标注"的工作量降到"审阅修改"。

## 目标与非目标

**目标**

- 5 步向导对齐后端流水线的 5 个核心阶段。
- 在 02 步中提供页面化的人工标注辅助，使非工程用户能完成 audit 校准集的 gold 标注。
- 在 03 / 04 步中暴露候选与评分，让用户在过程中保持成本意识与质量决策权。
- 在 05 步中将选定候选既保存到本次构建，又入库为可复用的历史草稿。
- 视觉风格统一为现代笔记风（白底、淡米背景、卡片化、留白），与系统其他界面观感一致。
- 文案以中文为主，仅在装饰位（候选 ID、prompt 原文、变量名）保留英文。

**非目标**

- 不重写后端流水线：除新增"标注内容查询/保存"接口外，不改变现有 `KnowledgeBaseBuildRunsController` 的语义边界。
- 不实现"知识库默认 prompt 全局固化"：仅做"本次构建 + 历史草稿入库"两种保存范围，全局固化属于运维操作，留在 CLI / 管理界面。
- 不引入 prompt 全文的可视化关系图谱编辑，关系仍用文本+下拉表达。
- 不暴露 smoke 模式：smoke 是开发期 fixture，前端只调 full。

## 架构与流程

### 5 步向导与后端阶段映射

| 前端步骤 | 后端阶段 | 主要后端脚本 / API | 用户可见产物 |
| --- | --- | --- | --- |
| 01 选模板 | seed 选择 | 仅前端状态 | `seed` 字段写入 `customPromptDraft` |
| 02 构建准备材料 | build samples + build audit + 人工标注 | `build_prompt_tuning_samples.py` / `build_audit_extraction_set.py` + 新增标注 API | `prompt_tuning_samples.json` / `audit_extraction_set.json`（含 gold） |
| 03 生成候选提示词 | candidate generation | `generate_candidate_prompts.py` | `prompts/candidates/*` + `manifest.json` |
| 04 抽取评分 | extract + score（仅 full） | `run_native_extraction.py` + `score_extraction_results.py` | `results/extraction_eval/*` + 排行报告 |
| 05 预览保存 | finalize for build run | 新增"草稿入库"API | `customPromptDraft` 写入 + 历史草稿表新增一条 |

### 进度门控

- 02 → 03：所有 `audit_priority="high"` 的样本"已完成"或"已跳过"才能继续。
- 03 → 04：至少勾选 1 个候选；候选数与 token 预估在底部摘要条实时显示。
- 04 → 05：full 模式跑通且已选定一个候选。
- 05 完成：返回 build wizard 的 04 步并自动带上 `promptStrategy=custom_pipeline`。

### 智能能力（02 步）

- **A. AI 预填实体/关系**：进入样本时后端用现有候选 prompt 跑一次抽取，结果作为虚线紫色卡片显示，用户"采纳/拒绝"。
- **C. 关系候选推荐**：用户添加完两个实体后，前端读 `config/schema/relation_types.json` 的 `source_types` / `target_types`，自动列出这两个实体类型之间合法的关系类型。
- **D. 历史标注复用**：以 `gold_stable_key`（`source_doc_id + page_start + page_end + text_hash`）为主键、`source_sample_id` 为副键，匹配本知识库其他构建中已经标过的 audit 样本，预填为"已确认"状态、绿色 ♻ 标识。后端 `preserve_existing_gold_annotations` 已实现此匹配，前端只需读结果展示。

## 用户旅程

```dot
digraph user_journey {
    rankdir=LR;
    node [shape=box, style=rounded];

    "进入手动调优" -> "01 选模板";
    "01 选模板" -> "02 构建准备材料";
    "02 构建准备材料" -> "标注 IDE";
    "标注 IDE" -> "03 生成候选";
    "03 生成候选" -> "勾选候选";
    "勾选候选" -> "04 抽取评分";
    "04 抽取评分" -> "选定候选";
    "选定候选" -> "05 预览保存";
    "05 预览保存" -> "返回构建向导 04";
}
```

## 页面详细设计

### 视觉基调

- 背景 `#fafaf9` 淡米色，主体卡片白色，边框 `#e7e5e4` 浅灰。
- 圆角统一为 8 / 12px 两档，阴影统一为单层 `0 1px 2px rgb(0 0 0 / 4%)`。
- 字号：标题 18-22px / 主体 13-14px / 辅助 11-12px / 等宽 12.5px。
- 强调色 `#6366f1`（紫蓝），成功 `#15803d`，警示 `#b45309`，危险 `#dc2626`。
- 装饰位英文使用 `ui-monospace, "SF Mono", Menlo` 等宽字体，正文中文使用 `-apple-system, "PingFang SC", "Microsoft YaHei"`。

### 01 选模板（沿用现状）

3 张种子卡：系统默认 / GraphRAG 自动调优 / 我的历史草稿。第 3 张在 05 步入库后才会有内容，否则空状态显示"05 步保存后会出现在这里"。

### 02 构建准备材料

**布局**：白色容器 + 左右两栏。左栏 280px 是样本列表，右栏弹性宽度是当前样本工作区。

**子任务 02.1 / 02.2**（脚本一键）

- 进入 02 步时，后端串行执行 `build_prompt_tuning_samples.py` 和 `build_audit_extraction_set.py`（约 15-20 秒）。
- 完成后两个子任务在顶部折叠展示：行内"✓ 02.1 调优样本集 · 80 条 · 5 类型 · 12 秒"，可点"查看"展开样本分布。
- 失败状态以红色头部条展示，附"重新运行"按钮。

**子任务 02.3 标注 IDE**

- 左栏样本列表：按 `audit_priority` 倒序（high/medium/low），同优先级内按 `source_sample_id`。每项展示样本 ID（mono 字体小字）、标题（heading_path 末段）、状态徽章（未开始 / 进行中 / 已完成 / 已跳过），完成的样本灰显沉底。顶部带筛选 tabs（全部 / high / medium / 未标）和总进度条。
- 右栏工作区从上到下：
  1. **样本元信息条**：标题 + breadcrumb（PDF 名 / 章节 / 页码）+ 类型 + 优先级徽章 + "跳过 / 完成 ✓"按钮。
  2. **♻ 历史标注横幅**（绿色，仅命中时显示）："发现 N 条已有标注来自 <其他构建>，已为你预填"，附"不使用"按钮。
  3. **✨ AI 预填横幅**（紫色，仅有候选时显示）："AI 助手已生成 N 个候选实体，请逐条审阅"。**只放一个"按置信度排序"按钮**，不提供"全部采纳"。这是与风险 1 缓解措施一致的硬性约束。
  4. **原文卡**：白底卡片展示样本 text，命中信号高亮（黄色背景，可点击添加为实体），已确认实体高亮（紫色背景）。鼠标在原文中拖选 → 浮动按钮"添加为实体"。
  5. **命中信号 chip 行**：定义信号 / 公式信号 / 步骤列表等。
  6. **实体卡列表**：每个实体一张卡片。
     - 已确认实体：白底，左侧 16px 留空，主区"实体名 + 类型 chip + 描述"，右侧"编辑 / 删除"图标按钮。
     - 历史复用：白底 + 左侧绿色 ♻ 图标。
     - AI 预填待审：紫色虚线边框 + 紫色 ✨ 图标 + 描述行额外显示"由 AI 从某句话识别 · 置信度 0.72"，右侧"采纳 / 拒绝"按钮。
     - 末尾"+ 手动添加实体"虚线按钮，点击展开行内编辑器（实体名输入 + 类型下拉 + 说明可选）。
  7. **关系卡列表**：每个关系一张卡片，主区流式呈现 `[源实体] → [关系类型] → [目标实体]`，下方"证据：xxx"。
     - 关系两端从已添加实体下拉选，避免拼错。
     - 关系类型下拉根据两端实体类型动态过滤（C 智能能力）。
     - AI 预填和已确认的视觉区分同实体卡。

**完成校验**：点"完成 ✓"时，要求至少 1 个实体；空表"跳过"需弹窗确认理由（"原文与课程无关 / 无可抽取实体 / 其他"）。

**进度门控**：所有 high 优先级样本完成或跳过才允许进 03。底部固定操作条显示当前进度与"进入下一步 →"按钮，未达门控时按钮禁用并显示阻塞原因。

### 03 生成候选提示词

进入 03 时后端运行 `generate_candidate_prompts.py`（数秒），完成后展示候选网格。

**布局**：

- **顶部摘要条**：横向 4 列指标，"已生成 N 个候选 / 本次将评分 X 个 · X×20=Y 次大模型调用 / 预估 token 消耗 / 预估时长"，所有数字随勾选实时更新。
- **候选网格**：2 列流式布局（CSS Grid `repeat(auto-fill, minmax(420px, 1fr))`），候选数从 4 扩展到 6+ 时自动换行，不会出现空白格。每个候选一张卡片。卡片顶部右上角圆形复选框，左侧主区：
  - 候选中文译名（大字体粗体）+ 后端候选标识符（mono 字体灰色副标题）。
  - 类型描述（"基线 · 课程域微调"等）。
  - 特性标签 chips（schema 注入 / 方向卡 / few-shot 蒸馏 / 严格 tuple 等）。
  - 关键 meta 表（来源、大小、schema、fewshot）。
  - token 估算条（绿→黄→红渐变），右侧 mono 字体显示 `~Xk`。
  - 底部"查看完整 prompt →"按钮。
- **推荐徽章**：根据后端 manifest 中的元数据（如 `is_recommended` 标志，需要后端在 manifest 中标注），最优候选卡片左上角带紫色"✦ 推荐"角标。
- **快捷动作行**：候选网格上方的"全选 / 反选 / 仅选基线"按钮。"仅选基线"指仅勾选 `category === "baseline"` 的候选（当前对应 `default`），用于快速做"基线对照单跑"。
- **底部固定操作条**：左侧实时摘要文案"已选 X 个候选 · 预估 ~Yk tokens · 约 Z 分钟"，右侧"返回 02 / 开始抽取评分 →"按钮。

**候选译名映射**（前端 hardcode，与后端 ID 解耦）：

| 后端候选 ID | 中文译名 |
| --- | --- |
| `default` | 默认基线 |
| `auto_tuned` | GraphRAG 自动调优 |
| `schema_aware_directional_v2` | 图谱感知 |
| `schema_fewshot_distilled_v2_strict_tuple` | 图谱感知 + 蒸馏样例 |

**查看完整 prompt 抽屉**：点击 "查看完整 prompt" 从右侧滑出抽屉（420px 宽，遮罩半透明），内部使用本设计 § "提示词文本显示组件" 中定义的组合方案。

### 04 抽取评分

仅运行 full 模式（每候选 20 条 audit 样本）。页面有两种时态：

**时态 ① 评分进行中（候选矩阵）**

- **顶部进度摘要**：横向 3 列，左为 "32 / 80 LLM 调用 · 已用 6 min · 剩余 12 min"，中为 "~ 192k tokens 已消耗 / 预估总量 480k"，右侧"中止评分"按钮。
- **候选矩阵**：每行一个候选，6 列网格：候选名 / 抽取进度条 / 抽取状态 chip / 评分进度条 / 评分状态 chip / 完成后的综合分 chip。
  - 行级状态用色块表达：完成绿底（`success-soft`）、进行中蓝底（`running-soft`）、排队灰显（opacity 0.55）。
  - 进度条颜色随状态变（蓝色进行中 / 绿色完成）。
  - 状态 chip 用 pill 样式，文案中文化（"✓ 完成 / ↺ 抽取中 / — 排队"）。

**时态 ② 评分完成（排行榜 + 详情抽屉）**

- **左侧排行榜表格**：列依次是 #（金银铜奖牌）/ 候选（中文译名 + ID 副标题）/ 综合分（数值 + 进度条）/ 解析成功率 / 召回率 / 准确率 / token 消耗 / **操作列**。
  - 第 1 名金牌（金黄色径向渐变）、第 2 名银牌（灰色渐变）、第 3 名铜牌（铜色渐变），其余灰圆。
  - 表头右上角下拉切换排序维度（综合分 / 召回率 / 准确率 / 解析成功率，默认综合分倒序）。
  - **行点击 = 查看详情**：点击候选行（操作列除外）右侧抽屉滑出展示详情，行左侧显示紫色高亮条。详情抽屉**只读**，不承担"选定"动作。
  - **操作列 = 选定/已选定**：每行操作列放一个按钮。未选定时显示"选定"，点击后立即把该候选记为本次构建选定候选；已选定的行操作列变为绿底"✓ 已选定"且不可再次点击其他行（先取消当前选定再选别行）。整张表同一时刻只能有一个"已选定"。
  - 行可被高亮和被选定是两个独立状态：可以查看候选 A 的详情（高亮）的同时，候选 B 处于"已选定"状态（操作列绿色）。
- **详情抽屉**（右侧 380px 宽，从右滑入，半透明遮罩，点空白或 Esc 关闭）：
  - 顶部排名徽章 + 候选译名 + 标识符副标题 + 当前是否"已选定"的状态 pill（仅展示，不可在抽屉内切换选定状态）。
  - 大号综合分数字（32px、紫色）+ small "综合分"。
  - 6 块指标方块（2 列 3 行）：解析成功率 / 召回率 / 准确率 / F1 / 实体均数 / 关系均数。颜色规则：解析成功率 / 召回率 / 准确率 / F1 这四项达到下方质量门控阈值显绿、低于阈值但 > 阈值×0.7 显黄、其余显红；实体均数和关系均数始终用中性色（黑），不参与门控配色。
  - **质量门控** 区块：每条规则一行，✓/✗ 图标 + 中文规则文案 + 实测值。规则与阈值（与后端 `score_extraction_results.py` 中 `gate_passed` 逻辑保持一致）：
    - 解析成功率 ≥ 80%
    - 召回率（校准集）≥ 50%
    - 准确率（校准集）≥ 50%
    - 关系类型方向正确（即所有关系都符合 `source_types` / `target_types` 约束）
  - **成本** 区块：tokens / 耗时两块。
  - 底部"查看 20 条样本抽取详情 →"链接（打开二级抽屉显示具体抽取结果对比 audit gold）。
- **底部固定操作条**：左侧"已选定：<候选译名>（rank N，综合分 X.XX）"或"尚未选定候选"，右侧"返回 03"和"进入 05 →"按钮。**"进入 05 →"在尚未选定候选时禁用**，hover 提示"请在排行榜操作列点击'选定'"。

### 05 预览保存

**布局**：左右两栏。左栏 prompt 预览卡（弹性宽度），右栏 360px 表单卡。

**左栏 prompt 预览**：

- 顶部 head 区：金牌 chip + 综合分 chip + 候选中文译名（h2）。下方副标题展示 `来源候选 + 标识符 + 大小 + 单次 token 估算`。右上角"📋 复制"按钮。
- Tab 行（5 个）：实体抽取提示词（标"主"，紫色徽章）/ 描述总结 / 社区报告·图 / 社区报告·文 / 声明抽取，后 4 个标"沿用模板"灰显并禁用点击；点击主 tab 时显示对应内容，hover 灰显 tab 时弹 tooltip"本流程仅调整实体抽取提示词，其余 4 个使用所选种子的默认内容"。
- 主体使用本设计 § "提示词文本显示组件" 中定义的组合方案：默认富文本视图（B），按钮可切原文视图（A）。

**右栏表单**：

- 头部图标 + "保存草稿"标题 + "为本次构建确认提示词"副标题。
- **草稿名输入**：默认值 "课程名 · 候选简称 · YYYY-MM-DD"，可改。
- **说明 textarea**：选填，默认占位提示"例如：经过 20 条校准集评估…"。建议系统按评分结果自动生成一段默认文案让用户改。
- **来源记录**（只读）：`课程 / 校准集 / 选定候选 / 评分 run` 4 行 mono 字体小字。
- **保存范围 radio**：
  - "仅本次构建 + 入库历史草稿"（默认选中）：写入本次构建的 `customPromptDraft`，并在 `prompt_drafts` 历史表新增一条。
  - "仅本次构建"：只写 `customPromptDraft`，不入库历史草稿（调试场景）。
- 底部"返回 04 / ✓ 保存并返回构建向导"按钮。

### 提示词文本显示组件（M 组合方案）

在 03 抽屉、05 左栏复用同一组件 `<PromptDisplay>`。

- 默认渲染模式 = `rich`（富文本文档风）。
- 头部右上角放视图切换 SegmentedControl："仅文档 / 分屏 / 仅原文"，对应模式 `rich` / `split` / `raw`。
- **`rich` 模式**：
  - 前端 parser 按 `^-([^-]+?)-$` 正则切分原文为段落，每段一张卡片：
    - 卡片头：图标（按段落名映射）+ 中文别名 + 副标题"原文标题 ___"+ 折叠按钮。
    - 卡片体：根据段落内容启发式渲染：
      - 段落首行匹配 `^(实体类型|relation_types)` 等 → 列表项渲染为 `<type tag> + 描述`。
      - 包含 `->` / `:` 的行 → 渲染为流式 `from → to: <type tag>`。
      - 单纯叙述段 → 渲染为段落。
      - 占位符 `{input_text}` / `{entity_types}` 等 → 黄色高亮 chip。
      - 行内课程域关键字（实体类型、关系类型）→ 紫色 inline tag。
- **`split` 模式**：左侧 `rich`、右侧 `raw`，同步滚动（监听一边 scrollTop 用 IntersectionObserver 推算另一边对应位置）。
- **`raw` 模式**：暗色 IDE 风（`#1e1e1e` 背景），等宽字体、行号、语法高亮（标题段绿、关键字蓝、占位橙、注释灰斜体）。复制按钮、下载按钮、折行按钮。

**容错**：如果 parser 解析失败（段落数 < 2 或某段过长），回退为 `raw` 模式并在头部条展示提示"无法解析为文档视图，已切到原文"。

## 路由设计

整个手动调优向导是单一路由，通过 query 切换步骤；这样浏览器后退 / 刷新 / 分享 URL 都能复现状态。

- 路由：`/app/knowledge-bases/:kbId/build/prompt-builder`（沿用现有路由 `name: "knowledge-base-prompt-builder"`，不新增）。
- Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `buildRunId` | string（数字 ID） | ✓ | 当前所属 build run，缺失时进入引导回构建向导 |
| `step` | `seed` / `prepare` / `candidates` / `scoring` / `save` | — | 当前步骤，缺失默认按数据状态推断（详见进度门控） |
| `sampleId` | string | — | 02 步当前打开的样本 ID，方便回到具体样本继续标注 |
| `selectedCandidates` | comma-separated string | — | 03 步勾选的候选 ID 列表（如 `default,auto_tuned`），缺失默认全选 |
| `evalRunId` | string | — | 04 步的评分 run，跑完后保留以便刷新页面恢复 |
| `selectedCandidate` | string | — | 用户在 04 步选定要进 05 的候选 ID |

URL 是真值之源：所有"返回上一步 / 浏览器后退"操作都改 query；组件内部不再保留独立的步骤 state。`selectedCandidates` 一旦写入就持久化，避免用户刷新后又要重选。

## 数据模型与后端契约

### 复用的现有契约

- `GET /api/v1/knowledge-base-build-runs/{id}` → `buildMetadata.customPromptDraft`：seed / prompts.extract_graph.content。
- `PUT /api/v1/knowledge-base-build-runs/{id}/custom-prompt-draft` → 写 `customPromptDraft`（现状只有 extract_graph，本设计扩展为多 key）。

### 新增后端 API（前端交付时需后端配合）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/prompt-tune-samples` | 触发 `build_prompt_tuning_samples.py`，返回执行结果摘要 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/audit-set` | 触发 `build_audit_extraction_set.py`，返回采样列表（含 `audit_priority` / `audit_reason` / 命中信号） |
| `GET` | `/api/v1/knowledge-base-build-runs/{id}/audit-samples` | 列出当前 build run 的 audit 样本（含 gold 字段） |
| `PUT` | `/api/v1/knowledge-base-build-runs/{id}/audit-samples/{sampleId}` | 更新某条样本的 gold_entities / gold_relations / annotation_notes |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/audit-samples/{sampleId}/ai-suggestions` | 后端用现有 prompt 抽一遍，返回候选实体/关系（智能能力 A） |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/candidates` | 触发 `generate_candidate_prompts.py` |
| `GET` | `/api/v1/knowledge-base-build-runs/{id}/candidates` | 列出候选与 manifest |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/extraction-eval` | 触发 full extract+score，请求 body 含 `selectedCandidates: string[]` |
| `GET` | `/api/v1/knowledge-base-build-runs/{id}/extraction-eval/status` | 轮询评分进度（候选矩阵数据源） |
| `GET` | `/api/v1/knowledge-base-build-runs/{id}/extraction-eval/report` | 评分完成后获取排行榜与候选详情 |
| `POST` | `/api/v1/knowledge-base-build-runs/{id}/finalize` | 保存选定候选到 customPromptDraft，可选入库 prompt_drafts |
| `GET` | `/api/v1/knowledge-bases/{kbId}/prompt-drafts` | 历史草稿列表（用于 01 步种子选择） |
| `GET` | `/api/v1/relation-schemas` | 返回 schema 定义供 02 步关系下拉过滤（可缓存） |

### 关键 API 响应字段约定

为避免前后端在实施时对字段语义产生分歧，固化以下字段约定：

**`GET /audit-samples` 响应中每条样本字段**：

- `id`、`source_sample_id`、`text`、`heading_path`、`page_start`、`page_end`、`audit_priority`、`audit_reason`、`hit_signals`（命中信号 chip 列表，如 `["definition_signal", "formula_signal"]`，前端按 § 02 命中信号 chip 行渲染）。
- `gold_entities`、`gold_relations`、`annotation_notes`、`reviewer_decision`、`reviewer_confidence`：用户标注内容。
- `gold_stable_key`：稳定来源键，用于历史复用匹配。
- `reused_from`（可空）：当本条样本是从历史标注复用而来时，包含 `{buildRunId, buildRunName, reusedAt}`。前端据此渲染 02 步绿色 ♻ 横幅文案"发现 N 条已有标注来自 <buildRunName>"。

**`GET /candidates` 响应中每个候选字段**：

- `candidate_id`：稳定标识符（如 `schema_fewshot_distilled_v2_strict_tuple`）。
- `display_name_zh`：中文译名（如 `图谱感知 + 蒸馏样例`）。后端从 manifest 读出，前端展示时优先用，缺失时退化到前端 hardcode 表（见 § 风险 3）。
- `category`：候选分类，枚举 `baseline` / `auto_tuned` / `schema_aware` / `schema_fewshot`。03 步"仅选基线"按钮选 `category === "baseline"` 的候选（即 `default`）。
- `is_recommended`：布尔值，后端依据 manifest 的 `notes` 标注或上一次评分历史决定。03 步据此渲染紫色"✦ 推荐"角标。
- `traits`：特性标签数组（如 `["schema_injected", "directional_card", "few_shot_distilled", "strict_tuple"]`），前端 hardcode 中文映射后渲染为 chips。
- `estimated_token_per_call`：单次调用估算 token 数，03 步据此计算 token 总量与渲染绿/黄/红渐变条。
- `prompt_size_bytes`、`schema_used`、`fewshot_example_count`、`fewshot_strategy`、`base_prompt_source`、`generation_time` 等 manifest 已有字段透传。

**`GET /extraction-eval/status` 评分进度响应**：

- `overall`：`{totalCalls, finishedCalls, elapsedSeconds, estimatedRemainingSeconds, tokensUsed, estimatedTotalTokens}`。
- `candidates[]`：每个候选 `{candidate_id, extract: {finished, total, currentSampleId}, score: {finished, total}, status: "queued" | "extracting" | "scoring" | "done" | "failed"}`。

**`GET /extraction-eval/report` 评分完成响应**：

- `candidates[]`：每个候选 `{candidate_id, rank, composite_score, parse_success_rate, recall, precision, f1, entity_count_avg, relation_count_avg, tokens_used, elapsed_seconds, gates: [{name, threshold, value, passed}], failed_samples: [{sample_id, reason}]}`。
- `gates[].name`：枚举 `parse_success` / `audit_recall` / `audit_precision` / `relation_direction`。前端按此 key hardcode 中文规则文案（如 § 04 详情抽屉所列）。

### 新增数据表

`prompt_drafts` 历史草稿表：

- id (PK)
- knowledge_base_id (FK)
- name（用户填的草稿名）
- description（说明，可空）
- seed (`system_default` / `auto_tuned` / `prompt_draft:N`)
- candidate_id（来源候选标识符）
- prompts_json（多 key prompt 内容快照）
- source_build_run_id（来自哪次构建）
- composite_score（评分时的综合分）
- created_at / updated_at

## 第三方依赖选型

**约束**：现有依赖见 `frontend/apps/admin-app/package.json`，已有 Vue 3.5、Element Plus 2.13、@vueuse/core 14、@iconify/vue、lucide-vue-next、Pinia、@formkit/auto-animate、colord。本节只列**需要新增**的库，沿用 Element Plus 能解决的就不引第三方。

### 已有库可直接复用

| 需求场景 | 已有库 | 用法 |
| --- | --- | --- |
| 抽屉（03 候选预览、04 详情、05 看原文） | Element Plus `<el-drawer>` | 直接用，宽度 / direction / 遮罩配置都齐 |
| 折叠面板（02 步任务折叠、05 来源记录） | Element Plus `<el-collapse>` | 直接用 |
| 表单（05 草稿名、说明、保存范围 radio） | Element Plus `<el-form>` / `<el-input>` / `<el-radio-group>` | 直接用 |
| 表格（04 排行榜） | Element Plus `<el-table>` 或自定义（已有 mock，可直接用 element-plus） | 排序、固定表头都内置 |
| 标签 chip / pill | Element Plus `<el-tag>` | 已经在用 |
| 进度条（02 标注进度、03 token 估算） | Element Plus `<el-progress>` | 已经在用，支持渐变色 |
| Tab 切换（05 prompt tab） | Element Plus `<el-tabs>` | 直接用 |
| 复选框 / 单选框 | Element Plus `<el-checkbox>` / `<el-radio>` | 直接用 |
| 列表渐入动画（实体卡 / AI 候选） | `@formkit/auto-animate` | 已有，加在父容器上即可 |
| 置信度 / 字节计数等数值动画 | `@vueuse/core` 的 `useTransition` 或 `useNow` | 已有 |
| 图标 | `lucide-vue-next` + `@iconify/vue` + `@element-plus/icons-vue` | 已有 3 套，足够覆盖 |

### 新增库（2 个）

| 库 | 大小 | License | 用途 | 选用理由 |
| --- | --- | --- | --- | --- |
| `markdown-it` | ~140 KB（gzipped ~30 KB） | MIT | 02 步样本原文 / 05 步 `<PromptDisplay>` rich 模式的章节解析，把 `-Schema Constraints-` 这种段落标记转成结构化段落 | 老牌、活跃维护、API 简单；用于把 prompt txt 解析成段落树，比自己写 parser 健壮 |
| `prismjs` | ~25 KB（按需引入） | MIT | `<PromptDisplay>` raw 模式的语法高亮（暗色 IDE 风） | 业界事实标准，体积小，能把英文 prompt + 中文混排都高亮好；不引重的 monaco / shiki |

`<PromptDisplay>` 的 split 模式同步滚动直接用 **已有依赖** `@vueuse/core` 的 `useScroll`，不引新库。Prism 主题用 PrismJS 自带 CSS（`prism.css` / `prism-tomorrow.css` 任选其一），不引 `prism-themes` 包。

### 标注 IDE（02 步）的库选型决策

调研发现的几个候选：

- **`v-annotator-vue3`**（doccano `v-annotator` 的 Vue 3 移植）：能用但社区活跃度低（Star 17、最近发布 2023），且核心是"可视化关系箭头连线"——和我们要的"表单式实体/关系编辑"形态不匹配。**不采用**。
- **`vue3-annotator`**：小众包（依赖 SVG 绘制），定位更偏"在文本上画框"而非业务标注。**不采用**。
- **`vue-annotated-text`**：只做高亮渲染不做编辑，可以参考实现思路。**不采用，但保留思路**。

**最终决策**：02 步标注 IDE **不引第三方标注组件库**，原因有三：
1. 我们的标注形态是"表单 + 文本高亮"，不是"画框 + 关系连线"，第三方组件覆盖度都不到 50%，强行用反而要改写。
2. 实体表 / 关系表用 Element Plus 的 form / table 已经能完整表达，加上 `markdown-it` 解析样本原文 + `colord` 处理高亮颜色，自己实现的成本也只是中等。
3. 智能能力 A（AI 预填）/ C（关系候选）/ D（历史复用）都涉及深度的业务定制，第三方组件无法承载。

### 候选间评分对比的可视化（04 步）

排行榜的 composite 进度条、6 块指标方块、token 渐变条都用纯 CSS 实现，**不引图表库**。原因：

- 单值 + 进度条用 div + 线性渐变即可，引 ECharts / Chart.js 这类几百 KB 的库杀鸡用牛刀。
- 候选数固定 4-6 个，不存在数据量大需要交互式探索的场景。

如果将来需要展示候选间的多指标雷达图对比（v2 需求），再考虑引入 `vue-echarts`（`echarts` 6.x 已支持按需引入，雷达图模块 ~30KB）。

## 错误处理

- 02.1 / 02.2 脚本失败 → 折叠头部转红色，附"查看错误详情 / 重新运行"按钮，进度门控锁定。
- 02.3 标注保存失败 → toast 错误提示 + 自动重试 3 次；失败后样本卡顶部显示"保存失败，已暂存到本地"。本地暂存使用 **IndexedDB**（不用 localStorage，避免 5MB 配额限制和大 JSON 阻塞主线程），库名 `ckqa-prompt-annotation-drafts`，每条样本一条记录，主键 `${buildRunId}:${sampleId}`，字段 `{gold_entities, gold_relations, annotation_notes, savedAt}`。下次进入页面时，前端先拉服务端样本，再用 IndexedDB 中匹配的本地暂存覆盖未上传字段，并在样本卡顶部展示"本地有未同步的修改"提示和"重试上传"按钮。
- 03 候选生成失败 → 整页错误状态，附"重试 / 返回 02"。
- 04 评分中途失败 → 候选矩阵中失败行变红，弹窗"评分失败，已完成的候选可继续选定，或重新运行"。
- 05 finalize 失败 → toast + 保留页面状态，"重试"按钮。

## 测试策略

- **单元测试**：所有 prompt parser、关系类型过滤、候选译名映射、评分指标格式化。
- **组件测试**：每个步骤组件用 vitest 渲染，模拟典型 / 边界 / 错误状态。
- **路由测试**：URL query 与步骤、buildRunId、当前选定候选保持双向同步；浏览器后退 / 刷新不丢失草稿。
- **端到端测试**：Playwright 模拟完整 5 步流程，覆盖正常路径和"中途返回上一步" / "刷新页面" / "标注未保存离开"等。

## 已识别风险

1. **AI 预填可能误导用户**：候选实体的置信度从原 prompt 抽取得来，本身是要被审阅的，过度依赖会污染 gold。缓解：UI 上明确写"由 AI 识别 · 置信度 X"，且采纳前必须二次确认（不能批量"一键全采纳"），上方横幅按钮文案改为"按置信度排序"而不是"全部采纳"。需要在 PR review 中再次验证。
2. **历史标注复用的稳定键失配**：text_hash 对原文空白和标点敏感。如果上游 build_audit_extraction_set 的 text 规范化策略改了，会失配。缓解：后端 `_normalize_text_for_hash` 已用 `re.sub(r"\s+", " ", text).strip()`，只要这个函数不动就稳定；如要改需写迁移脚本。
3. **候选译名硬编码与候选数量增长**：本设计把 4 个候选的中文译名硬编码在前端，但后端 `manifest.json.candidates` 的数量未来可能扩展到 6+。缓解：在 manifest 中新增 `display_name_zh` 字段，前端显示时优先读 manifest，退化到本地 hardcode 表。
4. **04 评分时长不可预测**：full 模式 80 次大模型调用，慢的话 30+ 分钟。如果用户中途关浏览器，前端状态丢失，但后端任务仍在跑。缓解：评分启动后端立即写 task 状态到 build run，刷新页面后能从 `extraction-eval/status` 恢复进度；离开页面前 onbeforeunload 弹窗确认。
5. **prompt parser 容错不足**：rich 模式的 parser 对非标准 prompt 文本会渲染怪样。缓解：parser 检测段落数 < 2 / 单段超长时回退到 raw，且头部加提示。

## 实施分期

> 本设计文档专注 What 而非 How，分期建议供后续 writing-plans 阶段参考。

- **Phase 1**：路由 / 5 步骨架 / 01 沿用现状 / 05 命名入库（仅"本次构建"模式，跳过历史草稿入库）。
- **Phase 2**：02 步标注 IDE 主体（无智能能力）+ 后端样本/校准集 API。
- **Phase 3**：02 步智能能力 A（AI 预填）/ C（关系候选）/ D（历史复用）。
- **Phase 4**：03 步候选生成 + 勾选 + 抽屉预览。
- **Phase 5**：04 步候选矩阵 + 排行榜 + 详情抽屉。
- **Phase 6**：05 步历史草稿入库 + 01 步历史草稿种子打通 + `<PromptDisplay>` M 组合方案。
- **Phase 7**：错误处理与边界、E2E 测试、文案与视觉打磨。

## 引用

- 后端流水线源码：`graphrag_pipeline/scripts/prompt_tuning/`
- 现有前端组件：`frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`、`prompt-builder/*.vue`
- 标注后端实现：`backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java#saveCustomPromptDraft`
- 后端字段保留：`KnowledgeBaseBuildRunService.preserve_existing_gold_annotations`
- 浏览器伴侣 mockup 留档：`.superpowers/brainstorm/37793-1778770716/content/`
