# Prompt Tuning Scripts

这个目录收口 GraphRAG Prompt 调优链路的实现代码：

- `build_prompt_tuning_samples.py`：从标准化输入构建调优样本。
- `build_audit_extraction_set.py`：从样本中抽小规模 audit 校准集。
- `generate_candidate_prompts.py`：生成候选 Prompt 与 manifest。
- `run_graphrag_prompt_tune.py`：封装官方 `prompt-tune` 执行与产物整理。
- `run_material_prompt_pipeline.py`：按单份 material 串起 fetch、sample、audit、prompt-tune、候选生成、smoke/full 抽取评分、gate、固化、索引和 QA smoke；默认只跑 smoke。
- `run_material_qa_smoke.py`：读取 material QA smoke 集，调用 GraphRAG query 并按期望关键词生成轻量报告。
- `finalize_candidate_prompt.py`：把选中的候选复制到 `prompts/final/<candidate>/`，更新 `.env` 中当前活动 Prompt 路径，并写出 `prompts/final/active_prompt.json`；pipeline 调用时会绑定 scoring run 的 manifest/scoring hash，避免跨 run 固化。

仓库外层仍保留同名兼容入口文件，现有 `python scripts/*.py` 命令无需修改。
