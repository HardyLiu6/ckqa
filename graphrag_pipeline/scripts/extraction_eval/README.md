# Extraction Eval Scripts

这个目录收口候选 Prompt 抽取、解析、打分和诊断实现：

- `run_candidate_extraction.py`：批量执行候选 Prompt 抽取。
- `extraction_schema.py`、`extraction_parser.py`：统一结构与容错解析。
- `output_guard.py`：识别缺少 JSON 根对象、可疑顶层字段和非领域输出，用于严格重试与评分诊断。
- `prompt_loader.py`、`prompt_renderer.py`、`llm_client.py`、`result_writer.py`：执行期共享组件。
- `score_extraction_results.py`、`scoring_*.py`：步骤 8 规则化自动评测；`--relation-validation-mode drop-invalid` 只用于端点过滤诊断，正式判定仍以默认 `raw` 为准。
- `relationship_postprocessor.py`：把既有 eval run 写成新的结构化后处理诊断 run；`strict` 模式会先做 alias 端点规范化、知识分类 `belongs_to -> contains` 安全翻转，再丢弃仍非法的关系；`strict-closure` 额外尝试补齐 evidence/description 中明确出现且类型可保守推断的缺失端点实体。
- `diagnose_step8.py`：评测诊断报告。
- `diagnose_gold_missing_relations.py`：对任一 eval run 做 gold 侧缺失关系归因（hit / direction_reversed / wrong_type / both_endpoints_present_but_not_connected / source_endpoint_missing / target_endpoint_missing / both_endpoints_missing 七类），用于判断瓶颈在实体层还是关系层；输出到 `results/reports/extraction_missing_relations/runs/<run_id>/`。

仓库外层仍保留同名兼容入口文件，现有 `python scripts/*.py` 命令无需修改。
