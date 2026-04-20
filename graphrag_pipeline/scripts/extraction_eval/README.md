# Extraction Eval Scripts

这个目录收口候选 Prompt 抽取、解析、打分和诊断实现：

- `run_candidate_extraction.py`：批量执行候选 Prompt 抽取。
- `extraction_schema.py`、`extraction_parser.py`：统一结构与容错解析。
- `prompt_loader.py`、`prompt_renderer.py`、`llm_client.py`、`result_writer.py`：执行期共享组件。
- `score_extraction_results.py`、`scoring_*.py`：步骤 8 规则化自动评测。
- `diagnose_step8.py`：评测诊断报告。

仓库外层仍保留同名兼容入口文件，现有 `python scripts/*.py` 命令无需修改。
