# GraphRAG Scripts Layout

`graphrag_pipeline/scripts/` 现在按工作流分成两组实现目录，方便审阅：

- `prompt_tuning/`：Prompt 调优样本、audit 集、候选 Prompt 生成、官方 `prompt-tune` 封装，以及 material 级 smoke/full 编排与 QA smoke 入口。
- `prompt_tuning/` 还包含候选 Prompt 最终固化脚本，用于把选中的候选写入 `prompts/final/` 并更新 `.env`；material 编排入口会在 full gate 通过后用 scoring artifact hash 绑定本次固化。
- `extraction_eval/`：候选 Prompt 抽取执行、结构化解析、规则化评测、endpoint 诊断与 top-k scoring artifact 生成。

兼容性约定：

- 根目录同名脚本仍然保留，例如 `scripts/build_prompt_tuning_samples.py`、`scripts/run_candidate_extraction.py`。
- 这些根目录文件现在只是很薄的兼容入口，负责把旧命令转发到新的实现目录。
- 因此现有命令、测试导入和文档示例默认不需要修改。
