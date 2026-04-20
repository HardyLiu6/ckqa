# GraphRAG Scripts Layout

`graphrag_pipeline/scripts/` 现在按工作流分成两组实现目录，方便审阅：

- `prompt_tuning/`：Prompt 调优样本、audit 集、候选 Prompt 生成、官方 `prompt-tune` 封装。
- `extraction_eval/`：候选 Prompt 抽取执行、结构化解析、规则化评测与诊断。

兼容性约定：

- 根目录同名脚本仍然保留，例如 `scripts/build_prompt_tuning_samples.py`、`scripts/run_candidate_extraction.py`。
- 这些根目录文件现在只是很薄的兼容入口，负责把旧命令转发到新的实现目录。
- 因此现有命令、测试导入和文档示例默认不需要修改。
