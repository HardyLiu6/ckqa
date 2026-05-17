# GraphRAG Scripts Layout

`graphrag_pipeline/scripts/` 现在按工作流分成两组实现目录，方便审阅：

- `prompt_tuning/`：Prompt 调优样本、audit 集、候选 Prompt 生成、官方 `prompt-tune` 封装。
- `prompt_tuning/` 还包含候选 Prompt 最终固化脚本，用于把选中的候选写入 `prompts/final/` 并更新 `.env`。
- `extraction_eval/`：候选 Prompt 抽取执行、结构化解析、规则化评测与诊断。
- `qa_eval/`：GraphRAG 四模式 baseline、规则/算法增强评分、bootstrap 显著性报告与第三方评测导出。

## qa_eval 脚本速查

| 脚本 | 用途 |
| --- | --- |
| `qa_eval/algorithmic_seed_builder.py` | 生成候选题材覆盖清单。 |
| `qa_eval/semantic_similarity.py` | 生成 BGE-M3 分块语义覆盖、ROUGE-Lsum、keyword recall 与可选 BERTScore。 |
| `qa_eval/semantic_threshold_calibrator.py` | 对 BGE-M3 语义覆盖阈值做抽样校准并生成 `semantic_threshold_calibration.md`。 |
| `qa_eval/run_loader.py` | 读取 run/test/raw 与索引路径，审计 text unit 前缀碰撞。 |
| `qa_eval/citation_extractor.py` | 解析 GraphRAG citation，并把 Reports / Sources / Entities / Relationships 映射回 text unit 前缀。 |
| `qa_eval/ir_metrics.py` | 调用 `ir-measures` 计算 citation R@k / RR / nDCG@k / AP。 |
| `qa_eval/algorithmic_scorer.py` | 生成规则指标 + IR 指标 + BGE-M3 语义覆盖 + latency/error + `effective_score_experimental`。 |
| `qa_eval/significance_reporter.py` | 生成四模式 bootstrap 对比，并在 category 样本过小时输出警告。 |
| `qa_eval/ragas_exporter.py` | 导出 RAGAS 兼容数据集。 |
| `qa_eval/factuality_extra_exporter.py` | 导出 SummaC / AlignScore / SCALE 兼容数据集。 |
| `backfill_qa_answer_citations.py` | dry-run 或显式执行历史问答回答中的 GraphRAG `[Data: Sources (...)]` 引用清理。 |

兼容性约定：

- 根目录同名脚本仍然保留，例如 `scripts/build_prompt_tuning_samples.py`、`scripts/run_candidate_extraction.py`。
- 这些根目录文件现在只是很薄的兼容入口，负责把旧命令转发到新的实现目录。
- 因此现有命令、测试导入和文档示例默认不需要修改。
