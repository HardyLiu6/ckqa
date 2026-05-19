# Hybrid v0 Q029/Q031 离线小修记录

## 背景

本轮不调用 GraphRAG API / LLM，只复盘已有 `hybrid-v0-v6-oneshot-4q-20260516-2218` 与 BM25 bakeoff 结果，目标是解释 Q029 暴露的问题，并做能离线验证的最小修正。

## 暴露出的问题

1. Q029 的人类答案质量可用，但 raw gold evidence 命中不足。
   - 4 题 one-shot smoke 中，Q029 `semantic_coverage_f1=1.0`，答案能解释 I/O 管理、磁盘调度、文件系统的分层衔接。
   - 但 `citation_recall_at_3=0.25`、`selected_evidence_recall_at_3=0.25`，说明证据选择没有覆盖全部 gold refs。

2. runtime 问题分层过粗。
   - “如何衔接 / 共同服务于什么目标”这类跨概念结构题此前会落到 `low`，没有体现它们更接近 relation / overview 的性质。

3. runtime selector 没有吃到离线 bakeoff 中有效的 section-aware 信号。
   - v7 bakeoff 的 Q029 audit 显示 section-aware/decomposition 能把 raw gold 拉入 top10。
   - 但 hybrid runtime 的 v6 selector 仍主要用原文 BM25 文本，章节/小节标题没有参与加权。

4. raw gold 指标和人类答案质量存在口径差。
   - Q029 的新候选会选到同章节/同主题的 I/O 接口、磁盘性能、文件目录片段；这些对回答更有帮助，但不一定刚好等于 gold text unit。
   - Q031 在 gold audit 中被标为 `question_too_broad`，raw gold 未命中不一定等价于答案不可用。

## 本轮修正

1. 问题分层增加结构题触发词：
   - `衔接`
   - `串联`
   - `贯通`
   - `协同`
   - `共同服务`
   - `共同体现`

2. hybrid v6 evidence selector 增加可选 section-aware search text：
   - 复用 bakeoff 的 `build_search_texts`
   - 将 `chapter / section / subsection / heading_path_text` 以默认权重 4 加入 BM25 检索文本
   - 不修改 GraphRAG 原始 `output/**/text_units.parquet`
   - 32 题离线回归显示该策略对 global_overview 有退化风险，因此不作为默认路径启用。

3. multi-query RRF 增加可选 facet anchor：
   - 对 “X、Y 和 Z 如何衔接” 这类问题，保留每个子查询的首个候选。
   - dense rerank 之后仍保留这些 facet anchors，避免重排把多概念覆盖压成单一主题。
   - 该策略能改善人类视角的多概念覆盖，但 raw gold RR 有下降风险，因此默认关闭，只用于后续受控 smoke。

## 离线验证结果

验证命令不调用 LLM，只读取本地 `text_units.parquet`、`qa_test_set.jsonl` 和已有 raw 结果。

32 题回归：

| strategy | overall r@3 | overall r@5 | overall r@10 | overall rr | global r@3 | global rr | Q029 r@3/r@5/r@10/rr |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| old-like no section/no facet | 0.4813 | 0.6057 | 0.7083 | 0.5858 | 0.3542 | 0.5387 | 0.50 / 0.50 / 0.75 / 1.00 |
| section only | 0.5047 | 0.5771 | 0.7500 | 0.5540 | 0.1979 | 0.4208 | 0.50 / 0.50 / 0.75 / 0.50 |
| facet only | 0.4604 | 0.6344 | 0.7422 | 0.5662 | 0.1667 | 0.4354 | 0.50 / 0.75 / 1.00 / 1.00 |
| section + facet | 0.4839 | 0.6240 | 0.7422 | 0.5349 | 0.2604 | 0.3646 | 0.25 / 0.50 / 0.50 / 0.3333 |

结论：section/facet 都不能无脑默认启用。它们是诊断/受控策略，不是全局替换 v6 默认选择器的稳定升级。

Q029：

- 分层：`low -> mixed`
- top10 selected evidence：
  - `cdfd392e0705`
  - `ef931e4dc568`
  - `0e7191c372f1`
  - `99c0b8e4ffd1`
  - `e796c9dfd296`
  - `92258a2e0cd3`
  - `23140ef67ae0`
  - `13552f644730`
  - `29558a596b42`
  - `616795a1c017`
- raw gold recall：
  - `recall_at_3=0.25`
  - `recall_at_5=0.50`
  - `recall_at_10=0.50`
- 人类视角：
  - top3 覆盖 I/O 接口、磁盘性能/调度、文件目录/FCB/索引结点三个 facet。
  - 虽然 raw gold top3 没明显提升，但证据更适合支撑“如何衔接”的答案结构。

Q031：

- 分层：`low -> mixed`
- top10 selected evidence 覆盖并发、同步、死锁三个 facet。
- raw gold recall 仍为 0。
- 人类视角：
  - gold audit 已将该题标为 `question_too_broad`，当前更应先复核 gold 口径，而不是为了 raw gold 强行调参。

## 结论

本轮小修主要改善的是“证据覆盖形态”：让跨概念问题的候选证据更像人类写答案时需要的材料，而不是只追单个 text unit 的 BM25 分数。

离线指标说明：Q029 raw gold top5/top10 有改善空间，但 top3 仍受 gold 粒度影响；Q031 更像 gold/问题口径问题。更重要的是，32 题回归已暴露过拟合风险，因此 section-aware 和 facet anchor 必须保持默认关闭，后续只在 Q029/Q031 这类结构题上受控验证。

## 下一步建议

1. 若要真实 smoke，只跑 Q029/Q031 两题，不重跑已正常的 Q001/Q006/Q025。
2. 真实 smoke 前先保留当前 one-shot 主路径，不启用 Local fallback，不启用二次 synthesis。
3. 评估时同时看：
   - 人类答案是否按 I/O、磁盘调度、文件系统/并发、同步、死锁形成清晰结构。
   - `selected_evidence_recall_at_5/10` 是否上升。
   - raw `citation_recall_at_3` 若未上升，要结合 `gold_section_audit` 判断是否是 gold 过窄。
4. 后续可新增 expanded evidence 指标，把 parent/sibling/child refs 纳入诊断，但不要混入现有 `effective_score_experimental`。
