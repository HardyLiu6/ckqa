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

> **Phase 4.5 已落地**：3 张种子卡的可用状态由 `GET /seed-availability` 决定；
> graphrag_tuned 当且仅当本次 build run 选材已有自动调优产物时可选；
> history_draft 由 `PromptDraftsService.countByKnowledgeBaseId` 决定可用：count > 0 时可点开抽屉选具体草稿（Phase 6 已落地）。
> seed 真正影响 03 步候选生成的底板（schema_aware_directional_v2 / schema_fewshot_distilled_v2_strict_tuple
> 这两族候选根据 seed 选用不同的 base_prompt）。

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

仅运行 full 模式（每候选 20 条 audit 样本，所有候选并发执行）。页面有两种时态：

**时态 ① 评分进行中（候选矩阵）**

- **顶部进度摘要**：横向 3 列，左为 "32 / 80 LLM 调用 · 已用 6 min · 预估剩余 < 1 min"（流水线模式：一个候选进入评分后释放抽取资源，下一个立即开始抽取），中为 "~ 192k tokens 已消耗 / 预估总量 480k"，右侧"中止评分"按钮。
- **候选矩阵**：每行一个候选，流水线调度（同一时刻最多 1 个在抽取 + 1 个在评分），列网格：候选名 / 抽取进度条 / 抽取状态 chip / 评分进度条 / 评分状态 chip / 完成后的综合分 chip。
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
   - **Phase 4 已落地缓解（路径 B：后端拼装）**：后端 `CandidateMetadataLookup` 静态硬编码 4 个候选的 `displayNameZh / category / isRecommended / traits`，与算法产物解耦；前端 mock / 单测无需自维护译名表。Phase 7+ 引入 `GET /relation-schemas` 时一并迁移到 manifest 配置或 schema 配置层。
4. **04 评分时长不可预测**：full 模式 80 次大模型调用，慢的话 30+ 分钟。如果用户中途关浏览器，前端状态丢失，但后端任务仍在跑。缓解：评分启动后端立即写 task 状态到 build run，刷新页面后能从 `extraction-eval/status` 恢复进度；离开页面前 onbeforeunload 弹窗确认。
   - **Phase 5 已落地缓解**：`prompt_tune_extraction_eval_runs` 表持久化 pending/running/extracting/scoring 全链路状态；前端按 `recommendedPollingIntervalMillis=1500ms` 轮询 `/extraction-eval/status` 恢复进度；服务重启时 `ExtractionEvalStartupRecovery` 三态分流（pending → 重派发 / running → failed / cancelling → cancelled）；中止评分走 `POST /extraction-eval/cancel` 候选边界软取消，前端持续轮询直到 cancelled 终态。
5. **prompt parser 容错不足**：rich 模式的 parser 对非标准 prompt 文本会渲染怪样。缓解：parser 检测段落数 < 2 / 单段超长时回退到 raw，且头部加提示。
   - **Phase 4 已落地缓解（候选 manifest 绝对路径压缩）**：`auto_tuned` 候选的 `base_prompt_source` 在 manifest 中是绝对路径（含 `/home/...`），后端 `CandidateManifestReader.simplifyBasePromptSource` 在透传给前端前压缩为文件名 / 相对路径，避免把服务器路径暴露到浏览器。
   - **Phase 6 已落地缓解**：`PromptDisplayRaw` 换成 prismjs 自定义 `prompt-tune` 语言（section / placeholder / arrow / comment / keyword 五类 token + tomorrow 暗色主题）；`parsePromptSections` 容错单测覆盖"段落数 < 2 / 单段超长"两条 fallback raw 路径，PromptDisplay 据此切到 raw 模式并在头部条提示。
6. **当前进度持久化未覆盖离线 / 并发 / 长事务**（Phase 2b/2c-pre 已落地的现状）：
   - **离线场景**：`persistFields` 失败时会回滚本地状态 + toast，用户输入会丢；`EntityEditor` / `RelationEditor` 提交后立即 reset，PUT 失败时表单内容也丢。缓解：留到 Phase 7 用 IndexedDB 暂存 + 编辑器持久化成功后再 reset。
   - **并发场景**：`prompt_tune_audit_samples` 没有 `version` 字段，多标签页同时编辑同一样本时"最后写入获胜"。缓解：留到 Phase 8 加 `@Version` 乐观锁。
   - **build run 归档**：外键 `fk_audit_samples_build_run` 是 `ON DELETE RESTRICT`，归档/删除 build run 时会被 audit 样本阻塞。缓解：留到 Phase 8 决策"软归档" vs "导出后清理"。
   - **后端长耗时同步阻塞**：`regenerateAuditSet` 在 controller 线程内同步等 ≤5 分钟（Python 子进程超时上限），并发请求高时会撑满线程池。缓解：留到 Phase 8 仿照 `PromptTuneWorker` 拆成异步任务 + 状态轮询。
   - **AI 单样本抽取（02.3）同步阻塞 90-180 秒**（Phase 3 落地后暴露的边界）：当前 `POST /audit-samples/{sampleId}/ai-suggestions` 在 controller 线程同步等 5 分钟（`SingleSampleExtractionOrchestrator.EXTRACT_TIMEOUT`）。问题表现为 ① 用户在抽取过程中关闭浏览器/网络抖动，HTTP 连接断开但 Java 子进程仍跑完 + 写入 `ai_suggested_*` 字段，前端再进入时拿到结果但缺少"任务正在跑"的指示；② axios timeout 必须单独配 5 分钟（已落地），但若用户多个 sample 同时点"生成候选"，Tomcat 工作线程会被几条长任务占满阻塞短请求。缓解：留到 Phase 8 仿照 `PromptTuneWorker` 模式拆成 `pending → running → success/failed` 异步任务，POST 立即返回 202 + taskId，前端轮询 `GET /ai-suggestions/status?sampleId=X`；同时利用 Phase 3 已落地的 `ai_suggested_*` 持久化字段，把任务的 running 状态作为 sample 三态（none / running / done）展示给前端，刷新页面后能从 DB 恢复 running 横幅。详见 Phase 8 落地计划。
7. **AI 候选与已采纳实体重复**（Phase 3 持久化方案落地后暴露的边界）：
   - 用户点"重新生成"或"AI 候选去重已采纳实体"未启用时，原文里被反复抽出的同一实体会作为新候选再次出现，与已采纳的 goldEntities 视觉上重复。
   - 当前 Phase 3 的边界是"用户可以手动拒绝重复候选"，候选与 gold 是两条独立卡片，行为正确但体验略冗余。
   - 缓解：留到 Phase 7 加"AI 候选去重已采纳实体"——后端在 `markEntitiesAsAiSuggested` 之后过滤掉与 sample.goldEntities 同名（或 name+type）已存在的候选，并对关系做相同处理。详见 Phase 7 落地计划。

8. **04 步评分进度只能在候选边界回写 DB，前端长时间停在 0**（Phase 5 落地后暴露的边界）：
   - 现状：`ExtractionEvalWorker.runInternal` 调 `runSingleCandidateExtract(...)` 阻塞跑一个候选的全部样本（典型 ~8 min / 候选），中间只在候选完成时写一次 DB（`finished_candidates` / `extracting_candidate_id`）；service 投影 status 时 `extract.finished` 是"二值"——`finished.contains(id) ? sampleTotal : 0`。
   - 表现：前端轮询 `recommendedPollingIntervalMillis=1500ms`，但拿到的 `extract.finished` 在该候选完成前永远是 0；用户能看到"已用时 47s"在跳，但分子 0/分母 N 长时间不动，看上去任务卡住。
   - 另一个边界：`build_audit_extraction_set.py --sample_size` 默认 20，但当原始 `prompt_tuning_samples.json` 总条数 < 20 时（例如教师只标了 2 条），`audit_with_gold.json` 实际只产 2 条；后端 service 之前**硬编码** 20 作为分母，UI 显示 0/20 永远跑不到分母。
   - **Phase 6.5 已落地缓解**：
     - SQL 加 `prompt_tune_extraction_eval_runs.sample_total` 列；`ExtractionEvalWorker.runInternal` 启动时读 `audit_with_gold.json` 真实长度回填，service 投影时用此值替换硬编码 20，分母随 build_audit_extraction_set 实际产出动态走（旧记录或回填失败时仍按 20 兜底）。
     - service 在 `projectStatus` 对当前 `extracting_candidate_id` 的候选按 `elapsedSeconds` 推算"估算 finished"——用 `已用秒数 / 单样本预估秒数`，限 0..sampleTotal-1，永远不到分母（避免抢真值）；overall 的 `finishedCalls` / `tokensUsed` / `estimatedRemainingSeconds` 同步按 sampleTotal 比例缩放。
     - DTO `ExtractionEvalStatusResponse.Stage` 加 `estimated:Boolean` 标志，前端 `CandidateMatrixRow` 在估算值后追加「（估算）」徽标，避免误以为是真值。
   - **未覆盖的短板（路线图：Phase 7「04 步评分样本级真进度」）**：仍是候选粒度的"估算型"反馈；要做样本级真进度，需要让 Python `run_native_extraction.py` 每完成 1 个样本写 `progress.json`（`{finished:N}`），Java worker 起 watcher 线程定期读 progress.json → 写 DB；候选边界软取消的 race 处理需要小心。详见 Phase 7 § "04 步评分样本级真进度" 落地计划。

9. **04 步评分汇总失败时「重试」会重跑 30+ 分钟抽取**（Phase 5 落地后暴露的边界）：
   - 现状：`ExtractionEvalWorker.runInternal` 抽取阶段全部完成后（`finished_candidates` 含全部候选）才进 scoring；如果 `orchestrator.runScoring(...)` 失败（如 Python 脚本异常 / 评分汇总报告损坏），整个 evalRun 进 failed 终态。
   - 前端「重试」按钮直接调 `triggerNewEval` → service `trigger` → 没有 active run（已 failed）→ 创建新 evalRun 重头跑抽取阶段。已在磁盘上的抽取产物（`${GRAPHRAG_ROOT}/results/extraction_eval/runs/<oldRunId>/*.json`）不被复用，浪费 30+ 分钟 LLM 调用。
   - **Phase 5.1 已落地缓解**：
     - DTO `ExtractionEvalStatusResponse.recoverableScoringOnly:Boolean`：service 投影时若 `status=failed && progress_stage=scoring && finished_candidates 非空`，置 true。
     - 新端点 `POST /knowledge-base-build-runs/{id}/extraction-eval/retry-scoring`：service `retryScoring(buildRunId)` 复用最新 evalRun，重置为 running + progress_stage=scoring，dispatch 一个仅跑 scoring 的轻量任务（`ExtractionEvalWorker.runScoringOnly`），跳过抽取段。worker 启动前校验 `sharedExtractDir` 仍在，缺失则回退 markFailed 让 caller 走全量重跑。
     - 前端 `PromptBuilderScoringStep` 在 `recoverableScoringOnly=true` 时把单按钮「重试」拆成「仅重跑评分」+「重新抽取并评分」二选一；后端拒绝（产物已被清理 / 任务非 failed 等 4106 + 409）时降级走全量重跑。
     - DTO `ExtractionEvalStatusResponse.lastSuccessfulEvalRunId:Long`：service 投影非 success 终态时附带最近一次 success 的 evalRunId；前端在失败 / 取消 / 中止终态显示「查看上次评分结果」按钮，调 `GET /extraction-eval/report?evalRunId=X` 拉历史 success 报告，让用户在不重跑的前提下查看 / 选定上次最佳候选。`getReportByEvalRunId` 校验 evalRun 属于该 buildRun 且 status=success，否则抛 4106。
   - **未覆盖的短板**：worker 在 `markFailed` 后没主动清理 `sharedExtractDir`，所以"产物未清理"是默认状态；下次 `runInternal` 启动时清理逻辑（`deleteIfExists(sharedExtractDir)`）只针对当前 runId，不影响旧 evalRun 的产物。如果手工清理 `${GRAPHRAG_ROOT}/results/extraction_eval/runs/` 或服务重启 + 启动恢复把任务标 failed 再清理，仅重跑会拒绝并降级。这条边界本期接受作为已知技术债。


## 实施分期

> 本设计文档专注 What 而非 How，分期建议供后续 writing-plans 阶段参考。
>
> **状态标记说明：** ✅ 已落地 / 🟡 部分落地 / ⏸ 未开始

### 已完成阶段

- **Phase 1（✅）：路由 / 5 步骨架 / 01 沿用现状 / 05 命名入库（仅"本次构建"模式，跳过历史草稿入库）。**
- **Phase 2a（✅）：DB 迁移（`prompt_tune_audit_samples` + `prompt_drafts` 表）+ Java Entity/Mapper/Service 骨架 + Controller 501 占位 + 前端 API 桩。**
- **Phase 2b（✅）：02 步标注 API 后端实现（`/audit-set` / `/audit-samples` GET、PUT）+ 前端去 mock 接入真实 API + 跨 build run 历史标注复用合并（数据层 silent 完成）+ force 防误删。**
- **Phase 2c-pre（✅）：02 步实体/关系手动新建（`EntityEditor` + `RelationEditor` 组件）+ 关系类型动态过滤（C 智能能力的同步部分）+ 删除实体级联清理关系 + 自环禁止。**

- **Phase 4.5（✅）：01 步种子分流真正影响 03 候选生成（仅覆盖 01-03 步业务逻辑；前端展示同步延伸到 05 步来源记录）**
  - 新增 `GET /knowledge-base-build-runs/{id}/seed-availability` 端点 + `SeedAvailabilityService`，
    01 步据此决定 `graphrag_tuned` 是否可点；不可用时附 tooltip 引导回知识库构建向导触发自动调优。
  - 01 步选 seed 时立即 PUT `customPromptDraft.seed`（仅 seed 子字段，不动 prompts），让 03 步后端能读到正确的种子。
  - `CandidateService.generate` 解析 build run metadata 取 seed → `CandidateGenerationOrchestrator.run`
    接受 `BaseOverride`：`system_default` 强制 fallback 到 default 分支；`graphrag_tuned` 先用 `probeBySelection`
    校验 cache success 状态、再用 `findReadyByCacheKey` 拿目录路径；`null` 走 Phase 4 兼容路径不附加 `--auto_tuned_prompt_dir`。
  - 候选生成结果旁路文件 `seed-info.json` 落盘（OffsetDateTime 含时区偏移）；`CandidateManifestReader` 注入
    `CandidateResponse.seed` 字段；POST 路径下 `CandidateService.withInjectedSeed` 直接用本次计算的 seed
    覆盖 reader 可能带回的旧值，保证写盘失败时响应仍含正确 seed。
  - 4109 错误码：`seed=graphrag_tuned` 但 prompt-tune cache 失效时拒绝候选生成。
  - **04 步评分对 seed 的感知（评分表 seed 列、报告透传）已移交 Phase 5 落地，本期不实施。**
  - 测试：后端 388 + 1 skipped PASS，前端 327 单测 + 3 个 e2e PASS。

### 进行中 / 计划阶段

- **Phase 3（⏸）：02 步剩余智能能力**
  - **A. AI 预填实体/关系**：实现 `POST /audit-samples/{sampleId}/ai-suggestions` 端点，前端紫色 ✨ 横幅 + AI 候选卡片审阅交互；候选实体扩展 `spanStart`/`spanEnd` 字段。
  - **D. 历史复用 banner UI**：把 Phase 2b 已经入库的 `reused_from_build_run_id` 渲染成 02 步绿色 ♻ 横幅"发现 N 条已有标注来自 <buildRunName>"。
  - **拖选原文添加实体**：原文卡的 `mouseup` 选区监听 + 浮动按钮"添加为实体"；选区计算 spanStart/spanEnd 写入实体；已确认实体在原文中渲染紫色高亮。
  - **关系自动反向**：A→B 在 schema 中只允许 B→A 时，UI 自动调换源/目标并提示。

- **Phase 4（✅）：03 步候选生成 + 勾选 + 抽屉预览**
  - 实现 `POST /candidates` / `GET /candidates` / `GET /candidates/{candidateId}/prompt` 端点（包装 `generate_candidate_prompts.py`）。
  - 后端 4104 门控（02 步至少 1 条 completed）+ 4105 未生成契约（HTTP 404 + envelope）+ 5007 契约漂移检测；DB gold 字段在 `AuditWithGoldExporter` 做 camelCase → snake_case 归一化，缺 `entity_id` 时按 `auto_e_<sampleId>_<idx>` 兜底。
  - 候选展示元数据由后端 `CandidateMetadataLookup` 硬编码 4 个候选（`default` / `auto_tuned` / `schema_aware_directional_v2` / `schema_fewshot_distilled_v2_strict_tuple`），并在 `simplifyBasePromptSource` 把绝对路径压缩成 manifest 友好的相对/文件名片段。
  - 前端 `PromptBuilderCandidatesStep` 五态（loading / error / blocked-by-gate / empty / ready）+ ready 态"重新生成候选"按钮 + 推荐徽章 + 抽屉懒加载 prompt 文本；axios 拦截器把后端 ApiResponse envelope 的业务码提到 `err.code` 顶层，确保 4104 / 4105 在 component 层可识别。
  - 测试：后端 362 测试 PASS，前端单测 325 PASS，Playwright e2e 9/9 PASS（覆盖五态 + 重新生成 + 抽屉懒加载）。

- **Phase 5（✅）：04 步候选矩阵 + 排行榜 + 详情抽屉**
  - 实现 `POST /extraction-eval` / `/status` / `/report` / `/cancel` 四个端点（异步任务化，仿 PromptTune 模式）。
  - 新增 `prompt_tune_extraction_eval_runs` 状态表 + `ExtractionEvalAsyncConfig`（独立线程池 corePoolSize=1，串行评分避免限流）。
  - 评分产物按 build run 隔离到 `<workspace>/eval/<evalRunId>/`，worker 跑完复制 + 清理共享磁盘路径，多 build run 互不污染。
  - 三重门控：02 ≥1 completed（4104）+ 03 候选已生成（4105）+ selectedCandidates ⊆ 已生成（4108）；前端在 onMounted 检查 selectedCandidates query 缺失 → 引导回 03 步。
  - 报告 gate.passed 在后端按 spec 阈值（0.8 / 0.5 / 0.5 / 0.95）重新计算，与脚本严格 GATE_THRESHOLD=0.95 解耦；F1 由 recall + precision 重算。
  - **整体终态语义**：finished 全空 → 整体 failed；finished 非空且 scoring 完成 → 整体 success（含部分候选 failed 的情况）。失败候选通过 `candidate_failures` JSON 列结构化持久化，由 `ExtractionEvalReportResponse.failedCandidates` 透传给前端，在排行榜下方"未进入排名"区域展示。
  - 服务启动恢复：`ExtractionEvalStartupRecovery` 三态分流（pending → 重新 dispatch / running → failed / cancelling → cancelled），避免僵尸任务卡住后续 trigger 复用逻辑。
  - 前端 5 态（loading / blocked / running / done / failed），轮询 1500ms，终态停止；中止按钮调 cancel 端点（候选边界软取消，不强杀子进程），worker 在下一个候选切换时落 cancelled 终态；前端 cancel 后继续轮询直到终态。
  - **承接 Phase 4.5 的 seed 透传**：表 schema 直接含 `seed` 列；`ExtractionEvalService` 启动评分时把 build run metadata.customPromptDraft.seed 写入快照；`ExtractionEvalReportResponse` 顶层透传 seed，前端可展示"本次评分基于哪个种子的候选"。
  - 测试：后端 427 PASS / 1 skipped（Phase 5 新增 ExtractionEvalOrchestratorTest 3 / ExtractionEvalReportAssemblerTest 11 / ExtractionEvalWorkerTest 6 / ExtractionEvalServiceTest 11 / ExtractionEvalStartupRecoveryTest 4）；前端单测 334 PASS（含新增 7 个 API 单测）；Playwright e2e 37 PASS / 4 skipped（含新增 7 个 04 步 e2e 覆盖五态 + 排行榜交互 + 中止 + 详情抽屉）。

- **Phase 6（✅）：05 步历史草稿入库 + 01 步种子打通 + PromptDisplay raw prismjs 高亮**
  - 实现 `POST /knowledge-base-build-runs/{id}/finalize` 端点：从 04 评分 run 取选定候选 prompt + composite_score；FinalizePromptService **直接写** `buildMetadata.customPromptDraft`（不复用 saveCustomPromptDraft），落库**完整 finalize 快照**：`seed` / `selectedCandidateId` / `compositeScore`（4 位小数）/ `sourceEvalRunId` / `finalizedAt`（ISO_OFFSET_DATE_TIME）/ `prompts.extract_graph.content`；当 `saveAsDraft=true` 时同事务插一条 `prompt_drafts` 行（保存范围两模式）。事务语义闭环：`promptDraftsService.save` 返 false 抛 5000 让 customPromptDraft 一并回滚（不依赖 mybatis-plus 静默 false）。
  - 实现 `GET /knowledge-bases/{kbId}/prompt-drafts` 端点：本期重新定义为**列表摘要语义**，返回该 kb 下按 created_at 倒序的 `PromptDraftResponse`（**不含 promptsJson 大字段**），由 `PromptDraftListService` 把 entity 投影成 DTO；mapper 层用 `select` 排除 `prompts_json` 列，避免 600 KB+ 列表响应；草稿详情接口（`GET /knowledge-bases/{kbId}/prompt-drafts/{id}` + `PromptDraftDetailResponse`）留 Phase 7+。
  - 错误码：4110 `EXTRACTION_EVAL_NOT_SUCCESS`（finalize 时 04 评分尚未 success 拒绝）、4111 `INVALID_FINALIZE_CANDIDATE`（candidateId 不在评分报告 candidates 中）；reportJson 解析失败抛 5000 `INTERNAL_ERROR`（区分服务端数据异常与业务入参错误）。
  - `SeedAvailabilityService.buildHistoryDraft` 真实化：注入 `PromptDraftsService.countByKnowledgeBaseId`，count > 0 时 available=true / summary 含数量；count = 0 时 reason="no_history_draft" / summary 友好文案，替代 Phase 4.5 占位 "phase_6_not_implemented"。
  - 前端 `PromptBuilderPage.handleSave` 真发 `POST /finalize`；`onMounted` 调 `listPromptDrafts(kbId)` 填充 `historyDrafts`。
  - 新组件 `PromptBuilderHistoryDraftDrawer.vue`：当 ≥2 条历史草稿时打开抽屉让用户选具体一条；count=1 时直接选取；count=0 时禁用并提示。`loadHistoryDraft` **不**恢复旧 `selectedCandidateId`，主动清空旧 03 / 04 步状态，与 spec § "history_draft 仅是种子来源、不复用旧候选 ID" 一致；`historyDraftId` 仅做前端记账，finalize 仍由 04 评分报告中的 candidateId 决定。
  - `PromptBuilderSeedStep.vue` 删硬编码 "Phase 1e 开放" 文案，按 availability 动态显示数量；reason 文案分支 `phase_6_not_implemented` 替换为 `no_history_draft`。
  - `PromptDisplayRaw.vue` 换 prismjs：自定义 `prompt-tune` 语言识别 section / placeholder / arrow / comment / keyword（课程域实体类型 hardcode 列表），引入 prism-tomorrow 暗色主题；不依赖 prismjs/components/prism-* 任何内置语言以避开 vite 全局 attach 兼容问题。
  - `parsePromptSections` 容错单测落地（spec § 风险 #5）：段落数 < 2 / 单段超长两条路径，验证 caller 据此决定 `fallbackToRaw`。
  - 测试：后端 442 PASS / 1 skipped（Phase 6 新增 16 条：PromptDraftsServiceImplTest 4 + SeedAvailabilityServiceTest 新增 2 + FinalizePromptServiceTest 8 + PromptDraftListServiceTest 2），前端单测 345 PASS（Phase 6 新增 11 条：finalize 4 + prompt-drafts 3 + parser 4），Playwright e2e 42 PASS / 4 skipped（Phase 6 新增 4 条：finalize 两模式 + 历史草稿空态 / 抽屉选择）。

### 鲁棒性与扩展阶段（Phase 7+）

#### Phase 7：进度持久化与离线鲁棒性

把当前的"触发即 PUT、刷新即 GET、失败本地回滚"基础升级为对网络抖动和长时间标注会话稳健的体验。

- **IndexedDB 离线暂存（spec § 错误处理已记）：**
  - 库名 `ckqa-prompt-annotation-drafts`，主键 `${buildRunId}:${sampleId}`，字段 `{gold_entities, gold_relations, annotation_notes, savedAt}`。
  - `persistFields` 失败时（Phase 2b 当前是 toast + 回滚本地状态），改为：toast + **保留本地编辑** + 写 IndexedDB 暂存 + 自动重试 3 次。
  - 进入页面时先拉服务端样本，再用 IndexedDB 暂存覆盖未上传字段，样本卡顶部展示"本地有未同步修改"提示和"重试上传"按钮。
- **EntityEditor / RelationEditor 提交后表单持久化：**
  - 当前 Phase 2c-pre 提交即 reset 表单，PUT 失败时用户输入丢失。
  - 改为：父组件 `handleCreateEntity` / `handleCreateRelation` 持久化成功后再 emit `clear` 让编辑器 reset；失败时编辑器保留输入并显示重试按钮。
  - 这需要把当前 emit/reset 的同步关系改成 promise/callback 链路（编辑器 props 接收 `onSubmit: async (payload) => boolean`）。
- **离线标注会话恢复脚本：**
  - 提供"清理本地暂存"和"导出本地暂存为 JSON"两个开发者工具入口（在样本列表右上角"⋯"菜单里），用于排障。

- **AI 候选去重已采纳实体（spec § 风险 #7）：**
  - 现状：Phase 3 持久化方案落地后，"重新生成"或自动复用历史 build run 的候选时，被用户已采纳进 goldEntities 的实体会作为新候选再次出现，视觉冗余。
  - 后端 `AiSuggestionService.markEntitiesAsAiSuggested` 在生成候选最后一步加过滤：`sample.goldEntities` 已存在同名（或同名 + 同类型）的实体直接从候选数组剔除；同时把使用该实体名作为 source/target 的关系候选也剔除。
  - 是否按 `name + type` 双键匹配可配置（默认 `name` 单键，避免 LLM 抖动改了类型导致漏过滤；用户在偏好设置开启严格模式后切到双键）。
  - 测试：单测覆盖"已采纳实体过滤"、"关系两端引用已采纳实体仍保留"、"已采纳实体名称改变后不再误过滤"三条路径。
  - 这一步落在 Phase 7 而不是 Phase 3 的原因：Phase 3 的核心是把 AI 候选链路打通（端到端 + 持久化），去重属于体验优化；过早做去重会让"我刚才采纳的怎么不见了"的回归测试变复杂。

- **04 步评分样本级真进度（spec § 风险 #8）：**
  - 现状（Phase 6.5 已落地）：service 在 `projectStatus` 中按 `elapsedSeconds` 推算"估算 finished"，前端在估算值后追加「（估算）」徽标。仍是候选粒度，不是真实推进。
  - 目标：把 worker 阻塞跑 sampleTotal 个样本的过程拆成"每完成 1 个样本就把进度写回 DB"，前端拿到的就是真值。
  - 实现路径（设计草案，留 Phase 7 实施时再细化）：
    1. **Python 写进度文件**：`graphrag_pipeline/scripts/extraction_eval/run_native_extraction.py`（或其当前调用方）每完成 1 个样本就 atomic 写一次 `<workspace>/eval/<evalRunId>/progress/<candidateId>.json`，内容形如 `{"finished": N, "total": M, "lastSampleId": "...", "ts": "..."}`。atomic 写 = `tmp + rename`，避免 Java watcher 读到半截 JSON。
    2. **Java watcher 线程**：`ExtractionEvalWorker` 在派发 `runSingleCandidateExtract` 之前起一个 `ScheduledExecutorService.scheduleAtFixedRate`（间隔 1.5s，与前端轮询同源），周期性读 `progress/<currentCandidateId>.json` → `evalRunsService.updateById` 写入新增字段 `current_extract_finished:int`；候选切换或软取消时停止 watcher，避免 race。
    3. **DB 字段**：`prompt_tune_extraction_eval_runs` 新增 `current_extract_finished int` 列，service 投影时优先用此真值，缺失（旧记录或 watcher 未启动）才回退 Phase 6.5 估算逻辑；DTO `Stage.estimated` 在用真值时设 false。
    4. **候选边界软取消的 race 处理**：worker 在 `cancelling` 状态检测到时先 `shutdownNow()` watcher，避免 watcher 在候选 done 后继续读已删除的 progress.json 报错；watcher 异常一律 swallow + 写 log（不能阻塞主流程）。
    5. **Phase 5 已有的"候选边界更新 finished_candidates"保留**：watcher 写的是"当前候选内部进度"；候选完成时仍由 worker 主流程在事务里把 candidateId 追加到 `finished_candidates`，并把 `current_extract_finished` 清零给下一候选用。
  - 测试：mock progress.json 文件读写，验证 ① watcher 周期读 → DB 写；② 候选切换时清零；③ progress.json 损坏/缺失时 fallback 估算。
  - 与 Phase 8「AI 单样本抽取异步化」的协同：Phase 8 的 `prompt_tune_ai_suggestion_tasks` 表只管"任务级"状态机（pending/running/done），与本 watcher 关心的"任务内部样本进度"是两层；二者不冲突，本期只动 04 评分这一处。
  - 风险：watcher 与 Python 子进程共享磁盘，若 worker JVM 与 Python 跑在不同容器，需要确认共享 volume；本期评估留给 Phase 7 实施时根据部署形态决定。

#### Phase 8：并发安全与 build run 生命周期

当前 DB 隔离假设单用户、单标签页操作，需要补齐多端并发与 build run 归档。

- **样本级乐观锁：**
  - `prompt_tune_audit_samples` 表新增 `version` 字段（`@Version` 注解），`updateById` 由 MyBatis-Plus 自动带 WHERE `version = ?` 子句。
  - PUT `/audit-samples/{id}` 收到 stale version 时返回 4104 `AUDIT_SAMPLE_VERSION_CONFLICT`，前端提示"该样本已被其他人/其他标签页修改，请刷新查看最新内容"。
- **`hasNonPendingSamples` + `replaceForBuildRun` 同事务化：**
  - 当前两步分开，理论上有"读到 false 但写前刚被标注"的窗口（毫秒级）。
  - 把 force 检查移到 `AuditSamplePersistenceService.replaceForBuildRun` 内部，与删除/插入同一事务。
- **Build run 归档级联策略：**
  - `fk_audit_samples_build_run` 当前是 `ON DELETE RESTRICT`，会阻塞 build run 删除。
  - 在 `KnowledgeBaseBuildRunService.archiveBuildRun` 中新增"软归档 audit 样本"逻辑：归档时把 `prompt_tune_audit_samples` 的 `build_run_id` 写到独立 `archived_build_run_id` 字段，外键改为 ON DELETE SET NULL；或者保留 RESTRICT 但归档时强制把样本表数据导出为 JSON 后清除。需要决策。
- **后端长耗时 API 异步化：**
  - 当前 `regenerateAuditSet` 在 controller 线程内同步等 5 分钟（`AuditPipelineOrchestrator.STEP_TIMEOUT`）。
  - 仿照 `PromptTuneWorker` 拆成 `pending → running → success/failed` 异步任务，前端轮询 `GET /audit-set/status`；同样适用于 Phase 5 的 04 步评分（80 次大模型调用，30+ 分钟）。
  - **AI 单样本抽取（02.3）同步阻塞 90-180 秒同步改造**：`POST /audit-samples/{sampleId}/ai-suggestions` 改为立即返回 202 + `taskId`。
    - 新增 `prompt_tune_ai_suggestion_tasks` 表（或复用通用任务表），字段 `task_id / sample_id / status / started_at / finished_at / error`。
    - 任务由独立线程池 worker 跑，复用 Phase 3 已落地的 `SingleSampleExtractionOrchestrator`，区别只是不在 controller 线程同步等。
    - 利用 Phase 3 已落地的 `ai_suggested_entities` / `ai_suggested_relations` 字段：worker 跑完直接写库，前端任意时刻刷新都能从 sample 查到最新候选；任务表只是给"running 横幅"用，做 UI 状态机。
    - 前端 `GET /audit-samples/{sampleId}/ai-suggestions/status` 返回 `{ status, startedAt, etaSeconds }`；02 步加载 sample 时同时拉这个状态决定横幅渲染（none / running / done）。
    - **跨页面恢复**：用户关浏览器再进 02 步，根据状态接口结果显示 spinner，不需要重跑。
    - **失败重试**：任务表保留 `error` 字段，前端 running → failed 时显示 toast + "重试"按钮，重试发起新 task 不污染历史。
    - **去重**：worker 启动前检查 sample 是否已有 running 任务，防并发重复触发。

#### Phase 9：错误处理边界、E2E、文案与视觉打磨

把分散在前面 phase 里"风险已识别但未做"的边界统一收口。

- **02.1 / 02.2 流水线失败前端 UI：**
  - spec § 错误处理已描述"折叠头部转红色 + 重新运行按钮"，当前简化为 toast。补齐红色错误条 + "查看错误详情"按钮（展开 stderr 摘要）。
- **AI 预填 PR review 验证（Phase 3 风险 #1）：**
  - 在 Phase 3 落地后单独跑一次"AI 预填会不会污染 gold"的回归测试：抽 10 条已人工标注样本，跑 AI 预填，对比 AI 候选与 gold 的 IoU 和置信度分布。
- **prompt parser 容错（spec § 风险 #5）：**
  - 在 Phase 6 `<PromptDisplay>` 落地后补"段落数 < 2 / 单段超长 → 回退 raw"路径的单测。
- **End-to-End Playwright 测试：**
  - 完整 5 步流程的浏览器自动化（含失败路径：刷新中断、并发修改、network 抖动）。
- **文案与视觉一致性扫一遍：**
  - 中文标点、空格、按钮文案动词时态对齐 spec § 视觉基调（"添加" / "采纳" / "已添加" 等的统一）。

#### Phase 10（可选）：Schema 后端化与 v2 评分可视化

低优先级，等业务真的需要再做。

- **`GET /api/v1/relation-schemas` 真实实现：**
  - 当前 controller 端点是 501 占位，前端用 hardcode `relation-types-model.js`。改为后端读 `graphrag_pipeline/config/schema/*.json` 返回，前端有缓存 + 1 小时 TTL。
- **候选间评分多指标雷达图：**
  - spec § 第三方依赖选型已说"如果将来需要展示候选间的多指标雷达图对比，再考虑引入 vue-echarts"。等用户在 04 步排行榜反馈"想横向对比 6 个候选的 6 个指标"再做。

## 已完成阶段的关键产物索引

执行 spec 时这些路径已经存在，新增功能不应重新发明：

- `frontend/apps/admin-app/src/views/pages/prompt-builder/`
  - `PromptBuilderPrepareStep.vue`（02 步主壳）
  - `AnnotationWorkArea.vue` / `AnnotationSampleList.vue` / `AnnotationEntityCard.vue` / `AnnotationRelationCard.vue`（02 步组件）
  - `EntityEditor.vue` / `RelationEditor.vue`（02 步行内编辑器，Phase 2c-pre）
  - `relation-types-model.js`（schema hardcode + filterRelationTypesByEndpoints）
  - `entity-id-generator.js`（本地 ID + 重名检测）
  - `prepare-step-api.js`（API ↔ 本地形态转换 + 三态 PATCH payload 构造）
- `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/`
  - `AuditPipelineOrchestrator.java`（Python 脚本包装）
  - `AuditSamplePersistenceService.java`（事务边界 + 历史复用合并）
  - `AuditSampleService.java`（业务编排）
  - `AuditSampleResponseMapper.java`（Entity → DTO）
- `backend/ckqa-back/src/main/java/org/ysu/ckqaback/controller/PromptTunePipelineController.java`（13 个端点，3 个真实实现 + 10 个 501）
- `sql/migrations/20260515_prompt_tune_pipeline.sql`（两张表）


## 引用

- 后端流水线源码：`graphrag_pipeline/scripts/prompt_tuning/`
- 现有前端组件：`frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`、`prompt-builder/*.vue`
- 标注后端实现：`backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java#saveCustomPromptDraft`
- 后端字段保留：`KnowledgeBaseBuildRunService.preserve_existing_gold_annotations`
- 浏览器伴侣 mockup 留档：`.superpowers/brainstorm/37793-1778770716/content/`
