# GraphRAG 自动化提示词调优流水线

本文档描述 `graphrag_pipeline/` 内的“GraphRAG 自动化提示词调优流水线”现状与职责边界。当前已经有最小可运行脚本，后续再继续补齐更多自动评测能力。

## 作用

这条流水线用于把 GraphRAG 提示词迭代从“手工改 prompt、手工比结果”整理为可重复执行的实验流程，后续可逐步支持：

1. 候选提示词管理
2. 抽样数据与评测集管理
3. 信息抽取质量评估
4. 问答效果评估
5. 评估报告沉淀与最终 prompt 固化

## 输入

当前规划中的主要输入包括：

1. `pdf_ingest` 导出的课程标准化文档或 GraphRAG 输入 JSON
2. GraphRAG 当前正在使用的 prompt 模板
3. 人工整理的 prompt tuning 样本
4. 面向抽取或问答的评测集、参考答案与评分配置
5. 评测所需的 schema 说明文件

## 输出

当前已经实际落地的主要输出包括：

1. `prompts/candidates/<candidate>/` 下的候选 Prompt 与说明文件
2. `prompts/candidates/manifest.json` 中的候选元信息
3. `prompts/final/<candidate>/` 下的最终固化 Prompt
4. `prompts/final/active_prompt.json` 记录的当前激活结果
5. `results/extraction_eval/`、`results/extraction_raw/`、`results/errors/` 下的抽取执行产物
6. `results/reports/extraction_scoring/runs/<run_id>/` 下的对比报表、Top-K 结果与 run 元数据
7. `results/reports/extraction_scoring/history.csv` 与 `latest.json` 这类跨 run 汇总索引

## 目录职责

```text
graphrag_pipeline/
├── config/schema/              # 调优样本、评测配置、结果汇总等 schema 占位
├── data/prompt_tuning_samples/ # 人工整理的调优样本
├── data/eval/                  # 抽取评测、QA 评测数据集
├── prompts/candidates/         # 候选 prompt 模板
├── prompts/final/              # 经验证后固化并激活的 prompt 模板
├── results/extraction_eval/    # 抽取质量评测输出
├── results/qa_eval/            # 问答效果评测输出
├── results/reports/            # 汇总报告、对比结论、可读化产物
└── scripts/                    # 调优流水线脚本（根目录兼容入口 + 子目录实现）
```

## 当前已落地脚本

当前 `scripts/` 目录已经包含以下最小可运行脚本，其中真实实现主要收口在 `scripts/prompt_tuning/` 与 `scripts/extraction_eval/`，根目录同名脚本保留兼容入口：

1. `build_prompt_tuning_samples.py`
   - 从 `input/` 或导出目录整理 prompt tuning 样本。
2. `build_audit_extraction_set.py`
   - 从样本中抽取小规模、高价值 audit 校准集。
3. `generate_candidate_prompts.py`
   - 统一生成 `default`、`schema_aware`、`schema_fewshot`、`auto_tuned` 等候选 Prompt 与 manifest。
4. `run_graphrag_prompt_tune.py`
   - 封装 GraphRAG 官方 `prompt-tune`，整理输出到 `prompts/candidates/auto_tuned/`。
5. `finalize_candidate_prompt.py`
   - 把选定候选复制到 `prompts/final/<candidate>/`，并更新 `.env` 中当前活动 Prompt 路径。
   - 写出 `prompts/final/active_prompt.json` 作为当前激活结果记录。
   - 当候选目录缺少某些可选 Prompt 时，自动回退到 `prompts/` 下默认模板。
6. `score_extraction_results.py`
   - 读取 `results/extraction_eval/*.json`，对候选 Prompt 做规则化自动评测。
   - 计算 8 项硬指标 + 2 项 audit 软指标，输出 composite score、排序与 top-k。
   - 产物全部落在 per-run 布局下：
     - `results/reports/extraction_scoring/runs/<run_id>/` 下 `extraction_compare.csv` / `.md`、`top_candidates.json`、`run_meta.json`
     - `results/reports/extraction_scoring/history.csv`（append-only，跨 run 一行一候选）
     - `results/reports/extraction_scoring/latest.json`（指向最新 `run_id`，可用于脚本化定位当前结果）

这些脚本都放在 `graphrag_pipeline/scripts/`。仓库根目录 `scripts/` 只保留仓库级工具，不再放模块专属脚本。

## 当前约束

1. 当前活动 Prompt 由 `.env` 和 `prompts/final/active_prompt.json` 协同记录；若候选已确认，需要显式执行 `python scripts/finalize_candidate_prompt.py --candidate <name>` 才会影响后续 `graphrag index`。
2. `finalize_candidate_prompt.py` 默认优先固化候选目录中现有的 Prompt 文件；候选缺失时，只对可选项回退到默认模板，不会替你补齐不存在的主候选。
3. 当前脚本已经覆盖“样本准备、候选生成、prompt-tune 封装、最终 Prompt 固化、抽取执行、规则化评测”主链路，但 QA 自动评测仍然相对薄弱。
4. 现有 `prompts/`、`results/`、`utils/` 继续按原用途工作；调优脚本只在对应子目录内产生产物。

## QA 评测

`scripts/qa_eval/` 提供「规则 + LLM 裁判」双层评测：

- 本流程默认复用 `graphrag_pipeline/output/` 下已有 parquet 与 LanceDB 查询产物；运行 QA baseline 不会执行 `graphrag index`。
- `data/eval/qa_test_set.jsonl`：不少于 30 道题，由 `test_set_validator.py` 校验；`gold_text_unit_ids` 字段建议非空率不低于 80%。
- `run_baseline_eval.py`：本地 GraphRAG API `/v1/chat/completions` 串行打四模式，写 `runs/<run_id>/raw/`；排查慢查询时可用 `--max-items` 先跑小样本诊断。
- `latency_reporter.py`：读取 baseline raw 产物，生成 `latency_breakdown.md/.csv/.json`，按模式、题型和慢请求拆分耗时。
- `baseline_scorer.py`：规则评分，输出 `entity_hit_rate / must_cite_hit / citation_format_present / negative_hit / length_score / info_density`，按（题型 x 模式）聚合。
- `text_unit_lookup.py` + `judge_scorer.py`：通过外部裁判 LLM（默认 `gpt-4o-mini`，可通过 `GRAPHRAG_JUDGE_MODEL` 切换）计算 `semantic_correctness / faithfulness / retrieval_precision`。
- `baseline_reporter.py`：合并两层评分，生成 `scoring.md`、`combined.csv` 与规则层 `rule_scoring.csv`。
- `manual_review_template.py`：自动注入指标均值、读取 `hypotheses.md` 生成「假设验证」与「hybrid 路由建议」槽位，供人工复核。

入门顺序：先确认现有 `output/` 完整并通过四模式 smoke query，再在 `results/qa_eval/hypotheses.md` 写至少 4 条可证伪假设，随后跑 baseline，最后在 `manual_review.md` 按假设验证格式给结论。
