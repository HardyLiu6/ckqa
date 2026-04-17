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

后续流水线的主要输出规划为：

1. 候选 prompt 版本及其元信息
2. 已验证的最终 prompt 集合
3. 抽取评测结果
4. QA 评测结果
5. 汇总报告、对比报表与推荐结论

## 目录职责

```text
graphrag_pipeline/
├── config/schema/              # 调优样本、评测配置、结果汇总等 schema 占位
├── data/prompt_tuning_samples/ # 人工整理的调优样本
├── data/eval/                  # 抽取评测、QA 评测数据集
├── prompts/candidates/         # 候选 prompt 模板
├── prompts/final/              # 经验证后准备固化的 prompt 模板
├── results/extraction_eval/    # 抽取质量评测输出
├── results/qa_eval/            # 问答效果评测输出
├── results/reports/            # 汇总报告、对比结论、可读化产物
└── scripts/                    # 调优流水线脚本
```

## 当前已落地脚本

当前 `scripts/` 目录已经包含以下最小可运行脚本：

1. `build_prompt_tuning_samples.py`
   - 从 `input/` 或导出目录整理 prompt tuning 样本。
2. `build_audit_extraction_set.py`
   - 从样本中抽取小规模、高价值 audit 校准集。
3. `generate_candidate_prompts.py`
   - 统一生成 `default`、`schema_aware`、`schema_fewshot`、`auto_tuned` 等候选 Prompt 与 manifest。
4. `run_graphrag_prompt_tune.py`
   - 封装 GraphRAG 官方 `prompt-tune`，整理输出到 `prompts/candidates/auto_tuned/`。

这些脚本都放在 `graphrag_pipeline/scripts/`。仓库根目录 `scripts/` 只保留仓库级工具，不再放模块专属脚本。

## 当前约束

1. 不改动 `settings.yaml`、现有生产 prompt 或 GraphRAG 主问答流程的既有行为。
2. 当前脚本以“样本准备、候选生成、prompt-tune 封装”为主，尚未补齐完整自动评测闭环。
3. 现有 `prompts/`、`results/`、`utils/` 继续按原用途工作；调优脚本只在对应子目录内产生产物。
